package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Department;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.mapper.DepartmentMapper;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysSerialNoConfigService serialNoService;
    private final DepartmentMapper departmentMapper;
    /** 平台库账号同步（2026-10-12）：员工档案建的账号必须同步平台库 + 租户关联，否则登录"查无此人" */
    private final com.erp.system.service.PlatformUserSyncService platformUserSyncService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public EmployeeServiceImpl(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper,
                               SysSerialNoConfigService serialNoService, DepartmentMapper departmentMapper,
                               com.erp.system.service.PlatformUserSyncService platformUserSyncService) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.serialNoService = serialNoService;
        this.departmentMapper = departmentMapper;
        this.platformUserSyncService = platformUserSyncService;
    }

    @Override
    public List<Map<String, Object>> listSelectOptions(String keyword, Boolean activeOnly) {
        QueryWrapper<Employee> qw = new QueryWrapper<>();
        if (Boolean.TRUE.equals(activeOnly)) {
            qw.eq("status", "在职");
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("employee_name", keyword).or().like("employee_code", keyword));
        }
        qw.orderByAsc("employee_code");
        qw.last("LIMIT 50");
        List<Employee> employees = list(qw);
        if (employees.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = employees.stream()
                .map(Employee::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : departmentMapper.selectList(new QueryWrapper<Department>().in("dept_id", deptIds)).stream()
                    .collect(Collectors.toMap(Department::getDeptId, d -> d.getDeptName() == null ? "" : d.getDeptName(), (a, b) -> a));
        return employees.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", e.getEmployeeId());
            m.put("userId", e.getUserId());
            m.put("employeeName", e.getEmployeeName());
            m.put("employeeCode", e.getEmployeeCode() == null ? "" : e.getEmployeeCode());
            m.put("departmentName", e.getDeptId() == null ? "" : deptNames.getOrDefault(e.getDeptId(), ""));
            m.put("positionName", e.getPosition() == null ? "" : e.getPosition());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public Employee getByCode(String employeeCode) {
        return getOne(new QueryWrapper<Employee>().eq("employee_code", employeeCode));
    }

    @Override
    public boolean existsByCode(String employeeCode) {
        return baseMapper.exists(new QueryWrapper<Employee>().eq("employee_code", employeeCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String employeeCode, Long employeeId) {
        return baseMapper.exists(new QueryWrapper<Employee>()
                .eq("employee_code", employeeCode).ne("employee_id", employeeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithUser(Employee employee) {
        if (employee.getEmployeeCode() == null || employee.getEmployeeCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_EMPLOYEE")) {
                throw new BusinessException("员工已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            employee.setEmployeeCode(serialNoService.generateSerialNo("BD_EMPLOYEE", this::nextEmployeeCode,
                    SegmentCodeProbe.of(baseMapper, "employee_code")));
        }
        if (exists(new QueryWrapper<Employee>().eq("employee_code", employee.getEmployeeCode()))) {
            throw new BusinessException("员工编码已存在");
        }
        save(employee);
        syncUserAccount(employee, true);
    }

    /**
     * 账号 → 员工档案（2026-09-21）：有账号就有员工档案，避免导入客户/分配业务员时映射不到人。
     * 已存在则同步姓名/手机号/邮箱；不存在则按姓名自动新建（员工编码走 BD_EMPLOYEE 规则），并回写 sys_user.employee_id。
     * 幂等：重复调用不会产生重复员工。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Employee ensureEmployeeForUser(Long userId, String employeeName, String mobilePhone, String email) {
        if (userId == null) {
            return null;
        }
        // selectList 取第一条：历史上存在重复数据时 selectOne 会抛异常（与用户名查重同坑）
        List<Employee> bound = list(new QueryWrapper<Employee>().eq("user_id", userId));
        if (!bound.isEmpty()) {
            Employee exist = bound.get(0);
            boolean changed = false;
            if (StringUtils.hasText(employeeName) && !employeeName.equals(exist.getEmployeeName())) {
                exist.setEmployeeName(employeeName);
                changed = true;
            }
            if (StringUtils.hasText(mobilePhone) && !mobilePhone.equals(exist.getMobilePhone())) {
                exist.setMobilePhone(mobilePhone);
                changed = true;
            }
            if (StringUtils.hasText(email) && !email.equals(exist.getEmail())) {
                exist.setEmail(email);
                changed = true;
            }
            if (changed) {
                updateById(exist);
            }
            return exist;
        }

        Employee employee = new Employee();
        employee.setEmployeeName(StringUtils.hasText(employeeName) ? employeeName.trim() : "账号" + userId);
        employee.setMobilePhone(mobilePhone);
        employee.setEmail(email);
        employee.setUserId(userId);
        employee.setStatus("在职");
        // 员工编码：与员工档案新增同口径（设置中心关闭自动编号时留空，交由后续手工补）
        if (serialNoService.isAutoGenerate("BD_EMPLOYEE")) {
            try {
                employee.setEmployeeCode(serialNoService.generateSerialNo("BD_EMPLOYEE", this::nextEmployeeCode,
                        SegmentCodeProbe.of(baseMapper, "employee_code")));
            } catch (Exception e) {
                log.warn("新增员工失败：生成员工编码失败: {}", e.getMessage(), e);
                throw new BusinessException("新增员工失败：生成员工编码失败：" + e.getMessage());
            }
        }
        save(employee);

        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getEmployeeId() == null) {
            user.setEmployeeId(employee.getEmployeeId());
            sysUserMapper.updateById(user);
        }
        log.info("账号已同步员工档案: userId={}, employeeId={}, name={}", userId, employee.getEmployeeId(), employee.getEmployeeName());
        return employee;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindAndDisableEmployeeByUser(Long userId) {
        if (userId == null) {
            return;
        }
        List<Employee> bound = list(new QueryWrapper<Employee>().eq("user_id", userId));
        for (Employee e : bound) {
            // 必须用 update+set 显式写 null：updateById 默认跳过 null 字段，user_id 会解绑不掉
            lambdaUpdate().eq(Employee::getEmployeeId, e.getEmployeeId())
                    .set(Employee::getUserId, null)
                    .set(Employee::getStatus, "停用")
                    .update();
        }
        if (!bound.isEmpty()) {
            log.info("账号已删除，其员工档案解绑并停用: userId={}, 员工数={}", userId, bound.size());
        }
    }

    /**
     * 防重续号探测：查询员工表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextEmployeeCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Employee last = getOne(new QueryWrapper<Employee>().likeRight("employee_code", prefix)
                .orderByDesc("employee_code").last("LIMIT 1"));
        if (last == null || last.getEmployeeCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getEmployeeCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithUser(Employee employee) {
        // 先更新员工
        updateById(employee);
        // 同步更新系统账号
        syncUserAccount(employee, false);
    }

    @Override
    public Employee getDetailWithUser(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee == null) return null;
        // 安全：详情接口不返回密码（即使是加密哈希也不暴露给前端）
        employee.setPassword(null);
        // 填充关联的系统账号信息
        if (employee.getUserId() != null) {
            SysUser user = sysUserMapper.selectById(employee.getUserId());
            if (user != null) {
                employee.setUsername(user.getUsername());
                // 查询角色ID
                SysUserRole ur = sysUserRoleMapper.selectOne(
                        new QueryWrapper<SysUserRole>().eq("user_id", user.getUserId()).last("LIMIT 1"));
                if (ur != null) {
                    employee.setRoleId(ur.getRoleId());
                }
            }
        }
        return employee;
    }

    @Override
    public boolean checkUsernameAvailable(String username, Long excludeEmployeeId) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        // 2026-09-02 修复：历史上 sys_user 存在重复 username（演示数据重复插入），
        // selectOne 查到多条会抛 TooManyResultsException 导致查重接口 500。
        // 改为 selectList 取第一条，重复数据不再阻断查重。
        List<SysUser> matches = sysUserMapper.selectList(
                new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (matches.isEmpty()) {
            return true;
        }
        SysUser exist = matches.get(0);
        // 同名用户存在：编辑场景若该用户正属于自身(employee_id 匹配)则算可用
        if (excludeEmployeeId != null && exist.getEmployeeId() != null && excludeEmployeeId.equals(exist.getEmployeeId())) {
            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithUser(Long employeeId) {
        Employee employee = getById(employeeId);
        if (employee == null) {
            return;
        }
        Long userId = employee.getUserId();
        // 2026-09-02 修复：删除员工必须联动清理系统账号，否则孤儿账号永久占用用户名，
        // 后续新建员工输入同名用户名会被"已被占用"拦截。
        removeById(employeeId);
        if (userId == null) {
            return;
        }
        // 该账号未被其他员工引用时才删除；被引用则仅解除本员工关联
        Long refCount = baseMapper.selectCount(
                new QueryWrapper<Employee>().eq("user_id", userId).ne("employee_id", employeeId));
        if (refCount != null && refCount == 0) {
            sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId));
            sysUserMapper.deleteById(userId);
            // ★ 平台库清理（2026-10-12）：只删关联会留下"仍能登录的孤儿账号"（与用户管理删除同口径）
            platformUserSyncService.deletePlatformUser(userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetEmployeePassword(Long employeeId, String newPassword) {
        Employee employee = getById(employeeId);
        if (employee == null) {
            throw new BusinessException("员工不存在");
        }
        if (employee.getUserId() == null) {
            throw new BusinessException("该员工未创建系统账号");
        }
        SysUser user = sysUserMapper.selectById(employee.getUserId());
        if (user == null) {
            throw new BusinessException("关联的系统账号不存在");
        }
        String pwd = StringUtils.hasText(newPassword) ? newPassword : "123456789";
        user.setPassword(passwordEncoder.encode(pwd));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        // ★ 平台库密码必须同步（2026-10-12）：登录验密以平台库优先，不同步=重置后员工仍登不进
        platformUserSyncService.syncUserUpdate(user.getUserId(), null, null, null, user.getPassword());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeEmployeePassword(Long employeeId, String oldPassword, String newPassword) {
        Employee employee = getById(employeeId);
        if (employee == null) {
            throw new BusinessException("员工不存在");
        }
        if (employee.getUserId() == null) {
            throw new BusinessException("该员工未创建系统账号");
        }
        SysUser user = sysUserMapper.selectById(employee.getUserId());
        if (user == null) {
            throw new BusinessException("关联的系统账号不存在");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(oldPassword == null ? "" : oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException("新密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        // ★ 平台库密码必须同步（2026-10-12）：登录验密以平台库优先，不同步=改密后旧密码还在平台库生效
        platformUserSyncService.syncUserUpdate(user.getUserId(), null, null, null, user.getPassword());
    }

    @Override
    public <E extends IPage<Employee>> E page(E page, Wrapper<Employee> queryWrapper) {
        E result = super.page(page, queryWrapper);
        fillDeptNames(result.getRecords());
        return result;
    }

    @Override
    public List<Employee> list(Wrapper<Employee> queryWrapper) {
        List<Employee> list = super.list(queryWrapper);
        fillDeptNames(list);
        return list;
    }

    /** 批量填充 deptName（按 deptId 关联部门表，避免 N+1） */
    private void fillDeptNames(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) return;
        Set<Long> deptIds = employees.stream()
                .map(Employee::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (deptIds.isEmpty()) return;
        Map<Long, String> deptNames = departmentMapper.selectList(
                new QueryWrapper<Department>().in("dept_id", deptIds)).stream()
                .collect(Collectors.toMap(Department::getDeptId,
                        d -> d.getDeptName() == null ? "" : d.getDeptName(), (a, b) -> a));
        for (Employee e : employees) {
            if (e.getDeptId() != null) {
                e.setDeptName(deptNames.getOrDefault(e.getDeptId(), ""));
            }
        }
    }

    /**
     * 同步员工与系统账号
     */
    private void syncUserAccount(Employee employee, boolean isNew) {
        String username = employee.getUsername();
        String password = employee.getPassword();
        Long roleId = employee.getRoleId();
        Long employeeId = employee.getEmployeeId();
        Long deptId = employee.getDeptId();

        // 如果没有用户名，跳过
        if (!StringUtils.hasText(username)) {
            return;
        }

        SysUser user;
        if (isNew || employee.getUserId() == null) {
            // 新建: 检查用户名是否已存在
            SysUser exist = sysUserMapper.selectOne(
                    new QueryWrapper<SysUser>().eq("username", username));
            if (exist != null) {
                throw new RuntimeException("用户名已存在: " + username);
            }
            // 创建新用户
            user = new SysUser();
            user.setUsername(username);
            user.setUserCode(username);
            user.setRealName(employee.getEmployeeName());
            user.setMobilePhone(employee.getMobilePhone());
            user.setEmail(employee.getEmail());
            user.setDeptId(deptId);
            user.setEmployeeId(employeeId);
            user.setUserType("员工");
            user.setIsAdmin((short) 0);
            user.setIsEnabled((short) 1);
            user.setIsLocked((short) 0);
            user.setStatus("正常");
            user.setCreatedAt(LocalDateTime.now());
            if (StringUtils.hasText(password)) {
                user.setPassword(passwordEncoder.encode(password));
            } else {
                // 默认密码: 123456789（与前端文案及重置密码默认值统一）
                user.setPassword(passwordEncoder.encode("123456789"));
            }
            sysUserMapper.insert(user);
            // 回填userId到employee
            employee.setUserId(user.getUserId());
            updateById(employee);
            // ★ 平台库同步（2026-10-12）：登录凭证只在业务主库+平台库查找，租户库建的账号
            // 不同步平台库 = 登录"查无此人"= 永远"用户名或密码错误"（121 生产实锤）
            platformUserSyncService.syncNewUserAfterCreate(user);
        } else {
            // 更新已有用户
            user = sysUserMapper.selectById(employee.getUserId());
            if (user == null) {
                // 关联用户不存在，重新创建
                user = new SysUser();
                user.setUsername(username);
                user.setUserCode(username);
                user.setCreatedAt(LocalDateTime.now());
                if (StringUtils.hasText(password)) {
                    user.setPassword(passwordEncoder.encode(password));
                }
                sysUserMapper.insert(user);
                employee.setUserId(user.getUserId());
                updateById(employee);
                // ★ 平台库同步（2026-10-12）：同新建分支口径
                platformUserSyncService.syncNewUserAfterCreate(user);
            } else {
                user.setUsername(username);
                user.setRealName(employee.getEmployeeName());
                user.setMobilePhone(employee.getMobilePhone());
                user.setEmail(employee.getEmail());
                user.setDeptId(deptId);
                user.setUpdatedAt(LocalDateTime.now());
                if (StringUtils.hasText(password)) {
                    user.setPassword(passwordEncoder.encode(password));
                }
                sysUserMapper.updateById(user);
                // ★ 平台库同步（2026-10-12）：姓名/状态/密码改了要跟上，否则"平台库还是旧值/旧密码"
                platformUserSyncService.syncUserUpdate(user.getUserId(), user.getRealName(),
                        user.getIsEnabled(), user.getStatus(), user.getPassword());
            }
        }

        // 处理角色分配
        if (roleId != null && user.getUserId() != null) {
            // 先删除旧角色关联
            sysUserRoleMapper.delete(
                    new QueryWrapper<SysUserRole>().eq("user_id", user.getUserId()));
            // 插入新角色
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getUserId());
            ur.setRoleId(roleId);
            ur.setIsPrimaryRole((short) 1);
            ur.setAssignDate(java.time.LocalDate.now());
            ur.setCreatedAt(LocalDateTime.now());
            sysUserRoleMapper.insert(ur);
        }
    }
}
