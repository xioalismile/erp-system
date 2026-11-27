package com.erp.tenant.service;

import com.erp.tenant.TenantContextHolder;
import com.erp.tenant.TenantRoutingDataSource;
import com.erp.tenant.support.TenantExpiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 租户管理服务（独立数据库模式）
 * <p>
 * 每个租户拥有独立的 PostgreSQL 数据库 erp_tenant_{code}（从模板库 erp_tenant_template 克隆），
 * 物理隔离、数据不互通。平台库 erp_platform 存放租户登记与用户-租户映射，登录密码验证以平台库为准。
 * <p>
 * 关键约束：
 * 1. 用户 ID 对齐——平台库与租户库中同一管理员的 user_id 必须一致（token 里的 userId 来自平台库，
 *    进入租户后按该 ID 查租户库，错位会导致登录后查无此人）。
 * 2. sys_user_role 无 (user_id, role_id) 唯一约束——禁用 ON CONFLICT，一律先查后插。
 * 3. sys_user_tenant 有 UNIQUE(user_id, tenant_id)——ON CONFLICT 安全。
 * 4. CREATE/DROP DATABASE 不能运行在事务内——本服务所有方法不加 @Transactional，失败一致性靠补偿逻辑。
 */
@Slf4j
@Service
public class TenantService {

    /** 租户库模板：CREATE DATABASE ... TEMPLATE erp_tenant_template（由 database/schema/create_tenant_template.ps1 维护） */
    private static final String TENANT_TEMPLATE_DB = "erp_tenant_template";
    /** 租户独立库命名前缀（同时也是 deleteTenant 整库删除的保护性前缀检查） */
    private static final String TENANT_DB_PREFIX = "erp_tenant_";
    /** jdbc:postgresql://host:port/dbname 解析用 */
    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("jdbc:postgresql://([^:/]+):(\\d+)/.+");

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired(required = false)
    @Qualifier("platformJdbcTemplate")
    private JdbcTemplate platformJdbcTemplate;

    /** 默认业务库路由（erp_db），仅作无平台库环境的兜底查询 */
    @Autowired(required = false)
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /** 多租户动态数据源：创建租户后预注册连接池 */
    @Autowired(required = false)
    private TenantRoutingDataSource routingDataSource;

    /**
     * 账号 → 员工档案（2026-09-21）：新建租户管理员时同时在租户库的员工档案里建一条并绑定，
     * 避免客户拿到账号后"员工档案里查无此人"（用该账号导入客户/分配销售员会映射不到人）。
     */
    @Autowired(required = false)
    private com.erp.basedata.service.EmployeeService employeeService;

    /** 默认业务库连接信息（jdbc:postgresql://host:port/dbname），用于解析主机端口并直连新建租户库 */
    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/erp_db}")
    private String defaultDbUrl;
    @Value("${spring.datasource.username:erp_user}")
    private String defaultDbUsername;
    @Value("${spring.datasource.password:Erp@2026}")
    private String defaultDbPassword;

    /** 按行业初始化引擎（2026-10-11 开账引导）：新增客户选了行业 → 建完立即自动预设 */
    @Autowired(required = false)
    private com.erp.system.onboarding.IndustryInitService industryInitService;

    /** 小楷助手积分（2026-10-24）：新客户按平台规则初始化公司总积分 */
    @Autowired(required = false)
    private com.erp.ai.credit.AiCreditService aiCreditService;

    /**
     * 创建租户及默认管理员账号（独立数据库模式）
     * <p>
     * 流程：参数校验 → 前置校验（编码/库/用户名唯一、模板库就绪）→ 从模板库克隆独立库 →
     * 平台库登记租户 → 平台库建管理员 → 租户库以相同 user_id 建管理员（ID 对齐）→
     * 租户库分配版本角色 → 平台库建用户-租户关联 → 预注册数据源。
     * 任一步失败自动回收已建的数据库与平台库记录（补偿逻辑）。
     *
     * @param tenantCode     租户编码（字母开头，2-40位字母/数字/下划线/中划线）
     * @param tenantName     客户名称
     * @param version        版本（STANDARD/CRM/ULTIMATE）
     * @param adminUsername  管理员用户名（为空则自动生成 {code}_admin）
     * @param adminPassword  管理员密码（为空则使用默认密码 Admin@123456）
     * @param contactPerson  联系人
     * @param contactPhone   联系电话
     * @param contactEmail   邮箱
     * @param expireDate     到期日期
     * @param maxUsers       最大用户数（为空默认 50）
     * @param industry       所属行业（2026-10-11 开账引导，选填；传了则建完自动按行业初始化，
     *                       客户首登不再弹小楷向导；不传则置"待引导"，首登弹向导）
     */
    public Map<String, Object> createTenantWithAdmin(
            String tenantCode, String tenantName, String version,
            String adminUsername, String adminPassword,
            String contactPerson, String contactPhone, String contactEmail,
            LocalDate expireDate, Integer maxUsers, String industry) {

        log.info("开始创建租户(独立库模式): tenantCode={}, tenantName={}, version={}", tenantCode, tenantName, version);

        // ---------- 0. 参数校验与默认值 ----------
        if (tenantCode == null || !tenantCode.matches("^[A-Za-z][A-Za-z0-9_-]{1,39}$")) {
            throw new RuntimeException("租户编码格式非法（需字母开头，2-40位字母/数字/下划线/中划线）: " + tenantCode);
        }
        if (tenantName == null || tenantName.trim().isEmpty()) {
            throw new RuntimeException("客户名称不能为空");
        }
        if (platformJdbcTemplate == null) {
            throw new RuntimeException("平台库不可用，无法以独立库模式创建租户");
        }
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            adminUsername = tenantCode.toLowerCase() + "_admin";
        }
        if (adminPassword == null || adminPassword.trim().isEmpty()) {
            adminPassword = "Admin@123456";
        }
        if (maxUsers == null || maxUsers <= 0) {
            maxUsers = 50;
        }

        // 保存当前租户上下文并清除，确保后续直连操作不受拦截器设置的租户路由影响
        Long savedTenantId = TenantContextHolder.getTenantId();
        String savedDbName = TenantContextHolder.getTenantDbName();
        TenantContextHolder.clear();

        String tenantDbName = TENANT_DB_PREFIX + tenantCode.toLowerCase().replace('-', '_');
        String userCode = tenantCode.toUpperCase() + "_ADMIN";
        Long tenantId = null;
        boolean dbCreated = false;
        Long platformUserId = null;
        boolean platformUserCreated = false;

        try {
            // ---------- 1. 前置校验 ----------
            // 1.1 编码不重复
            if (!platformJdbcTemplate.queryForList(
                    "SELECT tenant_id FROM sys_tenant WHERE tenant_code = ?", tenantCode).isEmpty()) {
                throw new RuntimeException("租户编码已存在: " + tenantCode);
            }
            // 1.2 目标库不存在（防幽灵库）
            Integer dbExists = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_database WHERE datname = ?", Integer.class, tenantDbName);
            if (dbExists != null && dbExists > 0) {
                throw new RuntimeException("租户数据库已存在: " + tenantDbName);
            }
            // 1.3 模板库就绪
            Integer tplExists = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_database WHERE datname = ?", Integer.class, TENANT_TEMPLATE_DB);
            if (tplExists == null || tplExists == 0) {
                throw new RuntimeException("租户模板库 " + TENANT_TEMPLATE_DB + " 不存在，请先执行 database/schema/create_tenant_template.ps1");
            }
            // 1.4 管理员用户名/编码在平台库未被占用（用户名撞车会导致登录密码验证取错记录）
            if (!platformJdbcTemplate.queryForList(
                    "SELECT user_id FROM sys_user WHERE username = ?", adminUsername).isEmpty()) {
                throw new RuntimeException("管理员用户名已存在: " + adminUsername + "，请更换");
            }
            if (!platformJdbcTemplate.queryForList(
                    "SELECT user_id FROM sys_user WHERE user_code = ?", userCode).isEmpty()) {
                throw new RuntimeException("用户编码已存在: " + userCode);
            }

            // ---------- 2. 从模板库克隆租户独立库 ----------
            String[] hp = parseHostPort(defaultDbUrl);
            // CREATE DATABASE ... TEMPLATE 要求模板库无其他活动连接，先清场
            platformJdbcTemplate.queryForList(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = ? AND pid <> pg_backend_pid()",
                    TENANT_TEMPLATE_DB);
            platformJdbcTemplate.execute("CREATE DATABASE " + tenantDbName + " TEMPLATE " + TENANT_TEMPLATE_DB);
            dbCreated = true;
            log.info("租户独立库创建成功: {} (模板: {})", tenantDbName, TENANT_TEMPLATE_DB);

            // ---------- 3. 平台库登记租户（记录真实 db_name，登录后 TenantInterceptor 按此路由） ----------
            platformJdbcTemplate.update(
                    "INSERT INTO sys_tenant (tenant_code, tenant_name, db_name, db_host, db_port, db_username, db_password, " +
                            "status, version, contact_person, contact_phone, contact_email, expire_date, max_users, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?, ?, ?, ?, ?)",
                    tenantCode, tenantName, tenantDbName, hp[0], Integer.parseInt(hp[1]),
                    defaultDbUsername, defaultDbPassword,
                    version, contactPerson, contactPhone, contactEmail,
                    expireDate, maxUsers, LocalDateTime.now(), LocalDateTime.now());
            List<Map<String, Object>> tenantRows = platformJdbcTemplate.queryForList(
                    "SELECT tenant_id FROM sys_tenant WHERE tenant_code = ?", tenantCode);
            if (tenantRows.isEmpty()) {
                throw new RuntimeException("租户登记失败: " + tenantCode);
            }
            tenantId = ((Number) tenantRows.get(0).get("tenant_id")).longValue();
            log.info("平台库租户登记成功: tenantId={}, dbName={}", tenantId, tenantDbName);

            // ---------- 4. 平台库创建管理员（登录密码验证以平台库为准） ----------
            String hashedPassword = passwordEncoder.encode(adminPassword);
            String realName = truncate(tenantName.trim() + "-管理员", 100);
            platformJdbcTemplate.update(
                    "INSERT INTO sys_user (user_code, username, password, real_name, is_admin, is_enabled, " +
                            "user_type, auth_type, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 0, 1, '管理员', '本地认证', '启用', ?, ?)",
                    userCode, adminUsername, hashedPassword, realName,
                    LocalDateTime.now(), LocalDateTime.now());
            List<Map<String, Object>> userRows = platformJdbcTemplate.queryForList(
                    "SELECT user_id FROM sys_user WHERE username = ?", adminUsername);
            if (userRows.isEmpty()) {
                throw new RuntimeException("平台库管理员创建失败: " + adminUsername);
            }
            platformUserId = ((Number) userRows.get(0).get("user_id")).longValue();
            platformUserCreated = true;
            log.info("平台库管理员创建成功: platformUserId={}, username={}", platformUserId, adminUsername);

            // ---------- 5. 租户库创建管理员（显式使用平台库 user_id，保证 ID 对齐） ----------
            JdbcTemplate tenantTpl = buildJdbcTemplate(tenantDbName, hp[0], Integer.parseInt(hp[1]));
            tenantTpl.update(
                    "INSERT INTO sys_user (user_id, user_code, username, password, real_name, is_admin, is_enabled, " +
                            "user_type, auth_type, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 1, 1, '管理员', '本地认证', '启用', ?, ?)",
                    platformUserId, userCode, adminUsername, hashedPassword, realName,
                    LocalDateTime.now(), LocalDateTime.now());
            // 显式插入 user_id 不会推进自增序列，修正序列防止租户内后续新增用户拿到冲突 ID
            tenantTpl.queryForObject(
                    "SELECT setval(pg_get_serial_sequence('sys_user','user_id'), (SELECT COALESCE(MAX(user_id),1) FROM sys_user))",
                    Long.class);
            log.info("租户库管理员创建成功(ID对齐): dbName={}, userId={}", tenantDbName, platformUserId);

            // ---------- 5.5 账号 → 员工档案：租户管理员在员工档案里建一条并绑定（2026-09-21） ----------
            // 建租户流程不走常规 mapper 租户上下文，这里显式注册数据源 + 临时切换上下文，
            // 让员工档案走与运行时完全相同的口径（含员工编码自动生成）；
            // 失败只告警、不阻断建租户（启动补齐会兜底）。
            if (employeeService != null && routingDataSource != null) {
                Long prevTenantId = TenantContextHolder.getTenantId();
                String prevDbName = TenantContextHolder.getTenantDbName();
                try {
                    routingDataSource.registerTenantDataSource(tenantDbName, hp[0], Integer.parseInt(hp[1]),
                            defaultDbUsername, defaultDbPassword);
                    TenantContextHolder.setTenantId(tenantId);
                    TenantContextHolder.setTenantDbName(tenantDbName);
                    employeeService.ensureEmployeeForUser(platformUserId, realName, contactPhone, contactEmail);
                    log.info("租户管理员员工档案已创建并绑定: dbName={}, userId={}", tenantDbName, platformUserId);
                } catch (Exception ex) {
                    log.warn("租户管理员员工档案创建失败（不阻断建租户，启动补齐会兜底）: {}", ex.getMessage());
                } finally {
                    if (prevTenantId != null) {
                        TenantContextHolder.setTenantId(prevTenantId);
                    }
                    if (prevDbName != null) {
                        TenantContextHolder.setTenantDbName(prevDbName);
                    } else {
                        TenantContextHolder.clear();
                    }
                }
            }

            // ---------- 5.9 小楷助手积分初始化（2026-10-24） ----------
            // 按平台「AI 默认规则 → 新客户初始积分」建公司总积分账户并启用积分控制；
            // 失败只告警不阻断建租户（模型选择/建号仍可用，平台可事后补积分）。
            try {
                if (aiCreditService != null) {
                    aiCreditService.grantNewTenantCreditsOn(tenantTpl);
                }
            } catch (Exception ex) {
                log.warn("租户积分初始化失败（不阻断建租户）: dbName={}, err={}", tenantDbName, ex.getMessage());
            }

            // ---------- 6. 租户库分配版本角色（无角色的管理员登录后菜单为空、直接卡死） ----------
            assignAdminRole(tenantTpl, platformUserId, version);

            // ---------- 6.5 裁剪租户库角色（租户角色列表只留本版本管理员+业务角色） ----------
            // 模板库为兼容三版本保留了全部角色，克隆后按版本裁掉：
            //   SUPER_ADMIN（平台专属）、其他两个版本的管理员（标准 ADMIN 与业务角色保留）
            trimTenantRoles(tenantTpl, platformUserId, version);

            // ---------- 6.5 菜单结构对齐主库（2026-10-12，模板库陈旧根治） ----------
            // 模板库是"主库在某个时刻的克隆快照"，主库每天在加菜单（设置中心子项/明细报表/开账引导等），
            // 克隆出的新租户库永远缺"克隆点之后新增的菜单"——实测本地模板库标准版可见仅 63 条（主库 111 条），
            // 租户管理员连设置中心都进不去（/system/settings 行缺失 → 授权无从谈起）。
            // 这里在建租户时按主库 sys_menu（menu_code 唯一对齐键）幂等补齐缺行（不动已有行），
            // 随后 6.6 的全菜单授权自然覆盖新行。平台菜单（/admin%）不同步，防越权泄漏。
            int menusAdded = syncTenantMenusFromMain(tenantTpl);
            if (menusAdded > 0) {
                log.info("租户库菜单结构对齐主库完成: dbName={}, 补齐 {} 条缺失菜单", tenantDbName, menusAdded);
            }

            // ---------- 6.6 给管理员授予全部菜单权限（2026-09-20 根治补丁） ----------
            // 模板库的 sys_role_permission 种子只覆盖业务角色，版本管理员角色是"空壳"（实测 0 条），
            // 导致租户管理员登录后菜单为空、permissions 为空（连设置中心都进不去）。
            // 这里按该租户自己的 sys_menu 全量补授，保证"创建出来就能用"。
            grantAllMenusToAdmin(tenantTpl, platformUserId);

            // ---------- 6.8 编号规则种子自愈（2026-10-12，121 生产实锤） ----------
            // 新租户库不再信任模板库的种子完备性：克隆后按唯一清单幂等补齐全部编号规则
            // （缺则补、有则不动），杜绝"本地主库有、模板库没有"导致的新租户
            // 报「单据类型 [BD_PRICE_LEVEL] 未配置编号规则」。
            try {
                int snAdded = com.erp.serialno.support.SerialNoDefaults.ensureOn(tenantTpl);
                log.info("租户库编号规则自愈完成: dbName={}, 新增 {} 条（清单共 {} 条）",
                        tenantDbName, snAdded, com.erp.serialno.support.SerialNoDefaults.size());
            } catch (Exception ex) {
                log.warn("租户库编号规则自愈失败（不阻断建租户，启动自愈会兜底）: {}", ex.getMessage());
            }

            // ---------- 6.8.1 初始化编码默认自动生成（2026-10-25，用户实锤） ----------
            // 《守则01》口径：初始化时带编码的资料一律默认自动生成。模板库克隆会把历史上被关掉的
            // 「物料分类」开关（auto_generate=0，当年按父级局部编号设计刻意关闭）一起带进新租户，
            // 导致新租户新增物料分类仍要手填编码。这里只对新租户库对齐默认值，
            // 不动存量租户（管理员在设置中心主动关闭的开关不能被翻回去）。
            try {
                int autoFixed = com.erp.serialno.support.SerialNoDefaults.alignDefaultAutoGenerateOn(
                        tenantTpl, "BD_MATERIAL_CATEGORY");
                log.info("租户库物料分类编码默认自动生成对齐完成: dbName={}, 修正 {} 条", tenantDbName, autoFixed);
            } catch (Exception ex) {
                log.warn("租户库物料分类编码自动生成对齐失败（不阻断建租户）: {}", ex.getMessage());
            }

            // ---------- 6.9 基础币种种子（2026-10-12，银行账户必填币种被卡根治） ----------
            // 模板库/租户库币种表实测全空（含存量租户）——银行账户必填币种、单据默认币种
            // 取"基础币种优先"，新租户开箱即卡。按常用币种清单"缺则补"（CNY=基础币种）。
            try {
                int curAdded = com.erp.tenant.support.BaseDataDefaults.ensureCurrenciesOn(tenantTpl);
                log.info("租户库基础币种自愈完成: dbName={}, 新增 {} 条", tenantDbName, curAdded);
            } catch (Exception ex) {
                log.warn("租户库基础币种自愈失败（不阻断建租户，启动自愈会兜底）: {}", ex.getMessage());
            }

            // ---------- 6.7 开账引导状态（2026-10-11） ----------
            // 新库必须显式置"待引导"（模板库可能携带主库的已完成种子，直接克隆会误判）：
            //   未选行业 → pending（客户初始管理员首登弹小楷向导）；
            //   选了行业 → 立即按行业自动预设并置 done（客户首登不弹向导）。
            // 初始化失败不阻断建租户（状态回退 pending，首登仍可走向导补做）。
            boolean industryApplied = false;
            if (industryInitService != null && industry != null && !industry.trim().isEmpty()
                    && routingDataSource != null) {
                Long prevTenantId7 = TenantContextHolder.getTenantId();
                String prevDbName7 = TenantContextHolder.getTenantDbName();
                try {
                    routingDataSource.registerTenantDataSource(tenantDbName, hp[0], Integer.parseInt(hp[1]),
                            defaultDbUsername, defaultDbPassword);
                    TenantContextHolder.setTenantId(tenantId);
                    TenantContextHolder.setTenantDbName(tenantDbName);
                    industryInitService.initialize(industry, 5, "系统开通");
                    industryApplied = true;
                    log.info("租户按行业自动初始化完成: dbName={}, industry={}", tenantDbName, industry);
                } catch (Exception ex) {
                    log.warn("租户按行业初始化失败（不阻断建租户，首登仍会弹向导）: {}", ex.getMessage());
                } finally {
                    if (prevTenantId7 != null) {
                        TenantContextHolder.setTenantId(prevTenantId7);
                    }
                    if (prevDbName7 != null) {
                        TenantContextHolder.setTenantDbName(prevDbName7);
                    } else {
                        TenantContextHolder.clear();
                    }
                }
            }
            if (!industryApplied) {
                upsertOnboardingStatus(tenantTpl, com.erp.system.onboarding.IndustryInitService.STATUS_PENDING);
            }

            // ---------- 7. 平台库建立用户-租户关联（登录后租户列表来源） ----------
            platformJdbcTemplate.update(
                    "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                            "VALUES (?, ?, 1, 'active', ?, ?) " +
                            "ON CONFLICT (user_id, tenant_id) DO NOTHING",
                    platformUserId, tenantId, LocalDateTime.now(), LocalDateTime.now());
            log.info("平台库用户-租户关联建立成功: platformUserId={}, tenantId={}", platformUserId, tenantId);

            // ---------- 8. 预注册租户数据源（登录选择租户后拦截器也会按需注册，此处提前热身） ----------
            if (routingDataSource != null) {
                try {
                    routingDataSource.registerTenantDataSource(tenantDbName, hp[0], Integer.parseInt(hp[1]),
                            defaultDbUsername, defaultDbPassword);
                } catch (Exception ex) {
                    log.warn("预注册租户数据源失败(不影响创建): {}", ex.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("tenantId", tenantId);
            result.put("tenantName", tenantName);
            result.put("tenantCode", tenantCode);
            result.put("dbName", tenantDbName);
            result.put("version", version);
            result.put("adminUsername", adminUsername);
            result.put("adminPassword", adminPassword);
            result.put("userId", platformUserId);
            result.put("maxUsers", maxUsers);
            result.put("expireDate", expireDate);
            result.put("industryInitialized", industryApplied);
            result.put("onboardingStatus", industryApplied
                    ? com.erp.system.onboarding.IndustryInitService.STATUS_DONE
                    : com.erp.system.onboarding.IndustryInitService.STATUS_PENDING);
            log.info("租户创建完成: tenantCode={}, tenantId={}, dbName={}, admin={}",
                    tenantCode, tenantId, tenantDbName, adminUsername);
            return result;
        } catch (Exception e) {
            // ---------- 补偿回收（逆序），单步失败不掩盖原始异常 ----------
            log.error("创建租户失败，启动补偿回收: tenantCode={}, dbName={}, dbCreated={}, platformUserCreated={}",
                    tenantCode, tenantDbName, dbCreated, platformUserCreated, e);
            rollbackTenantCreation(tenantDbName, dbCreated, tenantId, platformUserId, platformUserCreated);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("创建租户失败: " + e.getMessage(), e);
        } finally {
            restoreContext(savedTenantId, savedDbName);
        }
    }

    /**
     * 更新租户版本：平台库更新 version 字段 + 租户库切换管理员角色
     */
    public void updateTenantVersion(Long tenantId, String newVersion) {
        log.info("更新租户版本: tenantId={}, newVersion={}", tenantId, newVersion);
        if (platformJdbcTemplate == null) {
            throw new RuntimeException("平台库不可用");
        }
        platformJdbcTemplate.update(
                "UPDATE sys_tenant SET version = ?, updated_at = ? WHERE tenant_id = ?",
                newVersion, LocalDateTime.now(), tenantId);

        // 租户库：清除旧的管理员角色，分配新版本角色
        JdbcTemplate tenantTpl = tenantJdbcTemplate(tenantId);
        if (tenantTpl == null) {
            log.warn("未找到租户库，仅更新了平台库版本号: tenantId={}", tenantId);
            return;
        }
        String newRoleCode = resolveRoleCode(newVersion);
        Long newRoleId = findRoleId(tenantTpl, newRoleCode);
        if (newRoleId == null) {
            // 租户创建时 trimTenantRoles 只保留当时的版本角色，切换版本需按需补建
            // （历史bug：此处原来静默 return，导致"切换版本后重新登录菜单不变"）
            newRoleId = createVersionAdminRole(tenantTpl, newRoleCode, newVersion);
            log.info("租户库动态创建版本管理员角色: tenantId={}, roleCode={}, roleId={}", tenantId, newRoleCode, newRoleId);
        }
        if (newRoleId == null) {
            log.warn("租户库缺少目标角色({})且创建失败，跳过角色切换", newRoleCode);
            return;
        }
        tenantTpl.update(
                "DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE is_admin = 1) " +
                        "AND role_id IN (SELECT role_id FROM sys_role WHERE role_code LIKE 'ADMIN_%')");
        List<Map<String, Object>> admins = tenantTpl.queryForList(
                "SELECT user_id FROM sys_user WHERE is_admin = 1 AND is_enabled = 1");
        for (Map<String, Object> a : admins) {
            Long uid = ((Number) a.get("user_id")).longValue();
            List<Map<String, Object>> existing = tenantTpl.queryForList(
                    "SELECT user_role_id FROM sys_user_role WHERE user_id = ? AND role_id = ?", uid, newRoleId);
            if (existing.isEmpty()) {
                tenantTpl.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", uid, newRoleId);
            }
        }
        // 切换完成后清理其他版本的管理员角色，保持"每个租户只有一个版本管理员角色"
        cleanupOtherVersionAdminRoles(tenantTpl, newRoleCode);
        log.info("租户库管理员角色已切换: tenantId={}, newRoleCode={}, admins={}", tenantId, newRoleCode, admins.size());
    }

    /**
     * 在租户库动态创建版本管理员角色，并按 sys_menu.version 授权。
     * 授权口径与 AuthController.buildVersionFilter 完全一致：
     * STANDARD=STANDARD+NULL，CRM=STANDARD+CRM+NULL，ULTIMATE=全部；
     * menu_id 存 route_path（与 99_init_roles.sql 格式一致）。
     */
    private Long createVersionAdminRole(JdbcTemplate tenantTpl, String roleCode, String version) {
        String roleName;
        switch (roleCode) {
            case "ADMIN_STANDARD": roleName = "标准版管理员"; break;
            case "ADMIN_CRM": roleName = "CRM版管理员"; break;
            case "ADMIN_ULTIMATE": roleName = "旗舰版管理员"; break;
            default: roleName = "管理员"; break;
        }
        syncRoleIdSequence(tenantTpl);
        List<Map<String, Object>> created = tenantTpl.queryForList(
                "INSERT INTO sys_role (role_code, role_name, role_type, data_scope, is_system, status, is_enabled, created_at) " +
                        "VALUES (?, ?, '系统角色', '全部', 1, '启用', 1, CURRENT_TIMESTAMP) RETURNING role_id",
                roleCode, roleName);
        Long roleId = ((Number) created.get(0).get("role_id")).longValue();

        String menuFilter;
        switch (version == null ? "" : version.toUpperCase()) {
            case "STANDARD":
                menuFilter = "m.version IS NULL OR m.version = 'STANDARD'";
                break;
            case "CRM":
                menuFilter = "m.version IS NULL OR m.version IN ('STANDARD','CRM')";
                break;
            default:
                menuFilter = "1=1";
                break;
        }
        // DISTINCT 必需：sys_menu 里同一 route_path 可能有多行（实测模板库 23 个重复路径，
        // 如 /system/settings?item=menu-name 重复 45 行）；不带 DISTINCT 会插出
        // (role_id, menu_id, permission_type) 重复行 → 撞唯一键导致建租户整体失败。
        int granted = tenantTpl.update(
                "INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at) " +
                        "SELECT DISTINCT ?, m.route_path, '菜单', 1, CURRENT_TIMESTAMP FROM sys_menu m " +
                        "WHERE m.route_path IS NOT NULL AND m.route_path != '' AND (" + menuFilter + ") " +
                        "AND NOT EXISTS (SELECT 1 FROM sys_role_permission p WHERE p.role_id = ? " +
                        "                AND p.menu_id = m.route_path AND p.permission_type = '菜单')",
                roleId, roleId);
        log.info("版本管理员角色授权完成: roleCode={}, menus={}", roleCode, granted);
        return roleId;
    }

    /**
     * 删除租户库中除当前版本外的其他版本管理员角色（连同权限与用户关联）。
     */
    private void cleanupOtherVersionAdminRoles(JdbcTemplate tenantTpl, String keepRoleCode) {
        tenantTpl.update(
                "DELETE FROM sys_role_permission WHERE role_id IN " +
                        "(SELECT role_id FROM sys_role WHERE role_code LIKE 'ADMIN_%' AND role_code != ?)",
                keepRoleCode);
        tenantTpl.update(
                "DELETE FROM sys_user_role WHERE role_id IN " +
                        "(SELECT role_id FROM sys_role WHERE role_code LIKE 'ADMIN_%' AND role_code != ?)",
                keepRoleCode);
        tenantTpl.update(
                "DELETE FROM sys_role WHERE role_code LIKE 'ADMIN_%' AND role_code != ?",
                keepRoleCode);
    }

    /**
     * 重置租户管理员密码：平台库（登录验证）+ 租户库（保持一致）双更新
     */
    public void resetTenantAdminPassword(Long tenantId, String newPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        log.info("重置租户管理员密码: tenantId={}", tenantId);
        if (platformJdbcTemplate == null) {
            throw new RuntimeException("平台库不可用");
        }
        List<Map<String, Object>> adminUsers = platformJdbcTemplate.queryForList(
                "SELECT user_id FROM sys_user_tenant WHERE tenant_id = ? AND is_admin_in_tenant = 1", tenantId);
        if (adminUsers.isEmpty()) {
            throw new RuntimeException("该租户没有管理员账号: tenantId=" + tenantId);
        }
        String hashedPassword = passwordEncoder.encode(newPassword);

        // 1. 平台库：登录密码验证以此为准
        for (Map<String, Object> u : adminUsers) {
            Long uid = ((Number) u.get("user_id")).longValue();
            platformJdbcTemplate.update(
                    "UPDATE sys_user SET password = ?, updated_at = ? WHERE user_id = ?",
                    hashedPassword, LocalDateTime.now(), uid);
        }
        // 2. 租户库：ID 对齐，同一 user_id 同步更新
        JdbcTemplate tenantTpl = tenantJdbcTemplate(tenantId);
        if (tenantTpl != null) {
            for (Map<String, Object> u : adminUsers) {
                Long uid = ((Number) u.get("user_id")).longValue();
                tenantTpl.update(
                        "UPDATE sys_user SET password = ?, updated_at = ? WHERE user_id = ?",
                        hashedPassword, LocalDateTime.now(), uid);
            }
        } else {
            log.warn("未找到租户库，仅重置了平台库密码: tenantId={}", tenantId);
        }
        log.info("租户管理员密码已重置: tenantId={}, admins={}", tenantId, adminUsers.size());
    }

    /**
     * 更新租户基本信息（仅平台库：独立库模式下租户名称等元数据存于平台库）
     */
    public void updateTenantInfo(Long tenantId, String tenantName,
                                  String contactPerson, String contactPhone,
                                  String contactEmail, String address,
                                  Integer maxUsers, LocalDate expireDate) {
        log.info("更新租户基本信息: tenantId={}", tenantId);
        if (platformJdbcTemplate == null) {
            throw new RuntimeException("平台库不可用");
        }
        StringBuilder sql = new StringBuilder("UPDATE sys_tenant SET ");
        List<Object> params = new ArrayList<>();
        if (tenantName != null) {
            sql.append("tenant_name = ?, ");
            params.add(tenantName);
        }
        if (contactPerson != null) {
            sql.append("contact_person = ?, ");
            params.add(contactPerson);
        }
        if (contactPhone != null) {
            sql.append("contact_phone = ?, ");
            params.add(contactPhone);
        }
        if (contactEmail != null) {
            sql.append("contact_email = ?, ");
            params.add(contactEmail);
        }
        if (address != null) {
            sql.append("address = ?, ");
            params.add(address);
        }
        if (maxUsers != null) {
            sql.append("max_users = ?, ");
            params.add(maxUsers);
        }
        if (expireDate != null) {
            sql.append("expire_date = ?, ");
            params.add(expireDate);
        }
        sql.append("updated_at = ? WHERE tenant_id = ?");
        params.add(LocalDateTime.now());
        params.add(tenantId);
        platformJdbcTemplate.update(sql.toString(), params.toArray());
    }

    /**
     * 续费：到期日顺延（当前到期日晚于今天则从到期日顺延，否则从今天起算）
     */
    public void renewTenant(Long tenantId, int months) {
        log.info("租户续费: tenantId={}, months={}", tenantId, months);
        if (platformJdbcTemplate == null) {
            throw new RuntimeException("平台库不可用");
        }
        List<Map<String, Object>> rows = platformJdbcTemplate.queryForList(
                "SELECT expire_date FROM sys_tenant WHERE tenant_id = ?", tenantId);
        if (rows.isEmpty()) {
            throw new RuntimeException("租户不存在: " + tenantId);
        }
        LocalDate base = LocalDate.now();
        Object exp = rows.get(0).get("expire_date");
        if (exp instanceof java.sql.Date) {
            LocalDate expireDate = ((java.sql.Date) exp).toLocalDate();
            if (expireDate.isAfter(base)) {
                base = expireDate;
            }
        }
        platformJdbcTemplate.update(
                "UPDATE sys_tenant SET expire_date = ?, updated_at = ? WHERE tenant_id = ?",
                base.plusMonths(months), LocalDateTime.now(), tenantId);
        log.info("租户续费完成: tenantId={}, newExpireDate={}", tenantId, base.plusMonths(months));
    }

    /**
     * 获取所有租户列表
     */
    public List<Map<String, Object>> getAllTenants(String status) {
        String sql = "SELECT t.*, COUNT(ut.user_id) as user_count, " +
                "(SELECT u.username FROM sys_user_tenant ut2 " +
                " JOIN sys_user u ON ut2.user_id = u.user_id " +
                " WHERE ut2.tenant_id = t.tenant_id AND ut2.is_admin_in_tenant = 1 " +
                " LIMIT 1) as admin_username " +
                "FROM sys_tenant t " +
                "LEFT JOIN sys_user_tenant ut ON t.tenant_id = ut.tenant_id " +
                "WHERE 1=1";
        List<Object> params = new ArrayList<>();
        // 状态筛选（2026-11-09）："已过期"是按下发日期实时派生的状态，库里 status 仍是 active。
        // 若不在这里翻译，平台客户管理的"已过期"筛选永远为空（历史缺陷）。
        if (status != null && !status.isEmpty()) {
            if ("expired".equalsIgnoreCase(status)) {
                sql += " AND t.status = 'active' AND t.expire_date IS NOT NULL AND t.expire_date < CURRENT_DATE"
                        + " AND UPPER(t.tenant_code) <> 'DEFAULT'";
            } else if ("active".equalsIgnoreCase(status)) {
                sql += " AND t.status = 'active' AND (t.expire_date IS NULL OR t.expire_date >= CURRENT_DATE"
                        + " OR UPPER(t.tenant_code) = 'DEFAULT')";
            } else {
                sql += " AND t.status = ?";
                params.add(status);
            }
        }
        sql += " GROUP BY t.tenant_id ORDER BY t.created_at DESC";

        List<Map<String, Object>> rows;
        if (platformJdbcTemplate != null) {
            try {
                rows = platformJdbcTemplate.queryForList(sql, params.toArray());
            } catch (Exception e) {
                log.warn("从平台库获取租户列表失败，尝试从业务库获取", e);
                rows = jdbcTemplate.queryForList(sql, params.toArray());
            }
        } else {
            rows = jdbcTemplate.queryForList(sql, params.toArray());
        }
        // 状态派生：正常但已过到期日 → expired（只改返回结果，不动数据库，续费后自动恢复正常）
        for (Map<String, Object> row : rows) {
            String current = row.get("status") == null ? null : String.valueOf(row.get("status"));
            if ("active".equalsIgnoreCase(current)
                    && TenantExpiry.isExpired(row.get("tenant_code") == null ? null : String.valueOf(row.get("tenant_code")),
                            row.get("expire_date"))) {
                row.put("status", "expired");
            }
        }
        return rows;
    }

    /**
     * 获取租户详情
     */
    public Map<String, Object> getTenantById(Long tenantId) {
        if (platformJdbcTemplate != null) {
            List<Map<String, Object>> tenants = platformJdbcTemplate.queryForList(
                    "SELECT * FROM sys_tenant WHERE tenant_id = ?", tenantId);
            if (!tenants.isEmpty()) {
                return tenants.get(0);
            }
        }
        return null;
    }

    /**
     * 禁用租户：平台库 status 置 disabled（拦截器只路由 active 租户，即被阻断访问）+ 租户库用户禁用
     */
    public void disableTenant(Long tenantId) {
        log.info("禁用租户: tenantId={}", tenantId);
        if (platformJdbcTemplate != null) {
            platformJdbcTemplate.update(
                    "UPDATE sys_tenant SET status = 'disabled', updated_at = ? WHERE tenant_id = ?",
                    LocalDateTime.now(), tenantId);
        }
        JdbcTemplate tenantTpl = tenantJdbcTemplate(tenantId);
        if (tenantTpl != null) {
            try {
                tenantTpl.update(
                        "UPDATE sys_user SET is_enabled = 0, updated_at = ? WHERE is_admin = 1",
                        LocalDateTime.now());
            } catch (Exception e) {
                log.warn("租户库禁用用户失败(不阻断，平台库状态已生效): {}", e.getMessage());
            }
        }
    }

    /**
     * 启用租户
     */
    public void enableTenant(Long tenantId) {
        log.info("启用租户: tenantId={}", tenantId);
        if (platformJdbcTemplate != null) {
            platformJdbcTemplate.update(
                    "UPDATE sys_tenant SET status = 'active', updated_at = ? WHERE tenant_id = ?",
                    LocalDateTime.now(), tenantId);
        }
        JdbcTemplate tenantTpl = tenantJdbcTemplate(tenantId);
        if (tenantTpl != null) {
            try {
                tenantTpl.update(
                        "UPDATE sys_user SET is_enabled = 1, updated_at = ? WHERE is_admin = 1",
                        LocalDateTime.now());
            } catch (Exception e) {
                log.warn("租户库启用用户失败(不阻断，平台库状态已生效): {}", e.getMessage());
            }
        }
    }

    /**
     * 删除租户：整库删除 + 平台库记录/关联/孤儿用户清理。
     * 先删库后删记录（删库失败时记录保留可重试；反过来会留下无人知晓的幽灵库）。
     */
    public void deleteTenant(Long tenantId) {
        log.info("删除租户: tenantId={}", tenantId);
        if (platformJdbcTemplate == null) {
            throw new RuntimeException("平台库不可用");
        }
        List<Map<String, Object>> rows = platformJdbcTemplate.queryForList(
                "SELECT tenant_code, db_name FROM sys_tenant WHERE tenant_id = ?", tenantId);
        if (rows.isEmpty()) {
            throw new RuntimeException("租户不存在: " + tenantId);
        }
        String tenantCode = (String) rows.get(0).get("tenant_code");
        String dbName = (String) rows.get(0).get("db_name");
        if ("DEFAULT".equalsIgnoreCase(tenantCode)) {
            throw new RuntimeException("默认租户(DEFAULT, 指向共享业务库 erp_db)不允许删除");
        }

        // 1. 删除租户独立库（仅允许 erp_tenant_ 前缀，防止误删共享库）
        if (dbName != null && dbName.startsWith(TENANT_DB_PREFIX)) {
            platformJdbcTemplate.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
            log.info("租户独立库已删除: {}", dbName);
        } else {
            log.warn("租户库命名异常，跳过整库删除(保护性检查): dbName={}", dbName);
        }

        // 2. 平台库清理：关联、孤儿用户（不再属于任何租户的才删）、租户记录
        List<Map<String, Object>> tenantUsers = platformJdbcTemplate.queryForList(
                "SELECT user_id FROM sys_user_tenant WHERE tenant_id = ?", tenantId);
        platformJdbcTemplate.update("DELETE FROM sys_user_tenant WHERE tenant_id = ?", tenantId);
        for (Map<String, Object> u : tenantUsers) {
            Long uid = ((Number) u.get("user_id")).longValue();
            Integer otherCount = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user_tenant WHERE user_id = ?", Integer.class, uid);
            if (otherCount != null && otherCount == 0) {
                platformJdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", uid);
            }
        }
        platformJdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", tenantId);
        log.info("租户删除完成: tenantId={}, dbName={}", tenantId, dbName);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查询某客户（租户）本月全部用户的 AI 记账消耗合计（跨库直连该租户库）。
     * 供平台管理「AI额度」弹窗展示；库不可达/表不存在等异常返回 -1（前端显示为不可知，不阻塞分配）。
     */
    public long getTenantAiMonthUsed(Long tenantId) {
        JdbcTemplate tpl = tenantJdbcTemplate(tenantId);
        if (tpl == null) {
            return -1L;
        }
        try {
            String month = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            Long used = tpl.queryForObject(
                    "SELECT COALESCE(SUM(total_tokens), 0) FROM ai_token_usage WHERE month = ?",
                    Long.class, month);
            return used != null ? used : 0L;
        } catch (Exception e) {
            log.warn("查询租户本月AI消耗失败（不阻断额度分配）: tenantId={}, {}", tenantId, e.getMessage());
            return -1L;
        }
    }

    /**
     * 平台管理「积分」弹窗：读取某客户的公司总积分/累计消耗/本月消耗/已分配员工（跨库直连租户库）。
     * 库不可达时返回空摘要，不阻塞页面。
     */
    public Map<String, Object> getTenantCreditSummary(Long tenantId) {
        JdbcTemplate tpl = tenantJdbcTemplate(tenantId);
        if (tpl == null || aiCreditService == null) {
            return new java.util.LinkedHashMap<>();
        }
        return aiCreditService.tenantSummaryOn(tpl);
    }

    /** 平台给客户增加/扣减公司总积分（正数增加、负数扣减），返回最新摘要。 */
    public Map<String, Object> adjustTenantCredit(Long tenantId, java.math.BigDecimal delta, String remark) {
        JdbcTemplate tpl = tenantJdbcTemplate(tenantId);
        if (tpl == null || aiCreditService == null) {
            throw new RuntimeException("该客户的数据库暂不可达，无法调整积分");
        }
        aiCreditService.adjustTenantCreditsOn(tpl, delta, remark);
        return aiCreditService.tenantSummaryOn(tpl);
    }

    /**
     * 裁剪租户库角色：只保留管理员实际持有的角色（版本角色，缺失时为兜底 ADMIN）+ 业务角色。
     * 删除：SUPER_ADMIN（平台专属，不允许出现在租户库）、未被使用的 ADMIN、
     * 其他版本的管理员角色（如 STANDARD 租户删 ADMIN_CRM/ADMIN_ULTIMATE）。
     * 同步清理 sys_role_permission / sys_user_role 的关联记录，避免孤儿数据。
     */
    private void trimTenantRoles(JdbcTemplate tenantTpl, Long userId, String version) {
        String keepRoleCode = resolveRoleCode(version);
        // 管理员实际持有的角色一律保留（assignAdminRole 正常用版本角色；版本角色缺失时兜底 ADMIN）
        List<String> heldRoles = tenantTpl.queryForList(
                "SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.role_id WHERE ur.user_id = ?",
                String.class, userId);
        // 注意：**不再把标准 ADMIN 列入待删角色**（2026-09-20 根治补丁）。
        // 原因：全系统的管理员鉴权白名单认的是 ADMIN/SUPER_ADMIN/MANAGER，而版本角色
        // ADMIN_ULTIMATE/ADMIN_STANDARD/ADMIN_CRM 不在白名单内；一旦把 ADMIN 角色删掉，
        // 租户管理员将永远无法通过管理员鉴权（建用户/删用户/重置密码等一律 403）。
        // SUPER_ADMIN 仍删（平台专属，不允许出现在租户库）；MANAGER 仍删（业务角色，与管理员无关）。
        List<String> dropCodes = new ArrayList<>(Arrays.asList("SUPER_ADMIN", "MANAGER"));
        for (String code : Arrays.asList("ADMIN_STANDARD", "ADMIN_CRM", "ADMIN_ULTIMATE")) {
            if (!code.equals(keepRoleCode)) {
                dropCodes.add(code);
            }
        }
        dropCodes.removeAll(heldRoles);
        if (dropCodes.isEmpty()) {
            return;
        }
        String inList = dropCodes.stream().map(c -> "'" + c + "'").reduce((a, b) -> a + "," + b).orElse("''");
        int permDeleted = tenantTpl.update(
                "DELETE FROM sys_role_permission WHERE role_id IN " +
                        "(SELECT role_id FROM sys_role WHERE role_code IN (" + inList + "))");
        int userRoleDeleted = tenantTpl.update(
                "DELETE FROM sys_user_role WHERE role_id IN " +
                        "(SELECT role_id FROM sys_role WHERE role_code IN (" + inList + "))");
        int roleDeleted = tenantTpl.update(
                "DELETE FROM sys_role WHERE role_code IN (" + inList + ")");
        log.info("租户库角色裁剪完成: 版本角色={}, 管理员持有={}, 删除角色{}个/权限{}条/用户关联{}条",
                keepRoleCode, heldRoles, roleDeleted, permDeleted, userRoleDeleted);
    }

    /** 租户库分配版本管理员角色；角色缺失时就地补建（自愈），不再因模板陈旧而中断建租户 */
    private void assignAdminRole(JdbcTemplate tenantTpl, Long userId, String version) {
        String roleCode = resolveRoleCode(version);
        Long roleId = ensureAdminRoleExists(tenantTpl, roleCode, version);
        if (roleId == null) {
            roleId = ensureAdminRoleExists(tenantTpl, "ADMIN", version);
        }
        if (roleId == null) {
            throw new RuntimeException("租户库缺少管理员角色(" + roleCode + "/ADMIN)且自动补建失败，" +
                    "请检查租户库 sys_role/sys_role_permission 是否可写");
        }
        List<Map<String, Object>> existing = tenantTpl.queryForList(
                "SELECT user_role_id FROM sys_user_role WHERE user_id = ? AND role_id = ?", userId, roleId);
        if (existing.isEmpty()) {
            tenantTpl.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }

        // ★★ 2026-09-20 根治补丁：同时绑定"标准 ADMIN 角色" ★★
        // 为什么必须双角色（三处叠加导致的真实缺陷，实测租户 sc_wudong）：
        //   ① 全系统的管理员鉴权白名单是 hasAnyRole("ADMIN","SUPER_ADMIN","MANAGER")
        //      （SecurityUtils.isAdmin / SysUserController / EmployeeController / AiConfigController /
        //        SysRoleFieldPermissionController / SystemMaintainController 等 10 处）；
        //   ② 本方法原先只分配"版本角色" ADMIN_ULTIMATE / ADMIN_STANDARD / ADMIN_CRM —— 不在白名单内；
        //   ③ trimTenantRoles 又把标准 ADMIN 角色直接删掉。
        // 结果：租户创建时给的管理员，在自己租户里被判为普通用户 —— 建用户/删用户/重置密码/AI 配置
        //       一律 403「无权限管理用户」。用平台超管测试时因其持有 ADMIN 永远发现不了。
        // 处置：让管理员同时持有"版本角色 + 标准 ADMIN"，既保留版本语义，又能命中标准白名单。
        Long stdAdminRoleId = ensureAdminRoleExists(tenantTpl, "ADMIN", version);
        if (stdAdminRoleId != null && !stdAdminRoleId.equals(roleId)) {
            List<Map<String, Object>> exist2 = tenantTpl.queryForList(
                    "SELECT user_role_id FROM sys_user_role WHERE user_id = ? AND role_id = ?", userId, stdAdminRoleId);
            if (exist2.isEmpty()) {
                tenantTpl.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, stdAdminRoleId);
                log.info("租户库已为管理员补绑标准 ADMIN 角色: userId={}, roleId={}", userId, stdAdminRoleId);
            }
        }
        log.info("租户库角色分配成功: roleCode={}, roleId={}, userId={}", roleCode, roleId, userId);
    }

    /**
     * 给租户管理员授予该租户"全部菜单"权限（幂等）。
     *
     * 背景（2026-09-20 在 8cloud 租户 sc_wudong 实测）：新建租户的管理员角色在 sys_role_permission
     * 里是 **0 条** —— 模板库的权限种子只覆盖业务角色（SALES/PRODUCTION_MANAGER/…），
     * 版本管理员角色是"空壳"。表现为租户管理员登录后菜单为空、permissions 为空，
     * 连设置中心都进不去（userinfo.menuRoutePaths = 0）。
     */
    private void grantAllMenusToAdmin(JdbcTemplate tenantTpl, Long userId) {
        List<Long> adminRoleIds = tenantTpl.queryForList(
                "SELECT DISTINCT r.role_id FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.role_id " +
                        "WHERE ur.user_id = ? AND r.role_code LIKE 'ADMIN%'", Long.class, userId);
        if (adminRoleIds.isEmpty()) {
            log.warn("未找到管理员角色，跳过菜单授权: userId={}", userId);
            return;
        }
        int granted = 0;
        for (Long rid : adminRoleIds) {
            granted += tenantTpl.update(
                    "INSERT INTO sys_role_permission (role_id, menu_id, permission_type) " +
                            "SELECT ?, m.menu_id::text, '菜单' FROM sys_menu m " +
                            "WHERE m.menu_id IS NOT NULL " +
                            "AND NOT EXISTS (SELECT 1 FROM sys_role_permission p " +
                            "                WHERE p.role_id = ? AND p.menu_id = m.menu_id::text)",
                    rid, rid);
        }
        log.info("租户管理员菜单授权完成: userId={}, 角色={}, 新增权限={}条", userId, adminRoleIds, granted);
    }

    private String resolveRoleCode(String version) {
        if ("STANDARD".equalsIgnoreCase(version)) {
            return "ADMIN_STANDARD";
        }
        if ("CRM".equalsIgnoreCase(version)) {
            return "ADMIN_CRM";
        }
        return "ADMIN_ULTIMATE";
    }

    /**
     * 菜单结构对齐主库（2026-10-12，建租户链路自愈）。
     *
     * <p>以业务主库 sys_menu 为权威源，按 <b>menu_code</b>（唯一对齐键，route_path 在主库可重复、
     * 不可作键——2026-10-10 实锤 23 个地址重复）把租户库缺失的菜单行幂等补齐：
     * <b>缺则补、有则不动</b>（绝不 UPDATE 已有行，避免覆盖租户库既有数据）。</p>
     *
     * <p>口径：</p>
     * <ul>
     *   <li>平台菜单（route_path 以 /admin 开头）不同步——平台管理是平台侧功能，进租户库即越权泄漏；</li>
     *   <li>menu_code 为空的行跳过（无法对齐，历史脏数据由模板库重建收口）；</li>
     *   <li>父级按 parent 的 menu_code 在租户库映射；父级缺失则逐层迭代补插（最多 6 轮防环）；</li>
     *   <li>补插完成后修正 sys_menu 自增序列（模板种子用显式 ID，不推进 sequence，直接 nextval 会撞主键——
     *       与 2026-10-10 sys_role 序列同一坑）；</li>
     *   <li>版本（version）随主库行原样带入，租户版本过滤（userinfo buildVersionFilter）照常生效。</li>
     * </ul>
     *
     * @return 本次补插的菜单行数
     */
    private int syncTenantMenusFromMain(JdbcTemplate tenantTpl) {
        if (jdbcTemplate == null) {
            log.warn("菜单结构对齐跳过：业务主库连接不可用");
            return 0;
        }
        // 主库权威菜单（含父编码）。menu_code 为空/平台菜单不在同步范围。
        List<Map<String, Object>> mainMenus = jdbcTemplate.queryForList(
                "SELECT m.menu_id, m.menu_code, m.menu_name, m.menu_type, m.route_path, m.permission_code, "
              + "       m.component_path, m.sort_order, m.icon, m.is_visible, m.is_enabled, m.version, "
              + "       p.menu_code AS parent_code "
              + "FROM sys_menu m LEFT JOIN sys_menu p ON p.menu_id = m.parent_menu_id "
              + "WHERE m.menu_code IS NOT NULL AND m.menu_code <> '' "
              + "   OR (m.menu_code IS NULL AND m.route_path IS NOT NULL AND m.route_path <> '')");
        // 租户库现有 code → id 映射 + route_path → id 映射（设置中心子项无 menu_code，按 route_path 对齐——
        // 主库设置中心子项 route_path 实测唯一；租户库同 route_path 已有任意行即视为已存在，不插重复）
        Map<String, Long> tenantMap = new HashMap<>();
        Map<String, Long> tenantRouteMap = new HashMap<>();
        for (Map<String, Object> row : tenantTpl.queryForList(
                "SELECT menu_code, route_path, menu_id FROM sys_menu")) {
            Object code = row.get("menu_code");
            Object route = row.get("route_path");
            Object id = row.get("menu_id");
            if (id == null) {
                continue;
            }
            Long mid = ((Number) id).longValue();
            if (code != null && !String.valueOf(code).isBlank()) {
                tenantMap.putIfAbsent(String.valueOf(code), mid);
            }
            if (route != null && !String.valueOf(route).isBlank()) {
                tenantRouteMap.putIfAbsent(String.valueOf(route), mid);
            }
        }
        int added = 0;
        for (int round = 0; round < 6; round++) {
            boolean progressed = false;
            for (Map<String, Object> m : mainMenus) {
                Object codeObj = m.get("menu_code");
                String code = codeObj == null ? null : String.valueOf(codeObj);
                Object routeObj = m.get("route_path");
                String route = routeObj == null ? null : String.valueOf(routeObj);
                if (route != null && route.startsWith("/admin")) {
                    continue; // 平台菜单不同步
                }
                boolean hasCode = code != null && !code.isBlank();
                if (hasCode && tenantMap.containsKey(code)) {
                    continue;
                }
                if (!hasCode && route != null && tenantRouteMap.containsKey(route)) {
                    continue;
                }
                String parentCode = m.get("parent_code") == null ? null : String.valueOf(m.get("parent_code"));
                Long parentId;
                if (parentCode == null || parentCode.isBlank() || "null".equals(parentCode)) {
                    parentId = null;
                } else {
                    parentId = tenantMap.get(parentCode);
                    if (parentId == null) {
                        continue; // 父级尚未插入，等下一轮
                    }
                }
                tenantTpl.update(
                        "INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path, "
                      + "permission_code, component_path, sort_order, icon, is_visible, is_enabled, "
                      + "is_cache, keepalive, status, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 1, '启用', ?)",
                        hasCode ? code : null,
                        m.get("menu_name"),
                        parentId,
                        m.get("menu_type"),
                        route,
                        m.get("permission_code"),
                        m.get("component_path"),
                        m.get("sort_order"),
                        m.get("icon"),
                        m.get("is_visible") == null ? 1 : m.get("is_visible"),
                        m.get("version"));
                Long newId = null;
                if (hasCode) {
                    newId = tenantTpl.queryForObject(
                            "SELECT menu_id FROM sys_menu WHERE menu_code = ?", Long.class, code);
                    tenantMap.put(code, newId);
                } else if (route != null) {
                    newId = tenantTpl.queryForObject(
                            "SELECT menu_id FROM sys_menu WHERE route_path = ? ORDER BY menu_id DESC LIMIT 1",
                            Long.class, route);
                    tenantRouteMap.put(route, newId);
                }
                added++;
                progressed = true;
            }
            if (!progressed) {
                break;
            }
        }
        if (added > 0) {
            // 修正自增序列（显式 ID 插入不推进 sequence）
            tenantTpl.queryForObject(
                    "SELECT setval(pg_get_serial_sequence('sys_menu','menu_id'), "
                  + "(SELECT COALESCE(MAX(menu_id),1) FROM sys_menu))", Long.class);
        }
        return added;
    }

    /**
     * 直连租户库写开账引导状态（幂等 upsert；建租户链路专用，不走路由上下文）。
     */
    private void upsertOnboardingStatus(JdbcTemplate tenantTpl, String status) {
        try {
            int updated = tenantTpl.update(
                    "UPDATE sys_config SET config_value = ?, updated_at = CURRENT_TIMESTAMP WHERE config_key = ?",
                    status, com.erp.system.onboarding.IndustryInitService.CFG_STATUS);
            if (updated == 0) {
                tenantTpl.update(
                        "INSERT INTO sys_config (config_key, config_value, config_name, config_group) VALUES (?, ?, '开账引导', 'onboarding')",
                        com.erp.system.onboarding.IndustryInitService.CFG_STATUS, status);
            }
        } catch (Exception ex) {
            log.warn("开账引导状态写入失败(不阻断): {}", ex.getMessage());
        }
    }

    private Long findRoleId(JdbcTemplate tpl, String roleCode) {
        List<Map<String, Object>> rows = tpl.queryForList(
                "SELECT role_id FROM sys_role WHERE role_code = ? AND is_enabled = 1", roleCode);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("role_id")).longValue();
    }

    /**
     * 建租户自愈：确保租户库存在可用的管理员角色，缺失时就地补建（幂等）。
     *
     * <p>背景（2026-10-10 实测 8cloud 建租户失败）：租户库由模板库克隆，而模板库可能构建于
     * 角色种子之前 —— 实测 8cloud 的 erp_tenant_template 同时缺 ADMIN_STANDARD 与 ADMIN，
     * 导致新建"标准版"租户时找不到管理员角色，直接抛错并回收整个租户。
     * 改为就地补建后，任何陈旧模板都能正常建租户，无需人工重建模板库。</p>
     */
    private Long ensureAdminRoleExists(JdbcTemplate tenantTpl, String roleCode, String version) {
        // 1. 已存在且启用 → 直接复用
        Long id = findRoleId(tenantTpl, roleCode);
        if (id != null) {
            return id;
        }
        // 2. 存在但被停用 → 启用，避免重复插入撞 role_code 唯一键
        List<Map<String, Object>> rows = tenantTpl.queryForList(
                "SELECT role_id FROM sys_role WHERE role_code = ?", roleCode);
        if (!rows.isEmpty()) {
            Long existing = ((Number) rows.get(0).get("role_id")).longValue();
            tenantTpl.update("UPDATE sys_role SET is_enabled = 1, status = '启用' WHERE role_id = ?", existing);
            log.info("租户库管理员角色已重新启用: roleCode={}, roleId={}", roleCode, existing);
            return existing;
        }
        // 3. 不存在 → 补建（ADMIN 走基础管理员，版本角色走带菜单授权的版本管理员）
        if ("ADMIN".equals(roleCode)) {
            return createBaseAdminRole(tenantTpl);
        }
        return createVersionAdminRole(tenantTpl, roleCode, version);
    }

    /** 补建基础管理员角色 ADMIN：授予通配权限 '*'（与本系统既有 ADMIN 口径一致，全菜单+全按钮放行）。 */
    private Long createBaseAdminRole(JdbcTemplate tenantTpl) {
        syncRoleIdSequence(tenantTpl);
        List<Map<String, Object>> created = tenantTpl.queryForList(
                "INSERT INTO sys_role (role_code, role_name, role_type, data_scope, is_system, status, is_enabled, created_at) " +
                        "VALUES ('ADMIN', '系统管理员', '系统角色', '全部', 1, '启用', 1, CURRENT_TIMESTAMP) RETURNING role_id");
        Long roleId = ((Number) created.get(0).get("role_id")).longValue();
        tenantTpl.update(
                "INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at) " +
                        "VALUES (?, '*', '菜单', 1, CURRENT_TIMESTAMP)",
                roleId);
        log.info("租户库自动补建基础管理员角色 ADMIN: roleId={}", roleId);
        return roleId;
    }

    /**
     * 同步 sys_role 自增序列到当前最大 ID。
     * 模板库的种子数据用显式 ID 插入（如 100/101/102），不会推进序列；
     * 若直接 nextval 可能取到已被占用的 ID → 主键冲突。补建角色前先同步，保证安全。
     */
    private void syncRoleIdSequence(JdbcTemplate tenantTpl) {
        try {
            tenantTpl.queryForObject(
                    "SELECT setval(pg_get_serial_sequence('sys_role','role_id'), " +
                            "(SELECT COALESCE(MAX(role_id),1) FROM sys_role))", Long.class);
        } catch (Exception e) {
            log.warn("同步角色ID序列失败(不阻断): {}", e.getMessage());
        }
    }

    /**
     * 按平台库 sys_tenant 的连接信息直连租户独立库（管理操作用，低频场景不建连接池）
     *
     * @return 租户库 JdbcTemplate；租户不存在或连接失败返回 null（调用方自行降级）
     */
    private JdbcTemplate tenantJdbcTemplate(Long tenantId) {
        if (platformJdbcTemplate == null || tenantId == null) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = platformJdbcTemplate.queryForList(
                    "SELECT db_name, db_host, db_port FROM sys_tenant WHERE tenant_id = ?", tenantId);
            if (rows.isEmpty()) {
                log.warn("租户不存在: tenantId={}", tenantId);
                return null;
            }
            Map<String, Object> t = rows.get(0);
            return buildJdbcTemplate(
                    (String) t.get("db_name"),
                    (String) t.get("db_host"),
                    ((Number) t.get("db_port")).intValue());
        } catch (Exception e) {
            log.warn("定位租户库失败: tenantId={}, {}", tenantId, e.getMessage());
            return null;
        }
    }

    /** 用默认业务库账号直连指定库（租户库与业务库同实例同账号，克隆库时对象已归 erp_user 所有） */
    private JdbcTemplate buildJdbcTemplate(String dbName, String host, int port) {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:postgresql://" + host + ":" + port + "/" + dbName,
                defaultDbUsername, defaultDbPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(ds);
    }

    /** 从 jdbc:postgresql://host:port/dbname 解析 [host, port] */
    private String[] parseHostPort(String jdbcUrl) {
        Matcher m = JDBC_URL_PATTERN.matcher(jdbcUrl == null ? "" : jdbcUrl.trim());
        if (!m.matches()) {
            throw new RuntimeException("无法解析数据库连接地址: " + jdbcUrl);
        }
        return new String[]{m.group(1), m.group(2)};
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 创建失败的补偿回收：逆序删除已创建的资源，单步失败不影响后续步骤 */
    private void rollbackTenantCreation(String tenantDbName, boolean dbCreated,
                                        Long tenantId, Long platformUserId, boolean platformUserCreated) {
        if (platformUserCreated && platformUserId != null) {
            try {
                platformJdbcTemplate.update("DELETE FROM sys_user_tenant WHERE user_id = ?", platformUserId);
            } catch (Exception ex) {
                log.warn("补偿: 删除用户-租户关联失败: {}", ex.getMessage());
            }
            try {
                platformJdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", platformUserId);
            } catch (Exception ex) {
                log.warn("补偿: 删除平台用户失败: {}", ex.getMessage());
            }
        }
        if (tenantId != null) {
            try {
                platformJdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", tenantId);
            } catch (Exception ex) {
                log.warn("补偿: 删除租户记录失败: {}", ex.getMessage());
            }
        }
        if (dbCreated) {
            try {
                platformJdbcTemplate.execute("DROP DATABASE IF EXISTS " + tenantDbName + " WITH (FORCE)");
                log.info("补偿: 租户独立库已回收: {}", tenantDbName);
            } catch (Exception ex) {
                log.error("补偿: 删除租户库 {} 失败，请手动执行 DROP DATABASE {} WITH (FORCE)",
                        tenantDbName, tenantDbName, ex);
            }
        }
    }

    private void restoreContext(Long tenantId, String dbName) {
        try {
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }
            if (dbName != null) {
                TenantContextHolder.setTenantDbName(dbName);
            }
        } catch (Exception e) {
            log.warn("恢复租户上下文失败", e);
        }
    }
}
