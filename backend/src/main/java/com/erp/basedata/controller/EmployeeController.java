package com.erp.basedata.controller;

import com.erp.basedata.entity.Employee;
import com.erp.basedata.service.EmployeeService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工 Controller
 * 路由: /api/basedata/employee
 */
@RestController
@RequestMapping("/api/basedata/employee")
public class EmployeeController extends BaseCrudController<Employee, Long> {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Override
    protected IService<Employee> getService() { return employeeService; }

    @Override
    protected String getCodeField() { return "employee_code"; }

    @Override
    protected String getIdField() { return "employee_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("employee_code", "employee_name"); }
}
