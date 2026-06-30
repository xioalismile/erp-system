package com.erp.basedata.controller;

import com.erp.basedata.entity.PriceLevel;
import com.erp.basedata.service.PriceLevelService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

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

    @Override
    protected List<String> getSearchFields() { return List.of("price_level_code", "price_level_name"); }
}
