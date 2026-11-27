package com.erp.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.service.PurchaseInvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 采购发票控制器
 * 文档3.5 接口: GET/POST/PUT /api/purchase/invoices
 */
@RestController
@RequestMapping("/api/purchase/invoices")
@RequiredArgsConstructor
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<PurchaseInvoice>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String supplierId,
            @RequestParam(required = false) String employeeId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(purchaseInvoiceService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    @GetMapping("/{id}")
    public Result<PurchaseInvoice> detail(@PathVariable Long id) {
        return Result.success(purchaseInvoiceService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseInvoice> create(@RequestBody PurchaseInvoice invoice, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) invoice.setCreatedBy(username);
        purchaseInvoiceService.saveInvoiceWithItems(invoice, invoice.getItems());
        return Result.success(invoice);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseInvoice invoice, HttpServletRequest req) {
        invoice.setInvoiceId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) invoice.setUpdatedBy(username);
        purchaseInvoiceService.saveInvoiceWithItems(invoice, invoice.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseInvoiceService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 草稿/待审核 → 已生效 (回写源单已开票数量) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseInvoiceService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 (减少源单已开票数量, 已勾稽不可重置草稿) */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        purchaseInvoiceService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseInvoiceService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<PurchaseInvoice> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, null);
        long totalInvoices = purchaseInvoiceService.count(totalW);
        QueryWrapper<PurchaseInvoice> draftW = new QueryWrapper<PurchaseInvoice>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, null);
        long draftInvoices = purchaseInvoiceService.count(draftW);
        QueryWrapper<PurchaseInvoice> pendingW = new QueryWrapper<PurchaseInvoice>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, null);
        long pendingInvoices = purchaseInvoiceService.count(pendingW);
        QueryWrapper<PurchaseInvoice> approvedW = new QueryWrapper<PurchaseInvoice>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, null);
        long approvedInvoices = purchaseInvoiceService.count(approvedW);
        QueryWrapper<PurchaseInvoice> cancelledW = new QueryWrapper<PurchaseInvoice>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, null);
        long cancelledInvoices = purchaseInvoiceService.count(cancelledW);
        QueryWrapper<PurchaseInvoice> redW = new QueryWrapper<PurchaseInvoice>().eq("is_red_invoice", 1);
        scopeCtx.applyTo(redW, null);
        long redInvoices = purchaseInvoiceService.count(redW);
        // 卡片「本月发票」用：本月 invoice_date 区间（与列表同数据权限口径）
        QueryWrapper<PurchaseInvoice> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "invoice_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, null);
        long monthInvoices = purchaseInvoiceService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalInvoices", totalInvoices);
        data.put("draftInvoices", draftInvoices);
        data.put("pendingInvoices", pendingInvoices);
        data.put("approvedInvoices", approvedInvoices);
        data.put("cancelledInvoices", cancelledInvoices);
        data.put("redInvoices", redInvoices);
        data.put("monthInvoices", monthInvoices);
        return Result.success(data);
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseInvoiceService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseInvoiceService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        purchaseInvoiceService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseInvoiceService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        purchaseInvoiceService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseInvoiceService.deleteBill(id);
        return Result.success();
    }

}
