package com.erp.system.service;

import com.erp.system.entity.SysUser;
import com.erp.tenant.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台库账号同步通用服务（2026-10-12 新增）。
 *
 * 背景（121 生产实锤）：登录凭证只在「业务主库 + 平台库」两处查找，从不查租户独立库。
 * 「系统管理 → 用户管理」建的账号有平台库双写 + 租户关联（2026-09-20 补），
 * 但「基础资料 → 员工档案」建的账号此前只写租户库 → 租户员工一律"用户名或密码错误"登不进。
 * 本服务把"平台库双写 + 用户-租户关联"收口为一份通用实现，供员工档案链路与启动自愈共用。
 *
 * 口径：
 *  1) 平台库写失败一律告警不阻断（单机客户环境无平台库不影响建号）；
 *  2) 平台库副作用一律在业务事务【提交成功后】执行——业务事务回滚时平台库不残留半套状态；
 *  3) 当前库就是平台库时跳过双写，只补租户关联；
 *  4) 显式写主键后必须修正平台库自增序列（防后续新增用户撞主键）；
 *  5) 关联写入前先查重（PG 禁止依赖异常去重）；
 *  6) 【2026-10-12 重构】密码只存在租户库，平台库不存密码；用户名允许跨租户重名
 *     （不同租户的同名用户各自验各自的密码），只检查主键冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUserSyncService {

    private final JdbcTemplate platformJdbcTemplate;

    @Value("${erp.datasource.platform.url:jdbc:postgresql://localhost:5432/erp_platform}")
    private String platformDbUrl;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/erp_db}")
    private String defaultDbUrl;

    /** 平台库返回值：自愈补写结果 */
    public static final String HEAL_INSERTED = "inserted";
    public static final String HEAL_EXISTS = "exists";
    public static final String HEAL_CONFLICT = "conflict";
    public static final String HEAL_FAILED = "failed";

    /** 从 jdbc 地址提取库名（jdbc:postgresql://host:port/dbname → dbname） */
    private String dbNameOf(String url) {
        if (!StringUtils.hasText(url)) return null;
        int slash = url.lastIndexOf('/');
        return slash >= 0 && slash < url.length() - 1 ? url.substring(slash + 1) : null;
    }

    /** 平台库名 */
    public String platformDbName() {
        return dbNameOf(platformDbUrl);
    }

    /** 默认业务库名 */
    public String defaultDbName() {
        return dbNameOf(defaultDbUrl);
    }

    /** 当前请求实际落库的库名（租户上下文缺失时按默认业务库算） */
    private String resolveCurrentDbName() {
        String db = TenantContextHolder.getTenantDbName();
        if (!StringUtils.hasText(db)) {
            db = defaultDbName();
        }
        return db;
    }

    /**
     * 业务事务【提交成功后】执行任务；无事务上下文（如启动自愈）时立即执行。
     * 防止"业务事务回滚而平台库副作用已提交"的两边不一致。
     */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 建号后的平台库同步（守卫 + 告警全内置，绝不抛出，供业务链路直接调用）。
     * 复用自愈同一套守卫：平台库已有同账号则只补关联；主键/用户名冲突则跳过并告警。
     */
    public void syncNewUserAfterCreate(SysUser user) {
        if (user == null || user.getUserId() == null || !StringUtils.hasText(user.getUsername())) {
            return;
        }
        final SysUser u = user;
        final String currentDb = resolveCurrentDbName();
        final Long tenantId = TenantContextHolder.getTenantId();
        runAfterCommit(() -> {
            try {
                if (currentDb != null && currentDb.equalsIgnoreCase(platformDbName())) {
                    // 当前库就是平台库：账号记录已在本库，无需双写，只补租户关联
                    ensureAssociation(u.getUserId(), tenantId, isAdmin(u));
                    return;
                }
                TenantUserRow row = new TenantUserRow();
                row.userId = u.getUserId();
                row.userCode = u.getUserCode();
                row.username = u.getUsername();
                // 【2026-10-12 重构】平台库不存密码，密码只在租户库；登录时直连租户库验密
                row.password = null;
                row.realName = u.getRealName();
                row.isAdmin = u.getIsAdmin();
                row.isEnabled = u.getIsEnabled();
                row.userType = u.getUserType();
                row.authType = u.getAuthType();
                row.status = u.getStatus();
                row.createdAt = u.getCreatedAt();
                row.updatedAt = u.getUpdatedAt();
                String r = healInsertUser(row, tenantId);
                switch (r) {
                    case HEAL_INSERTED -> log.info("平台库用户记录已创建(账号同步): username={}, userId={}",
                            u.getUsername(), u.getUserId());
                    case HEAL_EXISTS -> log.info("平台库已有同一账号，账号同步跳过插入: username={}, userId={}",
                            u.getUsername(), u.getUserId());
                    case HEAL_CONFLICT -> log.warn("平台库账号同步跳过（主键或用户名冲突，撞名账号需改用户名后才能登录）: username={}, userId={}",
                            u.getUsername(), u.getUserId());
                    default -> log.warn("平台库账号同步失败(不影响建号): username={}", u.getUsername());
                }
            } catch (Exception e) {
                log.warn("平台库写入用户记录失败(不影响建号): username={}, err={}",
                        u.getUsername(), e.getMessage());
            }
        });
    }

    /**
     * 启动自愈补写（幂等，供 SchemaUpgradeEnsurer 逐租户库调用）。
     *
     * @return inserted=已补写 / exists=平台库已有同一账号(同id) / conflict=主键冲突需人工处理 / failed=写库异常
     */
    public String healInsertUser(TenantUserRow src, Long tenantId) {
        if (src == null || src.userId == null || !StringUtils.hasText(src.username)) {
            return HEAL_CONFLICT;
        }
        try {
            List<String> sameId = platformJdbcTemplate.queryForList(
                    "SELECT username FROM sys_user WHERE user_id = ?", String.class, src.userId);
            if (!sameId.isEmpty()) {
                // 同 id：平台库已有该账号记录（登录时能查到"用户名→公司"映射）。
                //
                // 2026-11-12 收紧：这里**不再自动补公司归属**。启动自愈拿不到明文密码，
                // 无法证明"这家公司库里的同名账号确实是这个人"；各公司库是独立序列，
                // 不同公司的两个人可能撞同一个 id，同名也可能是两个人。自动挂归属会让
                // "只知道 A 公司密码的人"在切换公司处直接进入 B 公司（越权）。
                // 归属统一由"密码验证通过的路径"建立：登录时归属发现 / 孤儿自愈 / 建号 / 建公司。
                String platformUsername = sameId.get(0);
                if (platformUsername == null || !platformUsername.equalsIgnoreCase(src.username)) {
                    log.warn("平台库同主键但用户名不一致，跳过同步（疑似两家公司的两个人撞了同一ID）: "
                                    + "userId={}, 平台库用户名={}, 当前公司库用户名={}, 公司ID={}",
                            src.userId, platformUsername, src.username, tenantId);
                    return HEAL_CONFLICT;
                }
                return HEAL_EXISTS;
            }
            // 【2026-10-12 重构】不再检查用户名跨租户重名——不同租户允许同名用户，
            // 登录时按租户逐个验密，互不影响。只检查主键冲突。
            insertPlatformRow(src.userId, src.userCode, src.username, null, src.realName,
                    src.isAdmin, src.isEnabled, src.userType, src.authType, src.status,
                    src.createdAt, src.updatedAt);
            ensureAssociation(src.userId, tenantId, src.isAdmin != null && src.isAdmin.intValue() == 1);
            return HEAL_INSERTED;
        } catch (Exception e) {
            log.warn("平台库自愈补写失败: username={}, err={}", src.username, e.getMessage());
            return HEAL_FAILED;
        }
    }

    /** 租户库用户行的轻量载体（避免依赖 MyBatis 实体的租户上下文路由语义） */
    public static class TenantUserRow {
        public Long userId;
        public String userCode;
        public String username;
        public String password;
        public String realName;
        public Short isAdmin;
        public Short isEnabled;
        public String userType;
        public String authType;
        public String status;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    /**
     * 编辑后的平台库同步（字段级：传 null 的字段不动）。绝不抛出。
     * 在业务事务提交成功后执行。
     * 【2026-10-12 重构】不再同步密码——密码只存在租户库，平台库不存密码。
     */
    public void syncUserUpdate(Long userId, String realName, Short isEnabled, String status, String passwordHash) {
        if (userId == null) {
            return;
        }
        final Long uid = userId;
        final String rn = realName;
        final Short en = isEnabled;
        final String st = status;
        runAfterCommit(() -> {
            try {
                StringBuilder sql = new StringBuilder("UPDATE sys_user SET updated_at = CURRENT_TIMESTAMP");
                List<Object> params = new java.util.ArrayList<>();
                if (StringUtils.hasText(rn)) {
                    sql.append(", real_name = ?");
                    params.add(rn);
                }
                if (en != null) {
                    sql.append(", is_enabled = ?");
                    params.add(en.intValue());
                }
                if (StringUtils.hasText(st)) {
                    sql.append(", status = ?");
                    params.add(st);
                }
                // 【2026-10-12】passwordHash 参数保留是为了不破坏调用方签名，但此处不写平台库密码
                sql.append(" WHERE user_id = ?");
                params.add(uid);
                platformJdbcTemplate.update(sql.toString(), params.toArray());
            } catch (Exception e) {
                log.warn("平台库同步用户信息失败(不影响保存): userId={}, err={}", uid, e.getMessage());
            }
        });
    }

    /**
     * 删除账号后的平台库清理（关联 + 记录）。绝不抛出。
     * 在业务事务提交成功后执行（业务删除回滚时平台库记录必须保留）。
     */
    public void deletePlatformUser(Long userId) {
        if (userId == null) {
            return;
        }
        final Long uid = userId;
        runAfterCommit(() -> {
            try {
                platformJdbcTemplate.update("DELETE FROM sys_user_tenant WHERE user_id = ?", uid);
            } catch (Exception e) {
                log.warn("删除平台库用户-租户关联失败: userId={}, err={}", uid, e.getMessage());
            }
            try {
                platformJdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", uid);
            } catch (Exception e) {
                log.warn("删除平台库用户记录失败: userId={}, err={}", uid, e.getMessage());
            }
        });
    }

    /**
     * 幂等建立用户-租户关联（登录后租户列表来源）。
     * 租户上下文缺失时兜底：平台库只有一个 active 租户则挂到该租户。
     */
    public void ensureAssociation(Long userId, Long tenantId, boolean isAdminInTenant) {
        if (userId == null) {
            return;
        }
        try {
            if (tenantId == null) {
                List<Long> activeTenants = platformJdbcTemplate.queryForList(
                        "SELECT tenant_id FROM sys_tenant WHERE status = 'active' ORDER BY tenant_id", Long.class);
                if (activeTenants.size() == 1) {
                    tenantId = activeTenants.get(0);
                }
            }
            if (tenantId == null) {
                log.warn("用户-租户关联未建立(无可用租户): userId={}", userId);
                return;
            }
            Integer count = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?",
                    Integer.class, userId, tenantId);
            if (count == null || count == 0) {
                platformJdbcTemplate.update(
                        "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                                "VALUES (?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        userId, tenantId, isAdminInTenant ? 1 : 0);
                log.info("自动关联用户到租户: userId={}, tenantId={}", userId, tenantId);
            }
        } catch (Exception e) {
            log.warn("建立用户-租户关联失败(不影响建号): userId={}, tenantId={}, err={}", userId, tenantId, e.getMessage());
        }
    }

    /** 向平台库插入一条用户记录（显式主键 + 修正序列），主键/用户名冲突由调用方处理 */
    private void insertPlatformRow(Long userId, String userCodeIn, String username, String passwordHash,
                                   String realNameIn, Short isAdminIn, Short isEnabledIn,
                                   String userTypeIn, String authTypeIn, String statusIn,
                                   LocalDateTime createdAtIn, LocalDateTime updatedAtIn) {
        String userCode = StringUtils.hasText(userCodeIn) ? userCodeIn : username;
        String realName = StringUtils.hasText(realNameIn) ? realNameIn : username;
        String userType = StringUtils.hasText(userTypeIn) ? userTypeIn : "普通用户";
        String authType = StringUtils.hasText(authTypeIn) ? authTypeIn : "本地认证";
        String status = StringUtils.hasText(statusIn) ? statusIn : "启用";
        int isAdmin = isAdminIn != null && isAdminIn.intValue() == 1 ? 1 : 0;
        int isEnabled = isEnabledIn != null ? isEnabledIn.intValue() : 1;
        LocalDateTime now = LocalDateTime.now();
        // 【2026-10-13 兜底】口径是"平台库不存密码"（passwordHash 传 null），但部分存量环境
        // （8cloud/121 平台库表结构较老）的 sys_user.password 列带 NOT NULL 约束——
        // 传 null 会违反约束 → 同步静默失败 → 平台库查无此人 → 建的号永远登录不上
        // （8cloud 测试租户实锤）。改传空串兼容 NOT NULL 列；列约束由启动自愈放开（DDL）。
        String passwordValue = StringUtils.hasText(passwordHash) ? passwordHash : "";
        platformJdbcTemplate.update(
                "INSERT INTO sys_user (user_id, user_code, username, password, real_name, is_admin, is_enabled, " +
                        "user_type, auth_type, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, userCode, username, passwordValue,
                realName, isAdmin, isEnabled, userType, authType, status,
                createdAtIn != null ? createdAtIn : now,
                updatedAtIn != null ? updatedAtIn : now);
        // 显式插入 user_id 不推进自增序列，修正平台库序列防止后续新增用户 ID 冲突
        platformJdbcTemplate.queryForObject(
                "SELECT setval(pg_get_serial_sequence('sys_user','user_id'), (SELECT COALESCE(MAX(user_id),1) FROM sys_user))",
                Long.class);
    }

    private boolean isAdmin(SysUser user) {
        return user.getIsAdmin() != null && user.getIsAdmin().intValue() == 1;
    }
}