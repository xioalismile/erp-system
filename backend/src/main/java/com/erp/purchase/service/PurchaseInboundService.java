package com.erp.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;

import java.util.List;

public interface PurchaseInboundService extends IService<PurchaseInbound> {

    void saveInboundWithItems(PurchaseInbound inbound, List<PurchaseInboundItem> items);

    PageResult<PurchaseInbound> pageQuery(int current, int size, String keyword, String status);

    PurchaseInbound getDetail(Long id);

    /** 审核: 草稿 → 已审核 */
    void approve(Long id, String approver);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateReceiptNo();
}
