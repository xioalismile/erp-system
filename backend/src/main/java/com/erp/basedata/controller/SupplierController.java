package com.erp.basedata.controller;

import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.SupplierService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商 Controller
 * 路由: /api/basedata/supplier
 */
@RestController
@RequestMapping("/api/basedata/supplier")
public class SupplierController extends BaseCrudController<Supplier, Long> {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Override
    protected IService<Supplier> getService() { return supplierService; }

    @Override
    protected String getCodeField() { return "supplier_code"; }

    @Override
    protected String getIdField() { return "supplier_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("supplier_code", "supplier_name", "short_name"); }
}
