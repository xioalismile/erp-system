package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;

import java.util.List;

public interface MaterialRequisitionService extends IService<MaterialRequisition> {

    void saveRequisitionWithItems(MaterialRequisition requisition, List<MaterialRequisitionItem> items);

    PageResult<MaterialRequisition> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String employeeId, String sortBy, String sortOrder, String customSearch);

    /** 按单据类型(领料/退料/补料)分页查询 */
    PageResult<MaterialRequisition> pageQueryByBillType(int current, int size, String keyword, String status, String billType, String startDate, String endDate, String employeeId, String sortBy, String sortOrder, String customSearch);

    /**
     * 在既有条件之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<MaterialRequisition> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String employeeId, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch);
    }

    /** 按单据类型分页查询 + 表头字段过滤（2026-10-14；null / 空 headerFilters = 行为与上一重载完全一致） */
    default PageResult<MaterialRequisition> pageQueryByBillType(int current, int size, String keyword, String status, String billType, String startDate, String endDate, String employeeId, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQueryByBillType(current, size, keyword, status, billType, startDate, endDate, employeeId, sortBy, sortOrder, customSearch);
    }

    MaterialRequisition getDetail(Long id);

    /** 提交审核: 草稿 → 待审核 */
    void submitForApproval(Long id);

    /** 审核: 待审核 → 已生效 (扣减库存) */
    void approve(Long id, String approver);

    /** 重置草稿: 已生效 → 草稿 (恢复库存, 清空审核人/审核时间) */
    void reverseApprove(Long id, String operator);

    /** 关闭: 已生效 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 反结案: 已关闭 → 已生效 (close_status 归 0) */
    void reopenOrder(Long id, String operator);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateRequisitionNo();

    /** 按单据类型生成单号 (领料/退料/补料) */
    String generateRequisitionNo(String billType);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 删除 */
    void deleteBill(Long id);
}
