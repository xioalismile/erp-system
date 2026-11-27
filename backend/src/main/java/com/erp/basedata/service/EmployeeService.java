package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Employee;

import java.util.List;
import java.util.Map;

public interface EmployeeService extends IService<Employee> {
    Employee getByCode(String employeeCode);
    boolean existsByCode(String employeeCode);
    boolean existsByCodeExcludeId(String employeeCode, Long employeeId);

    /** 员工下拉选项（审批人选择等场景），keyword 匹配姓名/工号，activeOnly=true 只返回在职 */
    List<Map<String, Object>> listSelectOptions(String keyword, Boolean activeOnly);

    /** 保存员工并同步创建/更新系统账号 */
    void saveWithUser(Employee employee);

    /**
     * 账号 → 员工档案（2026-09-21）：确保该登录账号在员工档案中存在并绑定，不存在则按姓名自动新建（幂等）。
     * 口径：员工姓名取账号「真实姓名」、员工编码走 BD_EMPLOYEE 自动编号、状态在职。
     * 场景：用户管理新增/编辑账号、平台管理开租户建管理员、启动补齐存量未绑定账号。
     */
    Employee ensureEmployeeForUser(Long userId, String employeeName, String mobilePhone, String email);

    /** 账号被删除时：解绑并停用其员工档案（不删员工，历史单据的业绩归属不能断） */
    void unbindAndDisableEmployeeByUser(Long userId);

    /** 删除员工并联动清理系统账号（防孤儿账号占用用户名），账号被其他员工引用时仅解除关联 */
    void deleteWithUser(Long employeeId);

    /** 更新员工并同步更新系统账号 */
    void updateWithUser(Employee employee);

    /** 查询员工详情(包含关联的系统账号信息) */
    Employee getDetailWithUser(Long employeeId);

    /** 校验用户名是否可用(编辑时排除自身)，true=可用 */
    boolean checkUsernameAvailable(String username, Long excludeEmployeeId);

    /** 重置员工关联系统账号的密码 */
    void resetEmployeePassword(Long employeeId, String newPassword);

    /** 修改员工关联系统账号的密码(需校验旧密码) */
    void changeEmployeePassword(Long employeeId, String oldPassword, String newPassword);
}
