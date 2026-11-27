package com.erp.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一数据权限解析器（2026-09-02 数据权限体系落地）
 *
 * 权限模型（与用户确认的语义一致）：
 * 1. 数据范围（sys_role.data_scope）是所有模块数据的过滤基础：
 *    - 仅本人数据：只能看到自己创建的数据（created_by=当前用户名），业务归属字段（如 saler_id）同样算"自己的"
 *    - 本部门数据：能看到本部门所有用户创建的数据
 *    - 全部数据：不过滤
 * 2. 员工权限补充（sys_config.employee_perm_{employeeId}）是叠加白名单：
 *    配置了白名单的仓库/客户/供应商/货品类别，额外可见
 * 3. 菜单权限只决定能否进入页面，与数据可见性无关
 *
 * 直通规则：SUPER_ADMIN / ADMIN 角色、is_admin=1 的用户 → 全部数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataScopeResolver {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_SELF = "SELF";

    @Getter
    public static class DataScopeContext {
        private final String scope;
        private final String username;
        private final Long userId;
        private final Long employeeId;
        private final List<String> deptUsernames;
        private final List<Long> deptUserIds;
        private final List<Long> deptEmployeeIds;
        private final List<Long> warehouseWhitelist;
        private final List<Long> customerWhitelist;
        private final List<Long> supplierWhitelist;
        private final List<Long> categoryWhitelist;

        DataScopeContext(String scope, String username, Long userId, Long employeeId,
                         List<String> deptUsernames, List<Long> deptUserIds, List<Long> deptEmployeeIds,
                         List<Long> warehouseWhitelist, List<Long> customerWhitelist,
                         List<Long> supplierWhitelist, List<Long> categoryWhitelist) {
            this.scope = scope;
            this.username = username;
            this.userId = userId;
            this.employeeId = employeeId;
            this.deptUsernames = deptUsernames == null ? Collections.emptyList() : deptUsernames;
            this.deptUserIds = deptUserIds == null ? Collections.emptyList() : deptUserIds;
            this.deptEmployeeIds = deptEmployeeIds == null ? Collections.emptyList() : deptEmployeeIds;
            this.warehouseWhitelist = warehouseWhitelist == null ? Collections.emptyList() : warehouseWhitelist;
            this.customerWhitelist = customerWhitelist == null ? Collections.emptyList() : customerWhitelist;
            this.supplierWhitelist = supplierWhitelist == null ? Collections.emptyList() : supplierWhitelist;
            this.categoryWhitelist = categoryWhitelist == null ? Collections.emptyList() : categoryWhitelist;
        }

        public boolean isAll() {
            return SCOPE_ALL.equals(scope);
        }

        /**
         * 单据/主档通用过滤（created_by 维度，Service 层可直接调用）。
         * scope=SELF → created_by=当前用户名（ownerField 非空时 OR 业务归属=当前员工）
         * scope=DEPT → created_by IN 部门用户名（ownerField 非空时 OR 业务归属 IN 部门员工）
         *
         * @param qw         查询条件
         * @param ownerField 业务归属字段名（如 sale_order.saler_id），无则传 null
         */
        public void applyTo(QueryWrapper<?> qw, String ownerField) {
            applyTo(qw, ownerField, "created_by");
        }

        /**
         * 同上，支持自定义创建人列名（部分表用 create_by 而非 created_by，如 sale_pre_receipt）。
         */
        public void applyTo(QueryWrapper<?> qw, String ownerField, String createdByColumn) {
            if (isAll()) return;
            qw.and(w -> applyScopeInner(w, createdByColumn, ownerField));
        }

        /**
         * 数据范围本体条件（不含外层括号）：created_by 维度 + 业务归属维度。
         * applyTo / applyToWithWhitelist 共用本方法，禁止再各写一套（否则又是"两处口径漂移"）。
         */
        private void applyScopeInner(QueryWrapper<?> w, String createdByColumn, String ownerField) {
            if (SCOPE_DEPT.equals(scope)) {
                if (!deptUsernames.isEmpty()) {
                    w.in(createdByColumn, deptUsernames);
                } else {
                    w.eq(createdByColumn, username);
                }
                if (ownerField != null && !deptEmployeeIds.isEmpty()) {
                    w.or().in(ownerField, deptEmployeeIds);
                }
            } else {
                if (username != null) {
                    w.eq(createdByColumn, username);
                } else {
                    // 无用户名的极端场景，条件恒假防止越权
                    w.apply("1 = 0");
                }
                if (ownerField != null && employeeId != null) {
                    w.or().eq(ownerField, employeeId);
                }
            }
        }

        /**
         * 带白名单叠加的过滤（主档类：仓库/客户/供应商/物料类别）。
         * 可见数据 = 数据范围过滤结果 OR 白名单内的记录。
         */
        public void applyToWithWhitelist(QueryWrapper<?> qw, String idField, List<Long> whitelist) {
            applyToWithWhitelist(qw, idField, whitelist, null, "created_by");
        }

        /**
         * 同上，支持自定义归属列与创建人列（导出取数等场景复用同一实现，保证与列表逐字同口径）。
         */
        public void applyToWithWhitelist(QueryWrapper<?> qw, String idField, List<Long> whitelist,
                                         String ownerField, String createdByColumn) {
            if (isAll()) return;
            if (whitelist == null || whitelist.isEmpty()) {
                applyTo(qw, ownerField, createdByColumn);
                return;
            }
            qw.and(outer -> {
                outer.and(inner -> applyScopeInner(inner, createdByColumn, ownerField));
                outer.or().in(idField, whitelist);
            });
        }
    }

    /**
     * 解析当前请求用户的数据范围上下文。
     * 依赖 AuthInterceptor 注入的 currentUserId/currentUsername/currentRoles/currentEmployeeId/currentDeptId。
     */
    public DataScopeContext resolve(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String username = (String) request.getAttribute("currentUsername");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("currentRoles");
        Long employeeId = (Long) request.getAttribute("currentEmployeeId");
        Long deptId = (Long) request.getAttribute("currentDeptId");
        return resolveForUser(username, userId, employeeId, deptId, roles);
    }

    /**
     * 非 Web 请求场景（AI 工具、定时任务）按指定用户解析数据范围。
     * 口径与 {@link #resolve(HttpServletRequest)} 完全一致，避免两套判断漂移。
     */
    public DataScopeContext resolveForUser(String username, Long userId, Long employeeId, Long deptId,
                                           List<String> roles) {
        // 1. 管理员直通
        if (roles != null && (roles.contains("SUPER_ADMIN") || roles.contains("ADMIN"))) {
            return allContext(username, userId, employeeId);
        }
        if (userId != null) {
            try {
                Integer isAdmin = jdbcTemplate.queryForObject(
                        "SELECT is_admin FROM sys_user WHERE user_id = ?", Integer.class, userId);
                if (isAdmin != null && isAdmin == 1) {
                    return allContext(username, userId, employeeId);
                }
            } catch (Exception e) {
                log.warn("[DataScope] 查询 is_admin 失败 userId={}: {}", userId, e.getMessage());
            }
        }

        // 2. 取用户所有角色的 data_scope，按最宽优先（ALL > DEPT > SELF）
        String scope = SCOPE_SELF;
        if (userId != null) {
            try {
                List<String> scopes = jdbcTemplate.queryForList(
                        "SELECT r.data_scope FROM sys_user_role ur JOIN sys_role r ON ur.role_id = r.role_id "
                                + "WHERE ur.user_id = ? AND r.data_scope IS NOT NULL", String.class, userId);
                int best = -1;
                for (String s : scopes) {
                    int level = scopeLevel(normalizeScope(s));
                    if (level > best) best = level;
                }
                if (best >= 0) scope = levelOf(best);
            } catch (Exception e) {
                log.warn("[DataScope] 查询角色 data_scope 失败 userId={}: {}", userId, e.getMessage());
            }
        }

        // 3. 本部门用户集合（DEPT 范围用）
        List<String> deptUsernames = Collections.emptyList();
        List<Long> deptUserIds = Collections.emptyList();
        List<Long> deptEmployeeIds = Collections.emptyList();
        if (SCOPE_DEPT.equals(scope)) {
            if (deptId == null) {
                // 无部门信息时降级为仅本人，避免越权
                scope = SCOPE_SELF;
            } else {
                try {
                    deptUsernames = jdbcTemplate.queryForList(
                            "SELECT username FROM sys_user WHERE dept_id = ? AND username IS NOT NULL",
                            String.class, deptId);
                    deptUserIds = jdbcTemplate.queryForList(
                            "SELECT user_id FROM sys_user WHERE dept_id = ?",
                            Long.class, deptId);
                    deptEmployeeIds = jdbcTemplate.queryForList(
                            "SELECT employee_id FROM sys_user WHERE dept_id = ? AND employee_id IS NOT NULL",
                            Long.class, deptId);
                } catch (Exception e) {
                    log.warn("[DataScope] 查询部门用户失败 deptId={}: {}", deptId, e.getMessage());
                    scope = SCOPE_SELF;
                    deptUsernames = Collections.emptyList();
                    deptUserIds = Collections.emptyList();
                    deptEmployeeIds = Collections.emptyList();
                }
            }
        }

        // 4. 员工权限补充白名单
        List<Long> warehouseWl = Collections.emptyList();
        List<Long> customerWl = Collections.emptyList();
        List<Long> supplierWl = Collections.emptyList();
        List<Long> categoryWl = Collections.emptyList();
        if (employeeId != null) {
            try {
                String configValue = jdbcTemplate.queryForObject(
                        "SELECT config_value FROM sys_config WHERE config_key = ?",
                        String.class, "employee_perm_" + employeeId);
                if (configValue != null && !configValue.isBlank()) {
                    JsonNode node = JSON.readTree(configValue);
                    warehouseWl = readIdArray(node, "warehouseIds");
                    customerWl = readIdArray(node, "customerIds");
                    supplierWl = readIdArray(node, "supplierIds");
                    categoryWl = readIdArray(node, "categoryIds");
                }
            } catch (Exception e) {
                // 无配置或解析失败=无白名单，不影响主流程
                log.debug("[DataScope] 员工白名单未配置或解析失败 employeeId={}: {}", employeeId, e.getMessage());
            }
        }

        return new DataScopeContext(scope, username, userId, employeeId,
                deptUsernames, deptUserIds, deptEmployeeIds,
                warehouseWl, customerWl, supplierWl, categoryWl);
    }

    /**
     * 在「当前 Web 请求」上下文内按数据范围为查询追加条件（统一入口，禁止各调用方自己拼）。
     *
     * <p>用于「汇总报表 / 导出 / 统计」等不便从 Controller 传 ctx 的场景：内部自取当前请求。</p>
     * <ul>
     *   <li>无 Web 请求（定时任务、内部调用）→ 不加条件（保持旧行为）</li>
     *   <li>全部数据（管理员等）→ 不加条件</li>
     * </ul>
     *
     * @param qw               查询条件
     * @param ownerField       业务归属列（如 saler_id/buyer_id），无则传 null
     * @param createdByColumn  创建人列（多数 created_by；部分表 create_by/applicant_name）
     */
    public void applyIfLimited(QueryWrapper<?> qw, String ownerField, String createdByColumn) {
        jakarta.servlet.http.HttpServletRequest req = null;
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            req = attrs == null ? null : attrs.getRequest();
        } catch (Exception ignored) {
            // 非 Web 上下文：不套权限
        }
        if (req == null) return;
        DataScopeContext ctx = resolve(req);
        if (ctx.isAll()) return;
        ctx.applyTo(qw, ownerField, createdByColumn);
    }

    // ===========================================================================
    // 「可见主档」统一口径（唯一一份，禁止各取数口子再各写一套）
    //
    // 背景（2026-10-09）：系统里同一份主档存在两套取数口子 —— 档案列表页（已按数据范围过滤）
    // 与"轻量联想/下拉"（另开接口、未过滤）。每新增一处下拉就漏一次权限，表现为"仅本人数据
    // 的角色仍能在筛选框里翻出全公司的客户/供应商/物料/仓库"。
    //
    // 口径：取数口子（档案列表页 + 列表页筛选下拉 + 单据选择器 + 报表取数）一律调用本组方法，
    // 做到"档案列表能看到什么，下拉就只能选什么"。
    // ===========================================================================

    /** 可见客户（客户档案 / 客户下拉统一口径）：数据范围 + 客户白名单 + 档案域准入（仅正式客户与存量档案客户）。 */
    public void applyVisibleCustomers(QueryWrapper<?> qw, HttpServletRequest req) {
        applyVisibleCustomers(qw, resolve(req));
    }

    /**
     * 同上（服务层内部取数用）：在「当前 Web 请求」上下文内自取请求。
     * 无 Web 请求（定时任务 / 内部调用）→ 不套条件，保持旧行为。
     */
    public void applyVisibleCustomers(QueryWrapper<?> qw) {
        HttpServletRequest req = currentRequestOrNull();
        if (req == null) return;
        applyVisibleCustomers(qw, req);
    }

    /** 取当前请求（非 Web 上下文返回 null） */
    private static HttpServletRequest currentRequestOrNull() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /** 同上（已持有上下文时复用，避免重复解析）。 */
    public void applyVisibleCustomers(QueryWrapper<?> qw, DataScopeContext ctx) {
        ctx.applyToWithWhitelist(qw, "customer_id", ctx.getCustomerWhitelist());
        // 档案域准入：CRM 私海培育中的潜在/意向/休眠/已流失客户不进客户档案与单据选客户下拉
        qw.and(w -> w.eq("crm_status", "正式客户").or().isNull("crm_status"));
    }

    /** 可见供应商（供应商档案 / 供应商下拉统一口径）：数据范围 + 供应商白名单。 */
    public void applyVisibleSuppliers(QueryWrapper<?> qw, HttpServletRequest req) {
        DataScopeContext ctx = resolve(req);
        ctx.applyToWithWhitelist(qw, "supplier_id", ctx.getSupplierWhitelist());
    }

    /** 可见仓库（仓库档案 / 仓库下拉统一口径）：数据范围 + 仓库白名单。 */
    public void applyVisibleWarehouses(QueryWrapper<?> qw, HttpServletRequest req) {
        DataScopeContext ctx = resolve(req);
        ctx.applyToWithWhitelist(qw, "stock_id", ctx.getWarehouseWhitelist());
    }

    /** 可见物料（物料档案 / 物料下拉统一口径）：数据范围 + 货品类别白名单。 */
    public void applyVisibleMaterials(QueryWrapper<?> qw, HttpServletRequest req) {
        DataScopeContext ctx = resolve(req);
        ctx.applyToWithWhitelist(qw, "category_id", ctx.getCategoryWhitelist());
    }

    /**
     * 可见批次（当前请求上下文自取）：批次主表 batch_info 无仓库/归属列，
     * 经 batch_inventory 关联收敛到「可见仓库」。
     *
     * @return false = 无任何可见仓库，调用方必须直接返回空结果（不要执行 SQL）
     */
    public boolean applyVisibleBatches(QueryWrapper<?> qw) {
        HttpServletRequest req = currentRequestOrNull();
        if (req == null) return true;
        DataScopeContext ctx = resolve(req);
        if (ctx.isAll()) return true;
        List<Long> whIds = visibleWarehouseIds(ctx);
        if (whIds == null) return true;
        if (whIds.isEmpty()) return false;
        String in = whIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        qw.inSql("id", "SELECT batch_id FROM batch_inventory WHERE warehouse_id IN (" + in + ")");
        return true;
    }

    /**
     * 当前请求下可见的仓库ID集合（自取请求）。
     *
     * @return null = 不过滤（无请求上下文或全部数据）；空列表 = 无任何可见仓库；非空 = 可见仓库集合
     */
    public List<Long> visibleWarehouseIdsForCurrentRequest() {
        HttpServletRequest req = currentRequestOrNull();
        if (req == null) return null;
        DataScopeContext ctx = resolve(req);
        return visibleWarehouseIds(ctx);
    }

    /**
     * 数据范围 → 可见「归属人用户ID」集合（客户私海、需按 owner_user_id 过滤的场景统一用本方法）。
     *
     * <p><b>为什么必须有这个方法</b>：客户私海列表与私海卡片各写一套归属人解析，导致同一页面
     * 两个数字口径不一致（历史 BUG：卡片显示 2、列表只有 1）。此后凡"按 owner 用户ID 过滤私海数据"
     * 一律调用本方法，禁止各调用方自己拼。</p>
     *
     * @param ctx           数据范围上下文
     * @param currentUserId 当前登录用户ID
     * @return null = 不过滤（全部数据）；空列表 = 无任何可见归属人（应返回 0 条）；非空 = 可见归属人集合
     */
    public List<Long> visibleOwnerUserIds(DataScopeContext ctx, Long currentUserId) {
        if (ctx == null || ctx.isAll()) return null;
        if (SCOPE_DEPT.equals(ctx.getScope())) {
            if (!ctx.getDeptUserIds().isEmpty()) return ctx.getDeptUserIds();
            // 部门信息缺失：降级为仅本人，避免越权
            return currentUserId != null ? List.of(currentUserId) : Collections.emptyList();
        }
        return currentUserId != null ? List.of(currentUserId) : Collections.emptyList();
    }

    /**
     * 员工ID → 用户ID。
     *
     * <p><b>为什么要有这个转换</b>：前端"分配销售员"下拉取的是员工列表，提交的是 <b>员工ID</b>；
     * 而客户归属列 owner_user_id 存的是 <b>用户ID</b>。历史 BUG：批量分配把员工ID直接写进 owner_user_id，
     * 导致"分配给我的客户"在我自己的私海里看不到、卡片数字与列表条数对不上。
     * 凡"拿员工ID去写 owner_user_id"的场景，必须先经本方法转换。</p>
     *
     * @return 对应启用中的用户ID；查不到返回 null（调用方不得把 null 写进 owner_user_id）
     */
    public Long userIdByEmployeeId(Long employeeId) {
        if (employeeId == null) return null;
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT user_id FROM sys_user WHERE employee_id = ? ORDER BY user_id LIMIT 1",
                    Long.class, employeeId);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("[DataScope] 员工ID转用户ID失败 employeeId={}: {}", employeeId, e.getMessage());
            return null;
        }
    }

    /** 用户ID → 员工ID（写 assigned_sales_id 等"员工ID列"时用）；查不到返回 null */
    public Long employeeIdByUserId(Long userId) {
        if (userId == null) return null;
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT employee_id FROM sys_user WHERE user_id = ? AND employee_id IS NOT NULL",
                    Long.class, userId);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("[DataScope] 用户ID转员工ID失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 计算当前用户可见的仓库ID集合（库存查询用）。
     * 可见仓库 = 数据范围内的仓库（created_by） ∪ 员工白名单仓库。
     *
     * @return null 表示不过滤（全部可见）；空列表表示无任何可见仓库；非空为白名单集合
     */
    public List<Long> visibleWarehouseIds(DataScopeContext ctx) {
        if (ctx.isAll()) return null;
        List<Long> ids = new ArrayList<>();
        if (SCOPE_DEPT.equals(ctx.getScope()) && !ctx.getDeptUsernames().isEmpty()) {
            ids.addAll(jdbcTemplate.queryForList(
                    "SELECT stock_id FROM warehouse WHERE created_by IN ("
                            + String.join(",", Collections.nCopies(ctx.getDeptUsernames().size(), "?")) + ")",
                    Long.class, ctx.getDeptUsernames().toArray()));
        } else if (ctx.getUsername() != null) {
            ids.addAll(jdbcTemplate.queryForList(
                    "SELECT stock_id FROM warehouse WHERE created_by = ?", Long.class, ctx.getUsername()));
        }
        // 白名单仓库直接可见
        for (Long wid : ctx.getWarehouseWhitelist()) {
            if (!ids.contains(wid)) ids.add(wid);
        }
        return ids;
    }

    private DataScopeContext allContext(String username, Long userId, Long employeeId) {
        return new DataScopeContext(SCOPE_ALL, username, userId, employeeId,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    /** data_scope 字段值标准化（兼容中文配置值与英文枚举值） */
    private String normalizeScope(String raw) {
        if (raw == null) return SCOPE_SELF;
        String s = raw.trim();
        if (s.contains("全部") || "all".equalsIgnoreCase(s)) return SCOPE_ALL;
        if (s.contains("部门") || "dept".equalsIgnoreCase(s)) return SCOPE_DEPT;
        return SCOPE_SELF;
    }

    private int scopeLevel(String scope) {
        switch (scope) {
            case SCOPE_ALL: return 2;
            case SCOPE_DEPT: return 1;
            default: return 0;
        }
    }

    private String levelOf(int level) {
        switch (level) {
            case 2: return SCOPE_ALL;
            case 1: return SCOPE_DEPT;
            default: return SCOPE_SELF;
        }
    }

    private List<Long> readIdArray(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n != null && n.canConvertToLong()) {
                long v = n.asLong();
                if (v > 0) ids.add(v);
            }
        }
        return ids;
    }
}
