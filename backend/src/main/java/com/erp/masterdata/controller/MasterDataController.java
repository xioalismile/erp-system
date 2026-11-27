package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.ReferenceCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/masterdata")
@RequiredArgsConstructor
public class MasterDataController {

    private final CustomerService customerService;
    private final MaterialService materialService;
    private final ReferenceCheckService referenceCheckService;
    private final DataScopeResolver dataScopeResolver;

    // ========== 客户管理 ==========

    /**
     * 客户联想下拉（列表页 / 单据报表「客户」筛选框的数据源）。
     *
     * <p>2026-10-09 权限收口：此前只按名称/编码模糊查库、取前 50 条，<b>没有任何数据权限</b>，
     * 导致「仅本人数据」的角色仍能在筛选框里翻出全公司客户。现统一调用
     * {@link DataScopeResolver#applyVisibleCustomers}（数据范围 + 客户白名单 + 仅正式客户/存量档案客户），
     * 与客户档案列表、单据选客户下拉口径完全一致。</p>
     */
    @GetMapping("/customers")
    public Result<List<Customer>> listCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit,
            jakarta.servlet.http.HttpServletRequest req) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 关键字必须成组 and(...)：平铺 or() 会吃掉后面追加的数据权限条件（本项目已记录的坑）
            wrapper.and(w -> w.like("customer_name", keyword).or().like("customer_code", keyword));
        }
        // 数据权限 + 档案域准入（与客户档案同口径）
        dataScopeResolver.applyVisibleCustomers(wrapper, req);
        wrapper.orderByAsc("customer_code");
        wrapper.last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return Result.success(customerService.list(wrapper));
    }

    @PostMapping("/customers")
    public Result<Customer> createCustomer(@RequestBody Customer customer) {
        customerService.save(customer);
        return Result.success(customer);
    }

    @PutMapping("/customers/{id}")
    public Result<Void> updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
        customer.setCustomerId(id);
        customerService.updateById(customer);
        return Result.success();
    }

    @DeleteMapping("/customers/{id}")
    public Result<Void> deleteCustomer(@PathVariable Long id) {
        // 删除前校验：被业务数据引用的客户禁止删除（防止单据悬空引用）
        String refErr = referenceCheckService.checkCustomer(id);
        if (refErr != null) {
            return Result.error(409, refErr);
        }
        customerService.removeById(id);
        return Result.success();
    }

    // ========== 物料管理 ==========

    /**
     * 物料联想下拉。2026-10-09 权限收口：统一调用
     * {@link DataScopeResolver#applyVisibleMaterials}（数据范围 + 货品类别白名单），与物料档案列表同口径。
     */
    @GetMapping("/materials")
    public Result<List<Material>> listMaterials(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "50") int limit,
            jakarta.servlet.http.HttpServletRequest req) {
        QueryWrapper<Material> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("material_name", keyword).or().like("material_code", keyword));
        }
        if (category != null) wrapper.eq("category_id", category);
        dataScopeResolver.applyVisibleMaterials(wrapper, req);
        wrapper.orderByAsc("material_code");
        wrapper.last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return Result.success(materialService.list(wrapper));
    }

    @PostMapping("/materials")
    public Result<Material> createMaterial(@RequestBody Material material) {
        materialService.save(material);
        return Result.success(material);
    }

    @PutMapping("/materials/{id}")
    public Result<Void> updateMaterial(@PathVariable Long id, @RequestBody Material material) {
        material.setMaterialId(id);
        materialService.updateById(material);
        return Result.success();
    }

    @DeleteMapping("/materials/{id}")
    public Result<Void> deleteMaterial(@PathVariable Long id) {
        // 删除前校验：被库存/单据/BOM 等引用的物料禁止删除（2026-09-19 补：与上面客户删除、
        // 以及 /api/basedata/material 的删除校验保持同口径，避免"这条路径能删、那条路径删不掉"）
        String refErr = referenceCheckService.checkMaterial(id);
        if (refErr != null) {
            return Result.error(409, refErr);
        }
        materialService.removeById(id);
        return Result.success();
    }
}
