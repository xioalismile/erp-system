package com.erp.basedata.controller;

import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库 Controller
 * 路由: /api/basedata/warehouse
 */
@RestController
@RequestMapping("/api/basedata/warehouse")
public class WarehouseController extends BaseCrudController<Warehouse, Long> {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Override
    protected IService<Warehouse> getService() { return warehouseService; }

    @Override
    protected String getCodeField() { return "stock_code"; }

    @Override
    protected String getIdField() { return "stock_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("stock_code", "stock_name"); }
}
