package com.erp.inventory.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.inventory.batch.mapper.BatchTraceMapper;
import com.erp.inventory.batch.service.BatchTraceService;
import com.erp.production.entity.BatchProductionRecord;
import com.erp.production.entity.ReleaseOrder;
import com.erp.production.mapper.BatchProductionRecordMapper;
import com.erp.production.mapper.ReleaseOrderMapper;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次追溯服务实现（化妆品批记录）。
 *
 * <p>口径写死（与设计文档 §3.4 一致）：
 * 上游由「生产入库明细批号 → 工单 → 领料明细批号 → 批次档案」推导；
 * 下游由「销售出库明细批号 → 出库单 → 客户」推导；
 * 历史单据无批次时该节点标注「历史数据未记录批次」，不报错。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTraceServiceImpl implements BatchTraceService {

    private final BatchTraceMapper traceMapper;
    private final BatchProductionRecordMapper recordMapper;
    private final QcRecordMapper qcRecordMapper;
    private final QcRecordItemMapper qcRecordItemMapper;
    private final ReleaseOrderMapper releaseOrderMapper;

    @Override
    public Map<String, Object> trace(Long batchId, String direction) {
        if (batchId == null) {
            throw new BusinessException("请指定要追溯的批次");
        }
        Map<String, Object> batch = traceMapper.findBatchById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        String dir = (direction == null || direction.isBlank()) ? "both" : direction.toLowerCase();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch", batch);
        if ("up".equals(dir) || "both".equals(dir)) {
            out.put("upstream", buildUpstream(batch));
        }
        if ("up".equals(dir) || "both".equals(dir)) {
            // 本批节点：up/both 时一并给出，作为树的中轴
            out.put("self", buildSelf(batch));
        }
        if ("down".equals(dir) || "both".equals(dir)) {
            out.put("downstream", buildDownstream(batch));
        }
        return out;
    }

    // ---------------- 上游：原料批次 ----------------

    private Map<String, Object> buildUpstream(Map<String, Object> batch) {
        String batchNo = str(batch.get("batchNo"));
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> orders = traceMapper.findWorkOrdersByBatchNo(batchNo);
        List<Map<String, Object>> materials = new ArrayList<>();
        List<String> orderNos = new ArrayList<>();
        boolean noBatchRecorded = false;

        for (Map<String, Object> o : orders) {
            String orderNo = str(o.get("orderNo"));
            if (orderNo != null) orderNos.add(orderNo);
            List<Map<String, Object>> feeding = traceMapper.findFeedingByWorkOrderNo(orderNo);
            if (feeding.isEmpty()) {
                noBatchRecorded = true;
            }
            for (Map<String, Object> f : feeding) {
                Map<String, Object> row = new LinkedHashMap<>(f);
                String materialBatchNo = str(f.get("batchNo"));
                if (materialBatchNo == null || materialBatchNo.isBlank()) {
                    row.put("batchNo", null);
                    row.put("note", "历史数据未记录批次");
                    noBatchRecorded = true;
                } else {
                    Map<String, Object> mb = traceMapper.findBatchByNo(materialBatchNo);
                    if (mb != null) {
                        row.put("supplierBatchNo", mb.get("supplierBatchNo"));
                        row.put("supplierName", mb.get("supplierName"));
                        row.put("qcStatus", mb.get("qcStatus"));
                        row.put("batchId", mb.get("batchId"));
                    }
                }
                materials.add(row);
            }
        }
        out.put("productionOrders", orders);
        out.put("materials", materials);
        if (orders.isEmpty()) {
            out.put("note", "未找到该批次的生产入库记录，可能是外购或历史数据未记录批次");
        } else if (noBatchRecorded) {
            out.put("note", "部分单据未记录原料批次，历史数据未记录批次");
        }
        return out;
    }

    // ---------------- 本批：工单 + 快照 + 检验 + 放行 ----------------

    private Map<String, Object> buildSelf(Map<String, Object> batch) {
        Map<String, Object> out = new LinkedHashMap<>();
        String batchNo = str(batch.get("batchNo"));
        BatchProductionRecord record = recordMapper.selectOne(new LambdaQueryWrapper<BatchProductionRecord>()
                .eq(BatchProductionRecord::getBatchNo, batchNo)
                .last("LIMIT 1"));
        out.put("batchRecord", record);
        if (record == null) {
            out.put("note", "该批次未关联批生产记录，历史数据未记录批次");
        }
        List<QcRecord> qcList = qcRecordMapper.selectList(new QueryWrapper<QcRecord>()
                .eq("batch_no", batchNo).orderByAsc("record_id"));
        List<Map<String, Object>> qcOut = new ArrayList<>();
        for (QcRecord qc : qcList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("record", qc);
            List<QcRecordItem> items = qcRecordItemMapper.selectList(
                    new QueryWrapper<QcRecordItem>().eq("record_id", qc.getRecordId()).orderByAsc("line_no"));
            row.put("items", items);
            qcOut.add(row);
        }
        out.put("qcRecords", qcOut);

        Object batchId = batch.get("id");
        List<ReleaseOrder> releases = releaseOrderMapper.selectList(new LambdaQueryWrapper<ReleaseOrder>()
                .eq(ReleaseOrder::getBatchId, batchId == null ? -1L : Long.valueOf(batchId.toString()))
                .orderByAsc(ReleaseOrder::getReleaseId));
        out.put("releaseOrders", releases);
        return out;
    }

    // ---------------- 下游：客户去向 + 召回反查 ----------------

    private Map<String, Object> buildDownstream(Map<String, Object> batch) {
        Map<String, Object> out = new LinkedHashMap<>();
        String batchNo = str(batch.get("batchNo"));
        List<Map<String, Object>> deliveries = traceMapper.findDeliveriesByBatchNo(batchNo);
        out.put("deliveries", deliveries);

        // 若本批被用作原料（出现在领料明细批号里），给出「哪些成品批用了它」——召回反查
        List<Map<String, Object>> products = traceMapper.findDownstreamProductsByMaterialBatch(batchNo);
        out.put("usedByProducts", products);

        if (deliveries.isEmpty() && products.isEmpty()) {
            out.put("note", "该批次暂无下游记录");
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
