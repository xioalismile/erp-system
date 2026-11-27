package com.erp.finance.prepayment.sale.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.finance.prepayment.sale.entity.SalePreReceipt;
import com.erp.finance.prepayment.sale.entity.SalePreReceiptItem;
import com.erp.finance.prepayment.sale.service.SalePreReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sale-pre-receipt")
@RequiredArgsConstructor
public class SalePreReceiptController {

    private final SalePreReceiptService salePreReceiptService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<SalePreReceipt>> pageList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String employeeId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围（替代 getCreatedByFilter 硬编码）
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(salePreReceiptService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder, headerFilters));
    }

    /** 列表页顶部统计卡片（与列表逐字同口径：同一数据权限列 create_by） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<SalePreReceipt> totalW = new QueryWrapper<>();
        if (scopeCtx != null) scopeCtx.applyTo(totalW, null, "create_by");
        long totalCount = salePreReceiptService.count(totalW);
        QueryWrapper<SalePreReceipt> pendingW = new QueryWrapper<SalePreReceipt>().eq("status", 1);
        if (scopeCtx != null) scopeCtx.applyTo(pendingW, null, "create_by");
        long pendingCount = salePreReceiptService.count(pendingW);
        QueryWrapper<SalePreReceipt> approvedW = new QueryWrapper<SalePreReceipt>().eq("status", 2);
        if (scopeCtx != null) scopeCtx.applyTo(approvedW, null, "create_by");
        long approvedCount = salePreReceiptService.count(approvedW);
        QueryWrapper<SalePreReceipt> writtenOffW = new QueryWrapper<SalePreReceipt>().eq("status", 3);
        if (scopeCtx != null) scopeCtx.applyTo(writtenOffW, null, "create_by");
        long writtenOffCount = salePreReceiptService.count(writtenOffW);
        // 卡片「本月新增」：本月 receipt_date 区间（当月 1 号 ~ 当月最后一天），与列表同数据权限口径
        QueryWrapper<SalePreReceipt> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "receipt_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        if (scopeCtx != null) scopeCtx.applyTo(monthW, null, "create_by");
        long monthCount = salePreReceiptService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", totalCount);
        data.put("monthCount", monthCount);
        data.put("pendingCount", pendingCount);
        data.put("approvedCount", approvedCount);
        data.put("writtenOffCount", writtenOffCount);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<SalePreReceipt> getDetail(@PathVariable Long id) {
        return Result.success(salePreReceiptService.getDetail(id));
    }

    @PostMapping
    public Result<SalePreReceipt> save(@RequestBody SalePreReceipt preReceipt, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        preReceipt.setCreateBy(operator);
        return Result.success(salePreReceiptService.savePreReceipt(preReceipt));
    }

    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        salePreReceiptService.audit(id);
        return Result.success();
    }

    /** 提交: 草稿 → 待审核(需审批) / 已生效(免审批) */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        salePreReceiptService.submit(id);
        return Result.success();
    }

    @PutMapping("/{id}/void")
    public Result<Void> voidReceipt(@PathVariable Long id) {
        salePreReceiptService.voidReceipt(id);
        return Result.success();
    }

    @PostMapping("/{id}/write-off")
    public Result<Void> writeOff(@PathVariable Long id, @RequestBody List<SalePreReceiptItem> items) {
        salePreReceiptService.writeOff(id, items);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        salePreReceiptService.delete(id);
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        salePreReceiptService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        salePreReceiptService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        salePreReceiptService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        salePreReceiptService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        salePreReceiptService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
