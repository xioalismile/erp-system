package com.erp.finance.prepayment.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.prepayment.sale.entity.SalePreReceipt;
import com.erp.finance.prepayment.sale.entity.SalePreReceiptItem;

import java.math.BigDecimal;
import java.util.List;

public interface SalePreReceiptService extends IService<SalePreReceipt> {

    /**
     * 分页查询预收款单
     * @param startDate  单据日期(receipt_date)起始，含端点，可空
     * @param endDate    单据日期(receipt_date)截止，含端点，可空
     * @param customerId 客户维度（映射 sale_pre_receipt.customer_id 列），可空
     * @param employeeId 员工维度（映射 sale_pre_receipt.cashier_id 列，业务口径「收款人」），可空
     * @param scopeCtx   数据权限过滤（sale_pre_receipt 表使用 create_by 列存储创建人），为 null 时不过滤
     */
    PageResult<SalePreReceipt> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SalePreReceipt> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, customerId, employeeId, scopeCtx, sortBy, sortOrder);
    }

    SalePreReceipt getDetail(Long id);

    /**
     * 销售订单审核通过后，按订单定金金额自动生成草稿销售预收款单（幂等）。
     * @return 生成的单据；未生成（无定金/已存在/关闭自动编号）返回 null
     */
    SalePreReceipt createFromSaleOrder(com.erp.sale.entity.SaleOrder order);

    /**
     * 销售订单反审联动：删除该订单定金自动生成的草稿预收单（连同收款明细）。
     * 若存在已生效（非草稿）的自动预收单则抛出 BusinessException，阻止订单反审。
     * @return 删除的草稿预收单数量
     */
    int rollbackAutoByOrder(Long orderId);

    SalePreReceipt savePreReceipt(SalePreReceipt preReceipt);

    /** 提交: 草稿 → 待审核(需审批) / 已生效(免审批直接生效) */
    void submit(Long id);

    void audit(Long id);

    void voidReceipt(Long id);

    void writeOff(Long id, List<SalePreReceiptItem> items);

    /** 反核销：回减已用金额（重置草稿/作废收款单时对称回减） */
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
