package com.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.util.SecurityUtils;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.service.OperationExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 运营费用控制器
 * 路径前缀: /api/finance/operation-expense
 *
 * 提供 CRUD + submit + approve 接口
 * 状态流转: 草稿 → 待审核 → 已生效
 */
@Slf4j
@RestController
@RequestMapping("/api/finance/operation-expense")
@RequiredArgsConstructor
public class OperationExpenseController {

    private final OperationExpenseService operationExpenseService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<OperationExpense>> pageList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            HttpServletRequest request,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = ListQuerySupport.resolvePage(current, page, size);
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围（替代 getCreatedByFilter 硬编码）
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(operationExpenseService.pageList(p[0], p[1], keyword, expenseType, status,
                startDate, endDate, employeeId, scopeCtx, sortBy, sortOrder, headerFilters));
    }

    /**
     * 仪表盘统计（与列表逐字同数据权限口径：operation_expense 表无 created_by 列，创建人列用 applicant_name）
     * 返回：totalCount 费用单总数 / monthCount 本月费用 / draftCount 草稿 / pendingCount 待审核 / approvedCount 已生效
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        QueryWrapper<OperationExpense> totalW = new QueryWrapper<>();
        if (scopeCtx != null) scopeCtx.applyTo(totalW, null, "applicant_name");
        long totalCount = operationExpenseService.count(totalW);
        QueryWrapper<OperationExpense> draftW = new QueryWrapper<OperationExpense>().eq("status", "草稿");
        if (scopeCtx != null) scopeCtx.applyTo(draftW, null, "applicant_name");
        long draftCount = operationExpenseService.count(draftW);
        QueryWrapper<OperationExpense> pendingW = new QueryWrapper<OperationExpense>().eq("status", "待审核");
        if (scopeCtx != null) scopeCtx.applyTo(pendingW, null, "applicant_name");
        long pendingCount = operationExpenseService.count(pendingW);
        QueryWrapper<OperationExpense> approvedW = new QueryWrapper<OperationExpense>().eq("status", "已生效");
        if (scopeCtx != null) scopeCtx.applyTo(approvedW, null, "applicant_name");
        long approvedCount = operationExpenseService.count(approvedW);
        // 卡片「本月费用」用：当月 1 号 ~ 当月最后一天（按 expense_date），与列表同数据权限口径
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        QueryWrapper<OperationExpense> monthW = new QueryWrapper<>();
        ListQuerySupport.applyDateRange(monthW, "expense_date",
                firstDayOfMonth.toString(), firstDayOfMonth.plusMonths(1).minusDays(1).toString());
        if (scopeCtx != null) scopeCtx.applyTo(monthW, null, "applicant_name");
        long monthCount = operationExpenseService.count(monthW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", totalCount);
        data.put("monthCount", monthCount);
        data.put("draftCount", draftCount);
        data.put("pendingCount", pendingCount);
        data.put("approvedCount", approvedCount);
        return Result.success(data);
    }

    /** 查询详情 */
    @GetMapping("/{id}")
    public Result<OperationExpense> getById(@PathVariable Long id) {
        return Result.success(operationExpenseService.getById(id));
    }

    /** 新增 */
    @PostMapping
    public Result<OperationExpense> save(@RequestBody OperationExpense expense, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        if (expense.getApplicantName() == null) expense.setApplicantName(operator);
        return Result.success(operationExpenseService.saveEntity(expense));
    }

    /** 修改（仅草稿状态） */
    @PutMapping("/{id}")
    public Result<OperationExpense> update(@PathVariable Long id, @RequestBody OperationExpense expense,
                                            HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        expense.setExpenseId(id);
        expense.setApplicantName(operator);
        return Result.success(operationExpenseService.updateEntity(expense));
    }

    /** 删除（仅草稿状态） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        operationExpenseService.delete(id);
        return Result.success();
    }

    /** 提交审核（草稿 → 待审核） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        operationExpenseService.submit(id, operator);
        return Result.success();
    }

    /** 审核通过（待审核 → 已生效） */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        String approver = (String) req.getAttribute("currentUsername");
        operationExpenseService.approve(id, approver);
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        operationExpenseService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        operationExpenseService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        operationExpenseService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        operationExpenseService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        operationExpenseService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        operationExpenseService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
