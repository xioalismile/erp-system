package com.erp.ecommerce.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.ecommerce.dto.PullOrdersDTO;
import com.erp.ecommerce.entity.EcomPlatformOrder;
import com.erp.ecommerce.service.EcomOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 电商平台订单接口（拉单 / 转销售订单） */
@Slf4j
@RestController
@RequestMapping("/api/ecommerce/orders")
@RequiredArgsConstructor
public class EcomOrderController {

    private final EcomOrderService orderService;

    @GetMapping
    public Result<PageResult<EcomPlatformOrder>> page(@RequestParam(defaultValue = "1") int current,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) Long shopId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer converted,
                                                      @RequestParam(required = false) String platform) {
        return Result.success(orderService.pageQuery(current, size, shopId, keyword, converted, platform));
    }

    @GetMapping("/{id}")
    public Result<EcomPlatformOrder> detail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id));
    }

    /** 拉取平台订单（按店铺 + 往前看天数） */
    @PostMapping("/pull")
    public Result<Map<String, Object>> pull(@RequestBody PullOrdersDTO dto) {
        return Result.success(orderService.pullOrders(dto.getShopId(), dto.getDays()));
    }

    /** 转销售订单（生成草稿态销售订单） */
    @PostMapping("/{id}/convert")
    public Result<Map<String, Object>> convert(@PathVariable Long id) {
        return Result.success(orderService.convertToSaleOrder(id));
    }
}
