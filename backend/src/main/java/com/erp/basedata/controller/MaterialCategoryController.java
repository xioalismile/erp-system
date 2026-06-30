package com.erp.basedata.controller;

import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.service.MaterialCategoryService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料分类 Controller
 * 路由: /api/basedata/material-category
 */
@RestController
@RequestMapping("/api/basedata/material-category")
public class MaterialCategoryController extends BaseCrudController<MaterialCategory, Long> {

    private final MaterialCategoryService materialCategoryService;

    public MaterialCategoryController(MaterialCategoryService materialCategoryService) {
        this.materialCategoryService = materialCategoryService;
    }

    @Override
    protected IService<MaterialCategory> getService() { return materialCategoryService; }

    @Override
    protected String getCodeField() { return "category_code"; }

    @Override
    protected String getIdField() { return "category_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("category_code", "category_name"); }

    /**
     * 返回全量物料分类列表，前端依据 parent_id 构建树
     */
    @GetMapping("/tree")
    public Result<List<MaterialCategory>> tree() {
        return Result.success(materialCategoryService.list());
    }
}
