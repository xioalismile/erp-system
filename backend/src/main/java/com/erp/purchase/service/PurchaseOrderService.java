package com.erp.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;

import java.util.List;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    void saveOrderWithItems(PurchaseOrder order, List<PurchaseOrderItem> items);

    PageResult<PurchaseOrder> pageQuery(int current, int size, String keyword, String status);

    PurchaseOrder getDetail(Long id);

    /** 审核: 草稿 → 已审核 */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateOrderNo();
}
