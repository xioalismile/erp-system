package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.ai.credit.AiCreditService;
import com.erp.common.PageResult;
import com.erp.config.AuthInterceptor;
import com.erp.config.BusinessException;
import com.erp.tenant.TenantContextHolder;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "auth_type",
            "avatar_url",
            "birthday",
            "created_at",
            "created_by",
            "data_permission_level",
            "dept_id",
            "email",
            "employee_id",
            "entry_date",
            "gender",
            "is_admin",
            "is_enabled",
            "is_locked",
            "last_login_ip",
            "last_login_time",
            "lock_time",
            "login_attempts",
            "mobile_phone",
            "position_code",
            "position_name",
            "real_name",
            "remark",
            "status",
            "telephone",
            "updated_at",
            "updated_by",
            "user_code",
            "user_id",
            "user_type",
            "username"
        );

    private final SysUserRoleMapper userRoleMapper;
    private final JdbcTemplate platformJdbcTemplate;

    /** 小楷助手积分（2026-10-24）：新员工按平台规则自动分配积分 */
    private final AiCreditService aiCreditService;

    /**
     * 账号 ↔ 员工档案联动（2026-09-21）：建账号即在员工档案生成并绑定对应员工，
     * 保证「分配销售员 / 归属人」这类需要员工的场景一定能映射到人（客户导入默认人就靠它）。
     */
    private final com.erp.basedata.service.EmployeeService employeeService;

    /**
     * 用户角色/部门/员工变更后必须清理该用户的鉴权缓存（AuthInterceptor 的 30 秒缓存），
     * 否则改完权限最长要等 30 秒才生效。
     */
    private final AuthInterceptor authInterceptor;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private static final String DEFAULT_PASSWORD = "admin123";

    /**
     * 启动后扫描：所有非 BCrypt 哈希的密码统一重置为 admin123
     * 避免历史明文密码导致登录失败
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            List<SysUser> all = list();
            String bcryptPrefix = "$2a$";
            int reset = 0;
            for (SysUser u : all) {
                String pwd = u.getPassword();
                if (pwd == null || !pwd.startsWith(bcryptPrefix)) {
                    u.setPassword(PASSWORD_ENCODER.encode(DEFAULT_PASSWORD));
                    updateById(u);
                    reset++;
                }
            }
            if (reset > 0) {
                log.warn("启动修复：已为 {} 个用户的密码重置为 BCrypt 哈希（明文密码={}）", reset, DEFAULT_PASSWORD);
            }
        } catch (Exception e) {
            log.error("启动期补偿失败：启动期密码修复：{}，请在界面上检查该账号", e.getMessage(), e);
        }

        // ★ 存量补齐：给"已启用但没有员工档案"的账号自动建员工档案并绑定（2026-09-21）★
        // 幂等：已有绑定的账号不会重复创建；单个失败只记错误日志，不影响启动。
        try {
            List<SysUser> unbound = list(new QueryWrapper<SysUser>()
                    .isNull("employee_id")
                    .ne("is_enabled", 0));
            int created = 0;
            for (SysUser u : unbound) {
                try {
                    employeeService.ensureEmployeeForUser(u.getUserId(), u.getRealName(),
                            u.getMobilePhone(), u.getEmail());
                    created++;
                } catch (Exception ex) {
                    log.error("启动期补偿失败：账号 {} 补齐员工档案：{}，请在界面上检查该账号", u.getUsername(), ex.getMessage());
                }
            }
            log.info("账号-员工打通：已启用账号 {} 个，本次补齐员工档案 {} 个", unbound.size(), created);
        } catch (Exception e) {
            log.error("启动期补偿失败：账号-员工打通启动补齐：{}，请在界面上检查该账号", e.getMessage(), e);
        }
    }

    @Override
    public PageResult<SysUser> pageQuery(int current, int size, String keyword, String status, String sortBy, String sortOrder) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("username", keyword)
                    .or().like("real_name", keyword)
                    .or().like("user_code", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SysUser.class);
        Page<SysUser> page = new Page<>(current, size);
        Page<SysUser> result = page(page, wrapper);
        // 安全：列表接口不下发密码哈希与盐（避免 F12/抓包拿到后离线破解）
        for (SysUser u : result.getRecords()) {
            u.setPassword(null);
            u.setPasswordSalt(null);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SysUser getDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new QueryWrapper<SysUserRole>().eq("user_id", id));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        user.setRoleIds(roleIds);
        // 密码与盐均不回传
        user.setPassword(null);
        user.setPasswordSalt(null);
        return user;
    }

    @Override
    @Transactional
    public void saveUser(SysUser user) {
        if (user.getUserCode() == null || user.getUserCode().isBlank()) {
            user.setUserCode(generateUserCode());
        }
        // 校验用户名唯一
        if (exists(new QueryWrapper<SysUser>().eq("username", user.getUsername()))) {
            throw new BusinessException("用户名已存在");
        }
        if (exists(new QueryWrapper<SysUser>().eq("user_code", user.getUserCode()))) {
            throw new BusinessException("用户编码已存在");
        }
        user.setPassword(PASSWORD_ENCODER.encode(DEFAULT_PASSWORD));
        if (user.getIsAdmin() == null) user.setIsAdmin((short) 0);
        if (user.getIsEnabled() == null) user.setIsEnabled((short) 1);
        if (user.getIsLocked() == null) user.setIsLocked((short) 0);
        if (user.getLoginAttempts() == null) user.setLoginAttempts(0);
        if (user.getStatus() == null) user.setStatus("启用");
        if (user.getUserType() == null) user.setUserType("普通用户");
        if (user.getAuthType() == null) user.setAuthType("本地认证");
        save(user);

        // ★ 账号 → 员工档案（2026-09-21）★
        // 建账号即在员工档案生成同名员工并绑定，否则该账号在员工档案里"查无此人"，
        // 用它导入客户/分配销售员时会映射不到人（121 生产导入外键报错的根因之一）。
        // 放在平台库双写之前：员工档案建失败则整个事务回滚，不留"平台库有账号、租户库没账号"的孤儿。
        try {
            employeeService.ensureEmployeeForUser(user.getUserId(), user.getRealName(),
                    user.getMobilePhone(), user.getEmail());
        } catch (Exception e) {
            log.error("新增账号同步员工档案失败: username={}", user.getUsername(), e);
            throw new BusinessException("员工档案创建失败，账号未保存：" + e.getMessage());
        }

        Long tenantId = TenantContextHolder.getTenantId();

        // ★★ 平台库双写（2026-09-20 修复"新建用户登不进自己租户"）★★
        // 多租户部署下登录凭证主表在平台库：AuthController.login 判定"用户属于哪个租户"的第一步，
        // 就是查平台库 sys_user 有没有这个用户名；查不到 → 整段租户判定被跳过 → isFallbackMode 保持
        // 初始值 true → 返回硬编码的假 default 租户，于是用户永远进不了自己的租户。
        // 实测（8cloud 租户 sc_wudong）：14 个新建测试账号全部落此坑（登录返回 tenants=[default]）。
        // 处置：以**租户库生成的 user_id 为准**，在平台库用同一个 id 写入记录
        // （与 TenantService 创建租户管理员时的"双库 ID 对齐"做法一致，只是方向相反）。
        if (platformJdbcTemplate != null && tenantId != null) {
            try {
                // 【2026-10-12 重构】平台库不存密码，密码只在租户库；用户名允许跨租户重名
                // （不同租户各自验自己的密码），只检查主键冲突。
                Integer dupId = platformJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_user WHERE user_id = ?", Integer.class, user.getUserId());
                if (dupId == null || dupId == 0) {
                    // 【2026-10-13 兜底】password 传空串而非 NULL：存量环境（8cloud/121）平台库
                    // password 列带 NOT NULL，传 NULL 会让同步静默失败 → 建的号登录不上
                    platformJdbcTemplate.update(
                            "INSERT INTO sys_user (user_id, user_code, username, password, real_name, is_admin, is_enabled, " +
                                    "user_type, auth_type, status, created_at, updated_at) " +
                                    "VALUES (?, ?, ?, '', ?, 0, 1, '普通用户', '本地认证', '启用', ?, ?)",
                            user.getUserId(), user.getUserCode(), user.getUsername(),
                            user.getRealName(), LocalDateTime.now(), LocalDateTime.now());
                    platformJdbcTemplate.queryForObject(
                            "SELECT setval(pg_get_serial_sequence('sys_user','user_id'), (SELECT COALESCE(MAX(user_id),1) FROM sys_user))",
                            Long.class);
                    log.info("平台库用户记录已创建(ID对齐, 无密码): username={}, userId={}", user.getUsername(), user.getUserId());
                }
            } catch (Exception e) {
                log.warn("平台库写入用户记录失败: username={}, err={}", user.getUsername(), e.getMessage());
                throw new BusinessException("新增账号失败：写入登录账号失败：" + e.getMessage());
            }
        }

        // 自动将新用户添加到当前租户（使用平台库数据源）
        if (tenantId == null) {
            // 兜底：token 未带租户时（默认单租户部署），取平台库唯一 active 租户
            try {
                List<Long> activeTenants = platformJdbcTemplate.queryForList(
                    "SELECT tenant_id FROM sys_tenant WHERE status = 'active' ORDER BY tenant_id", Long.class);
                if (activeTenants.size() == 1) {
                    tenantId = activeTenants.get(0);
                }
            } catch (Exception e) {
                log.warn("查询单租户兜底失败: {}", e.getMessage());
                throw new BusinessException("新增账号失败：读取登录账号失败：" + e.getMessage());
            }
        }
        if (tenantId != null) {
            // 检查是否已存在关联
            Integer count = platformJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_tenant WHERE user_id = ? AND tenant_id = ?",
                Integer.class, user.getUserId(), tenantId);
            if (count == null || count == 0) {
                platformJdbcTemplate.update(
                    "INSERT INTO sys_user_tenant (user_id, tenant_id, is_admin_in_tenant, status, created_at, updated_at) " +
                    "VALUES (?, ?, 0, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    user.getUserId(), tenantId);
                log.info("自动关联用户 {} 到租户 {}", user.getUsername(), tenantId);
            }
        }

        // 新员工开通：按平台积分规则自动分配（0=不分配，默认全员共用公司总积分）。
        // 独立事务、失败只记日志，不阻断建号（2026-10-24 积分制）。
        try {
            aiCreditService.applyNewUserRule(user.getUserId(), user.getUsername());
        } catch (Exception e) {
            log.warn("新员工积分默认分配失败（不阻断建号）: userId={}, err={}", user.getUserId(), e.getMessage());
        }

        log.info("新增用户: {} 编码: {}", user.getUsername(), user.getUserCode());
    }

    @Override
    @Transactional
    public void updateUser(SysUser user) {
        if (user.getUserId() == null) throw new BusinessException("用户ID不能为空");
        // 校验用户名唯一(排除自身)
        if (exists(new QueryWrapper<SysUser>().eq("username", user.getUsername()).ne("user_id", user.getUserId()))) {
            throw new BusinessException("用户名已存在");
        }
        // 不更新密码: 置空以跳过更新
        user.setPassword(null);
        updateById(user);
        // 账号 → 员工档案：编辑时同步姓名/手机号/邮箱（同步失败必须如实报错，避免界面显示已改、库里没改）
        try {
            employeeService.ensureEmployeeForUser(user.getUserId(), user.getRealName(),
                    user.getMobilePhone(), user.getEmail());
        } catch (Exception e) {
            log.warn("编辑账号失败：同步员工档案失败: userId={}, err={}", user.getUserId(), e.getMessage());
            throw new BusinessException("编辑账号失败：同步员工档案失败：" + e.getMessage());
        }
        // 平台库同步（登录凭证主表）：不同步会出现"租户库里改了、平台库还是旧值"
        if (platformJdbcTemplate != null) {
            try {
                platformJdbcTemplate.update(
                        "UPDATE sys_user SET real_name = ?, status = ?, is_enabled = ?, updated_at = CURRENT_TIMESTAMP " +
                                "WHERE user_id = ?",
                        user.getRealName(), user.getStatus(), user.getIsEnabled(), user.getUserId());
            } catch (Exception e) {
                log.warn("编辑账号失败：同步登录账号失败: userId={}, err={}", user.getUserId(), e.getMessage());
                throw new BusinessException("编辑账号失败：同步登录账号失败：" + e.getMessage());
            }
        }
        authInterceptor.evict(user.getUserId());
        log.info("更新用户: id={}", user.getUserId());
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if (user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            throw new BusinessException("超级管理员不允许删除");
        }
        // 初始化/租户管理员账号不允许删除（2026-09-21 用户口径：初始化账号一定是管理员，不能删）
        if (user.getUserType() != null && user.getUserType().contains("管理员")) {
            throw new BusinessException("【" + user.getUsername() + "】是管理员账号，不允许删除；如需停用请改为「禁用」状态");
        }
        // 账号 → 员工档案：解绑并停用（不删员工，历史单据的业务员/业绩归属不能断）
        try {
            employeeService.unbindAndDisableEmployeeByUser(id);
        } catch (Exception e) {
            log.warn("删除账号失败：解绑员工档案失败: userId={}, err={}", id, e.getMessage());
            throw new BusinessException("删除账号失败：解绑员工档案失败：" + e.getMessage());
        }
        // 删除用户角色关联
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", id));
        // 删除用户租户关联（使用平台库数据源）
        platformJdbcTemplate.update("DELETE FROM sys_user_tenant WHERE user_id = ?", id);
        // 删除平台库用户记录（登录凭证主表）：只删关联会留下"仍能登录的孤儿账号"
        try {
            platformJdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", id);
        } catch (Exception e) {
            log.warn("删除账号失败：删除登录账号失败: userId={}, err={}", id, e.getMessage());
            throw new BusinessException("删除账号失败：删除登录账号失败：" + e.getMessage());
        }
        removeById(id);
        authInterceptor.evict(id);
        log.info("删除用户: id={}", id);
    }

    @Override
    @Transactional
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(PASSWORD_ENCODER.encode(DEFAULT_PASSWORD));
        updateById(user);
        // 【2026-10-12 重构】平台库不存密码，登录时直连租户库验密，无需同步平台库
        log.info("重置用户密码: id={}", id);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        // 先删除旧关联
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId));
        // 再插入新关联
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            }
        }
        authInterceptor.evict(userId);
        log.info("分配角色: userId={} 角色数={}", userId, roleIds != null ? roleIds.size() : 0);
    }

    /** 生成用户编码: USER + yyyyMMdd + 0001递增 */
    public String generateUserCode() {
        String prefix = "USER" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.likeRight("user_code", prefix).orderByDesc("user_code").last("LIMIT 1");
        SysUser last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getUserCode() != null) {
            try {
                seq = Integer.parseInt(last.getUserCode().substring(prefix.length())) + 1;
            } catch (Exception e) {
                log.warn("新增账号失败：生成员工编码失败: prefix={}, lastCode={}, err={}", prefix, last.getUserCode(), e.getMessage());
                throw new BusinessException("新增账号失败：生成员工编码失败：" + e.getMessage());
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
