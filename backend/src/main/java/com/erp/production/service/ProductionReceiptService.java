package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;

import java.util.List;

public interface ProductionReceiptService extends IService<ProductionReceipt> {

    void saveReceiptWithItems(ProductionReceipt receipt, List<ProductionReceiptItem> items);

    PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status);

    ProductionReceipt getDetail(Long id);

    /** 审核: 草稿 → 已审核 */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateReceiptNo();
}
