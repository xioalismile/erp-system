package com.erp.basedata.controller;

import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

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

    @Override
    protected List<String> getSearchFields() { return List.of("unit_code", "unit_name"); }
}
