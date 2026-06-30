package com.erp.basedata.controller;

import com.erp.basedata.entity.Department;
import com.erp.basedata.service.DepartmentService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门 Controller
 * 路由: /api/basedata/department
 */
@RestController
@RequestMapping("/api/basedata/department")
public class DepartmentController extends BaseCrudController<Department, Long> {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @Override
    protected IService<Department> getService() { return departmentService; }

    @Override
    protected String getCodeField() { return "dept_code"; }

    @Override
    protected String getIdField() { return "dept_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("dept_code", "dept_name"); }

    /**
     * 返回全量部门列表，前端依据 parent_dept_id 构建树
     */
    @GetMapping("/tree")
    public Result<List<Department>> tree() {
        return Result.success(departmentService.list());
    }
}
