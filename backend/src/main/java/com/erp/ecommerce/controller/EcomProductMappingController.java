package com.erp.ecommerce.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.ecommerce.entity.EcomProductMapping;
import com.erp.ecommerce.service.EcomProductMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 电商商品映射接口 */
@Slf4j
@RestController
@RequestMapping("/api/ecommerce/product-mappings")
@RequiredArgsConstructor
public class EcomProductMappingController {

    private final EcomProductMappingService mappingService;

    @GetMapping
    public Result<PageResult<EcomProductMapping>> page(@RequestParam(defaultValue = "1") int current,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) Long shopId,
                                                       @RequestParam(required = false) String keyword) {
        return Result.success(mappingService.pageQuery(current, size, shopId, keyword));
    }

    @PostMapping
    public Result<Void> create(@RequestBody EcomProductMapping mapping) {
        mappingService.saveMapping(mapping);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody EcomProductMapping mapping) {
        mapping.setId(id);
        mappingService.updateMapping(mapping);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mappingService.deleteMapping(id);
        return Result.success();
    }
}
