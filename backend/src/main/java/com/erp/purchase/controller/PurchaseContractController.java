package com.erp.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.purchase.entity.PurchaseContract;
import com.erp.purchase.service.PurchaseContractService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/purchase/contract")
@RequiredArgsConstructor
public class PurchaseContractController {

    private final PurchaseContractService purchaseContractService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/{id}")
    public Result<PurchaseContract> detail(@PathVariable Long id) {
        return Result.success(purchaseContractService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseContract> create(@RequestBody PurchaseContract contract, HttpServletRequest req) {
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) contract.setCreatedBy(username);
        purchaseContractService.saveContractWithItems(contract, contract.getItems());
        return Result.success(contract);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PurchaseContract contract, HttpServletRequest req) {
        contract.setContractId(id);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) contract.setUpdatedBy(username);
        purchaseContractService.saveContractWithItems(contract, contract.getItems());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 【2026-09-14 修复】原实现直接 removeById：①绕过状态守卫（已生效合同也能被删）
        //   ②不清理 purchase_contract_item 明细（孤儿明细）。改走 deleteBill 统一口径。
        purchaseContractService.deleteBill(id);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult<PurchaseContract>> page(
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
        return Result.success(purchaseContractService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 仪表盘统计（与列表一致，按数据权限过滤）—— 列表页顶部统计卡片数据源 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<PurchaseContract> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "buyer_id");
        long totalContracts = purchaseContractService.count(totalW);
        QueryWrapper<PurchaseContract> draftW = new QueryWrapper<PurchaseContract>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "buyer_id");
        long draftContracts = purchaseContractService.count(draftW);
        QueryWrapper<PurchaseContract> pendingW = new QueryWrapper<PurchaseContract>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "buyer_id");
        long pendingContracts = purchaseContractService.count(pendingW);
        QueryWrapper<PurchaseContract> approvedW = new QueryWrapper<PurchaseContract>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "buyer_id");
        long approvedContracts = purchaseContractService.count(approvedW);
        QueryWrapper<PurchaseContract> cancelledW = new QueryWrapper<PurchaseContract>().eq("status", "已取消");
        scopeCtx.applyTo(cancelledW, "buyer_id");
        long cancelledContracts = purchaseContractService.count(cancelledW);
        // 卡片「本月合同」用：本月 contract_date 区间（与列表同数据权限口径）
        QueryWrapper<PurchaseContract> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "contract_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "buyer_id");
        long monthContracts = purchaseContractService.count(monthW);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("totalContracts", totalContracts);
        data.put("draftContracts", draftContracts);
        data.put("pendingContracts", pendingContracts);
        data.put("approvedContracts", approvedContracts);
        data.put("cancelledContracts", cancelledContracts);
        data.put("monthContracts", monthContracts);
        return Result.success(data);
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        purchaseContractService.submit(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 草稿/待审核 → 已生效 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        purchaseContractService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        purchaseContractService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /**
     * 重置草稿: 已生效 → 草稿
     * 简单实现: 直接更新状态字段，清除审核人和审核时间
     */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        PurchaseContract contract = purchaseContractService.getById(id);
        if (contract == null) {
            return Result.error("合同不存在");
        }
        contract.setStatus("草稿");
        contract.setApprovalStatus("草稿");
        contract.setApprovedBy(null);
        contract.setApprovedAt(null);
        String username = (String) req.getAttribute("currentUsername");
        if (username != null) contract.setUpdatedBy(username);
        purchaseContractService.updateById(contract);
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseContractService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        purchaseContractService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        purchaseContractService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        purchaseContractService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        purchaseContractService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
