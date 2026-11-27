package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;

import java.util.List;

public interface ProductionReceiptService extends IService<ProductionReceipt> {

    void saveReceiptWithItems(ProductionReceipt receipt, List<ProductionReceiptItem> items);

    /** 分页查询（统一入参：startDate/endDate 为入库日期区间、employeeId 为经手人）；数据权限按角色 data_scope（统一数据权限） */
    PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status,
                                            String startDate, String endDate, String employeeId,
                                            String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status,
                                                    String startDate, String endDate, String employeeId,
                                                    String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上再叠加「自定义字段搜索」（2026-11-24 列表页按自定义字段筛选）。
     * customSearch = JSON {fieldKey: 值}，由 CustomFieldSearchHelper 转 EXISTS 子查询；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<ProductionReceipt> pageQuery(int current, int size, String keyword, String status,
                                                    String startDate, String endDate, String employeeId,
                                                    String sortBy, String sortOrder, String customSearch,
                                                    java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder);
    }

    ProductionReceipt getDetail(Long id);

    /** 审核: 草稿 → 已生效 */
    void approve(Long id, String approver);

    /** 关闭: 已生效 → 已关闭 */
    void close(Long id, String closer, String reason);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateReceiptNo();

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /** 删除（仅草稿族 + 无已生效下游；整单明细级联删除，统一状态机口径） */
    void deleteBill(Long id);
}
