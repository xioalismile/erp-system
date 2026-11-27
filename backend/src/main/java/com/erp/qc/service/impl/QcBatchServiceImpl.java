package com.erp.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.config.BusinessException;
import com.erp.common.util.SecurityUtils;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.mapper.BatchInfoMapper;
import com.erp.production.service.SignatureService;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import com.erp.qc.service.QcBatchService;
import com.erp.qc.service.QcTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 批次检验服务实现（化妆品批记录）。
 * 模板匹配复用 QcTemplateService.resolveTemplateForMaterial（物料 → 分类链）；
 * 成品检无匹配时兜底默认模板 QT-COSMETIC-FINISH；记录号格式与工序质检一致（QCR+时间戳）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcBatchServiceImpl implements QcBatchService {

    private static final String DEFAULT_FINISH_TEMPLATE_CODE = "QT-COSMETIC-FINISH";
    private static final List<String> ALLOWED_STAGES = List.of("来料检", "成品检");
    private static final List<String> ALLOWED_RESULTS = List.of("合格", "不合格", "让步接收");

    private final QcRecordMapper qcRecordMapper;
    private final QcRecordItemMapper qcRecordItemMapper;
    private final QcTemplateService qcTemplateService;
    private final BatchInfoMapper batchInfoMapper;
    private final SignatureService signatureService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromBatch(String qcStage, Long batchId, HttpServletRequest request) {
        if (qcStage == null || !ALLOWED_STAGES.contains(qcStage)) {
            throw new BusinessException("检验环节只允许：来料检 / 成品检");
        }
        BatchInfo batch = batchInfoMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        Long ongoing = qcRecordMapper.selectCount(new LambdaQueryWrapper<QcRecord>()
                .eq(QcRecord::getBatchId, batchId)
                .and(w -> w.isNull(QcRecord::getResult).or().eq(QcRecord::getResult, "")));
        if (ongoing != null && ongoing > 0) {
            throw new BusinessException("该批次已有进行中的检验记录，请先完成提交");
        }
        QcTemplate template = qcTemplateService.resolveTemplateForMaterial(batch.getMaterialId());
        if (template == null && "成品检".equals(qcStage)) {
            template = findDefaultFinishTemplate();
        }
        if (template == null) {
            throw new BusinessException("该物料未配置检验模板，请先在质检模板中配置");
        }

        Long operatorId = SecurityUtils.getCurrentUserId(request);
        String operatorName = displayName(request);

        QcRecord record = new QcRecord();
        record.setRecordNo(nextRecordNo());
        record.setQcType(qcStage);
        record.setQcStage(qcStage);
        record.setBatchId(batchId);
        record.setBatchNo(batch.getBatchNo());
        record.setTemplateId(template.getTemplateId());
        record.setInspectorId(operatorId);
        record.setInspectorName(operatorName);
        record.setInspectTime(LocalDateTime.now());
        qcRecordMapper.insert(record);

        List<com.erp.qc.entity.QcTemplateItem> items = template.getItems();
        if (items != null) {
            for (com.erp.qc.entity.QcTemplateItem ti : items) {
                QcRecordItem row = new QcRecordItem();
                row.setRecordId(record.getRecordId());
                row.setLineNo(ti.getLineNo());
                row.setItemName(ti.getItemName());
                row.setStandardValue(ti.getStandardValue());
                row.setUpperLimit(ti.getUpperLimit());
                row.setLowerLimit(ti.getLowerLimit());
                qcRecordItemMapper.insert(row);
            }
        }

        batch.setQcStatus("待检");
        batchInfoMapper.updateById(batch);
        log.info("[批记录] 批次建检 {} | 批次 {} | 模板 {}", qcStage, batch.getBatchNo(), template.getTemplateCode());
        return record.getRecordId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitWithSign(Long recordId, QcRecord payload, HttpServletRequest request,
                               String password, String signImage) {
        QcRecord record = qcRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("检验记录不存在或已被删除");
        }
        if (record.getResult() != null && !record.getResult().isBlank()) {
            throw new BusinessException("该检验记录已提交，不能重复提交");
        }
        String result = payload.getResult();
        if (result == null || !ALLOWED_RESULTS.contains(result)) {
            throw new BusinessException("检验结论只允许：合格 / 不合格 / 让步接收");
        }
        // 实测明细落值（按 itemId 更新）
        List<QcRecordItem> items = payload.getItems();
        if (items != null) {
            for (QcRecordItem row : items) {
                if (row.getItemId() == null) continue;
                QcRecordItem db = qcRecordItemMapper.selectById(row.getItemId());
                if (db == null || !recordId.equals(db.getRecordId())) continue;
                db.setMeasuredValue(row.getMeasuredValue());
                db.setResult(row.getResult());
                db.setRemark(row.getRemark());
                qcRecordItemMapper.updateById(db);
            }
        }
        record.setResult(result);
        record.setInspectTime(LocalDateTime.now());
        Long operatorId = SecurityUtils.getCurrentUserId(request);
        record.setInspectorId(operatorId);
        record.setInspectorName(displayName(request));
        if (payload.getRemark() != null) {
            record.setRemark(payload.getRemark());
        }
        qcRecordMapper.updateById(record);

        signatureService.sign("检验提交", "qc_record", recordId,
                "提交检验记录（" + record.getQcStage() + " 批次 " + record.getBatchNo() + "，结论：" + result + "）",
                request, password, signImage);

        // 回写批次质量状态与结论
        BatchInfo batch = batchInfoMapper.selectById(record.getBatchId());
        if (batch != null) {
            batch.setQcResult(result);
            batch.setQcStatus("不合格".equals(result) ? "不合格" : "合格");
            batchInfoMapper.updateById(batch);
        }
        log.info("[批记录] 检验提交 记录{} 批次{} 结论{}", recordId, record.getBatchNo(), result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewWithSign(Long recordId, HttpServletRequest request, String password, String signImage) {
        QcRecord record = qcRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("检验记录不存在或已被删除");
        }
        if (record.getResult() == null || record.getResult().isBlank()) {
            throw new BusinessException("该检验记录尚未提交，请先完成检验提交");
        }
        Long reviewerId = SecurityUtils.getCurrentUserId(request);
        String reviewerName = displayName(request);
        signatureService.sign("检验复核", "qc_record", recordId,
                "复核检验记录（" + record.getQcStage() + " 批次 " + record.getBatchNo() + "，结论：" + record.getResult() + "）",
                request, password, signImage);
        record.setReviewerId(reviewerId);
        record.setReviewerName(reviewerName);
        qcRecordMapper.updateById(record);
        log.info("[批记录] 检验复核 记录{} 批次{} 复核人{}", recordId, record.getBatchNo(), reviewerName);
    }

    // ---------------- 内部辅助 ----------------

    private QcTemplate findDefaultFinishTemplate() {
        return qcTemplateService.listEnabled().stream()
                .filter(t -> DEFAULT_FINISH_TEMPLATE_CODE.equals(t.getTemplateCode()))
                .findFirst().map(t -> qcTemplateService.getDetail(t.getTemplateId())).orElse(null);
    }

    private static String nextRecordNo() {
        return "QCR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private static String displayName(HttpServletRequest request) {
        String realName = SecurityUtils.getCurrentRealName(request);
        return realName == null || realName.isBlank()
                ? SecurityUtils.getCurrentUsername(request) : realName;
    }
}
