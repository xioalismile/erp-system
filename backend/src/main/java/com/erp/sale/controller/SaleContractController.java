package com.erp.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.util.SecurityUtils;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.service.SaleContractService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sale")
@RequiredArgsConstructor
public class SaleContractController {

    private final SaleContractService saleContractService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;
    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    @GetMapping("/contracts")
    public Result<PageResult<SaleContract>> page(
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
        return Result.success(saleContractService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 仪表盘统计（与列表同数据权限口径：按 saler_id 过滤）—— 列表页顶部统计卡片数据源 */
    @GetMapping("/contracts/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract> draftW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, "saler_id");
        long draftContracts = saleContractService.count(draftW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract> pendingW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract>().eq("status", "待审核");
        scopeCtx.applyTo(pendingW, "saler_id");
        long pendingContracts = saleContractService.count(pendingW);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract> approvedW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract>().eq("status", "已生效");
        scopeCtx.applyTo(approvedW, "saler_id");
        long approvedContracts = saleContractService.count(approvedW);
        // 「本月合同」卡：按单据日期 contract_date 落在当月 1 号~当月最后一天（与列表点卡片后的区间筛选同口径）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleContract> monthW = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "contract_date",
                java.time.LocalDate.now().withDayOfMonth(1).toString(),
                java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString());
        scopeCtx.applyTo(monthW, "saler_id");
        long monthContracts = saleContractService.count(monthW);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthContracts", monthContracts);
        data.put("draftContracts", draftContracts);
        data.put("pendingContracts", pendingContracts);
        data.put("approvedContracts", approvedContracts);
        return Result.success(data);
    }

    @GetMapping("/contracts/{id}")
    public Result<SaleContract> detail(@PathVariable Long id) {
        return Result.success(saleContractService.getDetail(id));
    }

    @PostMapping("/contracts")
    public Result<SaleContract> create(@RequestBody SaleContract contract) {
        saleContractService.saveContractWithItems(contract, contract.getItems());
        return Result.success(contract);
    }

    @PutMapping("/contracts/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SaleContract contract) {
        contract.setContractId(id);
        saleContractService.saveContractWithItems(contract, contract.getItems());
        return Result.success();
    }

    @DeleteMapping("/contracts/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("sale_contract", id);
        saleContractService.removeById(id);
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核（进入审批流程，提交人记录到审批任务） */
    @PostMapping("/contracts/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        saleContractService.submitForApproval(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/contracts/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        saleContractService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/contracts/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        saleContractService.cancel(id, body.get("reason"));
        return Result.success();
    }

    /**
     * 关闭合同: 已生效 → 已关闭
     * 简单实现: 直接更新状态字段与 close_status
     */
    @PostMapping("/contracts/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        SaleContract contract = saleContractService.getById(id);
        if (contract == null) {
            return Result.error("合同不存在");
        }
        contract.setStatus("已关闭");
        contract.setBusStatus(com.erp.common.bill.BusStatusEnum.CLOSED.getLabel());  // 双写：迁移期保持 status 不变，另写业务执行态
        contract.setCloseStatus(1);
        saleContractService.updateById(contract);
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/contracts/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        saleContractService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/contracts/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        saleContractService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/contracts/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body, HttpServletRequest req) {
        saleContractService.voidBill(id, (String) req.getAttribute("currentUsername"), body != null ? body.get("reason") : null);
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/contracts/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        saleContractService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/contracts/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        saleContractService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/contracts/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        saleContractService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
