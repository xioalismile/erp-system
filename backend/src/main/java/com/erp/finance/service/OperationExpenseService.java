package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.entity.OperationExpense;

/**
 * 运营费用服务
 * 来源: 2026-07-01 客户录音访谈需求
 *
 * 单号格式: OE-yyyyMMdd-0001
 * 状态流转: 草稿 → 待审核 → 已生效
 * 删除限制: 仅草稿族（草稿/已驳回/已取消）可删除
 */
public interface OperationExpenseService extends IService<OperationExpense> {

    /** 分页查询
     * @param startDate 费用日期区间起（operation_expense.expense_date，含端点，非法/为空忽略）
     * @param endDate   费用日期区间止（operation_expense.expense_date，含端点，非法/为空忽略）
     * @param sortBy    列头排序字段（走列名白名单校验，未传/未命中则用默认排序：最新在前）
     * @param sortOrder 排序方向 asc / desc
     * @param scopeCtx 数据权限上下文（2026-09-02 配置驱动）；
     *                 由于 operation_expense 表无 created_by 字段，使用 applicant_name 作为创建人
     */
    PageResult<OperationExpense> pageList(int current, int size, String keyword,
                                          String expenseType, String status, String startDate, String endDate,
                                          String employeeId,
                                          com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                          String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<OperationExpense> pageList(int current, int size, String keyword,
                                                  String expenseType, String status, String startDate, String endDate,
                                                  String employeeId,
                                                  com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                                  String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageList(current, size, keyword, expenseType, status, startDate, endDate, employeeId, scopeCtx, sortBy, sortOrder);
    }

    /** 查询详情 */
    OperationExpense getById(Long id);

    /** 新增（自动生成单号，默认草稿状态） */
    OperationExpense saveEntity(OperationExpense expense);

    /** 修改（仅草稿状态可修改） */
    OperationExpense updateEntity(OperationExpense expense);

    /** 删除（仅草稿族可删除） */
    boolean delete(Long id);

    /** 提交审核（草稿 → 待审核） */
    void submit(Long id, String operator);

    /** 审核通过（待审核 → 已生效） */
    void approve(Long id, String approver);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效 → close_status=1（status 保持已生效） */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);
}
