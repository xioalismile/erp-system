package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.entity.StockCheck;
import com.erp.inventory.entity.StockCheckItem;

import java.util.List;

public interface StockCheckService extends IService<StockCheck> {

    void saveCheckWithItems(StockCheck check, List<StockCheckItem> items);

    PageResult<StockCheck> pageQuery(int current, int size, String keyword, String status);

    StockCheck getDetail(Long id);

    /** 审核: 草稿 → 已审核 (盘盈入库 + 盘亏出库) */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateCheckNo();
}
