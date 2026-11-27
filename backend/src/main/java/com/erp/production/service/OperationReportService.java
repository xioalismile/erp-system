package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;

public interface OperationReportService extends IService<OperationReport> {

    /** 保存工序汇报单: 新增或编辑 */
    void saveReport(OperationReport report);

    /** 分页查询（统一入参：startDate/endDate 为报工日期区间、employeeId 为报工员工）；数据权限按角色 data_scope（统一数据权限） */
    PageResult<OperationReport> pageQuery(int current, int size, String keyword, String status,
                                          String startDate, String endDate, String employeeId,
                                          String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<OperationReport> pageQuery(int current, int size, String keyword, String status,
                                                  String startDate, String endDate, String employeeId,
                                                  String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上再叠加「自定义字段搜索」（2026-11-24 列表页按自定义字段筛选）。
     * customSearch = JSON {fieldKey: 值}，由 CustomFieldSearchHelper 转 EXISTS 子查询；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<OperationReport> pageQuery(int current, int size, String keyword, String status,
                                                  String startDate, String endDate, String employeeId,
                                                  String sortBy, String sortOrder, String customSearch,
                                                  java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder);
    }

    /** 详情 */
    OperationReport getDetail(Long id);

    /** 提交审核: 草稿 → 待审核 */
    void submit(Long id, String operator);

    /** 审核: 待审核 → 已生效 */
    void approve(Long id, String approver);

    /** 重置草稿: 已生效 → 草稿 (扣减已回写的报工合格量, 反向更新工单工序状态) */
    void reverseApprove(Long id, String operator);

    /** 生成汇报单号: PRP-yyyyMMdd-0001 */
    String generateReportNo();

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效 → close_status=1 (status 保持已生效, 前端按 closeStatus 渲染已结案) */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /** 删除（仅草稿族 + 无已生效下游；整单明细级联删除，统一状态机口径） */
    void deleteBill(Long id);
}
