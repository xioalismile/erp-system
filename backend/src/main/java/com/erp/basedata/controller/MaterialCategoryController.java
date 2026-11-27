package com.erp.basedata.controller;

import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.service.MaterialCategoryService;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 物料分类 Controller
 * 路由: /api/basedata/material-category
 */
@RestController
@RequestMapping("/api/basedata/material-category")
public class MaterialCategoryController extends BaseCrudController<MaterialCategory, Long> {

    private final MaterialCategoryService materialCategoryService;
    private final DataScopeResolver dataScopeResolver;

    public MaterialCategoryController(MaterialCategoryService materialCategoryService,
                                      DataScopeResolver dataScopeResolver) {
        this.materialCategoryService = materialCategoryService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 数据权限（2026-09-28）：与物料档案同一份口径 ——
     * 可见分类 = 角色数据范围内的分类（仅本人＝自己创建的；本部门＝部门成员创建的；全部＝不过滤）
     *          ∪ 「员工权限补充」里勾选的货品类别白名单（勾了就可见，用户 2026-09-28 确认的口径）。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<MaterialCategory> qw) {
        DataScopeResolver.DataScopeContext ctx = dataScopeResolver.resolve(currentRequest());
        ctx.applyToWithWhitelist(qw, "category_id", ctx.getCategoryWhitelist());
    }

    @Override
    protected IService<MaterialCategory> getService() { return materialCategoryService; }

    @Override
    protected String getCodeField() { return "category_code"; }

    @Override
    protected String getIdField() { return "category_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='material_category'） */
    @Override
    protected String getCustomFieldEntityType() { return "material_category"; }

    @Override
    protected List<String> getSearchFields() { return List.of("category_code", "category_name"); }

    /** 新增：同级（同一父分类下）编码唯一。分类编码为本位段，如 01 可同时存在于 E、M 系列 */
    @Override
    @PostMapping
    public Result<Void> save(@RequestBody MaterialCategory entity) {
        if (entity.getCategoryCode() != null && !entity.getCategoryCode().isBlank()
                && materialCategoryService.existsUnderParent(entity.getCategoryCode(), entity.getParentId(), null)) {
            return Result.error(409, "同级下已存在该分类编码");
        }
        materialCategoryService.save(entity);
        return Result.success();
    }

    /** 编辑：同级编码唯一（排除自身） */
    @Override
    @PutMapping
    public Result<Void> update(@RequestBody MaterialCategory entity) {
        if (entity.getCategoryCode() != null && !entity.getCategoryCode().isBlank()
                && materialCategoryService.existsUnderParent(entity.getCategoryCode(), entity.getParentId(), entity.getCategoryId())) {
            return Result.error(409, "同级下已存在该分类编码");
        }
        materialCategoryService.updateById(entity);
        return Result.success();
    }

    /**
     * 返回全量物料分类列表，前端依据 parent_id 构建树
     */
    @GetMapping("/tree")
    public Result<List<MaterialCategory>> tree() {
        // 数据权限（2026-09-28）：树与列表同口径 —— 否则出现"列表已收窄、树仍是全量"两套口径
        QueryWrapper<MaterialCategory> qw = new QueryWrapper<>();
        applyDataScopeFilter(qw);
        return Result.success(materialCategoryService.list(qw));
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "category_code",
            "category_id",
            "category_level",
            "category_name",
            "created_at",
            "created_by",
            "parent_id",
            "remark",
            "sort_order",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
