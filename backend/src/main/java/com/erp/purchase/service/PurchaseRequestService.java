package com.erp.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseRequest;
import com.erp.purchase.entity.PurchaseRequestItem;

import java.util.List;

/**
 * 采购申请单服务接口
 * 文档3.1 状态流转: 草稿 → 待审批 → 已审批 → 已关闭
 */
public interface PurchaseRequestService extends IService<PurchaseRequest> {

    void saveRequestWithItems(PurchaseRequest request, List<PurchaseRequestItem> items);

    /**
     * 列表分页查询（列表页查询统一约定，见 docs/knowledge-base.md 6.1）
     * 日期区间 startDate/endDate → request_date；员工 employeeId → applicant_id（申请人）；
     * 供应商维度不适用（purchase_request 主表无 supplier_id 列，仅供参考的建议供应商在明细行上）
     */
    PageResult<PurchaseRequest> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<PurchaseRequest> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch);
    }

    PurchaseRequest getDetail(Long id);

    /** 提交审批: 草稿 → 待审批 */
    void submit(Long id, String submitter);

    /** 审批: 待审批 → 已审批 */
    void approve(Long id, String approver);

    /** 重置草稿: 已审批 → 待审批 (已关闭不可重置草稿, 已被采购订单引用不可重置草稿) */
    void reverseApprove(Long id, String operator);

    /** 关闭: 已审批 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateRequestNo();

    /** 下推到采购订单: 已生效的申请单 → 生成采购订单(草稿) */
    PurchaseOrder pushToOrder(Long id, String operator);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已审批 → close_status=1（status 保持已审批） */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /** 删除 */
    void deleteBill(Long id);
}
