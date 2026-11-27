package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.sale.entity.SaleExchange;
import com.erp.sale.entity.SaleExchangeItem;
import com.erp.sale.service.SaleExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售换货单（替换货）。
 *
 * <p>入口约束：只能由销售订单「销售替代出库」按钮生成（from-order），不提供无来源的手工新建。</p>
 */
@RestController
@RequestMapping("/api/sale")
@RequiredArgsConstructor
public class SaleExchangeController {

    private final SaleExchangeService saleExchangeService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/exchanges")
    public Result<PageResult<SaleExchange>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String employeeId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch) {
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(saleExchangeService.pageQuery(p[0], p[1], keyword, status, startDate, endDate,
                customerId, employeeId, scopeCtx, sortBy, sortOrder, customSearch));
    }

    /** 销售订单页「销售替代出库」弹窗数据：台账七个数 + 替代清单 + 可关联退货额度 */
    @GetMapping("/exchanges/replaceable-lines")
    public Result<List<Map<String, Object>>> replaceableLines(@RequestParam Long orderId) {
        return Result.success(saleExchangeService.getReplaceableLines(orderId));
    }

    /** 由销售订单按钮生成换货单草稿 */
    @PostMapping("/exchanges/from-order")
    public Result<SaleExchange> createFromOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long orderId = body.get("orderId") == null ? null : Long.valueOf(String.valueOf(body.get("orderId")));
        if (orderId == null) throw new com.erp.config.BusinessException("缺少订单ID");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        String operator = (String) request.getAttribute("currentUsername");
        return Result.success(saleExchangeService.createFromOrder(orderId, lines, operator));
    }

    @GetMapping("/exchanges/{id}")
    public Result<SaleExchange> detail(@PathVariable Long id) {
        return Result.success(saleExchangeService.getDetail(id));
    }

    @PostMapping("/exchanges")
    public Result<SaleExchange> create(@RequestBody SaleExchange bill, HttpServletRequest request) {
        String operator = (String) request.getAttribute("currentUsername");
        bill.setCreatedBy(operator);
        bill.setUpdatedBy(operator);
        // 入口约束：必须带来源订单
        if (bill.getSourceId() == null) throw new com.erp.config.BusinessException("销售换货单必须由销售订单发起");
        return Result.success(saleExchangeService.saveExchangeWithItems(bill, bill.getItems()));
    }

    @PutMapping("/exchanges/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleExchange bill, HttpServletRequest request) {
        bill.setExchangeId(id);
        bill.setUpdatedBy((String) request.getAttribute("currentUsername"));
        saleExchangeService.saveExchangeWithItems(bill, bill.getItems());
        return Result.success();
    }

    @PostMapping("/exchanges/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        saleExchangeService.submitForApproval(id);
        return Result.success();
    }

    @PostMapping("/exchanges/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        saleExchangeService.approve(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/exchanges/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        saleExchangeService.reject(id, (String) request.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    @PostMapping("/exchanges/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest request) {
        saleExchangeService.reverseApprove(id, (String) request.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/exchanges/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, String> body,
                                 HttpServletRequest request) {
        String reason = body == null ? null : body.get("reason");
        saleExchangeService.voidBill(id, (String) request.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    @DeleteMapping("/exchanges/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleExchangeService.deleteBill(id);
        return Result.success();
    }
}
