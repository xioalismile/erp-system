package com.erp.basedata.controller;

import com.erp.basedata.entity.Department;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.service.DepartmentService;
import com.erp.basedata.service.EmployeeService;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.baomidou.mybatisplus.extension.service.IService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;
import java.util.Map;

/**
 * 员工 Controller
 * 路由: /api/basedata/employee
 */
@Slf4j
@RestController
@RequestMapping("/api/basedata/employee")
public class EmployeeController extends BaseCrudController<Employee, Long> {

    private final EmployeeService employeeService;
    /** 员工下拉要显示「姓名 (部门)」，需回填部门名称（Employee.deptName 是 @TableField(exist=false)，SQL 不带出） */
    private final DepartmentService departmentService;

    public EmployeeController(EmployeeService employeeService, DepartmentService departmentService) {
        this.employeeService = employeeService;
        this.departmentService = departmentService;
    }

    @Override
    protected IService<Employee> getService() { return employeeService; }

    @Override
    protected String getCodeField() { return "employee_code"; }

    @Override
    protected String getIdField() { return "employee_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='employee'） */
    @Override
    protected String getCustomFieldEntityType() { return "employee"; }

    @Override
    protected List<String> getSearchFields() { return List.of("employee_code", "employee_name"); }

    /**
     * 重写save: 同步创建系统账号
     * 2026-09-02 修复：回传新员工 employeeId，前端同步后二次保存查重才能排除自身，
     * 否则保存成功后再点保存会被自己的用户名判定"已被占用"。
     * 父类签名固定 Result<Void>（不能协变到 Result<Long>，改重载会导致 URL 双映射 Ambiguous），
     * 故用泛型擦除把 employeeId 放进 data —— Jackson 序列化后前端拿到的仍是 {"data": <id>}。
     */
    @Override
    @PostMapping
    public Result<Void> save(@RequestBody Employee employee) {
        // 编码唯一性校验（编码留空 = 由 saveWithUser 按编号规则自动生成，不能拿空串查重）
        if (employee.getEmployeeCode() != null && !employee.getEmployeeCode().isBlank()
                && employeeService.existsByCode(employee.getEmployeeCode())) {
            return Result.error(409, "编码已存在");
        }
        employeeService.saveWithUser(employee);
        // 泛型擦除：raw Result 携带 employeeId（父类签名固定 Result<Void>，
        // 不能协变 Result<Long>，重载会导致同 URL 双映射 Ambiguous）
        @SuppressWarnings({"unchecked", "rawtypes"})
        Result result = Result.success(employee.getEmployeeId());
        return result;
    }

    /**
     * 重写delete: 联动清理系统账号（2026-09-02 修复孤儿账号占用用户名问题）
     */
    @Override
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.deleteWithUser(id);
        return Result.success();
    }

    /**
     * 重写batchDelete: 联动清理系统账号
     */
    @Override
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null) {
            ids.forEach(employeeService::deleteWithUser);
        }
        return Result.success();
    }

    /**
     * 重写update: 同步更新系统账号
     */
    @Override
    @PutMapping
    public Result<Void> update(@RequestBody Employee employee) {
        // 编辑时排除自身的编码唯一性校验
        if (employee.getEmployeeCode() != null && employee.getEmployeeId() != null
                && employeeService.existsByCodeExcludeId(employee.getEmployeeCode(), employee.getEmployeeId())) {
            return Result.error(409, "编码已存在");
        }
        employeeService.updateWithUser(employee);
        return Result.success();
    }

    /**
     * 重写get: 返回包含系统账号信息的详情
     */
    @Override
    @GetMapping("/{id}")
    public Result<Employee> get(@PathVariable Long id) {
        return Result.success(employeeService.getDetailWithUser(id));
    }

    /**
     * 重写 list：回填部门名称（2026-09-20）
     * <p>Employee.deptName 是 @TableField(exist=false) 的响应载体，MyBatis-Plus 不会带出，
     * 而 CRM 线索/商机/跟进、销售目标、采购订单等多个页面的员工下拉都用 `e.deptName` 拼「姓名 (部门)」，
     * 不回填则括号里的部门静默丢失（下拉只剩姓名）。与 MaterialController /list 回填 categoryName 同款做法。</p>
     * <p>回填失败只留空部门名，不抛异常、不阻断员工下拉与列表。</p>
     */
    @Override
    @GetMapping("/list")
    public Result<List<Employee>> list(@RequestParam(required = false) String status) {
        Result<List<Employee>> result = super.list(status);
        try {
            List<Employee> employees = result.getData();
            if (employees != null && !employees.isEmpty()) {
                Set<Long> deptIds = employees.stream()
                        .map(Employee::getDeptId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
                if (!deptIds.isEmpty()) {
                    Map<Long, String> deptNameMap = departmentService.listByIds(deptIds).stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    Department::getDeptId, Department::getDeptName, (a, b) -> a));
                    employees.forEach(e -> e.setDeptName(deptNameMap.get(e.getDeptId())));
                }
            }
        } catch (Exception e) {
            // 回填失败不影响员工列表：部门名留空即可，不得阻断下拉
            log.warn("员工列表回填部门名称失败: {}", e.getMessage());
        }
        return result;
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "created_at",
            "created_by",
            "dept_id",
            "education",
            "email",
            "employee_code",
            "employee_id",
            "employee_name",
            "entry_date",
            "gender",
            "id_card",
            "leave_date",
            "mobile_phone",
            "position",
            "remark",
            "status",
            "updated_at",
            "updated_by",
            "user_id"
        );
    }

    /**
     * 员工下拉选项（审批人选择等场景）
     * @param keyword   关键词（匹配姓名/工号）
     * @param activeOnly true=只返回在职员工（默认 true）
     */
    @GetMapping("/select-options")
    public Result<List<Map<String, Object>>> selectOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        return Result.success(employeeService.listSelectOptions(keyword, activeOnly));
    }

    /**
     * 校验用户名是否可用（新建/编辑查重）
     */
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long excludeEmployeeId) {
        return Result.success(employeeService.checkUsernameAvailable(username, excludeEmployeeId));
    }

    /**
     * 重置员工关联系统账号的密码为默认 123456789（可自定义）
     *
     * 安全审计 C-1：原实现无任何服务端校验，任意已登录用户可重置任意员工账号密码。
     * 2026-10-13 起改为可配置：「管理员角色 / 通配权限」或 显式勾选
     * 「员工档案 → 重置密码」按钮码（basedata:employee:resetPassword）均可执行——
     * 用户口径「核心是可以所有人都支持配置，关键是我有没有给权限」。
     * 「修改密码」（change-password，校验旧密码的自助改密）不受影响。
     */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestBody(required = false) java.util.Map<String, String> body) {
        if (!canResetPassword()) {
            return Result.error(403, "无权重置密码：请联系管理员在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「员工档案」的「重置密码」权限");
        }
        String newPassword = body == null ? null : body.get("newPassword");
        employeeService.resetEmployeePassword(id, newPassword);
        return Result.success();
    }

    /** 重置密码口径：管理员角色 / 通配权限 / 显式勾选「员工档案:重置密码」按钮码（2026-10-13 起可配置） */
    private static boolean canResetPassword() {
        jakarta.servlet.http.HttpServletRequest req = currentRequest();
        return SecurityUtils.hasAnyRole(req, "ADMIN", "SUPER_ADMIN")
                || SecurityUtils.hasVersionAdminRole(req)
                || SecurityUtils.hasPermission(req, "*")
                || SecurityUtils.hasPermission(req, "basedata:employee:resetPassword");
    }

    /**
     * 修改员工关联系统账号的密码（需校验原密码）
     */
    @PostMapping("/{id}/change-password")
    public Result<Void> changePassword(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        employeeService.changeEmployeePassword(id, body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }
}
