package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/masterdata")
@RequiredArgsConstructor
public class MasterDataController {

    private final CustomerService customerService;
    private final MaterialService materialService;

    // ========== 客户管理 ==========

    @GetMapping("/customers")
    public Result<List<Customer>> listCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("customer_name", keyword).or().like("customer_code", keyword);
        }
        wrapper.last("LIMIT " + limit);
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
        customerService.removeById(id);
        return Result.success();
    }

    // ========== 物料管理 ==========

    @GetMapping("/materials")
    public Result<List<Material>> listMaterials(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "50") int limit) {
        QueryWrapper<Material> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("material_name", keyword).or().like("material_code", keyword);
        }
        if (category != null) wrapper.eq("category_id", category);
        wrapper.last("LIMIT " + limit);
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
        materialService.removeById(id);
        return Result.success();
    }
}
