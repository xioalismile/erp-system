package com.erp.common.bill.link;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据关联查询服务（来源单据 / 去向单据）。
 *
 * <p>配置驱动：所有表名/列名/SQL 均来自 {@link BillLinkRegistry} 常量，接口入参只作绑定参数，
 * 不存在字符串拼接注入风险。单据类型未登记时直接返回空，不做任何动态查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillLinkService {

    private final JdbcTemplate jdbcTemplate;
    private final BillLinkRegistry registry;

    private static final int MAX_ROWS = 200;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 该单据类型是否**有可查看的来源或去向**（前端据此决定是否渲染「来源单据 / 去向单据」页签）。
     *
     * <p>2026-09-20 口径修正：原实现只判断"是否登记了 DocDef"，导致
     * {@code OPERATION_EXPENSE} / {@code PRODUCE_ASSEMBLE} / {@code PRODUCE_DISASSEMBLE}
     * 这类"已登记、但既没有来源列、也没有任何下游注册"的单据，页签照常渲染，
     * 用户点开永远是「暂无来源单据 / 暂无下游单据」——两个无意义的空页签。</p>
     *
     * <p>现按"真有链路"判断：</p>
     * <ul>
     *   <li>有来源 = DocDef 配了 {@code srcNoCol} 或 {@code srcIdCol}（与 {@link #sources} 的取数门槛一致）</li>
     *   <li>有去向 = {@code registerTargets} 里登记了该类型的下游</li>
     * </ul>
     * <p>未登记的类型同样返回 false（行为不变）；{@code sources} / {@code targets} 的返回体不变。</p>
     */
    public boolean isRegistered(String billType) {
        BillLinkRegistry.DocDef doc = registry.doc(billType);
        if (doc == null) return false;
        boolean hasSource = doc.srcNoCol != null || doc.srcIdCol != null;
        boolean hasTarget = !registry.targets(billType).isEmpty();
        return hasSource || hasTarget;
    }

    /**
     * 来源单据：当前单据的来源单（表头 + 明细），并给出每条来源明细的 下推数量/下推单价/累计已下推/未下推。
     */
    public Map<String, Object> sources(String billType, Long billId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registered", registry.isRegistered(billType));
        List<Map<String, Object>> list = new ArrayList<>();
        out.put("list", list);

        BillLinkRegistry.DocDef doc = registry.doc(billType);
        if (doc == null || billId == null) return out;

        try {
            // 2026-10-28：来源一律以明细行（含收款/付款的核销明细）为准；明细行没有才退回表头（历史数据兜底）
            for (SourceRef ref : resolveSourceRefs(doc, billId)) {
                Map<String, Object> dm = buildSourceDoc(billType, doc, billId, ref);
                if (dm != null) list.add(dm);
            }
        } catch (Exception e) {
            log.warn("查询来源单据失败 billType={} billId={} err={}", billType, billId, e.getMessage());
        }
        return out;
    }

    /** 一条来源引用：来源单据类型编码（可空）+ 来源单号 */
    private static final class SourceRef {
        final String docType;
        final String no;
        SourceRef(String docType, String no) { this.docType = docType; this.no = no; }
    }

    /**
     * 解析本单的来源引用（多来源）：
     * ① 优先取明细行的来源单号（收款单/付款单取核销明细；普通单据取商品明细）；
     * ② 明细行没有来源信息 → 退回表头来源单号/来源主键（历史单据兜底）。
     */
    private List<SourceRef> resolveSourceRefs(BillLinkRegistry.DocDef doc, Long billId) {
        List<SourceRef> refs = lineSourceRefs(doc, billId);
        if (!refs.isEmpty()) return refs;
        if (doc.srcNoCol == null && doc.srcIdCol == null) return refs;

        String headSql = "select " + (doc.srcNoCol != null ? doc.srcNoCol : "null") + " as srcNo, "
                + (doc.srcTypeCol != null ? doc.srcTypeCol : "null") + " as srcType, "
                + (doc.srcIdCol != null ? doc.srcIdCol : "null") + " as srcId"
                + " from " + doc.table + " where " + doc.pkCol + " = ?";
        List<Map<String, Object>> head = jdbcTemplate.queryForList(headSql, billId);
        if (head.isEmpty()) return refs;

        String srcNo = str(head.get(0).get("srcNo"));
        String srcType = str(head.get(0).get("srcType"));
        // 预收款/预付款这类只存来源主键的：用主键反查来源单号
        if (isBlank(srcNo) && doc.srcIdCol != null) {
            BillLinkRegistry.DocDef src = registry.doc(registry.docTypeByName(srcType));
            Long srcPkId = toLong(head.get(0).get("srcId"));
            if (src != null && srcPkId != null) {
                List<Map<String, Object>> sh = jdbcTemplate.queryForList(
                        "select " + src.noCol + " as no from " + src.table + " where " + src.pkCol + " = ?", srcPkId);
                if (!sh.isEmpty()) srcNo = str(sh.get(0).get("no"));
            }
        }
        if (!isBlank(srcNo)) refs.add(new SourceRef(registry.docTypeByName(srcType), srcNo));
        return refs;
    }

    /** 明细行里的来源引用（去重；只认有来源单号的行） */
    private List<SourceRef> lineSourceRefs(BillLinkRegistry.DocDef doc, Long docId) {
        List<SourceRef> out = new ArrayList<>();
        if (doc == null || docId == null || doc.srcRowsTable == null || doc.srcRowsFkCol == null) return out;
        String noCol = doc.srcRowsNoCol != null ? doc.srcRowsNoCol
                : firstColumn(doc.srcRowsTable, "source_bill_no", "source_no");
        if (noCol == null) return out;
        String typeCol = doc.srcRowsTypeCol != null ? doc.srcRowsTypeCol
                : firstColumn(doc.srcRowsTable, "source_bill_type", "source_type");
        StringBuilder sql = new StringBuilder("select distinct ").append(noCol).append(" as no")
                .append(typeCol != null ? ", " + typeCol + " as tp" : "")
                .append(" from ").append(doc.srcRowsTable)
                .append(" where ").append(doc.srcRowsFkCol).append(" = ?")
                .append(" and ").append(noCol).append(" is not null and ").append(noCol).append(" <> ''");
        if (doc.srcRowsFilter != null && !doc.srcRowsFilter.isBlank()) {
            sql.append(" and (").append(doc.srcRowsFilter).append(")");
        }
        sql.append(" limit 200");
        try {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (Map<String, Object> r : jdbcTemplate.queryForList(sql.toString(), docId)) {
                String no = str(r.get("no"));
                if (isBlank(no)) continue;
                String docType = registry.docTypeByName(str(r.get("tp")));
                String key = (docType == null ? "" : docType) + "|" + no;
                if (seen.add(key)) out.add(new SourceRef(docType, no));
            }
        } catch (Exception e) {
            log.warn("读取行级来源失败 table={} docId={} err={}", doc.srcRowsTable, docId, e.getMessage());
        }
        return out;
    }

    /** 组装一张来源单据（表头 + 明细 + 本单下推量/累计/剩余） */
    private Map<String, Object> buildSourceDoc(String billType, BillLinkRegistry.DocDef doc,
                                               Long billId, SourceRef ref) {
        String srcNo = ref.no;
        if (isBlank(srcNo)) return null;
        BillLinkRegistry.DocDef src = registry.doc(ref.docType);
        if (src == null) src = registry.doc(guessDocTypeByNo(srcNo));
        if (src == null) return null;

        List<Map<String, Object>> sHead = jdbcTemplate.queryForList(
                "select " + src.pkCol + " as id, " + src.noCol + " as no, "
                        + src.dateCol + " as dt, " + src.statusCol + " as st"
                        + " from " + src.table + " where " + src.noCol + " = ?", srcNo);
        if (sHead.isEmpty()) return null;
        Long srcId = toLong(sHead.get(0).get("id"));

        String turnedCol = src.turnedByTarget.get(billType);
        List<Map<String, Object>> sItems = new ArrayList<>();
        if (src.itemTable != null && src.itemFkCol != null) {
            StringBuilder itemSql = new StringBuilder("select ")
                    .append(src.itemPkCol).append(" as itemId, ")
                    .append(src.lineCol != null ? src.lineCol : "null").append(" as lineNo, ")
                    .append(src.itemMatCodeCol != null ? src.itemMatCodeCol + " as materialCode" : "null as materialCode").append(", ")
                    .append(src.itemMatNameCol != null ? src.itemMatNameCol + " as materialName" : "null as materialName").append(", ")
                    .append(src.itemSpecCol != null ? src.itemSpecCol + " as spec" : "null as spec").append(", ")
                    .append(src.qtyCol != null ? src.qtyCol : "null").append(" as srcQty, ")
                    .append(src.priceCol != null ? src.priceCol : "null").append(" as srcPrice, ")
                    .append(turnedCol != null ? turnedCol : "null").append(" as turnedTotal")
                    .append(" from ").append(src.itemTable)
                    .append(" where ").append(src.itemFkCol).append(" = ?");
            if (src.lineCol != null) itemSql.append(" order by ").append(src.lineCol);
            sItems = jdbcTemplate.queryForList(itemSql.toString(), srcId);
        }

        // 本单明细按行级来源主键归集；同表有来源单号列时只统计属于本来源单的行
        Map<Long, BigDecimal> pushedQtyBySrcItem = new HashMap<>();
        Map<Long, BigDecimal> pushedPriceBySrcItem = new HashMap<>();
        Map<String, BigDecimal> pushedQtyByMaterial = new HashMap<>();
        Map<String, BigDecimal> pushedPriceByMaterial = new HashMap<>();
        if (doc.itemTable != null && doc.itemFkCol != null && doc.srcItemCol != null) {
            try {
                String curNoCol = firstColumn(doc.itemTable, "source_bill_no");
                String sql = "select " + doc.srcItemCol + " as srcItemId, "
                        + (doc.itemMatCodeCol != null ? doc.itemMatCodeCol + " as materialCode" : "null as materialCode") + ", "
                        + (doc.qtyCol != null ? doc.qtyCol + " as qty" : "null as qty") + ", "
                        + (doc.priceCol != null ? doc.priceCol + " as price" : "null as price")
                        + " from " + doc.itemTable + " where " + doc.itemFkCol + " = ?";
                Object[] args;
                if (curNoCol != null) {
                    sql += " and (" + curNoCol + " = ? or " + curNoCol + " is null or " + curNoCol + " = '')";
                    args = new Object[]{billId, srcNo};
                } else {
                    args = new Object[]{billId};
                }
                for (Map<String, Object> r : jdbcTemplate.queryForList(sql, args)) {
                    Long sid = toLong(r.get("srcItemId"));
                    BigDecimal q = toDec(r.get("qty"));
                    BigDecimal p = toDec(r.get("price"));
                    if (sid != null) {
                        pushedQtyBySrcItem.merge(sid, q, BigDecimal::add);
                        pushedPriceBySrcItem.putIfAbsent(sid, p);
                    }
                    String mc = str(r.get("materialCode"));
                    if (mc != null) {
                        pushedQtyByMaterial.merge(mc, q, BigDecimal::add);
                        pushedPriceByMaterial.putIfAbsent(mc, p);
                    }
                }
            } catch (Exception ignore) {
                // 该明细表无 source_item_id 列时忽略（不影响表头级展示）
            }
        }
        boolean matchedByMaterial = pushedQtyBySrcItem.isEmpty() && !pushedQtyByMaterial.isEmpty();

        Map<String, Object> docMap = new LinkedHashMap<>();
        docMap.put("docType", src.docType);
        docMap.put("docName", src.name);
        docMap.put("id", srcId);
        docMap.put("no", str(sHead.get(0).get("no")));
        docMap.put("date", dateStr(sHead.get(0).get("dt")));
        docMap.put("status", str(sHead.get(0).get("st")));
        docMap.put("matchedByMaterial", matchedByMaterial);
        docMap.put("matchedByLine", true);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sumQty = BigDecimal.ZERO, sumPushedCur = BigDecimal.ZERO,
                sumTurned = BigDecimal.ZERO, sumRemain = BigDecimal.ZERO, sumAmount = BigDecimal.ZERO;
        for (Map<String, Object> it : sItems) {
            Long itemId = toLong(it.get("itemId"));
            String materialCode = str(it.get("materialCode"));
            BigDecimal srcQty = toDec(it.get("srcQty"));
            BigDecimal srcPrice = toDec(it.get("srcPrice"));
            BigDecimal pushedCur;
            BigDecimal pushedPrice;
            if (matchedByMaterial) {
                pushedCur = pushedQtyByMaterial.getOrDefault(materialCode, BigDecimal.ZERO);
                pushedPrice = pushedPriceByMaterial.get(materialCode);
            } else {
                pushedCur = pushedQtyBySrcItem.getOrDefault(itemId, BigDecimal.ZERO);
                pushedPrice = pushedPriceBySrcItem.get(itemId);
            }
            BigDecimal turned = it.get("turnedTotal") == null ? null : toDec(it.get("turnedTotal"));
            BigDecimal turnedTotal = turned != null ? turned : pushedCur;
            BigDecimal remain = srcQty.subtract(turnedTotal);
            if (remain.compareTo(BigDecimal.ZERO) < 0) remain = BigDecimal.ZERO;
            BigDecimal pushedAmount = pushedCur.multiply(pushedPrice != null ? pushedPrice : srcPrice);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", itemId);
            row.put("lineNo", it.get("lineNo"));
            row.put("materialCode", str(it.get("materialCode")));
            row.put("materialName", str(it.get("materialName")));
            row.put("spec", str(it.get("spec")));
            row.put("srcQty", srcQty);
            row.put("srcPrice", srcPrice);
            row.put("pushedQty", pushedCur);
            row.put("pushedPrice", pushedPrice != null ? pushedPrice : srcPrice);
            row.put("pushedAmount", pushedAmount);
            row.put("turnedTotal", turnedTotal);
            row.put("remainQty", remain);
            row.put("turnedColAvailable", turned != null);
            rows.add(row);

            sumQty = sumQty.add(srcQty);
            sumPushedCur = sumPushedCur.add(pushedCur);
            sumTurned = sumTurned.add(turnedTotal);
            sumRemain = sumRemain.add(remain);
            sumAmount = sumAmount.add(pushedAmount);
        }
        docMap.put("items", rows);

        Map<String, Object> total = new LinkedHashMap<>();
        total.put("srcQty", sumQty);
        total.put("pushedQty", sumPushedCur);
        total.put("turnedTotal", sumTurned);
        total.put("remainQty", sumRemain);
        total.put("pushedAmount", sumAmount);
        docMap.put("total", total);
        return docMap;
    }

    /** 表结构里第一个存在的列（信息架构缓存；不存在返回 null） */
    private String firstColumn(String table, String... candidates) {
        if (table == null) return null;
        java.util.Set<String> cols = tableColumns.computeIfAbsent(table, t -> {
            java.util.Set<String> s = new java.util.HashSet<>();
            try {
                for (Map<String, Object> r : jdbcTemplate.queryForList(
                        "select column_name from information_schema.columns where table_name = ?", t)) {
                    Object c = r.get("column_name");
                    if (c != null) s.add(String.valueOf(c).toLowerCase());
                }
            } catch (Exception ignore) { /* 表不存在时按无列处理 */ }
            return s;
        });
        for (String c : candidates) {
            if (c != null && cols.contains(c.toLowerCase())) return c;
        }
        return null;
    }

    private final Map<String, java.util.Set<String>> tableColumns = new java.util.concurrent.ConcurrentHashMap<>();

    /** 去向最大追溯层级（沿"来源→去向"逐级向下，防爆量） */
    private static final int MAX_DEPTH = 5;

    /**
     * 去向单据：从当前单据出发，沿"来源→去向"关系**逐级向下追溯**（多级，直到末级或达上限）。
     * 每行带 level（1=直接下游）与 parentNo/parentDocName（上级单据），展开明细里标注"对应上级行 / 上级剩余"。
     */
    public Map<String, Object> targets(String billType, Long billId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registered", registry.isRegistered(billType));
        List<Map<String, Object>> list = new ArrayList<>();
        out.put("list", list);

        BillLinkRegistry.DocDef doc = registry.doc(billType);
        if (doc == null || billId == null) return out;

        try {
            List<Map<String, Object>> head = jdbcTemplate.queryForList(
                    "select " + doc.noCol + " as no from " + doc.table + " where " + doc.pkCol + " = ?", billId);
            if (head.isEmpty()) return out;
            String no = str(head.get(0).get("no"));
            if (isBlank(no)) return out;

            java.util.Set<String> visited = new java.util.HashSet<>();
            visited.add(billType + "|" + no);
            // 2026-11-18：全局去重（同一张下游单据可能同时命中"表头来源"与"明细行来源"，只展示一次）
            java.util.Set<String> listed = new java.util.HashSet<>();
            collectTargets(doc, billId, no, 1, null, visited, listed, list);
        } catch (Exception e) {
            log.warn("查询去向单据失败 billType={} billId={} err={}", billType, billId, e.getMessage());
        }
        out.put("maxDepth", MAX_DEPTH);
        return out;
    }

    /**
     * 递归收集一层下游（BFS）。
     *
     * @param parentDoc   上级单据定义
     * @param parentId    上级单据主键（用于取上级明细做"对应上级行/剩余"标注）
     * @param parentNo    上级单据号（用于匹配下游）
     * @param level       本层层级（1=当前单据的直接下游）
     * @param parentLabel 上级单据展示名（null 时用 parentDoc.name）
     */
    private void collectTargets(BillLinkRegistry.DocDef parentDoc, Long parentId, String parentNo,
                                int level, String parentLabel, java.util.Set<String> visited,
                                java.util.Set<String> listed, List<Map<String, Object>> list) {
        if (level > MAX_DEPTH || list.size() >= MAX_ROWS) return;

        // 上级明细（按明细ID + 行号双索引），用于展开明细里标注"对应上级行 / 上级剩余"
        Map<Long, Map<String, Object>> pById = new LinkedHashMap<>();
        Map<String, Map<String, Object>> pByLine = new LinkedHashMap<>();
        Map<String, Map<String, Object>> pByMat = new LinkedHashMap<>();
        loadItems(parentDoc, parentId, pById, pByLine, pByMat);

        String pLabel = parentLabel != null ? parentLabel : parentDoc.name;
        List<Object[]> children = new ArrayList<>(); // {docType, id, no}
        Map<Map<String, Object>, Map<String, Object>> itemToParent = new java.util.IdentityHashMap<>();
        Map<String, BigDecimal> pushedByLine = new HashMap<>();

        for (BillLinkRegistry.TargetDef t : registry.targets(parentDoc.docType)) {
            if (list.size() >= MAX_ROWS) break;
            try {
                // 统一包一层以强制条数上限（SQL 为注册表常量，参数仍为绑定值）
                List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                        "select * from (" + t.sql + ") t limit " + MAX_ROWS, parentNo));
                // 2026-10-28：去向同样按"下游明细行的来源单号"补齐（表头 SQL 只覆盖历史/自动生成单据）
                rows.addAll(lineTargetHeads(t, parentDoc, parentNo));
                java.util.Set<Long> seenIds = new java.util.HashSet<>();
                for (Map<String, Object> r : rows) {
                    if (list.size() >= MAX_ROWS) break;
                    Long rowId = toLong(r.get("id"));
                    if (rowId != null && !seenIds.add(rowId)) continue;
                    String rowNo = str(r.get("no"));
                    // 全局去重：同一下游单据在多层/多来源命中时只保留首次出现
                    String listKey = t.docType + "|" + (rowId != null ? String.valueOf(rowId) : rowNo);
                    if (!listed.add(listKey)) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("docType", t.docType);
                    m.put("docName", t.name);
                    m.put("id", rowId);
                    m.put("no", rowNo);
                    m.put("date", dateStr(r.get("dt")));
                    m.put("status", str(r.get("st")));
                    m.put("amount", r.get("amt") == null ? null : toDec(r.get("amt")));
                    m.put("level", level);
                    m.put("parentDocName", pLabel);
                    m.put("parentNo", parentNo);
                    // 2026-11-18：下游单据表头来源类型（仅销售出库单目标补了该列）。
                    // 由销售换货单生成的出库单：标注「替代发货」并把上级改为换货单，避免在订单去向里
                    // 看起来跟普通下推出库单一样。
                    String srcType = str(r.get("srcType"));
                    if (!isBlank(srcType)) {
                        m.put("sourceBillType", srcType);
                        if (t.docType.equals("SALE_DELIVERY") && srcType.contains("销售换货单")) {
                            m.put("exchangeGenerated", true);
                            try {
                                List<Map<String, Object>> ex = jdbcTemplate.queryForList(
                                        "select exchange_no as no from sale_exchange where generated_delivery_id = ?",
                                        rowId);
                                if (!ex.isEmpty() && !isBlank(str(ex.get(0).get("no")))) {
                                    m.put("parentDocName", "销售换货单");
                                    m.put("parentNo", str(ex.get(0).get("no")));
                                }
                            } catch (Exception ignore) { /* 溯源失败不影响展示 */ }
                        }
                    }
                    List<Map<String, Object>> items = loadTargetItems(t, rowId);
                    for (Map<String, Object> it : items) {
                        Map<String, Object> cur = resolveParentLine(it.get("srcRef"), it.get("materialCode"),
                                pById, pByLine, pByMat);
                        if (cur != null) {
                            itemToParent.put(it, cur);
                            pushedByLine.merge(lineKey(cur), toDec(it.get("qty")), BigDecimal::add);
                        }
                    }
                    m.put("items", items);
                    m.put("qtyTotal", sumQty(items));
                    list.add(m);
                    if (rowId != null && !isBlank(rowNo)) children.add(new Object[]{t.docType, rowId, rowNo});
                }
            } catch (Exception e) {
                log.warn("查询去向单据失败 source={} target={} no={} err={}",
                        parentDoc.docType, t.docType, parentNo, e.getMessage());
            }
        }

        // 写回"对应上级行 / 上级物料 / 上级数量 / 上级剩余"（剩余=上级该行数量−该行跨全部下游已推，不为负）
        for (Map<String, Object> row : list) {
            Object itemsObj = row.get("items");
            if (!(itemsObj instanceof List)) continue;
            for (Object o : (List<?>) itemsObj) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> it = (Map<String, Object>) o;
                Map<String, Object> cur = itemToParent.get(it);
                if (cur == null) continue;
                BigDecimal qty = toDec(cur.get("qty"));
                BigDecimal pushed = pushedByLine.getOrDefault(lineKey(cur), BigDecimal.ZERO);
                BigDecimal remain = qty.subtract(pushed);
                if (remain.compareTo(BigDecimal.ZERO) < 0) remain = BigDecimal.ZERO;
                it.put("srcLineNo", cur.get("lineNo"));
                it.put("srcMaterialCode", cur.get("materialCode"));
                it.put("srcQty", qty);
                it.put("srcRemainQty", remain);
            }
        }

        // 递归下一级（未登记 / 已访问过的单据不再展开）
        for (Object[] c : children) {
            if (list.size() >= MAX_ROWS) break;
            String cType = (String) c[0];
            BillLinkRegistry.DocDef cd = registry.doc(cType);
            if (cd == null) continue;
            String key = cType + "|" + c[2];
            if (!visited.add(key)) continue;
            collectTargets(cd, (Long) c[1], (String) c[2], level + 1, null, visited, listed, list);
        }
    }

    /**
     * 按"下游明细行的来源单号"找下游单据（2026-10-28 统一口径）：
     * 与表头来源单号的结果合并去重，手动选来源单（只写明细、不写表头）也能出现在原单的去向里。
     */
    private List<Map<String, Object>> lineTargetHeads(BillLinkRegistry.TargetDef t,
                                                      BillLinkRegistry.DocDef parentDoc, String parentNo) {
        List<Map<String, Object>> out = new ArrayList<>();
        BillLinkRegistry.DocDef td = registry.doc(t.docType);
        if (td == null || td.srcRowsTable == null || td.srcRowsFkCol == null || isBlank(parentNo)) return out;
        String noCol = td.srcRowsNoCol != null ? td.srcRowsNoCol
                : firstColumn(td.srcRowsTable, "source_bill_no", "source_no");
        if (noCol == null) return out;

        StringBuilder sql = new StringBuilder("select distinct tg.").append(td.pkCol).append(" as id from ")
                .append(td.table).append(" tg join ").append(td.srcRowsTable).append(" r on r.")
                .append(td.srcRowsFkCol).append(" = tg.").append(td.pkCol)
                .append(" where r.").append(noCol).append(" = ?");
        if (td.srcRowsFilter != null && !td.srcRowsFilter.isBlank()) {
            sql.append(" and (").append(td.srcRowsFilter).append(")");
        }
        sql.append(" limit ").append(MAX_ROWS);

        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : jdbcTemplate.queryForList(sql.toString(), parentNo)) {
            Long id = toLong(r.get("id"));
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return out;

        String ph = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        // 2026-11-18：带回表头来源类型，供"替代发货"等特殊链路识别（DocDef 未登记来源类型列时返回 null）
        String srcTypeSel = td.srcTypeCol != null ? ", " + td.srcTypeCol + " as srcType" : "";
        String headSql = "select " + td.pkCol + " as id, " + td.noCol + " as no, " + td.dateCol + " as dt, "
                + td.statusCol + " as st, " + (td.amountCol != null ? td.amountCol : "null") + " as amt"
                + srcTypeSel + " from "
                + td.table + " where " + td.pkCol + " in (" + ph + ")";
        out.addAll(jdbcTemplate.queryForList(headSql, ids.toArray()));
        return out;
    }

    /** 载入某单据明细，构造 明细ID→行 与 行号→行 两个索引 */
    private void loadItems(BillLinkRegistry.DocDef doc, Long docId,
                           Map<Long, Map<String, Object>> byId, Map<String, Map<String, Object>> byLine,
                           Map<String, Map<String, Object>> byMat) {
        if (doc == null || docId == null || doc.itemTable == null || doc.itemFkCol == null) return;
        try {
            List<Map<String, Object>> cur = jdbcTemplate.queryForList(
                    "select " + doc.itemPkCol + " as id, "
                            + (doc.lineCol != null ? doc.lineCol : "null") + " as lineNo, "
                            + (doc.itemMatCodeCol != null ? doc.itemMatCodeCol : "null") + " as materialCode, "
                            + (doc.qtyCol != null ? doc.qtyCol : "null") + " as qty"
                            + " from " + doc.itemTable + " where " + doc.itemFkCol + " = ?", docId);
            for (Map<String, Object> r : cur) {
                Long id = toLong(r.get("id"));
                if (id != null) byId.put(id, r);
                Object ln = r.get("lineNo");
                if (ln != null) byLine.put(String.valueOf(ln), r);
                Object mc = r.get("materialCode");
                if (mc != null && byMat != null) byMat.putIfAbsent(String.valueOf(mc), r);
            }
        } catch (Exception e) {
            log.trace("加载明细失败 table={} docId={} err={}", doc.itemTable, docId, e.getMessage());
        }
    }

    /**
     * 定位下游明细对应的上级明细行：
     * ① 优先按 srcRef（上游回写的行级引用：明细ID 或 行号）；② 无引用列时按物料编码兜底（与来源侧口径一致）。
     */
    private static Map<String, Object> resolveParentLine(Object ref, Object materialCode,
                                                         Map<Long, Map<String, Object>> byId,
                                                         Map<String, Map<String, Object>> byLine,
                                                         Map<String, Map<String, Object>> byMat) {
        Map<String, Object> cur = null;
        if (ref != null) {
            Long refId = toLong(ref);
            cur = refId != null ? byId.get(refId) : null;
            if (cur == null) cur = byLine.get(String.valueOf(ref));
        }
        if (cur == null && materialCode != null) cur = byMat.get(String.valueOf(materialCode));
        return cur;
    }

    /** 单号前缀 → 单据类型兜底识别（来源类型名为空或口径不统一时用） */
    private String guessDocTypeByNo(String no) {
        if (no == null) return null;
        String n = no.trim().toUpperCase();
        if (n.startsWith("QUT")) return "SALE_QUOTE";
        if (n.startsWith("SCON-") || n.startsWith("SC-")) return "SALE_CONTRACT";
        if (n.startsWith("SO-")) return "SALE_ORDER";
        if (n.startsWith("SOUT")) return "SALE_DELIVERY";
        if (n.startsWith("SINV")) return "SALE_INVOICE";
        if (n.startsWith("SRT")) return "SALE_RETURN";
        // 生产链路（退料/补料表头没有 source_bill_type 时按单号前缀反查来源单据）
        if (n.startsWith("WO-")) return "PRODUCTION_ORDER";
        if (n.startsWith("MR-")) return "MATERIAL_REQUISITION";
        if (n.startsWith("ORP-")) return "OPERATION_REPORT";
        if (n.startsWith("PRS-")) return "PRODUCTION_RECEIPT";
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "null".equals(s);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return "null".equals(s) ? null : s;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toDec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String dateStr(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d.format(DATE_FMT);
        if (o instanceof LocalDateTime dt) return dt.format(DATE_FMT);
        if (o instanceof java.sql.Date d) return d.toLocalDate().format(DATE_FMT);
        if (o instanceof Timestamp ts) return ts.toLocalDateTime().format(DATE_FMT);
        String s = String.valueOf(o);
        return s.length() > 10 ? s.substring(0, 10) : s;
    }

    /** 取去向单据的物料级明细（明细 SQL 为注册表常量，绑定参数仅下游单据主键） */
    private List<Map<String, Object>> loadTargetItems(BillLinkRegistry.TargetDef t, Long rowId) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (t.detailSql == null || rowId == null) return items;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select * from (" + t.detailSql + ") t limit " + MAX_ROWS, rowId);
            for (Map<String, Object> r : rows) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("lineNo", pick(r, "lineno", "lineNo"));
                it.put("materialCode", pick(r, "materialcode", "materialCode"));
                it.put("materialName", pick(r, "materialname", "materialName"));
                it.put("spec", pick(r, "spec"));
                it.put("qty", pick(r, "qty"));
                it.put("price", pick(r, "price"));
                it.put("amount", pick(r, "amount"));
                // 下游明细指回本单行的引用（source_item_id / source_line_no / source_row_no 等），用于标注对应行
                it.put("srcRef", pick(r, "srcref", "srcRef"));
                items.add(it);
            }
        } catch (Exception e) {
            log.warn("查询去向明细失败 target={} id={} err={}", t.docType, rowId, e.getMessage());
        }
        return items;
    }

    /** 明细数量合计（qty 为空按 0） */
    private static BigDecimal sumQty(List<Map<String, Object>> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            Object q = it.get("qty");
            if (q != null) sum = sum.add(toDec(q));
        }
        return sum;
    }

    private static String lineKey(Map<String, Object> cur) {
        Object id = cur.get("id");
        return id != null ? ("id:" + id) : ("ln:" + cur.get("lineNo"));
    }

    /** 大小写键兼容取值（PostgreSQL 会把未加引号的别名折叠为小写） */
    private static Object pick(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (row.containsKey(k)) return row.get(k);
        }
        return null;
    }
}
