package com.erp.report.custom.core;

import com.erp.common.scope.DataScopeResolver;
import com.erp.common.util.SecurityUtils;
import com.erp.report.custom.core.EntityMetadataRegistry.DataSourceMeta;
import com.erp.report.custom.core.EntityMetadataRegistry.FieldMeta;
import com.erp.report.custom.core.EntityMetadataRegistry.JoinMeta;
import com.erp.report.custom.core.EntityMetadataRegistry.ResolvedColumn;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 自定义报表安全 SQL 构建器
 *
 * 安全约定：
 * 1. 表名/列名/别名/JOIN 条件全部来自 EntityMetadataRegistry 白名单，
 *    配置中的 source/field 必须先经解析校验，未命中直接抛异常拒绝
 * 2. 用户输入只作为 PreparedStatement 参数（? 占位）进入 SQL，绝不拼接
 * 3. 排序方向仅允许 asc/desc；聚合函数仅允许 SUM/COUNT/AVG/MAX/MIN，白名单硬编码
 * 4. 跨单据关联：目标数据源白名单 + 双方列名一致且在可关联外键集合内 +
 *    受限角色跨模块限制（sales-only 仅 sale/crm，purchase-only 仅 purchase）
 * 5. 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人），
 *    注入 (created_by OR 业务归属列) 条件，归属列口径与各标准报表/列表页一致；
 *    关联目标数据源的跨模块限制仍按受限角色（销售/采购）校验
 */
@Slf4j
@Component
public class CustomReportSqlBuilder {

    private final DataScopeResolver dataScopeResolver;
    private final ReportCustomFieldSupport customFieldSupport;

    public CustomReportSqlBuilder(DataScopeResolver dataScopeResolver,
                                  ReportCustomFieldSupport customFieldSupport) {
        this.dataScopeResolver = dataScopeResolver;
        this.customFieldSupport = customFieldSupport;
    }

    public static final int MAX_COLUMNS = 60;
    public static final int MAX_ORDER_BY = 5;
    public static final int MAX_EXPORT_ROWS = 100_000;
    public static final int MAX_GROUP_BY = 10;
    public static final int MAX_AGGREGATES = 30;
    public static final int MAX_EXTRA_JOINS = 20;
    public static final int MAX_CALC_COLS = 20;
    /** 单报表最多引用的自定义字段数（= 最多生成的 sys_custom_field_value LEFT JOIN 数） */
    public static final int MAX_CF_FIELDS = 40;
    /** 运行时任意字段过滤条件的数量上限 */
    public static final int MAX_CONDITIONS = 50;
    /** 单个聚合指标自己的条件数量上限 */
    public static final int MAX_AGG_FILTERS = 10;
    /** 单报表下钻目标数量上限 */
    public static final int MAX_DRILL_TARGETS = 4;
    /** in / notIn 单条件的值个数上限 */
    public static final int MAX_IN_VALUES = 50;

    /** 运行时过滤操作符白名单（与审批流程路由 matchSingleRule 同口径，另补 like/in/isNull 系） */
    private static final Set<String> COND_OPS = new HashSet<>(Arrays.asList(
            "eq", "ne", "gt", "ge", "lt", "le", "like", "notLike",
            "between", "in", "notIn", "isNull", "isNotNull"));

    /** 聚合函数白名单 */
    private static final Set<String> AGG_FUNCS = new HashSet<>(Arrays.asList("SUM", "COUNT", "AVG", "MAX", "MIN"));
    /** 图表类型白名单 */
    public static final Set<String> CHART_TYPES = new HashSet<>(Arrays.asList("bar", "pie", "line"));
    /** 计算列运算符白名单 */
    private static final Set<String> CALC_OPS = new HashSet<>(Arrays.asList("+", "-", "*", "/"));
    /** 计算列常量操作数：仅数字（小数/负数），防注入 */
    private static final Pattern NUMERIC_CONST = Pattern.compile("-?\\d+(\\.\\d+)?");

    // ==================== 构建结果 ====================

    /** 构建结果：SQL 片段 + 参数列表 */
    public static class BuiltQuery {
        public final String selectSql;      // 不含 LIMIT/OFFSET
        public final String countSql;
        public final List<Object> params;   // 与 selectSql 一致（count 同参数）
        public final List<Object> pageParams; // select + 分页参数
        public final String orderSql;       // ORDER BY 子句（已校验）
        public final List<ColDef> columns;  // 输出列定义（前端渲染/导出用）
        /** 汇总模式：整体合计行 SQL（无 GROUP BY），明细模式为 null */
        public final String totalsSql;
        /** 合计行参数（与 totalsSql 占位严格对齐），明细模式为 null */
        public final List<Object> totalsParams;

        public BuiltQuery(String selectSql, String countSql, List<Object> params,
                          List<Object> pageParams, String orderSql, List<ColDef> columns,
                          String totalsSql, List<Object> totalsParams) {
            this.selectSql = selectSql;
            this.countSql = countSql;
            this.params = params;
            this.pageParams = pageParams;
            this.orderSql = orderSql;
            this.columns = columns;
            this.totalsSql = totalsSql;
            this.totalsParams = totalsParams;
        }
    }

    public static class ColDef {
        public final String field;
        public final String label;
        public final String type;
        public ColDef(String field, String label, String type) {
            this.field = field;
            this.label = label;
            this.type = type;
        }
    }

    /** Join 上下文：完整 FROM+JOIN 与 source → 别名/字段 映射（含跨单 ext 源） */
    public static class JoinContext {
        /**
         * 完整 FROM+JOIN 片段。用 StringBuilder 而非 String：
         * 自定义字段的值表 LEFT JOIN 是在解析字段引用时按需追加的，必须是可变的同一个缓冲区。
         */
        public final StringBuilder from = new StringBuilder();
        public final Map<String, String> aliasBySource = new LinkedHashMap<>();
        public final Map<String, Map<String, FieldMeta>> fieldsBySource = new LinkedHashMap<>();
        /** source → 业务表名（自定义字段按表名查定义） */
        public final Map<String, String> tableBySource = new LinkedHashMap<>();
        /** source → 行主键列名（自定义字段值表 entity_id 关联用） */
        public final Map<String, String> pkColumnBySource = new LinkedHashMap<>();
        /** ext 前缀 → 目标数据源（跨模块权限校验已通过） */
        public final Map<String, DataSourceMeta> extTargets = new LinkedHashMap<>();
        /** 自定义字段 JOIN 缓存：source|ref → 值表别名 cfvN（去重，同一字段只 JOIN 一次） */
        public final Map<String, String> cfAliasByKey = new LinkedHashMap<>();
        /**
         * 待物化的自定义字段值表 JOIN（key=source|ref → JOIN 片段），由 {@link #fromSql()} 统一追加到末尾。
         *
         * <p><b>为什么延迟物化</b>：字段引用是在 SELECT/WHERE/ORDER/GROUP 各子句解析时才发现自定义字段的，
         * 而跨单关联的 leftField 解析又发生在 ext JOIN 生成之前。若解析时就往 FROM 里插 JOIN，
         * 会出现「JOIN 引用了尚未出现的表别名」→ SQL 非法；延迟到全部子句解析完再追加，
         * 此时所有别名（main/join/extN/extN.join/聚合子查询）都已就绪，与解析顺序完全无关。
         */
        public final Map<String, String> pendingCfJoins = new LinkedHashMap<>();
        /** 自定义字段值表别名自增序号 */
        public int cfAliasSeq = 0;
        /** 当前数据源 key：AI 创建的报表可能以数据源 key 引用主表字段（如 sale_order.orderNo），容错等同 main */
        public String dsKey;
        /** 主数据源表名与业务归属列（数据权限过滤用；ownerColumn=null 表示仅按 created_by 过滤） */
        public String table;
        public String ownerColumn;
        /**
         * FROM 子句内产生的参数（跨单聚合子查询里的 {@code FILTER (WHERE ...)} 占位值）。
         * SQL 文本顺序为 SELECT → FROM → WHERE，故调用方需把这里的参数排在 SELECT 参数之后、WHERE 参数之前。
         */
        public final List<Object> joinParams = new ArrayList<>();

        /** FROM+JOIN 最终串（所有子句拼装完成后取；自定义字段值表 JOIN 在此统一追加到末尾） */
        public String fromSql() {
            if (pendingCfJoins.isEmpty()) return from.toString();
            StringBuilder sb = new StringBuilder(from);
            for (String j : pendingCfJoins.values()) sb.append(j);
            return sb.toString();
        }
    }

    // ==================== 对外入口 ====================

    /**
     * 构建查询 SQL（明细 / 汇总模式自动分流）
     *
     * @param dsMeta  数据源元数据（白名单）
     * @param config  已解析的报表配置（字段引用将被校验）
     * @param req     当前请求（数据权限 + 跨模块关联限制）
     * @param filters 运行时筛选值：startDate/endDate/keyword/status（均可为空）
     */
    public BuiltQuery build(EntityMetadataRegistry registry, DataSourceMeta dsMeta, ReportConfig config,
                            HttpServletRequest req, RunFilters filters) {
        JoinContext ctx = buildJoinContext(registry, dsMeta, config, req);
        return config.isSummary()
                ? buildSummary(registry, ctx, config, req, filters)
                : buildDetail(ctx, config, req, filters);
    }

    /** 保存前校验：数据源白名单由 Service 校验，此处校验全部字段引用/聚合/关联/图表 */
    public void validateConfig(EntityMetadataRegistry registry, DataSourceMeta dsMeta, ReportConfig config,
                               HttpServletRequest req) {
        JoinContext ctx = buildJoinContext(registry, dsMeta, config, req);
        if (config.isSummary()) {
            List<Object> dryParams = new ArrayList<>();
            List<SummaryPart> parts = parseSummarySpec(ctx, config, dryParams);
            // 结果条件（HAVING）干跑校验：引用指标必须存在、比较方式必须合法
            buildHavingSql(config, parts, new ArrayList<>());
        } else {
            if (config.columns == null || config.columns.isEmpty()) {
                throw new IllegalArgumentException("请至少选择一个显示列");
            }
            if (config.columns.size() > MAX_COLUMNS) {
                throw new IllegalArgumentException("显示列超出上限 " + MAX_COLUMNS);
            }
            for (ReportConfig.ColRef c : config.columns) {
                resolveRef(ctx, c, "显示列");
            }
            if (config.calcColumns != null && !config.calcColumns.isEmpty()) {
                if (config.calcColumns.size() > MAX_CALC_COLS) {
                    throw new IllegalArgumentException("计算列超出上限 " + MAX_CALC_COLS);
                }
                // 干跑表达式构建完成校验（运算符白名单/字段数值/链式引用/常量合法性）
                Map<Integer, String> calcExprs = new LinkedHashMap<>();
                for (int i = 0; i < config.calcColumns.size(); i++) {
                    buildCalcExpr(ctx, config.calcColumns.get(i), i, calcExprs);
                }
            }
        }
        if (config.dateField != null) {
            ResolvedColumn rc = resolveRef(ctx, config.dateField, "日期筛选字段");
            if (!"date".equals(rc.meta.type) && !"datetime".equals(rc.meta.type)) {
                throw new IllegalArgumentException("日期筛选字段必须是日期类型");
            }
        }
        if (config.keywordField != null) resolveRef(ctx, config.keywordField, "关键字筛选字段");
        if (config.statusField != null) resolveRef(ctx, config.statusField, "状态筛选字段");
        // 默认筛选条件（config.filters）干跑校验：字段白名单/操作符/类型不合法在「保存时」即拦截，
        // 不让坏配置进库、到运行时才炸（SQL 文本丢弃，只用其校验副作用）
        if (config.filters != null && !config.filters.isEmpty()) {
            if (config.filters.size() > MAX_CONDITIONS) {
                throw new IllegalArgumentException("默认筛选条件超出上限 " + MAX_CONDITIONS);
            }
            appendConditions(ctx, config.filters, new StringBuilder(), new ArrayList<>());
        }
        validateOrderBy(ctx, config);
        if (config.chart != null && config.chart.type != null) {
            if (!CHART_TYPES.contains(config.chart.type)) {
                throw new IllegalArgumentException("图表类型不支持: " + config.chart.type);
            }
            if (!config.isSummary()) {
                throw new IllegalArgumentException("图表仅支持汇总模式");
            }
        }
        // 下钻目标：目标地址/传参必须完整（目标页面自身的权限仍由该页面校验）
        if (config.drill != null && !config.drill.isEmpty()) {
            if (config.drill.size() > MAX_DRILL_TARGETS) {
                throw new IllegalArgumentException("下钻目标超出上限 " + MAX_DRILL_TARGETS);
            }
            for (ReportConfig.DrillTarget d : config.drill) {
                if (d == null || isBlank(d.label) || isBlank(d.route) || isBlank(d.keyField) || isBlank(d.param)) {
                    throw new IllegalArgumentException("下钻目标配置不完整：名称、目标页面、传参字段、参数名均必填");
                }
                if (!d.route.trim().startsWith("/")) {
                    throw new IllegalArgumentException("下钻目标页面地址必须以 / 开头: " + d.route);
                }
            }
        }
    }

    // ==================== Join 上下文构建（含跨单关联） ====================

    /**
     * 构建 FROM+JOIN 与 source 映射：
     * - 主表 t1 + 主树 join（表头/基础资料，别名 t2..tN）
     * - 每个 extraJoin：目标主表别名 t{N+1}（ON=主侧列=目标列），并克隆目标 join 树
     *   source 命名："ext{i}"（目标主表）、"ext{i}.{joinKey}"（目标的表头/基础资料）
     */
    public JoinContext buildJoinContext(EntityMetadataRegistry registry, DataSourceMeta dsMeta,
                                        ReportConfig config, HttpServletRequest req) {
        int[] seq = {2};
        JoinContext ctx = new JoinContext();
        // from 直接引用 ctx.from（同一缓冲区）：自定义字段值表的 LEFT JOIN 会在解析引用时按需追加，
        // 追加位置必须在主表/主树 join 之后，故不能再用"本地 StringBuilder + 事后快照"的写法
        StringBuilder from = ctx.from;
        from.append(" FROM ").append(dsMeta.table).append(" t1 ");
        ctx.dsKey = dsMeta.key;
        ctx.table = dsMeta.table;
        ctx.ownerColumn = resolveOwnerColumn(dsMeta.table, dsMeta.module);
        ctx.aliasBySource.put("main", "t1");
        ctx.fieldsBySource.put("main", dsMeta.fields);
        ctx.tableBySource.put("main", dsMeta.table);
        ctx.pkColumnBySource.put("main", dsMeta.pkColumn);
        for (JoinMeta jm : dsMeta.joins.values()) {
            from.append("LEFT JOIN ").append(jm.table).append(" ").append(jm.alias)
                .append(" ON ").append(jm.onSql).append(" ");
            ctx.aliasBySource.put(jm.key, jm.alias);
            ctx.fieldsBySource.put(jm.key, jm.fields);
            ctx.tableBySource.put(jm.key, jm.table);
            // join 的 rightColumn 即目标表主键（基础资料 FK 关联 / 明细→表头关联均是此口径）
            ctx.pkColumnBySource.put(jm.key, jm.rightColumn);
            seq[0] = Math.max(seq[0], Integer.parseInt(jm.alias.substring(1)) + 1);
        }

        if (config.extraJoins != null && !config.extraJoins.isEmpty()) {
            if (config.extraJoins.size() > MAX_EXTRA_JOINS) {
                throw new IllegalArgumentException("跨单据关联超出上限 " + MAX_EXTRA_JOINS);
            }
            // 预扫：跨单关联的 leftField 可能是**主表/主树关联表上的自定义字段**（如"客户.区域"），
            // 它会出现在后续 ext JOIN 的 ON 里；而 ON 中被引用的表必须在该 JOIN 之前就已存在。
            // 因此先把这类 cf JOIN 物化进 FROM（此时主表/主树别名已就绪），再生成 ext JOIN。
            for (ReportConfig.ExtraJoin ej : config.extraJoins) {
                if (ej == null || ej.leftField == null || customFieldSupport == null) continue;
                String src = ej.leftField.source;
                boolean mainTree = src != null && !src.startsWith("ext");
                if (mainTree && customFieldSupport.isCustomRef(ej.leftField.field)) {
                    resolveRef(ctx, ej.leftField, "关联字段(主侧)");
                }
            }
            flushPendingCfJoins(ctx);
            Set<String> usedTargets = new HashSet<>();
            for (int i = 0; i < config.extraJoins.size(); i++) {
                ReportConfig.ExtraJoin ej = config.extraJoins.get(i);
                if (ej == null || ej.dataSourceKey == null || ej.leftField == null || ej.rightField == null) {
                    throw new IllegalArgumentException("跨单据关联配置不完整");
                }
                DataSourceMeta target = registry.getDataSource(ej.dataSourceKey);
                if (target == null) {
                    throw new IllegalArgumentException("关联数据源不在白名单内: " + ej.dataSourceKey);
                }
                // 自关联（目标=主表自身）仅聚合挂载模式放行：用于聚合主表自身明细（如订单数量=SUM(订单明细.quantity)）；
                // 平铺模式仍禁止（平铺自关联=纯行翻倍，无业务意义）
                boolean selfJoin = target.key.equals(dsMeta.key);
                boolean aggMountMode = ej.aggFields != null && !ej.aggFields.isEmpty();
                if (selfJoin && !aggMountMode) {
                    throw new IllegalArgumentException("平铺模式不允许关联主表自身"
                            + "（聚合挂载模式才允许关联主表自身以聚合其明细）: " + ej.dataSourceKey);
                }
                // 目标去重仅平铺模式需要（平铺重复JOIN=行翻倍）；聚合挂载每个子查询独立GROUP BY后1:1回连，
                // 同一目标可多次挂载（铁律：数量走明细/金额走主表必须拆两个关联，如发票明细数量+发票主表金额）
                if (!aggMountMode && !usedTargets.add(target.key)) {
                    throw new IllegalArgumentException("平铺模式关联数据源重复: " + ej.dataSourceKey);
                }
                // 受限角色跨模块限制：防销售/采购角色借 join 绕过数据隔离
                if (req != null && SecurityUtils.isSalesOnly(req)
                        && !"sale".equals(target.module) && !"crm".equals(target.module)) {
                    throw new IllegalArgumentException("销售角色仅支持关联销售/CRM模块数据源");
                }
                if (req != null && SecurityUtils.isPurchaseOnly(req) && !"purchase".equals(target.module)) {
                    throw new IllegalArgumentException("采购角色仅支持关联采购模块数据源");
                }
                // 左字段（主树内）解析：支持自定义字段（延迟物化的值表别名，见 pendingCfJoins）
                ResolvedColumn left = resolveRef(ctx, ej.leftField, "关联字段(主侧)");
                // 右字段（目标侧）解析：系统字段走注册表；自定义字段走内联相关子查询（ON 内无法引用后置 JOIN 的别名）
                boolean rightIsCf = customFieldSupport != null && customFieldSupport.isCustomRef(ej.rightField.field);
                FieldMeta rightFm = rightIsCf ? null : findFieldInTarget(target, ej.rightField);
                if (!rightIsCf && rightFm == null) {
                    throw new IllegalArgumentException("关联字段(目标侧)不在白名单内: "
                            + ej.dataSourceKey + "." + ej.rightField.source + "." + ej.rightField.field);
                }
                // 目标侧操作数（类型与别名无关，先按聚合子查询别名 t0 解析一次用于校验与聚合分支复用）
                TargetExpr rightExprAgg = rightIsCf
                        ? resolveTargetExpr(target, "t0", ej.rightField, "关联字段(目标侧)")
                        : null;
                // 关联条件校验：左右字段类型兼容（值匹配语义，不要求同名）
                String leftKind = typeKind(left.meta.type);
                String rightKind = typeKind(rightIsCf ? rightExprAgg.type : rightFm.type);
                if (!leftKind.equals(rightKind)) {
                    throw new IllegalArgumentException("关联字段类型不兼容："
                            + left.meta.label + "(" + left.meta.type + ") 与 "
                            + (rightIsCf ? rightExprAgg.label : rightFm.label)
                            + "(" + (rightIsCf ? rightExprAgg.type : rightFm.type)
                            + ")，请选择同类型字段（数值↔数值/文本↔文本/日期↔日期）");
                }
                // ---- 聚合挂载模式：目标单按关联键 GROUP BY 成子查询再挂回，杜绝 fan-out 失真 ----
                if (ej.via != null && (ej.aggFields == null || ej.aggFields.isEmpty())) {
                    throw new IllegalArgumentException("跳板(via)仅聚合挂载模式支持（平铺多跳会行翻倍）: "
                            + ej.dataSourceKey);
                }
                if (ej.aggFields != null && !ej.aggFields.isEmpty()) {
                    if (ej.aggFields.size() > MAX_AGGREGATES) {
                        throw new IllegalArgumentException("跨单聚合指标超出上限 " + MAX_AGGREGATES);
                    }
                    // 聚合子查询仅针对目标主表（rightField 必须在目标主表上）
                    if (!"main".equals(ej.rightField.source)
                            && !ej.rightField.source.equals(target.key)) {
                        throw new IllegalArgumentException("聚合挂载模式下关联字段(目标侧)必须是目标主表字段");
                    }
                    // ---- 两跳追溯（via）：目标单经跳板表回溯主表（如入库单→采购订单→销售订单） ----
                    DataSourceMeta viaDs = null;
                    FieldMeta viaTargetFm = null;
                    FieldMeta viaMainFm = null;
                    if (ej.via != null) {
                        if (ej.via.targetField == null || ej.via.mainField == null) {
                            throw new IllegalArgumentException("跳板(via)配置不完整：targetField/mainField 均必填");
                        }
                        viaDs = registry.getDataSource(ej.via.dataSourceKey);
                        if (viaDs == null) {
                            throw new IllegalArgumentException("跳板数据源不在白名单内: " + ej.via.dataSourceKey);
                        }
                        if (viaDs.key.equals(dsMeta.key) || viaDs.key.equals(target.key)) {
                            throw new IllegalArgumentException("跳板数据源不可与主表或目标表相同: "
                                    + ej.via.dataSourceKey);
                        }
                        // 受限角色跨模块限制（跳板表同样不得绕过数据隔离）
                        if (req != null && SecurityUtils.isSalesOnly(req)
                                && !"sale".equals(viaDs.module) && !"crm".equals(viaDs.module)) {
                            throw new IllegalArgumentException("销售角色仅支持关联销售/CRM模块数据源（含跳板）");
                        }
                        if (req != null && SecurityUtils.isPurchaseOnly(req)
                                && !"purchase".equals(viaDs.module)) {
                            throw new IllegalArgumentException("采购角色仅支持关联采购模块数据源（含跳板）");
                        }
                        viaTargetFm = viaDs.fields.get(ej.via.targetField);
                        viaMainFm = viaDs.fields.get(ej.via.mainField);
                        if (viaTargetFm == null || viaMainFm == null) {
                            throw new IllegalArgumentException("跳板关联字段不在白名单内: "
                                    + ej.via.dataSourceKey + "."
                                    + (viaTargetFm == null ? ej.via.targetField : ej.via.mainField));
                        }
                        // 类型兼容：目标.rightField↔跳板.targetField；主表.leftField↔跳板.mainField
                        if (!typeKind(rightFm.type).equals(typeKind(viaTargetFm.type))) {
                            throw new IllegalArgumentException("跳板与目标表关联字段类型不兼容："
                                    + rightFm.label + "(" + rightFm.type + ") 与 "
                                    + viaTargetFm.label + "(" + viaTargetFm.type + ")");
                        }
                        if (!typeKind(left.meta.type).equals(typeKind(viaMainFm.type))) {
                            throw new IllegalArgumentException("跳板与主表关联字段类型不兼容："
                                    + left.meta.label + "(" + left.meta.type + ") 与 "
                                    + viaMainFm.label + "(" + viaMainFm.type + ")");
                        }
                    }
                    // ---- 聚合子查询作用域：t0=目标主表；明细/表头/基础资料别名沿用注册表（子查询内独立作用域） ----
                    SubScope subScope = prepareSubScope(target, ej.aggFields);
                    JoinContext subCtx = new JoinContext();
                    subCtx.dsKey = target.key;
                    subCtx.aliasBySource.putAll(subScope.aliasBySource);
                    subCtx.fieldsBySource.putAll(subScope.fieldsBySource);
                    String subAlias = "ext" + i;
                    // 第一遍：解析每个聚合指标的来源（主表/明细表）与字段白名单
                    Map<String, FieldMeta> virtualFields = new LinkedHashMap<>();
                    Map<String, String> aggExprs = new LinkedHashMap<>(); // 输出列名 -> SQL 聚合表达式
                    Map<String, JoinMeta> usedDetails = new LinkedHashMap<>(); // 明细key -> join
                    Map<String, String> mainAggCols = new LinkedHashMap<>(); // 输出列名 -> 目标主表列名
                    boolean hasMainAgg = false;
                    boolean hasDetailAgg = false;
                    for (ReportConfig.AggField af : ej.aggFields) {
                        if (af == null) throw new IllegalArgumentException("跨单聚合指标配置不完整");
                        String func = af.func == null ? "" : af.func.trim().toUpperCase();
                        if (!AGG_FUNCS.contains(func)) {
                            throw new IllegalArgumentException("跨单聚合函数不支持: " + af.func + "（可用 SUM/COUNT/AVG/MAX/MIN）");
                        }
                        String srcKey = af.source == null ? "" : af.source.trim();
                        JoinMeta dj = null;
                        if (!srcKey.isEmpty()) {
                            dj = target.detailJoins.get(srcKey);
                            if (dj == null) {
                                throw new IllegalArgumentException("跨单聚合字段来源不在目标明细表白名单内: "
                                        + ej.dataSourceKey + "." + srcKey + "（可选明细表: "
                                        + String.join("/", target.detailJoins.keySet()) + "）");
                            }
                            hasDetailAgg = true;
                            usedDetails.putIfAbsent(srcKey, dj);
                        }
                        boolean countAll = "COUNT".equals(func) && (af.field == null || af.field.isEmpty());
                        FieldMeta fm = null;
                        if (!countAll) {
                            if (af.field == null) throw new IllegalArgumentException("跨单聚合指标缺少字段");
                            Map<String, FieldMeta> pool = dj == null ? target.fields : dj.fields;
                            fm = pool.get(af.field);
                            if (fm == null) {
                                throw new IllegalArgumentException("跨单聚合字段不在白名单内: "
                                        + ej.dataSourceKey + (srcKey.isEmpty() ? "" : "." + srcKey) + "." + af.field);
                            }
                            if (("SUM".equals(func) || "AVG".equals(func)) && !isNumericType(fm.type)) {
                                throw new IllegalArgumentException("跨单 SUM/AVG 仅支持数值（金额/数值）字段: " + fm.label);
                            }
                            if (dj == null) hasMainAgg = true;
                        }
                        // 主表字段与明细字段不可混配：明细 JOIN 扇出会放大主表字段的 SUM（COUNT 计单据数除外，DISTINCT 修正）
                        if (hasMainAgg && hasDetailAgg) {
                            throw new IllegalArgumentException("同一关联的聚合指标不可混配目标主表字段与明细表字段"
                                    + "（明细JOIN会放大主表SUM），请拆成两个关联分别挂载: " + ej.dataSourceKey);
                        }
                        // 虚拟字段名：优先用自定义输出名（同一字段多次聚合必须自定义），缺省 agg_{原字段} / agg_count
                        String vName = (af.alias != null && !af.alias.trim().isEmpty())
                                ? normalizeAlias(af.alias, "聚合输出名")
                                : (countAll ? "agg_count" : "agg_" + af.field);
                        if (virtualFields.containsKey(vName)) {
                            throw new IllegalArgumentException("跨单聚合指标重复: " + af.field);
                        }
                        String label = (af.label != null && !af.label.trim().isEmpty())
                                ? af.label.trim()
                                : ("COUNT".equals(func) && countAll ? "单据数"
                                    : funcName(func) + "(" + (fm == null ? "" : fm.label) + ")");
                        String outType = "COUNT".equals(func) ? "number"
                                : ("SUM".equals(func) || "AVG".equals(func))
                                    ? ("amount".equals(fm.type) ? "amount" : "number")
                                    : (fm == null ? "number" : fm.type);
                        virtualFields.put(vName, new FieldMeta(vName, vName, label, outType));
                        // 指标自带条件：在目标单据子查询内生效（如今年/前年、排除草稿作废）
                        ConditionSql aggFs = compileAggFilters(subCtx, af.filters, "聚合指标条件");
                        String aggFilter = aggFs.sql.isEmpty() ? "" : " FILTER (WHERE " + aggFs.sql + ")";
                        if (!aggFs.sql.isEmpty()) ctx.joinParams.addAll(aggFs.params);
                        // 聚合表达式：主表字段=裸列名（无明细JOIN）；明细字段=明细别名.列名；COUNT空字段=单据数
                        if (countAll) {
                            JoinMeta first = usedDetails.isEmpty() ? null : usedDetails.values().iterator().next();
                            aggExprs.put(vName, (first == null ? "COUNT(*)"
                                    : "COUNT(DISTINCT t0." + first.leftColumn + ")") + aggFilter);
                        } else {
                            // 主表字段统一带 t0. 前缀：子查询里可能补了表头/其它明细 JOIN，同名列不歧义
                            aggExprs.put(vName, func + "(" + (dj == null ? "t0." + fm.column : dj.alias + "." + fm.column) + ")"
                                    + aggFilter);
                            if (dj == null) {
                                mainAggCols.put(vName, fm.column); // 两跳时需补 t0. 前缀防同名列歧义
                            }
                        }
                    }
                    boolean hasVia = ej.via != null;
                    // 两跳+纯主表聚合：目标表带别名 t0，主表字段聚合补 t0. 前缀（跳板表可能有同名列）
                    if (hasVia && usedDetails.isEmpty()) {
                        for (Map.Entry<String, String> e : aggExprs.entrySet()) {
                            String col = mainAggCols.get(e.getKey());
                            if (col != null) {
                                aggExprs.put(e.getKey(),
                                        e.getValue().replace("(" + col + ")", "(t0." + col + ")"));
                            }
                        }
                    }
                    // 第二遍：拼子查询。统一使用 t0 别名（原「无明细无跳板 → 裸表名无别名」分支合并进来）：
                    // ① 便于挂自定义字段值表（需目标主键）② SQL 语义与合并前完全一致
                    // 关联键表达式：两跳用跳板列；否则用目标侧操作数（系统字段=列名 / 自定义字段=内联相关子查询）
                    String rightKeyExpr = rightIsCf ? rightExprAgg.expr : "t0." + rightFm.column;
                    StringBuilder subSelect = new StringBuilder("SELECT ");
                    StringBuilder subFrom = new StringBuilder();
                    String jkCol = hasVia ? "v." + viaMainFm.column : rightKeyExpr;
                    subSelect.append(jkCol).append(" AS jk");
                    // 暴露目标单主键（组内取极小值，用聚合函数包裹故不改变分组粒度）：供外层挂自定义字段值表
                    if (target.pkColumn != null) {
                        subSelect.append(", MIN(t0.").append(target.pkColumn).append(") AS cf_id");
                    }
                    for (Map.Entry<String, String> e : aggExprs.entrySet()) {
                        subSelect.append(", ").append(e.getValue()).append(" AS ").append(e.getKey());
                    }
                    subFrom.append(" FROM ").append(target.table).append(" t0");
                    for (JoinMeta dj : usedDetails.values()) {
                        subFrom.append(" JOIN ").append(dj.table).append(" ").append(dj.alias)
                               .append(" ON t0.").append(dj.leftColumn)
                               .append(" = ").append(dj.alias).append(".").append(dj.rightColumn);
                    }
                    // 指标条件引用到的表头/基础资料/其它明细表：补进子查询 FROM
                    for (String j : subScope.joins) {
                        subFrom.append(j);
                    }
                    for (String dk : subScope.filterDetailKeys) {
                        if (usedDetails.containsKey(dk)) continue;
                        JoinMeta dj = target.detailJoins.get(dk);
                        if (dj == null) continue;
                        subFrom.append(" JOIN ").append(dj.table).append(" ").append(dj.alias)
                               .append(" ON t0.").append(dj.leftColumn)
                               .append(" = ").append(dj.alias).append(".").append(dj.rightColumn);
                    }
                    if (hasVia) {
                        // 两跳：t0.目标关联列 = v.跳板单号列（如入库单.来源单号 = 采购订单.订单号）
                        subFrom.append(" JOIN ").append(viaDs.table).append(" v")
                               .append(" ON ").append(rightKeyExpr)
                               .append(" = v.").append(viaTargetFm.column);
                    }
                    subFrom.append(" GROUP BY ").append(jkCol);
                    from.append("LEFT JOIN (").append(subSelect).append(subFrom)
                        .append(") ").append(subAlias)
                        .append(" ON ").append(left.expr)
                        .append(" = ").append(subAlias).append(".jk ");
                    ctx.aliasBySource.put(subAlias, subAlias);
                    ctx.fieldsBySource.put(subAlias, virtualFields);
                    ctx.tableBySource.put(subAlias, target.table);
                    ctx.pkColumnBySource.put(subAlias, target.pkColumn == null ? null : "cf_id");
                    ctx.extTargets.put(subAlias, target);
                    continue;
                }
                // ---- 平铺模式（默认）：目标主表 + 克隆目标 join 树 ----
                String extMainAlias = "t" + (seq[0]++);
                TargetExpr rightExprFlat = rightIsCf
                        ? resolveTargetExpr(target, extMainAlias, ej.rightField, "关联字段(目标侧)")
                        : null;
                from.append("LEFT JOIN ").append(target.table).append(" ").append(extMainAlias)
                    .append(" ON ").append(left.expr)
                    .append(" = ").append(rightIsCf ? rightExprFlat.expr : extMainAlias + "." + rightFm.column)
                    .append(" ");
                String prefix = "ext" + i;
                ctx.aliasBySource.put(prefix, extMainAlias);
                ctx.fieldsBySource.put(prefix, target.fields);
                ctx.tableBySource.put(prefix, target.table);
                ctx.pkColumnBySource.put(prefix, target.pkColumn);
                ctx.extTargets.put(prefix, target);
                for (JoinMeta clone : registry.cloneJoinTree(target, prefix, extMainAlias, seq)) {
                    from.append("LEFT JOIN ").append(clone.table).append(" ").append(clone.alias)
                        .append(" ON ").append(clone.onSql).append(" ");
                    ctx.aliasBySource.put(clone.key, clone.alias);
                    ctx.fieldsBySource.put(clone.key, clone.fields);
                    ctx.tableBySource.put(clone.key, clone.table);
                    ctx.pkColumnBySource.put(clone.key, clone.rightColumn);
                }
            }
        }
        return ctx;
    }

    /** 聚合子查询作用域：t0=目标主表；其它表沿用注册表别名（子查询内独立作用域，不与外层冲突） */
    private static class SubScope {
        final Map<String, String> aliasBySource = new LinkedHashMap<>();
        final Map<String, Map<String, FieldMeta>> fieldsBySource = new LinkedHashMap<>();
        /** 需要补进子查询 FROM 的表头/基础资料 JOIN 片段 */
        final List<String> joins = new ArrayList<>();
        /** 聚合条件直接引用的明细表（若聚合指标本身没用它，也需要补 JOIN） */
        final Set<String> filterDetailKeys = new LinkedHashSet<>();
    }

    /**
     * 为聚合子查询准备字段解析作用域，并收集聚合条件引用到的关联表。
     * 明细条件只能引用目标主表、目标明细表、以及目标注册表内的表头/基础资料关联字段。
     */
    private SubScope prepareSubScope(DataSourceMeta target, List<ReportConfig.AggField> aggFields) {
        SubScope sc = new SubScope();
        sc.aliasBySource.put("main", "t0");
        sc.aliasBySource.put(target.key, "t0");
        sc.fieldsBySource.put("main", target.fields);
        sc.fieldsBySource.put(target.key, target.fields);
        for (JoinMeta dj : target.detailJoins.values()) {
            sc.aliasBySource.put(dj.key, dj.alias);
            sc.fieldsBySource.put(dj.key, dj.fields);
        }
        for (JoinMeta jm : target.joins.values()) {
            sc.aliasBySource.put(jm.key, jm.alias);
            sc.fieldsBySource.put(jm.key, jm.fields);
        }
        Set<String> needed = new LinkedHashSet<>();
        if (aggFields != null) {
            for (ReportConfig.AggField af : aggFields) {
                if (af == null || af.filters == null) continue;
                for (ReportConfig.Condition c : af.filters) {
                    if (c == null) continue;
                    String src = (c.source == null || c.source.isBlank()) ? "main" : c.source.trim();
                    if ("main".equals(src) || target.key.equals(src)) continue;
                    needed.add(src);
                }
            }
        }
        Set<String> included = new LinkedHashSet<>();
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String k : needed) {
            if (target.detailJoins.containsKey(k)) {
                sc.filterDetailKeys.add(k);
            } else {
                stack.push(k);
            }
        }
        while (!stack.isEmpty()) {
            String k = stack.pop();
            if (!included.add(k)) continue;
            JoinMeta jm = target.joins.get(k);
            if (jm == null) {
                throw new IllegalArgumentException("聚合指标条件的来源不支持: " + k);
            }
            if (jm.leftAlias != null && !"t1".equals(jm.leftAlias)) {
                String dep = null;
                for (JoinMeta x : target.joins.values()) {
                    if (jm.leftAlias.equals(x.alias)) {
                        dep = x.key;
                        break;
                    }
                }
                if (dep == null) {
                    throw new IllegalArgumentException("聚合指标条件的关联链不完整: " + k);
                }
                stack.push(dep);
            }
        }
        for (JoinMeta jm : target.joins.values()) {
            if (!included.contains(jm.key)) continue;
            String left = "t1".equals(jm.leftAlias) ? "t0" : jm.leftAlias;
            sc.joins.add(" JOIN " + jm.table + " " + jm.alias + " ON " + left + "." + jm.leftColumn
                    + " = " + jm.rightAlias + "." + jm.rightColumn + " ");
        }
        return sc;
    }

    /** 在目标数据源树内按 source/field 查找字段（source="main" 或目标树 joinKey） */
    private FieldMeta findFieldInTarget(DataSourceMeta target, ReportConfig.ColRef ref) {
        if (ref.source == null || ref.field == null) return null;
        if ("main".equals(ref.source)) return target.fields.get(ref.field);
        JoinMeta jm = target.joins.get(ref.source);
        return jm == null ? null : jm.fields.get(ref.field);
    }

    /** 字段类型归并（跨单关联类型兼容判断与计算列数值判断共用）；"int" 为历史兼容值，统一归 number */
    private static String typeKind(String type) {
        switch (type == null ? "" : type) {
            case "amount":
            case "number":
            case "int": return "number";
            case "string":
            // 自定义字段的下拉/开关底层存 text，比较语义即文本；归 string 才能与系统文本字段做关联键
            case "select":
            case "bool": return "string";
            case "date":
            case "datetime": return "date";
            default: return type == null ? "" : type;
        }
    }

    /** 数值类型判断（计算列用）：金额/数值（含历史 int 值）均可参与运算 */
    private static boolean isNumericType(String type) {
        return "amount".equals(type) || "number".equals(type) || "int".equals(type);
    }

    // ==================== 计算列（批次3：±×÷ + 链式 + 常量） ====================

    /**
     * 构建明细模式计算列 SQL 表达式（同时供 validateConfig 干跑校验复用）。
     * 链式引用说明：PostgreSQL 同层 SELECT 不可引用输出别名，
     * 故引用更早计算列时直接内联展开其表达式（calcExprs 缓存 1 基序号 → 表达式）。
     */
    private String buildCalcExpr(JoinContext ctx, ReportConfig.CalcCol cc, int idx, Map<Integer, String> calcExprs) {
        if (cc == null) throw new IllegalArgumentException("计算列配置不完整");
        // 自由公式（优先级最高）：[source.field] / [calcN] / 数字 / 四则 / 括号 / ROUND / ABS / COALESCE
        if (cc.formula != null && !cc.formula.trim().isEmpty()) {
            FormulaEngine.Result fr = FormulaEngine.compile(cc.formula, refName -> {
                String f = refName.trim();
                Integer n = calcIndex(f);
                if (n != null && f.toLowerCase().startsWith("calc")) {
                    String e = calcExprs.get(n);
                    if (e == null) {
                        throw new IllegalArgumentException("公式只能引用更早定义的计算列: " + f);
                    }
                    return new FormulaEngine.Resolved(e, "number");
                }
                ReportConfig.ColRef cr = new ReportConfig.ColRef();
                int dot = f.indexOf('.');
                if (dot > 0) {
                    cr.source = f.substring(0, dot);
                    cr.field = f.substring(dot + 1);
                } else {
                    cr.source = "main";
                    cr.field = f;
                }
                ResolvedColumn rc = resolveRef(ctx, cr, "公式字段");
                if (!isNumericType(rc.meta.type)) {
                    throw new IllegalArgumentException("公式仅支持数值（金额/数值）字段: " + rc.meta.label);
                }
                return new FormulaEngine.Resolved(rc.expr, rc.meta.type);
            }, false);
            calcExprs.put(idx + 1, fr.sql);
            return fr.sql;
        }
        String op = cc.op == null ? null : cc.op.trim();
        if (!CALC_OPS.contains(op)) {
            throw new IllegalArgumentException("计算列运算符仅支持 + - * /");
        }
        String exprA = calcOperandExpr(ctx, cc.refA, idx, calcExprs, "计算列字段A");
        String exprB = calcOperandB(ctx, cc, idx, calcExprs);
        String full;
        if ("/".equals(op)) {
            // 除数为 0 或 NULL 时结果为 NULL（前端显示空白），避免除零 SQL 异常
            full = "(CASE WHEN COALESCE(" + exprB + ",0) = 0 THEN NULL"
                    + " ELSE COALESCE(" + exprA + ",0) / COALESCE(" + exprB + ",0) END)";
        } else {
            full = "(COALESCE(" + exprA + ",0) " + op + " COALESCE(" + exprB + ",0))";
        }
        calcExprs.put(idx + 1, full);
        return full;
    }

    /** 计算列操作数 A/B 通用解析：白名单字段（数值）或更早计算列（source=calc, field=calcN） */
    private String calcOperandExpr(JoinContext ctx, ReportConfig.ColRef ref, int curIdx,
                                   Map<Integer, String> calcExprs, String what) {
        if (ref == null || ref.source == null || ref.field == null) {
            throw new IllegalArgumentException(what + "配置不完整");
        }
        if ("calc".equals(ref.source)) {
            Integer n = calcIndex(ref.field);
            // 仅允许引用更早定义的计算列（1 基序号 <= 当前 0 基序号），天然防循环引用
            if (n == null || n < 1 || n > curIdx || !calcExprs.containsKey(n)) {
                throw new IllegalArgumentException(what + "只能引用更早定义的计算列: " + ref.field);
            }
            return calcExprs.get(n);
        }
        ResolvedColumn rc = resolveRef(ctx, ref, what);
        if (!isNumericType(rc.meta.type)) {
            throw new IllegalArgumentException(what + "仅支持数值（金额/数值）字段: " + rc.meta.label);
        }
        return rc.expr;
    }

    /** 计算列操作数 B：字段引用优先，否则常量（仅数字字符，防注入） */
    private String calcOperandB(JoinContext ctx, ReportConfig.CalcCol cc, int idx, Map<Integer, String> calcExprs) {
        if (cc.refB != null && cc.refB.source != null && cc.refB.field != null) {
            return calcOperandExpr(ctx, cc.refB, idx, calcExprs, "计算列字段B");
        }
        if (cc.constB != null && NUMERIC_CONST.matcher(cc.constB.trim()).matches()) {
            return cc.constB.trim();
        }
        throw new IllegalArgumentException("计算列字段B或常量缺失/无效（常量仅支持数字）");
    }

    /** "calc3" → 3，非该格式返回 null */
    private static Integer calcIndex(String field) {
        if (field == null || !field.startsWith("calc") || field.length() <= 4) return null;
        try {
            return Integer.parseInt(field.substring(4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 计算列缺省显示名：A标签 opSymbol B标签（操作数可为字段/更早计算列/常量） */
    private String defaultCalcLabel(JoinContext ctx, ReportConfig.CalcCol cc, int idx,
                                    Map<Integer, String> calcLabels, String fallback) {
        try {
            String la = operandLabel(ctx, cc.refA, idx, calcLabels, "计算列字段A");
            String lb = (cc.refB != null && cc.refB.source != null && cc.refB.field != null)
                    ? operandLabel(ctx, cc.refB, idx, calcLabels, "计算列字段B")
                    : cc.constB;
            return la + opSymbol(cc.op) + lb;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String operandLabel(JoinContext ctx, ReportConfig.ColRef ref, int curIdx,
                                Map<Integer, String> calcLabels, String what) {
        if ("calc".equals(ref.source)) {
            Integer n = calcIndex(ref.field);
            return (n != null && calcLabels.containsKey(n)) ? calcLabels.get(n) : ref.field;
        }
        return resolveRef(ctx, ref, what).meta.label;
    }

    /** 计算列输出类型：任一操作数为 amount 则 amount（金额），否则 number */
    private String calcOutputType(JoinContext ctx, ReportConfig.CalcCol cc, int idx, Map<Integer, String> calcTypes) {
        if (isOperandAmount(ctx, cc.refA, calcTypes) || isOperandAmount(ctx, cc.refB, calcTypes)) return "amount";
        return "number";
    }

    private boolean isOperandAmount(JoinContext ctx, ReportConfig.ColRef ref, Map<Integer, String> calcTypes) {
        if (ref == null || ref.source == null || ref.field == null) return false;
        if ("calc".equals(ref.source)) {
            Integer n = calcIndex(ref.field);
            return n != null && "amount".equals(calcTypes.get(n));
        }
        try {
            return "amount".equals(resolveRef(ctx, ref, "计算列").meta.type);
        } catch (Exception e) {
            return false;
        }
    }

    /** 运算符显示符号（缺省列名用） */
    private static String opSymbol(String op) {
        switch (op == null ? "" : op) {
            case "+": return "+";
            case "-": return "-";
            case "*": return "×";
            case "/": return "÷";
            default: return " ";
        }
    }

    /** 统一字段引用解析：main / 主树 joinKey / extN / extN.joinKey；数据源 key 容错等同 main（AI 创建场景） */
    private ResolvedColumn resolveRef(JoinContext ctx, ReportConfig.ColRef c, String what) {
        if (c == null || c.source == null || c.field == null) {
            throw new IllegalArgumentException(what + "配置不完整");
        }
        String source = c.source;
        // AI/手工配置可能用数据源 key（如 sale_order）引用主表字段，与 main 等同（仅当该 key 未注册为其它源时）
        if (!ctx.aliasBySource.containsKey(source) && source.equals(ctx.dsKey)) {
            source = "main";
        }
        String alias = ctx.aliasBySource.get(source);
        Map<String, FieldMeta> fields = ctx.fieldsBySource.get(source);
        if (alias == null || fields == null) {
            throw new IllegalArgumentException(what + "不在白名单内: " + c.source + "." + c.field);
        }
        // 自定义字段（cf_ 前缀）：系统字段优先，未命中且带前缀才走自定义字段分支
        if (customFieldSupport != null && customFieldSupport.isCustomRef(c.field) && !fields.containsKey(c.field)) {
            return resolveCustomFieldRef(ctx, source, alias, c.field, what);
        }
        FieldMeta fm = fields.get(c.field);
        if (fm == null) {
            throw new IllegalArgumentException(what + "不在白名单内: " + c.source + "." + c.field);
        }
        return new ResolvedColumn(alias, fm);
    }

    /**
     * 解析自定义字段引用：按需追加 sys_custom_field_value 的 LEFT JOIN（同一字段只 JOIN 一次），
     * 返回该值表别名上的类型化取值表达式。
     *
     * <p>值表有唯一索引 {@code (entity_type, entity_id, field_key)}，LEFT JOIN 不会放大行数。
     * entity_type / field_key 来自字段定义表（非请求参数）且已过安全字符正则，此处仅作拼接常量使用；
     * 关联主键列取自注册表（main = 数据源 @TableId 列，join = JoinMeta.rightColumn）。
     */
    private ResolvedColumn resolveCustomFieldRef(JoinContext ctx, String source, String alias,
                                                 String ref, String what) {
        String table = ctx.tableBySource.get(source);
        String pk = ctx.pkColumnBySource.get(source);
        if (table == null || pk == null) {
            throw new IllegalArgumentException(what + "所在数据源不支持自定义字段: " + source + "." + ref);
        }
        ReportCustomFieldSupport.CfField cf = customFieldSupport.find(table, ref);
        if (cf == null) {
            throw new IllegalArgumentException(what + "不在白名单内: " + source + "." + ref);
        }
        String cacheKey = source + "|" + cf.ref;
        String cfAlias = ctx.cfAliasByKey.get(cacheKey);
        if (cfAlias == null) {
            if (ctx.cfAliasByKey.size() >= MAX_CF_FIELDS) {
                throw new IllegalArgumentException("自定义字段引用超出上限 " + MAX_CF_FIELDS);
            }
            cfAlias = "cfv" + (++ctx.cfAliasSeq);
            ctx.cfAliasByKey.put(cacheKey, cfAlias);
            // 延迟物化：此处只登记，fromSql() 在全部子句解析完后统一追加（避免引用了尚未出现的表别名）
            ctx.pendingCfJoins.put(cacheKey, "LEFT JOIN sys_custom_field_value " + cfAlias
                    + " ON " + cfAlias + ".entity_type = '" + cf.entityType + "'"
                    + " AND " + cfAlias + ".field_key = '" + cf.fieldKey + "'"
                    + " AND " + cfAlias + ".entity_id = " + alias + "." + pk + " ");
        }
        FieldMeta meta = new FieldMeta(ref, "field_value", cf.label, cf.type);
        return new ResolvedColumn(cfAlias, meta, customFieldSupport.valueExpr(cf, cfAlias));
    }

    /**
     * 自定义字段的**内联相关子查询**取值表达式（用于跨单关联的 ON 子句）。
     *
     * <p>为什么不能用别名 JOIN：该 ON 正在定义右表别名（如 t5），而值表 JOIN 又要按
     * {@code t5.<主键>} 关联 → 先 JOIN 值表会形成循环依赖（SQL 不允许 ON 引用后置表）。
     * 内联相关子查询在 ON 里引用右表自身是合法的，因此右侧自定义字段走本方法。
     *
     * @param idExpr 目标行主键表达式，如 t5.order_id / t0.delivery_id
     */
    private String inlineCfExpr(ReportCustomFieldSupport.CfField cf, String idExpr) {
        return "(SELECT _cfv.field_value FROM sys_custom_field_value _cfv"
                + " WHERE _cfv.entity_type = '" + cf.entityType + "'"
                + " AND _cfv.field_key = '" + cf.fieldKey + "'"
                + " AND _cfv.entity_id = " + idExpr + ")";
    }

    /** 跨单关联的目标侧操作数：系统字段 = 目标别名.列；自定义字段 = 内联相关子查询表达式 */
    private static class TargetExpr {
        final String expr;
        final String type;
        final String label;

        TargetExpr(String expr, String type, String label) {
            this.expr = expr;
            this.type = type;
            this.label = label;
        }
    }

    /**
     * 解析跨单关联的目标侧字段（含自定义字段与类型）。
     *
     * <p>目标侧自定义字段**仅支持目标主表**：目标表二级关联表（如 目标单→客户）是在本 ON 之后
     * 才 JOIN 的克隆树，同一 ON 内无法引用，属 SQL 硬约束，此类引用直接报明确错误而非静默出错。
     */
    private TargetExpr resolveTargetExpr(DataSourceMeta target, String mainAlias,
                                         ReportConfig.ColRef ref, String what) {
        if (ref == null || ref.source == null || ref.field == null) {
            throw new IllegalArgumentException(what + "配置不完整");
        }
        boolean mainLike = "main".equals(ref.source) || target.key.equals(ref.source);
        if (customFieldSupport != null && customFieldSupport.isCustomRef(ref.field)) {
            if (!mainLike) {
                throw new IllegalArgumentException(what + "不支持目标表二级关联表上的自定义字段"
                        + "（二级表在关联之后才 JOIN，同一 ON 内无法引用）: " + ref.source + "." + ref.field);
            }
            if (target.pkColumn == null) {
                throw new IllegalArgumentException(what + "目标数据源无法定位行主键，不支持自定义字段: " + target.key);
            }
            ReportCustomFieldSupport.CfField cf = customFieldSupport.find(target.table, ref.field);
            if (cf == null) {
                throw new IllegalArgumentException(what + "不在白名单内: " + ref.source + "." + ref.field);
            }
            return new TargetExpr(inlineCfExpr(cf, mainAlias + "." + target.pkColumn), cf.type, cf.label);
        }
        if (mainLike) {
            FieldMeta fm = target.fields.get(ref.field);
            if (fm == null) {
                throw new IllegalArgumentException(what + "不在白名单内: " + ref.source + "." + ref.field);
            }
            return new TargetExpr(mainAlias + "." + fm.column, fm.type, fm.label);
        }
        JoinMeta jm = target.joins.get(ref.source);
        FieldMeta fm = jm == null ? null : jm.fields.get(ref.field);
        if (fm == null) {
            throw new IllegalArgumentException(what + "不在白名单内: " + ref.source + "." + ref.field);
        }
        // 兼容既有行为：目标表头字段在主表别名上取值（历史实现口径，保持不变）
        return new TargetExpr(mainAlias + "." + fm.column, fm.type, fm.label);
    }

    /**
     * 把待物化的自定义字段值表 JOIN 立即追加到 FROM，并清空待办。
     *
     * <p>用于"ON 里要引用 cf JOIN"的场景：ON 中引用的表必须在该 JOIN 之前已存在，
     * 因此在生成 ext JOIN 之前要先 flush 一次。已 flush 的片段不会在 {@link JoinContext#fromSql()} 中重复追加。
     */
    private void flushPendingCfJoins(JoinContext ctx) {
        if (ctx.pendingCfJoins.isEmpty()) return;
        for (String j : ctx.pendingCfJoins.values()) ctx.from.append(j);
        ctx.pendingCfJoins.clear();
    }

    /** 输出列名：ext 源字段加 e{i}_ 前缀，防止跨单字段重名（如双方都有 orderNo） */
    private static String outputField(String source, String field) {
        if (source.startsWith("ext")) return "e" + source.substring(3) + "_" + field;
        return field;
    }

    // ==================== 明细模式 ====================

    private BuiltQuery buildDetail(JoinContext ctx, ReportConfig config,
                                   HttpServletRequest req, RunFilters filters) {
        if (config.columns == null || config.columns.isEmpty()) {
            throw new IllegalArgumentException("报表未配置显示列");
        }
        if (config.columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("显示列超出上限 " + MAX_COLUMNS);
        }

        // ---------- SELECT 列（全部白名单校验，双引号别名保持 camelCase） ----------
        StringBuilder select = new StringBuilder("SELECT ");
        List<ColDef> colDefs = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (ReportConfig.ColRef c : config.columns) {
            ResolvedColumn rc = resolveRef(ctx, c, "显示列");
            String out = uniqueName(outputField(c.source, rc.meta.field), usedNames);
            select.append(rc.expr).append(" AS \"").append(out).append("\", ");
            colDefs.add(new ColDef(out, rc.meta.label, rc.meta.type));
        }
        // ---------- 计算列（批次3：±×÷ + 链式引用更早计算列 + 常量；除零结果为 NULL 显示空白） ----------
        if (config.calcColumns != null && !config.calcColumns.isEmpty()) {
            if (config.calcColumns.size() > MAX_CALC_COLS) {
                throw new IllegalArgumentException("计算列超出上限 " + MAX_CALC_COLS);
            }
            Map<Integer, String> calcExprs = new LinkedHashMap<>();
            Map<Integer, String> calcLabels = new LinkedHashMap<>();
            Map<Integer, String> calcTypes = new LinkedHashMap<>();
            for (int i = 0; i < config.calcColumns.size(); i++) {
                ReportConfig.CalcCol cc = config.calcColumns.get(i);
                String expr = buildCalcExpr(ctx, cc, i, calcExprs);
                String out = uniqueName("calc" + (i + 1), usedNames);
                String label = (cc.label != null && !cc.label.trim().isEmpty())
                        ? cc.label.trim() : defaultCalcLabel(ctx, cc, i, calcLabels, "计算列" + (i + 1));
                String type = calcOutputType(ctx, cc, i, calcTypes);
                calcLabels.put(i + 1, label);
                calcTypes.put(i + 1, type);
                select.append(expr).append(" AS \"").append(out).append("\", ");
                colDefs.add(new ColDef(out, label, type));
            }
        }
        select.setLength(select.length() - 2);

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(ctx, config, filters, where, params);
        applyDataPermission(ctx, req, where, params);

        String order = buildDetailOrder(ctx, config);
        String selectSql = select + ctx.fromSql() + where + order;
        String countSql = "SELECT COUNT(*)" + ctx.fromSql() + where;

        return new BuiltQuery(selectSql, countSql, params, params, order, colDefs, null, null);
    }

    private String buildDetailOrder(JoinContext ctx, ReportConfig config) {
        if (config.orderBy == null || config.orderBy.isEmpty()) return "";
        if (config.orderBy.size() > MAX_ORDER_BY) {
            throw new IllegalArgumentException("排序列超出上限 " + MAX_ORDER_BY);
        }
        // 计算列输出别名集合（calc1..calcN，供排序引用校验）
        Set<String> calcAliases = new HashSet<>();
        if (config.calcColumns != null) {
            for (int i = 0; i < config.calcColumns.size(); i++) calcAliases.add("calc" + (i + 1));
        }
        StringBuilder order = new StringBuilder();
        for (ReportConfig.OrderRef o : config.orderBy) {
            String dir = "desc".equalsIgnoreCase(o.dir) ? "DESC" : "ASC";
            if ("calc".equals(o.source)) {
                if (o.field == null || !calcAliases.contains(o.field)) {
                    throw new IllegalArgumentException("排序计算列不存在: " + o.field);
                }
                order.append("\"").append(o.field).append("\" ").append(dir).append(", ");
                continue;
            }
            ResolvedColumn rc = resolveRef(ctx, o, "排序字段");
            order.append(rc.expr).append(" ").append(dir).append(", ");
        }
        order.setLength(order.length() - 2);
        order.insert(0, " ORDER BY ");
        return order.toString();
    }

    // ==================== 汇总模式 ====================

    /** 汇总列规格：分组列 + 聚合列统一表达 */
    private static class SummaryPart {
        ColDef def;      // 输出列（field=输出别名）
        String expr;     // SELECT 表达式（tN.col 或 SUM(tN.col)）
        boolean group;   // true=分组列
        boolean compare; // true=环比列（较上期%，LAG 窗口函数生成）
        boolean noTotals;// true=不参与合计行（公式/占比类，整体重算无业务意义）
        String srcKey;   // 仅分组列：源列唯一键 alias.column（汇总排序必须命中分组列，PG 要求排序列在 GROUP BY 内）
        /**
         * 本列 {@link #expr} 里占位符对应的参数（指标自带条件 FILTER 的占位值）。
         * 表达式被环比列/计算列内联复制一次，占位符就多一份，参数必须跟着补一份（占位顺序=参数顺序）；
         * 聚合列=首次登记的那份参数；指标±指标计算列=构建本列表达式时逐次补进的那份（合计行重算复用）。
         */
        List<Object> filterParams;
    }

    /** 解析汇总配置（分组+聚合），校验并生成输出列定义 */
    private List<SummaryPart> parseSummarySpec(JoinContext ctx, ReportConfig config) {
        return parseSummarySpec(ctx, config, new ArrayList<>());
    }

    /**
     * 解析汇总配置（分组+聚合）。
     *
     * @param selectParams 聚合指标自带条件（FILTER）产生的参数：出现在 SELECT 子句，
     *                     必须排在 WHERE 参数之前，由调用方按此顺序并入总参数表；
     *                     指标表达式被环比列/计算列内联复制一次，此处会再补一份参数（占位顺序=参数顺序）
     */
    private List<SummaryPart> parseSummarySpec(JoinContext ctx, ReportConfig config, List<Object> selectParams) {
        if (config.groupBy == null || config.groupBy.isEmpty()) {
            throw new IllegalArgumentException("汇总模式请至少选择一个分组字段");
        }
        if (config.groupBy.size() > MAX_GROUP_BY) {
            throw new IllegalArgumentException("分组字段超出上限 " + MAX_GROUP_BY);
        }
        if (config.aggregates == null || config.aggregates.isEmpty()) {
            throw new IllegalArgumentException("汇总模式请至少配置一个聚合指标");
        }
        if (config.aggregates.size() > MAX_AGGREGATES) {
            throw new IllegalArgumentException("聚合指标超出上限 " + MAX_AGGREGATES);
        }
        List<SummaryPart> parts = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        List<String> groupExprs = new ArrayList<>();
        for (ReportConfig.ColRef g : config.groupBy) {
            ResolvedColumn rc = resolveRef(ctx, g, "分组字段");
            SummaryPart p = new SummaryPart();
            p.group = true;
            p.srcKey = rc.alias + "." + rc.meta.column;
            String baseExpr = rc.expr;
            String granularity = normalizeGranularity(g.granularity, rc);
            if (granularity == null) {
                // 无粒度（默认按天/原值）：原样分组，类型保持
                p.def = new ColDef(uniqueName(outputField(g.source, rc.meta.field), usedNames), rc.meta.label, rc.meta.type);
                p.expr = baseExpr;
            } else {
                // 日期粒度分组：date_trunc 截断 + to_char 输出可读文本（2026-08 / 2026-Q3 / 2026）
                p.def = new ColDef(uniqueName(outputField(g.source, rc.meta.field), usedNames), rc.meta.label, "text");
                p.expr = truncExpr(baseExpr, granularity);
            }
            parts.add(p);
            groupExprs.add(p.expr);
        }
        for (ReportConfig.AggRef a : config.aggregates) {
            if (a.func == null) throw new IllegalArgumentException("聚合函数不能为空");
            String func = a.func.trim().toUpperCase();
            if (!AGG_FUNCS.contains(func)) {
                throw new IllegalArgumentException("聚合函数不支持: " + a.func);
            }
            boolean countAll = "COUNT".equals(func) && (a.field == null || a.field.isEmpty());
            ResolvedColumn rc = null;
            String defaultLabel;
            if (!countAll) {
                if (a.source == null || a.field == null) {
                    throw new IllegalArgumentException("聚合指标配置不完整");
                }
                rc = resolveRef(ctx, a, "聚合字段");
                if (("SUM".equals(func) || "AVG".equals(func)) && !isNumericType(rc.meta.type)) {
                    throw new IllegalArgumentException("SUM/AVG 仅支持数值（金额/数值）字段: " + rc.meta.label);
                }
                // 跨单聚合字段的标签本身已是"合计(数量)"这类形式，避免再包一层变成"合计(合计(数量))"
                defaultLabel = (rc.meta.label != null && rc.meta.label.contains("("))
                        ? rc.meta.label
                        : funcName(func) + "(" + rc.meta.label + ")";
            } else {
                defaultLabel = "行数";
            }
            SummaryPart p = new SummaryPart();
            p.group = false;
            String base = (a.alias != null && !a.alias.trim().isEmpty())
                    ? normalizeAlias(a.alias, "聚合列名")
                    : outputField(a.source == null ? "main" : a.source,
                        countAll ? func.toLowerCase() + "_all" : func.toLowerCase() + "_" + a.field);
            String label = (a.label != null && !a.label.trim().isEmpty()) ? a.label.trim() : defaultLabel;
            String outType = "COUNT".equals(func) ? "number"
                    : ("SUM".equals(func) || "AVG".equals(func))
                        ? ("amount".equals(rc.meta.type) ? "amount" : "number")
                        : rc.meta.type;
            p.def = new ColDef(uniqueName(base, usedNames), label, outType);
            // 指标自带条件：只影响这一列的口径（如今年/前年、排除草稿作废）
            ConditionSql aggFilter = compileAggFilters(ctx, a.filters, "聚合指标条件");
            String aggExpr = countAll ? "COUNT(*)" : func + "(" + rc.expr + ")";
            if (!aggFilter.sql.isEmpty()) {
                aggExpr += " FILTER (WHERE " + aggFilter.sql + ")";
                selectParams.addAll(aggFilter.params);
                p.filterParams = aggFilter.params;
            }
            p.expr = aggExpr;
            parts.add(p);
            // 较上期环比列（LAG 窗口，按分组列升序取上一行；首行/上期为0 → NULL 前端显示空白）
            if ("pct".equals(a.compare)) {
                // 聚合表达式在本环比列 SQL 中共出现 5 次（LAG 内 1 次 + lag 串复用 3 次 + ELSE 分支裸表达式 1 次），
                // 每次出现都复制占位符，参数必须逐份补齐，缺一份 = 驱动报「未设定参数值 N」（2026-11-17 实锤两轮）
                String lag = "LAG(" + p.expr + ") OVER (ORDER BY " + String.join(", ", groupExprs) + ")";
                SummaryPart cp = new SummaryPart();
                cp.group = false;
                cp.compare = true;
                cp.def = new ColDef(uniqueName(base + "_mom", usedNames), label + "环比%", "compare");
                cp.expr = "CASE WHEN " + lag + " IS NULL OR " + lag + " = 0 THEN NULL"
                        + " ELSE ROUND((" + p.expr + " - " + lag + ") * 100.0 / " + lag + ", 2) END";
                if (p.filterParams != null && !p.filterParams.isEmpty()) {
                    for (int k = 0; k < 5; k++) selectParams.addAll(p.filterParams);
                }
                parts.add(cp);
            }
        }
        appendSummaryCalcParts(config, parts, usedNames, selectParams);
        return parts;
    }

    /**
     * 汇总模式计算列（批次3）：聚合指标 ±×÷ 聚合指标/常量。
     * PG 同层 SELECT 不可引用别名，故直接内联展开两侧聚合表达式；
     * 合计行（totalsSql 无 GROUP BY）同样用 parts.expr 重算，语义正确。
     * 内联会把被引用指标的「自带条件占位符」一并复制进本列表达式 → 每内联一次，
     * 必须把该指标的 filterParams 补一份进 selectParams（占位顺序=参数顺序），
     * 同时把本列补进的参数登记到自身 filterParams（合计行整体重算时同量补齐）。
     */
    private void appendSummaryCalcParts(ReportConfig config, List<SummaryPart> parts, Set<String> usedNames,
                                        List<Object> selectParams) {
        if (config.calcColumns == null || config.calcColumns.isEmpty()) return;
        if (config.calcColumns.size() > MAX_CALC_COLS) {
            throw new IllegalArgumentException("计算列超出上限 " + MAX_CALC_COLS);
        }
        Map<String, String> aggExprs = new LinkedHashMap<>();
        Map<String, String> aggLabels = new LinkedHashMap<>();
        Map<String, String> aggTypes = new LinkedHashMap<>();
        Map<String, List<Object>> aggFilterParams = new LinkedHashMap<>();
        for (SummaryPart p : parts) {
            if (!p.group) {
                aggExprs.put(p.def.field, p.expr);
                aggLabels.put(p.def.field, p.def.label);
                aggTypes.put(p.def.field, p.def.type);
                aggFilterParams.put(p.def.field, p.filterParams);
            }
        }
        for (int i = 0; i < config.calcColumns.size(); i++) {
            ReportConfig.CalcCol cc = config.calcColumns.get(i);
            if (cc == null) throw new IllegalArgumentException("计算列配置不完整");
            // 自由公式（优先级最高）：[指标输出名] / 数字 / 四则 / 括号 / ROUND / ABS / COALESCE / TOTAL
            if (cc.formula != null && !cc.formula.trim().isEmpty()) {
                FormulaEngine.Result fr = FormulaEngine.compile(cc.formula, refName -> {
                    String f = refName.trim();
                    if (f.startsWith("agg.")) f = f.substring(4);
                    String expr = aggExprs.get(f);
                    if (expr == null) {
                        throw new IllegalArgumentException("公式引用的指标不存在: " + refName);
                    }
                    // 公式里每引用一次指标 = 内联一次其表达式（含自带条件占位符）→ 参数补一份
                    List<Object> fp = aggFilterParams.get(f);
                    if (fp != null && !fp.isEmpty()) selectParams.addAll(fp);
                    return new FormulaEngine.Resolved(expr, aggTypes.getOrDefault(f, "number"));
                }, true);
                SummaryPart fp = new SummaryPart();
                fp.group = false;
                fp.noTotals = true;
                fp.def = new ColDef(uniqueName("calc_" + (i + 1), usedNames),
                        (cc.label != null && !cc.label.trim().isEmpty()) ? cc.label.trim() : ("公式" + (i + 1)),
                        fr.type);
                fp.expr = fr.sql;
                parts.add(fp);
                continue;
            }
            String op = cc.op == null ? null : cc.op.trim();
            if (!CALC_OPS.contains(op)) {
                throw new IllegalArgumentException("计算列运算符仅支持 + - * /");
            }
            List<Object> ownParams = new ArrayList<>(); // 本列表达式内占位符对应的参数（合计行重算时同量补齐）
            String exprA = summaryCalcOperand(cc.refA, aggExprs, "汇总计算列A");
            String labelA = summaryCalcOperandLabel(cc.refA, aggLabels);
            String exprB;
            String labelB;
            if (cc.refB != null && cc.refB.source != null && cc.refB.field != null) {
                exprB = summaryCalcOperand(cc.refB, aggExprs, "汇总计算列B");
                labelB = summaryCalcOperandLabel(cc.refB, aggLabels);
            } else if (cc.constB != null && NUMERIC_CONST.matcher(cc.constB.trim()).matches()) {
                exprB = cc.constB.trim();
                labelB = cc.constB.trim();
            } else {
                throw new IllegalArgumentException("汇总计算列字段B或常量缺失/无效（常量仅支持数字）");
            }
            String full;
            if ("/".equals(op)) {
                full = "(CASE WHEN COALESCE((" + exprB + "),0) = 0 THEN NULL"
                        + " ELSE COALESCE((" + exprA + "),0) / COALESCE((" + exprB + "),0) END)";
            } else {
                full = "(COALESCE((" + exprA + "),0) " + op + " COALESCE((" + exprB + "),0))";
            }
            // 操作数表达式内联几次，其自带条件参数就补几份，且必须按 SQL 文本顺序补：
            // 除法的文本顺序是 [操作数B(CASE守卫) → 操作数A(分子) → 操作数B(分母)]，顺序错了参数整体错位，
            // 会把守卫值换成别的口径 → 0÷0 报「除以零」（2026-11-17 gate11 矩阵实锤）；
            // selectParams 供主查询/计数查询，ownParams 供合计行整体重算
            if ("/".equals(op)) {
                appendOperandFilterParams(cc.refB, aggFilterParams, selectParams, ownParams, 1);
                appendOperandFilterParams(cc.refA, aggFilterParams, selectParams, ownParams, 1);
                appendOperandFilterParams(cc.refB, aggFilterParams, selectParams, ownParams, 1);
            } else {
                appendOperandFilterParams(cc.refA, aggFilterParams, selectParams, ownParams, 1);
                appendOperandFilterParams(cc.refB, aggFilterParams, selectParams, ownParams, 1);
            }
            String label = (cc.label != null && !cc.label.trim().isEmpty())
                    ? cc.label.trim() : labelA + opSymbol(op) + labelB;
            String type = ("amount".equals(aggTypes.getOrDefault(cc.refA == null ? null : cc.refA.field, null))
                    || "amount".equals(aggTypes.getOrDefault(cc.refB == null ? null : cc.refB.field, null)))
                    ? "amount" : "number";
            SummaryPart p = new SummaryPart();
            p.group = false;
            p.def = new ColDef(uniqueName("calc_" + (i + 1), usedNames), label, type);
            p.expr = full;
            p.filterParams = ownParams;
            parts.add(p);
        }
    }

    /** 汇总计算列操作数：仅允许引用聚合指标输出别名（source=agg） */
    private String summaryCalcOperand(ReportConfig.ColRef ref, Map<String, String> aggExprs, String what) {
        if (ref == null || ref.source == null || ref.field == null) {
            throw new IllegalArgumentException(what + "配置不完整");
        }
        if (!"agg".equals(ref.source)) {
            throw new IllegalArgumentException(what + "仅支持引用聚合指标");
        }
        String expr = aggExprs.get(ref.field);
        if (expr == null) {
            throw new IllegalArgumentException(what + "引用的聚合指标不存在: " + ref.field);
        }
        return expr;
    }

    /**
     * 计算列操作数内联参数：操作数表达式在整段 SQL 里出现 {@code times} 次，
     * 其自带条件（FILTER）占位符跟着复制 {@code times} 份 → 参数同步补 {@code times} 份
     * （主查询一份份补进 selectParams；合计行重算用 sinkParams 同量登记）。
     */
    private void appendOperandFilterParams(ReportConfig.ColRef ref, Map<String, List<Object>> aggFilterParams,
                                           List<Object> selectParams, List<Object> sinkParams, int times) {
        if (ref == null || ref.field == null || times <= 0) return;
        List<Object> fp = aggFilterParams.get(ref.field);
        if (fp == null || fp.isEmpty()) return;
        for (int i = 0; i < times; i++) {
            selectParams.addAll(fp);
            sinkParams.addAll(fp);
        }
    }

    private String summaryCalcOperandLabel(ReportConfig.ColRef ref, Map<String, String> aggLabels) {
        return ref == null || ref.field == null ? "" : aggLabels.getOrDefault(ref.field, ref.field);
    }

    /** 分组粒度白名单校验：null/空/"day"→null（按原值分组），其余必须 day/month/quarter/year 且字段须为日期类型 */
    private String normalizeGranularity(String input, ResolvedColumn rc) {
        if (input == null || input.isBlank() || "day".equalsIgnoreCase(input.trim())) return null;
        String g = input.trim().toLowerCase();
        if (!"month".equals(g) && !"quarter".equals(g) && !"year".equals(g)) {
            throw new IllegalArgumentException("分组粒度不支持: " + input + "（可选 day/month/quarter/year）");
        }
        if (!"date".equals(rc.meta.type) && !"datetime".equals(rc.meta.type)) {
            throw new IllegalArgumentException("分组粒度仅支持日期类型字段: " + rc.meta.label);
        }
        return g;
    }

    /** date_trunc + to_char 生成可读分组表达式（PG）：月=2026-08 / 季=2026-Q3 / 年=2026 */
    private String truncExpr(String baseExpr, String granularity) {
        switch (granularity) {
            case "month":   return "to_char(date_trunc('month', " + baseExpr + "), 'YYYY-MM')";
            case "quarter": return "to_char(date_trunc('quarter', " + baseExpr + "), 'YYYY\"-Q\"Q')";
            default:        return "to_char(date_trunc('year', " + baseExpr + "), 'YYYY')";
        }
    }

    private String funcName(String func) {
        switch (func) {
            case "SUM": return "合计";
            case "COUNT": return "计数";
            case "AVG": return "平均";
            case "MAX": return "最大";
            default: return "最小";
        }
    }

    private String uniqueName(String base, Set<String> used) {
        String name = base;
        int i = 2;
        while (used.contains(name)) {
            name = base + "_" + i++;
        }
        used.add(name);
        return name;
    }

    /** 输出列名（英文标识）白名单：只允许字母/数字/下划线且不以数字开头 */
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private String normalizeAlias(String alias, String what) {
        String a = alias == null ? "" : alias.trim();
        if (!IDENT.matcher(a).matches()) {
            throw new IllegalArgumentException(what + "只能用英文、数字、下划线，且不能以数字开头: " + alias);
        }
        if (a.length() > 64) {
            throw new IllegalArgumentException(what + "长度不能超过 64 个字符");
        }
        return a;
    }

    /**
     * 结果条件（HAVING）：对聚合结果再过滤。
     * field 引用聚合输出名（可写 "agg.xxx"，也可直接写输出名）；支持 AND/OR 成组，口径与普通筛选一致。
     */
    private String buildHavingSql(ReportConfig config, List<SummaryPart> parts, List<Object> params) {
        if (config.having == null || config.having.isEmpty()) return "";
        if (config.having.size() > MAX_CONDITIONS) {
            throw new IllegalArgumentException("结果条件超出上限 " + MAX_CONDITIONS);
        }
        Map<String, SummaryPart> byField = new LinkedHashMap<>();
        for (SummaryPart p : parts) {
            if (!p.group) byField.put(p.def.field, p);
        }
        List<String> frags = new ArrayList<>();
        List<List<Object>> fragParams = new ArrayList<>();
        List<Boolean> orFlags = new ArrayList<>();
        for (ReportConfig.Condition c : config.having) {
            if (c == null || c.field == null || c.field.isBlank()) continue;
            String f = c.field.trim();
            if (f.startsWith("agg.")) f = f.substring(4);
            SummaryPart part = byField.get(f);
            if (part == null) {
                throw new IllegalArgumentException("结果条件引用的指标不存在: " + c.field);
            }
            String op = (c.op == null || c.op.isBlank()) ? "eq" : c.op.trim();
            if (!COND_OPS.contains(op)) {
                throw new IllegalArgumentException("结果条件比较方式不支持: " + c.op);
            }
            String cast = castSuffix(part.def.type);
            StringBuilder frag = new StringBuilder();
            List<Object> fp = new ArrayList<>();
            switch (op) {
                case "isNull" -> frag.append(part.expr).append(" IS NULL");
                case "isNotNull" -> frag.append(part.expr).append(" IS NOT NULL");
                case "between" -> {
                    if (!isBlank(c.value) && !isBlank(c.value2)) {
                        frag.append(part.expr).append(" BETWEEN ?").append(cast)
                            .append(" AND ?").append(cast);
                        fp.add(c.value.trim());
                        fp.add(c.value2.trim());
                    }
                }
                case "in", "notIn" -> {
                    if (c.values != null && !c.values.isEmpty()) {
                        if (c.values.size() > MAX_IN_VALUES) {
                            throw new IllegalArgumentException("结果条件值个数超出上限 " + MAX_IN_VALUES);
                        }
                        frag.append(part.expr).append("notIn".equals(op) ? " NOT IN (" : " IN (");
                        for (int i = 0; i < c.values.size(); i++) {
                            if (i > 0) frag.append(", ");
                            frag.append("?").append(cast);
                            fp.add(c.values.get(i));
                        }
                        frag.append(")");
                    }
                }
                default -> {
                    if (!isBlank(c.value)) {
                        frag.append(part.expr).append(" ").append(opSql(op)).append(" ?").append(cast);
                        fp.add(c.value.trim());
                    }
                }
            }
            if (frag.length() == 0) continue;
            frags.add(frag.toString());
            fragParams.add(fp);
            orFlags.add("OR".equalsIgnoreCase(c.logic == null ? "" : c.logic.trim()));
        }
        if (frags.isEmpty()) return "";
        StringBuilder grouped = new StringBuilder();
        int i = 0;
        while (i < frags.size()) {
            if (i > 0) grouped.append(" AND ");
            int j = i;
            while (j + 1 < frags.size() && orFlags.get(j + 1)) j++;
            grouped.append("(");
            for (int k = i; k <= j; k++) {
                if (k > i) grouped.append(" OR ");
                grouped.append(frags.get(k));
            }
            grouped.append(")");
            i = j + 1;
        }
        for (List<Object> fp : fragParams) params.addAll(fp);
        return " HAVING (" + grouped + ") ";
    }

    private BuiltQuery buildSummary(EntityMetadataRegistry registry, JoinContext ctx, ReportConfig config,
                                    HttpServletRequest req, RunFilters filters) {
        List<Object> selectParams = new ArrayList<>();
        List<SummaryPart> parts = parseSummarySpec(ctx, config, selectParams);

        // ---------- SELECT ----------
        StringBuilder select = new StringBuilder("SELECT ");
        StringBuilder groupBy = new StringBuilder();
        List<ColDef> colDefs = new ArrayList<>();
        for (SummaryPart p : parts) {
            select.append(p.expr).append(" AS \"").append(p.def.field).append("\", ");
            colDefs.add(p.def);
            if (p.group) groupBy.append(p.expr).append(", ");
        }
        select.setLength(select.length() - 2);
        if (groupBy.length() > 0) groupBy.setLength(groupBy.length() - 2);

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        // 参数顺序必须与 SQL 文本一致：SELECT(指标条件) → FROM(跨单子查询条件) → WHERE → HAVING
        List<Object> params = new ArrayList<>(selectParams);
        params.addAll(ctx.joinParams);
        int whereParamsFrom = params.size();
        appendFilters(ctx, config, filters, where, params);
        applyDataPermission(ctx, req, where, params);
        // WHERE 段参数（筛选+数据权限）：合计行 SQL 同样带这段 WHERE，需要同量参数
        List<Object> whereParams = new ArrayList<>(params.subList(whereParamsFrom, params.size()));
        // 结果条件（HAVING）：对聚合结果再过滤，如"今年销售数量 > 1000"
        List<Object> havingParams = new ArrayList<>();
        String havingSql = buildHavingSql(config, parts, havingParams);
        params.addAll(havingParams);

        String groupClause = " GROUP BY " + groupBy;
        String order = buildSummaryOrder(ctx, config, parts);
        String selectSql = select + ctx.fromSql() + where + groupClause + havingSql + order;
        log.debug("[自定义报表] 汇总查询: {} | 参数: {}", selectSql, params);
        String countSql = "SELECT COUNT(*) FROM (" + select + ctx.fromSql() + where + groupClause + havingSql + ") _g";

        // 合计行：整体重算（SUM求和/COUNT求和/AVG整体平均/MAX MIN整体极值）
        // 注意：无 GROUP BY 时非聚合列不能与聚合函数混用（PG语法限制），分组列不参与合计行输出，
        // 由 Service 端按列位置补"合计"/null（列顺序 = 分组列在前、聚合列在后，与 parseSummarySpec 一致）；
        // 环比列（compare）无整体语义，同样跳过
        StringBuilder totals = new StringBuilder("SELECT ");
        List<Object> totalsSelectParams = new ArrayList<>();
        boolean totalsHasCol = false;
        for (SummaryPart p : parts) {
            if (p.group || p.compare || p.noTotals) continue;
            totals.append(p.expr).append(" AS \"").append(p.def.field).append("\", ");
            // 计算列表达式里内联了指标条件占位符 → 参数随文本逐列补齐（聚合列=首次登记那份，计算列=构建时补的那份）
            if (p.filterParams != null && !p.filterParams.isEmpty()) totalsSelectParams.addAll(p.filterParams);
            totalsHasCol = true;
        }
        String totalsSql = null;
        List<Object> totalsParams = null;
        if (totalsHasCol) {
            totals.setLength(totals.length() - 2);
            totalsSql = totals + ctx.fromSql() + where;
            // 参数与 SQL 文本严格对齐：合计SELECT(指标条件) → FROM(跨单子查询条件) → WHERE(筛选+数据权限)
            totalsParams = new ArrayList<>(totalsSelectParams);
            totalsParams.addAll(ctx.joinParams);
            totalsParams.addAll(whereParams);
        }

        return new BuiltQuery(selectSql, countSql, params, params, order, colDefs, totalsSql, totalsParams);
    }

    /**
     * 汇总排序：source="agg" 按聚合输出别名排序；否则仅允许分组列（按输出别名）。
     * 非分组列的排序项直接忽略（PG 要求排序列在 GROUP BY 内，聚合行按非分组列排序语义无定义），
     * 忽略时记 warn 日志便于诊断配置问题。
     */
    private String buildSummaryOrder(JoinContext ctx, ReportConfig config, List<SummaryPart> parts) {
        if (config.orderBy == null || config.orderBy.isEmpty()) return "";
        if (config.orderBy.size() > MAX_ORDER_BY) {
            throw new IllegalArgumentException("排序列超出上限 " + MAX_ORDER_BY);
        }
        Set<String> aggAliases = new HashSet<>();
        Map<String, String> groupOutBySrcKey = new HashMap<>();
        for (SummaryPart p : parts) {
            if (!p.group) aggAliases.add(p.def.field);
            else if (p.srcKey != null) groupOutBySrcKey.put(p.srcKey, p.def.field);
        }
        StringBuilder order = new StringBuilder();
        for (ReportConfig.OrderRef o : config.orderBy) {
            String dir = "desc".equalsIgnoreCase(o.dir) ? "DESC" : "ASC";
            if ("agg".equals(o.source)) {
                if (o.field == null || !aggAliases.contains(o.field)) {
                    throw new IllegalArgumentException("排序聚合列不存在: " + o.field);
                }
                // NULLS LAST：PG 的 DESC 默认把空值排最前，用户会以为"整列都是空的"
                order.append("\"").append(o.field).append("\" ").append(dir).append(" NULLS LAST, ");
            } else {
                ResolvedColumn rc = resolveRef(ctx, o, "排序字段");
                String outAlias = groupOutBySrcKey.get(rc.alias + "." + rc.meta.column);
                if (outAlias != null) {
                    // 命中分组列：按输出别名排序（granularity 场景与分组值口径一致）
                    order.append("\"").append(outAlias).append("\" ").append(dir).append(" NULLS LAST, ");
                } else {
                    log.warn("汇总模式忽略非分组列排序（排序列必须在GROUP BY内）: {}.{}", rc.alias, rc.meta.column);
                }
            }
        }
        if (order.length() == 0) return "";
        order.setLength(order.length() - 2);
        order.insert(0, " ORDER BY ");
        return order.toString();
    }

    private void validateOrderBy(JoinContext ctx, ReportConfig config) {
        if (config.orderBy == null || config.orderBy.isEmpty()) return;
        if (config.orderBy.size() > MAX_ORDER_BY) {
            throw new IllegalArgumentException("排序列超出上限 " + MAX_ORDER_BY);
        }
        // 完整校验在 buildDetailOrder/buildSummaryOrder 中执行，此处仅查空引用
        for (ReportConfig.OrderRef o : config.orderBy) {
            if (o == null || o.source == null || o.field == null) {
                throw new IllegalArgumentException("排序字段配置不完整");
            }
        }
    }

    // ==================== 公共子句 ====================

    /** 运行时筛选：报表预置的 日期/关键字/状态 三档 + 任意字段条件（filters.conditions） */
    private void appendFilters(JoinContext ctx, ReportConfig config, RunFilters filters,
                               StringBuilder where, List<Object> params) {
        if (filters == null) return;
        if (config.dateField != null) {
            ResolvedColumn rc = resolveRef(ctx, config.dateField, "日期筛选字段");
            boolean isDate = "date".equals(rc.meta.type);
            String cast = isDate ? "?::date" : "?::timestamp";
            if (filters.startDate != null && !filters.startDate.isEmpty()) {
                where.append("AND ").append(rc.expr).append(" >= ").append(cast).append(" ");
                params.add(isDate ? filters.startDate : filters.startDate + " 00:00:00");
            }
            if (filters.endDate != null && !filters.endDate.isEmpty()) {
                where.append("AND ").append(rc.expr).append(" <= ").append(cast).append(" ");
                params.add(isDate ? filters.endDate : filters.endDate + " 23:59:59");
            }
        }
        if (config.keywordField != null && filters.keyword != null && !filters.keyword.trim().isEmpty()) {
            ResolvedColumn rc = resolveRef(ctx, config.keywordField, "关键字筛选字段");
            where.append("AND ").append(rc.expr).append(" LIKE ? ");
            params.add("%" + filters.keyword.trim() + "%");
        }
        if (config.statusField != null && filters.status != null && !filters.status.isEmpty()) {
            ResolvedColumn rc = resolveRef(ctx, config.statusField, "状态筛选字段");
            where.append("AND ").append(rc.expr).append(" = ? ");
            params.add(filters.status);
        }
        appendConditions(ctx, effectiveConditions(config, filters), where, params);
    }

    /**
     * 生效筛选条件：区分「未传」与「传了空数组」两种语义。
     * <ul>
     *   <li>conditions == null（接口没传）→ 用报表配置的默认筛选 config.filters（对外/AI 直接调用友好）</li>
     *   <li>conditions != null（含空数组）→ 以运行时为准：空数组 = 用户明确"不要任何附加条件"（运行页清空即可清掉默认筛选）</li>
     * </ul>
     */
    private List<ReportConfig.Condition> effectiveConditions(ReportConfig config, RunFilters filters) {
        if (filters.conditions != null) return filters.conditions;
        return config == null ? null : config.filters;
    }

    /**
     * 任意字段过滤条件（报表配置 filters 与运行时 conditions 共用同一实现）。
     *
     * <p>字段引用仍走注册表白名单（系统字段 + 自定义字段，含跨单 ext 源）；操作符硬编码白名单；
     * 值一律 {@code ?} 占位，按字段类型追加 {@code ::numeric / ::date / ::timestamp} 显式 CAST
     * （否则 varchar 与数值/日期不可比会报 bad SQL grammar）。
     * 空值条件（没有值）直接跳过、不产生 SQL —— 与三个预置筛选位"空即不过滤"口径一致。
     *
     * <p><b>OR 成组</b>：每条条件的 {@code logic} 表示它与<b>上一条</b>的连接符（AND 默认 / OR）。
     * 连续多条 OR 归入同一个括号组，组与组之间为 AND：
     * {@code [A, B(OR), C(AND), D(OR)]} → {@code A AND (B OR C) AND D}。
     * 全部不写 logic 时与"纯 AND 串联"逐字等价（向后兼容）。
     */
    private void appendConditions(JoinContext ctx, List<ReportConfig.Condition> conditions,
                                  StringBuilder where, List<Object> params) {
        if (conditions == null || conditions.isEmpty()) return;
        if (conditions.size() > MAX_CONDITIONS) {
            throw new IllegalArgumentException("筛选条件超出上限 " + MAX_CONDITIONS);
        }
        // ---- 第一步：逐条编译成独立 SQL 片段（不含 AND/OR 前缀）+ 该片段自己的参数 ----
        List<String> frags = new ArrayList<>();
        List<List<Object>> fragParams = new ArrayList<>();
        List<Boolean> orFlags = new ArrayList<>();
        for (ReportConfig.Condition c : conditions) {
            if (c == null || c.field == null || c.field.isBlank()) continue;
            String op = (c.op == null || c.op.isBlank()) ? "eq" : c.op.trim();
            if (!COND_OPS.contains(op)) {
                throw new IllegalArgumentException("筛选操作符不支持: " + c.op);
            }
            ReportConfig.ColRef ref = new ReportConfig.ColRef();
            ref.source = (c.source == null || c.source.isBlank()) ? "main" : c.source.trim();
            ref.field = c.field.trim();
            ResolvedColumn rc = resolveRef(ctx, ref, "筛选条件字段");
            if (("like".equals(op) || "notLike".equals(op)) && !isStringKind(rc.meta.type)) {
                throw new IllegalArgumentException("包含/不包含仅支持文本字段: " + rc.meta.label);
            }
            String cast = castSuffix(rc.meta.type);
            StringBuilder frag = new StringBuilder();
            List<Object> fp = new ArrayList<>();
            // 相对时间（今年/去年/前年/本月/上月/近N月）：按运行当天换算成具体区间
            if (c.relative != null && !c.relative.trim().isEmpty()) {
                if (!"date".equals(rc.meta.type) && !"datetime".equals(rc.meta.type)) {
                    throw new IllegalArgumentException("相对时间仅支持日期字段: " + rc.meta.label);
                }
                String[] range = resolveRelativeRange(c.relative.trim(), c.value);
                frag.append(rc.expr).append(" BETWEEN ?").append(cast)
                    .append(" AND ?").append(cast);
                fp.add(range[0]);
                fp.add(range[1]);
            } else {
            switch (op) {
                case "isNull" -> frag.append(rc.expr).append(" IS NULL");
                case "isNotNull" -> frag.append(rc.expr).append(" IS NOT NULL");
                case "like", "notLike" -> {
                    if (!isBlank(c.value)) {
                        frag.append(rc.expr).append("notLike".equals(op) ? " NOT LIKE ?" : " LIKE ?");
                        fp.add("%" + c.value.trim() + "%");
                    }
                }
                case "between" -> {
                    if (!isBlank(c.value) && !isBlank(c.value2)) {
                        frag.append(rc.expr).append(" BETWEEN ?").append(cast).append(" AND ?").append(cast);
                        fp.add(c.value.trim());
                        fp.add(c.value2.trim());
                    }
                }
                case "in", "notIn" -> {
                    List<String> vals = c.values;
                    if (vals != null && !vals.isEmpty()) {
                        if (vals.size() > MAX_IN_VALUES) {
                            throw new IllegalArgumentException("in 条件值个数超出上限 " + MAX_IN_VALUES);
                        }
                        frag.append(rc.expr).append("notIn".equals(op) ? " NOT IN (" : " IN (");
                        for (int i = 0; i < vals.size(); i++) {
                            if (i > 0) frag.append(", ");
                            frag.append("?").append(cast);
                            fp.add(vals.get(i));
                        }
                        frag.append(")");
                    }
                }
                default -> {
                    if (!isBlank(c.value)) {
                        frag.append(rc.expr).append(" ").append(opSql(op)).append(" ?").append(cast);
                        fp.add(c.value.trim());
                    }
                }
            }
            }
            // 空值条件跳过且不占用组位（orFlags 与 frags 下标严格对齐）
            if (frag.length() == 0) continue;
            frags.add(frag.toString());
            fragParams.add(fp);
            orFlags.add("OR".equalsIgnoreCase(c.logic == null ? "" : c.logic.trim()));
        }
        if (frags.isEmpty()) return;

        // ---- 第二步：成组 —— 起始项 + 其后所有 logic=OR 的项为一组，组内 OR、组间 AND ----
        StringBuilder grouped = new StringBuilder();
        int i = 0;
        while (i < frags.size()) {
            if (i > 0) grouped.append(" AND ");
            int j = i;
            while (j + 1 < frags.size() && orFlags.get(j + 1)) j++;
            grouped.append("(");
            for (int k = i; k <= j; k++) {
                if (k > i) grouped.append(" OR ");
                grouped.append(frags.get(k));
            }
            grouped.append(")");
            i = j + 1;
        }
        // 参数按片段顺序入队（与 SQL 中 ? 的出现顺序一致）
        for (List<Object> fp : fragParams) params.addAll(fp);
        where.append("AND (").append(grouped).append(") ");
    }

    /** 条件编译结果：SQL 片段（不含 AND 前缀）+ 该片段自己的参数 */
    private static class ConditionSql {
        final String sql;
        final List<Object> params;

        ConditionSql(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /**
     * 聚合指标自带条件：复用通用条件编译，去掉外层 "AND (...)" 包装，
     * 结果供 SQL 的 {@code FILTER (WHERE ...)} 使用。
     */
    private ConditionSql compileAggFilters(JoinContext ctx, List<ReportConfig.Condition> conds, String what) {
        if (conds == null || conds.isEmpty()) return new ConditionSql("", new ArrayList<>());
        if (conds.size() > MAX_AGG_FILTERS) {
            throw new IllegalArgumentException(what + "超出上限 " + MAX_AGG_FILTERS);
        }
        StringBuilder sb = new StringBuilder();
        List<Object> ps = new ArrayList<>();
        appendConditions(ctx, conds, sb, ps);
        String s = sb.toString().trim();
        if (s.isEmpty()) return new ConditionSql("", ps);
        if (s.startsWith("AND (")) s = s.substring(5);
        if (s.endsWith(")")) s = s.substring(0, s.length() - 1);
        return new ConditionSql(s.trim(), ps);
    }

    /**
     * 相对时间 → 具体日期区间 [起, 止]（含首尾）。
     * 支持 THIS_YEAR / LAST_YEAR / YEAR_BEFORE / THIS_MONTH / LAST_MONTH / LAST_N_MONTHS（N 取 value）。
     */
    private String[] resolveRelativeRange(String relative, String nValue) {
        java.time.LocalDate today = java.time.LocalDate.now();
        switch (relative.toUpperCase()) {
            case "THIS_YEAR": {
                java.time.LocalDate s = today.withDayOfYear(1);
                return new String[]{s.toString(), s.plusYears(1).minusDays(1).toString()};
            }
            case "LAST_YEAR": {
                java.time.LocalDate s = today.withDayOfYear(1).minusYears(1);
                return new String[]{s.toString(), s.plusYears(1).minusDays(1).toString()};
            }
            case "YEAR_BEFORE": {
                java.time.LocalDate s = today.withDayOfYear(1).minusYears(2);
                return new String[]{s.toString(), s.plusYears(1).minusDays(1).toString()};
            }
            case "THIS_MONTH": {
                java.time.LocalDate s = today.withDayOfMonth(1);
                return new String[]{s.toString(), s.plusMonths(1).minusDays(1).toString()};
            }
            case "LAST_MONTH": {
                java.time.LocalDate s = today.withDayOfMonth(1).minusMonths(1);
                return new String[]{s.toString(), s.plusMonths(1).minusDays(1).toString()};
            }
            case "LAST_N_MONTHS": {
                int n;
                try {
                    n = Integer.parseInt(nValue == null ? "6" : nValue.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("近N月的 N 必须是数字");
                }
                if (n < 1 || n > 60) {
                    throw new IllegalArgumentException("近N月的 N 需在 1~60 之间");
                }
                java.time.LocalDate s = today.withDayOfMonth(1).minusMonths(n - 1L);
                return new String[]{s.toString(), today.toString()};
            }
            default:
                throw new IllegalArgumentException("相对时间不支持: " + relative
                        + "（可用 THIS_YEAR/LAST_YEAR/YEAR_BEFORE/THIS_MONTH/LAST_MONTH/LAST_N_MONTHS）");
        }
    }

    /** 比较操作符 → SQL 符号（op 已过 COND_OPS 白名单） */
    private static String opSql(String op) {
        return switch (op) {
            case "ne" -> "!=";
            case "gt" -> ">";
            case "ge" -> ">=";
            case "lt" -> "<";
            case "le" -> "<=";
            default -> "=";
        };
    }

    /** 占位符类型转换后缀：数值/日期显式 CAST，避免 varchar 与数值/日期不可比（生产库历史坑） */
    private static String castSuffix(String type) {
        if (type == null) return "";
        return switch (type) {
            case "number", "amount", "int" -> "::numeric";
            case "date" -> "::date";
            case "datetime" -> "::timestamp";
            default -> "";
        };
    }

    /** 是否文本类字段（LIKE 系操作符仅对文本字段开放） */
    private static boolean isStringKind(String type) {
        return type == null || "string".equals(type) || "text".equals(type)
                || "select".equals(type) || "bool".equals(type);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 主数据源表 → 业务归属列（口径与各标准报表/列表页一致，缺省 = 仅按 created_by 过滤） */
    private static final Map<String, String> OWNER_COLUMN_BY_TABLE = Map.ofEntries(
            Map.entry("sale_quote", "saler_id"),
            Map.entry("sale_order", "saler_id"),
            Map.entry("sale_contract", "saler_id"),
            Map.entry("sale_delivery", "saler_id"),
            Map.entry("sale_return", "saler_id"),
            Map.entry("sale_invoice", "saler_id"),
            Map.entry("account_receivable", "saler_id"),
            Map.entry("account_payable", "buyer_id"),
            Map.entry("purchase_request", "applicant_id"),
            Map.entry("purchase_order", "buyer_id"),
            Map.entry("purchase_contract", "buyer_id"),
            Map.entry("purchase_inbound", "buyer_id"),
            Map.entry("purchase_return", "buyer_id"),
            Map.entry("crm_opportunity", "assigned_sales_id"),
            Map.entry("crm_lead", "assigned_sales_id"));

    /** 主数据源属地业务归属列（按表精确匹配，其次按模块兜底；null = 仅按 created_by 过滤） */
    private String resolveOwnerColumn(String table, String module) {
        return ownerColumnOf(table, module);
    }

    /**
     * 主数据源表 → 业务归属列（公开静态：AI 聚合查询等复用<b>同一份</b>口径，杜绝两处漂移）。
     * 按表精确匹配，其次按模块兜底；null = 仅按 created_by 过滤。
     */
    public static String ownerColumnOf(String table, String module) {
        String byTable = table == null ? null : OWNER_COLUMN_BY_TABLE.get(table);
        if (byTable != null) return byTable;
        if ("sale".equals(module)) return "saler_id";
        if ("purchase".equals(module)) return "buyer_id";
        if ("crm".equals(module)) return "assigned_sales_id";
        return null;
    }

    /**
     * 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人）。
     * 全部数据 → 不加条件；仅本人/本部门 → (created_by 条件 OR 业务归属列条件)。
     * 主数据源树内既无 created_by 也无归属列时保持改造前行为（不过滤）并记 warn。
     */
    private void applyDataPermission(JoinContext ctx, HttpServletRequest req,
                                     StringBuilder where, List<Object> params) {
        if (req == null) return;
        DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(req);
        if (scopeCtx == null || scopeCtx.isAll()) return;
        String createdAlias = findColumnAlias(ctx, "created_by");
        String ownerAlias = ctx.ownerColumn == null ? null : findColumnAlias(ctx, ctx.ownerColumn);
        if (createdAlias == null && ownerAlias == null) {
            log.warn("[自定义报表] 主数据源 {} 无 created_by/归属列，数据权限未过滤", ctx.table);
            return;
        }
        StringBuilder cond = new StringBuilder();
        if (DataScopeResolver.SCOPE_DEPT.equals(scopeCtx.getScope())) {
            if (createdAlias != null) {
                if (!scopeCtx.getDeptUsernames().isEmpty()) {
                    appendIn(cond, createdAlias, "created_by", scopeCtx.getDeptUsernames(), params);
                } else if (scopeCtx.getUsername() != null) {
                    appendEq(cond, createdAlias, "created_by", scopeCtx.getUsername(), params);
                }
            }
            if (ownerAlias != null && !scopeCtx.getDeptEmployeeIds().isEmpty()) {
                appendIn(cond, ownerAlias, ctx.ownerColumn, scopeCtx.getDeptEmployeeIds(), params);
            }
        } else {
            if (createdAlias != null && scopeCtx.getUsername() != null) {
                appendEq(cond, createdAlias, "created_by", scopeCtx.getUsername(), params);
            }
            if (ownerAlias != null && scopeCtx.getEmployeeId() != null) {
                appendEq(cond, ownerAlias, ctx.ownerColumn, scopeCtx.getEmployeeId(), params);
            }
        }
        if (cond.length() == 0) {
            // 数据范围无法定位到任何人 → 条件恒假，防越权
            where.append("AND 1 = 0 ");
            return;
        }
        where.append("AND (").append(cond).append(") ");
    }

    private void appendEq(StringBuilder cond, String alias, String column, Object value, List<Object> params) {
        if (cond.length() > 0) cond.append(" OR ");
        cond.append(alias).append(".").append(column).append(" = ?");
        params.add(value);
    }

    private void appendIn(StringBuilder cond, String alias, String column, List<?> values, List<Object> params) {
        if (cond.length() > 0) cond.append(" OR ");
        cond.append(alias).append(".").append(column).append(" IN (");
        cond.append(String.join(",", java.util.Collections.nCopies(values.size(), "?")));
        cond.append(")");
        params.addAll(values);
    }

    /** 主数据源树内查找列所属别名（跳过跨单 ext 源）；未命中返回 null */
    private String findColumnAlias(JoinContext ctx, String column) {
        if (column == null) return null;
        Map<String, FieldMeta> mainFields = ctx.fieldsBySource.get("main");
        if (mainFields != null) {
            for (FieldMeta fm : mainFields.values()) {
                if (fm.column.equals(column)) return "t1";
            }
        }
        for (Map.Entry<String, Map<String, FieldMeta>> e : ctx.fieldsBySource.entrySet()) {
            if (e.getKey().startsWith("ext")) continue; // 数据权限列仅认主数据源树
            for (FieldMeta fm : e.getValue().values()) {
                if (fm.column.equals(column)) return ctx.aliasBySource.get(e.getKey());
            }
        }
        return null;
    }

    /** 运行时筛选值 */
    public static class RunFilters {
        public String startDate;
        public String endDate;
        public String keyword;
        public String status;
        /** 任意字段过滤条件（0~MAX_CONDITIONS 个；结构见 {@link ReportConfig.Condition}，支持 logic=OR 成组） */
        public List<ReportConfig.Condition> conditions;
    }
}
