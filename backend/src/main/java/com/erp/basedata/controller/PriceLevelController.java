package com.erp.basedata.controller;

import com.erp.basedata.entity.PriceLevel;
import com.erp.basedata.service.PriceLevelService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 价格等级 Controller
 * 路由: /api/basedata/price-level
 */
@RestController
@RequestMapping("/api/basedata/price-level")
public class PriceLevelController extends BaseCrudController<PriceLevel, Long> {

    private final PriceLevelService priceLevelService;

    public PriceLevelController(PriceLevelService priceLevelService) {
        this.priceLevelService = priceLevelService;
    }

    @Override
    protected IService<PriceLevel> getService() { return priceLevelService; }

    @Override
    protected String getCodeField() { return "price_level_code"; }

    @Override
    protected String getIdField() { return "price_level_id"; }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='price_level'） */
    @Override
    protected String getCustomFieldEntityType() { return "price_level"; }

    @Override
    protected List<String> getSearchFields() { return List.of("price_level_code", "price_level_name"); }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "created_at",
            "created_by",
            "price_adjust_rate",
            "price_level_code",
            "price_level_id",
            "price_level_name",
            "remark",
            "sort_order",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
