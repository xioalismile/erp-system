package com.erp.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.purchase.entity.PurchaseReturn;
import com.erp.purchase.service.PurchaseReturnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 采购退货单控制器
 * 文档3.4 接口: GET/POST/PUT /api/purchase/returns
 */
@RestController
@RequestMapping("/api/purchase/returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<PurchaseReturn>> page(
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
        return Result.success(purchaseReturnService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    @GetMapping("/{id}")
    public Result<PurchaseReturn> detail(@PathVariable Long id) {
        return Result.success(purchaseReturnService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseReturn> create(@RequestBody PurchaseReturn returnBill, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) returnBill.setCreatedBy(username);
        purchaseReturnService.saveReturnWithItems(returnBill, returnBill.getItems());
        return Result.success(returnBill);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseReturn returnBill, HttpServletRequest req) {
        returnBill.setReturnId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) returnBill.setUpdatedBy(username);
        purchaseReturnService.saveReturnWithItems(returnBill, returnBill.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseReturnService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 草稿/待审核 → 已生效 (扣减库存+回写采购入库单已退货数量) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseReturnService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 (恢复库存+减少已退货数量, 已生成红字发票不可重置草稿) */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        purchaseReturnService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseReturnService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<PurchaseReturn> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "buyer_id");
        long totalReturns = purchaseReturnService.count(totalW);
        QueryWrapper<PurchaseReturn> draftW = new QueryWrapper<PurchaseReturn>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "buyer_id");
        long draftReturns = purchaseReturnService.count(draftW);
        QueryWrapper<PurchaseReturn> pendingW = new QueryWrapper<PurchaseReturn>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "buyer_id");
        long pendingReturns = purchaseReturnService.count(pendingW);
        QueryWrapper<PurchaseReturn> approvedW = new QueryWrapper<PurchaseReturn>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "buyer_id");
        long approvedReturns = purchaseReturnService.count(approvedW);
        QueryWrapper<PurchaseReturn> cancelledW = new QueryWrapper<PurchaseReturn>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, "buyer_id");
        long cancelledReturns = purchaseReturnService.count(cancelledW);
        // 卡片「本月退货」用：本月 return_date 区间（与列表同数据权限口径）
        QueryWrapper<PurchaseReturn> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "return_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "buyer_id");
        long monthReturns = purchaseReturnService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalReturns", totalReturns);
        data.put("draftReturns", draftReturns);
        data.put("pendingReturns", pendingReturns);
        data.put("approvedReturns", approvedReturns);
        data.put("cancelledReturns", cancelledReturns);
        data.put("monthReturns", monthReturns);
        return Result.success(data);
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseReturnService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseReturnService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        purchaseReturnService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseReturnService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        purchaseReturnService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseReturnService.deleteBill(id);
        return Result.success();
    }

}
