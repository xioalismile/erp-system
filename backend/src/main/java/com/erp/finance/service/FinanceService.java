package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FinanceService extends IService<AccountsReceivable> {

    // ========== 应收账款 ==========

    /**
     * 销售订单确认时生成应收
     */
    AccountsReceivable createArFromSaleOrder(Long saleOrderId);

    /**
     * 销售出库审核时生成应收（sourceBillType="销售出库单"）
     */
    AccountsReceivable createArFromSaleDelivery(Long deliveryId);

    /**
     * 销售发票审核时生成应收（sourceBillType="销售发票"）
     */
    AccountsReceivable createArFromSaleInvoice(Long invoiceId);

    /**
     * 收款时更新应收
     */
    void receivePayment(Long arId, BigDecimal amount, Long settleModeId, String documentNo, String operator);

    /**
     * 按来源销售订单号查询收款记录（2026-10-12 销售订单列表「收款详情」弹窗）：
     * 含「直接订单收款」与「发货收款（沿应收单来源订单号关联）」两条路径的已冲销核销行。
     * 返回字段：receiptNo 收款单号 / writeoffDate 收款日期 / amount 金额 / path 收款路径 / itemName 货品（明细收款）/ arNo 应收单号
     */
    List<Map<String, Object>> listReceiptsBySourceOrder(String orderNo);

    /**
     * 应收账款列表
     * @param scopeCtx 数据权限上下文（2026-09-02 统一数据权限，null=不过滤）
     */
    PageResult<AccountsReceivable> pageAr(int current, int size, String customerName, String status, String arNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx);

    /**
     * 获取应收详情
     */
    AccountsReceivable getArDetail(Long id);

    /** 手动新增应收单 */
    AccountsReceivable saveAr(AccountsReceivable ar);

    /** 更新应收单 */
    void updateAr(AccountsReceivable ar);

    /** 删除应收单（仅草稿状态可删） */
    void deleteAr(Long id);

    /**
     * 按源单据号和源单类型查询应收账款
     * 用于红字发票冲减原蓝字发票的应收
     */
    AccountsReceivable getArBySourceBillNo(String sourceBillNo, String sourceBillType);

    // ========== 应付账款 ==========

    /**
     * 采购订单确认时生成应付
     */
    AccountsPayable createApFromPurchaseOrder(Long purchaseOrderId);

    /**
     * 付款时更新应付
     */
    void makePayment(Long apId, BigDecimal amount, Long settleModeId, String documentNo, String operator);

    /**
     * 应付账款列表
     * @param scopeCtx 数据权限上下文（2026-09-02 统一数据权限，null=不过滤）
     */
    PageResult<AccountsPayable> pageAp(int current, int size, String supplierName, String status, String apNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx);

    /**
     * 获取应付详情
     */
    AccountsPayable getApDetail(Long id);

    /** 手动新增应付单 */
    AccountsPayable saveAp(AccountsPayable ap);

    /** 更新应付单 */
    void updateAp(AccountsPayable ap);

    /** 删除应付单（仅草稿状态可删） */
    void deleteAp(Long id);

    // ========== 收款记录 ==========

    /**
     * 新增收款记录
     */
    PaymentReceipt createReceipt(PaymentReceipt receipt);

    /**
     * 业务单据审核后按「收款单生成方式」直接生成收款单草稿（系统无应收单，设计图 v3 第二批）。
     * 支持来源：销售订单 / 销售出库单 / 销售发票 / 售后维修单。
     * 草稿金额默认＝来源单未收金额；核销明细按来源单明细行逐行落库（售后维修单整单一条「维修服务费」）；同一来源单已有未作废收款单时跳过（幂等）。
     */
    PaymentReceipt createReceiptDraftFromSource(String sourceType, Long sourceId);

    /**
     * 按主键读取收款单（含明细，明细可为空）。
     * 销售侧售后维修单用它判断自动生成的收款单当前状态（草稿可直接删、已生效需先处理）。
     */
    PaymentReceipt getReceiptById(Long id);

    /**
     * 已生效收款单的核销金额汇总（按来源单据 + 来源明细行）。
     * 返回字段：sourceType / sourceId / sourceItemId / amount。
     * 用途：销售出库单、销售发票没有「已收金额」列，选单弹窗的已收/未收必须按核销明细反算。
     */
    List<Map<String, Object>> getWriteoffReceivedBySource(String sourceTypes);

    /**
     * 某来源单类型下「存在未作废收款单」的来源单号集合。
     * 收款单选单弹窗用于排除已开过收款单的来源单；收款单作废后该来源单应重新进入候选。
     */
    List<String> listActiveReceiptSourceNos(String sourceType);

    /**
     * 提交收款单（草稿→开审批则待审核/免审批则直接生效）
     */
    void submitReceipt(Long id, String submittedBy);

    /**
     * 收款记录列表
     * @param filterCreatedBy 数据权限过滤值(创建人用户名)，为 null 时不过滤
     */
    PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    /**
     * 收款单分页 + 统一查询口径（2026-09-16「列表页查询口径通用化」）。
     * 关键词 keyword 与旧参数 customerName / receiptNo 并存：keyword 优先（成组匹配 单号 + 客户名称），
     * 旧参数保留仅为兼容旧前端/旧链接；customerId = 客户维度；startDate/endDate = 收款日期区间（receipt_date）；
     * employeeId = 员工维度（本页真实列 cashier_id，业务口径「出纳」）。
     */
    PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo,
                                           String keyword, String customerId, String employeeId,
                                           com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                           String sortBy, String sortOrder, String startDate, String endDate);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo,
                                                   String keyword, String customerId, String employeeId,
                                                   com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                                   String sortBy, String sortOrder, String startDate, String endDate,
                                                   java.util.Map<String, String> headerFilters) {
        return pageReceipt(current, size, customerName, status, receiptNo, keyword, customerId, employeeId, scopeCtx, sortBy, sortOrder, startDate, endDate);
    }

    /**
     * 确认收款
     */
    void confirmReceipt(Long id, String confirmedBy);

    /**
     * 重置草稿收款（已确认→草稿），回减订单 received_amount
     */
    void reverseReceipt(Long id, String operator);

    /**
     * 作废收款单，回减订单 received_amount（记录作废前状态供反作废恢复）
     */
    void voidReceipt(Long id, String operator, String reason);

    /**
     * 反作废收款单（已作废→恢复作废前状态；作废前为已确认时重新回写业务影响）
     */
    void unvoidReceipt(Long id, String operator);

    /**
     * 驳回收款单（待审核→草稿）
     */
    void rejectReceipt(Long id, String rejector, String reason);

    /**
     * 结案收款单（已确认→已结案，终态）
     */
    void closeReceipt(Long id, String operator, String reason);

    /**
     * 反结案收款单（已结案→未结案）
     */
    void reopenReceipt(Long id, String operator);

    /**
     * 删除收款单（仅草稿状态可删）
     */
    void deleteReceipt(Long id);

    // ========== 付款记录 ==========

    /**
     * 新增付款记录
     */
    PaymentVoucher createVoucher(PaymentVoucher voucher);

    /**
     * 提交付款单（草稿→开审批则待审核/免审批则直接生效）
     */
    void submitVoucher(Long id, String submittedBy);

    /**
     * 付款记录列表
     * @param filterCreatedBy 数据权限过滤值(创建人用户名)，为 null 时不过滤
     */
    PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    /**
     * 付款单分页 + 统一查询口径（2026-09-16「列表页查询口径通用化」）。
     * 关键词 keyword 与旧参数 supplierName / voucherNo 并存：keyword 优先（成组匹配 单号 + 供应商名称），
     * 旧参数保留仅为兼容旧前端/旧链接；supplierId = 供应商维度；startDate/endDate = 付款日期区间（payment_date）；
     * employeeId = 员工维度（本页真实列 cashier_id，业务口径「出纳」）。
     */
    PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo,
                                           String keyword, String supplierId, String employeeId,
                                           com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                           String sortBy, String sortOrder, String startDate, String endDate);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo,
                                                   String keyword, String supplierId, String employeeId,
                                                   com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                                   String sortBy, String sortOrder, String startDate, String endDate,
                                                   java.util.Map<String, String> headerFilters) {
        return pageVoucher(current, size, supplierName, status, voucherNo, keyword, supplierId, employeeId, scopeCtx, sortBy, sortOrder, startDate, endDate);
    }

    /**
     * 确认付款
     */
    void confirmVoucher(Long id, String confirmedBy);

    /**
     * 驳回付款单（待审核→草稿）
     */
    void rejectVoucher(Long id, String rejector, String reason);

    /**
     * 重置草稿付款单（已确认→草稿），回减应付单已付金额
     */
    void reverseVoucher(Long id, String operator);

    /**
     * 作废付款单（记录作废前状态；已确认单据回减应付单已付金额）
     */
    void voidVoucher(Long id, String operator, String reason);

    /**
     * 反作废付款单（已作废→恢复作废前状态；作废前为已确认时重新回写应付单）
     */
    void unvoidVoucher(Long id, String operator);

    /**
     * 结案付款单（已确认→已结案，终态）
     */
    void closeVoucher(Long id, String operator, String reason);

    /**
     * 反结案付款单（已结案→未结案）
     */
    void reopenVoucher(Long id, String operator);

    /**
     * 删除付款单（仅草稿状态可删）
     */
    void deleteVoucher(Long id);

    // ========== 财务报表 ==========

    /**
     * 应收应付汇总
     */
    Map<String, Object> getSummary();

    /**
     * 账龄分析
     */
    List<Map<String, Object>> getAgingAnalysis();

    /**
     * 按后台配置的来源单据类型，拉取可生成应收的来源单据列表
     * @param sourceType "sale_delivery" 或 "sale_invoice"
     */
    PageResult<Map<String, Object>> listArSourceBills(String sourceType, int current, int size, String keyword);

    // ========== 应收/应付 账款汇总报表 ==========

    /**
     * 应收账款汇总报表：按客户汇总期初、本期发生及结余
     * @param startDate 开始日期（含），可选
     * @param endDate   结束日期（含），可选
     * @param currencyId 币别ID，可选
     * @param customerId 客户ID，可选
     * @param customerType 客户类型，可选
     * @return 汇总结果：records(按客户分组的明细)、total、totalAmount、receivedAmount、unreceivedAmount
     */
    Map<String, Object> getReceivableSummary(LocalDate startDate, LocalDate endDate,
                                             Long currencyId, Long customerId, String customerType);

    /**
     * 应收汇总 + 数据看板穿透过滤（可选，缺省时与上一个方法行为完全一致）。
     * @param overdueOnly 仅看逾期（到期日 < 今天）未付清记录
     * @param agingOver   仅看逾期天数 ≥ N 天（如 90/180）的记录
     */
    default Map<String, Object> getReceivableSummary(LocalDate startDate, LocalDate endDate,
                                                     Long currencyId, Long customerId, String customerType,
                                                     Boolean overdueOnly, Integer agingOver) {
        return getReceivableSummary(startDate, endDate, currencyId, customerId, customerType);
    }

    /**
     * 应付账款汇总报表：按供应商汇总期初、本期发生及结余
     * @param startDate 开始日期（含），可选
     * @param endDate   结束日期（含），可选
     * @param currencyId 币别ID，可选
     * @param supplierId 供应商ID，可选
     * @param supplierType 供应商类型，可选
     * @return 汇总结果：records(按供应商分组的明细)、total、totalAmount、paidAmount、unpaidAmount
     */
    Map<String, Object> getPayableSummary(LocalDate startDate, LocalDate endDate,
                                           Long currencyId, Long supplierId, String supplierType);
}
