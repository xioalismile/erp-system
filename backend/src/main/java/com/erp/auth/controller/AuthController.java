package com.erp.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.auth.util.JwtUtil;
import com.erp.common.Result;
import com.erp.config.AuthInterceptor;
import com.erp.config.ErpProperties;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import com.erp.tenant.TenantContextHolder;
import com.erp.tenant.support.TenantExpiry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper sysUserMapper;
    private final ErpProperties erpProperties;
    /** 权限版本号来源（角色/权限变更时自增，见 AuthInterceptor#evictAll） */
    private final AuthInterceptor authInterceptor;

    @Autowired(required = false)
    @Qualifier("platformJdbcTemplate")
    private JdbcTemplate platformJdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        Long userId = null;
        String username = null;
        String realName = null;
        String storedPassword = null;
        boolean isFallbackMode = true;
        List<Map<String, Object>> verifiedTenants = new ArrayList<>();
        // 密码校验通过但客户已到期（2026-11-09）：与"密码错误"区分开，给出明确提示；
        // 平台超管仍可凭此进入（用于给客户续费），普通用户直接拒绝登录。
        List<Map<String, Object>> expiredTenants = new ArrayList<>();
        // 密码正确但员工档案已离职/停用（2026-11-12）：单独计数，用于把"离职"与"密码错误"区分开
        List<Map<String, Object>> inactiveTenants = new ArrayList<>();

        // ===== 1) 平台库优先：按用户名查平台库 → 拿租户列表 → 逐个连租户库验密码 =====
        // 设计原则（2026-10-12 重构）：密码只存在租户库，平台库只做用户名→租户的映射。
        // 这样租户员工账号不需要跨公司查重，每个租户自己验自己的密码。
        if (platformJdbcTemplate != null) {
            try {
                List<Map<String, Object>> platformUsers = platformJdbcTemplate.queryForList(
                        "SELECT user_id, username, real_name FROM sys_user WHERE username = ? AND is_enabled = 1",
                        req.getUsername());
                if (!platformUsers.isEmpty()) {
                    Map<String, Object> user = platformUsers.get(0);
                    Long platformUserId = ((Number) user.get("user_id")).longValue();
                    username = (String) user.get("username");
                    realName = (String) user.getOrDefault("real_name", username);

                    // 查该用户挂的所有 active 租户（含租户库连接信息）
                    List<Map<String, Object>> tenantRows = platformJdbcTemplate.queryForList(
                            "SELECT t.tenant_id, t.tenant_code, t.tenant_name, t.db_name, t.db_host, t.db_port, " +
                                    "t.db_username, t.db_password, t.expire_date, ut.is_admin_in_tenant, ut.last_login_time " +
                                    "FROM sys_user_tenant ut " +
                                    "JOIN sys_tenant t ON ut.tenant_id = t.tenant_id " +
                                    "WHERE ut.user_id = ? AND ut.status = 'active' AND t.status = 'active' " +
                                    "ORDER BY ut.last_login_time DESC NULLS LAST",
                            platformUserId);

                    if (!tenantRows.isEmpty()) {
                        userId = platformUserId;
                        isFallbackMode = false;

                        // 逐个连租户库查 sys_user 密码并比对
                        for (Map<String, Object> row : tenantRows) {
                            String dbName = (String) row.get("db_name");
                            String dbHost = row.get("db_host") != null ? (String) row.get("db_host") : "localhost";
                            int dbPort = row.get("db_port") != null ? ((Number) row.get("db_port")).intValue() : 5432;
                            String dbUsername = (String) row.get("db_username");
                            String dbPassword = (String) row.get("db_password");

                            TenantLoginRow loginRow = loadTenantLoginRow(username, dbName, dbHost, dbPort, dbUsername, dbPassword);
                            String tenantPwd = loginRow == null ? null : loginRow.password;
                            if (tenantPwd != null && (tenantPwd.startsWith("$2a$") || tenantPwd.startsWith("$2b$"))
                                    && passwordEncoder.matches(req.getPassword(), tenantPwd)) {
                                // 员工档案已离职/停用（2026-11-12）：密码对也不能进，与"密码错误"分开提示。
                                // 未绑定员工档案的纯系统账号（管理员等）不受此限。
                                if (!isEmployeeActive(loginRow.employeeStatus)) {
                                    inactiveTenants.add(row);
                                    log.warn("密码校验通过但员工已离职/停用: username={}, tenant={}, 员工状态={}",
                                            username, row.get("tenant_code"), loginRow.employeeStatus);
                                    continue;
                                }
                                if (TenantExpiry.isExpired((String) row.get("tenant_code"), row.get("expire_date"))) {
                                    expiredTenants.add(row);
                                    log.info("密码校验通过但客户已到期: username={}, tenant={}, expireDate={}",
                                            username, row.get("tenant_code"), row.get("expire_date"));
                                } else {
                                    verifiedTenants.add(row);
                                    log.info("密码校验通过: username={}, tenant={}, db={}", username, row.get("tenant_code"), dbName);
                                }
                            }
                        }

                        // ===== 归属发现（2026-11-12）：已有归属的账号也要能发现"其它公司库里的同名账号" =====
                        // 旧逻辑只在"一条归属都没有"的孤儿场景跨公司扫描，已有 1 条归属的人永远
                        // 不会发现第二家公司（121 实锤：张天养在日晖库有账号，却只挂了农标一家，
                        // 登录只返回 1 家公司 → 直进、不弹选择，日晖永远进不去）。
                        // 判定必须同时满足：用户名一致 + 密码校验通过 + 姓名一致（两侧都有值时）+ 账号启用 + 公司未到期。
                        // 只按用户名合并会把不同公司的同名人合成一个，故禁止。
                        // 注意：必须放在"是否已到期"判定之前 —— 他唯一挂着的公司到期时，
                        // 也要先发现其它正常公司，不能直接以"已到期"把人挡在外面。
                        if (!verifiedTenants.isEmpty() || !expiredTenants.isEmpty() || !inactiveTenants.isEmpty()) {
                            try {
                                discoverTenantMemberships(username, realName, req.getPassword(), userId,
                                        tenantRows, verifiedTenants);
                            } catch (Exception e) {
                                log.warn("归属发现失败(不影响登录): username={}, err={}", username, e.getMessage());
                            }
                        }

                        if (verifiedTenants.isEmpty()) {
                            if (!expiredTenants.isEmpty()) {
                                // 密码正确但客户全部到期（2026-11-09）：必须明确告知"已到期"，不能混成"密码错误"
                                if (!isPlatformSuperAdmin(userId)) {
                                    log.warn("登录失败: 客户已到期 username={}, 到期客户数={}", username, expiredTenants.size());
                                    return Result.error("该客户已到期，请联系服务商续费");
                                }
                                log.info("平台超管登录到期客户(用于续费): username={}, 到期客户数={}", username, expiredTenants.size());
                            } else if (!inactiveTenants.isEmpty()) {
                                // 密码正确但员工已离职/停用（2026-11-12）：与"密码错误"区分开
                                log.warn("登录失败: 员工已离职/停用 username={}, 公司数={}", username, inactiveTenants.size());
                                return Result.error("该账号已停用（离职），请联系管理员");
                            } else {
                                // 所有租户库密码都不对
                                log.warn("登录失败: 用户名={} 在所有租户库密码均不匹配", username);
                                return Result.error("用户名或密码错误");
                            }
                        }

                        log.info("多租户登录: username={}, 匹配租户数={}", username, verifiedTenants.size());
                    } else {
                        // ===== 孤儿账号登录自愈（2026-10-12）：平台库有记录但无租户关联 =====
                        // 场景：重启自愈"先插记录、后插关联"的时间窗内登录 / 历史清理残留。
                        // 原行为：走主库兜底返回假 default 租户 → 前端自动选它 → 选租户校验失败
                        // 报「无权访问该租户」→ 前端吞错假装登录成功 → 卡在登录页（121 实锤）。
                        // 现行为：当场直连各 active 租户库验密，验过者自动补关联（幂等）并计入
                        // 可登录租户——登录一次即完成自愈，不依赖重启。
                        try {
                            List<Map<String, Object>> activeTenants = platformJdbcTemplate.queryForList(
                                    "SELECT tenant_id, tenant_code, tenant_name, db_name, db_host, db_port, db_username, db_password, expire_date " +
                                            "FROM sys_tenant WHERE status = 'active'");
                            for (Map<String, Object> t : activeTenants) {
                                String tName = (String) t.get("db_name");
                                String tHost = t.get("db_host") != null ? (String) t.get("db_host") : "localhost";
                                int tPort = t.get("db_port") != null ? ((Number) t.get("db_port")).intValue() : 5432;
                                TenantLoginRow loginRow = loadTenantLoginRow(username, tName, tHost, tPort,
                                        (String) t.get("db_username"), (String) t.get("db_password"));
                                String tenantPwd = loginRow == null ? null : loginRow.password;
                                if (tenantPwd != null && (tenantPwd.startsWith("$2a$") || tenantPwd.startsWith("$2b$"))
                                        && passwordEncoder.matches(req.getPassword(), tenantPwd)) {
                                    // 员工已离职/停用（2026-11-12）：密码对也不补关联、不发放登录身份
                                    if (!isEmployeeActive(loginRow.employeeStatus)) {
                                        inactiveTenants.add(t);
                                        log.warn("孤儿账号密码匹配但员工已离职/停用: username={}, tenant={}, 员工状态={}",
                                                username, t.get("tenant_code"), loginRow.employeeStatus);
                                        continue;
                                    }
                                    if (TenantExpiry.isExpired((String) t.get("tenant_code"), t.get("expire_date"))) {
                                        // 密码对得上但客户已到期：补关联没有意义，只记录以便上层给出"已到期"提示
                                        expiredTenants.add(t);
                                        log.info("孤儿账号密码匹配但客户已到期: username={}, tenant={}", username, t.get("tenant_code"));
                                        continue;
                                    }
                                    Long tId = ((Number) t.get("tenant_id")).longValue();
                                    platformJdbcTemplate.update(
                                            "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                                                    "SELECT ?, ?, 0, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                                                    "WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?)",
                                            platformUserId, tId, platformUserId, tId);
                                    verifiedTenants.add(t);
                                    log.info("孤儿账号自愈: 平台库缺租户关联已补建 username={}, tenant={}, db={}",
                                            username, t.get("tenant_code"), tName);
                                }
                            }
                            if (!verifiedTenants.isEmpty()) {
                                userId = platformUserId;
                                isFallbackMode = false;
                                log.info("多租户登录(自愈后): username={}, 匹配租户数={}", username, verifiedTenants.size());
                            } else if (!expiredTenants.isEmpty()) {
                                if (!isPlatformSuperAdmin(platformUserId)) {
                                    log.warn("登录失败: 客户已到期(孤儿账号) username={}, 到期客户数={}", username, expiredTenants.size());
                                    return Result.error("该客户已到期，请联系服务商续费");
                                }
                                userId = platformUserId;
                                isFallbackMode = false;
                                log.info("平台超管登录到期客户(孤儿账号,用于续费): username={}", username);
                            } else if (!inactiveTenants.isEmpty()) {
                                log.warn("登录失败: 员工已离职/停用(孤儿账号) username={}, 公司数={}", username, inactiveTenants.size());
                                return Result.error("该账号已停用（离职），请联系管理员");
                            }
                        } catch (Exception orphanEx) {
                            log.warn("孤儿账号登录自愈失败(走单租户兜底): username={}, err={}", username, orphanEx.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // 平台库异常不阻断登录，走单租户兜底
                log.debug("平台库登录查询异常，走单租户兜底: {}", e.getMessage());
            }
        }

        // ===== 2) 单租户兜底：直接查业务库 sys_user 验密码 =====
        if (userId == null) {
            try {
                SysUser u = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername())
                        .eq(SysUser::getIsEnabled, 1)
                        .last("LIMIT 1"));
                if (u != null) {
                    userId = u.getUserId();
                    username = u.getUsername();
                    realName = u.getRealName() != null ? u.getRealName() : username;
                    storedPassword = u.getPassword();
                    log.info("单租户模式查到用户: userId={}, username={}", userId, username);
                }
            } catch (Exception ex) {
                log.warn("业务库查用户失败: {}", ex.getMessage());
            }

            // 业务库 MyBatis 没命中，再试 JdbcTemplate
            if (userId == null) {
                try {
                    List<Map<String, Object>> businessUsers = jdbcTemplate.queryForList(
                            "SELECT user_id, username, real_name, password FROM sys_user WHERE username = ? AND is_enabled = 1",
                            req.getUsername());
                    if (!businessUsers.isEmpty()) {
                        Map<String, Object> user = businessUsers.get(0);
                        userId = ((Number) user.get("user_id")).longValue();
                        username = (String) user.get("username");
                        realName = (String) user.getOrDefault("real_name", username);
                        storedPassword = (String) user.get("password");
                    }
                } catch (Exception ignored) { }
            }
        }

        if (userId == null) {
            log.warn("登录失败: 用户不存在 username={}", req.getUsername());
            return Result.error("用户名或密码错误");
        }

        // 单租户模式验密码
        if (isFallbackMode) {
            boolean passwordMatch = storedPassword != null
                    && (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$"))
                    && passwordEncoder.matches(req.getPassword(), storedPassword);
            if (!passwordMatch) {
                log.warn("登录失败: 密码不匹配 userId={}", userId);
                return Result.error("用户名或密码错误");
            }
            // 员工档案已离职/停用（2026-11-12）：单库形态同样拦截，未绑定员工的纯系统账号不受限
            try {
                List<String> empStatus = jdbcTemplate.queryForList(
                        "SELECT e.status FROM sys_user u LEFT JOIN employee e ON e.employee_id = u.employee_id " +
                                "WHERE u.user_id = ?", String.class, userId);
                if (!empStatus.isEmpty() && !isEmployeeActive(empStatus.get(0))) {
                    log.warn("登录失败: 员工已离职/停用 userId={}, 员工状态={}", userId, empStatus.get(0));
                    return Result.error("该账号已停用（离职），请联系管理员");
                }
            } catch (Exception ignore) {
                // 员工表不可用时不阻断登录（保持旧行为）
            }
        }

        String token = jwtUtil.generateToken(userId, username);

        // ===== 3) 构建租户列表 =====
        List<Map<String, Object>> tenants = new ArrayList<>();
        if (isFallbackMode) {
            Map<String, Object> defaultTenant = new HashMap<>();
            defaultTenant.put("tenantId", 1L);
            defaultTenant.put("tenantCode", "default");
            defaultTenant.put("tenantName", "默认公司");
            defaultTenant.put("lastLoginTime", null);
            tenants.add(defaultTenant);
        } else {
            for (Map<String, Object> row : verifiedTenants) {
                Map<String, Object> tenant = new HashMap<>();
                tenant.put("tenantId", ((Number) row.get("tenant_id")).longValue());
                tenant.put("tenantCode", row.get("tenant_code"));
                tenant.put("tenantName", row.get("tenant_name"));
                tenant.put("lastLoginTime", row.get("last_login_time"));
                tenants.add(tenant);
            }
            // 平台超管：按"人员-客户关联"列出全部正常客户（与"切换公司"同一口径），已到期的加标记。
            // 原因：超管进入客户不依赖租户库密码（选公司只校验归属），若只看密码命中项，
            // 密码与主租户不同的关联客户会整块消失，到期客户也就无法在列表里被识别和续费。
            if (isPlatformSuperAdmin(userId)) {
                try {
                    List<Map<String, Object>> linked = platformJdbcTemplate.queryForList(
                            "SELECT t.tenant_id, t.tenant_code, t.tenant_name, t.expire_date, ut.last_login_time " +
                                    "FROM sys_user_tenant ut JOIN sys_tenant t ON ut.tenant_id = t.tenant_id " +
                                    "WHERE ut.user_id = ? AND ut.status = 'active' AND t.status = 'active' " +
                                    "ORDER BY ut.last_login_time DESC NULLS LAST, t.tenant_id",
                            userId);
                    if (!linked.isEmpty()) {
                        tenants.clear();
                        for (Map<String, Object> row : linked) {
                            Map<String, Object> tenant = new HashMap<>();
                            tenant.put("tenantId", ((Number) row.get("tenant_id")).longValue());
                            tenant.put("tenantCode", row.get("tenant_code"));
                            tenant.put("tenantName", row.get("tenant_name"));
                            tenant.put("lastLoginTime", row.get("last_login_time"));
                            if (TenantExpiry.isExpired((String) row.get("tenant_code"), row.get("expire_date"))) {
                                tenant.put("expired", Boolean.TRUE);
                            }
                            tenants.add(tenant);
                        }
                    }
                } catch (Exception e) {
                    log.warn("平台超管客户列表查询失败(保留密码命中列表): userId={}, err={}", userId, e.getMessage());
                }
            }
            if (tenants.isEmpty()) {
                Map<String, Object> defaultTenant = new HashMap<>();
                defaultTenant.put("tenantId", 1L);
                defaultTenant.put("tenantCode", "default");
                defaultTenant.put("tenantName", "默认公司");
                defaultTenant.put("lastLoginTime", null);
                tenants.add(defaultTenant);
            }
        }

        boolean requireTenantSelect = tenants.size() > 1;

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("realName", realName);
        result.put("requireTenantSelect", requireTenantSelect);
        result.put("tenants", tenants);
        return Result.success(result);
    }

    /**
     * 是否平台超级管理员（客户到期后唯一仍可进入的运维身份，用于办理续费）。
     * 判定依据平台库"平台角色"关系；表缺失或查询异常一律按非超管处理（宁严不松）。
     */
    private boolean isPlatformSuperAdmin(Long userId) {
        if (userId == null || platformJdbcTemplate == null) {
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

    /** 租户库中的登录行：密码哈希 + 绑定员工的在职状态（未绑定员工时为 null） */
    private static class TenantLoginRow {
        final String password;
        final String employeeStatus;

        TenantLoginRow(String password, String employeeStatus) {
            this.password = password;
            this.employeeStatus = employeeStatus;
        }
    }

    /**
     * 员工在职判定（2026-11-12）：员工档案为空、未填状态、或历史同义值（正常/启用）按"在职"处理，
     * 只有明确写成"离职/停用"等其它值才拒绝。历史数据不误拦；与工序认领等处口径一致。
     */
    private boolean isEmployeeActive(String status) {
        if (status == null || status.isBlank()) return true;
        String s = status.trim();
        return "在职".equals(s) || "正常".equals(s) || "启用".equals(s);
    }

    /**
     * 直连租户库查询「密码哈希 + 员工在职状态」（2026-10-12 新增，2026-11-12 加员工状态）。
     * 密码只存在租户库，平台库不存密码；登录时按租户逐个验密。
     * db_username/db_password 为空时用默认 erp_user / Erp@2026。
     *
     * <p>兼容兜底：老库没有 employee 表 / 状态列时，关联查询会报错 ——
     * 此时退回"只查密码"的旧口径（视为无员工绑定），绝不能让登录整体失败。</p>
     */
    private TenantLoginRow loadTenantLoginRow(String username, String dbName, String dbHost, int dbPort,
                                              String dbUsername, String dbPassword) {
        if (username == null || dbName == null) return null;
        String user = dbUsername != null ? dbUsername : "erp_user";
        String pwd = dbPassword != null ? dbPassword : "Erp@2026";
        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        try (Connection conn = DriverManager.getConnection(url, user, pwd)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.password, e.status AS employee_status FROM sys_user u " +
                            "LEFT JOIN employee e ON e.employee_id = u.employee_id " +
                            "WHERE u.username = ? AND u.is_enabled = 1 LIMIT 1")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new TenantLoginRow(rs.getString("password"), rs.getString("employee_status"));
                    }
                }
                return null;
            } catch (Exception joinEx) {
                // 老库缺 employee 表/状态列：退回只查密码（不阻断登录）
                log.debug("租户库员工表/状态列不可用，按无员工绑定登录: db={}, err={}", dbName, joinEx.getMessage());
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT password FROM sys_user WHERE username = ? AND is_enabled = 1 LIMIT 1")) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return new TenantLoginRow(rs.getString("password"), null);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("连租户库查账号失败: db={}, username={}, err={}", dbName, username, e.getMessage());
        }
        return null;
    }

    /**
     * 归属发现（2026-11-12）：把「同一账号存在于其它公司库」补齐为公司归属。
     *
     * <p>解决：已有 1 条公司归属的账号永远发现不了第二家公司 —— 旧逻辑只在"一条归属都没有"
     * 的孤儿场景跨公司扫描，所以登录只返回 1 家公司 → 直进、不弹选择，第二家公司永远进不去
     * （121 实锤：张天养在日晖库有账号，却只挂了农标一家）。</p>
     *
     * <p>自动补归属的唯一安全判据是「用户名 + 密码 + 姓名」三者都对得上：
     * 不同公司允许同名账号（2026-10-12 口径），只按用户名合并会把两个不同的人合成一个。
     * 通过后写一条归属记录（幂等），本次登录立即返回该公司，以后登录持续可见。</p>
     *
     * <p>任何单个公司连不上/查不到都只跳过该公司，绝不影响登录本身。</p>
     */
    private void discoverTenantMemberships(String username, String realName, String rawPassword, Long userId,
                                           List<Map<String, Object>> associatedRows,
                                           List<Map<String, Object>> verifiedTenants) {
        if (platformJdbcTemplate == null || userId == null || username == null
                || rawPassword == null || rawPassword.isEmpty()) {
            return;
        }
        Set<Long> knownTenantIds = new HashSet<>();
        for (Map<String, Object> row : associatedRows) {
            Object id = row.get("tenant_id");
            if (id instanceof Number) knownTenantIds.add(((Number) id).longValue());
        }
        List<Map<String, Object>> activeTenants;
        try {
            activeTenants = platformJdbcTemplate.queryForList(
                    "SELECT tenant_id, tenant_code, tenant_name, db_name, db_host, db_port, " +
                            "db_username, db_password, expire_date FROM sys_tenant " +
                            "WHERE status = 'active' ORDER BY tenant_id");
        } catch (Exception e) {
            log.debug("归属发现: 读取公司清单失败 username={}, err={}", username, e.getMessage());
            return;
        }
        String normalizedName = realName == null ? null : realName.trim();
        for (Map<String, Object> tenant : activeTenants) {
            Object idObj = tenant.get("tenant_id");
            if (!(idObj instanceof Number)) continue;
            Long tenantId = ((Number) idObj).longValue();
            if (knownTenantIds.contains(tenantId)) continue;
            String dbName = tenant.get("db_name") == null ? null : String.valueOf(tenant.get("db_name"));
            if (dbName == null || dbName.isBlank()) continue;
            String tenantCode = tenant.get("tenant_code") == null ? null : String.valueOf(tenant.get("tenant_code"));
            try {
                String host = tenant.get("db_host") == null ? "localhost" : String.valueOf(tenant.get("db_host"));
                int port = 5432;
                if (tenant.get("db_port") != null) {
                    try { port = Integer.parseInt(String.valueOf(tenant.get("db_port"))); } catch (Exception ignore) { }
                }
                String dbUser = tenant.get("db_username") == null ? "erp_user" : String.valueOf(tenant.get("db_username"));
                String dbPwd = tenant.get("db_password") == null ? "Erp@2026" : String.valueOf(tenant.get("db_password"));
                String tenantHash = null;
                String tenantRealName = null;
                String tenantEmpStatus = null;
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql://" + host + ":" + port + "/" + dbName, dbUser, dbPwd);
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT u.password, u.real_name, e.status AS employee_status FROM sys_user u " +
                                     "LEFT JOIN employee e ON e.employee_id = u.employee_id " +
                                     "WHERE u.username = ? AND u.is_enabled = 1 LIMIT 1")) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            tenantHash = rs.getString("password");
                            tenantRealName = rs.getString("real_name");
                            tenantEmpStatus = rs.getString("employee_status");
                        }
                    }
                }
                if (tenantHash == null || !(tenantHash.startsWith("$2a$") || tenantHash.startsWith("$2b$"))) {
                    continue;
                }
                if (!passwordEncoder.matches(rawPassword, tenantHash)) {
                    continue;
                }
                // 员工已离职/停用：不能把离职的人再挂回这家公司
                if (!isEmployeeActive(tenantEmpStatus)) {
                    log.info("归属发现跳过(员工已离职/停用): username={}, tenant={}, 员工状态={}",
                            username, tenantCode, tenantEmpStatus);
                    continue;
                }
                String tenantNameTrimmed = tenantRealName == null ? null : tenantRealName.trim();
                if (normalizedName != null && !normalizedName.isEmpty()
                        && tenantNameTrimmed != null && !tenantNameTrimmed.isEmpty()
                        && !normalizedName.equals(tenantNameTrimmed)) {
                    log.info("归属发现跳过(同名不同人): username={}, tenant={}", username, tenantCode);
                    continue;
                }
                if (TenantExpiry.isExpired(tenantCode, tenant.get("expire_date"))) {
                    continue;
                }
                platformJdbcTemplate.update(
                        "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                                "SELECT ?, ?, 0, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                                "WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?)",
                        userId, tenantId, userId, tenantId);
                verifiedTenants.add(tenant);
                knownTenantIds.add(tenantId);
                log.info("归属发现: 已补建公司归属 username={}, tenant={}, db={}", username, tenantCode, dbName);
            } catch (Exception e) {
                log.debug("归属发现: 公司库检查跳过 username={}, tenant={}, err={}", username, tenantCode, e.getMessage());
            }
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    /**
     * 权限版本号（2026-10-12）：前端定时轮询，发现版本变化即重拉 userinfo，
     * 使「角色权限设置 → 按钮权限」的改动**无需重新登录**即在各端生效。
     * 变化来源 = 任何角色/权限保存（后端 evictAll/evict 处自增）。
     */
    @GetMapping("/perm-version")
    public Result<Map<String, Object>> permVersion() {
        return Result.success(Map.of("version", authInterceptor.getPermissionVersion()));
    }

    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userInfo(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("currentUserId");
        String username = (String) req.getAttribute("currentUsername");

        Map<String, Object> info = new HashMap<>();
        info.put("userId", userId);
        info.put("username", username);

        // 统一从 sys_user_role -> sys_role 动态查询角色
        // 同时查 is_admin 用于角色兜底
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    "SELECT real_name, is_admin FROM sys_user WHERE user_id = ?", userId);
        } catch (Exception ex) {
            if (platformJdbcTemplate != null) {
                user = platformJdbcTemplate.queryForMap(
                        "SELECT real_name, is_admin FROM sys_user WHERE user_id = ?", userId);
            } else {
                user = new HashMap<>();
                user.put("real_name", username);
                user.put("is_admin", 0);
            }
        }
        info.put("realName", user.get("real_name"));
        boolean isAdmin = user.get("is_admin") != null && ((Number) user.get("is_admin")).intValue() == 1;

        List<String> roleCodes = new ArrayList<>();
        List<Long> roleIds = new ArrayList<>();
        try {
            List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT r.role_id, r.role_code FROM sys_user_role ur " +
                    "JOIN sys_role r ON ur.role_id = r.role_id WHERE ur.user_id = ?", userId);
            for (Map<String, Object> row : roleRows) {
                Object rc = row.get("role_code");
                if (rc != null) roleCodes.add(rc.toString().toUpperCase());
                Object rid = row.get("role_id");
                if (rid != null) roleIds.add(((Number) rid).longValue());
            }
        } catch (Exception ignored) { }
        if (roleCodes.isEmpty() && platformJdbcTemplate != null) {
            try {
                List<Map<String, Object>> roleRows = platformJdbcTemplate.queryForList(
                        "SELECT DISTINCT r.role_id, r.role_code FROM sys_user_role ur " +
                        "JOIN sys_role r ON ur.role_id = r.role_id WHERE ur.user_id = ?", userId);
                for (Map<String, Object> row : roleRows) {
                    Object rc = row.get("role_code");
                    if (rc != null) roleCodes.add(rc.toString().toUpperCase());
                    Object rid = row.get("role_id");
                    if (rid != null) roleIds.add(((Number) rid).longValue());
                }
            } catch (Exception ignored) { }
        }

        if (roleCodes.isEmpty()) {
            roleCodes.add(isAdmin ? "ADMIN" : "VIEWER");
        }
        if (isAdmin && !roleCodes.contains("ADMIN")) {
            roleCodes.add(0, "ADMIN");
        }
        info.put("roles", roleCodes);

        // —— 关键：版本控制逻辑 ——
        // 配置开关 erp.tenant.version-control-enabled:
        //   false (默认): 独立部署模式，所有租户不受版本限制，保持全功能访问
        //   true: 多租户模式，只有默认租户(ID=1)不受限制，其他租户按版本限制
        // 连接了平台库（platformJdbcTemplate 可用）= 多租户/SaaS 形态，
        // 版本控制强制生效，不依赖人工配置（历史bug：本地开关默认 false，
        // 导致独立库租户被当 ULTIMATE 全量放行）；独立部署客户包无平台库则保持 false。
        boolean versionControlEnabled = erpProperties.isTenantVersionControlEnabled()
                || platformJdbcTemplate != null;
        Long tenantId = TenantContextHolder.getTenantId();
        String tenantVersion = null;
        
        // 独立部署模式：直接设置为 ULTIMATE，跳过版本限制
        if (!versionControlEnabled) {
            tenantVersion = "ULTIMATE";
        } else {
            // 多租户模式：检查租户版本
            boolean isDefaultTenant = tenantId != null && tenantId == 1;
            
            if (tenantId != null && platformJdbcTemplate != null) {
                try {
                    Map<String, Object> tenantMap = platformJdbcTemplate.queryForMap(
                            "SELECT tenant_name, version FROM sys_tenant WHERE tenant_id = ?", tenantId);
                    info.put("tenantName", tenantMap.get("tenant_name"));
                    if (!isDefaultTenant && tenantMap.get("version") != null) {
                        tenantVersion = tenantMap.get("version").toString().toUpperCase();
                    }
                } catch (Exception e) {
                    log.warn("获取租户信息失败", e);
                }
            }
            
            // 默认租户不受版本限制
            if (isDefaultTenant) {
                tenantVersion = "ULTIMATE";
            }
            
            // 兜底逻辑：从用户自身获取租户信息
            if (tenantVersion == null) {
                try {
                    List<Map<String, Object>> userInfoList = jdbcTemplate.queryForList(
                            "SELECT tenant_id, tenant_name, tenant_version FROM sys_user WHERE user_id = ?", userId);
                    if (!userInfoList.isEmpty()) {
                        Map<String, Object> userRow = userInfoList.get(0);
                        Long userTenantId = userRow.get("tenant_id") != null 
                                ? ((Number) userRow.get("tenant_id")).longValue() : null;
                        
                        if (userRow.get("tenant_id") != null) {
                            info.put("tenantId", userTenantId);
                        }
                        if (userRow.get("tenant_name") != null) {
                            info.put("tenantName", userRow.get("tenant_name").toString());
                        }
                        
                        boolean userIsDefaultTenant = userTenantId != null && userTenantId == 1;
                        if (userIsDefaultTenant) {
                            tenantVersion = "ULTIMATE";
                        } else if (userRow.get("tenant_version") != null) {
                            tenantVersion = userRow.get("tenant_version").toString().toUpperCase();
                        }
                    }
                } catch (Exception e) {
                    log.warn("从用户获取租户信息失败", e);
                }
            }
        }
        
        info.put("tenantVersion", tenantVersion != null ? tenantVersion : "");

        // === 修复：支持两种权限存储格式 ===
        // 1. 通配符 "*"：表示全量权限（用于 ADMIN 角色）
        // 2. 数字ID：菜单的 menu_id（用于版本化管理员角色，如 ADMIN_STANDARD）
        // 3. 路径字符串：菜单的 route_path（用于普通角色）
        
        // 1. 先获取用户有权限访问的 menu_id 列表（从 sys_role_permission）
        List<String> allowedMenuKeys = new ArrayList<>();
        boolean hasWildcardPermission = false;
        try {
            if (!roleIds.isEmpty()) {
                String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
                List<Object> params = new ArrayList<>(roleIds);
                String sql = "SELECT DISTINCT rp.menu_id FROM sys_role_permission rp " +
                        "WHERE rp.role_id IN (" + placeholders + ")";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
                for (Map<String, Object> row : rows) {
                    Object mid = row.get("menu_id");
                    if (mid != null && !mid.toString().isBlank()) {
                        String key = mid.toString();
                        if ("*".equals(key)) {
                            hasWildcardPermission = true;
                        }
                        allowedMenuKeys.add(key);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取角色菜单权限失败", e);
        }

        // ===== 兜底：仅当用户无任何角色（roleIds 空）且是管理员时才授予通配符 =====
        // 历史bug：原来 isAdmin 一律给通配符，导致租户管理员（is_admin=1）即使分配了
        // 版本角色（如 ADMIN_STANDARD 的95条菜单授权）也被通配符覆盖成全部菜单。
        // 修复原则：权限来源唯一化——有角色按角色的菜单授权来；
        // 通配符只允许来自角色权限记录里的 '*'（admin 的 ADMIN 角色自带）
        // 或"完全无角色"的降级兜底（超管进租户库等场景）。
        if (isAdmin && roleIds.isEmpty() && !hasWildcardPermission) {
            hasWildcardPermission = true;
        }

        // ===== SUPER_ADMIN 角色口径统一：平台超管 = 平台管理 + 业务全权限 =====
        // 历史bug：前端菜单兜底把 SUPER_ADMIN 渲染成全菜单（看得见），但角色权限表
        // 若无记录则 menuRoutePaths 为空、路由守卫不放行（点手册 403）。
        // 修复原则：SUPER_ADMIN 与 ADMIN 同级，直接授予通配符，不依赖权限表配置。
        if (roleCodes.contains("SUPER_ADMIN")) {
            hasWildcardPermission = true;
        }

        // 2. 获取版本过滤后的菜单（包含所有用户可见的菜单）
        List<String> allMenuRoutePaths = new ArrayList<>();
        List<String> allMenuIds = new ArrayList<>();
        List<String> allPermissions = new ArrayList<>();
        
        String versionFilter = buildVersionFilter(tenantVersion);
        
        // 构建 menuId -> routePath 的映射，用于 ID 格式权限的转换
        Map<String, String> menuIdToPathMap = new HashMap<>();

        // rows 提到 try 外定义：构建 menuIds/permissions 时（try 块外）也要复用，避免重复查 sys_menu
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            String sql = "SELECT m.menu_id, m.route_path, m.permission_code FROM sys_menu m WHERE 1=1" + versionFilter;
            rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                Object menuId = row.get("menu_id");
                Object routePath = row.get("route_path");
                Object permissionCode = row.get("permission_code");
                
                if (menuId != null) {
                    allMenuIds.add(menuId.toString());
                }
                if (routePath != null && !routePath.toString().isBlank()) {
                    allMenuRoutePaths.add(routePath.toString());
                    // 建立 ID -> Path 映射
                    if (menuId != null) {
                        menuIdToPathMap.put(menuId.toString(), routePath.toString());
                    }
                }
                if (permissionCode != null && !permissionCode.toString().isBlank()) {
                    allPermissions.add(permissionCode.toString());
                }
            }
        } catch (Exception e) {
            log.warn("获取版本菜单失败", e);
        }

        // 3. 计算用户实际有权限的菜单（角色权限 ∩ 版本权限）
        List<String> menuRoutePaths = new ArrayList<>();
        List<String> menuIds = new ArrayList<>();
        List<String> permissions = new ArrayList<>();
        
        // 允许两种情况进入：①有明确的角色权限key ②isAdmin兜底授予了通配符权限（allowedMenuKeys可能空）
        if (!allowedMenuKeys.isEmpty() || hasWildcardPermission) {
            if (hasWildcardPermission) {
                // 通配符：直接返回版本过滤后的所有菜单
                menuRoutePaths.addAll(allMenuRoutePaths);
            } else {
                // 非通配符：需要处理两种格式的权限
                // a) 路径字符串：直接与 allMenuRoutePaths 匹配
                // b) 数字ID：通过 menuIdToPathMap 转换为路径后再匹配
                
                // 先将数字ID格式的权限转换为路径
                Set<String> allowedPathSet = new HashSet<>();
                Set<String> allowedIdSet = new HashSet<>();
                
                for (String key : allowedMenuKeys) {
                    // 检查是否是纯数字ID
                    if (key.matches("\\d+")) {
                        // 数字ID：通过映射转换
                        String path = menuIdToPathMap.get(key);
                        if (path != null) {
                            allowedPathSet.add(path);
                        }
                        allowedIdSet.add(key);
                    } else {
                        // 路径字符串：直接添加
                        allowedPathSet.add(key);
                    }
                }
                
                // 用转换后的路径集合过滤版本菜单
                for (String routePath : allMenuRoutePaths) {
                    if (allowedPathSet.contains(routePath)) {
                        menuRoutePaths.add(routePath);
                    }
                }
            }
            
            // 构建 menuIds 和 permissions（复用上面已查询的 rows，不再重复查 sys_menu）
            Set<String> menuRoutePathSet = new HashSet<>(menuRoutePaths);
            for (Map<String, Object> row : rows) {
                Object mid = row.get("menu_id");
                Object rp = row.get("route_path");
                Object pc = row.get("permission_code");
                if (rp != null && menuRoutePathSet.contains(rp.toString())) {
                    if (mid != null) menuIds.add(mid.toString());
                    if (pc != null && !pc.toString().isBlank()) permissions.add(pc.toString());
                }
            }
        }

        // ===== 关键修复：通配符权限用户（ADMIN）的 permissions 必须含 '*' =====
        // 前端 v-permission 指令对 perms.includes('*') 全放行；
        // sys_menu.permission_code 只存菜单查看码（无 :add/:delete 操作码），
        // 若不补 '*'，管理员的 新增/编辑/删除 按钮会被权限指令直接移除
        if (hasWildcardPermission) {
            permissions.add(0, "*");
        }

        // ===== 按钮权限码并入 permissions（2026-09-02）=====
        // sys_role_permission 中 permission_type='按钮' 的记录，menu_id 存的就是按钮码
        // （形如 模块:对象:动作），与前端 v-permission、后端写接口校验同一套码。
        // 历史缺陷：这批码从未下发前端 →"角色权限里勾了按钮却看不出任何效果"。
        // 通配用户已含 '*'（全放行），无需重复补。
        if (!hasWildcardPermission) {
            for (String key : allowedMenuKeys) {
                if (isButtonCode(key) && !permissions.contains(key)) {
                    permissions.add(key);
                }
            }
        }

        info.put("menuIds", menuIds);
        info.put("menuRoutePaths", menuRoutePaths);
        info.put("permissions", permissions);
        // 租户管理员标记（sys_user.is_admin=1，2026-10-13 下发）：前端按钮显隐的"管理级放行"
        // 需要与后端同口径——租户管理员角色编码不一定是 ADMIN_*、权限也无 '*'，不下发此标记
        // 前端会把管理员的写按钮按"普通角色"隐藏（8cloud 测试租户实测）
        info.put("isAdmin", isAdmin);
        return Result.success(info);
    }

    /**
     * 判断权限记录的 menu_id 是否为"按钮码"（模块:对象:动作）。
     * 特征：至少两段冒号、不含 '/'、不以纯数字构成；用于把 sys_role_permission 中
     * permission_type='按钮' 的码并入 permissions 下发给前端 v-permission。
     */
    private boolean isButtonCode(String key) {
        if (key == null || key.isBlank()) return false;
        if (key.indexOf('/') >= 0 || "*".equals(key)) return false;
        if (key.matches("\\d+")) return false;
        return key.matches("^[A-Za-z][A-Za-z0-9_-]*(:[A-Za-z0-9_-]+){2,}$");
    }

    private String buildVersionFilter(String tenantVersion) {
        if (tenantVersion == null || tenantVersion.isEmpty() || "ULTIMATE".equals(tenantVersion)) {
            return "";
        }
        if ("STANDARD".equals(tenantVersion)) {
            return " AND (m.version IS NULL OR m.version = 'STANDARD')";
        }
        if ("CRM".equals(tenantVersion)) {
            return " AND (m.version IS NULL OR m.version = 'STANDARD' OR m.version = 'CRM')";
        }
        return "";
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
