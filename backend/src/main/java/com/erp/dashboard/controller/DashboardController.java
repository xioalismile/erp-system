package com.erp.dashboard.controller;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.dashboard.dto.*;
import com.erp.dashboard.service.BillApprovalService;
import com.erp.dashboard.service.BillSummaryService;
import com.erp.dashboard.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final BillApprovalService billApprovalService;
    private final BillSummaryService billSummaryService;

    @GetMapping("/overview")
    public Result<OverviewDTO> getOverview() {
        return Result.success(dashboardService.getOverview());
    }

    @GetMapping("/sale-trend")
    public Result<TrendDTO> getSaleTrend(@RequestParam(defaultValue = "30") Integer days) {
        return Result.success(dashboardService.getSaleTrend(days));
    }

    @GetMapping("/purchase-trend")
    public Result<TrendDTO> getPurchaseTrend(@RequestParam(defaultValue = "30") Integer days) {
        return Result.success(dashboardService.getPurchaseTrend(days));
    }

    @GetMapping("/top-sale")
    public Result<List<TopMaterialDTO>> getTopSaleMaterials(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(dashboardService.getTopSaleMaterials(limit));
    }

    @GetMapping("/top-purchase")
    public Result<List<TopMaterialDTO>> getTopPurchaseMaterials(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(dashboardService.getTopPurchaseMaterials(limit));
    }

    @GetMapping("/warning-count")
    public Result<WarningCountDTO> getInventoryWarningCount() {
        return Result.success(dashboardService.getInventoryWarningCount());
    }

    @GetMapping("/todo-list")
    public Result<List<TodoItemDTO>> getTodoList() {
        return Result.success(dashboardService.getTodoList());
    }

    @GetMapping("/employee-ranking")
    public Result<List<EmployeeRankingDTO>> getEmployeeRanking(
            @RequestParam(required = false) String month) {
        return Result.success(dashboardService.getEmployeeRanking(month));
    }

    // ========== 个人工作台 ==========

    /** 个人工作台顶部统计 */
    @GetMapping("/my/overview")
    public Result<MyOverviewDTO> getMyOverview(HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        Long employeeId = SecurityUtils.getCurrentEmployeeId(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        return Result.success(dashboardService.getMyOverview(username, employeeId, roles));
    }

    /** 待我审批的单据 */
    @GetMapping("/my/pending-approvals")
    public Result<List<MyTodoDTO>> getMyPendingApprovals(HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        Long employeeId = SecurityUtils.getCurrentEmployeeId(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        return Result.success(dashboardService.getMyPendingApprovals(username, employeeId, roles));
    }

    /** 即将超期/已超期单据 */
    @GetMapping("/my/overdue")
    public Result<List<MyTodoDTO>> getMyOverdueTasks(HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        Long employeeId = SecurityUtils.getCurrentEmployeeId(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        return Result.success(dashboardService.getMyOverdueTasks(username, employeeId, roles));
    }

    /** 我创建的草稿 */
    @GetMapping("/my/drafts")
    public Result<List<MyTodoDTO>> getMyDrafts(HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        Long employeeId = SecurityUtils.getCurrentEmployeeId(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        return Result.success(dashboardService.getMyDrafts(username, employeeId, roles));
    }

    /** 应收应付跟进 */
    @GetMapping("/my/ar-ap-followup")
    public Result<List<MyTodoDTO>> getMyArApFollowUp(HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        Long employeeId = SecurityUtils.getCurrentEmployeeId(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        return Result.success(dashboardService.getMyArApFollowUp(username, employeeId, roles));
    }

    // ========== 工作台审批 ==========

    /** 单条审批
     * body: { "type": "SALE_ORDER", "id": 123 }
     */
    @PostMapping("/my/approve")
    public Result<ApproveResultDTO> approve(@RequestBody Map<String, Object> body,
                                            HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        String type = (String) body.get("type");
        Long id = body.get("id") == null ? null :
                (body.get("id") instanceof Number ? ((Number) body.get("id")).longValue()
                        : Long.valueOf(body.get("id").toString()));
        return Result.success(billApprovalService.approve(type, id, username));
    }

    /** 单条审批驳回（2026-11-19：与 /my/approve 对齐，供工作台卡片与居中弹窗直接驳回）
     * body: { "type": "SALE_ORDER", "id": 123, "comment": "驳回原因" }
     */
    @PostMapping("/my/reject")
    public Result<ApproveResultDTO> reject(@RequestBody Map<String, Object> body,
                                           HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        String type = (String) body.get("type");
        Long id = body.get("id") == null ? null :
                (body.get("id") instanceof Number ? ((Number) body.get("id")).longValue()
                        : Long.valueOf(body.get("id").toString()));
        String comment = body.get("comment") == null ? "" : body.get("comment").toString();
        return Result.success(billApprovalService.reject(type, id, username, comment));
    }

    /** 单据摘要（2026-11-19 弹窗明细）：按类型返回头信息+明细行，供全局弹窗展示「什么单据、什么内容」 */
    @GetMapping("/my/bill-summary")
    public Result<Map<String, Object>> billSummary(@RequestParam String type, @RequestParam Long id) {
        return Result.success(billSummaryService.getBillSummary(type, id));
    }

    /** 当前用户是否为该单据的审批人（2026-11-19）：审核/驳回按钮可见性依据；无审批任务=true */
    @GetMapping("/my/is-bill-approver")
    public Result<Boolean> isBillApprover(@RequestParam String type, @RequestParam Long id,
                                          HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        return Result.success(billApprovalService.isBillApprover(type, id, username));
    }

    /** 批量审批（一键审批）
     * body: { "items": [{"type":"SALE_ORDER","id":1}, ...] }
     */
    @PostMapping("/my/batch-approve")
    public Result<List<ApproveResultDTO>> batchApprove(@RequestBody Map<String, Object> body,
                                                       HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        Object itemsObj = body.get("items");
        if (itemsObj == null) {
            return Result.success(java.util.Collections.emptyList());
        }
        List<BillApprovalService.ApproveRequest> items = new java.util.ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object o : (List<?>) itemsObj) {
                if (o instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    BillApprovalService.ApproveRequest ar = new BillApprovalService.ApproveRequest();
                    ar.setType((String) m.get("type"));
                    Object idObj = m.get("id");
                    if (idObj != null) {
                        ar.setId(idObj instanceof Number ? ((Number) idObj).longValue()
                                : Long.valueOf(idObj.toString()));
                    }
                    items.add(ar);
                }
            }
        }
        return Result.success(billApprovalService.batchApprove(items, username));
    }
}
