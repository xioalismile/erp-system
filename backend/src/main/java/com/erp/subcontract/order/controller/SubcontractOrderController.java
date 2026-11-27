package com.erp.subcontract.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.order.service.SubcontractOrderService;
import com.erp.subcontract.pick.entity.SubcontractPick;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subcontract/orders")
@RequiredArgsConstructor
public class SubcontractOrderController {

    private final SubcontractOrderService subcontractOrderService;

    /** 统一数据权限解析件（按角色 sys_role.data_scope 过滤，禁止各页面自拼） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /**
     * 分页查询（统一入参：current/page/size + keyword/status/startDate/endDate/supplierId）
     */
    @GetMapping
    public Result<PageResult<SubcontractOrder>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String supplierId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 自定义字段搜索（设置中心勾选「可搜索」的字段）：JSON {fieldKey: 值}
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(subcontractOrderService.pageQuery(p[0], p[1], keyword, status, sortBy, sortOrder,
                startDate, endDate, supplierId, customSearch, headerFilters));
    }

    /** 与 GET /api/subcontract/orders 同语义的旧路径别名（保留；入参口径与主路径完全一致） */
    @GetMapping("/page")
    public Result<PageResult<SubcontractOrder>> pageAlias(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String supplierId,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 自定义字段搜索（设置中心勾选「可搜索」的字段）：JSON {fieldKey: 值}
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：与主路径同一口径
            @RequestParam java.util.Map<String, String> allParams) {
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(subcontractOrderService.pageQuery(p[0], p[1], keyword, status, sortBy, sortOrder,
                startDate, endDate, supplierId, customSearch, headerFilters));
    }

    /**
     * 仪表盘统计（与列表同数据权限口径：统一数据权限按 create_by 过滤；「本月」= 当月1号 ~ 当月最后一天）
     */
    @GetMapping("/dashboard")
    public Result<java.util.Map<String, Object>> dashboard(HttpServletRequest request) {
        // 数据权限与列表逐字相同（统一数据权限：按角色 data_scope 过滤 create_by 列）
        QueryWrapper<SubcontractOrder> draftW = new QueryWrapper<SubcontractOrder>().eq("status", 0);
        dataScopeResolver.applyIfLimited(draftW, null, "create_by");
        long draftOrders = subcontractOrderService.count(draftW);
        QueryWrapper<SubcontractOrder> releasedW = new QueryWrapper<SubcontractOrder>().eq("status", 3);
        dataScopeResolver.applyIfLimited(releasedW, null, "create_by");
        long releasedOrders = subcontractOrderService.count(releasedW);
        QueryWrapper<SubcontractOrder> completedW = new QueryWrapper<SubcontractOrder>().eq("status", 5);
        dataScopeResolver.applyIfLimited(completedW, null, "create_by");
        long completedOrders = subcontractOrderService.count(completedW);
        // 本月委外单：order_date 是 date 列 → 统一件 applyDateRange（含端点）
        java.time.LocalDate monthStart = java.time.LocalDate.now().withDayOfMonth(1);
        java.time.LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        QueryWrapper<SubcontractOrder> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "order_date", monthStart.toString(), monthEnd.toString());
        dataScopeResolver.applyIfLimited(monthW, null, "create_by");
        long monthOrders = subcontractOrderService.count(monthW);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthOrders", monthOrders);
        data.put("draftOrders", draftOrders);
        data.put("releasedOrders", releasedOrders);
        data.put("completedOrders", completedOrders);
        return Result.success(data);
    }

    @GetMapping("/list")
    public Result<List<SubcontractOrder>> list() {
        return Result.success(subcontractOrderService.list(new QueryWrapper<SubcontractOrder>().orderByDesc("create_time")));
    }

    @GetMapping("/{id}")
    public Result<SubcontractOrder> detail(@PathVariable Long id) {
        return Result.success(subcontractOrderService.getDetail(id));
    }

    @PostMapping
    public Result<SubcontractOrder> create(@RequestBody SubcontractOrder order) {
        subcontractOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success(order);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SubcontractOrder order) {
        order.setId(id);
        subcontractOrderService.saveOrderWithItems(order, order.getItems());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 2026-09-20：删除收口到 Service —— ①仅草稿可删 ②下游已生效单据（发料/入库/补料/退料等）非空则拒绝
        // ③校验通过后同事务整单级联删除（先清明细/从表，再删主表）。与全站口径、前端按钮可见条件一致。
        subcontractOrderService.deleteOrder(id);
        return Result.success();
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        subcontractOrderService.submitForApproval(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam(required = false) String approver,
                                HttpServletRequest req) {
        String operator = (approver != null && !approver.isBlank())
                ? approver
                : (String) req.getAttribute("currentUsername");
        subcontractOrderService.approve(id, operator);
        return Result.success();
    }

    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        subcontractOrderService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        String closer = (String) req.getAttribute("currentUsername");
        subcontractOrderService.close(id, closer, reason);
        return Result.success();
    }

    @PostMapping("/{id}/unclose")
    public Result<Void> unclose(@PathVariable Long id, HttpServletRequest req) {
        subcontractOrderService.unclose(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案(与 /unclose 同语义): close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        subcontractOrderService.unclose(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        subcontractOrderService.cancel(id, reason);
        return Result.success();
    }

    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        subcontractOrderService.release(id);
        return Result.success();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        subcontractOrderService.complete(id);
        return Result.success();
    }

    @com.erp.common.bill.link.PushGuard(sourceType = "SUBCONTRACT_ORDER", targetType = "SUBCONTRACT_DELIVERY")
    @PostMapping("/{id}/push-pick")
    public Result<SubcontractPick> pushPick(@PathVariable Long id) {
        return Result.success(subcontractOrderService.pushPick(id));
    }

    @com.erp.common.bill.link.PushGuard(sourceType = "SUBCONTRACT_ORDER", targetType = "SUBCONTRACT_RECEIPT")
    @PostMapping("/{id}/push-inbound")
    public Result<SubcontractInbound> pushInbound(@PathVariable Long id) {
        return Result.success(subcontractOrderService.pushInbound(id));
    }

    @GetMapping("/items/{itemId}/bom-materials")
    public Result<List<SubcontractOrderItem>> getBomMaterials(@PathVariable Long itemId) {
        return Result.success(subcontractOrderService.getBomMaterials(itemId));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        subcontractOrderService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        subcontractOrderService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        subcontractOrderService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
