package com.erp.subcontract.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.pick.entity.SubcontractPick;

import java.util.List;

public interface SubcontractOrderService extends IService<SubcontractOrder> {

    void saveOrderWithItems(SubcontractOrder order, List<SubcontractOrderItem> items);

    /**
     * 分页查询
     *
     * @param keyword    关键词(单号 order_no / 供应商名称 supplier_name)
     * @param startDate  委外日期起(统一入参)
     * @param endDate    委外日期止(统一入参)
     * @param supplierId 委外供应商(统一入参)
     */
    PageResult<SubcontractOrder> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                           String startDate, String endDate, String supplierId);

    /**
     * 在既有条件之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广 · 委外模块批次）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SubcontractOrder> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                   String startDate, String endDate, String supplierId,
                                                   java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, startDate, endDate, supplierId, null, headerFilters);
    }

    /**
     * 在既有查询之上再叠加「自定义字段搜索」（2026-11-24 列表页按自定义字段筛选）。
     * customSearch = JSON {fieldKey: 值}，由 CustomFieldSearchHelper 转 EXISTS 子查询；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SubcontractOrder> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                   String startDate, String endDate, String supplierId,
                                                   String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, startDate, endDate, supplierId);
    }

    SubcontractOrder getDetail(Long id);

    void submitForApproval(Long id);

    void approve(Long id, String approver);

    void reverseApprove(Long id, String operator);

    void close(Long id, String closer, String reason);

    void unclose(Long id, String operator);

    void cancel(Long id, String reason);

    void release(Long id);

    void complete(Long id);

    String generateOrderNo();

    List<SubcontractOrderItem> getBomMaterials(Long orderItemId);

    /** 下推生成委外发料单 */
    SubcontractPick pushPick(Long orderId);

    /** 下推生成委外入库单 */
    SubcontractInbound pushInbound(Long orderId);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /**
     * 整单删除（2026-09-20）：仅草稿可删；有下游已生效单据（发料/入库/补料/退料等）时拒绝。
     * 校验在任何删除动作之前完成，通过后同事务清理明细/从表再删主表。
     */
    void deleteOrder(Long id);
}
