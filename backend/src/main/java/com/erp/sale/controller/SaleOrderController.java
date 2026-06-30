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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        return Result.success(saleOrderService.pageQuery(current, size, customerName, status, orderNo));
    }

    @GetMapping("/orders/{id}")
    public Result<SaleOrder> detail(@PathVariable Long id) {
        return Result.success(saleOrderService.getDetail(id));
    }

    @PostMapping("/orders")
    public Result<SaleOrder> create(@RequestBody SaleOrder order, HttpServletRequest req) {
        saleOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success(order);
    }

    @PutMapping("/orders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleOrder order) {
        order.setId(id);
        saleOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 已确认 */
    @PostMapping("/orders/{id}/submit")
    public Result<Void> submitAudit(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.submitAudit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 发货: 已确认 → 已发货 (扣减库存) */
    @PostMapping("/orders/{id}/ship")
    public Result<Void> ship(@PathVariable Long id, @RequestBody ShipRequest body, HttpServletRequest req) {
        saleOrderService.shipOrder(id, (String) req.getAttribute("currentUsername"),
                body.getLogisticsNo(), body.getDeliveryMethod());
        return Result.success();
    }

    /** 开票: 已发货 → 已开票 */
    @PostMapping("/orders/{id}/invoice")
    public Result<Void> invoice(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleOrderService.invoiceOrder(id, body.get("invoiceNo"));
        return Result.success();
    }

    /** 完成: 已开票 → 已完成 */
    @PostMapping("/orders/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        saleOrderService.completeOrder(id);
        return Result.success();
    }

    /** 取消: 草稿/已确认 → 已取消 */
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleOrderService.cancelOrder(id, body.get("reason"));
        return Result.success();
    }

    /** 退货: 已发货/已完成 → 已退货 (库存回退) */
    @PostMapping("/orders/{id}/return")
    public Result<Void> returnOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleOrderService.returnOrder(id, body.get("reason"));
        return Result.success();
    }

    /** 复制订单: 生成新草稿 */
    @PostMapping("/orders/{id}/copy")
    public Result<SaleOrder> copy(@PathVariable Long id) {
        return Result.success(saleOrderService.copyOrder(id));
    }

    /** 收款登记 */
    @PostMapping("/orders/{id}/payment")
    public Result<Void> payment(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        saleOrderService.recordPayment(id, body.get("amount"));
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
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>();
        long totalOrders = saleOrderService.count();
        long draftOrders = saleOrderService.count(wrapper.eq("status", "草稿"));
        long confirmedOrders = saleOrderService.count(wrapper.eq("status", "已确认"));
        long shippedOrders = saleOrderService.count(wrapper.eq("status", "已发货"));
        long completedOrders = saleOrderService.count(wrapper.eq("status", "已完成"));
        long cancelledOrders = saleOrderService.count(wrapper.eq("status", "已取消"));
        return Result.success(Map.of(
                "totalOrders", totalOrders,
                "draftOrders", draftOrders,
                "confirmedOrders", confirmedOrders,
                "shippedOrders", shippedOrders,
                "completedOrders", completedOrders,
                "cancelledOrders", cancelledOrders
        ));
    }

    @Data
    public static class ShipRequest {
        private String logisticsNo;
        private String deliveryMethod;
    }
}
