package com.erp.dashboard.service;

import com.erp.dashboard.dto.*;

import java.util.List;

public interface DashboardService {
    OverviewDTO getOverview();
    TrendDTO getSaleTrend(Integer days);
    TrendDTO getPurchaseTrend(Integer days);
    List<TopMaterialDTO> getTopSaleMaterials(Integer limit);
    List<TopMaterialDTO> getTopPurchaseMaterials(Integer limit);
    WarningCountDTO getInventoryWarningCount();
    List<TodoItemDTO> getTodoList();
    List<EmployeeRankingDTO> getEmployeeRanking(String month);

    // ========== 个人工作台 ==========

    /**
     * 个人工作台顶部统计
     * @param username 当前登录用户名
     * @param employeeId 当前员工ID
     * @param roles 当前用户角色编码列表
     */
    MyOverviewDTO getMyOverview(String username, Long employeeId, List<String> roles);

    /**
     * 待我审批的单据列表
     * - 管理员/经理：所有 approval_status='待审核' 单据
     * - 销售员：销售单据待审核
     * - 采购员：采购单据待审核
     * - 库管：库存单据待审核
     * - 财务：财务单据待审核
     */
    List<MyTodoDTO> getMyPendingApprovals(String username, Long employeeId, List<String> roles);

    /**
     * 仅「审批引擎真实指派给我的待办」（不含模块维度的待审核单据）。
     * 供系统通知定时任务使用，保证铃铛「待审批」与「我的待办」页面同源。
     */
    List<MyTodoDTO> getMyEnginePendingApprovals(String username);

    /**
     * 即将超期 / 已超期单据列表
     * - 计算各单据的 due_date - today
     * - 超期等级：OVERDUE(已超期)/URGENT(0~3天)/SOON(4~7天)
     */
    List<MyTodoDTO> getMyOverdueTasks(String username, Long employeeId, List<String> roles);

    /**
     * 我创建的草稿单据列表（提醒我继续完善）
     */
    List<MyTodoDTO> getMyDrafts(String username, Long employeeId, List<String> roles);

    /**
     * 应收应付跟进列表（已生效未结清且过到期日）
     */
    List<MyTodoDTO> getMyArApFollowUp(String username, Long employeeId, List<String> roles);

    /**
     * 小铃铛「超期/即将超期」通知专用：在工作台口径之上再收敛为"只与我有关"（created_by=当前账号）。
     * 通知是个人提醒，不允许把"管理员可见的全公司单据"推成别人的红点。
     */
    List<MyTodoDTO> getMyOverdueTasksSelfOnly(String username, Long employeeId, List<String> roles);

    /**
     * 小铃铛「应收应付」通知专用：同 {@link #getMyOverdueTasksSelfOnly}，只保留"与我有关"的单据。
     */
    List<MyTodoDTO> getMyArApFollowUpSelfOnly(String username, Long employeeId, List<String> roles);
}
