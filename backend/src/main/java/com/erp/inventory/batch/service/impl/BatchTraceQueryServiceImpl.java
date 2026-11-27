package com.erp.inventory.batch.service.impl;

import com.erp.common.bill.link.BillLinkRegistry;
import com.erp.config.BusinessException;
import com.erp.inventory.batch.mapper.BatchTraceQueryMapper;
import com.erp.inventory.batch.service.BatchTraceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批次溯源查询实现（2026-11-06）。
 *
 * <p>推导口径（写死）：</p>
 * <ul>
 *   <li>相关单据：库存流水按「批次号+物料」取出，再按单据类型/单号回查单据头；</li>
 *   <li>组成物料：生产入库明细批号 → 生产工单 → 生产领料单（PICK）明细 → 投料物料及批号；</li>
 *   <li>多层：组成物料本身若是自制半成品，继续按同样路径向上追，最多 5 层、防循环；</li>
 *   <li>下游：本批被哪些领料单领用 → 对应工单产出的成品批；销售出库去向从流水单据里带客户。</li>
 * </ul>
 *
 * <p>历史单据无批号：节点标注「历史数据未记录批次」，不报错。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTraceQueryServiceImpl implements BatchTraceQueryService {

    private static final int MAX_NODES = 200;

    private final BatchTraceQueryMapper traceMapper;
    private final BillLinkRegistry registry;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public BatchTraceQueryMapper mapper() {
        return traceMapper;
    }

    @Override
    public Map<String, Object> trace(String batchNo, Long materialId, Integer depth) {
        if (batchNo == null || batchNo.isBlank()) throw new BusinessException("请指定要溯源的批次号");
        if (materialId == null) throw new BusinessException("请指定批次所属物料");
        String no = batchNo.trim();
        int maxDepth = depth == null ? 5 : Math.max(1, Math.min(8, depth));

        Map<String, Object> batch = traceMapper.findBatch(no, materialId);
        Map<String, Object> archive = traceMapper.findBatchArchive(no, materialId);
        if (batch == null && archive == null) {
            throw new BusinessException("未找到批次「" + no + "」的库存或档案记录");
        }
        if (batch == null) {
            batch = archive;
        } else if (archive != null) {
            // 库存批次缺的展示字段用批次档案补（不覆盖已有值）
            for (Map.Entry<String, Object> e : archive.entrySet()) {
                if (batch.get(e.getKey()) == null || String.valueOf(batch.get(e.getKey())).isBlank()) {
                    batch.put(e.getKey(), e.getValue());
                }
            }
        }

        Map<String, List<Map<String, Object>>> docs = ledgerDocs(no, materialId);

        List<String> notes = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(materialId + "|" + no);
        int[] nodeCounter = new int[]{0};
        List<Map<String, Object>> components = buildComponents(no, materialId, maxDepth, visited, nodeCounter, notes);
        if (components.isEmpty()) {
            notes.add("该批次未关联生产入库记录，可能是外购原料或历史数据未记录批次");
        }

        Map<String, Object> downstream = buildDownstream(no, materialId, docs.get("outbound"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch", batch);
        out.put("inbound", docs.get("inbound"));
        out.put("outbound", docs.get("outbound"));
        out.put("components", components);
        out.put("downstream", downstream);
        out.put("notes", notes);
        return out;
    }

    @Override
    public Map<String, List<Map<String, Object>>> ledgerDocs(String batchNo, Long materialId) {
        List<Map<String, Object>> ledger = traceMapper.findLedger(batchNo, materialId);
        List<Map<String, Object>> inbound = new ArrayList<>();
        List<Map<String, Object>> outbound = new ArrayList<>();
        int count = 0;
        for (Map<String, Object> row : ledger) {
            if (count++ >= MAX_NODES) break;
            BigDecimal inQty = dec(row.get("inQuantity"));
            BigDecimal outQty = dec(row.get("outQuantity"));
            Map<String, Object> doc = resolveDoc(str(row.get("sourceBillType")), str(row.get("sourceBillNo")));
            Map<String, Object> line = new LinkedHashMap<>(row);
            line.put("docNo", doc.get("docNo"));
            line.put("docType", doc.get("docType"));
            line.put("docTypeName", doc.get("docTypeName"));
            line.put("docDate", doc.get("docDate"));
            line.put("docStatus", doc.get("docStatus"));
            line.put("billId", doc.get("billId"));
            line.put("supplierName", doc.get("supplierName"));
            line.put("customerName", doc.get("customerName"));
            if (inQty != null && inQty.compareTo(BigDecimal.ZERO) > 0) {
                line.put("direction", "入库");
                line.put("quantity", inQty);
                inbound.add(line);
            } else if (outQty != null && outQty.compareTo(BigDecimal.ZERO) != 0) {
                line.put("direction", "出库");
                line.put("quantity", outQty.abs());
                outbound.add(line);
            }
        }
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        out.put("inbound", inbound);
        out.put("outbound", outbound);
        return out;
    }

    @Override
    public Map<String, Object> resolveDoc(String billType, String billNo) {
        Map<String, Object> out = new LinkedHashMap<>();
        String type = billType == null ? "" : billType.trim();
        String no = billNo == null ? "" : billNo.trim();
        String docType = registry.docTypeByName(type);
        if (docType == null) docType = registry.docTypeByName(type.toUpperCase());
        BillLinkRegistry.DocDef def = registry.doc(docType);
        out.put("docType", docType == null ? type : docType);
        out.put("docTypeName", def != null && def.name != null ? def.name : (type.isBlank() ? "" : type));
        out.put("docNo", no);
        out.put("docDate", null);
        out.put("docStatus", null);
        out.put("billId", null);
        out.put("supplierName", null);
        out.put("customerName", null);
        if (no.isBlank() || def == null || def.table == null || def.noCol == null) return out;

        try {
            String sql = "SELECT " + def.pkCol + " AS id, "
                    + (def.dateCol == null ? "NULL" : def.dateCol) + " AS dt, "
                    + (def.statusCol == null ? "NULL" : def.statusCol) + " AS st"
                    + " FROM " + def.table + " WHERE " + def.noCol + " = ? LIMIT 1";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, no);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                out.put("billId", row.get("id"));
                out.put("docDate", row.get("dt"));
                out.put("docStatus", row.get("st"));
            }
        } catch (Exception e) {
            log.warn("[批次溯源] 单据头查询失败 type={} no={}: {}", type, no, e.getMessage());
        }

        // 供应商 / 客户（化妆品溯源需要看到来源与去向）
        try {
            if ("PURCHASE_INBOUND".equals(docType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT supplier_name FROM purchase_receipt WHERE receipt_no = ? LIMIT 1", no);
                if (!rows.isEmpty()) out.put("supplierName", rows.get(0).get("supplier_name"));
            } else if ("SALE_DELIVERY".equals(docType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT c.customer_name FROM sale_delivery d"
                                + " LEFT JOIN customer c ON c.customer_id = d.customer_id"
                                + " WHERE d.delivery_no = ? LIMIT 1", no);
                if (!rows.isEmpty()) out.put("customerName", rows.get(0).get("customer_name"));
            }
        } catch (Exception e) {
            log.warn("[批次溯源] 供应商/客户查询失败 type={} no={}: {}", type, no, e.getMessage());
        }
        return out;
    }

    // ---------------- 上游：多层组成物料 ----------------

    private List<Map<String, Object>> buildComponents(String batchNo, Long materialId, int depth,
                                                      Set<String> visited, int[] nodeCounter, List<String> notes) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (depth <= 0) return out;
        List<Map<String, Object>> receipts = traceMapper.findProductionReceipts(batchNo, materialId);
        if (receipts == null || receipts.isEmpty()) return out;

        Map<String, List<Map<String, Object>>> feedingCache = new HashMap<>();
        for (Map<String, Object> receipt : receipts) {
            String workOrderNo = str(receipt.get("workOrderNo"));
            if (workOrderNo == null || workOrderNo.isBlank()) continue;
            List<Map<String, Object>> feeding = feedingCache.computeIfAbsent(workOrderNo,
                    traceMapper::findFeedingByWorkOrder);
            for (Map<String, Object> f : feeding) {
                if (nodeCounter[0]++ >= MAX_NODES) {
                    notes.add("组成物料超过 " + MAX_NODES + " 条，已截断");
                    return out;
                }
                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("materialId", f.get("materialId"));
                comp.put("materialCode", f.get("materialCode"));
                comp.put("materialName", f.get("materialName"));
                comp.put("specification", f.get("specification"));
                comp.put("quantity", f.get("quantity"));
                comp.put("batchNo", f.get("batchNo"));
                comp.put("requisitionNo", f.get("requisitionNo"));
                comp.put("requisitionDate", f.get("requisitionDate"));
                comp.put("workOrderNo", workOrderNo);
                comp.put("receiptNo", receipt.get("receiptNo"));

                String compBatchNo = str(f.get("batchNo"));
                Long compMaterialId = f.get("materialId") == null ? null : ((Number) f.get("materialId")).longValue();
                if (compBatchNo == null || compBatchNo.isBlank()) {
                    comp.put("note", "历史数据未记录批次");
                    comp.put("inbound", new ArrayList<>());
                    comp.put("components", new ArrayList<>());
                    out.add(comp);
                    continue;
                }

                String key = compMaterialId + "|" + compBatchNo;
                comp.put("batch", traceMapper.findBatch(compBatchNo, compMaterialId));
                Map<String, List<Map<String, Object>>> docs = ledgerDocs(compBatchNo, compMaterialId);
                comp.put("inbound", docs.get("inbound"));
                if (visited.contains(key)) {
                    comp.put("note", "该批次已在上层出现，避免循环不再展开");
                    comp.put("components", new ArrayList<>());
                } else {
                    visited.add(key);
                    comp.put("components", buildComponents(compBatchNo, compMaterialId, depth - 1,
                            visited, nodeCounter, notes));
                }
                out.add(comp);
            }
        }
        return out;
    }

    // ---------------- 下游：成品批 + 客户 ----------------

    private Map<String, Object> buildDownstream(String batchNo, Long materialId, List<Map<String, Object>> outbound) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> requisitions = traceMapper.findConsumingRequisitions(batchNo, materialId);
        out.put("requisitions", requisitions == null ? new ArrayList<>() : requisitions);

        List<Map<String, Object>> products = new ArrayList<>();
        Set<String> seenOrders = new HashSet<>();
        Set<String> seenProducts = new HashSet<>();
        if (requisitions != null) {
            for (Map<String, Object> r : requisitions) {
                String workOrderNo = str(r.get("workOrderNo"));
                if (workOrderNo == null || workOrderNo.isBlank() || !seenOrders.add(workOrderNo)) continue;
                for (Map<String, Object> p : traceMapper.findProductsByWorkOrder(workOrderNo)) {
                    String productNo = str(p.get("batchNo"));
                    if (productNo != null && productNo.equals(batchNo)) continue;
                    String key = str(p.get("materialId")) + "|" + productNo;
                    if (!seenProducts.add(key)) continue;
                    Map<String, Object> row = new LinkedHashMap<>(p);
                    row.put("workOrderNo", workOrderNo);
                    products.add(row);
                }
            }
        }
        out.put("products", products);

        List<Map<String, Object>> deliveries = new ArrayList<>();
        if (outbound != null) {
            for (Map<String, Object> row : outbound) {
                if ("SALE_DELIVERY".equals(str(row.get("docType")))) {
                    deliveries.add(row);
                }
            }
        }
        out.put("deliveries", deliveries);
        return out;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
