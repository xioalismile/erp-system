package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Employee;

public interface EmployeeService extends IService<Employee> {
    Employee getByCode(String employeeCode);
    boolean existsByCode(String employeeCode);
    boolean existsByCodeExcludeId(String employeeCode, Long employeeId);
}
