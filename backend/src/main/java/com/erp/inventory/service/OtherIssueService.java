package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.entity.OtherIssueItem;

import java.util.List;

public interface OtherIssueService extends IService<OtherIssue> {

    void saveIssueWithItems(OtherIssue issue, List<OtherIssueItem> items);

    PageResult<OtherIssue> pageQuery(int current, int size, String keyword, String status);

    OtherIssue getDetail(Long id);

    /** 审核: 草稿 → 已审核 (扣减库存) */
    void approve(Long id, String approver);

    /** 关闭: 已审核 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateIssueNo();
}
