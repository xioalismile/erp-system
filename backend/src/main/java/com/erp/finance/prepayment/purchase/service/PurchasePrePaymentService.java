package com.erp.finance.prepayment.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePayment;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePaymentItem;

import java.math.BigDecimal;
import java.util.List;

public interface PurchasePrePaymentService extends IService<PurchasePrePayment> {

    /**
     * 分页查询预付款单
     * @param startDate  单据日期(payment_date)起始，含端点，可空
     * @param endDate    单据日期(payment_date)截止，含端点，可空
     * @param supplierId 供应商维度（映射 purchase_pre_payment.supplier_id 列），可空
     * @param employeeId 员工维度（映射 purchase_pre_payment.cashier_id 列，业务口径「付款人」），可空
     * @param scopeCtx   数据权限过滤（purchase_pre_payment 表使用 create_by 列存储创建人），为 null 时不过滤
     */
    PageResult<PurchasePrePayment> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<PurchasePrePayment> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, supplierId, employeeId, scopeCtx, sortBy, sortOrder);
    }

    PurchasePrePayment getDetail(Long id);

    /**
     * 采购订单审核通过后，按订单预付金额自动生成草稿采购预付款单（幂等）。
     * @return 生成的单据；未生成（无预付/已存在/关闭自动编号）返回 null
     */
    PurchasePrePayment createFromPurchaseOrder(com.erp.purchase.entity.PurchaseOrder order);

    /**
     * 采购订单反审联动：删除该订单预付自动生成的草稿预付款单（连同付款明细）。
     * 若存在已生效（非草稿）的自动预付款单则抛出 BusinessException，阻止订单反审。
     * @return 删除的草稿预付款单数量
     */
    int rollbackAutoByOrder(Long orderId);

    PurchasePrePayment savePrePayment(PurchasePrePayment prePayment);

    /** 提交: 草稿 → 待审核(需审批) / 已生效(免审批直接生效) */
    void submit(Long id);

    void audit(Long id);

    void voidPayment(Long id);

    void writeOff(Long id, List<PurchasePrePaymentItem> items);

    /** 反核销：回减已用金额（重置草稿/作废付款单时对称回减） */
    void reverseWriteOff(Long id, BigDecimal amount);

    boolean delete(Long id);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效 → close_status=1（status 保持已生效） */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);
}
