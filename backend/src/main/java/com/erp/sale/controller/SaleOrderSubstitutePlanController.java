package com.erp.sale.controller;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.config.BusinessException;
import com.erp.sale.entity.SaleOrderSubstitutePlan;
import com.erp.sale.service.SaleOrderSubstitutePlanService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 替换发货指示（销售在订单行上预先指定替代品）。
 * 2026-11-27 替换发货需求；按钮权限沿用销售订单下推码 sale:order:push。
 */
@RestController
@RequestMapping("/api/sale/order-substitute-plans")
@RequiredArgsConstructor
public class SaleOrderSubstitutePlanController {

    private final SaleOrderSubstitutePlanService service;

    @GetMapping
    public Result<List<SaleOrderSubstitutePlan>> list(@RequestParam Long orderId) {
        return Result.success(service.listByOrder(orderId));
    }

    @PostMapping
    public Result<SaleOrderSubstitutePlan> create(@RequestBody SaleOrderSubstitutePlan plan,
                                                  HttpServletRequest req) {
        requirePlanPermission(req);
        return Result.success(service.savePlan(plan, currentUser(req)));
    }

    @PutMapping("/{id}")
    public Result<SaleOrderSubstitutePlan> update(@PathVariable Long id,
                                                  @RequestBody SaleOrderSubstitutePlan plan,
                                                  HttpServletRequest req) {
        requirePlanPermission(req);
        return Result.success(service.updatePlan(id, plan, currentUser(req)));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest req) {
        requirePlanPermission(req);
        service.cancelPlan(id, currentUser(req));
        return Result.success();
    }

    private void requirePlanPermission(HttpServletRequest req) {
        if (!SecurityUtils.hasPermission(req, "sale:order:push")) {
            throw new BusinessException("无替换发货权限（sale:order:push）");
        }
    }

    private String currentUser(HttpServletRequest req) {
        Object v = req.getAttribute("currentUsername");
        return v == null ? "system" : String.valueOf(v);
    }
}
