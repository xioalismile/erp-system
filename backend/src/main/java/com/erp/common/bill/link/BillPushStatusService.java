package com.erp.common.bill.link;

import com.erp.common.bill.BillStatusUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 列表页「下推状态」批量汇总服务（只读，2026-10-10 新增）。
 *
 * <p>口径（用户 2026-10-10 确认）：</p>
 * <ul>
 *   <li>数量勾稽从下游单据明细【实时聚合】，不依赖来源明细上的累计回写列（历史数据/手工选单路径可能不准）；</li>
 *   <li>已作废的下游单据一律剔除；</li>
 *   <li>已生效类下游明细数量计入"已下推数量"；草稿/待审核只计"已下推未生效"张数，不计数量（防止"显示完成但下游还没生效"）；</li>
 *   <li>状态：NONE=无有效下游（如"未发货"）/ PARTIAL=进行中（如"发货中"）/ FULL=全部完成（如"发货完成"）。</li>
 * </ul>
 *
 * <p>本期接入：销售订单（发货情况=销售出库单 / 开票情况=销售发票 / 生产情况=生产工单）。
 * 其它单据列表后续铺开时在 {@link #summarize} 登记，架构不变。</p>
 *
 * <p>安全：所有查询均为参数化占位符（无字符串拼接值）；入参 ids 由 Controller 解析校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillPushStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final BillLinkRegistry registry;

    /** 单次汇总最大单据数（与列表分页上限对齐，防滥用） */
    public static final int MAX_IDS = 200;

    /** 状态枚举：无有效下游 / 进行中 / 全部完成 */
    public static final String STATE_NONE = "NONE";
    public static final String STATE_PARTIAL = "PARTIAL";
    public static final String STATE_FULL = "FULL";

    public Map<String, Map<String, Object>> summarize(String billType, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new LinkedHashMap<>();
        try {
            Map<String, Map<String, Object>> generic = summarizeTargets(billType, ids);
            if ("SALE_ORDER".equals(billType)) {
                Map<String, Map<String, Object>> legacy = summarizeSaleOrder(ids);
                for (Long id : ids) {
                    Map<String, Object> row = legacy.computeIfAbsent(String.valueOf(id), k -> new LinkedHashMap<>());
                    Map<String, Object> targetMap = generic.get(String.valueOf(id));
                    if (targetMap != null) row.put("targets", targetMap.get("targets"));
                }
                return legacy;
            }
            return generic;
        } catch (Exception e) {
            // 汇总失败不影响列表主数据展示，前端状态列按空渲染
            log.warn("下推状态汇总失败 billType={} idCount={}", billType, ids.size(), e);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 通用下推目标汇总（2026-10-31，覆盖所有登记了来源→去向的单据）：
     * 用 BillLinkRegistry 的 TargetDef SQL 取下游表头/明细，按“已生效 + 草稿/待审核”数量汇总；
     * 数量型目标按下游数量合计判断 FULL；无明细/一对一目标按“是否已生成下游”判断 FULL。
     */
    private Map<String, Map<String, Object>> summarizeTargets(String billType, List<Long> ids) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        BillLinkRegistry.DocDef src = registry.doc(billType);
        if (src == null) return out;
        List<BillLinkRegistry.TargetDef> targets = registry.targets(billType);

        for (Long id : ids) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, Object> targetMap = new LinkedHashMap<>();

            String sourceNo = null;
            try {
                if (src.noCol != null) {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            "select " + src.noCol + " as no from " + src.table + " where " + src.pkCol + " = ?", id);
                    if (!rows.isEmpty() && rows.get(0).get("no") != null) {
                        sourceNo = String.valueOf(rows.get(0).get("no"));
                    }
                }
            } catch (Exception e) {
                log.warn("下推状态取来源单号失败 billType={} id={}", billType, id, e);
            }

            BigDecimal sourceQty = BigDecimal.ONE;
            if (src.itemTable != null && src.qtyCol != null && src.itemFkCol != null) {
                try {
                    BigDecimal q = jdbcTemplate.queryForObject(
                            "select coalesce(sum(" + src.qtyCol + "),0) from " + src.itemTable
                                    + " where " + src.itemFkCol + " = ?", BigDecimal.class, id);
                    sourceQty = q == null ? BigDecimal.ZERO : q;
                } catch (Exception e) {
                    log.warn("下推状态取来源数量失败 billType={} id={}", billType, id, e);
                }
            }

            if (sourceNo != null && !targets.isEmpty()) {
                for (BillLinkRegistry.TargetDef td : targets) {
                    try {
                        List<Map<String, Object>> heads = jdbcTemplate.queryForList(td.sql, sourceNo);
                        int billCount = 0;
                        BigDecimal effectiveQty = BigDecimal.ZERO;
                        BigDecimal draftQty = BigDecimal.ZERO;
                        for (Map<String, Object> head : heads) {
                            Object st = head.get("st");
                            if (BillStatusUtils.isVoided(st)) continue;
                            billCount++;
                            if (td.detailSql == null) continue;
                            Long hid = toLong(head.get("id"));
                            if (hid == null) continue;
                            List<Map<String, Object>> lines = jdbcTemplate.queryForList(td.detailSql, hid);
                            for (Map<String, Object> line : lines) {
                                BigDecimal q = toDec(line.get("qty"));
                                if (q.signum() == 0) continue;
                                if (BillStatusUtils.isApprovedClass(st)) {
                                    effectiveQty = effectiveQty.add(q);
                                } else {
                                    draftQty = draftQty.add(q);
                                }
                            }
                        }
                        BigDecimal totalQty = effectiveQty.add(draftQty);
                        String state;
                        if (billCount == 0) {
                            state = STATE_NONE;
                        } else if (td.detailSql == null) {
                            state = STATE_FULL;
                        } else if (sourceQty.signum() > 0 && totalQty.signum() > 0) {
                            state = totalQty.compareTo(sourceQty) >= 0 ? STATE_FULL : STATE_PARTIAL;
                        } else {
                            state = STATE_PARTIAL;
                        }
                        Map<String, Object> st = new LinkedHashMap<>();
                        st.put("state", state);
                        st.put("billCount", billCount);
                        st.put("effectiveQty", effectiveQty);
                        st.put("draftQty", draftQty);
                        st.put("sourceQty", sourceQty);
                        st.put("remainingQty", STATE_FULL.equals(state)
                                ? BigDecimal.ZERO
                                : sourceQty.subtract(totalQty).max(BigDecimal.ZERO));
                        st.put("name", td.name);
                        targetMap.put(td.docType, st);
                    } catch (Exception e) {
                        log.warn("下推目标状态汇总失败 billType={} target={} id={}", billType, td.docType, id, e);
                    }
                }
            }
            row.put("targets", targetMap);
            out.put(String.valueOf(id), row);
        }
        return out;
    }

    // ==================================================================================
    // 销售订单：发货（销售出库单） / 开票（销售发票） / 生产（生产工单，按表头来源单号匹配）
    // ==================================================================================

    private Map<String, Map<String, Object>> summarizeSaleOrder(List<Long> ids) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] idArgs = ids.toArray();

        // 1) 订单号（生产工单按"来源单号"匹配，只存单号没有主键） + 订单数量
        Map<Long, String> orderNoOf = new LinkedHashMap<>();
        Map<String, Long> orderIdByNo = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbcTemplate.queryForList(
                "select order_id, order_no from sale_order where order_id in (" + ph + ")", idArgs)) {
            Long id = ((Number) r.get("order_id")).longValue();
            String no = r.get("order_no") == null ? "" : String.valueOf(r.get("order_no"));
            orderNoOf.put(id, no);
            if (!no.isEmpty()) orderIdByNo.putIfAbsent(no, id);
        }
        Map<Long, BigDecimal> orderQtyOf = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbcTemplate.queryForList(
                "select order_id, coalesce(sum(quantity),0) as q from sale_order_item where order_id in ("
                        + ph + ") group by order_id", idArgs)) {
            orderQtyOf.put(((Number) r.get("order_id")).longValue(), toDec(r.get("q")));
        }

        // 2) 发货情况：销售出库单（行级按明细来源主键勾稽；表头级兜底：明细没记来源主键但表头记了来源单号 → 只计张数）
        Agg delivery = new Agg();
        Set<Long> countedDeliveryIds = new LinkedHashSet<>();
        for (Map<String, Object> r : jdbcTemplate.queryForList(
                "select i.source_id as oid, h.delivery_id as bid, h.status as st, sum(i.quantity) as q"
                        + " from sale_delivery_item i join sale_delivery h on h.delivery_id = i.delivery_id"
                        + " where i.source_id in (" + ph + ") group by i.source_id, h.delivery_id, h.status", idArgs)) {
            Long oid = toLong(r.get("oid"));
            if (oid == null) continue;
            accumulateQty(delivery, oid, r.get("st"), toDec(r.get("q")));
            Long bid = toLong(r.get("bid"));
            if (bid != null) countedDeliveryIds.add(bid);
        }
        accumulateHeaderOnly(jdbcTemplate, delivery, countedDeliveryIds, orderIdByNo, "sale_delivery", "delivery_id");

        // 3) 开票情况：销售发票（勾稽方式同出库单）
        Agg invoice = new Agg();
        Set<Long> countedInvoiceIds = new LinkedHashSet<>();
        for (Map<String, Object> r : jdbcTemplate.queryForList(
                "select i.source_id as oid, h.invoice_id as bid, h.status as st, sum(i.quantity) as q"
                        + " from sale_invoice_item i join sale_invoice h on h.invoice_id = i.invoice_id"
                        + " where i.source_id in (" + ph + ") group by i.source_id, h.invoice_id, h.status", idArgs)) {
            Long oid = toLong(r.get("oid"));
            if (oid == null) continue;
            accumulateQty(invoice, oid, r.get("st"), toDec(r.get("q")));
            Long bid = toLong(r.get("bid"));
            if (bid != null) countedInvoiceIds.add(bid);
        }
        accumulateHeaderOnly(jdbcTemplate, invoice, countedInvoiceIds, orderIdByNo, "sale_invoice", "invoice_id");

        // 4) 生产情况：生产工单（2026-10-28：行级优先——成品明细 source_item_id 指回订单明细；表头来源单号兜底）
        Map<Long, ProdAgg> prodOf = new LinkedHashMap<>();
        Map<Long, Set<Long>> prodSeen = new LinkedHashMap<>();
        if (!orderIdByNo.isEmpty()) {
            // 4.1 行级：成品明细行直接指回订单明细（手动选单/下推都走这条）
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "select soi.order_id as oid, w.order_id as wid, w.status as st, w.close_status as cs"
                            + " from work_order_product wop"
                            + " join production_order w on w.order_id = wop.order_id"
                            + " join sale_order_item soi on soi.order_item_id = wop.source_item_id"
                            + " where soi.order_id in (" + ph + ")", idArgs)) {
                Long oid = toLong(r.get("oid"));
                Long wid = toLong(r.get("wid"));
                if (oid == null || wid == null) continue;
                if (!prodSeen.computeIfAbsent(oid, k -> new LinkedHashSet<>()).add(wid)) continue;
                ProdAgg pa = prodOf.computeIfAbsent(oid, k -> new ProdAgg());
                if (BillStatusUtils.isVoided(r.get("st"))) continue;
                pa.billCount++;
                if (isProductionDone(r.get("st"), r.get("cs"))) pa.doneCount++;
            }
            // 4.2 表头兜底：工单表头写 source_no + source_type='销售订单'（老数据/直推接口）
            List<String> nos = new ArrayList<>(orderIdByNo.keySet());
            String phNo = String.join(",", Collections.nCopies(nos.size(), "?"));
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "select w.order_id as wid, w.source_no as sno, w.status as st, w.close_status as cs from production_order w"
                            + " where w.source_no in (" + phNo + ") and w.source_type = '销售订单'",
                    nos.toArray())) {
                String no = r.get("sno") == null ? "" : String.valueOf(r.get("sno"));
                Long oid = orderIdByNo.get(no);
                Long wid = toLong(r.get("wid"));
                if (oid == null || wid == null) continue;
                if (!prodSeen.computeIfAbsent(oid, k -> new LinkedHashSet<>()).add(wid)) continue;
                ProdAgg pa = prodOf.computeIfAbsent(oid, k -> new ProdAgg());
                if (BillStatusUtils.isVoided(r.get("st"))) continue;
                pa.billCount++;
                if (isProductionDone(r.get("st"), r.get("cs"))) pa.doneCount++;
            }
        }

        // 5) 组装结果（key = 订单主键字符串）
        for (Long id : ids) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("delivery", qtyState(delivery.map.get(id), orderQtyOf.get(id)));
            m.put("invoice", qtyState(invoice.map.get(id), orderQtyOf.get(id)));
            ProdAgg pa = prodOf.get(id);
            Map<String, Object> prod = new LinkedHashMap<>();
            if (pa == null || pa.billCount == 0) {
                prod.put("state", STATE_NONE);
                prod.put("billCount", 0);
                prod.put("doneCount", 0);
            } else {
                prod.put("state", pa.doneCount >= pa.billCount ? STATE_FULL : STATE_PARTIAL);
                prod.put("billCount", pa.billCount);
                prod.put("doneCount", pa.doneCount);
            }
            m.put("production", prod);
            out.put(String.valueOf(id), m);
        }
        return out;
    }

    /** 数量型状态（发货/开票）：已生效数量 >= 订单数量 → FULL；有有效下游但未全覆盖 → PARTIAL；无 → NONE */
    private Map<String, Object> qtyState(Agg.PerOrder a, BigDecimal orderQty) {
        Map<String, Object> m = new LinkedHashMap<>();
        int billCount = a == null ? 0 : a.billCount;
        BigDecimal effQty = a == null ? BigDecimal.ZERO : a.effQty;
        BigDecimal oq = orderQty == null ? BigDecimal.ZERO : orderQty;
        String state;
        if (billCount == 0) state = STATE_NONE;
        else if (oq.signum() > 0 && effQty.compareTo(oq) >= 0) state = STATE_FULL;
        else state = STATE_PARTIAL;
        m.put("state", state);
        m.put("billCount", billCount);
        m.put("pendingCount", a == null ? 0 : a.pendingBills);
        m.put("effectiveQty", effQty);
        m.put("orderQty", oq);
        return m;
    }

    private void accumulateQty(Agg agg, Long orderId, Object status, BigDecimal qty) {
        if (BillStatusUtils.isVoided(status)) return; // 已作废一律剔除
        Agg.PerOrder a = agg.map.computeIfAbsent(orderId, k -> new Agg.PerOrder());
        a.billCount++;
        if (BillStatusUtils.isApprovedClass(status)) a.effQty = a.effQty.add(qty == null ? BigDecimal.ZERO : qty);
        else a.pendingBills++; // 草稿/待审核：已下推未生效，只计张数不计数量
    }

    /** 表头级兜底：明细行没记来源主键、只有表头来源单号的下游单 → 计张数（不计数量，避免错算进度） */
    private void accumulateHeaderOnly(JdbcTemplate jdbc, Agg agg, Set<Long> countedBillIds,
                                      Map<String, Long> orderIdByNo, String headTable, String pkCol) {
        if (orderIdByNo.isEmpty()) return;
        List<String> nos = new ArrayList<>(orderIdByNo.keySet());
        String phNo = String.join(",", Collections.nCopies(nos.size(), "?"));
        try {
            for (Map<String, Object> r : jdbc.queryForList(
                    "select h." + pkCol + " as bid, h.status as st, h.source_bill_no as sno from " + headTable
                            + " h where h.source_bill_no in (" + phNo + ")", nos.toArray())) {
                Long bid = toLong(r.get("bid"));
                if (bid == null || countedBillIds.contains(bid)) continue; // 行级已统计过，防重复计张
                Long oid = orderIdByNo.get(r.get("sno") == null ? "" : String.valueOf(r.get("sno")));
                if (oid == null) continue;
                Object st = r.get("st");
                if (BillStatusUtils.isVoided(st)) continue;
                Agg.PerOrder a = agg.map.computeIfAbsent(oid, k -> new Agg.PerOrder());
                a.billCount++;
                if (!BillStatusUtils.isApprovedClass(st)) a.pendingBills++;
                // 已生效但明细没记来源主键：无法归集数量 → 不计 effectiveQty，状态最多 PARTIAL（宁可保守不虚报完成）
            }
        } catch (Exception e) {
            log.warn("下推状态表头级兜底查询失败 table={}", headTable, e);
        }
    }

    /** 生产工单是否完成：关闭标志=1，或状态为 已完工/已结案/已关闭 */
    private boolean isProductionDone(Object status, Object closeStatus) {
        if (closeStatus != null && ((Number) closeStatus).intValue() == 1) return true;
        String s = status == null ? "" : String.valueOf(status).trim();
        return s.equals("已完工") || s.equals("已结案") || s.equals("已关闭");
    }

    private static BigDecimal toDec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    /** 数量型聚合桶 */
    private static class Agg {
        final Map<Long, PerOrder> map = new LinkedHashMap<>();
        static class PerOrder {
            int billCount;
            int pendingBills;
            BigDecimal effQty = BigDecimal.ZERO;
        }
    }

    /** 生产工单聚合桶 */
    private static class ProdAgg {
        int billCount;
        int doneCount;
    }
}
