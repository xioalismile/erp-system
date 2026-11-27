package com.erp.ecommerce.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.ecommerce.entity.EcomShop;
import com.erp.ecommerce.service.EcomShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 电商店铺授权接口 */
@Slf4j
@RestController
@RequestMapping("/api/ecommerce/shops")
@RequiredArgsConstructor
public class EcomShopController {

    private final EcomShopService shopService;

    @GetMapping
    public Result<PageResult<EcomShop>> page(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.success(shopService.pageQuery(current, size, keyword, status));
    }

    /** 下拉用：全部启用店铺 */
    @GetMapping("/enabled")
    public Result<List<EcomShop>> enabled() {
        return Result.success(shopService.listEnabled());
    }

    @GetMapping("/{id}")
    public Result<EcomShop> detail(@PathVariable Long id) {
        return Result.success(shopService.getDetail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody EcomShop shop) {
        shopService.saveShop(shop);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody EcomShop shop) {
        shop.setId(id);
        shopService.updateShop(shop);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shopService.deleteShop(id);
        return Result.success();
    }

    /** 授权测试：向卖家平台换取访问令牌，成功记"已授权" */
    @PostMapping("/{id}/test-auth")
    public Result<Void> testAuth(@PathVariable Long id) {
        shopService.testAuth(id);
        return Result.success();
    }
}
