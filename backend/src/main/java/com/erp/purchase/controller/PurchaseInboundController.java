package com.erp.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.service.PurchaseInboundService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 采购入库单控制器
 * 文档3.3 接口: GET/POST/PUT /api/purchase/inbounds
 */
@RestController
@RequestMapping("/api/purchase/inbounds")
@RequiredArgsConstructor
public class PurchaseInboundController {

    private final PurchaseInboundService purchaseInboundService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<PurchaseInbound>> page(
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
        return Result.success(purchaseInboundService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    @GetMapping("/{id}")
    public Result<PurchaseInbound> detail(@PathVariable Long id) {
        return Result.success(purchaseInboundService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseInbound> create(@RequestBody PurchaseInbound inbound, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) inbound.setCreatedBy(username);
        // 保存 = 草稿：不做必填/业务校验（严格校验在提交环节）；与前端宽松保存口径一致
        purchaseInboundService.saveInboundWithItems(inbound, inbound.getItems());
        return Result.success(inbound);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseInbound inbound, HttpServletRequest req) {
        inbound.setReceiptId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) inbound.setUpdatedBy(username);
        purchaseInboundService.saveInboundWithItems(inbound, inbound.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 草稿/待审核 → 已生效 (增加库存+回写采购订单已入库数量) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 (扣减库存+减少已入库数量, 已生成发票不可重置草稿) */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseInboundService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<PurchaseInbound> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "buyer_id");
        long totalInbounds = purchaseInboundService.count(totalW);
        QueryWrapper<PurchaseInbound> draftW = new QueryWrapper<PurchaseInbound>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "buyer_id");
        long draftInbounds = purchaseInboundService.count(draftW);
        QueryWrapper<PurchaseInbound> pendingW = new QueryWrapper<PurchaseInbound>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "buyer_id");
        long pendingInbounds = purchaseInboundService.count(pendingW);
        QueryWrapper<PurchaseInbound> approvedW = new QueryWrapper<PurchaseInbound>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "buyer_id");
        long approvedInbounds = purchaseInboundService.count(approvedW);
        QueryWrapper<PurchaseInbound> cancelledW = new QueryWrapper<PurchaseInbound>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, "buyer_id");
        long cancelledInbounds = purchaseInboundService.count(cancelledW);
        // 卡片「本月入库」用：本月 receipt_date 区间（与列表同数据权限口径）
        QueryWrapper<PurchaseInbound> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "receipt_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "buyer_id");
        long monthInbounds = purchaseInboundService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalInbounds", totalInbounds);
        data.put("draftInbounds", draftInbounds);
        data.put("pendingInbounds", pendingInbounds);
        data.put("approvedInbounds", approvedInbounds);
        data.put("cancelledInbounds", cancelledInbounds);
        data.put("monthInbounds", monthInbounds);
        return Result.success(data);
    }

    /** 下推到采购发票: 已生效的入库单 → 生成发票(草稿) */
    @com.erp.common.bill.link.PushGuard(sourceType = "PURCHASE_INBOUND", targetType = "PURCHASE_INVOICE")
    @PostMapping("/{id}/push-to-invoice")
    public Result<PurchaseInvoice> pushToInvoice(@PathVariable Long id) {
        return Result.success(purchaseInboundService.pushToInvoice(id, null));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseInboundService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseInboundService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        purchaseInboundService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseInboundService.deleteBill(id);
        return Result.success();
    }

}
