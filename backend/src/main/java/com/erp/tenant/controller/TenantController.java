package com.erp.tenant.controller;

import com.erp.auth.util.JwtUtil;
import com.erp.common.Result;
import com.erp.tenant.support.TenantExpiry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {
    private final JdbcTemplate platformJdbcTemplate;
    private final JwtUtil jwtUtil;

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> myTenants(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.error("未登录");
        }
        List<String> roles = (List<String>) request.getAttribute("currentRoles");
        boolean isSuperAdmin = roles != null && roles.contains("SUPER_ADMIN");

        List<Map<String, Object>> rows;
        if (isSuperAdmin) {
            rows = platformJdbcTemplate.queryForList(
                "SELECT t.tenant_id, t.tenant_code, t.tenant_name, t.status, t.expire_date, " +
                "COALESCE(ut.is_admin_in_tenant, 1) AS is_admin_in_tenant, ut.last_login_time " +
                "FROM sys_tenant t " +
                "LEFT JOIN sys_user_tenant ut ON ut.tenant_id = t.tenant_id AND ut.user_id = ? AND ut.status = 'active' " +
                "WHERE t.status = 'active' " +
                "ORDER BY ut.last_login_time DESC NULLS LAST, t.tenant_id",
                userId);
        } else {
            // 普通用户：已到期客户不再出现在可选公司里（默认客户与未设置到期日的永不限制）
            rows = platformJdbcTemplate.queryForList(
                "SELECT t.tenant_id, t.tenant_code, t.tenant_name, t.status, t.expire_date, " +
                "ut.is_admin_in_tenant, ut.last_login_time " +
                "FROM sys_user_tenant ut " +
                "JOIN sys_tenant t ON ut.tenant_id = t.tenant_id " +
                "WHERE ut.user_id = ? AND ut.status = 'active' AND t.status = 'active' " +
                "AND (t.expire_date IS NULL OR t.expire_date >= CURRENT_DATE OR UPPER(t.tenant_code) = 'DEFAULT') " +
                "ORDER BY ut.last_login_time DESC NULLS LAST",
                userId);
        }

        List<Map<String, Object>> tenants = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> t = new HashMap<>();
            t.put("tenantId", row.get("tenant_id") != null ? ((Number) row.get("tenant_id")).longValue() : null);
            t.put("tenantCode", row.get("tenant_code"));
            t.put("tenantName", row.get("tenant_name"));
            // 状态按到期日期实时派生（不改库）：正常但已过到期日 → expired，供平台超管识别并续费
            String statusText = row.get("status") == null ? null : String.valueOf(row.get("status"));
            if ("active".equalsIgnoreCase(statusText)
                    && TenantExpiry.isExpired((String) row.get("tenant_code"), row.get("expire_date"))) {
                statusText = "expired";
            }
            t.put("status", statusText);
            t.put("expireDate", row.get("expire_date"));
            Object isAdmin = row.get("is_admin_in_tenant");
            t.put("isAdminInTenant", isAdmin instanceof Number ? ((Number) isAdmin).intValue() : (isAdmin != null ? Integer.parseInt(isAdmin.toString()) : 0));
            t.put("lastLoginTime", row.get("last_login_time"));
            tenants.add(t);
        }
        return Result.success(tenants);
    }

    @PostMapping("/select")
    public Result<Map<String, Object>> selectTenant(@RequestBody SelectTenantRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String username = (String) request.getAttribute("currentUsername");
        if (userId == null) {
            return Result.error("未登录");
        }
        Long tenantId = req.getTenantId();
        if (tenantId == null) {
            return Result.error("客户信息无效，请重新登录");
        }
        // ===== 客户可用性校验（2026-11-09）=====
        // 选公司是"真正进入系统"的最后一道关：即使前端缓存了旧公司列表、或直接调接口，
        // 到期客户也必须挡在这里。平台超管放行（唯一用途是进入后台办理续费）。
        List<Map<String, Object>> targetRows = platformJdbcTemplate.queryForList(
            "SELECT tenant_code, tenant_name, status, expire_date FROM sys_tenant WHERE tenant_id = ?", tenantId);
        if (targetRows.isEmpty()) {
            return Result.error("客户不存在");
        }
        Map<String, Object> targetTenant = targetRows.get(0);
        if (!"active".equalsIgnoreCase(String.valueOf(targetTenant.get("status")))) {
            return Result.error("该客户已停用，请联系服务商");
        }
        if (TenantExpiry.isExpired((String) targetTenant.get("tenant_code"), targetTenant.get("expire_date"))
                && !isPlatformSuperAdmin(userId)) {
            log.warn("选取已到期客户被拒: userId={}, tenantId={}, expireDate={}",
                    userId, tenantId, targetTenant.get("expire_date"));
            return Result.error("该客户已到期，请联系服务商续费");
        }
        List<String> roles = (List<String>) request.getAttribute("currentRoles");
        boolean isSuperAdmin = roles != null && roles.contains("SUPER_ADMIN");

        // 验证用户是否有权访问该租户
        List<Map<String, Object>> links = platformJdbcTemplate.queryForList(
            "SELECT * FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ? AND status = 'active'",
            userId, tenantId);

        if (links.isEmpty() && isSuperAdmin) {
            // SUPER_ADMIN 自动补关联后放行
            platformJdbcTemplate.update(
                "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                "SELECT ?, ?, 1, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?)",
                userId, tenantId, userId, tenantId);
            links = platformJdbcTemplate.queryForList(
                "SELECT * FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ? AND status = 'active'",
                userId, tenantId);
        }

        if (links.isEmpty()) {
            // 单租户自愈：历史新增用户缺关联记录（旧版 saveUser 不写 sys_user_tenant）。
            // 若目标租户是平台库唯一 active 租户，自动补关联后放行（幂等）。
            try {
                List<Long> activeTenantIds = platformJdbcTemplate.queryForList(
                    "SELECT tenant_id FROM sys_tenant WHERE status = 'active' ORDER BY tenant_id", Long.class);
                if (activeTenantIds.size() == 1 && activeTenantIds.get(0).equals(tenantId)) {
                    platformJdbcTemplate.update(
                        "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                        "SELECT ?, ?, 0, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                        "WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?)",
                        userId, tenantId, userId, tenantId);
                    links = platformJdbcTemplate.queryForList(
                        "SELECT * FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ? AND status = 'active'",
                        userId, tenantId);
                }
            } catch (Exception healEx) {
                // 自愈失败走原有报错
            }
        }
        if (links.isEmpty()) {
            // 租户库 fallback 账号放行（2026-09-02 修复"登录先报无权访问该租户"误报）：
            // 员工档案创建的账号只写租户库 sys_user，平台库无身份。
            // 登录接口对此类账号走 fallback 模式返回默认租户(tenantId=1)；
            // 本接口必须与登录行为对齐：平台库无此 user_id 时直接按默认租户放行，
            // 否则每次登录都会先弹"无权访问该租户"再成功。
            try {
                Long platformUserCount = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE user_id = ?", Long.class, userId);
                if (platformUserCount == null || platformUserCount == 0) {
                    links = List.of(Map.of("user_id", userId, "tenant_id", tenantId,
                            "status", "active", "is_admin_in_tenant", 0));
                }
            } catch (Exception fallbackEx) {
                // 平台库不可用（单机客户环境）走原有报错逻辑
            }
        }
        if (links.isEmpty()) {
            // ===== 归属关系终局自愈（2026-10-12）：平台库有账号记录但缺租户关联 =====
            // 真源是租户库账号本身，平台库关联只是映射：直连目标租户库按用户名确认账号
            // 真实存在（启用中）→ 自动补关联放行（幂等）。修"重启自愈时间窗内登录被拒"
            // 与"历史清理残留的有记录无关联僵尸账号"（单租户自愈在多租户下永不触发）。
            try {
                Map<String, Object> tenantRow = platformJdbcTemplate.queryForMap(
                    "SELECT tenant_id, db_name, db_host, db_port, db_username, db_password " +
                    "FROM sys_tenant WHERE tenant_id = ? AND status = 'active'", tenantId);
                String dbName = (String) tenantRow.get("db_name");
                if (dbName != null && !dbName.isBlank() && username != null && !username.isBlank()) {
                    String host = tenantRow.get("db_host") == null ? "localhost" : String.valueOf(tenantRow.get("db_host"));
                    int port = 5432;
                    try { if (tenantRow.get("db_port") != null) port = Integer.parseInt(String.valueOf(tenantRow.get("db_port"))); } catch (Exception ignore) { }
                    String dbUser = tenantRow.get("db_username") == null ? "erp_user" : String.valueOf(tenantRow.get("db_username"));
                    String dbPwd = tenantRow.get("db_password") == null ? "Erp@2026" : String.valueOf(tenantRow.get("db_password"));
                    boolean userExistsInTenantDb = false;
                    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                            "jdbc:postgresql://" + host + ":" + port + "/" + dbName, dbUser, dbPwd);
                         java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM sys_user WHERE username = ? AND is_enabled = 1")) {
                        ps.setString(1, username);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) userExistsInTenantDb = rs.getInt(1) > 0;
                        }
                    }
                    if (userExistsInTenantDb) {
                        platformJdbcTemplate.update(
                            "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                            "SELECT ?, ?, 0, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                            "WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?)",
                            userId, tenantId, userId, tenantId);
                        links = platformJdbcTemplate.queryForList(
                            "SELECT * FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ? AND status = 'active'",
                            userId, tenantId);
                        log.info("租户归属自愈: 平台库缺关联已按租户库实名补建 username={}, tenantId={}", username, tenantId);
                    }
                }
            } catch (Exception healEx) {
                log.warn("租户归属终局自愈失败(按原口径报无权): username={}, tenantId={}, err={}",
                        username, tenantId, healEx.getMessage());
            }
        }
        if (links.isEmpty()) {
            return Result.error("无权访问该租户");
        }
        // 更新最后登录时间
        platformJdbcTemplate.update(
            "UPDATE sys_user_tenant SET last_login_time = CURRENT_TIMESTAMP WHERE user_id = ? AND tenant_id = ?",
            userId, tenantId);
        // 生成带tenantId的新token
        String newToken = jwtUtil.generateTokenWithTenant(userId, username, tenantId);
        // 查询租户信息
        Map<String, Object> tenant = platformJdbcTemplate.queryForMap(
            "SELECT tenant_id, tenant_code, tenant_name FROM sys_tenant WHERE tenant_id = ?", tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("token", newToken);
        result.put("tenantId", tenantId);
        result.put("tenantName", tenant.get("tenant_name"));
        result.put("tenantCode", tenant.get("tenant_code"));
        return Result.success(result);
    }

    /**
     * 是否平台超级管理员（客户到期后唯一仍可进入的运维身份，用于办理续费）。
     * 与登录接口同口径：查平台库"平台角色"关系；异常一律按非超管处理。
     */
    private boolean isPlatformSuperAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            Integer count = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_platform_user_role pur " +
                            "JOIN sys_platform_role pr ON pur.role_id = pr.role_id " +
                            "WHERE pur.user_id = ? AND pr.role_code IN ('PLATFORM_ADMIN', 'SUPER_ADMIN')",
                    Integer.class, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("查询平台超管身份失败(按非超管处理): userId={}, err={}", userId, e.getMessage());
            return false;
        }
    }

    @Data
    public static class SelectTenantRequest {
        private Long tenantId;
    }
}
