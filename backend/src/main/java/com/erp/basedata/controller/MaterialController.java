package com.erp.basedata.controller;

import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料 Controller
 * 路由: /api/basedata/material
 */
@RestController
@RequestMapping("/api/basedata/material")
public class MaterialController extends BaseCrudController<Material, Long> {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @Override
    protected IService<Material> getService() { return materialService; }

    @Override
    protected String getCodeField() { return "material_code"; }

    @Override
    protected String getIdField() { return "material_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("material_code", "material_name", "full_name"); }
}
