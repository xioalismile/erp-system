package com.erp.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.service.PurchaseOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final SupplierService supplierService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/orders")
    public Result<PageResult<PurchaseOrder>> page(
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
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围，
        // 替代原 isPurchaseOnly 角色编码硬编码的补丁式逻辑
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(purchaseOrderService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    @GetMapping("/orders/{id}")
    public Result<PurchaseOrder> detail(@PathVariable Long id) {
        return Result.success(purchaseOrderService.getDetail(id));
    }

    @PostMapping("/orders")
    public Result<PurchaseOrder> create(@RequestBody PurchaseOrder order, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) order.setCreatedBy(username);
        // 保存 = 草稿：不做必填/业务校验（严格校验在提交环节）；与前端宽松保存口径一致
        purchaseOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success(order);
    }

    @PutMapping("/orders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseOrder order, HttpServletRequest req) {
        order.setOrderId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) order.setUpdatedBy(username);
        purchaseOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/orders/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 草稿/待审核 → 已生效 */
    @PostMapping("/orders/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 (已关闭/已被引用不可重置草稿) */
    @PostMapping("/orders/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已生效 → 已关闭 */
    @PostMapping("/orders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        purchaseOrderService.close(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseOrderService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 结案: 已生效 → 已结案 */
    @PostMapping("/orders/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: 已结案 → 已生效 */
    @PostMapping("/orders/{id}/reopen")
    public Result<Void> reopenOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 驳回: 待审核 → 草稿 */
    @PostMapping("/orders/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        purchaseOrderService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废（双终态2，需下游未生效） */
    @PostMapping("/orders/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        purchaseOrderService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废（恢复到作废前状态） */
    @PostMapping("/orders/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        purchaseOrderService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿/驳回/已取消态允许） */
    @DeleteMapping("/orders/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseOrderService.deleteBill(id);
        return Result.success();
    }

    /** 查询订单行执行状态 (执行完毕/开票完毕) */
    @GetMapping("/orders/{id}/item-status")
    public Result<List<Map<String, Object>>> itemStatus(@PathVariable Long id) {
        return Result.success(purchaseOrderService.queryItemExecutionStatus(id));
    }

    /** 供应商下拉（2026-10-09 权限收口：与供应商档案同口径 —— 数据范围 + 供应商白名单） */
    @GetMapping("/suppliers")
    public Result<List<Supplier>> suppliers(@RequestParam(required = false) String keyword,
                                            HttpServletRequest request) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Supplier>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("supplier_name", keyword).or().like("supplier_code", keyword));
        }
        dataScopeResolver.applyVisibleSuppliers(wrapper, request);
        wrapper.orderByAsc("supplier_code");
        wrapper.last("LIMIT 50");
        return Result.success(supplierService.list(wrapper));
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<PurchaseOrder> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "buyer_id");
        long totalOrders = purchaseOrderService.count(totalW);
        QueryWrapper<PurchaseOrder> draftW = new QueryWrapper<PurchaseOrder>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "buyer_id");
        long draftOrders = purchaseOrderService.count(draftW);
        QueryWrapper<PurchaseOrder> approvedW = new QueryWrapper<PurchaseOrder>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "buyer_id");
        long approvedOrders = purchaseOrderService.count(approvedW);
        QueryWrapper<PurchaseOrder> closedW = new QueryWrapper<PurchaseOrder>().eq("status", "已关闭");
        scopeCtx.applyTo(closedW, "buyer_id");
        long closedOrders = purchaseOrderService.count(closedW);
        QueryWrapper<PurchaseOrder> cancelledW = new QueryWrapper<PurchaseOrder>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, "buyer_id");
        long cancelledOrders = purchaseOrderService.count(cancelledW);
        // 卡片「待审核」用
        QueryWrapper<PurchaseOrder> pendingW = new QueryWrapper<PurchaseOrder>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "buyer_id");
        long pendingOrders = purchaseOrderService.count(pendingW);
        // 卡片「本月采购」用：本月 order_date 区间（与列表同数据权限口径）
        QueryWrapper<PurchaseOrder> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "order_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "buyer_id");
        long monthOrders = purchaseOrderService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", totalOrders);
        data.put("draftOrders", draftOrders);
        data.put("pendingOrders", pendingOrders);
        data.put("approvedOrders", approvedOrders);
        data.put("closedOrders", closedOrders);
        data.put("cancelledOrders", cancelledOrders);
        data.put("monthOrders", monthOrders);
        return Result.success(data);
    }

    /** 下推到采购入库单: 已生效的订单 → 生成入库单(草稿) */
    @com.erp.common.bill.link.PushGuard(sourceType = "PURCHASE_ORDER", targetType = "PURCHASE_INBOUND")
    @PostMapping("/orders/{id}/push-to-inbound")
    public Result<PurchaseInbound> pushToInbound(@PathVariable Long id) {
        return Result.success(purchaseOrderService.pushToInbound(id, null));
    }
}
