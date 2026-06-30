package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.entity.StockTransferItem;

import java.util.List;

public interface StockTransferService extends IService<StockTransfer> {

    void saveTransferWithItems(StockTransfer transfer, List<StockTransferItem> items);

    PageResult<StockTransfer> pageQuery(int current, int size, String keyword, String status);

    StockTransfer getDetail(Long id);

    /** 审核: 草稿 → 已审核 */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateTransferNo();
}
