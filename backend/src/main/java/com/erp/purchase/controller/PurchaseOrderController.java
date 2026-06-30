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
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        return Result.success(purchaseOrderService.pageQuery(current, size, supplierName, status, orderNo));
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
        order.setId(id);
        purchaseOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    /** 提交审核 */
    @PostMapping("/orders/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.submitAudit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消 */
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseOrderService.cancelOrder(id, body.get("reason"));
        return Result.success();
    }

    /**
     * 从销售订单转采购订单
     * POST /api/purchase/orders/convert-from-sale/{saleOrderId}
     * body: { items: [{saleOrderItemId: 1, quantity: 100}], buyer: "张三" }
     * items为空则全部转
     */
    @PostMapping("/orders/convert-from-sale/{saleOrderId}")
    public Result<List<PurchaseOrder>> convertFromSale(
            @PathVariable Long saleOrderId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest req) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemSelection = body != null ? (List<Map<String, Object>>) body.get("items") : null;
        String buyer = body != null ? (String) body.get("buyer") : (String) req.getAttribute("currentUsername");

        List<PurchaseOrder> orders = purchaseOrderService.convertFromSaleOrder(saleOrderId, itemSelection, buyer);
        return Result.success(orders);
    }

    /**
     * 从MRP采购建议生成采购订单
     * POST /api/purchase/orders/generate-from-mrp
     * body: { suggestions: [...], buyer: "张三" }
     */
    @PostMapping("/orders/generate-from-mrp")
    public Result<List<PurchaseOrder>> generateFromMrp(
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
        String buyer = body != null ? (String) body.get("buyer") : (String) req.getAttribute("currentUsername");

        List<PurchaseOrder> orders = purchaseOrderService.generateFromMrp(suggestions, buyer);
        return Result.success(orders);
    }

    /** 采购入库 */
    @PostMapping("/orders/{id}/inbound")
    public Result<Void> inbound(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        String warehouse = body.get("warehouse");
        String operator = (String) req.getAttribute("currentUsername");
        purchaseOrderService.inbound(id, warehouse, operator);
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
}
