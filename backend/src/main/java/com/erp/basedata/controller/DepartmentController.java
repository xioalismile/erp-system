package com.erp.basedata.controller;

import com.erp.basedata.entity.Department;
import com.erp.basedata.service.DepartmentService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

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

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='department'） */
    @Override
    protected String getCustomFieldEntityType() { return "department"; }

    @Override
    protected List<String> getSearchFields() { return List.of("dept_code", "dept_name"); }

    /**
     * 返回全量部门列表，前端依据 parent_dept_id 构建树
     */
    @GetMapping("/tree")
    public Result<List<Department>> tree() {
        return Result.success(departmentService.list());
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "created_at",
            "created_by",
            "dept_code",
            "dept_id",
            "dept_name",
            "dept_type",
            "manager_id",
            "parent_dept_id",
            "remark",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
