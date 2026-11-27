package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.common.scope.DataScopeResolver;
import com.erp.permission.service.FieldPermissionOutputFilter;
import com.erp.report.custom.core.CustomReportSqlBuilder;
import com.erp.report.custom.core.EntityMetadataRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 只读工具：按自定义报表数据源白名单做**聚合排行**（分组 + 汇总 + 排序 + 前 N）。
 *
 * <p>用途（用户 2026-11-22 明确要求）：「物料出库排行榜」「客户销售金额排行」这类"算给你看"的表。
 * 只走自定义报表数据源白名单（与 report_list_datasources 同一份登记），分组/指标列必须在该
 * 数据源字段清单内——列名全部来自代码登记的常量，不接用户输入的任何 SQL 片段；
 * 数据权限与自定义报表完全同口径（{@link CustomReportSqlBuilder} 的 created_by/业务归属列规则）。</p>
 *
 * <p>典型用法：数据源「销售出库明细」+ 分组「物料编码」+ 汇总「数量 求和」+ 前 10 → 物料出库排行榜。
 * 建议分组用带名称列的明细数据源（物料编码/名称、客户名称等是主表列，可直接分组出可读标签）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAggregateQueryTool implements AiTool {

    private static final List<String> AGGS = List.of("sum", "count", "avg", "max", "min");

    private final EntityMetadataRegistry registry;
    private final DataScopeResolver dataScopeResolver;
    private final JdbcTemplate jdbcTemplate;
    /** 字段权限：聚合项的来源字段被设为不可见时拒绝，避免"换个口径算出来"绕过 */
    private final FieldPermissionOutputFilter fieldPermissionOutputFilter;

    @Override
    public String getName() {
        return "report_aggregate_query";
    }

    @Override
    public String getDescription() {
        return "【只读】按数据源做**聚合排行表**：分组字段 + 汇总方式（求和/计数/平均/最大/最小）+ 排序 + 前 N 名。"
                + "用于「物料出库排行榜」「客户销售金额排行」「按部门统计领料数量」等。"
                + "数据源与字段先用 report_list_datasources 查（字段可用中文名或英文名）；"
                + "求和/平均只支持数值字段；数据权限与自定义报表一致。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("dataSource", Map.of("type", "string",
                "description", "数据源（report_list_datasources 返回的 key 或中文名），如「销售出库明细」"));
        props.put("groupField", Map.of("type", "string",
                "description", "分组字段（字段英文名或中文名），如 materialCode/物料编码、customerName/客户名称"));
        props.put("metricField", Map.of("type", "string",
                "description", "汇总字段（数值）；聚合方式为 count 时可省略"));
        props.put("agg", Map.of("type", "string",
                "description", "汇总方式：sum（求和，默认）/ count（计数）/ avg（平均）/ max / min"));
        props.put("topN", Map.of("type", "integer",
                "description", "取前 N 名，默认 10，最大 100"));
        props.put("order", Map.of("type", "string",
                "description", "排序：desc（降序，默认）/ asc（升序）"));
        props.put("dateField", Map.of("type", "string",
                "description", "可选：按日期字段过滤（字段英文名或中文名，如 deliveryDate/出库日期）"));
        props.put("dateFrom", Map.of("type", "string", "description", "日期起（YYYY-MM-DD，含）"));
        props.put("dateTo", Map.of("type", "string", "description", "日期止（YYYY-MM-DD，含）"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("dataSource", "groupField"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            if (params == null) {
                return fail("参数缺失：需要 dataSource 与 groupField");
            }
            // 1) 解析数据源（key 或中文名）
            String dsKey = str(params.get("dataSource"));
            EntityMetadataRegistry.DataSourceMeta ds = registry.getDataSource(dsKey);
            String dsName;
            if (ds == null && dsKey != null) {
                // 按中文名匹配（精确 → 包含，唯一命中才放行）
                EntityMetadataRegistry.DataSourceMeta matched = null;
                List<String> candidates = new ArrayList<>();
                for (Map<String, Object> m : registry.listDataSources()) {
                    String name = String.valueOf(m.get("name"));
                    if (name.equals(dsKey)) {
                        matched = registry.getDataSource(String.valueOf(m.get("key")));
                        break;
                    }
                    if (name.contains(dsKey.trim())) {
                        candidates.add(name + "(" + m.get("key") + ")");
                    }
                }
                if (matched == null && candidates.size() == 1) {
                    String key = candidates.get(0).substring(candidates.get(0).indexOf('(') + 1,
                            candidates.get(0).length() - 1);
                    matched = registry.getDataSource(key);
                }
                if (matched == null && !candidates.isEmpty()) {
                    return fail("数据源名不精确，命中最像的有多项：" + String.join("、", candidates));
                }
                ds = matched;
            }
            if (ds == null) {
                return fail("没有叫「" + dsKey + "」的数据源；请先用 report_list_datasources 查可用数据源");
            }
            dsName = ds.name;
            Set<String> hidden = hiddenKeysOf(ds, scope);

            // 2) 解析分组字段（白名单：必须在数据源字段清单内）
            EntityMetadataRegistry.FieldMeta group = resolveField(ds, str(params.get("groupField")));
            if (group == null) {
                return fail("数据源「" + dsName + "」没有字段「" + params.get("groupField") + "」；"
                        + "可用字段请用 report_list_datasources 传该数据源 key 查询");
            }
            if (isHidden(hidden, group)) {
                return fail("字段「" + group.label + "」在当前账号的字段权限中不可见，不能用于分组");
            }
            // 3) 解析聚合方式与指标字段
            String agg = str(params.get("agg"));
            agg = agg == null || agg.isBlank() ? "sum" : agg.trim().toLowerCase();
            if (!AGGS.contains(agg)) {
                return fail("不支持的汇总方式「" + agg + "」；可用：sum/count/avg/max/min");
            }
            EntityMetadataRegistry.FieldMeta metric = resolveField(ds, str(params.get("metricField")));
            boolean count = "count".equals(agg);
            if (!count && metric == null) {
                return fail("汇总方式 " + agg + " 需要传 metricField（数值字段）");
            }
            if (!count && isHidden(hidden, metric)) {
                return fail("字段「" + metric.label + "」在当前账号的字段权限中不可见，不能用于汇总");
            }
            if (!count && !isNumeric(metric)) {
                return fail("汇总字段「" + metric.label + "」不是数值字段（" + metric.type + "）；"
                        + "sum/avg/max/min 只支持数值字段，计数请用 agg=count");
            }
            String metricExpr = count ? "COUNT(*)" : agg.toUpperCase() + "(g." + metric.column + ")";

            // 4) 数据权限（与自定义报表完全同口径：created_by OR 业务归属列）
            StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
            List<Object> whereParams = new ArrayList<>();
            where.append(" AND g.").append(group.column).append(" IS NOT NULL ");
            appendDataPermission(ds, scope, where, whereParams);

            // 5) 可选日期过滤
            String dateFieldIn = str(params.get("dateField"));
            LocalDate from = parseDate(params.get("dateFrom"));
            LocalDate to = parseDate(params.get("dateTo"));
            if (dateFieldIn != null && (from != null || to != null)) {
                EntityMetadataRegistry.FieldMeta dateF = resolveField(ds, dateFieldIn);
                if (dateF == null) {
                    return fail("数据源「" + dsName + "」没有日期字段「" + dateFieldIn + "」");
                }
                if (isHidden(hidden, dateF)) {
                    return fail("字段「" + dateF.label + "」在当前账号的字段权限中不可见，不能用于日期筛选");
                }
                if (from != null) {
                    where.append(" AND g.").append(dateF.column).append(" >= CAST(? AS DATE) ");
                    whereParams.add(from);
                }
                if (to != null) {
                    where.append(" AND g.").append(dateF.column).append(" <= CAST(? AS DATE) ");
                    whereParams.add(to);
                }
            }

            int topN = intVal(params.get("topN"), 10);
            topN = Math.max(1, Math.min(topN, 100));
            String order = "desc".equalsIgnoreCase(str(params.get("order"))) || str(params.get("order")) == null
                    || str(params.get("order")).isBlank() ? "DESC" : "ASC";

            // 6) 聚合 SQL（表名/列名全部来自登记常量；值全部参数绑定）
            String sql = "SELECT g." + group.column + " AS label, " + metricExpr + " AS value"
                    + " FROM " + ds.table + " g" + where
                    + " GROUP BY g." + group.column
                    + " ORDER BY value " + order + ", label ASC"
                    + " LIMIT " + topN;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, whereParams.toArray());

            StringBuilder sb = new StringBuilder("【").append(dsName).append("】")
                    .append("按「").append(group.label).append("」")
                    .append(count ? "计数" : aggLabel(agg) + "「" + metric.label + "」")
                    .append(from != null || to != null ? "（日期 " + (from == null ? "…" : from) + " ~ " + (to == null ? "…" : to) + "）" : "")
                    .append("排行 Top ").append(topN)
                    .append("，共 ").append(rows.size()).append(" 组");
            int shown = Math.min(rows.size(), 10);
            for (int i = 0; i < shown; i++) {
                sb.append("\n").append(i + 1).append(". ").append(rows.get(i).get("label"))
                        .append(" = ").append(rows.get(i).get("value"));
            }
            if (rows.size() > shown) {
                sb.append("\n（其余 ").append(rows.size() - shown).append(" 组见明细表）");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("message", sb.toString());
            out.put("data", rows);
            out.put("total", rows.size());
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("label", group.label);
            labels.put("value", count ? "计数" : aggLabel(agg) + "「" + metric.label + "」");
            out.put("labels", labels);
            out.put("dataSource", dsName);
            return out;
        } catch (DateTimeParseException e) {
            return fail("日期格式应为 YYYY-MM-DD，收到：" + e.getParsedString());
        } catch (Exception e) {
            log.error("[AI聚合排行] 查询失败", e);
            return fail("查询失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 数据权限：与 CustomReportSqlBuilder.applyDataPermission 同一口径
     * （created_by IN 部门/本人 OR 业务归属列；缺列时保持不过滤并 WARN，与引擎一致）。
     */
    private void appendDataPermission(EntityMetadataRegistry.DataSourceMeta ds, AiDataScope scope,
                                      StringBuilder where, List<Object> params) {
        DataScopeResolver.DataScopeContext ctx = resolveScope(scope);
        if (ctx == null || ctx.isAll()) {
            return;
        }
        EntityMetadataRegistry.FieldMeta created = findByColumn(ds, "created_by");
        String ownerColumn = CustomReportSqlBuilder.ownerColumnOf(ds.table, ds.module);
        EntityMetadataRegistry.FieldMeta owner = ownerColumn == null ? null : findByColumn(ds, ownerColumn);
        if (created == null && owner == null) {
            log.warn("[AI聚合排行] 数据源 {} 无 created_by/归属列，数据权限未过滤", ds.table);
            return;
        }
        StringBuilder cond = new StringBuilder();
        if (DataScopeResolver.SCOPE_DEPT.equals(ctx.getScope())) {
            if (created != null) {
                if (!ctx.getDeptUsernames().isEmpty()) {
                    appendIn(cond, created.column, ctx.getDeptUsernames(), params);
                } else if (ctx.getUsername() != null) {
                    appendEq(cond, created.column, ctx.getUsername(), params);
                }
            }
            if (owner != null && !ctx.getDeptEmployeeIds().isEmpty()) {
                appendIn(cond, owner.column, ctx.getDeptEmployeeIds(), params);
            }
        } else {
            if (created != null && ctx.getUsername() != null) {
                appendEq(cond, created.column, ctx.getUsername(), params);
            }
            if (owner != null && ctx.getEmployeeId() != null) {
                appendEq(cond, owner.column, ctx.getEmployeeId(), params);
            }
        }
        if (cond.length() == 0) {
            where.append(" AND 1 = 0 ");
            return;
        }
        where.append(" AND (").append(cond).append(") ");
    }

    private DataScopeResolver.DataScopeContext resolveScope(AiDataScope scope) {
        if (scope == null || scope.getUserId() == null) {
            return dataScopeResolver.resolveForUser(null, null, null, null, Collections.emptyList());
        }
        return dataScopeResolver.resolveForUser(scope.getUsername(), scope.getUserId(),
                scope.getEmployeeId(), scope.getDeptId(), scope.getRoles());
    }

    /** 当前用户在该数据源（=主表 page_key）不可见的字段；小助手线程无请求上下文，按 scope 显式解析 */
    private Set<String> hiddenKeysOf(EntityMetadataRegistry.DataSourceMeta ds, AiDataScope scope) {
        if (scope != null && scope.getUserId() != null) {
            return fieldPermissionOutputFilter.hiddenKeysForUser(scope.getUserId(), scope.isAdmin(), ds.table);
        }
        return fieldPermissionOutputFilter.hiddenKeys(ds.table);
    }

    private static boolean isHidden(Set<String> hidden, EntityMetadataRegistry.FieldMeta fm) {
        return fm != null && (hidden.contains(fm.field) || hidden.contains(fm.column));
    }

    /** 字段解析：英文名（不分大小写）或中文名（精确），均须在数据源字段白名单内 */
    private EntityMetadataRegistry.FieldMeta resolveField(EntityMetadataRegistry.DataSourceMeta ds, String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        for (EntityMetadataRegistry.FieldMeta fm : ds.fields.values()) {
            if (fm.field.equalsIgnoreCase(n) || fm.column.equalsIgnoreCase(n)) return fm;
        }
        for (EntityMetadataRegistry.FieldMeta fm : ds.fields.values()) {
            if (fm.label != null && fm.label.equals(n)) return fm;
        }
        return null;
    }

    private EntityMetadataRegistry.FieldMeta findByColumn(EntityMetadataRegistry.DataSourceMeta ds, String column) {
        for (EntityMetadataRegistry.FieldMeta fm : ds.fields.values()) {
            if (fm.column.equalsIgnoreCase(column)) return fm;
        }
        return null;
    }

    private static boolean isNumeric(EntityMetadataRegistry.FieldMeta fm) {
        return "amount".equals(fm.type) || "number".equals(fm.type) || "int".equals(fm.type);
    }

    private static String aggLabel(String agg) {
        return switch (agg) {
            case "sum" -> "求和";
            case "count" -> "计数";
            case "avg" -> "平均";
            case "max" -> "最大";
            case "min" -> "最小";
            default -> agg;
        };
    }

    private static void appendEq(StringBuilder cond, String column, Object value, List<Object> params) {
        if (cond.length() > 0) cond.append(" OR ");
        cond.append("g.").append(column).append(" = ?");
        params.add(value);
    }

    private static void appendIn(StringBuilder cond, String column, List<?> values, List<Object> params) {
        if (cond.length() > 0) cond.append(" OR ");
        cond.append("g.").append(column).append(" IN (");
        cond.append(String.join(",", Collections.nCopies(values.size(), "?")));
        cond.append(")");
        params.addAll(values);
    }

    private static LocalDate parseDate(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return LocalDate.parse(String.valueOf(v).trim(), DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static int intVal(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
