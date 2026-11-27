package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.service.SaleInvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sale")
@RequiredArgsConstructor
public class SaleInvoiceController {

    private final SaleInvoiceService saleInvoiceService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/invoices")
    public Result<PageResult<SaleInvoice>> page(
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
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(saleInvoiceService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 仪表盘统计（与列表同数据权限口径：按 saler_id 过滤）—— 列表页顶部统计卡片数据源 */
    @GetMapping("/invoices/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice> draftW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "saler_id");
        long draftInvoices = saleInvoiceService.count(draftW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice> pendingW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "saler_id");
        long pendingInvoices = saleInvoiceService.count(pendingW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice> approvedW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "saler_id");
        long approvedInvoices = saleInvoiceService.count(approvedW);
        // 「本月开票」卡：按单据日期 invoice_date 落在当月 1 号~当月最后一天（与列表点卡片后的区间筛选同口径）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice> monthW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "invoice_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "saler_id");
        long monthInvoices = saleInvoiceService.count(monthW);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthInvoices", monthInvoices);
        data.put("draftInvoices", draftInvoices);
        data.put("pendingInvoices", pendingInvoices);
        data.put("approvedInvoices", approvedInvoices);
        return Result.success(data);
    }

    @GetMapping("/invoices/{id}")
    public Result<SaleInvoice> detail(@PathVariable Long id) {
        return Result.success(saleInvoiceService.getDetail(id));
    }

    @PostMapping("/invoices")
    public Result<SaleInvoice> create(@RequestBody SaleInvoice saleInvoice) {
        saleInvoiceService.saveInvoiceWithItems(saleInvoice, saleInvoice.getItems());
        return Result.success(saleInvoice);
    }

    @PutMapping("/invoices/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleInvoice saleInvoice) {
        saleInvoice.setInvoiceId(id);
        saleInvoiceService.saveInvoiceWithItems(saleInvoice, saleInvoice.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/invoices/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        saleInvoiceService.submitForApproval(id);
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/invoices/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        saleInvoiceService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/invoices/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleInvoiceService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /**
     * 批量查询源单明细是否被互斥锁定（销售发票选单时调用）。
     * 互斥规则：销售订单与销售出库单"二选一"，任一路径已被开票引用则另一路径锁定。
     *
     * 入参: { sourceType: "销售订单"|"销售出库单", sourceItemIds: [1,2,3] }
     * 出参: { lockedItemIds: [1,2,3] }
     */
    @PostMapping("/invoices/mutex-check")
    public Result<Map<String, Object>> mutexCheck(@RequestBody Map<String, Object> body) {
        String sourceType = (String) body.get("sourceType");
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("sourceItemIds");
        List<Long> sourceItemIds = new java.util.ArrayList<>();
        if (rawIds != null) {
            for (Object o : rawIds) {
                if (o instanceof Number) {
                    sourceItemIds.add(((Number) o).longValue());
                }
            }
        }
        List<Long> locked = saleInvoiceService.checkMutex(sourceType, sourceItemIds);
        return Result.success(Map.of("lockedItemIds", locked));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/invoices/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        saleInvoiceService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/invoices/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        saleInvoiceService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/invoices/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        saleInvoiceService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/invoices/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        saleInvoiceService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/invoices/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        saleInvoiceService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/invoices/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        saleInvoiceService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/invoices/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleInvoiceService.deleteBill(id);
        return Result.success();
    }

}
