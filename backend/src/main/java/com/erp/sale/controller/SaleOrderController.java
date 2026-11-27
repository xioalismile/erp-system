package com.erp.sale.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.util.SecurityUtils;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.service.SaleOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sale")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService saleOrderService;
    private final CustomerService customerService;
    private final MaterialService materialService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/orders")
    public Result<PageResult<SaleOrder>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String customerLevel,
            @RequestParam(required = false) String materialId,
            @RequestParam(required = false) String productLineId,
            @RequestParam(required = false) String employeeId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围，
        // 替代原 isSalesOnly 角色编码硬编码（SALES 才过滤）的补丁式逻辑
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 列表页筛选维度（数据看板穿透 + 通用搜索栏共用同一出口；缺省时行为与原先完全一致）
        // employeeId = 员工维度统一入参名（本页映射到 sale_order.saler_id，见 SaleOrderServiceImpl.applyDrillFilter）
        java.util.Map<String, String> drill = new java.util.HashMap<>();
        drill.put("startDate", startDate);
        drill.put("endDate", endDate);
        drill.put("customerId", customerId);
        drill.put("customerLevel", customerLevel);
        drill.put("materialId", materialId);
        drill.put("productLineId", productLineId);
        drill.put("employeeId", employeeId);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = new java.util.HashMap<>();
        allParams.forEach((k, v) -> {
            if (k != null && k.startsWith("f_") && v != null && !v.isBlank()) {
                headerFilters.put(k.substring(2), v);
            }
        });
        return Result.success(saleOrderService.pageQuery(p[0], p[1], keyword, status, scopeCtx, sortBy, sortOrder, drill, customSearch, headerFilters));
    }

    @GetMapping("/orders/{id}")
    public Result<SaleOrder> detail(@PathVariable Long id) {
        return Result.success(saleOrderService.getDetail(id));
    }

    @PostMapping("/orders")
    public Result<SaleOrder> create(@RequestBody SaleOrder order) {
        // 保存 = 草稿：不做必填/业务校验（严格校验在提交环节）；与前端宽松保存口径一致
        saleOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success(order);
    }

    @PutMapping("/orders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleOrder order) {
        order.setOrderId(id);
        saleOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/orders/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        saleOrderService.submitForApproval(id);
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/orders/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已生效 → 已关闭 */
    @PostMapping("/orders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        saleOrderService.close(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleOrderService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 下推到销售出库单: 已生效的订单 → 生成出库单(草稿) */
    @com.erp.common.bill.link.PushGuard(sourceType = "SALE_ORDER", targetType = "SALE_DELIVERY")
    @PostMapping("/orders/{id}/push-to-delivery")
    public Result<SaleDelivery> pushToDelivery(@PathVariable Long id, HttpServletRequest req) {
        return Result.success(saleOrderService.pushToDelivery(id, (String) req.getAttribute("currentUsername")));
    }

    /**
     * 下推到采购订单: 已生效的销售订单 → 生成采购订单(草稿)
     * 用于销售订单中的物料需要采购的场景（如贸易型、原料采购）
     * 通过sourceBillType/sourceBillNo/sourceId/sourceItemId 建立与销售订单的关联
     */
    @com.erp.common.bill.link.PushGuard(sourceType = "SALE_ORDER", targetType = "PURCHASE_ORDER")
    @PostMapping("/orders/{id}/push-to-purchase")
    public Result<PurchaseOrder> pushToPurchase(@PathVariable Long id, HttpServletRequest req) {
        return Result.success(saleOrderService.pushToPurchase(id, (String) req.getAttribute("currentUsername")));
    }

    /** 结案: 已生效 → 已结案 */
    @PostMapping("/orders/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: 已结案 → 已生效 */
    @PostMapping("/orders/{id}/reopen")
    public Result<Void> reopenOrder(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 驳回: 待审核 → 草稿 */
    @PostMapping("/orders/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        saleOrderService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿（需下游未生效） */
    @PostMapping("/orders/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废（双终态2，需下游未生效） */
    @PostMapping("/orders/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        saleOrderService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废（恢复到作废前状态） */
    @PostMapping("/orders/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        saleOrderService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿/驳回/已取消态允许） */
    @DeleteMapping("/orders/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleOrderService.deleteBill(id);
        return Result.success();
    }

    /** 客户下拉（2026-10-09 权限收口：与客户档案同口径 —— 数据范围 + 客户白名单 + 仅正式客户） */
    @GetMapping("/customers")
    public Result<List<Customer>> customers(@RequestParam(required = false) String keyword,
                                            HttpServletRequest request) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Customer>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("customer_name", keyword).or().like("customer_code", keyword));
        }
        dataScopeResolver.applyVisibleCustomers(wrapper, request);
        wrapper.orderByAsc("customer_code");
        wrapper.last("LIMIT 50");
        return Result.success(customerService.list(wrapper));
    }

    /** 物料下拉（2026-10-09 权限收口：与物料档案同口径 —— 数据范围 + 货品类别白名单） */
    @GetMapping("/materials")
    public Result<List<Material>> materials(@RequestParam(required = false) String keyword,
                                            HttpServletRequest request) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Material>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("material_name", keyword).or().like("material_code", keyword));
        }
        dataScopeResolver.applyVisibleMaterials(wrapper, request);
        wrapper.orderByAsc("material_code");
        wrapper.last("LIMIT 50");
        return Result.success(materialService.list(wrapper));
    }

    /** 仪表盘统计（与列表同数据权限口径：仅本人 = 创建人或本人的销售员单据） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<SaleOrder> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "saler_id");
        long totalOrders = saleOrderService.count(totalW);
        QueryWrapper<SaleOrder> draftW = new QueryWrapper<SaleOrder>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "saler_id");
        long draftOrders = saleOrderService.count(draftW);
        QueryWrapper<SaleOrder> approvedW = new QueryWrapper<SaleOrder>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "saler_id");
        long approvedOrders = saleOrderService.count(approvedW);
        QueryWrapper<SaleOrder> closedW = new QueryWrapper<SaleOrder>().eq("status", "已关闭");
        scopeCtx.applyTo(closedW, "saler_id");
        long closedOrders = saleOrderService.count(closedW);
        QueryWrapper<SaleOrder> cancelledW = new QueryWrapper<SaleOrder>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, "saler_id");
        long cancelledOrders = saleOrderService.count(cancelledW);
        // 待审核（卡片「待审核」用）
        QueryWrapper<SaleOrder> pendingW = new QueryWrapper<SaleOrder>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "saler_id");
        long pendingOrders = saleOrderService.count(pendingW);
        // 本月（按单据日期 order_date 落在当月，卡片「本月订单」用）
        QueryWrapper<SaleOrder> monthW = new QueryWrapper<>();
        ListQuerySupport.applyDateRange(monthW, "order_date",
                LocalDate.now().withDayOfMonth(1).toString(),
                LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "saler_id");
        long monthOrders = saleOrderService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", totalOrders);
        data.put("draftOrders", draftOrders);
        data.put("approvedOrders", approvedOrders);
        data.put("closedOrders", closedOrders);
        data.put("cancelledOrders", cancelledOrders);
        data.put("pendingOrders", pendingOrders);
        data.put("monthOrders", monthOrders);
        return Result.success(data);
    }
}
