package com.erp.report.detail;

import com.erp.common.scope.DataScopeResolver;
import com.erp.report.detail.BillDetailReportConfig.Col;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据明细报表引擎（销售订单 / 销售出库 / 销售发票 共用这一份实现）。
 *
 * <p><b>口径</b>
 * <ul>
 *   <li>一行 = 一条商品明细（单据 LEFT JOIN 明细：单据没有明细行时仍出一行表头，不丢单据）</li>
 *   <li>列清单 = 配置里的系统字段 + <b>自定义字段列</b>（配置指定的两个实体的启用字段）</li>
 *   <li>筛选条件 = <b>每一列都可作为筛选条件</b>，按列类型自动派生运算符；
 *       未显式登记筛选的列由引擎按类型派生，保证"新加一列就自动可筛"</li>
 *   <li>列清单为<b>白名单</b>：SELECT / WHERE / ORDER BY 只允许配置里登记过的表达式；
 *       筛选值一律 {@code ?} 参数化；自定义字段 field_key 过正则白名单</li>
 *   <li>行级数据权限：{@code created_by}（用户名）OR {@code saler_id}（员工），
 *       分支与 DataScopeContext.applyScopeInner 逐条对齐</li>
 *   <li>合计行：明细级列直接求和；单据级列<b>按单据去重后求和</b>（防一单多行重复累加）</li>
 *   <li>非法筛选值（非法日期 / 非法数字）一律忽略该条件，绝不阻断查询</li>
 * </ul>
 *
 * <p><b>筛选入参命名</b>：{@code f_<key>} / {@code f_<key>From|To}（日期区间）/ {@code f_<key>Min|Max}（数值区间）。
 * 具体入参名由 {@code /columns} 接口的 {@code paramKeys} 下发，前端不自己拼。
 */
@Slf4j
public class BillDetailReportEngine {

    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_EXPORT_ROWS = 100000;

    private static final String CF_KEY_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*$";

    protected final JdbcTemplate jdbcTemplate;
    private final DataScopeResolver dataScopeResolver;
    private BillDetailReportConfig cfg;
    /**
     * 配置按请求重建（多明细合并报表用）：这类配置要把各明细表的自定义字段并进列清单，
     * 而自定义字段定义按租户库不同，不能在启动时固化。单明细报表仍传固定配置（supplier=null）。
     */
    private final java.util.function.Supplier<BillDetailReportConfig> cfgSupplier;

    protected BillDetailReportEngine(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver,
                                     BillDetailReportConfig cfg) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeResolver = dataScopeResolver;
        this.cfg = cfg;
        this.cfgSupplier = null;
    }

    protected BillDetailReportEngine(JdbcTemplate jdbcTemplate, DataScopeResolver dataScopeResolver,
                                     java.util.function.Supplier<BillDetailReportConfig> cfgSupplier) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeResolver = dataScopeResolver;
        this.cfgSupplier = cfgSupplier;
        // 不在构造期取配置：那时没有请求租户上下文，自定义字段会读到错误的库
        this.cfg = null;
    }

    /** 每个对外查询入口刷新一次配置（仅 supplier 模式；固定配置时是空操作） */
    private void refreshCfg() {
        if (cfgSupplier != null) cfg = cfgSupplier.get();
    }

    // ==================== 列集合 ====================

    /** 一次请求内的列集合与索引 */
    private static final class ColumnSet {
        final List<Col> cols = new ArrayList<>();
        final Map<String, Col> byKey = new LinkedHashMap<>();

        void add(Col c) {
            cols.add(c);
            byKey.put(c.key, c);
        }

        List<Col> filters() {
            List<Col> out = new ArrayList<>();
            for (Col c : cols) if (c.filterType != null && c.expr != null && !c.expr.isBlank()) out.add(c);
            return out;
        }
    }

    private ColumnSet buildColumnSet() {
        ColumnSet cs = new ColumnSet();
        for (Col c : cfg.columns) {
            // 同 key 冲突软防护（2026-10-13）：key 是白名单 Map 的主键，重复时后者覆盖前者
            // （SELECT/列设置都会丢列）。修复靠配置唯一性，这里告警让问题在日志里可见。
            if (cs.byKey.containsKey(c.key)) {
                log.warn("[单据明细报表] 配置存在同 key 重复列: key={}, 后者覆盖前者（请改配置保证唯一）", c.key);
            }
            // 未显式登记筛选的列，按类型自动派生（"每一列都可作为筛选条件"）
            // 图片列不派生筛选条件（搜图片 URL 无业务意义）
            if (c.filterType == null) {
                deriveFilter(c);
            }
            cs.add(c);
        }
        for (Col c : loadCustomFieldColumns()) {
            if (!cs.byKey.containsKey(c.key)) cs.add(c);
        }
        return cs;
    }

    /** 未显式登记筛选的列按类型派生筛选运算符（"每一列都可作为筛选条件"） */
    static void deriveFilter(Col c) {
        if (c.filterType != null) return;
        switch (c.type) {
            case "date", "datetime" -> c.filter("dateRange", false);
            case "number", "amount", "int" -> c.filter("numberRange", false);
            case "image" -> { }
            default -> c.filter("text", false);
        }
    }

    /**
     * 自定义字段列：配置指定的单据实体 + 明细实体。
     * 值是 EAV 表 sys_custom_field_value 的相关子查询（与系统"按自定义字段搜索"同数据源）。
     * 任何异常一律降级为空列表，绝不阻断报表。
     */
    private List<Col> loadCustomFieldColumns() {
        return loadCustomFieldColumns(jdbcTemplate, cfg);
    }

    /**
     * 自定义字段列（按配置指定的单据/明细实体）。
     * 多明细合并配置（{@link BillDetailReportUnion}）也要用它把各来源的字段并进列清单，故开放为包内静态方法。
     */
    static List<Col> loadCustomFieldColumns(JdbcTemplate jdbcTemplate, BillDetailReportConfig cfg) {
        List<Col> out = new ArrayList<>();
        if (cfg.cfEntityDoc == null && cfg.cfEntityItem == null) return out;
        List<Map<String, Object>> defs;
        try {
            StringBuilder in = new StringBuilder();
            if (cfg.cfEntityDoc != null) in.append("'").append(cfg.cfEntityDoc).append("'");
            if (cfg.cfEntityItem != null) {
                if (in.length() > 0) in.append(", ");
                in.append("'").append(cfg.cfEntityItem).append("'");
            }
            defs = jdbcTemplate.queryForList(
                    "SELECT entity_type, field_key, field_label FROM sys_custom_field "
                  + " WHERE is_enabled = 1 AND entity_type IN (" + in + ") "
                  + " ORDER BY entity_type, sort_order, field_id");
        } catch (Exception e) {
            log.warn("[单据明细报表] 读取自定义字段定义失败，按无自定义字段处理: {}", e.getMessage());
            return out;
        }
        for (Map<String, Object> d : defs) {
            String entityType = str(d.get("entity_type"));
            String fieldKey = str(d.get("field_key"));
            String label = str(d.get("field_label"));
            if (!isSafeCfKey(fieldKey)) continue;
            boolean itemLevel = cfg.hasItems() && cfg.cfEntityItem != null && cfg.cfEntityItem.equals(entityType);
            String idExpr = itemLevel ? ("i." + cfg.itemIdColumn) : ("o." + cfg.docIdColumn);
            String valueExpr = "(SELECT v.field_value FROM sys_custom_field_value v"
                    + " WHERE v.entity_type = '" + entityType + "' AND v.entity_id = " + idExpr
                    + " AND v.field_key = '" + fieldKey + "')";
            String key = "cf_" + (itemLevel ? "item_" : "doc_") + fieldKey;
            Col c = new Col(key, (label == null || label.isBlank() ? fieldKey : label),
                    itemLevel ? BillDetailReportConfig.G_CF_ITEM : BillDetailReportConfig.G_CF_DOC,
                    "text", valueExpr, false, null, false);
            c.filterType = "cfText";
            c.filterExpr = idExpr;
            c.cfEntityType = entityType;
            c.cfFieldKey = fieldKey;
            c.filterDefault = false;
            out.add(c);
        }
        return out;
    }

    private static boolean isSafeCfKey(String k) {
        return k != null && k.matches(CF_KEY_PATTERN);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ==================== 元数据 ====================

    public Map<String, Object> describeColumns() {
        refreshCfg();
        ColumnSet cs = buildColumnSet();
        List<Map<String, Object>> cols = new ArrayList<>();
        int i = 0;
        for (Col c : cs.cols) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", c.key);
            m.put("label", c.label);
            m.put("group", c.group);
            m.put("type", c.type);
            // 默认可见 = 全部列（用户 2026-09-22 口径：报表明细默认全部显示，不需要的自己用列设置关）
            // 例外：cfg.reportHiddenKeys 里登记的列（列表页明细模式专用，如行级回写列）对报表默认隐藏，
            // 避免既有明细报表的默认视图被新列撑乱（守则 2.7.2-6）；用户仍可在列设置里手动勾出。
            m.put("visible", !cfg.reportHiddenKeys.contains(c.key));
            m.put("fixed", c.fixed);
            m.put("sortable", c.sortable);
            m.put("order", i++);
            cols.add(m);
        }
        List<Map<String, Object>> filters = new ArrayList<>();
        for (Col c : cs.filters()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", c.key);
            m.put("label", c.label);
            m.put("group", c.group);
            m.put("type", c.filterType);
            m.put("defaultChecked", c.filterDefault);
            m.put("optionSource", c.optionSource);
            List<String> opts = BillDetailReportConfig.staticOptions(c.optionSource);
            if (opts != null) m.put("options", opts);
            // 单据日期 = 默认时间口径：前端据此把该条件预置为「本月 1 日 ~ 今天」
            if (c.key.equals(cfg.docDateFilterKey)) m.put("defaultCurrentMonth", true);
            Map<String, Object> pk = new LinkedHashMap<>();
            switch (c.filterType) {
                case "dateRange" -> { pk.put("from", "f_" + c.key + "From"); pk.put("to", "f_" + c.key + "To"); }
                case "numberRange" -> { pk.put("min", "f_" + c.key + "Min"); pk.put("max", "f_" + c.key + "Max"); }
                default -> pk.put("value", "f_" + c.key);
            }
            m.put("paramKeys", pk);
            filters.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", cols);
        result.put("filters", filters);
        result.put("defaultVisible", allKeys(cs));
        result.put("defaultFilterChecked",
                cs.filters().stream().filter(c -> c.filterDefault).map(c -> c.key).toList());
        // 统计卡指标：由配置里实际存在的列决定（入库单/退货单/申请单没有"明细价税合计"列，
        // 就不该强行显示一张永远 ¥0.00 的卡片）—— 前端按这个数组渲染，不自造。
        List<Map<String, Object>> statsDefs = new ArrayList<>();
        statsDefs.add(statDef("orderCount", "单据张数", "int"));
        statsDefs.add(statDef("rowCount", "明细行数", "int"));
        if (cs.byKey.containsKey(BillDetailReportConfig.KEY_QUANTITY)) {
            statsDefs.add(statDef("totalQuantity", "商品数量合计", "number"));
        }
        if (cs.byKey.containsKey(BillDetailReportConfig.KEY_ITEM_NET_AMOUNT)) {
            statsDefs.add(statDef("totalNetAmount", "价税合计（明细）", "amount"));
        }
        result.put("stats", statsDefs);
        return result;
    }

    private static Map<String, Object> statDef(String key, String label, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        return m;
    }

    // ==================== FROM ====================

    private String fromSql() {
        StringBuilder sb = new StringBuilder(" FROM ")
                .append(cfg.mainTable).append(" o");
        // 无明细子表的单据（如工序汇报单）：不 JOIN 明细表，一行 = 一张单
        if (cfg.itemJoinSql != null && !cfg.itemJoinSql.isBlank()) {
            // 明细来自主表 JSON 数组等自定义片段（见 BillDetailReportConfig.itemJoinSql）
            sb.append(" ").append(cfg.itemJoinSql);
        } else if (cfg.hasItems()) {
            // 【2026-10-28 用户实报 BUG 修复】原来用 INNER JOIN：单据没有明细行就整张消失
            // （财务收款单/付款单的"明细"是核销明细，没核销过的单据一张都不出现 → 汇总 28 张、明细只剩 14 条）。
            // 口径统一为 LEFT JOIN：单据至少出一行，明细列留空；有明细行时照常一行一条明细。
            sb.append(" LEFT JOIN ").append(cfg.itemTable).append(" i ON i.")
              .append(cfg.itemFkColumn).append(" = o.").append(cfg.docIdColumn);
            // 明细表侧的类型区分条件必须放在 ON 里（写进 WHERE 会退化成 INNER JOIN）
            for (String f : cfg.itemFixedWhere) {
                if (f != null && !f.isBlank()) sb.append(" AND (").append(f).append(")");
            }
        }
        for (String j : cfg.extraJoins) sb.append(" ").append(j);
        return sb.toString();
    }

    // ==================== WHERE（筛选 + 数据权限） ====================

    private String buildWhere(Map<String, String> params, ColumnSet cs,
                              DataScopeResolver.DataScopeContext ctx, List<Object> out) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1 ");
        // 固定条件（配置里的常量；用于 bill_type 区分同表多单据的场景）
        for (String f : cfg.fixedWhere) {
            if (f != null && !f.isBlank()) w.append(" AND (").append(f).append(") ");
        }
        for (Col c : cs.filters()) {
            switch (c.filterType) {
                case "text" -> {
                    String v = params.get("f_" + c.key);
                    if (notBlank(v)) {
                        w.append(" AND ").append(c.filterExpr).append(" LIKE ? ");
                        out.add("%" + v.trim() + "%");
                    }
                }
                case "cfText" -> {
                    String v = params.get("f_" + c.key);
                    if (notBlank(v)) {
                        w.append(" AND EXISTS (SELECT 1 FROM sys_custom_field_value v")
                         .append(" WHERE v.entity_type = ? AND v.entity_id = ").append(c.filterExpr)
                         .append(" AND v.field_key = ? AND v.field_value ILIKE ?) ");
                        out.add(c.cfEntityType);
                        out.add(c.cfFieldKey);
                        out.add("%" + v.trim() + "%");
                    }
                }
                case "select" -> {
                    String v = params.get("f_" + c.key);
                    if (notBlank(v)) {
                        String label = v.trim();
                        String raw = BillDetailReportConfig.staticOptions(c.optionSource) == null
                                ? label : BillDetailReportConfig.staticOptionValue(c.optionSource, label);
                        Object bound = coerce(raw, c.valueKind);
                        if (bound != null) {
                            w.append(" AND ").append(c.filterExpr).append(" = ? ");
                            out.add(bound);
                        }
                    }
                }
                case "dateRange" -> {
                    // 必须显式 CAST + 先解析：① 字符串无法直接与 date/timestamp 比较（PG 报 bad SQL grammar）；
                    // ② 非法日期直接进 SQL 会 500 → 忽略该条件，绝不阻断查询
                    LocalDate fromD = parseDateOrNull(params.get("f_" + c.key + "From"));
                    LocalDate toD = parseDateOrNull(params.get("f_" + c.key + "To"));
                    if (fromD != null) {
                        w.append(" AND ").append(c.filterExpr).append(" >= CAST(? AS DATE) ");
                        out.add(fromD);
                    }
                    if (toD != null) {
                        w.append(" AND ").append(c.filterExpr).append(" <= CAST(? AS DATE) ");
                        out.add(toD);
                    }
                }
                case "numberRange" -> {
                    BigDecimal mn = decimalOrNull(params.get("f_" + c.key + "Min"));
                    BigDecimal mx = decimalOrNull(params.get("f_" + c.key + "Max"));
                    if (mn != null) {
                        w.append(" AND ").append(c.filterExpr).append(" >= ? ");
                        out.add(mn);
                    }
                    if (mx != null) {
                        w.append(" AND ").append(c.filterExpr).append(" <= ? ");
                        out.add(mx);
                    }
                }
                default -> { /* 未登记筛选 */ }
            }
        }
        w.append(dataScopeSql(ctx, out));

        // 行级主键直选（2026-10-12 列表页明细模式"勾选 N 条明细导出"用）：
        // 入参 _itemIds = 逗号分隔的明细主键；只保留能解析成数字的值，上限 1000 个，非法值一律忽略。
        String itemIdsRaw = params.get("_itemIds");
        if (notBlank(itemIdsRaw)) {
            List<Long> ids = new ArrayList<>();
            for (String s : itemIdsRaw.split(",")) {
                try { ids.add(Long.valueOf(s.trim())); } catch (NumberFormatException ignore) { }
                if (ids.size() >= 1000) break;
            }
            if (!ids.isEmpty()) {
                // 无明细子表的单据：行级主键即单据主键（一行 = 一张单）
                String idCol = (cfg.hasItems() ? "i." + cfg.itemIdColumn : "o." + cfg.docIdColumn);
                w.append(" AND ").append(idCol)
                 .append(" IN (").append(placeholders(ids.size())).append(")");
                out.addAll(ids);
            }
        }
        return w.toString();
    }

    private static Object coerce(String raw, String valueKind) {
        if (raw == null) return null;
        try {
            return switch (valueKind == null ? "string" : valueKind) {
                case "long" -> Long.valueOf(raw.trim());
                case "int" -> Integer.valueOf(raw.trim());
                default -> raw;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimalOrNull(String s) {
        if (!notBlank(s)) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String s) {
        if (!notBlank(s)) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 行级数据权限：与 DataScopeContext.applyScopeInner 的分支**逐条对齐**，
     * 但归属列/创建人列改为按单据配置（各单据口径不同，实测：销售用 saler_id、
     * 采购订单用 buyer_id、库存单据无归属维度、个别表创建人列叫 create_by）。
     *
     * <p>口径（与系统统一数据权限一致）：
     * <ul>
     *   <li>ALL → 不过滤</li>
     *   <li>DEPT → created_by ∈ 部门用户名集合（为空则 = 本人用户名）OR owner ∈ 部门员工集合（为空则 = 本人员工ID）</li>
     *   <li>其它（SELF）→ created_by = 本人用户名 OR owner = 本人员工ID</li>
     *   <li>两个维度都不可用、或都没有可绑定值时写 {@code 1 = 0}，条件恒假防越权</li>
     * </ul>
     */
    private String dataScopeSql(DataScopeResolver.DataScopeContext ctx, List<Object> params) {
        if (ctx == null || ctx.isAll()) return "";
        String cb = cfg.dataScopeCreatedByColumn;
        String ow = cfg.dataScopeOwnerColumn;
        if ((cb == null || cb.isBlank()) && (ow == null || ow.isBlank())) return "";

        StringBuilder sb = new StringBuilder(" AND (");
        boolean any = false;

        if (DataScopeResolver.SCOPE_DEPT.equals(ctx.getScope())) {
            if (cb != null && !cb.isBlank()) {
                if (!ctx.getDeptUsernames().isEmpty()) {
                    sb.append("o.").append(cb).append(" IN (").append(placeholders(ctx.getDeptUsernames().size())).append(")");
                    params.addAll(ctx.getDeptUsernames());
                    any = true;
                } else if (ctx.getUsername() != null) {
                    sb.append("o.").append(cb).append(" = ?");
                    params.add(ctx.getUsername());
                    any = true;
                }
            }
            if (ow != null && !ow.isBlank()) {
                if (!ctx.getDeptEmployeeIds().isEmpty()) {
                    if (any) sb.append(" OR ");
                    sb.append("o.").append(ow).append(" IN (").append(placeholders(ctx.getDeptEmployeeIds().size())).append(")");
                    params.addAll(ctx.getDeptEmployeeIds());
                    any = true;
                } else if (ctx.getEmployeeId() != null) {
                    if (any) sb.append(" OR ");
                    sb.append("o.").append(ow).append(" = ?");
                    params.add(ctx.getEmployeeId());
                    any = true;
                }
            }
        } else {
            if (cb != null && !cb.isBlank() && ctx.getUsername() != null) {
                sb.append("o.").append(cb).append(" = ?");
                params.add(ctx.getUsername());
                any = true;
            }
            if (ow != null && !ow.isBlank() && ctx.getEmployeeId() != null) {
                if (any) sb.append(" OR ");
                sb.append("o.").append(ow).append(" = ?");
                params.add(ctx.getEmployeeId());
                any = true;
            }
        }

        if (!any) sb.append("1 = 0");
        sb.append(")");
        return sb.toString();
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ==================== ORDER BY / SELECT ====================

    private String orderBySql(Map<String, String> params, ColumnSet cs) {
        String def = cfg.defaultOrderBy;
        String sortBy = params.get("sortBy");
        if (!notBlank(sortBy)) return def;
        Col c = cs.byKey.get(sortBy.trim());
        if (c == null || !c.sortable) return def;
        String dir = "asc".equalsIgnoreCase(params.get("sortOrder")) ? " ASC " : " DESC ";
        return " ORDER BY " + c.expr + dir + "NULLS LAST, " + cfg.docNoExpr + " DESC NULLS LAST "
                + (cs.byKey.containsKey("lineNo") ? ", i.line_no ASC NULLS LAST " : " ");
    }

    private String selectSql(List<String> keys, ColumnSet cs) {
        List<String> parts = new ArrayList<>();
        // 行级主键（前端"单号点击跳单据 / 勾选明细行导出"都靠它）；下划线前缀不会与业务列重名
        // 无明细子表的单据：行级主键 = 单据主键
        parts.add((cfg.hasItems() ? "i." + cfg.itemIdColumn : "o." + cfg.docIdColumn) + " AS \"_itemId\"");
        parts.add("o." + cfg.docIdColumn + " AS \"_docId\"");
        for (String k : keys) {
            Col c = cs.byKey.get(k);
            if (c == null) continue;
            parts.add(c.expr + " AS \"" + c.key + "\"");
        }
        return "SELECT " + String.join(", ", parts);
    }

    /**
     * 默认可见列 = **全部列**（含自定义字段列）。
     *
     * <p>用户 2026-09-22 明确口径：报表明细默认全部显示，不需要的由用户自己在「列设置」里关掉。
     * 因此这里不再按 Col.visible 过滤 —— 否则自定义字段列被默认隐藏，用户会以为"自定义字段的值没显示"
     * （实测就是这个原因：字段值链路本身是通的，只是列没默认勾上）。
     */
    private static List<String> allKeys(ColumnSet cs) {
        return cs.cols.stream().map(c -> c.key).toList();
    }

    public List<String> sanitizeKeys(List<String> keys) {
        refreshCfg();
        return sanitizeKeys(keys, buildColumnSet());
    }

    private List<String> sanitizeKeys(List<String> keys, ColumnSet cs) {
        List<String> out = new ArrayList<>();
        if (keys != null) {
            for (String k : keys) {
                if (k != null && cs.byKey.containsKey(k.trim()) && !out.contains(k.trim())) out.add(k.trim());
            }
        }
        // 前端没传 cols（例如直接调导出接口）时 = 全部列，与"默认全部显示"口径一致
        return out.isEmpty() ? allKeys(cs) : out;
    }

    public List<String> labelsOf(List<String> keys) {
        refreshCfg();
        ColumnSet cs = buildColumnSet();
        List<String> out = new ArrayList<>();
        for (String k : sanitizeKeys(keys, cs)) {
            Col c = cs.byKey.get(k);
            if (c != null) out.add(c.label);
        }
        return out;
    }

    public DataScopeResolver.DataScopeContext resolveScope(jakarta.servlet.http.HttpServletRequest req) {
        return dataScopeResolver.resolve(req);
    }

    /** 报表配置（AI 固定报表查询工具取单据日期筛选键等元数据用；只读视图） */
    public BillDetailReportConfig getConfig() {
        refreshCfg();
        return cfg;
    }

    // ==================== 对外查询 ====================

    public Map<String, Object> page(Map<String, String> params, int page, int size,
                                    DataScopeResolver.DataScopeContext ctx, List<String> keys) {
        refreshCfg();
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        ColumnSet cs = buildColumnSet();
        List<String> cols = sanitizeKeys(keys, cs);

        List<Object> whereParams = new ArrayList<>();
        String where = buildWhere(params, cs, ctx, whereParams);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + fromSql() + where,
                Long.class, whereParams.toArray());
        long totalRows = total == null ? 0L : total;

        List<Object> pageParams = new ArrayList<>(whereParams);
        pageParams.add(s);
        pageParams.add((p - 1) * s);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                selectSql(cols, cs) + fromSql() + where + orderBySql(params, cs) + " LIMIT ? OFFSET ?",
                pageParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", totalRows);
        result.put("page", p);
        result.put("size", s);
        result.put("stats", stats(params, cs, ctx));
        result.put("totals", totals(params, cs, ctx));
        return result;
    }

    /** 统计卡：单据张数按单号去重，避免一单多行被重复计数；缺列时该项以 0 计（不报错） */
    private Map<String, Object> stats(Map<String, String> params, ColumnSet cs,
                                      DataScopeResolver.DataScopeContext ctx) {
        List<Object> out = new ArrayList<>();
        String where = buildWhere(params, cs, ctx, out);
        String qtyExpr = exprOrZero(cs, BillDetailReportConfig.KEY_QUANTITY);
        String netExpr = exprOrZero(cs, BillDetailReportConfig.KEY_ITEM_NET_AMOUNT);
        String sql = "SELECT COUNT(*) AS \"rowCount\", COUNT(DISTINCT " + cfg.docNoExpr + ") AS \"orderCount\","
                + " " + qtyExpr + " AS \"totalQuantity\","
                + " " + netExpr + " AS \"totalNetAmount\""
                + fromSql() + where;
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, out.toArray());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rowCount", row.get("rowCount"));
        r.put("orderCount", row.get("orderCount"));
        r.put("totalQuantity", row.get("totalQuantity"));
        r.put("totalNetAmount", row.get("totalNetAmount"));
        return r;
    }

    /** 取列的求和表达式；该单据没有这一列时返回常量 0（统计卡按 /columns 的 stats 声明渲染，不会误导） */
    private static String exprOrZero(ColumnSet cs, String key) {
        Col c = cs.byKey.get(key);
        return c == null ? "0" : "COALESCE(SUM(" + c.expr + "),0)";
    }

    /** 合计行：明细级列直接求和 + 单据级列按单据去重求和 */
    private Map<String, Object> totals(Map<String, String> params, ColumnSet cs,
                                       DataScopeResolver.DataScopeContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();

        // ① 明细级：直接对明细行求和（配置里声明但本单据不存在的列自动跳过，避免 NPE → 500）
        List<String> itemKeys = existingKeys(cs, cfg.itemTotalKeys);
        if (!itemKeys.isEmpty()) {
            List<Object> itemParams = new ArrayList<>();
            String itemWhere = buildWhere(params, cs, ctx, itemParams);
            StringBuilder itemSel = new StringBuilder();
            for (String k : itemKeys) {
                if (itemSel.length() > 0) itemSel.append(", ");
                itemSel.append("COALESCE(SUM(").append(cs.byKey.get(k).expr).append("),0) AS \"").append(k).append("\"");
            }
            out.putAll(jdbcTemplate.queryForMap("SELECT " + itemSel + fromSql() + itemWhere, itemParams.toArray()));
        }

        // ② 单据级：先按单据去重（一单多行只算一次），再求和
        List<String> docKeys = existingKeys(cs, cfg.docTotalKeys);
        if (!docKeys.isEmpty()) {
            List<Object> docParams = new ArrayList<>();
            String docWhere = buildWhere(params, cs, ctx, docParams);
            StringBuilder inner = new StringBuilder("o.").append(cfg.docIdColumn);
            StringBuilder outer = new StringBuilder();
            int idx = 0;
            for (String k : docKeys) {
                inner.append(", ").append(cs.byKey.get(k).expr).append(" AS c").append(idx);
                if (outer.length() > 0) outer.append(", ");
                outer.append("COALESCE(SUM(t.c").append(idx).append("),0) AS \"").append(k).append("\"");
                idx++;
            }
            out.putAll(jdbcTemplate.queryForMap(
                    "SELECT " + outer + " FROM (SELECT DISTINCT " + inner + fromSql() + docWhere + ") t",
                    docParams.toArray()));
        }
        return out;
    }

    private static List<String> existingKeys(ColumnSet cs, List<String> keys) {
        List<String> out = new ArrayList<>();
        for (String k : keys) if (cs.byKey.containsKey(k)) out.add(k);
        return out;
    }

    /** 导出取数（不分页，套用同一 WHERE / ORDER BY，上限 MAX_EXPORT_ROWS） */
    public List<Map<String, Object>> exportRows(Map<String, String> params,
                                                DataScopeResolver.DataScopeContext ctx, List<String> keys) {
        refreshCfg();
        ColumnSet cs = buildColumnSet();
        List<String> cols = sanitizeKeys(keys, cs);
        List<Object> out = new ArrayList<>();
        String where = buildWhere(params, cs, ctx, out);
        return jdbcTemplate.queryForList(
                selectSql(cols, cs) + fromSql() + where + orderBySql(params, cs) + " LIMIT " + MAX_EXPORT_ROWS,
                out.toArray());
    }
}
