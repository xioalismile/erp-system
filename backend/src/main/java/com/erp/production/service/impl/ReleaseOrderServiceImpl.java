package com.erp.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.util.SecurityUtils;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.mapper.BatchInfoMapper;
import com.erp.production.entity.ReleaseOrder;
import com.erp.production.entity.SampleRetention;
import com.erp.production.mapper.ReleaseOrderMapper;
import com.erp.production.mapper.SampleRetentionMapper;
import com.erp.production.service.ReleaseOrderService;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.mapper.QcRecordMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 放行单服务实现（化妆品批记录）。
 *
 * <p>状态机：草稿 → 提交（审批开=待审核 / 审批关=直接生效）→ 已生效；作废/反作废走基类。</p>
 * <p>严格校验（validateStrictSubmit）：批次存在且检验结论为 合格/让步接收。</p>
 */
@Slf4j
@Service
public class ReleaseOrderServiceImpl extends AbstractBillService<ReleaseOrderMapper, ReleaseOrder>
        implements ReleaseOrderService {

    private static final BigDecimal SAMPLE_RATIO = new BigDecimal("0.01");

    @Autowired private BatchInfoMapper batchInfoMapper;
    @Autowired private QcRecordMapper qcRecordMapper;
    @Autowired private SampleRetentionMapper sampleRetentionMapper;
    @Autowired private SysSerialNoConfigService serialNoService;
    @Autowired private SysConfigService sysConfigService;

    // ---------------- 字段访问抽象 ----------------

    @Override protected String getBillNo(ReleaseOrder b) { return b.getReleaseNo(); }
    @Override protected Object getId(ReleaseOrder b) { return b.getReleaseId(); }
    @Override protected String getStatus(ReleaseOrder b) { return b.getStatus(); }
    @Override protected void setStatus(ReleaseOrder b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(ReleaseOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(ReleaseOrder b, Integer c) { b.setCloseStatus(c); }
    @Override protected void setApprovedBy(ReleaseOrder b, String a) { b.setApprovedBy(a); }
    @Override protected void setApprovedAt(ReleaseOrder b, LocalDateTime t) { b.setApprovedAt(t); }
    @Override protected void setApprovalStatus(ReleaseOrder b, String s) { b.setApprovalStatus(s); }
    @Override protected void setClosedBy(ReleaseOrder b, String u) { b.setClosedBy(u); }
    @Override protected void setClosedAt(ReleaseOrder b, LocalDateTime t) { b.setClosedAt(t); }
    @Override protected void setClosedReason(ReleaseOrder b, String r) { b.setClosedReason(r); }
    @Override protected void setBillBeforeStatus(ReleaseOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(ReleaseOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(ReleaseOrder b, String u) { b.setVoidedBy(u); }
    @Override protected void setVoidedAt(ReleaseOrder b, LocalDateTime t) { b.setVoidedAt(t); }
    @Override protected void setVoidReason(ReleaseOrder b, String r) { b.setVoidReason(r); }
    @Override protected String getBillTypeLabel() { return "放行单"; }
    @Override protected String getApprovalBillType() { return "RELEASE_ORDER"; }

    // ---------------- 严格校验 ----------------

    @Override
    protected void validateStrictSubmit(ReleaseOrder bill) {
        if (bill.getBatchId() == null) {
            throw new BusinessException("放行单必须指定批次");
        }
        BatchInfo batch = batchInfoMapper.selectById(bill.getBatchId());
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        String result = batch.getQcResult();
        if (result == null || !( "合格".equals(result) || "让步接收".equals(result))) {
            throw new BusinessException("批次 " + batch.getBatchNo()
                    + " 检验结论为「" + (result == null || result.isBlank() ? "未检验" : result)
                    + "」，只有检验合格或让步接收的批次才能放行");
        }
    }

    // ---------------- 生效影响：回写批次 + 自动留样 ----------------

    @Override
    public void onApproved(ReleaseOrder bill) {
        BatchInfo batch = batchInfoMapper.selectById(bill.getBatchId());
        if (batch != null) {
            batch.setQcStatus("已放行");
            batch.setReleasedBy(bill.getApprovedBy() == null ? bill.getUpdatedBy() : bill.getApprovedBy());
            batch.setReleasedAt(LocalDateTime.now());
            batchInfoMapper.updateById(batch);
        }
        createSampleRetention(bill, batch);
        log.info("[批记录] 放行生效 批次{} 放行单{}", bill.getBatchNo(), bill.getReleaseNo());
    }

    @Override
    public void onReverseApproved(ReleaseOrder bill) {
        rollbackBatch(bill);
    }

    @Override
    public void onVoided(ReleaseOrder bill) {
        rollbackBatch(bill);
    }

    /**
     * 反作废影响（2026-11-09）：作废时已把批次从「已放行」回退，反作废恢复到「已生效」必须重新回写放行状态。
     * 只重放批次回写，不整体调用 onApproved：自动留样创建写入唯一留样单号且作废时并未删除留样记录，
     * 重放会产生重复留样/浪费流水号，故跳过（留样记录仍在，无需重建）。
     */
    @Override
    public void onUnvoided(ReleaseOrder bill) {
        restoreBatch(bill);
    }

    private void rollbackBatch(ReleaseOrder bill) {
        BatchInfo batch = batchInfoMapper.selectById(bill.getBatchId());
        if (batch != null && "已放行".equals(batch.getQcStatus())) {
            batch.setQcStatus("合格");
            batch.setReleasedBy(null);
            batch.setReleasedAt(null);
            batchInfoMapper.updateById(batch);
            log.info("[批记录] 放行回退 批次{} 放行单{}", bill.getBatchNo(), bill.getReleaseNo());
        }
    }

    /** 反作废重放：批次重新回写「已放行」+ 放行人/放行时间（与 onVoided 严格镜像） */
    private void restoreBatch(ReleaseOrder bill) {
        BatchInfo batch = batchInfoMapper.selectById(bill.getBatchId());
        if (batch != null && !"已放行".equals(batch.getQcStatus())) {
            batch.setQcStatus("已放行");
            batch.setReleasedBy(bill.getApprovedBy() == null ? bill.getUpdatedBy() : bill.getApprovedBy());
            batch.setReleasedAt(LocalDateTime.now());
            batchInfoMapper.updateById(batch);
            log.info("[批记录] 放行恢复 批次{} 放行单{}", bill.getBatchNo(), bill.getReleaseNo());
        }
    }

    /** 自动生成留样任务：留样量=批次数量×1%，保存期限=设置中心参数（默认24个月） */
    private void createSampleRetention(ReleaseOrder bill, BatchInfo batch) {
        try {
            long exists = sampleRetentionMapper.selectCount(new LambdaQueryWrapper<SampleRetention>()
                    .eq(SampleRetention::getBatchId, bill.getBatchId()));
            if (exists > 0) {
                log.info("[批记录] 批次{}已有留样记录，跳过自动生成", bill.getBatchNo());
                return;
            }
            int months;
            try {
                months = sysConfigService.getIntValue("batch.sample.keep.months", 24);
            } catch (Exception e) {
                months = 24;
            }
            BigDecimal qty = batch == null || batch.getQuantity() == null
                    ? BigDecimal.ZERO : batch.getQuantity();
            SampleRetention r = new SampleRetention();
            r.setRetentionNo(serialNoService.generateOrDefault("SAMPLE_RETENTION",
                    () -> "SR" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))));
            r.setBatchId(bill.getBatchId());
            r.setBatchNo(bill.getBatchNo());
            r.setMaterialId(bill.getMaterialId());
            r.setMaterialCode(bill.getMaterialCode());
            r.setMaterialName(bill.getMaterialName());
            r.setRetainQty(qty.multiply(SAMPLE_RATIO).setScale(2, RoundingMode.HALF_UP));
            r.setRetainDate(LocalDate.now());
            r.setKeepUntil(LocalDate.now().plusMonths(months));
            r.setRemindStatus("未到期");
            r.setStatus("已生效");
            sampleRetentionMapper.insert(r);
            log.info("[批记录] 留样自动生成 {} 批次{} 留样量{} 保存至{}",
                    r.getRetentionNo(), r.getBatchNo(), r.getRetainQty(), r.getKeepUntil());
        } catch (Exception e) {
            // 留样生成失败不阻断放行主流程，但必须留日志可查
            log.error("[批记录] 留样自动生成失败 放行单{} 原因: {}", bill.getReleaseNo(), e.getMessage(), e);
        }
    }

    // ---------------- 便利入口 ----------------

    @Override
    @Transactional
    public ReleaseOrder createDraftFromBatch(Long batchId, HttpServletRequest request) {
        BatchInfo batch = batchInfoMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        // 同批次已有未作废放行单时禁止重复创建
        Long open = baseMapper.selectCount(new LambdaQueryWrapper<ReleaseOrder>()
                .eq(ReleaseOrder::getBatchId, batchId)
                .ne(ReleaseOrder::getStatus, "已作废"));
        if (open != null && open > 0) {
            throw new BusinessException("该批次已存在放行单，请在原单上操作");
        }
        // 关联最近一条该批次的检验记录
        QcRecord qc = qcRecordMapper.selectOne(new LambdaQueryWrapper<QcRecord>()
                .eq(QcRecord::getBatchId, batchId)
                .isNotNull(QcRecord::getResult)
                .orderByDesc(QcRecord::getRecordId)
                .last("LIMIT 1"));

        ReleaseOrder bill = new ReleaseOrder();
        bill.setReleaseNo(serialNoService.generateOrDefault("RELEASE_ORDER",
                () -> "RO" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))));
        bill.setBatchId(batch.getId());
        bill.setBatchNo(batch.getBatchNo());
        bill.setMaterialId(batch.getMaterialId());
        bill.setMaterialCode(batch.getMaterialCode());
        bill.setMaterialName(batch.getMaterialName());
        if (qc != null) {
            bill.setWorkOrderId(qc.getOrderId());
            bill.setWorkOrderNo(qc.getOrderNo());
            bill.setQcRecordId(qc.getRecordId());
        }
        bill.setReleaseResult("放行");
        bill.setCreatedBy(SecurityUtils.getCurrentUsername(request));
        saveDraft(bill);
        return bill;
    }

    /** 批次是否存在已生效放行单（出库校验用） */
    public boolean hasEffectiveRelease(Long batchId) {
        if (batchId == null) return false;
        List<ReleaseOrder> list = baseMapper.selectList(new LambdaQueryWrapper<ReleaseOrder>()
                .eq(ReleaseOrder::getBatchId, batchId)
                .select(ReleaseOrder::getReleaseId, ReleaseOrder::getStatus));
        return list.stream().anyMatch(r -> "已生效".equals(r.getStatus()));
    }
}
