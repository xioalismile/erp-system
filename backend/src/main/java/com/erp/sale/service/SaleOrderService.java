package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;

import java.math.BigDecimal;
import java.util.List;

public interface SaleOrderService extends IService<SaleOrder> {

    /** 保存（草稿态，宽松校验：允许必填项为空、允许无明细） */
    void saveOrderWithItems(SaleOrder order, List<SaleOrderItem> items);

    PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch);

    /**
     * 带"数据看板穿透"过滤条件的查询（可选参数，缺省时与上一个方法行为完全一致）。
     * drill 支持的 key：startDate/endDate（单据日期）、customerId、customerLevel、materialId、productLineId（一级分类）。
     */
    default PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status,
                                            com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                            String sortBy, String sortOrder, java.util.Map<String, String> drill,
                                            String customSearch) {
        return pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder, customSearch);
    }

    /**
     * 在穿透过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 一期）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status,
                                            com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                            String sortBy, String sortOrder, java.util.Map<String, String> drill,
                                            String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder, drill, customSearch);
    }

    SaleOrder getDetail(Long id);

    /** 提交审核（严格校验：若未保存则一步到位保存+提交）*/
    void submitForApproval(Long id);

    /** 审核: 待审核 → 已生效 */
    void approve(Long id, String approver);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿（需下游未生效） */
    void reverseApprove(Long id, String operator);

    /** 关闭: 已生效 → 已关闭（兼容旧逻辑，实际内部走结案 close_status=1） */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    /** 结案: 已生效 → 已结案 (close_status=1) */
    void closeOrder(Long id, String closer);

    /** 反结案: 已结案 → 已生效 */
    void reopenOrder(Long id, String closer);

    /** 作废（双终态2，需下游未生效） */
    void voidBill(Long id, String operator, String reason);

    /** 反作废（恢复到作废前状态） */
    void unvoidBill(Long id, String operator);

    /** 删除（仅草稿态允许） */
    void deleteBill(Long id);

    /** 更新订单明细勾稽数量（出库回写） */
    void updateTurnedQuantity(Long orderId, Long orderItemId, BigDecimal quantity, String turnType);

    /** 累加订单明细「已开票金额」（发票生效 +net_amount / 重置草稿 -net_amount；与 turned_invoice_quantity 同路径），并触发完结重算 */
    void updateTurnedInvoiceAmount(Long orderId, Long orderItemId, BigDecimal amountDelta);

    /** 按当前「销售订单状态设置」整单重算完结状态（明细行逐行判 + 订单级汇总；手动结案订单不覆盖）。收款回写等无勾稽数量的触发点调用 */
    void recalcOrderCloseStatus(Long orderId);

    /** 订单是否已完结（close_status=1）——下推守卫共用判定；订单不存在时返回 false（由调用方各自处理"不存在"） */
    boolean isOrderClosed(Long orderId);

    String generateOrderNo();

    /** 系统自动建单专用编号：设置中心关闭自动编号时仍按编号规则取号，保证自动下推可用 */
    String generateOrderNoForSystem();

    /** 下推到销售出库单: 已生效的订单 → 生成出库单(草稿) */
    SaleDelivery pushToDelivery(Long id, String operator);

    /** 下推到采购订单: 已生效的订单 → 生成采购订单(草稿) */
    PurchaseOrder pushToPurchase(Long id, String operator);
}
