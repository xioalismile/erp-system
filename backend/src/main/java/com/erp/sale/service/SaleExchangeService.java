package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.sale.entity.SaleExchange;
import com.erp.sale.entity.SaleExchangeItem;
import com.erp.sale.entity.SaleReturnItem;

import java.util.List;
import java.util.Map;

/**
 * 销售换货单：用替换货品代替原货品发出，数据回写销售订单原货品行。
 */
public interface SaleExchangeService extends IService<SaleExchange> {

    PageResult<SaleExchange> pageQuery(int current, int size, String keyword, String status,
                                       String startDate, String endDate, String customerId, String employeeId,
                                       com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                       String sortBy, String sortOrder, String customSearch);

    SaleExchange getDetail(Long id);

    /** 保存草稿（含明细）；草稿/已驳回可改，其余状态拒绝 */
    SaleExchange saveExchangeWithItems(SaleExchange bill, List<SaleExchangeItem> items);

    /** 销售订单「销售替代出库」弹窗数据：台账七个数 + 路线提示 + 可关联退货额度 */
    List<Map<String, Object>> getReplaceableLines(Long orderId);

    /**
     * 由销售订单按钮生成换货单草稿。
     * lines 每项：orderItemId、quantity、substituteMaterialId、unitPrice、sourceDeliveryId?、linkedReturnItemId?
     */
    SaleExchange createFromOrder(Long orderId, List<Map<String, Object>> lines, String operator);

    String generateExchangeNo();

    void submitForApproval(Long id);

    void approve(Long id, String approver);

    void reject(Long id, String rejector, String reason);

    void reverseApprove(Long id, String operator);

    void voidBill(Long id, String operator, String reason);

    void deleteBill(Long id);

    /** 人工结案：放弃该订单行所有待退回（记录 waived 数量与操作人） */
    void waivePendingReturns(Long orderId, String operator, String reason);

    /** 退货审核冲抵：按退货来源区分退原货（冲 P）/ 退替换货（记 R_C） */
    void offsetReturn(Long orderId, List<SaleReturnItem> items, String operator);

    /** 订单是否存在生效的【普通】销售出库单（按钮显隐用，排除换货生成的出库单） */
    boolean hasEffectiveNormalDelivery(Long orderId);

    /** 该订单是否还有待退回（P > 0） */
    boolean hasPendingReturn(Long orderId);

    /** 该订单剩余可替换数量合计（所有行可替换额度之和；=0 表示替换发货已完成，入口应隐藏） */
    java.math.BigDecimal sumReplaceableQuantity(Long orderId);

    /** 指定订单行是否还有待退回（行级自动结案判定用） */
    boolean hasPendingReturnForItem(Long orderItemId);

    /** 订单明细展示用：按订单行汇总已生效换货信息（替换货品、数量、换货单号、待回库） */
    List<Map<String, Object>> listExchangeInfoByOrder(Long orderId);
}
