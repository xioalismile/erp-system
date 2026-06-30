package com.erp.purchase.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.service.PurchaseInboundService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase/inbounds")
@RequiredArgsConstructor
public class PurchaseInboundController {

    private final PurchaseInboundService purchaseInboundService;
    private final SupplierService supplierService;

    @GetMapping
    public Result<PageResult<PurchaseInbound>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(purchaseInboundService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<PurchaseInbound> detail(@PathVariable Long id) {
        return Result.success(purchaseInboundService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseInbound> create(@RequestBody PurchaseInbound inbound) {
        purchaseInboundService.saveInboundWithItems(inbound, inbound.getItems());
        return Result.success(inbound);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseInbound inbound) {
        inbound.setReceiptId(id);
        purchaseInboundService.saveInboundWithItems(inbound, inbound.getItems());
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseInboundService.cancel(id, body.get("reason"));
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
        long totalInbounds = purchaseInboundService.count();
        long draftInbounds = purchaseInboundService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound>().eq("status", "草稿"));
        long approvedInbounds = purchaseInboundService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound>().eq("status", "已审核"));
        long cancelledInbounds = purchaseInboundService.count(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound>().eq("status", "已取消"));

        Map<String, Object> data = new HashMap<>();
        data.put("totalInbounds", totalInbounds);
        data.put("draftInbounds", draftInbounds);
        data.put("approvedInbounds", approvedInbounds);
        data.put("cancelledInbounds", cancelledInbounds);
        return Result.success(data);
    }
}
