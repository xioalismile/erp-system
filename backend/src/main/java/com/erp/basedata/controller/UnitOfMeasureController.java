package com.erp.basedata.controller;

import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 计量单位 Controller
 * 路由: /api/basedata/unit
 */
@RestController
@RequestMapping("/api/basedata/unit")
public class UnitOfMeasureController extends BaseCrudController<UnitOfMeasure, Long> {

    private final UnitOfMeasureService unitOfMeasureService;

    public UnitOfMeasureController(UnitOfMeasureService unitOfMeasureService) {
        this.unitOfMeasureService = unitOfMeasureService;
    }

    @Override
    protected IService<UnitOfMeasure> getService() { return unitOfMeasureService; }

    @Override
    protected String getCodeField() { return "unit_code"; }

    @Override
    protected String getIdField() { return "unit_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='unit'） */
    @Override
    protected String getCustomFieldEntityType() { return "unit"; }

    @Override
    protected List<String> getSearchFields() { return List.of("unit_code", "unit_name"); }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "conversion_rate",
            "created_at",
            "created_by",
            "is_base_unit",
            "remark",
            "status",
            "unit_code",
            "unit_group",
            "unit_id",
            "unit_name",
            "updated_at",
            "updated_by"
        );
    }
}
