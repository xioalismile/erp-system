package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleInvoice;

import java.util.List;

public interface SaleDeliveryService extends IService<SaleDelivery> {

    void saveDeliveryWithItems(SaleDelivery delivery, List<SaleDeliveryItem> items);

    /**
     * 列表分页查询（列表页查询统一约定，见 docs/knowledge-base.md 6.1）
     * 日期区间 startDate/endDate → delivery_date；客户 customerId → customer_id；员工 employeeId → saler_id（销售员）
     */
    PageResult<SaleDelivery> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch);

    /**
     * 在标准过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响，default 委托旧签名保住其它调用方）。
     */
    default PageResult<SaleDelivery> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder, customSearch);
    }

    SaleDelivery getDetail(Long id);

    /** 提交审核: 草稿 → 待审核 */
    void submitForApproval(Long id);

    /** 审核: 待审核 → 已生效 (扣减库存+回写销售订单已出库数量) */
    void approve(Long id, String approver);

    /**
     * 提交/审核前的只读超量预检（不改变单据状态）：
     * 返回 policy / exceeded / message / issues，前端据此在提交前弹出「取消 / 继续提交」。
     */
    java.util.Map<String, Object> checkOverShipment(Long id);

    /** 重置草稿: 已生效 → 草稿 (回冲库存+减少已出库数量) */
    void reverseApprove(Long id, String operator);

    /** 取消: 草稿 → 已取消 */
    void cancel(Long id, String reason);

    String generateDeliveryNo();

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效 → close_status=1（status 保持已生效） */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /** 删除 */
    void deleteBill(Long id);

    /** 下推到销售发票: 已生效的出库单 → 生成发票(草稿) */
    SaleInvoice pushToInvoice(Long id, String operator);
}
