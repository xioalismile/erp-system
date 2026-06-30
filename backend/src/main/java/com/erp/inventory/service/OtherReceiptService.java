package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.entity.OtherReceipt;
import com.erp.inventory.entity.OtherReceiptItem;

import java.util.List;

public interface OtherReceiptService extends IService<OtherReceipt> {

    void saveReceiptWithItems(OtherReceipt receipt, List<OtherReceiptItem> items);

    PageResult<OtherReceipt> pageQuery(int current, int size, String keyword, String status);

    OtherReceipt getDetail(Long id);

    /** 审核: 草稿 → 已审核 */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateReceiptNo();
}
