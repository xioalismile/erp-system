package com.erp.purchase.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.service.PurchaseOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final SupplierService supplierService;

    @GetMapping("/orders")
    public Result<PageResult<PurchaseOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(purchaseOrderService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/orders/{id}")
    public Result<PurchaseOrder> detail(@PathVariable Long id) {
        return Result.success(purchaseOrderService.getDetail(id));
    }

    @PostMapping("/orders")
    public Result<PurchaseOrder> create(@RequestBody PurchaseOrder order) {
        purchaseOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success(order);
    }

    @PutMapping("/orders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseOrder order) {
        order.setOrderId(id);
        purchaseOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 */
    @PostMapping("/orders/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/orders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        purchaseOrderService.close(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseOrderService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 供应商下拉 */
    @GetMapping("/suppliers")
    public Result<List<Supplier>> suppliers(@RequestParam(required = false) String keyword) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Supplier>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("supplier_name", keyword).or().like("supplier_code", keyword);
        }
        wrapper.last("LIMIT 50");
        return Result.success(supplierService.list(wrapper));
    }

    /** 仪表盘统计 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        long totalOrders = purchaseOrderService.count();
        long draftOrders = purchaseOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder>().eq("status", "草稿"));
        long approvedOrders = purchaseOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder>().eq("status", "已审核"));
        long closedOrders = purchaseOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder>().eq("status", "已关闭"));
        long cancelledOrders = purchaseOrderService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder>().eq("status", "已取消"));

        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", totalOrders);
        data.put("draftOrders", draftOrders);
        data.put("approvedOrders", approvedOrders);
        data.put("closedOrders", closedOrders);
        data.put("cancelledOrders", cancelledOrders);
        return Result.success(data);
    }
}
