package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.service.SaleDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sale/deliveries")
@RequiredArgsConstructor
public class SaleDeliveryController {

    private final SaleDeliveryService saleDeliveryService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<SaleDelivery>> page(
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
        return Result.success(saleDeliveryService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 仪表盘统计（与列表同数据权限口径：按 saler_id 过滤）—— 列表页顶部统计卡片数据源 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery> draftW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "saler_id");
        long draftDeliveries = saleDeliveryService.count(draftW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery> pendingW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "saler_id");
        long pendingDeliveries = saleDeliveryService.count(pendingW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery> approvedW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "saler_id");
        long approvedDeliveries = saleDeliveryService.count(approvedW);
        // 「本月出库」卡：按单据日期 delivery_date 落在当月 1 号~当月最后一天（与列表点卡片后的区间筛选同口径）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery> monthW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "delivery_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "saler_id");
        long monthDeliveries = saleDeliveryService.count(monthW);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthDeliveries", monthDeliveries);
        data.put("draftDeliveries", draftDeliveries);
        data.put("pendingDeliveries", pendingDeliveries);
        data.put("approvedDeliveries", approvedDeliveries);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<SaleDelivery> detail(@PathVariable Long id) {
        return Result.success(saleDeliveryService.getDetail(id));
    }

    /**
     * 仓库直接替代（replace_plan_id 为空、replaced_from 非空）需要独立权限；
     * 销售指示执行行（replace_plan_id 非空）随出库单编辑权限。
     */
    private void requireWarehouseReplacePermission(SaleDelivery delivery, HttpServletRequest req) {
        if (delivery == null || delivery.getItems() == null) return;
        boolean warehouseReplace = false;
        for (SaleDeliveryItem it : delivery.getItems()) {
            if (it != null && it.getReplacedFromMaterialId() != null && it.getReplacePlanId() == null) {
                warehouseReplace = true;
                break;
            }
        }
        if (warehouseReplace && !SecurityUtils.hasPermission(req, "sale:deliveries:replace")) {
            throw new com.erp.config.BusinessException("无替代出库权限（sale:deliveries:replace）");
        }
    }

    @PostMapping
    public Result<SaleDelivery> create(@RequestBody SaleDelivery delivery, HttpServletRequest request) {
        // 保存 = 草稿：不做必填/业务校验（严格校验在提交环节）；与前端宽松保存口径一致
        requireWarehouseReplacePermission(delivery, request);
        saleDeliveryService.saveDeliveryWithItems(delivery, delivery.getItems());
        return Result.success(delivery);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleDelivery delivery, HttpServletRequest request) {
        delivery.setDeliveryId(id);
        requireWarehouseReplacePermission(delivery, request);
        saleDeliveryService.saveDeliveryWithItems(delivery, delivery.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        saleDeliveryService.submitForApproval(id);
        return Result.success();
    }

    /** 提交/审核前只读超量预检（不改变单据状态） */
    @GetMapping("/{id}/over-qty-check")
    public Result<Map<String, Object>> overQtyCheck(@PathVariable Long id) {
        return Result.success(saleDeliveryService.checkOverShipment(id));
    }

    /** 审核: 待审核 → 已生效 (扣减库存+回写销售订单已出库数量) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        saleDeliveryService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 (回冲库存+减少已出库数量) */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        saleDeliveryService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleDeliveryService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 下推到销售发票: 已生效的出库单 → 生成发票(草稿) */
    @com.erp.common.bill.link.PushGuard(sourceType = "SALE_DELIVERY", targetType = "SALE_INVOICE")
    @PostMapping("/{id}/push-to-invoice")
    public Result<SaleInvoice> pushToInvoice(@PathVariable Long id) {
        return Result.success(saleDeliveryService.pushToInvoice(id, null));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        saleDeliveryService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        saleDeliveryService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        saleDeliveryService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        saleDeliveryService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        saleDeliveryService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleDeliveryService.deleteBill(id);
        return Result.success();
    }

}
