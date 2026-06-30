package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.service.SaleOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sale")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService saleOrderService;
    private final CustomerService customerService;
    private final MaterialService materialService;

    @GetMapping("/orders")
    public Result<PageResult<SaleOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(saleOrderService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/orders/{id}")
    public Result<SaleOrder> detail(@PathVariable Long id) {
        return Result.success(saleOrderService.getDetail(id));
    }

    @PostMapping("/orders")
    public Result<SaleOrder> create(@RequestBody SaleOrder order) {
        saleOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success(order);
    }

    @PutMapping("/orders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleOrder order) {
        order.setOrderId(id);
        saleOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 */
    @PostMapping("/orders/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/orders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        saleOrderService.close(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleOrderService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 客户下拉 */
    @GetMapping("/customers")
    public Result<List<Customer>> customers(@RequestParam(required = false) String keyword) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Customer>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("customer_name", keyword).or().like("customer_code", keyword);
        }
        wrapper.last("LIMIT 50");
        return Result.success(customerService.list(wrapper));
    }

    /** 物料下拉 */
    @GetMapping("/materials")
    public Result<List<Material>> materials(@RequestParam(required = false) String keyword) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Material>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("material_name", keyword).or().like("material_code", keyword);
        }
        wrapper.last("LIMIT 50");
        return Result.success(materialService.list(wrapper));
    }

    /** 仪表盘统计 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        long totalOrders = saleOrderService.count();
        long draftOrders = saleOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>().eq("status", "草稿"));
        long approvedOrders = saleOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>().eq("status", "已审核"));
        long closedOrders = saleOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>().eq("status", "已关闭"));
        long cancelledOrders = saleOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>().eq("status", "已取消"));

        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", totalOrders);
        data.put("draftOrders", draftOrders);
        data.put("approvedOrders", approvedOrders);
        data.put("closedOrders", closedOrders);
        data.put("cancelledOrders", cancelledOrders);
        return Result.success(data);
    }
}
