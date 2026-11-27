package com.erp.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.production.entity.BatchProductionRecord;
import com.erp.production.entity.RecordCorrection;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.entity.WorkOperationLog;
import com.erp.production.entity.WorkOrder;
import com.erp.production.mapper.BatchProductionRecordMapper;
import com.erp.production.mapper.RecordCorrectionMapper;
import com.erp.production.mapper.RoutingMapper;
import com.erp.production.mapper.RoutingOperationMapper;
import com.erp.production.mapper.WorkOperationLogMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.service.BatchRecordService;
import com.erp.production.service.SignatureService;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批生产记录服务实现（化妆品批记录）。
 *
 * <p>快照口径：工单生效时把 BOM 与工艺路线序列化为 JSONB 固化，后续改配方不影响已生成的记录。
 * 聚合口径：投料（领料单按工单号关联，带原料批次）/ 工序（work_operation_log 只增不改）/
 * 检验（qc_record 按工单）/ 更正（record_correction）/ 签名（signature_record）全部实时查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchRecordServiceImpl implements BatchRecordService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BatchProductionRecordMapper recordMapper;
    private final WorkOrderMapper workOrderMapper;
    private final BomMapper bomMapper;
    private final RoutingMapper routingMapper;
    private final RoutingOperationMapper routingOperationMapper;
    private final WorkOperationLogMapper workOperationLogMapper;
    private final MaterialRequisitionMapper requisitionMapper;
    private final MaterialRequisitionItemMapper requisitionItemMapper;
    private final QcRecordMapper qcRecordMapper;
    private final QcRecordItemMapper qcRecordItemMapper;
    private final RecordCorrectionMapper correctionMapper;
    private final SignatureService signatureService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchProductionRecord ensureRecord(Long workOrderId) {
        if (workOrderId == null) return null;
        BatchProductionRecord exists = recordMapper.selectOne(new LambdaQueryWrapper<BatchProductionRecord>()
                .eq(BatchProductionRecord::getWorkOrderId, workOrderId));
        if (exists != null) return exists;
        try {
            WorkOrder order = workOrderMapper.selectById(workOrderId);
            if (order == null) return null;

            BatchProductionRecord r = new BatchProductionRecord();
            r.setWorkOrderId(workOrderId);
            r.setWorkOrderNo(order.getOrderNo());
            r.setProductId(order.getMaterialId());
            r.setProductCode(order.getMaterialCode());
            r.setProductName(order.getMaterialName());
            r.setBomSnapshot(snapshotBom(order.getBomId()));
            r.setRoutingSnapshot(snapshotRouting(order.getRoutingId()));
            r.setStatus("生成中");
            recordMapper.insert(r);
            log.info("[批记录] 工单{} 批生产记录已预生成（配方/工艺快照已固化）", order.getOrderNo());
            return r;
        } catch (Exception e) {
            // 记录预生成失败不阻断工单生效，但必须留日志
            log.error("[批记录] 工单{} 批生产记录预生成失败: {}", workOrderId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getFullRecord(Long workOrderId) {
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在或已被删除");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        BatchProductionRecord header = recordMapper.selectOne(new LambdaQueryWrapper<BatchProductionRecord>()
                .eq(BatchProductionRecord::getWorkOrderId, workOrderId));
        out.put("header", header == null ? new BatchProductionRecord() : header);
        out.put("workOrder", order);
        out.put("materials", loadMaterials(order));
        out.put("operations", loadOperations(workOrderId));
        out.put("qcRecords", loadQcRecords(workOrderId));
        out.put("corrections", loadCorrections(workOrderId));
        out.put("signatures", signatureService.listByTarget("batch_production_record", workOrderId));
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEnv(Long workOrderId, BigDecimal temperature, BigDecimal humidity, HttpServletRequest request) {
        BatchProductionRecord r = recordMapper.selectOne(new LambdaQueryWrapper<BatchProductionRecord>()
                .eq(BatchProductionRecord::getWorkOrderId, workOrderId));
        if (r == null) {
            r = ensureRecord(workOrderId);
        }
        if (r == null) {
            throw new BusinessException("批生产记录不存在，无法保存环境记录");
        }
        r.setEnvTemperature(temperature);
        r.setEnvHumidity(humidity);
        r.setEnvRecorder(com.erp.common.util.SecurityUtils.getCurrentRealName(request) == null
                ? com.erp.common.util.SecurityUtils.getCurrentUsername(request)
                : com.erp.common.util.SecurityUtils.getCurrentRealName(request));
        r.setEnvRecordTime(LocalDateTime.now());
        recordMapper.updateById(r);
    }

    // ---------------- 明细聚合 ----------------

    /** 投料明细：按工单号关联领料单，带出每行原料批次（追溯链上游数据源） */
    private List<Map<String, Object>> loadMaterials(WorkOrder order) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) return rows;
        List<MaterialRequisition> heads = requisitionMapper.selectList(
                new QueryWrapper<MaterialRequisition>().eq("work_order_no", order.getOrderNo()));
        for (MaterialRequisition head : heads) {
            if (head.getStatus() != null && "已作废".equals(head.getStatus())) continue;
            List<MaterialRequisitionItem> items = requisitionItemMapper.selectList(
                    new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", head.getRequisitionId()));
            for (MaterialRequisitionItem it : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("requisitionNo", head.getRequisitionNo());
                row.put("requisitionDate", head.getRequisitionDate());
                row.put("materialCode", it.getMaterialCode());
                row.put("materialName", it.getMaterialName());
                row.put("specification", it.getSpecification());
                row.put("quantity", it.getQuantity());
                row.put("batchNo", it.getBatchNo());
                rows.add(row);
            }
        }
        return rows;
    }

    /** 工序记录：操作日志只增不改，直接按工单查询 */
    private List<WorkOperationLog> loadOperations(Long workOrderId) {
        return workOperationLogMapper.selectList(new QueryWrapper<WorkOperationLog>()
                .eq("order_id", workOrderId)
                .orderByAsc("operate_time"));
    }

    /** 检验记录：按工单关联的检验记录，附检项明细 */
    private List<Map<String, Object>> loadQcRecords(Long workOrderId) {
        List<QcRecord> records = qcRecordMapper.selectList(new QueryWrapper<QcRecord>()
                .eq("order_id", workOrderId)
                .orderByAsc("record_id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (QcRecord rec : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("record", rec);
            List<QcRecordItem> items = qcRecordItemMapper.selectList(
                    new QueryWrapper<QcRecordItem>().eq("record_id", rec.getRecordId())
                            .orderByAsc("line_no"));
            row.put("items", items);
            out.add(row);
        }
        return out;
    }

    /** 更正链：按本工单涉及的记录定位（工单本身 + 其工序日志 + 其检验记录） */
    private List<RecordCorrection> loadCorrections(Long workOrderId) {
        List<RecordCorrection> out = new ArrayList<>(correctionMapper.selectList(
                new QueryWrapper<RecordCorrection>()
                        .eq("target_table", "production_order")
                        .eq("target_id", workOrderId)));
        List<QcRecord> qcList = qcRecordMapper.selectList(new QueryWrapper<QcRecord>()
                .eq("order_id", workOrderId).select("record_id"));
        for (QcRecord qc : qcList) {
            out.addAll(correctionMapper.selectList(new QueryWrapper<RecordCorrection>()
                    .eq("target_table", "qc_record").eq("target_id", qc.getRecordId())));
        }
        out.sort((a, b) -> {
            LocalDateTime x = a.getCorrectedAt(), y = b.getCorrectedAt();
            if (x == null) return y == null ? 0 : -1;
            return y == null ? 1 : x.compareTo(y);
        });
        return out;
    }

    // ---------------- 快照 ----------------

    private String snapshotBom(Long bomId) {
        if (bomId == null) return null;
        try {
            Bom bom = bomMapper.selectById(bomId);
            return bom == null ? null : JSON.writeValueAsString(bom);
        } catch (Exception e) {
            log.warn("[批记录] BOM 快照序列化失败 bomId={}: {}", bomId, e.getMessage());
            return null;
        }
    }

    private String snapshotRouting(Long routingId) {
        if (routingId == null) return null;
        try {
            Routing routing = routingMapper.selectById(routingId);
            if (routing == null) return null;
            List<RoutingOperation> ops = routingOperationMapper.selectList(
                    new QueryWrapper<RoutingOperation>().eq("routing_id", routingId)
                            .orderByAsc("sequence_no"));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("routing", routing);
            snapshot.put("operations", ops);
            return JSON.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("[批记录] 工艺路线快照序列化失败 routingId={}: {}", routingId, e.getMessage());
            return null;
        }
    }
}
