package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.service.SaleReturnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sale")
@RequiredArgsConstructor
public class SaleReturnController {

    private final SaleReturnService saleReturnService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/returns")
    public Result<PageResult<SaleReturn>> page(
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
        return Result.success(saleReturnService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 仪表盘统计（与列表同数据权限口径：按 saler_id 过滤）—— 列表页顶部统计卡片数据源 */
    @GetMapping("/returns/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn> draftW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "saler_id");
        long draftReturns = saleReturnService.count(draftW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn> pendingW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "saler_id");
        long pendingReturns = saleReturnService.count(pendingW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn> approvedW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "saler_id");
        long approvedReturns = saleReturnService.count(approvedW);
        // 「本月退货」卡：按单据日期 return_date 落在当月 1 号~当月最后一天（与列表点卡片后的区间筛选同口径）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn> monthW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "return_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "saler_id");
        long monthReturns = saleReturnService.count(monthW);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthReturns", monthReturns);
        data.put("draftReturns", draftReturns);
        data.put("pendingReturns", pendingReturns);
        data.put("approvedReturns", approvedReturns);
        return Result.success(data);
    }

    @GetMapping("/returns/{id}")
    public Result<SaleReturn> detail(@PathVariable Long id) {
        return Result.success(saleReturnService.getDetail(id));
    }

    @PostMapping("/returns")
    public Result<SaleReturn> create(@RequestBody SaleReturn saleReturn) {
        saleReturnService.saveReturnWithItems(saleReturn, saleReturn.getItems());
        return Result.success(saleReturn);
    }

    @PutMapping("/returns/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleReturn saleReturn) {
        saleReturn.setReturnId(id);
        saleReturnService.saveReturnWithItems(saleReturn, saleReturn.getItems());
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/returns/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        saleReturnService.submitForApproval(id);
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/returns/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        saleReturnService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/returns/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleReturnService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/returns/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        saleReturnService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/returns/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        saleReturnService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/returns/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        saleReturnService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/returns/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        saleReturnService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/returns/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        saleReturnService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/returns/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        saleReturnService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 删除（仅草稿态允许） */
    @DeleteMapping("/returns/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleReturnService.deleteBill(id);
        return Result.success();
    }

}
