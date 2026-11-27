package com.erp.report.custom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.SecurityUtils;
import com.erp.permission.service.FieldPermissionOutputFilter;
import com.erp.report.custom.core.CustomReportSqlBuilder;
import com.erp.report.custom.core.EntityMetadataRegistry;
import com.erp.report.custom.core.ReportConfig;
import com.erp.report.custom.entity.CustomReport;
import com.erp.report.custom.mapper.CustomReportMapper;
import com.erp.report.custom.service.CustomReportService;
import com.erp.report.util.StandardExcelUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义报表服务实现
 *
 * 安全要点：
 * - data_source 与 config 内全部字段引用保存前即做白名单校验（运行时再校验一次）
 * - 可见性：创建人本人 / 共享角色命中 / ADMIN、MANAGER
 * - 编辑/删除：仅创建人或 ADMIN、MANAGER
 * - run/export 前均做可见性 + 白名单双重校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomReportServiceImpl implements CustomReportService {

    private final CustomReportMapper customReportMapper;
    private final EntityMetadataRegistry registry;
    private final CustomReportSqlBuilder sqlBuilder;
    private final JdbcTemplate jdbcTemplate;
    /** 字段权限：设计器字段、运行结果、导出三处共用同一份"不可见字段"规则 */
    private final FieldPermissionOutputFilter fieldPermissionOutputFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CONFIG_JSON_LENGTH = 32 * 1024;
    private static final int MAX_PAGE_SIZE = 500;

    // ==================== 元数据 ====================

    @Override
    public Map<String, Object> listDataSources() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataSources", registry.listDataSources());
        result.put("buildId", registry.getBuildId());
        return result;
    }

    @Override
    public Map<String, Object> describeDataSource(String dataSourceKey) {
        return describeDataSource(dataSourceKey, null, null);
    }

    /**
     * 数据源元数据（带字段权限）。
     *
     * <p>userId/admin 为 null 时按请求上下文解析（页面设计器）；AI 小助手跑在线程池，
     * 由工具入口传入当前用户显式解析。不可见字段不进字段清单——设计器里根本选不到，
     * 运行/导出再按同一份规则兜底剔除。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> describeDataSource(String dataSourceKey, Long userId, Boolean admin) {
        Map<String, Object> m = registry.describeDataSource(dataSourceKey);
        if (m == null) {
            throw new IllegalArgumentException("数据源不存在或未开放: " + dataSourceKey);
        }
        EntityMetadataRegistry.DataSourceMeta ds = registry.getDataSource(dataSourceKey);
        if (ds != null) {
            Set<String> mainHidden = hiddenOfTable(ds.table, userId, admin);
            filterFieldTree(m.get("fields"), mainHidden);
            Object joinsObj = m.get("joins");
            if (joinsObj instanceof List<?> joins) {
                for (Object o : joins) {
                    if (!(o instanceof Map<?, ?> j)) continue;
                    Object keyObj = j.get("key");
                    EntityMetadataRegistry.JoinMeta jm = keyObj == null ? null : ds.joins.get(String.valueOf(keyObj));
                    Set<String> hidden = jm == null ? mainHidden
                            : FieldPermissionOutputFilter.union(mainHidden, hiddenOfTable(jm.table, userId, admin));
                    filterFieldTree(j.get("fields"), hidden);
                }
            }
            Object detailObj = m.get("detailJoins");
            if (detailObj instanceof List<?> details) {
                for (Object o : details) {
                    if (!(o instanceof Map<?, ?> j)) continue;
                    Object keyObj = j.get("key");
                    EntityMetadataRegistry.JoinMeta jm = keyObj == null ? null : ds.detailJoins.get(String.valueOf(keyObj));
                    Set<String> hidden = jm == null ? mainHidden
                            : FieldPermissionOutputFilter.union(mainHidden, hiddenOfTable(jm.table, userId, admin));
                    filterFieldTree(j.get("fields"), hidden);
                }
            }
        }
        m.put("buildId", registry.getBuildId());
        return m;
    }

    /** 字段树（[{field,label,type}]）剔除不可见字段 */
    @SuppressWarnings("unchecked")
    private static void filterFieldTree(Object fieldsObj, Set<String> hidden) {
        if (hidden.isEmpty() || !(fieldsObj instanceof List<?> list)) return;
        ((List<Object>) list).removeIf(o -> o instanceof Map<?, ?> fm
                && fm.get("field") != null
                && matchesHidden(hidden, String.valueOf(fm.get("field"))));
    }

    /** 当前用户在某张表（=page_key）不可见的字段：优先按显式用户（AI 线程），否则按请求上下文 */
    private Set<String> hiddenOfTable(String table, Long userId, Boolean admin) {
        if (table == null || table.isBlank()) return java.util.Collections.emptySet();
        if (userId != null || admin != null) {
            return fieldPermissionOutputFilter.hiddenKeysForUser(userId, Boolean.TRUE.equals(admin), table);
        }
        return fieldPermissionOutputFilter.hiddenKeys(table);
    }

    /**
     * 报表运行/导出涉及的整棵数据源（主表 + 关联 + 明细反向关联 + 跨单关联）的不可见字段并集。
     * 输出列名对普通关联不加前缀，因此按并集判断（宁可多藏，不放过）。
     */
    private Set<String> hiddenForReport(EntityMetadataRegistry.DataSourceMeta ds, ReportConfig config) {
        Set<String> hidden = new HashSet<>(hiddenOfTable(ds.table, null, null));
        for (EntityMetadataRegistry.JoinMeta jm : ds.joins.values()) {
            hidden.addAll(hiddenOfTable(jm.table, null, null));
        }
        for (EntityMetadataRegistry.JoinMeta jm : ds.detailJoins.values()) {
            hidden.addAll(hiddenOfTable(jm.table, null, null));
        }
        if (config != null && config.extraJoins != null) {
            for (ReportConfig.ExtraJoin ej : config.extraJoins) {
                if (ej == null || ej.dataSourceKey == null) continue;
                EntityMetadataRegistry.DataSourceMeta target = registry.getDataSource(ej.dataSourceKey);
                if (target != null) hidden.addAll(hiddenOfTable(target.table, null, null));
            }
        }
        return hidden;
    }

    /**
     * 输出列名是否命中不可见字段。
     * 跨单关联的输出列带前缀（ext1 → e1_、ext1.j1 → e1.j1_），去掉前缀再比一次。
     */
    private static boolean matchesHidden(Set<String> hidden, String outField) {
        if (outField == null || hidden.isEmpty()) return false;
        if (hidden.contains(outField)) return true;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^e\\d+[._]").matcher(outField);
        if (m.find()) {
            String rest = outField.substring(m.end());
            if (hidden.contains(rest)) return true;
            int us = rest.indexOf('_');
            if (us > 0 && hidden.contains(rest.substring(us + 1))) return true;
        }
        return false;
    }

    /** 运行/导出前的筛选条件校验：不可见字段不能被拿来当条件（否则可反推值） */
    private static void rejectHiddenConditions(Set<String> hidden, List<ReportConfig.Condition> conditions) {
        if (hidden.isEmpty() || conditions == null || conditions.isEmpty()) return;
        for (ReportConfig.Condition c : conditions) {
            if (c != null && c.field != null && matchesHidden(hidden, c.field)) {
                throw new IllegalArgumentException("字段「" + c.field + "」在当前账号的字段权限中不可见，不能用作筛选条件");
            }
        }
    }

    @Override
    public Map<String, Object> joinables(HttpServletRequest req, String dataSourceKey, String targetKey) {
        EntityMetadataRegistry.DataSourceMeta target = registry.getDataSource(targetKey);
        if (target == null) {
            throw new IllegalArgumentException("关联数据源不存在或未开放: " + targetKey);
        }
        // 受限角色跨模块限制（与 SqlBuilder 构建期校验口径一致，列表期即拦截）
        if (req != null && SecurityUtils.isSalesOnly(req)
                && !"sale".equals(target.module) && !"crm".equals(target.module)) {
            throw new IllegalArgumentException("销售角色仅支持关联销售/CRM模块数据源");
        }
        if (req != null && SecurityUtils.isPurchaseOnly(req) && !"purchase".equals(target.module)) {
            throw new IllegalArgumentException("采购角色仅支持关联采购模块数据源");
        }
        Map<String, Object> trees = registry.joinables(dataSourceKey, targetKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetKey", target.key);
        result.put("targetName", target.name);
        result.put("leftTree", trees.get("left"));
        result.put("rightTree", trees.get("right"));
        result.put("rightDetailTree", trees.get("rightDetailTree"));
        return result;
    }

    // ==================== 报表 CRUD ====================

    @Override
    public Map<String, Object> listMyReports(HttpServletRequest req, String keyword, int page, int size) {
        String username = SecurityUtils.getCurrentUsername(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        boolean admin = SecurityUtils.isAdmin(req);

        LambdaQueryWrapper<CustomReport> qw = new LambdaQueryWrapper<>();
        if (!admin) {
            qw.and(w -> w.eq(CustomReport::getCreatedBy, username)
                    .or().eq(CustomReport::getShareScope, "共享"));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.and(w -> w.like(CustomReport::getReportName, keyword.trim())
                    .or().like(CustomReport::getReportCode, keyword.trim()));
        }
        qw.orderByDesc(CustomReport::getCreatedAt);
        List<CustomReport> all = customReportMapper.selectList(qw);

        // 共享报表需角色命中才可见
        List<CustomReport> visible = all.stream()
                .filter(r -> canView(r, username, roles, admin))
                .collect(Collectors.toList());

        int total = visible.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        List<Map<String, Object>> records = fromIndex >= total ? new ArrayList<>() :
                visible.subList(fromIndex, toIndex).stream().map(this::toSummary).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> create(HttpServletRequest req, CustomReport report) {
        return createForAi(SecurityUtils.getCurrentUsername(req), report);
    }

    @Override
    public Map<String, Object> createForAi(String username, CustomReport report) {
        validateForSave(report, null);
        report.setReportId(null);
        report.setReportCode(generateReportCode());
        report.setCreatedBy(username);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        if (report.getStatus() == null || report.getStatus().isEmpty()) report.setStatus("启用");
        if (report.getShareScope() == null || report.getShareScope().isEmpty()) report.setShareScope("私有");
        if (!"共享".equals(report.getShareScope())) report.setShareRoles(null);
        customReportMapper.insert(report);
        return toDetail(report);
    }

    @Override
    public Map<String, Object> update(HttpServletRequest req, Long id, CustomReport report) {
        CustomReport exist = customReportMapper.selectById(id);
        if (exist == null) throw new IllegalArgumentException("报表不存在");
        requireOwner(req, exist);

        report.setReportId(id);
        report.setReportCode(exist.getReportCode());
        report.setCreatedBy(exist.getCreatedBy());
        report.setCreatedAt(exist.getCreatedAt());
        report.setUpdatedAt(LocalDateTime.now());
        if (report.getStatus() == null || report.getStatus().isEmpty()) report.setStatus(exist.getStatus());
        if (report.getShareScope() == null || report.getShareScope().isEmpty()) report.setShareScope(exist.getShareScope());
        if (!"共享".equals(report.getShareScope())) report.setShareRoles(null);
        validateForSave(report, req);
        customReportMapper.updateById(report);
        return toDetail(customReportMapper.selectById(id));
    }

    @Override
    public void delete(HttpServletRequest req, Long id) {
        CustomReport exist = customReportMapper.selectById(id);
        if (exist == null) throw new IllegalArgumentException("报表不存在");
        requireOwner(req, exist);
        customReportMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> copy(HttpServletRequest req, Long id) {
        CustomReport exist = customReportMapper.selectById(id);
        if (exist == null) throw new IllegalArgumentException("报表不存在");
        if (!canView(exist, SecurityUtils.getCurrentUsername(req), SecurityUtils.getCurrentRoles(req), SecurityUtils.isAdmin(req))) {
            throw new IllegalArgumentException("无权访问该报表");
        }
        exist.setReportId(null);
        exist.setReportCode(generateReportCode());
        exist.setReportName(exist.getReportName() + "-副本");
        exist.setShareScope("私有");
        exist.setShareRoles(null);
        exist.setCreatedBy(SecurityUtils.getCurrentUsername(req));
        exist.setCreatedAt(LocalDateTime.now());
        exist.setUpdatedAt(LocalDateTime.now());
        customReportMapper.insert(exist);
        return toDetail(exist);
    }

    @Override
    public Map<String, Object> detail(HttpServletRequest req, Long id) {
        CustomReport exist = customReportMapper.selectById(id);
        if (exist == null) throw new IllegalArgumentException("报表不存在");
        if (!canView(exist, SecurityUtils.getCurrentUsername(req), SecurityUtils.getCurrentRoles(req), SecurityUtils.isAdmin(req))) {
            throw new IllegalArgumentException("无权访问该报表");
        }
        Map<String, Object> m = toDetail(exist);
        m.put("defaultDateField", findDefaultDateField(exist.getDataSource()));
        return m;
    }

    /**
     * 查看页日期筛选兜底：报表未配置 dateField 时，取主表默认日期字段
     * （优先 createdAt/单据日期类，否则第一个 date/datetime 字段），供前端展示日期筛选条。
     */
    private Map<String, String> findDefaultDateField(String dataSourceKey) {
        try {
            EntityMetadataRegistry.DataSourceMeta ds = registry.getDataSource(dataSourceKey);
            if (ds == null || ds.fields == null) return null;
            Map<String, String> first = null;
            for (Map.Entry<String, EntityMetadataRegistry.FieldMeta> e : ds.fields.entrySet()) {
                EntityMetadataRegistry.FieldMeta fm = e.getValue();
                if (!"date".equals(fm.type) && !"datetime".equals(fm.type)) continue;
                String name = fm.field != null ? fm.field : e.getKey();
                if ("createdAt".equals(name) || name.toLowerCase().contains("date")) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("source", "main");
                    m.put("field", name);
                    return m;
                }
                if (first == null) {
                    first = new LinkedHashMap<>();
                    first.put("source", "main");
                    first.put("field", name);
                }
            }
            return first;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ==================== 运行与导出 ====================

    @Override
    public Map<String, Object> run(HttpServletRequest req, Long id, String startDate, String endDate,
                                   String keyword, String status, int page, int size) {
        return run(req, id, startDate, endDate, keyword, status, page, size, null, null, null);
    }

    @Override
    public Map<String, Object> run(HttpServletRequest req, Long id, String startDate, String endDate,
                                   String keyword, String status, int page, int size,
                                   String dateSource, String dateField) {
        return run(req, id, startDate, endDate, keyword, status, page, size, dateSource, dateField, null);
    }

    @Override
    public Map<String, Object> run(HttpServletRequest req, Long id, String startDate, String endDate,
                                   String keyword, String status, int page, int size,
                                   String dateSource, String dateField, String conditionsJson) {
        CustomReport report = requireRunnable(req, id);
        EntityMetadataRegistry.DataSourceMeta dsMeta = registry.getDataSource(report.getDataSource());
        ReportConfig config = parseConfig(report.getConfigJson());
        applyDateFieldOverride(config, dateSource, dateField);
        CustomReportSqlBuilder.RunFilters filters = new CustomReportSqlBuilder.RunFilters();
        filters.startDate = startDate;
        filters.endDate = endDate;
        filters.keyword = keyword;
        filters.status = status;
        filters.conditions = parseConditions(conditionsJson);

        CustomReportSqlBuilder.BuiltQuery q = sqlBuilder.build(registry, dsMeta, config, req, filters);

        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (size < 1) size = 20;
        if (page < 1) page = 1;

        Long total = jdbcTemplate.queryForObject(q.countSql, Long.class, q.params.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        if (total != null && total > 0) {
            List<Object> pageParams = new ArrayList<>(q.params);
            pageParams.add(size);
            pageParams.add((long) (page - 1) * size);
            records = jdbcTemplate.queryForList(q.selectSql + " LIMIT ? OFFSET ?", pageParams.toArray());
        }

        // 字段权限：与列表/明细报表同一份"不可见字段"规则（运行结果、列定义、合计行一起剔）
        Set<String> hidden = hiddenForReport(dsMeta, config);
        rejectHiddenConditions(hidden, filters.conditions);
        fieldPermissionOutputFilter.filterRecords(hidden, records);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        result.put("mode", config.isSummary() ? "summary" : "detail");
        if (config.isSummary() && config.chart != null && config.chart.type != null) {
            Map<String, Object> chart = new LinkedHashMap<>();
            chart.put("type", config.chart.type);
            result.put("chart", chart);
        }
        List<Map<String, Object>> cols = new ArrayList<>();
        for (CustomReportSqlBuilder.ColDef c : q.columns) {
            if (matchesHidden(hidden, c.field)) continue;
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("field", c.field);
            cm.put("label", c.label);
            cm.put("type", c.type);
            cols.add(cm);
        }
        result.put("columns", cols);
        // 汇总模式：整体合计行（分组列置"合计"/空，聚合列取整体重算值）
        if (q.totalsSql != null) {
            result.put("totals", fieldPermissionOutputFilter.filterRecord(hidden, queryTotalsRow(q, config)));
        }
        return result;
    }

    @Override
    public void export(HttpServletRequest req, HttpServletResponse resp, Long id, String startDate, String endDate,
                       String keyword, String status) {
        export(req, resp, id, startDate, endDate, keyword, status, null, null, null);
    }

    @Override
    public void export(HttpServletRequest req, HttpServletResponse resp, Long id, String startDate, String endDate,
                       String keyword, String status, String dateSource, String dateField) {
        export(req, resp, id, startDate, endDate, keyword, status, dateSource, dateField, null);
    }

    @Override
    public void export(HttpServletRequest req, HttpServletResponse resp, Long id, String startDate, String endDate,
                       String keyword, String status, String dateSource, String dateField, String conditionsJson) {
        CustomReport report = requireRunnable(req, id);
        EntityMetadataRegistry.DataSourceMeta dsMeta = registry.getDataSource(report.getDataSource());
        ReportConfig config = parseConfig(report.getConfigJson());
        applyDateFieldOverride(config, dateSource, dateField);
        CustomReportSqlBuilder.RunFilters filters = new CustomReportSqlBuilder.RunFilters();
        filters.startDate = startDate;
        filters.endDate = endDate;
        filters.keyword = keyword;
        filters.status = status;
        filters.conditions = parseConditions(conditionsJson);

        CustomReportSqlBuilder.BuiltQuery q = sqlBuilder.build(registry, dsMeta, config, req, filters);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                q.selectSql + " LIMIT " + CustomReportSqlBuilder.MAX_EXPORT_ROWS, q.params.toArray());

        // 汇总模式：末尾附加整体合计行
        if (q.totalsSql != null) {
            Map<String, Object> totalsRow = queryTotalsRow(q, config);
            if (totalsRow != null) records.add(totalsRow);
        }

        // 字段权限：导出文件与运行结果同一份规则（表头与数据一起剔）
        Set<String> hidden = hiddenForReport(dsMeta, config);
        rejectHiddenConditions(hidden, filters.conditions);
        fieldPermissionOutputFilter.filterRecords(hidden, records);

        List<String[]> headers = q.columns.stream()
                .filter(c -> !matchesHidden(hidden, c.field))
                .map(c -> new String[]{c.label, c.field})
                .collect(Collectors.toList());
        StandardExcelUtil.export(resp, report.getReportName(), headers, records);
    }

    // ==================== 可见性校验（供仪表盘等服务复用） ====================

    @Override
    public CustomReport getReportIfVisible(HttpServletRequest req, Long id) {
        CustomReport report = customReportMapper.selectById(id);
        if (report == null) throw new IllegalArgumentException("报表不存在");
        if (!canView(report, SecurityUtils.getCurrentUsername(req), SecurityUtils.getCurrentRoles(req), SecurityUtils.isAdmin(req))) {
            throw new IllegalArgumentException("无权访问该报表");
        }
        return report;
    }

    @Override
    public CustomReport requireRunnable(HttpServletRequest req, Long id) {
        CustomReport report = getReportIfVisible(req, id);
        if (!"启用".equals(report.getStatus())) {
            throw new IllegalArgumentException("报表已停用");
        }
        if (!registry.isRegistered(report.getDataSource())) {
            throw new IllegalArgumentException("报表数据源已失效: " + report.getDataSource());
        }
        return report;
    }

    // ==================== 内部方法 ====================


    /** 保存前校验：数据源白名单 + 长度限制 + 全量字段引用校验（明细/汇总模式分流，含跨单关联） */
    private void validateForSave(CustomReport report, HttpServletRequest req) {
        if (report.getReportName() == null || report.getReportName().trim().isEmpty()) {
            throw new IllegalArgumentException("报表名称不能为空");
        }
        if (report.getReportName().length() > 100) {
            throw new IllegalArgumentException("报表名称过长");
        }
        if (!registry.isRegistered(report.getDataSource())) {
            throw new IllegalArgumentException("数据源不在白名单内: " + report.getDataSource());
        }
        if (report.getConfigJson() != null && report.getConfigJson().length() > MAX_CONFIG_JSON_LENGTH) {
            throw new IllegalArgumentException("报表配置超出大小限制");
        }
        ReportConfig config = parseConfig(report.getConfigJson());
        if (config == null) {
            throw new IllegalArgumentException("报表配置不能为空");
        }
        EntityMetadataRegistry.DataSourceMeta dsMeta = registry.getDataSource(report.getDataSource());
        // 全量校验委托 SqlBuilder：明细列/汇总分组聚合/跨单关联/筛选/排序/图表类型
        sqlBuilder.validateConfig(registry, dsMeta, config, req);
        // 保存即试跑：用拼好的查询真实执行 1 行（零之二铁律「报表要能查出来」前移到保存时），
        // 参数配对/语法/类型等运行期问题当场拦截，坏配置进不了库（2026-11-17「未设定参数值7」实锤后新增）
        dryRunQuery(req, report.getDataSource(), config);
    }

    /** 保存时真实试跑 1 行：查询无法执行时给用户看得懂的中文原因，不再等到运行页才炸 */
    private void dryRunQuery(HttpServletRequest req, String dataSourceKey, ReportConfig config) {
        try {
            EntityMetadataRegistry.DataSourceMeta dsMeta = registry.getDataSource(dataSourceKey);
            CustomReportSqlBuilder.RunFilters filters = new CustomReportSqlBuilder.RunFilters();
            CustomReportSqlBuilder.BuiltQuery q = sqlBuilder.build(registry, dsMeta, config, req, filters);
            jdbcTemplate.queryForList(q.selectSql + " LIMIT 1", q.params.toArray());
        } catch (org.springframework.dao.DataAccessException e) {
            String root = rootCauseMessage(e);
            log.warn("[自定义报表] 保存试跑失败: {}", root);
            throw new IllegalArgumentException("报表配置无法执行，请检查指标条件/公式/计算列等配置（原因: "
                    + truncateText(root, 120) + "）");
        }
    }

    private String rootCauseMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private String truncateText(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 汇总模式整体合计行：totalsSql 无 GROUP BY 整体重算（SUM求和/COUNT求和/AVG整体平均/MAX MIN整体极值），
     * 分组列第一个置"合计"、其余置 null（列顺序 = 分组列在前、聚合列在后，与 parseSummarySpec 一致）
     */
    private Map<String, Object> queryTotalsRow(CustomReportSqlBuilder.BuiltQuery q, ReportConfig config) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(q.totalsSql, q.totalsParams.toArray());
        if (rows.isEmpty()) return null;
        Map<String, Object> t = rows.get(0);
        int groupCount = (config.groupBy == null) ? 0 : config.groupBy.size();
        Map<String, Object> row = new LinkedHashMap<>();
        boolean first = true;
        int i = 0;
        for (CustomReportSqlBuilder.ColDef c : q.columns) {
            if (i < groupCount) {
                row.put(c.field, first ? "合计" : null);
                first = false;
            } else {
                row.put(c.field, t.get(c.field));
            }
            i++;
        }
        return row;
    }

    /**
     * 运行时日期筛选字段覆盖：报表未配置 dateField 且请求带了 dateSource/dateField 时生效。
     * 仅修改本次请求的临时 config 实例，不落库；白名单校验由 build 阶段
     * appendFilters→resolveRef 执行（非法引用抛"日期筛选字段不在白名单内"）。
     */
    private void applyDateFieldOverride(ReportConfig config, String dateSource, String dateField) {
        if (config.dateField != null) return; // 报表已配置，忽略覆盖
        if (dateSource == null || dateSource.trim().isEmpty()
                || dateField == null || dateField.trim().isEmpty()) return;
        ReportConfig.ColRef ref = new ReportConfig.ColRef();
        ref.source = dateSource.trim();
        ref.field = dateField.trim();
        config.dateField = ref;
    }

    private ReportConfig parseConfig(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(configJson, ReportConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("报表配置格式错误");
        }
    }

    /**
     * 解析运行时「任意字段过滤条件」JSON 数组（[{source,field,op,value,value2,values}]）。
     * 此处只做格式解析；字段白名单（含自定义字段）与操作符白名单校验在 SqlBuilder 拼 SQL 前执行，
     * 与报表配置的校验同一套，避免两处口径漂移。
     */
    private List<ReportConfig.Condition> parseConditions(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(conditionsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<ReportConfig.Condition>>() {
                    });
        } catch (Exception e) {
            throw new IllegalArgumentException("筛选条件格式错误");
        }
    }

    private boolean canView(CustomReport r, String username, List<String> roles, boolean admin) {
        if (admin) return true;
        if (username != null && username.equals(r.getCreatedBy())) return true;
        if ("共享".equals(r.getShareScope()) && r.getShareRoles() != null && roles != null && !roles.isEmpty()) {
            Set<String> mine = new HashSet<>(roles);
            return Arrays.stream(r.getShareRoles().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(mine::contains);
        }
        return false;
    }

    private void requireOwner(HttpServletRequest req, CustomReport report) {
        if (SecurityUtils.isAdmin(req)) return;
        String username = SecurityUtils.getCurrentUsername(req);
        if (username == null || !username.equals(report.getCreatedBy())) {
            throw new IllegalArgumentException("仅报表创建人可修改/删除");
        }
    }

    /** CR + yyMMdd + 3位流水，冲突自增重试 */
    private String generateReportCode() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        long todayCount = customReportMapper.selectCount(new LambdaQueryWrapper<CustomReport>()
                .likeRight(CustomReport::getReportCode, "CR" + datePart));
        int seq = (int) todayCount + 1;
        while (true) {
            String code = String.format("CR%s-%03d", datePart, seq);
            Long exists = customReportMapper.selectCount(new LambdaQueryWrapper<CustomReport>()
                    .eq(CustomReport::getReportCode, code));
            if (exists == null || exists == 0) return code;
            seq++;
        }
    }

    private Map<String, Object> toSummary(CustomReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", r.getReportId());
        m.put("reportCode", r.getReportCode());
        m.put("reportName", r.getReportName());
        m.put("dataSource", r.getDataSource());
        m.put("dataSourceName", registry.getDataSourceName(r.getDataSource()));
        m.put("status", r.getStatus());
        m.put("shareScope", r.getShareScope());
        m.put("shareRoles", r.getShareRoles());
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", r.getCreatedAt());
        m.put("remark", r.getRemark());
        // 报表模式（detail/summary）：仪表盘设计器卡片标签用，解析失败按明细处理
        ReportConfig cfg = null;
        try {
            cfg = parseConfig(r.getConfigJson());
        } catch (Exception ignored) {
        }
        m.put("mode", cfg != null && cfg.isSummary() ? "summary" : "detail");
        return m;
    }

    private Map<String, Object> toDetail(CustomReport r) {
        Map<String, Object> m = toSummary(r);
        m.put("configJson", r.getConfigJson());
        m.put("updatedAt", r.getUpdatedAt());
        return m;
    }
}
