package com.erp.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseRequest;
import com.erp.purchase.service.PurchaseRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 采购申请单控制器
 * 文档3.1 接口: GET/POST/PUT /api/purchase/requests
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase/requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<PurchaseRequest>> page(
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
        return Result.success(purchaseRequestService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    @GetMapping("/{id}")
    public Result<PurchaseRequest> detail(@PathVariable Long id) {
        return Result.success(purchaseRequestService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseRequest> create(@RequestBody PurchaseRequest request, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        Long userId = (Long) req.getAttribute("currentUserId");
        if (username != null) request.setCreatedBy(username);
        if (request.getApplicantId() == null && userId != null) {
            request.setApplicantId(userId);
            request.setApplierName(username);
        }
        purchaseRequestService.saveRequestWithItems(request, request.getItems());
        return Result.success(request);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseRequest request, HttpServletRequest req) {
        request.setRequestId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) request.setUpdatedBy(username);
        purchaseRequestService.saveRequestWithItems(request, request.getItems());
        return Result.success();
    }

    /** 提交审批: 草稿 → 待审批 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseRequestService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审批: 待审批 → 已审批 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseRequestService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已审批 → 待审批 (已关闭/已被引用不可重置草稿) */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        purchaseRequestService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审批 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        purchaseRequestService.close(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseRequestService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 下推到采购订单: 已审批的申请单 → 生成采购订单(草稿) */
    @com.erp.common.bill.link.PushGuard(sourceType = "PURCHASE_REQUEST", targetType = "PURCHASE_ORDER")
    @PostMapping("/{id}/push-to-order")
    public Result<PurchaseOrder> pushToOrder(@PathVariable Long id, HttpServletRequest req) {
        return Result.success(purchaseRequestService.pushToOrder(id, (String) req.getAttribute("currentUsername")));
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<PurchaseRequest> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "applicant_id");
        long totalRequests = purchaseRequestService.count(totalW);
        QueryWrapper<PurchaseRequest> draftW = new QueryWrapper<PurchaseRequest>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "applicant_id");
        long draftRequests = purchaseRequestService.count(draftW);
        QueryWrapper<PurchaseRequest> pendingW = new QueryWrapper<PurchaseRequest>().in("status", "待审批", "待审核");
        scopeCtx.applyTo(pendingW, "applicant_id");
        long pendingRequests = purchaseRequestService.count(pendingW);
        QueryWrapper<PurchaseRequest> approvedW = new QueryWrapper<PurchaseRequest>().in("status", "已审批", "已生效");
        scopeCtx.applyTo(approvedW, "applicant_id");
        long approvedRequests = purchaseRequestService.count(approvedW);
        QueryWrapper<PurchaseRequest> closedW = new QueryWrapper<PurchaseRequest>().eq("status", "已关闭");
        scopeCtx.applyTo(closedW, "applicant_id");
        long closedRequests = purchaseRequestService.count(closedW);
        // 卡片「本月申请」用：本月 request_date 区间（与列表同数据权限口径）
        QueryWrapper<PurchaseRequest> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "request_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "applicant_id");
        long monthRequests = purchaseRequestService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalRequests", totalRequests);
        data.put("draftRequests", draftRequests);
        data.put("pendingRequests", pendingRequests);
        data.put("approvedRequests", approvedRequests);
        data.put("closedRequests", closedRequests);
        data.put("monthRequests", monthRequests);
        return Result.success(data);
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseRequestService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseRequestService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        purchaseRequestService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已审批 → close_status=1（status 保持已审批，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseRequestService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        purchaseRequestService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseRequestService.deleteBill(id);
        return Result.success();
    }

}
