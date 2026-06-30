package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;

import java.util.List;

public interface SaleOrderService extends IService<SaleOrder> {

    void saveOrderWithItems(SaleOrder order, List<SaleOrderItem> items);

    PageResult<SaleOrder> pageQuery(int current, int size, String keyword, String status);

    SaleOrder getDetail(Long id);

    /** 审核: 草稿 → 已审核 */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateOrderNo();
}
