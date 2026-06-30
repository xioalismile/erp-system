package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.sale.entity.SaleOrder;

public interface SaleOrderService extends IService<SaleOrder> {

    void saveOrderWithItems(SaleOrder order, java.util.List<com.erp.sale.entity.SaleOrderItem> items);

    PageResult<SaleOrder> pageQuery(int current, int size, String customerName, String status, String orderNo);

    SaleOrder getDetail(Long id);

    /** 提交审核: 草稿 → 已确认 */
    void submitAudit(Long id, String auditor);

    /** 发货: 已确认 → 已发货 (扣减库存) */
    void shipOrder(Long id, String auditor, String logisticsNo, String deliveryMethod);

    /** 开票: 已发货 → 已开票 */
    void invoiceOrder(Long id, String invoiceNo);

    /** 完成: 已开票 → 已完成 */
    void completeOrder(Long id);

    /** 取消: 草稿/已确认 → 已取消 */
    void cancelOrder(Long id, String reason);

    /** 退货: 已发货/已完成 → 已退货 (库存回退) */
    void returnOrder(Long id, String reason);

    /** 复制订单: 生成新草稿 */
    SaleOrder copyOrder(Long sourceId);

    /** 收款登记 */
    void recordPayment(Long id, java.math.BigDecimal amount);

    String generateOrderNo();
}
