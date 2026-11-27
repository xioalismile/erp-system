package com.erp.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;

import java.util.List;
import java.util.Map;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    void saveOrderWithItems(PurchaseOrder order, List<PurchaseOrderItem> items);

    /**
     * 列表分页查询（列表页查询统一约定，见 docs/knowledge-base.md 6.1）
     * 日期区间 startDate/endDate → order_date；供应商 supplierId → supplier_id；员工 employeeId → buyer_id
     */
    PageResult<PurchaseOrder> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<PurchaseOrder> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder, customSearch);
    }

    PurchaseOrder getDetail(Long id);

    /** 提交审核: 草稿 → 待审核 */
    void submit(Long id, String submitter);

    /** 审核: 草稿/待审核 → 已生效 */
    void approve(Long id, String approver);

    /** 重置草稿: 已生效 → 草稿 (已关闭不可重置草稿, 已被入库单/发票引用不可重置草稿) */
    void reverseApprove(Long id, String operator);

    /** 关闭: 已生效 → 已关闭 (手动强制关闭) */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    /** 自动关闭判定: 所有行均已执行完毕+开票完毕时, 自动关闭订单 */
    boolean tryAutoClose(Long id);

    /** 回写已入库数量(由入库单审核触发) */
    void increaseReceivedQuantity(Long orderId, Long itemId, java.math.BigDecimal qty);

    /** 回写已开票数量(由发票审核触发) */
    void increaseInvoicedQuantity(Long orderId, Long itemId, java.math.BigDecimal qty);

    /** 更新订单明细勾稽数量（入库/退货回写） */
    void updateTurnedQuantity(Long orderId, Long orderItemId, java.math.BigDecimal quantity, String turnType);

    /** 结案: 已生效 → 已结案 */
    void closeOrder(Long id, String closer);

    /** 反结案: 已结案 → 已生效 */
    void reopenOrder(Long id, String closer);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废（双终态2，需下游未生效） */
    void voidBill(Long id, String operator, String reason);

    /** 反作废（恢复到作废前状态） */
    void unvoidBill(Long id, String operator);

    /** 删除（仅草稿态允许） */
    void deleteBill(Long id);

    /** 查询订单行执行状态(执行完毕/开票完毕) */
    List<Map<String, Object>> queryItemExecutionStatus(Long orderId);

    /** 下推到采购入库单: 已生效的订单 → 生成入库单(草稿) */
    PurchaseInbound pushToInbound(Long id, String operator);

    String generateOrderNo();
}
