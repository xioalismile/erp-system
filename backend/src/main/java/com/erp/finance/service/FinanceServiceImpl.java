package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.BillWriteoffDetail;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentReceiptItem;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.entity.PaymentVoucherItem;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.BillWriteoffDetailMapper;
import com.erp.finance.mapper.PaymentReceiptItemMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherItemMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePayment;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePaymentItem;
import com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService;
import com.erp.finance.prepayment.sale.entity.SalePreReceipt;
import com.erp.finance.prepayment.sale.entity.SalePreReceiptItem;
import com.erp.finance.prepayment.sale.service.SalePreReceiptService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.SupplierService;
import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.service.PaymentTermService;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleInvoiceItem;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleDeliveryItemMapper;
import com.erp.sale.mapper.SaleInvoiceItemMapper;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.service.SaleDeliveryService;
import com.erp.sale.service.SaleInvoiceService;
import com.erp.sale.service.SaleOrderService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.service.PurchaseOrderService;
import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.erp.common.util.SortHelper;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;

/**
 * 财务服务 - 已对齐PG新实体字段
 * AccountsReceivable: arId/arNo/customerId/customerName/currencyId/totalAmount/paidAmount/balanceAmount/dueDate/sourceBillType/sourceBillNo/status/approvalStatus
 * AccountsPayable: apId/apNo/supplierId/supplierName/...
 * PaymentReceipt: receiptId/receiptNo/customerId/customerName/...
 * PaymentVoucher: paymentId/paymentNo/supplierId/supplierName/...
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl extends ServiceImpl<AccountsReceivableMapper, AccountsReceivable>
        implements FinanceService, BillApprovalCallback {

    private final AccountsReceivableMapper arMapper;

    /**
     * 数据权限解析器：应收/应付「汇总报表」与「汇总导出」必须与对应列表页同口径，
     * 否则普通账号可以把全公司的应收/应付汇总出来（历史 BUG）。
     */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /**
     * 汇总/导出取数的数据权限过滤（与列表页逐字同口径，统一走 DataScopeResolver）。
     * 无 Web 请求 / 全部数据时不加条件，保持旧行为。
     *
     * @param ownerColumn 归属列（应收=saler_id，应付=buyer_id）
     */
    private void applySummaryDataScope(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> qw, String ownerColumn) {
        dataScopeResolver.applyIfLimited(qw, ownerColumn, "created_by");
    }
    private final AccountsPayableMapper apMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final PaymentVoucherMapper voucherMapper;
    private final SaleOrderService saleOrderService;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final SaleDeliveryItemMapper saleDeliveryItemMapper;
    private final SaleInvoiceItemMapper saleInvoiceItemMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final PaymentTermService paymentTermService;

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义 · 财务推广批）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（客户/收款人/供应商/付款人/部门/币种/结算方式）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        Map<String, BillHeaderFilterSupport.Spec> r = new java.util.LinkedHashMap<>();
        // ── 收款单（cash_receipt）文本模糊 ──
        r.put("receiptNo", new BillHeaderFilterSupport.Spec("receiptNo", BillHeaderFilterSupport.Kind.TEXT));
        r.put("documentNo", new BillHeaderFilterSupport.Spec("documentNo", BillHeaderFilterSupport.Kind.TEXT));
        r.put("bankAccount", new BillHeaderFilterSupport.Spec("bankAccount", BillHeaderFilterSupport.Kind.TEXT));
        r.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        r.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        r.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 收款单 精确（枚举 / 外键 ID）──
        r.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        r.put("customerName", new BillHeaderFilterSupport.Spec("customerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        r.put("cashierName", new BillHeaderFilterSupport.Spec("cashierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        r.put("settleModeName", new BillHeaderFilterSupport.Spec("settleModeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        r.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        r.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        r.put("closeStatus", new BillHeaderFilterSupport.Spec("closeStatus", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 收款单 日期区间 / 数值区间 ──
        r.put("receiptDate", new BillHeaderFilterSupport.Spec("receiptDate", BillHeaderFilterSupport.Kind.DATE));
        r.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        r.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        r.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        r.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        r.put("receivedAmount", new BillHeaderFilterSupport.Spec("receivedAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("paymentReceipt", PaymentReceipt.class, r);

        Map<String, BillHeaderFilterSupport.Spec> v = new java.util.LinkedHashMap<>();
        // ── 付款单（cash_payment）文本模糊 ──
        v.put("paymentNo", new BillHeaderFilterSupport.Spec("paymentNo", BillHeaderFilterSupport.Kind.TEXT));
        v.put("documentNo", new BillHeaderFilterSupport.Spec("documentNo", BillHeaderFilterSupport.Kind.TEXT));
        v.put("bankAccount", new BillHeaderFilterSupport.Spec("bankAccount", BillHeaderFilterSupport.Kind.TEXT));
        v.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        v.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        v.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 付款单 精确（枚举 / 外键 ID）──
        v.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        v.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        v.put("cashierName", new BillHeaderFilterSupport.Spec("cashierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        v.put("settleModeName", new BillHeaderFilterSupport.Spec("settleModeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        v.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        v.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        v.put("closeStatus", new BillHeaderFilterSupport.Spec("closeStatus", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 付款单 日期区间 / 数值区间 ──
        v.put("paymentDate", new BillHeaderFilterSupport.Spec("paymentDate", BillHeaderFilterSupport.Kind.DATE));
        v.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        v.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        v.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        v.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        v.put("paidAmount", new BillHeaderFilterSupport.Spec("paidAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("paymentVoucher", PaymentVoucher.class, v);
    }

    /**
     * 账期天数（2026-09-28 新口径）：应收/应付到期日 = 基准日 + 账期天数。
     * 取值优先级：① 单据上的付款条件的账期天数；② 客户/供应商档案的账期天数；③ 都没有 → 返回 null（调用方沿用原行为）。
     */
    private Integer resolveCreditDays(Long paymentTermId, Long customerId, Long supplierId) {
        if (paymentTermId != null) {
            PaymentTerm pt = paymentTermService.getById(paymentTermId);
            if (pt != null && pt.getPaymentDays() != null && pt.getPaymentDays() > 0) {
                return pt.getPaymentDays();
            }
        }
        if (customerId != null) {
            Customer c = customerService.getById(customerId);
            if (c != null && c.getCreditDays() != null && c.getCreditDays() > 0) {
                return c.getCreditDays();
            }
        }
        if (supplierId != null) {
            Supplier s = supplierService.getById(supplierId);
            if (s != null && s.getCreditDays() != null && s.getCreditDays() > 0) {
                return s.getCreditDays();
            }
        }
        return null;
    }

    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final ApprovalGateHelper approvalGateHelper;
    /** 审批通用接入件（提交进入审批引擎 + 构建条件路由 billData） */
    private final BillApprovalAdapter billApprovalAdapter;

    /**
     * 审批运行时引擎：用于「已有进行中审批任务时禁止绕过引擎直审」的防护判断。
     * @Lazy 打破循环依赖：审批引擎按 BillApprovalCallback 收集全部单据Service，
     * 单据Service又需要审批引擎 —— 延迟代理避免启动期循环创建失败。
     */
    @Lazy
    @Autowired
    private ApprovalRuntimeService approvalRuntimeService;
    private final BillWriteoffDetailMapper billWriteoffDetailMapper;
    private final PaymentReceiptItemMapper receiptItemMapper;
    private final PaymentVoucherItemMapper voucherItemMapper;
    private final SalePreReceiptService salePreReceiptService;
    private final PurchasePrePaymentService purchasePrePaymentService;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    /**
     * 售后维修单收费：财务侧只需读取维修单号/客户/合计费用/已回写收款单，
     * 直接走 jdbc 读取 sale_repair，不注入销售模块服务（避免财务↔销售循环依赖）。
     */
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    @Lazy
    private SaleDeliveryService saleDeliveryService;

    @Autowired
    @Lazy
    private SaleInvoiceService saleInvoiceService;

    @Autowired
    private SysConfigService sysConfigService;

    /** 付款单列表可排序列白名单（防 SQL 注入；前端 prop 驼峰自动转下划线后匹配） */
    private static final Set<String> VOUCHER_SORTABLE_COLUMNS = Set.of(
            "payment_id", "payment_no", "payment_date", "supplier_name",
            "total_amount", "paid_amount", "created_by", "created_at");

    /** 收款单列表可排序列白名单（防 SQL 注入） */
    private static final Set<String> RECEIPT_SORTABLE_COLUMNS = Set.of(
            "receipt_id", "receipt_no", "receipt_date", "customer_name",
            "total_amount", "received_amount", "created_by", "created_at");

    // ========== 应收账款 ==========

    @Override
    @Transactional
    public AccountsReceivable createArFromSaleOrder(Long saleOrderId) {
        SaleOrder order = saleOrderService.getDetail(saleOrderId);
        if (order == null) throw new BusinessException("销售订单不存在");

        // 检查是否已生成应收
        AccountsReceivable existing = arMapper.selectOne(
                new QueryWrapper<AccountsReceivable>().eq("source_bill_no", order.getOrderNo()).eq("source_bill_type", "销售订单"));
        if (existing != null) {
            throw new BusinessException("该销售订单已生成应收单");
        }
        // 【2026-10-13 来源唯一】该订单已按其它来源（出库单/发票）生成过应收 → 不允许再整单生成（来源链一经生成不可换）
        List<AccountsReceivable> crossSourceArs = arMapper.selectList(
                new QueryWrapper<AccountsReceivable>().eq("source_order_no", order.getOrderNo()));
        boolean hasCrossSource = crossSourceArs != null && crossSourceArs.stream()
                .anyMatch(a -> a != null && a.getSourceBillType() != null && !"销售订单".equals(a.getSourceBillType()));
        if (hasCrossSource) {
            throw new BusinessException("该订单已按销售出库单/销售发票生成应收单，不允许再按整单生成应收（来源唯一）");
        }

        // 通过customerId查询客户名
        String customerName = "未知客户";
        if (order.getCustomerId() != null) {
            Customer customer = customerService.getById(order.getCustomerId());
            if (customer != null) customerName = customer.getCustomerName();
        }

        AccountsReceivable ar = new AccountsReceivable();
        ar.setArNo(generateArNo());
        ar.setArDate(LocalDate.now());
        ar.setCustomerId(order.getCustomerId());
        ar.setCustomerName(customerName);
        ar.setCurrencyId(order.getCurrencyId());
        ar.setExchangeRate(order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE);
        // 【2026-10-14 修复·与发票/出库侧 2026-09-11 同款金蝶标准】应收金额 = 含税价税合计（net_amount）。
        // 原实现取 totalAmount（不含税），balanceAmount 也取它 → 应收余额漏计税额，
        // 收款核销按余额封顶永远差一截税额（订单 565 只能核 500），「销售发货+销售发票+收款单」完结模式的收款完成永远无法满足。
        BigDecimal arAmount = order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO;
        ar.setTotalAmount(arAmount);
        ar.setTaxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO);
        ar.setNetAmount(order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO);
        ar.setPaidAmount(BigDecimal.ZERO);
        ar.setBalanceAmount(arAmount);
        Integer creditDays = resolveCreditDays(order.getPaymentTermId(), order.getCustomerId(), null);
        LocalDate arBase = order.getDeliveryDate() != null ? order.getDeliveryDate() : LocalDate.now();
        ar.setDueDate(creditDays != null ? arBase.plusDays(creditDays) : arBase);
        ar.setPaymentTermId(order.getPaymentTermId());
        ar.setSourceBillType("销售订单");
        ar.setSourceBillNo(order.getOrderNo());
        ar.setSourceOrderNo(order.getOrderNo());
        ar.setSalerId(order.getSalerId());
        ar.setIsAutoGen((short) 1);
        ar.setIsClosed((short) 0);
        ar.setStatus("待收款");
        ar.setApprovalStatus("已生效");

        arMapper.insert(ar);
        log.info("应收账款生成: {} 来源: {} 金额: {}", ar.getArNo(), order.getOrderNo(), ar.getTotalAmount());
        return ar;
    }

    @Override
    @Transactional
    public AccountsReceivable createArFromSaleDelivery(Long deliveryId) {
        SaleDelivery delivery = saleDeliveryService.getDetail(deliveryId);
        if (delivery == null) throw new BusinessException("销售出库单不存在");

        // 检查是否已生成应收
        AccountsReceivable existing = arMapper.selectOne(
                new QueryWrapper<AccountsReceivable>()
                        .eq("source_bill_no", delivery.getDeliveryNo())
                        .eq("source_bill_type", "销售出库单"));
        if (existing != null) {
            throw new BusinessException("该销售出库单已生成应收单");
        }

        String customerName = "未知客户";
        if (delivery.getCustomerId() != null) {
            Customer customer = customerService.getById(delivery.getCustomerId());
            if (customer != null) customerName = customer.getCustomerName();
        }

        AccountsReceivable ar = new AccountsReceivable();
        ar.setArNo(generateArNo());
        ar.setArDate(LocalDate.now());
        ar.setCustomerId(delivery.getCustomerId());
        ar.setCustomerName(customerName);
        ar.setCurrencyId(delivery.getCurrencyId());
        ar.setExchangeRate(delivery.getExchangeRate() != null ? delivery.getExchangeRate() : BigDecimal.ONE);
        // 【2026-09-11 修复】同上：应收金额取含税（价税合计）netAmount
        BigDecimal arAmount = delivery.getNetAmount() != null ? delivery.getNetAmount() : BigDecimal.ZERO;
        ar.setTotalAmount(arAmount);
        ar.setTaxAmount(delivery.getTaxAmount() != null ? delivery.getTaxAmount() : BigDecimal.ZERO);
        ar.setNetAmount(delivery.getNetAmount() != null ? delivery.getNetAmount() : BigDecimal.ZERO);
        ar.setPaidAmount(BigDecimal.ZERO);
        ar.setBalanceAmount(arAmount);
        Integer creditDays = resolveCreditDays(null, delivery.getCustomerId(), null);
        LocalDate arBase = delivery.getDeliveryDate() != null ? delivery.getDeliveryDate() : LocalDate.now();
        ar.setDueDate(arBase.plusDays(creditDays != null ? creditDays : 30));
        ar.setSourceBillType("销售出库单");
        ar.setSourceBillNo(delivery.getDeliveryNo());
        // 来源销售订单号（2026-10-12）：发货收款回写订单的关联链路；出库单带不出订单号时留空（手工出库不回写）
        String sourceOrderNo = delivery.getSourceBillNo() != null
                && ("sale_order".equals(delivery.getSourceBillType()) || "销售订单".equals(delivery.getSourceBillType()))
                ? delivery.getSourceBillNo() : null;
        // 【2026-10-13 来源唯一】该订单已存在「整单应收」（来源=销售订单）→ 不允许再按出库单生成（重复挂应收）
        if (sourceOrderNo != null) {
            AccountsReceivable orderLevelAr = arMapper.selectOne(new QueryWrapper<AccountsReceivable>()
                    .eq("source_bill_type", "销售订单").eq("source_bill_no", sourceOrderNo).last("LIMIT 1"));
            if (orderLevelAr != null) {
                throw new BusinessException(String.format(
                        "该订单已生成整单应收单（%s），不允许再按销售出库单生成应收（来源唯一）", orderLevelAr.getArNo()));
            }
        }
        ar.setSourceOrderNo(sourceOrderNo);
        ar.setSalerId(delivery.getSalerId());
        ar.setIsAutoGen((short) 1);
        ar.setIsClosed((short) 0);
        ar.setStatus("待收款");
        ar.setApprovalStatus("已生效");

        // 出库生成应收时的「订单已收抵扣」：
        // 【2026-10-12 口径修正】订单已收金额现在同时包含「直接订单收款」与「发货收款回写」两部分，
        // 直接拿订单已收做抵扣会把已分摊给其它应收单的金额再抵一次（重复抵扣 → 应收余额虚低、订单已收虚增）。
        // 可抵扣额 = 订单已收 − 该订单已有应收单的已收合计（尚未被任何应收单占用的已收部分）。
        if (sourceOrderNo != null) {
            SaleOrder order = saleOrderService.getOne(new QueryWrapper<SaleOrder>().eq("order_no", sourceOrderNo));
            if (order != null) {
                BigDecimal orderReceived = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
                BigDecimal arPaidSum = BigDecimal.ZERO;
                List<AccountsReceivable> sameOrderArs = arMapper.selectList(
                        new QueryWrapper<AccountsReceivable>().eq("source_order_no", sourceOrderNo));
                for (AccountsReceivable other : sameOrderArs) {
                    if (other.getPaidAmount() != null) arPaidSum = arPaidSum.add(other.getPaidAmount());
                }
                BigDecimal available = orderReceived.subtract(arPaidSum);
                BigDecimal arTotal = ar.getTotalAmount();
                if (available.compareTo(arTotal) >= 0) {
                    // 订单已收未占用部分 ≥ 出库金额，应收单直接标记已结清
                    ar.setPaidAmount(arTotal);
                    ar.setBalanceAmount(BigDecimal.ZERO);
                    ar.setStatus("已结清");
                    ar.setIsClosed((short) 1);
                    log.info("出库生成应收: 订单已收未占用部分({})≥应收金额({}), 应收单直接结清", available, arTotal);
                } else if (available.compareTo(BigDecimal.ZERO) > 0) {
                    // 部分抵扣：应收余额 = 出库金额 - 未占用已收
                    ar.setPaidAmount(available);
                    ar.setBalanceAmount(arTotal.subtract(available));
                    ar.setStatus("部分收款");
                    log.info("出库生成应收: 订单已收未占用部分({}), 应收余额调整为 {}", available, ar.getBalanceAmount());
                }
            }
        }

        arMapper.insert(ar);
        log.info("应收账款生成(来自出库单): {} 来源: {} 金额: {}", ar.getArNo(), delivery.getDeliveryNo(), ar.getTotalAmount());
        return ar;
    }

    @Override
    @Transactional
    public AccountsReceivable createArFromSaleInvoice(Long invoiceId) {
        SaleInvoice invoice = saleInvoiceService.getDetail(invoiceId);
        if (invoice == null) throw new BusinessException("销售发票不存在");

        // 检查是否已生成应收
        AccountsReceivable existing = arMapper.selectOne(
                new QueryWrapper<AccountsReceivable>()
                        .eq("source_bill_no", invoice.getInvoiceNo())
                        .eq("source_bill_type", "销售发票"));
        if (existing != null) {
            throw new BusinessException("该销售发票已生成应收单");
        }

        String customerName = "未知客户";
        if (invoice.getCustomerId() != null) {
            Customer customer = customerService.getById(invoice.getCustomerId());
            if (customer != null) customerName = customer.getCustomerName();
        }

        AccountsReceivable ar = new AccountsReceivable();
        ar.setArNo(generateArNo());
        ar.setArDate(LocalDate.now());
        ar.setCustomerId(invoice.getCustomerId());
        ar.setCustomerName(customerName);
        ar.setCurrencyId(invoice.getCurrencyId());
        ar.setExchangeRate(invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE);
        // 【2026-09-11 修复】金蝶标准：应收单的"应收金额"= 含税金额（价税合计）= 发票 netAmount。
        // 原实现取 totalAmount（不含税1000），balanceAmount 也取它 → 应收余额漏计税额130，按余额收款少收。
        BigDecimal arAmount = invoice.getNetAmount() != null ? invoice.getNetAmount() : BigDecimal.ZERO;
        ar.setTotalAmount(arAmount);
        ar.setTaxAmount(invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO);
        ar.setNetAmount(invoice.getNetAmount() != null ? invoice.getNetAmount() : BigDecimal.ZERO);
        ar.setPaidAmount(BigDecimal.ZERO);
        ar.setBalanceAmount(arAmount);
        Integer creditDays = resolveCreditDays(null, invoice.getCustomerId(), null);
        LocalDate arBase = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now();
        ar.setDueDate(arBase.plusDays(creditDays != null ? creditDays : 30));
        ar.setSourceBillType("销售发票");
        ar.setSourceBillNo(invoice.getInvoiceNo());
        // 【2026-10-13 来源唯一】发票由订单直接开出的场合：订单已有整单应收 → 不允许再按发票生成（重复挂应收）
        if ("销售订单".equals(invoice.getSourceBillType()) && invoice.getSourceBillNo() != null) {
            AccountsReceivable orderLevelArInv = arMapper.selectOne(new QueryWrapper<AccountsReceivable>()
                    .eq("source_bill_type", "销售订单").eq("source_bill_no", invoice.getSourceBillNo()).last("LIMIT 1"));
            if (orderLevelArInv != null) {
                throw new BusinessException(String.format(
                        "该订单已生成整单应收单（%s），不允许再按销售发票生成应收（来源唯一）", orderLevelArInv.getArNo()));
            }
        }
        ar.setSalerId(invoice.getSalerId());
        ar.setIsAutoGen((short) 1);
        ar.setIsClosed((short) 0);
        ar.setStatus("待收款");
        ar.setApprovalStatus("已生效");

        arMapper.insert(ar);
        log.info("应收账款生成(来自发票): {} 来源: {} 金额: {}", ar.getArNo(), invoice.getInvoiceNo(), ar.getTotalAmount());
        return ar;
    }

    @Override
    @Transactional
    public void receivePayment(Long arId, BigDecimal amount, Long settleModeId, String documentNo, String operator) {
        AccountsReceivable ar = arMapper.selectById(arId);
        if (ar == null) throw new BusinessException("应收单不存在");

        BigDecimal newReceived = ar.getPaidAmount().add(amount);
        ar.setPaidAmount(newReceived);
        ar.setBalanceAmount(ar.getTotalAmount().subtract(newReceived));

        if (newReceived.compareTo(ar.getTotalAmount()) >= 0) {
            ar.setStatus("已结清");
            ar.setIsClosed((short) 1);
        } else if (newReceived.compareTo(BigDecimal.ZERO) > 0) {
            ar.setStatus("部分收款");
        }

        arMapper.updateById(ar);

        // 生成收款记录
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setReceiptNo(generateReceiptNo());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setCustomerId(ar.getCustomerId());
        receipt.setCustomerName(ar.getCustomerName());
        receipt.setSettleModeId(settleModeId);
        receipt.setCurrencyId(ar.getCurrencyId());
        receipt.setExchangeRate(ar.getExchangeRate());
        receipt.setTotalAmount(ar.getTotalAmount());
        receipt.setAmount(amount);
        receipt.setDiscountAmount(BigDecimal.ZERO);
        receipt.setReceivedAmount(newReceived);
        receipt.setDocumentNo(documentNo);
        receipt.setSourceBillType("应收单");
        receipt.setSourceBillNo(ar.getArNo());
        receipt.setStatus("已确认");
        receipt.setApprovalStatus("已生效");
        receiptMapper.insert(receipt);
        // 自动生成的收款单也必须落库收款明细，否则打开该单「收款明细」为空白
        PaymentReceiptItem receiptItem = new PaymentReceiptItem();
        receiptItem.setAmount(amount);
        List<PaymentReceiptItem> receiptItems = new java.util.ArrayList<>();
        receiptItems.add(receiptItem);
        saveReceiptItems(receipt.getReceiptId(), receiptItems);

        log.info("收款登记: {} 金额: {} 累计: {}", ar.getArNo(), amount, newReceived);
    }

    @Override
    public PageResult<AccountsReceivable> pageAr(int current, int size, String customerName, String status, String arNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<AccountsReceivable> wrapper = new QueryWrapper<>();
        SortHelper.orderByNewest(wrapper, AccountsReceivable.class);
        if (customerName != null && !customerName.isBlank()) wrapper.like("customer_name", customerName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (arNo != null && !arNo.isBlank()) wrapper.like("ar_no", arNo);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        Page<AccountsReceivable> page = new Page<>(current, size);
        Page<AccountsReceivable> result = arMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public AccountsReceivable getArDetail(Long id) {
        return arMapper.selectById(id);
    }

    @Override
    @Transactional
    public AccountsReceivable saveAr(AccountsReceivable ar) {
        if (ar.getArNo() == null || ar.getArNo().isEmpty()) {
            ar.setArNo(generateArNo());
        }
        if (ar.getArDate() == null) ar.setArDate(LocalDate.now());
        if (ar.getStatus() == null) ar.setStatus("待收款");
        if (ar.getApprovalStatus() == null) ar.setApprovalStatus("已生效");
        if (ar.getPaidAmount() == null) ar.setPaidAmount(BigDecimal.ZERO);
        if (ar.getTotalAmount() == null) ar.setTotalAmount(BigDecimal.ZERO);
        if (ar.getBalanceAmount() == null) ar.setBalanceAmount(ar.getTotalAmount());
        if (ar.getExchangeRate() == null) ar.setExchangeRate(BigDecimal.ONE);
        if (ar.getTaxAmount() == null) ar.setTaxAmount(BigDecimal.ZERO);
        if (ar.getNetAmount() == null) ar.setNetAmount(ar.getTotalAmount());
        if (ar.getCustomerId() == null) ar.setCustomerId(0L);
        if (ar.getCurrencyId() == null) ar.setCurrencyId(1L);
        if (ar.getIsAutoGen() == null) ar.setIsAutoGen((short) 0);  // 手动新增标记
        if (ar.getIsClosed() == null) ar.setIsClosed((short) 0);
        if (ar.getSourceBillType() == null) ar.setSourceBillType("手动新增");
        if (ar.getSourceBillNo() == null) ar.setSourceBillNo("手动新增");
        if (ar.getCreatedBy() == null) ar.setCreatedBy("系统");
        arMapper.insert(ar);
        log.info("应收账款手动新增: {} 金额: {}", ar.getArNo(), ar.getTotalAmount());
        return ar;
    }

    @Override
    @Transactional
    public void updateAr(AccountsReceivable ar) {
        // 仅待收款/部分收款状态可编辑
        AccountsReceivable existing = arMapper.selectById(ar.getArId());
        if (existing == null) throw new BusinessException("应收单不存在");
        if (!"待收款".equals(existing.getStatus()) && !"部分收款".equals(existing.getStatus())) {
            throw new BusinessException("当前状态不允许编辑");
        }
        // 编辑时保持已收金额、余额、状态不变，仅允许修改基础信息
        ar.setPaidAmount(existing.getPaidAmount());
        ar.setBalanceAmount(existing.getBalanceAmount());
        ar.setStatus(existing.getStatus());
        // 【2026-10-13 来源不可改】来源单据/来源单号/来源订单号落库后锁定（无论手工还是自动生成），
        // 防止编辑时改挂来源导致同一来源单重复生成应收、收款链路错乱
        ar.setSourceBillType(existing.getSourceBillType());
        ar.setSourceBillNo(existing.getSourceBillNo());
        ar.setSourceOrderNo(existing.getSourceOrderNo());
        arMapper.updateById(ar);
        log.info("应收账款更新: {}", ar.getArNo());
    }

    @Override
    @Transactional
    public void deleteAr(Long id) {
        AccountsReceivable existing = arMapper.selectById(id);
        if (existing == null) throw new BusinessException("应收单不存在");
        if (existing.getPaidAmount() != null && existing.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已收款的应收单不允许删除");
        }
        arMapper.deleteById(id);
        log.info("应收账款删除: {}", existing.getArNo());
    }

    @Override
    public AccountsReceivable getArBySourceBillNo(String sourceBillNo, String sourceBillType) {
        if (sourceBillNo == null || sourceBillType == null) return null;
        return arMapper.selectOne(
            new QueryWrapper<AccountsReceivable>()
                .eq("source_bill_no", sourceBillNo)
                .eq("source_bill_type", sourceBillType)
                .last("LIMIT 1"));
    }

    // ========== 应付账款 ==========

    @Override
    @Transactional
    public AccountsPayable createApFromPurchaseOrder(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderService.getDetail(purchaseOrderId);
        if (order == null) throw new BusinessException("采购订单不存在");

        // 检查是否已生成应付
        AccountsPayable existing = apMapper.selectOne(
                new QueryWrapper<AccountsPayable>().eq("source_bill_no", order.getOrderNo()).eq("source_bill_type", "采购订单"));
        if (existing != null) {
            throw new BusinessException("该采购订单已生成应付单");
        }

        // 通过supplierId查询供应商名
        String supplierName = "未知供应商";
        if (order.getSupplierId() != null) {
            Supplier supplier = supplierService.getById(order.getSupplierId());
            if (supplier != null) supplierName = supplier.getSupplierName();
        }

        AccountsPayable ap = new AccountsPayable();
        ap.setApNo(generateApNo());
        ap.setApDate(LocalDate.now());
        ap.setSupplierId(order.getSupplierId());
        ap.setSupplierName(supplierName);
        ap.setCurrencyId(order.getCurrencyId());
        ap.setExchangeRate(order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE);
        ap.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        ap.setTaxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO);
        ap.setNetAmount(order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO);
        ap.setPaidAmount(BigDecimal.ZERO);
        ap.setBalanceAmount(ap.getTotalAmount());
        Integer creditDays = resolveCreditDays(order.getPaymentTermId(), null, order.getSupplierId());
        LocalDate apBase = order.getDeliveryDate() != null ? order.getDeliveryDate() : LocalDate.now();
        ap.setDueDate(creditDays != null ? apBase.plusDays(creditDays) : apBase);
        ap.setPaymentTermId(order.getPaymentTermId());
        ap.setSourceBillType("采购订单");
        ap.setSourceBillNo(order.getOrderNo());
        ap.setBuyerId(order.getBuyerId());
        ap.setIsAutoGen((short) 1);
        ap.setIsClosed((short) 0);
        ap.setStatus("待付款");
        ap.setApprovalStatus("已生效");

        apMapper.insert(ap);
        log.info("应付账款生成: {} 来源: {} 金额: {}", ap.getApNo(), order.getOrderNo(), ap.getTotalAmount());
        return ap;
    }

    @Override
    @Transactional
    public void makePayment(Long apId, BigDecimal amount, Long settleModeId, String documentNo, String operator) {
        AccountsPayable ap = apMapper.selectById(apId);
        if (ap == null) throw new BusinessException("应付单不存在");

        BigDecimal newPaid = ap.getPaidAmount().add(amount);
        ap.setPaidAmount(newPaid);
        ap.setBalanceAmount(ap.getTotalAmount().subtract(newPaid));

        if (newPaid.compareTo(ap.getTotalAmount()) >= 0) {
            ap.setStatus("已结清");
            ap.setIsClosed((short) 1);
        } else if (newPaid.compareTo(BigDecimal.ZERO) > 0) {
            ap.setStatus("部分付款");
        }

        apMapper.updateById(ap);

        // 生成付款记录
        PaymentVoucher voucher = new PaymentVoucher();
        voucher.setPaymentNo(generateVoucherNo());
        voucher.setPaymentDate(LocalDate.now());
        voucher.setSupplierId(ap.getSupplierId());
        voucher.setSupplierName(ap.getSupplierName());
        voucher.setSettleModeId(settleModeId);
        voucher.setCurrencyId(ap.getCurrencyId());
        voucher.setExchangeRate(ap.getExchangeRate());
        voucher.setTotalAmount(ap.getTotalAmount());
        voucher.setAmount(amount);
        voucher.setDiscountAmount(BigDecimal.ZERO);
        voucher.setPaidAmount(newPaid);
        voucher.setDocumentNo(documentNo);
        voucher.setSourceBillType("应付单");
        voucher.setSourceBillNo(ap.getApNo());
        voucher.setStatus("已确认");
        voucher.setApprovalStatus("已生效");
        voucherMapper.insert(voucher);
        // 自动生成的付款单也必须落库付款明细，否则打开该单「付款明细」为空白
        PaymentVoucherItem voucherItem = new PaymentVoucherItem();
        voucherItem.setAmount(amount);
        List<PaymentVoucherItem> voucherItems = new java.util.ArrayList<>();
        voucherItems.add(voucherItem);
        saveVoucherItems(voucher.getPaymentId(), voucherItems);

        log.info("付款登记: {} 金额: {} 累计: {}", ap.getApNo(), amount, newPaid);
    }

    @Override
    public PageResult<AccountsPayable> pageAp(int current, int size, String supplierName, String status, String apNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
        SortHelper.orderByNewest(wrapper, AccountsPayable.class);
        if (supplierName != null && !supplierName.isBlank()) wrapper.like("supplier_name", supplierName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (apNo != null && !apNo.isBlank()) wrapper.like("ap_no", apNo);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        Page<AccountsPayable> page = new Page<>(current, size);
        Page<AccountsPayable> result = apMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public AccountsPayable getApDetail(Long id) {
        return apMapper.selectById(id);
    }

    @Override
    @Transactional
    public AccountsPayable saveAp(AccountsPayable ap) {
        if (ap.getApNo() == null || ap.getApNo().isEmpty()) {
            ap.setApNo(generateApNo());
        }
        if (ap.getApDate() == null) ap.setApDate(LocalDate.now());
        if (ap.getStatus() == null) ap.setStatus("待付款");
        if (ap.getApprovalStatus() == null) ap.setApprovalStatus("已生效");
        if (ap.getPaidAmount() == null) ap.setPaidAmount(BigDecimal.ZERO);
        if (ap.getTotalAmount() == null) ap.setTotalAmount(BigDecimal.ZERO);
        if (ap.getBalanceAmount() == null) ap.setBalanceAmount(ap.getTotalAmount());
        if (ap.getExchangeRate() == null) ap.setExchangeRate(BigDecimal.ONE);
        if (ap.getTaxAmount() == null) ap.setTaxAmount(BigDecimal.ZERO);
        if (ap.getNetAmount() == null) ap.setNetAmount(ap.getTotalAmount());
        if (ap.getSupplierId() == null) ap.setSupplierId(0L);
        if (ap.getCurrencyId() == null) ap.setCurrencyId(1L);
        if (ap.getIsAutoGen() == null) ap.setIsAutoGen((short) 0);  // 手动新增标记
        if (ap.getIsClosed() == null) ap.setIsClosed((short) 0);
        if (ap.getSourceBillType() == null) ap.setSourceBillType("手动新增");
        if (ap.getSourceBillNo() == null) ap.setSourceBillNo("手动新增");
        if (ap.getCreatedBy() == null) ap.setCreatedBy("系统");
        apMapper.insert(ap);
        log.info("应付账款手动新增: {} 金额: {}", ap.getApNo(), ap.getTotalAmount());
        return ap;
    }

    @Override
    @Transactional
    public void updateAp(AccountsPayable ap) {
        // 仅待付款/部分付款状态可编辑
        AccountsPayable existing = apMapper.selectById(ap.getApId());
        if (existing == null) throw new BusinessException("应付单不存在");
        if (!"待付款".equals(existing.getStatus()) && !"部分付款".equals(existing.getStatus())) {
            throw new BusinessException("当前状态不允许编辑");
        }
        // 编辑时保持已付金额、余额、状态不变，仅允许修改基础信息
        ap.setPaidAmount(existing.getPaidAmount());
        ap.setBalanceAmount(existing.getBalanceAmount());
        ap.setStatus(existing.getStatus());
        apMapper.updateById(ap);
        log.info("应付账款更新: {}", ap.getApNo());
    }

    @Override
    @Transactional
    public void deleteAp(Long id) {
        AccountsPayable existing = apMapper.selectById(id);
        if (existing == null) throw new BusinessException("应付单不存在");
        if (existing.getPaidAmount() != null && existing.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已付款的应付单不允许删除");
        }
        apMapper.deleteById(id);
        log.info("应付账款删除: {}", existing.getApNo());
    }

    // ========== 收款记录 ==========

    @Override
    @Transactional
    public PaymentReceipt createReceipt(PaymentReceipt receipt) {
        // 提成兜底计算（2026-10-16）：行提成/表头不含税/表头提成统一重算（前端实时算+后端兜底，两处同口径）
        applyReceiptCommissionCalc(receipt);
        // 编辑已存在的单据 → 更新；否则新增
        if (receipt.getReceiptId() != null && receiptMapper.selectById(receipt.getReceiptId()) != null) {
            PaymentReceipt existing = receiptMapper.selectById(receipt.getReceiptId());
            String est = existing.getStatus();
            if (!"草稿".equals(est) && !"DRAFT".equals(est)) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + est);
            }
            if (existing.getCloseStatus() != null && existing.getCloseStatus() == 1) {
                throw new BusinessException("收款单[" + existing.getReceiptNo() + "]已结案，不能修改");
            }
            receiptMapper.updateById(receipt);
            saveReceiptItems(receipt.getReceiptId(), receipt.getItems());
            saveWriteoffDetails("收款单", receipt.getReceiptId(), receipt.getReceiptNo(), receipt.getWriteOffItems());
            return receipt;
        }
        if (receipt.getReceiptNo() == null || receipt.getReceiptNo().isBlank()) {
            receipt.setReceiptNo(generateReceiptNo());
        }
        // 保存即草稿；是否需要审批由「提交」时校验（submitReceipt）
        if (receipt.getStatus() == null || receipt.getStatus().isBlank()) receipt.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"（前端表单会送空串，"待审核"只在提交后出现）
        if (receipt.getApprovalStatus() == null || receipt.getApprovalStatus().isBlank()
                || ("草稿".equals(receipt.getStatus()) && "待审核".equals(receipt.getApprovalStatus()))) {
            receipt.setApprovalStatus("草稿");
        }
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(LocalDate.now());
        if (receipt.getExchangeRate() == null) receipt.setExchangeRate(BigDecimal.ONE);
        if (receipt.getDiscountAmount() == null) receipt.setDiscountAmount(BigDecimal.ZERO);
        receiptMapper.insert(receipt);
        saveReceiptItems(receipt.getReceiptId(), receipt.getItems());
        saveWriteoffDetails("收款单", receipt.getReceiptId(), receipt.getReceiptNo(), receipt.getWriteOffItems());
        return receipt;
    }

    /**
     * 业务单据审核 → 直接生成收款单草稿（设计图 v3：系统不生成应收单）。
     * 收款单只有一张核销明细列表，来源靠 sourceType（销售订单 / 销售出库单 / 销售发票 / 售后维修单）区分；
     * 草稿金额默认＝来源单未收金额，财务可在草稿里改成实收金额（支持部分收款）。
     */
    @Override
    @Transactional
    public PaymentReceipt createReceiptDraftFromSource(String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) return null;
        String sourceNo;
        Long customerId;
        String customerName;
        Long currencyId;
        BigDecimal exchangeRate;
        List<BillWriteoffDetail> rows = new ArrayList<>();
        switch (sourceType) {
            case "销售订单": {
                SaleOrder o = saleOrderService.getDetail(sourceId);
                sourceNo = o.getOrderNo();
                customerId = o.getCustomerId();
                customerName = o.getCustomerName();
                currencyId = o.getCurrencyId();
                exchangeRate = o.getExchangeRate();
                for (SaleOrderItem it : o.getItems() == null ? Collections.<SaleOrderItem>emptyList() : o.getItems()) {
                    BigDecimal net = money(it.getNetAmount());
                    BigDecimal rec = money(it.getReceivedAmount());
                    BigDecimal remain = net.subtract(rec).max(BigDecimal.ZERO);
                    if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
                    rows.add(buildWriteoffRow("销售订单", o.getOrderId(), o.getOrderNo(),
                            it.getOrderItemId(), it.getMaterialName(), net, rec, remain));
                }
                break;
            }
            case "销售出库单": {
                SaleDelivery d = saleDeliveryService.getDetail(sourceId);
                sourceNo = d.getDeliveryNo();
                customerId = d.getCustomerId();
                customerName = d.getCustomerName();
                currencyId = d.getCurrencyId();
                exchangeRate = d.getExchangeRate();
                for (SaleDeliveryItem it : d.getItems() == null ? Collections.<SaleDeliveryItem>emptyList() : d.getItems()) {
                    BigDecimal net = money(it.getNetAmount());
                    BigDecimal rec = writtenOffAmount("销售出库单", d.getDeliveryId(), it.getDeliveryItemId());
                    BigDecimal remain = net.subtract(rec).max(BigDecimal.ZERO);
                    if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
                    rows.add(buildWriteoffRow("销售出库单", d.getDeliveryId(), d.getDeliveryNo(),
                            it.getDeliveryItemId(), it.getMaterialName(), net, rec, remain));
                }
                break;
            }
            case "销售发票": {
                SaleInvoice inv = saleInvoiceService.getDetail(sourceId);
                sourceNo = inv.getInvoiceNo();
                customerId = inv.getCustomerId();
                customerName = inv.getCustomerName();
                currencyId = inv.getCurrencyId();
                exchangeRate = inv.getExchangeRate();
                for (SaleInvoiceItem it : inv.getItems() == null ? Collections.<SaleInvoiceItem>emptyList() : inv.getItems()) {
                    BigDecimal net = money(it.getNetAmount());
                    BigDecimal rec = writtenOffAmount("销售发票", inv.getInvoiceId(), it.getInvoiceItemId());
                    BigDecimal remain = net.subtract(rec).max(BigDecimal.ZERO);
                    if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
                    rows.add(buildWriteoffRow("销售发票", inv.getInvoiceId(), inv.getInvoiceNo(),
                            it.getInvoiceItemId(), it.getMaterialName(), net, rec, remain));
                }
                break;
            }
            case "售后维修单": {
                // 保外维修完成的收费：整单一条「维修服务费」核销明细（维修单无物料明细行，sourceItemId 置空）。
                // 只读 sale_repair：不注入销售服务，避免财务↔销售循环依赖。
                List<Map<String, Object>> repairs = jdbcTemplate.queryForList(
                        "SELECT repair_id, repair_no, customer_id, customer_name, total_fee, receipt_id "
                                + "FROM sale_repair WHERE repair_id = ?", sourceId);
                if (repairs == null || repairs.isEmpty()) return null;
                Map<String, Object> repair = repairs.get(0);
                // 幂等：已回写收款单 → 不再生成
                if (repair.get("receipt_id") != null) return null;
                sourceNo = repair.get("repair_no") == null ? null : String.valueOf(repair.get("repair_no"));
                if (sourceNo == null || sourceNo.isBlank()) return null;
                customerId = toLong(repair.get("customer_id"));
                customerName = repair.get("customer_name") == null ? "" : String.valueOf(repair.get("customer_name"));
                currencyId = null;
                exchangeRate = BigDecimal.ONE;
                BigDecimal totalFee = toDecimal(repair.get("total_fee"));
                BigDecimal rec = writtenOffAmount("售后维修单", sourceId, null);
                BigDecimal remain = totalFee.subtract(rec).max(BigDecimal.ZERO);
                if (remain.compareTo(BigDecimal.ZERO) <= 0) return null;
                rows.add(buildWriteoffRow("售后维修单", sourceId, sourceNo,
                        null, "维修服务费", totalFee, rec, remain));
                break;
            }
            default:
                return null;
        }
        if (rows.isEmpty()) return null;
        // 幂等：同一来源单已有未作废收款单 → 不重复生成草稿
        Long exists = receiptMapper.selectCount(new QueryWrapper<PaymentReceipt>()
                .eq("source_bill_type", sourceType)
                .eq("source_bill_no", sourceNo)
                .ne("status", "已作废"));
        if (exists != null && exists > 0) return null;
        BigDecimal total = rows.stream().map(BillWriteoffDetail::getWriteoffAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = exchangeRate == null ? BigDecimal.ONE : exchangeRate;
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setReceiptDate(LocalDate.now());
        receipt.setCustomerId(customerId);
        receipt.setCustomerName(customerName == null ? "" : customerName);
        receipt.setCurrencyId(currencyId);
        receipt.setExchangeRate(rate);
        receipt.setTotalAmount(total);
        receipt.setAmount(total);
        receipt.setReceivedAmount(total);
        receipt.setDiscountAmount(BigDecimal.ZERO);
        receipt.setSourceType("系统自动生成");
        receipt.setSourceBillType(sourceType);
        receipt.setSourceBillNo(sourceNo);
        receipt.setStatus("草稿");
        receipt.setApprovalStatus("草稿");
        PaymentReceiptItem item = new PaymentReceiptItem();
        item.setAmount(total);
        item.setBaseAmount(total.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
        item.setRemark("系统自动生成（来源：" + sourceNo + "）");
        receipt.setItems(new ArrayList<>(Collections.singletonList(item)));
        receipt.setWriteOffItems(rows);
        PaymentReceipt saved = createReceipt(receipt);
        log.info("按配置生成收款单草稿: {} 来源: {}#{} 金额: {}", saved.getReceiptNo(), sourceType, sourceNo, total);
        // 售后维修单：生成后回写 receipt_id / receipt_no，供维修单列表直达收款单、删除时清理草稿
        if ("售后维修单".equals(sourceType)) {
            jdbcTemplate.update("UPDATE sale_repair SET receipt_id = ?, receipt_no = ? WHERE repair_id = ?",
                    saved.getReceiptId(), saved.getReceiptNo(), sourceId);
        }
        return saved;
    }

    /**
     * 回滚按来源自动生成的收款单草稿（源单反审/作废用）。
     * 草稿直接删除；待审核/已确认抛出业务异常，避免源单已回退而收款单仍在流转。
     */
    @Override
    @Transactional
    public int rollbackAutoReceiptBySource(String sourceType, String sourceNo) {
        if (sourceType == null || sourceNo == null || sourceNo.isBlank()) return 0;
        List<PaymentReceipt> list = receiptMapper.selectList(new QueryWrapper<PaymentReceipt>()
                .eq("source_bill_type", sourceType)
                .eq("source_bill_no", sourceNo)
                .eq("source_type", "系统自动生成")
                .ne("status", "已作废"));
        if (list == null || list.isEmpty()) return 0;
        for (PaymentReceipt r : list) {
            if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(r.getStatus()))) {
                throw new BusinessException("该单据自动生成的收款单[" + r.getReceiptNo() + "]当前状态：" + r.getStatus()
                        + "，请先在财务模块处理该收款单后再操作");
            }
        }
        int removed = 0;
        for (PaymentReceipt r : list) {
            deleteReceipt(r.getReceiptId());
            removed++;
        }
        log.info("回滚自动收款单草稿 {} 张，来源: {}#{}", removed, sourceType, sourceNo);
        return removed;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(String.valueOf(v));
        try {
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    /** 售后维修单合计费用（只读 sale_repair；查不到返回 null） */
    private BigDecimal queryRepairTotalFee(Long repairId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT total_fee FROM sale_repair WHERE repair_id = ?", repairId);
        if (list == null || list.isEmpty()) return null;
        return toDecimal(list.get(0).get("total_fee"));
    }

    private BillWriteoffDetail buildWriteoffRow(String sourceType, Long sourceId, String sourceNo,
                                                Long sourceItemId, String itemName,
                                                BigDecimal sourceAmount, BigDecimal usedAmount, BigDecimal writeoffAmount) {
        BillWriteoffDetail d = new BillWriteoffDetail();
        d.setSourceType(sourceType);
        d.setSourceId(sourceId);
        d.setSourceNo(sourceNo);
        d.setSourceItemId(sourceItemId);
        d.setSourceItemName(itemName);
        d.setSourceAmount(sourceAmount);
        d.setUsedAmount(usedAmount);
        d.setWriteoffAmount(writeoffAmount);
        d.setSettledQty("未冲销");
        return d;
    }

    /** 某来源单/来源明细行已生效的核销金额（销售出库单、销售发票没有已收列，只能按核销明细反算） */
    private BigDecimal writtenOffAmount(String sourceType, Long sourceId, Long sourceItemId) {
        QueryWrapper<BillWriteoffDetail> q = new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "收款单")
                .eq("source_type", sourceType)
                .eq("source_id", sourceId)
                .eq("settled_qty", "已冲销")
                .gt("writeoff_amount", 0);
        if (sourceItemId == null) q.isNull("source_item_id");
        else q.eq("source_item_id", sourceItemId);
        List<BillWriteoffDetail> list = billWriteoffDetailMapper.selectList(q);
        BigDecimal sum = BigDecimal.ZERO;
        if (list != null) for (BillWriteoffDetail d : list) sum = sum.add(money(d.getWriteoffAmount()));
        return sum;
    }

    @Override
    public List<Map<String, Object>> getWriteoffReceivedBySource(String sourceTypes) {
        List<String> types = new ArrayList<>();
        if (sourceTypes != null) {
            for (String t : sourceTypes.split(",")) {
                if (t != null && !t.isBlank()) types.add(t.trim());
            }
        }
        if (types.isEmpty()) return Collections.emptyList();
        List<BillWriteoffDetail> list = billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "收款单")
                .eq("settled_qty", "已冲销")
                .gt("writeoff_amount", 0)
                .in("source_type", types));
        List<Map<String, Object>> out = new ArrayList<>();
        if (list == null) return out;
        for (BillWriteoffDetail d : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("sourceType", d.getSourceType());
            m.put("sourceId", d.getSourceId());
            m.put("sourceItemId", d.getSourceItemId());
            m.put("amount", money(d.getWriteoffAmount()));
            out.add(m);
        }
        return out;
    }

    /**
     * 某来源单类型下「存在未作废收款单」的来源单号集合（收款单选单候选排除用）。
     * 判定口径：cash_receipt.source_bill_type = 来源类型 AND source_bill_no = 来源单号 AND status <> '已作废'。
     * 只看收款单自身状态，不看来源单上回写的收款单主键 —— 收款单作废后来源单必须重新可选。
     */
    @Override
    public List<String> listActiveReceiptSourceNos(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return Collections.emptyList();
        List<PaymentReceipt> list = receiptMapper.selectList(new QueryWrapper<PaymentReceipt>()
                .select("DISTINCT source_bill_no")
                .eq("source_bill_type", sourceType)
                .ne("status", "已作废")
                .isNotNull("source_bill_no"));
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (PaymentReceipt r : list) {
            String no = r.getSourceBillNo();
            if (no != null && !no.isBlank() && !out.contains(no)) out.add(no);
        }
        return out;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 收款单销售提成兜底计算（2026-10-16 全行级口径，用户拍板：一切提成字段在核销明细行，表头不展示）。
     * 公式（与前端实时计算同尺子，保存时以后端为准）：
     *   行提成比例     = 行级可改（空回落单头，再回落设置中心默认）
     *   行销售提成     = (行不含税收款 − 行特殊服务费) × 行提成比例%（两位小数四舍五入）
     *   表头合计列     = Σ行（不含税 / 特殊服务费 / 销售提成），仅随主表落库供列表与导出，界面不展示
     *   表头提成比例空 → 取设置中心「销售提成默认比例」（取不到兜底 3），同时作为空比例行的回落值
     */
    private void applyReceiptCommissionCalc(PaymentReceipt receipt) {
        if (receipt == null) return;
        BigDecimal headerRate = receipt.getCommissionRate();
        if (headerRate == null || headerRate.compareTo(BigDecimal.ZERO) <= 0) {
            String cfg = sysConfigService.getValue("finance.commission.default.rate", "3");
            BigDecimal cfgRate;
            try {
                cfgRate = cfg == null || cfg.isBlank() ? new BigDecimal("3") : new BigDecimal(cfg.trim());
            } catch (NumberFormatException e) {
                cfgRate = new BigDecimal("3");
            }
            headerRate = cfgRate;
            receipt.setCommissionRate(headerRate);
        }
        BigDecimal taxExcludedSum = BigDecimal.ZERO;
        BigDecimal specialSum = BigDecimal.ZERO;
        BigDecimal commissionSum = BigDecimal.ZERO;
        if (receipt.getWriteOffItems() != null) {
            for (BillWriteoffDetail w : receipt.getWriteOffItems()) {
                if (w == null || "预收款单".equals(w.getSourceType())) continue; // 预收冲抵不参与提成
                BigDecimal lineTax = w.getTaxExcludedAmount() == null ? BigDecimal.ZERO : w.getTaxExcludedAmount();
                BigDecimal lineSpecial = w.getSpecialServiceFee() == null ? BigDecimal.ZERO : w.getSpecialServiceFee();
                BigDecimal lineRate = w.getCommissionRate();
                if (lineRate == null || lineRate.compareTo(BigDecimal.ZERO) <= 0) {
                    lineRate = headerRate;
                    w.setCommissionRate(lineRate);
                }
                BigDecimal lineCommission = lineTax.subtract(lineSpecial).multiply(lineRate)
                        .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                w.setCommission(lineCommission);
                taxExcludedSum = taxExcludedSum.add(lineTax);
                specialSum = specialSum.add(lineSpecial);
                commissionSum = commissionSum.add(lineCommission);
            }
        }
        receipt.setTaxExcludedReceivedAmount(taxExcludedSum);
        receipt.setSpecialServiceFee(specialSum);
        receipt.setSalesCommission(commissionSum);
    }

    /** 保存收款明细（先删后插，草稿可反复编辑） */
    private void saveReceiptItems(Long receiptId, List<PaymentReceiptItem> items) {        if (receiptId == null) return;
        receiptItemMapper.delete(new QueryWrapper<PaymentReceiptItem>().eq("receipt_id", receiptId));
        if (items == null || items.isEmpty()) return;
        int idx = 0;
        for (PaymentReceiptItem it : items) {
            it.setItemId(null);
            it.setReceiptId(receiptId);
            if (it.getSortNo() == null) it.setSortNo(idx);
            idx++;
            receiptItemMapper.insert(it);
        }
    }

    /** 保存付款明细（先删后插，草稿可反复编辑） */
    private void saveVoucherItems(Long paymentId, List<PaymentVoucherItem> items) {
        if (paymentId == null) return;
        voucherItemMapper.delete(new QueryWrapper<PaymentVoucherItem>().eq("payment_id", paymentId));
        if (items == null || items.isEmpty()) return;
        int idx = 0;
        for (PaymentVoucherItem it : items) {
            it.setItemId(null);
            it.setPaymentId(paymentId);
            if (it.getSortNo() == null) it.setSortNo(idx);
            idx++;
            voucherItemMapper.insert(it);
        }
    }

    @Override
    @Transactional
    public void submitReceipt(Long id, String submittedBy) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        String st = receipt.getStatus();
        if (!"草稿".equals(st) && !"DRAFT".equals(st) && !"待确认".equals(st)) {
            throw new BusinessException("只有草稿状态可提交，当前状态: " + st);
        }
        if (approvalGateHelper.needApproval("PAYMENT_RECEIPT")) {
            // 开启审批：落「待审核」并进审批引擎建待办（无可用审批流程时 startApproval 抛错 → 整事务回滚，单据留草稿）
            receipt.setStatus("待审核");
            receipt.setApprovalStatus("待审核");
            receiptMapper.updateById(receipt);
            billApprovalAdapter.submitToApproval("PAYMENT_RECEIPT", receipt.getReceiptId(), receipt.getReceiptNo(),
                    submittedBy != null && !submittedBy.isBlank() ? submittedBy : BillApprovalAdapter.currentSubmitter(), receipt);
            log.info("收款单提交审批并生成审批待办: {}", receipt.getReceiptNo());
        } else {
            // 免审批：提交即生效
            confirmReceipt(id, "系统(免审批)");
        }
    }

    @Override
    public PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        return pageReceipt(current, size, customerName, status, receiptNo, null, null, null, scopeCtx, sortBy, sortOrder, null, null);
    }

    @Override
    public PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo, String keyword, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String startDate, String endDate) {
        QueryWrapper<PaymentReceipt> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用原默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, RECEIPT_SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PaymentReceipt.class);
        // 关键词：单号 + 客户名称 多列匹配，强制成组 AND (receipt_no LIKE ? OR customer_name LIKE ?)
        // （原来是 customerName / receiptNo 两条独立 like 且默认 AND → 前端把同一个词同时塞进两个参数，几乎永远搜不到；
        //   而平铺 OR 又会让命中单号的行绕过状态筛选与数据权限，故统一走 applyKeywordGroup）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "customer_name");
        if (keyword == null || keyword.isBlank()) {
            // 兼容旧前端/旧链接：keyword 未传时仍支持 customerName、receiptNo 两个旧参数（各一条 like，保持原语义）
            if (customerName != null && !customerName.isBlank()) wrapper.like("customer_name", customerName);
            if (receiptNo != null && !receiptNo.isBlank()) wrapper.like("receipt_no", receiptNo);
        }
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        // 收款日期区间（数据看板"经营现金流"穿透 + 通用搜索栏日期维度）
        // 注意：receipt_date 是 DATE 列，applyDateRange 内部绑定 LocalDate，直接绑字符串会报 date >= character varying
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        // 客户维度（统一入参 customerId → 本页真实列 customer_id）
        ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        // 员工维度（统一入参 employeeId → 本页真实列 cashier_id，业务口径「出纳」）
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<PaymentReceipt> page = new Page<>(current, size);
        Page<PaymentReceipt> result = receiptMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo, String keyword, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String startDate, String endDate, Map<String, String> headerFilters) {
        QueryWrapper<PaymentReceipt> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, RECEIPT_SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PaymentReceipt.class);
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "customer_name");
        if (keyword == null || keyword.isBlank()) {
            if (customerName != null && !customerName.isBlank()) wrapper.like("customer_name", customerName);
            if (receiptNo != null && !receiptNo.isBlank()) wrapper.like("receipt_no", receiptNo);
        }
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "paymentReceipt", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<PaymentReceipt> page = new Page<>(current, size);
        Page<PaymentReceipt> result = receiptMapper.selectPage(page, wrapper);
        List<PaymentReceipt> records = result.getRecords();
        fillReceiptSourceFromWriteoff(records);
        return PageResult.of(records, result.getTotal(), current, size);
    }

    /**
     * 列表「来源单号 / 来源单据类型」以核销明细行为准（2026-10-28）：
     * 手动选来源单的收款只写核销明细、不写表头，列表不能再读表头导致显示空白；
     * 多个来源按"、"拼接；核销明细没有来源时才保留表头历史值。
     */
    private void fillReceiptSourceFromWriteoff(List<PaymentReceipt> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = new java.util.ArrayList<>();
        for (PaymentReceipt r : records) if (r.getReceiptId() != null) ids.add(r.getReceiptId());
        if (ids.isEmpty()) return;
        List<BillWriteoffDetail> rows = billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "收款单").in("bill_id", ids));
        Map<Long, java.util.LinkedHashSet<String>> nos = new HashMap<>();
        Map<Long, java.util.LinkedHashSet<String>> types = new HashMap<>();
        for (BillWriteoffDetail d : rows) {
            if (d.getBillId() == null || d.getSourceNo() == null || d.getSourceNo().isBlank()) continue;
            nos.computeIfAbsent(d.getBillId(), k -> new java.util.LinkedHashSet<>()).add(d.getSourceNo());
            if (d.getSourceType() != null && !d.getSourceType().isBlank()) {
                types.computeIfAbsent(d.getBillId(), k -> new java.util.LinkedHashSet<>()).add(d.getSourceType());
            }
        }
        for (PaymentReceipt r : records) {
            java.util.LinkedHashSet<String> n = nos.get(r.getReceiptId());
            if (n == null || n.isEmpty()) continue;
            r.setSourceBillNo(String.join("、", n));
            java.util.LinkedHashSet<String> ts = types.get(r.getReceiptId());
            if (ts != null && !ts.isEmpty()) r.setSourceBillType(String.join("、", ts));
        }
    }

    @Override
    @Transactional
    public void confirmReceipt(Long id, String confirmedBy) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款记录不存在");
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        // （由引擎回调 onApprovalApproved 触发的审核不受影响：引擎会先把任务置为已通过再回写单据）
        if (approvalRuntimeService.getRunningTask("PAYMENT_RECEIPT", id) != null) {
            throw new BusinessException("收款单已提交审批流程，请由指定审批人在审批中心处理");
        }
        String st = receipt.getStatus();
        if (!"草稿".equals(st) && !"DRAFT".equals(st) && !"待审核".equals(st) && !"PENDING".equals(st) && !"待确认".equals(st)) {
            throw new BusinessException("只有草稿或待审核状态可审核，当前状态: " + st);
        }
        if (receipt.getCloseStatus() != null && receipt.getCloseStatus() == 1) {
            throw new BusinessException("收款单[" + receipt.getReceiptNo() + "]已结案，不能审核");
        }

        // 装载明细 → 金额校验（核销需求不得超过可用资金：实收现金 + 预收款冲抵）
        loadReceiptDetails(receipt);
        validateReceiptAmount(receipt);

        receipt.setStatus("已确认");
        receipt.setApprovalStatus("已生效");
        // 免审批自动通过时 confirmedBy 为系统占位 → 审核人留空（用户 2026-10-28 口径）
        receipt.setApprovedBy(BillStatusUtils.normalizeApprover(confirmedBy));
        receipt.setApprovedAt(java.time.LocalDateTime.now());
        receiptMapper.updateById(receipt);

        applyReceiptWriteback(receipt, confirmedBy);

        // 多收转预收：现金结余自动生成草稿销售预收款单（幂等）
        createOveragePreReceipt(receipt, confirmedBy);

        log.info("收款确认: {}", receipt.getReceiptNo());
    }

    /** 装载收款单明细（收款明细 + 核销明细），用于金额校验与多收计算 */
    private void loadReceiptDetails(PaymentReceipt receipt) {
        receipt.setItems(receiptItemMapper.selectList(
                new QueryWrapper<PaymentReceiptItem>().eq("receipt_id", receipt.getReceiptId())));
        receipt.setWriteOffItems(billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "收款单").eq("bill_id", receipt.getReceiptId())));
    }

    /**
     * 收款金额三元组：ret[0]=实收现金(items合计) ret[1]=预收款单冲抵合计 ret[2]=核销需求(销售订单+应收单)。
     * 预收款单冲抵是"用已有预收款抵账"，不消耗本次现金，故计入可用资金而非需求。
     */
    private BigDecimal[] receiptAmounts(PaymentReceipt receipt) {
        BigDecimal cash = BigDecimal.ZERO;
        if (receipt.getItems() != null) {
            for (PaymentReceiptItem it : receipt.getItems()) {
                if (it.getAmount() != null) cash = cash.add(it.getAmount());
            }
        }
        BigDecimal preOffset = BigDecimal.ZERO;
        BigDecimal demand = BigDecimal.ZERO;
        if (receipt.getWriteOffItems() != null) {
            for (BillWriteoffDetail d : receipt.getWriteOffItems()) {
                BigDecimal amt = d.getWriteoffAmount() == null ? BigDecimal.ZERO : d.getWriteoffAmount();
                if ("预收款单".equals(d.getSourceType())) preOffset = preOffset.add(amt);
                else demand = demand.add(amt);
            }
        }
        return new BigDecimal[]{cash, preOffset, demand};
    }

    /** 收款金额校验：核销需求 ≤ 实收现金 + 预收款冲抵 */
    private void validateReceiptAmount(PaymentReceipt receipt) {
        BigDecimal[] a = receiptAmounts(receipt);
        BigDecimal available = a[0].add(a[1]);
        if (a[2].compareTo(available.add(new BigDecimal("0.01"))) > 0) {
            throw new BusinessException(String.format(
                    "核销金额超过收款可用金额：可用(实收%.2f + 预收冲抵%.2f)=%.2f，需核销%.2f",
                    a[0], a[1], available, a[2]));
        }
    }

    /** 多收转预收：现金结余 = 实收现金 - 现金实际使用(核销需求-预收冲抵) > 0 时生成草稿销售预收款单（幂等） */
    private void createOveragePreReceipt(PaymentReceipt receipt, String operator) {
        BigDecimal[] a = receiptAmounts(receipt);
        BigDecimal cashUsed = a[2].subtract(a[1]).max(BigDecimal.ZERO);
        BigDecimal overage = a[0].subtract(cashUsed).setScale(2, java.math.RoundingMode.HALF_UP);
        if (overage.compareTo(new BigDecimal("0.01")) < 0) return;
        // 幂等：同一收款单已转入过则跳过
        Long exist = salePreReceiptService.count(new QueryWrapper<SalePreReceipt>()
                .eq("source_type", "PAYMENT_RECEIPT_OVERAGE")
                .eq("source_bill_id", receipt.getReceiptId()));
        if (exist != null && exist > 0) return;
        if (!sysSerialNoConfigService.isAutoGenerate("SALE_PRE_RECEIPT")) {
            log.warn("销售预收款单已关闭自动编号，收款单[{}]多收{}未自动转预收", receipt.getReceiptNo(), overage);
            return;
        }
        SalePreReceipt p = new SalePreReceipt();
        p.setCustomerId(receipt.getCustomerId());
        p.setCustomerName(receipt.getCustomerName());
        p.setReceiptDate(LocalDate.now());
        p.setAmount(overage);
        BigDecimal rate = receipt.getExchangeRate() != null ? receipt.getExchangeRate() : BigDecimal.ONE;
        p.setExchangeRate(rate);
        p.setBaseAmount(overage.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
        p.setUsedAmount(BigDecimal.ZERO);
        p.setRemainAmount(overage);
        p.setStatus(0);
        p.setSourceType("PAYMENT_RECEIPT_OVERAGE");
        p.setSourceBillId(receipt.getReceiptId());
        p.setRemark("收款单[" + receipt.getReceiptNo() + "]多收自动转入");
        // 明细必须与主表一起落库：savePreReceipt 只会落库 items，不造明细则打开预收款单「收款明细」为空白
        SalePreReceiptItem overageItem = new SalePreReceiptItem();
        overageItem.setAmount(overage);
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            PaymentReceiptItem first = receipt.getItems().get(0);
            overageItem.setAccountId(first.getAccountId());
            overageItem.setAccountName(first.getAccountName());
            overageItem.setAccountNo(first.getAccountNo() != null ? first.getAccountNo() : first.getBankAccount());
        }
        List<SalePreReceiptItem> overageItems = new java.util.ArrayList<>();
        overageItems.add(overageItem);
        p.setItems(overageItems);
        salePreReceiptService.savePreReceipt(p);
        log.info("收款单[{}]多收{}自动生成草稿销售预收款单[{}]", receipt.getReceiptNo(), overage, p.getReceiptNo());
    }

    /** 保存收款单/付款单草稿时落库核销来源明细（先删后插，草稿可反复编辑） */
    private void saveWriteoffDetails(String billType, Long billId, String billNo, List<BillWriteoffDetail> details) {
        if (billId == null) return;
        billWriteoffDetailMapper.delete(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", billType).eq("bill_id", billId));
        if (details == null || details.isEmpty()) return;
        for (BillWriteoffDetail d : details) {
            // 已选但「本次核销金额」暂为 0 的行是合法中间态（选单后由收款明细自动向下核销，可能仍有行未被分配），
            // 必须原样保留；此前按 0 元跳过 + 先删后插，导致用户选好的核销明细在保存/提交后整体丢失（2026-10-29 修复）。
            if (d.getWriteoffAmount() == null) d.setWriteoffAmount(BigDecimal.ZERO);
            d.setWriteoffId(null);
            d.setBillType(billType);
            d.setBillId(billId);
            d.setBillNo(billNo);
            if (d.getWriteoffDate() == null) d.setWriteoffDate(LocalDate.now());
            if (d.getSettledQty() == null || d.getSettledQty().isBlank()) d.setSettledQty("未冲销");
            if (d.getWaiveRemainder() == null) d.setWaiveRemainder(0);
            // 已冲销的行保留冲销时间（避免重存后丢失）
            if ("已冲销".equals(d.getSettledQty()) && d.getSettledAt() == null) {
                d.setSettledAt(java.time.LocalDateTime.now());
            }
            billWriteoffDetailMapper.insert(d);
        }
    }

    /** 按来源销售订单号查询收款记录（2026-10-12 订单列表「收款详情」）：直接订单收款 + 发货路径（沿应收单来源订单号）已冲销核销行 */
    @Override
    public List<Map<String, Object>> listReceiptsBySourceOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return Collections.emptyList();
        List<BillWriteoffDetail> rows = new ArrayList<>();
        // ① 直接订单收款（汇总/明细行）
        rows.addAll(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "收款单").eq("settled_qty", "已冲销")
                .eq("source_type", "销售订单").eq("source_no", orderNo)));
        // ② 发货收款：该订单所有应收单（来源订单号关联）的核销行
        List<AccountsReceivable> ars = arMapper.selectList(
                new QueryWrapper<AccountsReceivable>().eq("source_order_no", orderNo));
        if (!ars.isEmpty()) {
            Set<String> arNos = new HashSet<>();
            for (AccountsReceivable a : ars) arNos.add(a.getArNo());
            rows.addAll(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                    .eq("bill_type", "收款单").eq("settled_qty", "已冲销")
                    .eq("source_type", "应收单").in("source_no", arNos)));
        }
        rows.sort(Comparator.comparing(BillWriteoffDetail::getWriteoffDate,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<Map<String, Object>> result = new ArrayList<>();
        for (BillWriteoffDetail d : rows) {
            boolean viaAr = "应收单".equals(d.getSourceType());
            Map<String, Object> m = new HashMap<>();
            m.put("receiptNo", d.getBillNo());
            m.put("writeoffDate", d.getWriteoffDate());
            m.put("amount", d.getWriteoffAmount());
            m.put("path", viaAr ? "发货收款" : "订单收款");
            m.put("itemName", d.getSourceItemName());
            m.put("arNo", viaAr ? d.getSourceNo() : null);
            result.add(m);
        }
        return result;
    }

    /**
     * 收款确认后的业务回写（正向）：按核销明细分发（预收款单/销售订单/应收单）。
     * 供 confirmReceipt 与 unvoidReceipt（恢复已确认态）复用，与 rollbackReceiptWriteback 对称。
     */
    private void applyReceiptWriteback(PaymentReceipt receipt, String operator) {
        List<BillWriteoffDetail> details = billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "收款单").eq("bill_id", receipt.getReceiptId()));
        if (details == null || details.isEmpty()) return;
        for (BillWriteoffDetail d : details) {
            applyWriteoffDetail(d, operator);
        }
    }

    /**
     * 执行单行核销（收款单/付款单通用）：已冲销的行跳过（幂等，避免重复核销）；
     * waive_remainder=1 时对来源单做"余数不收"结案（不再纳入后续收/付款候选）。
     */
    private void applyWriteoffDetail(BillWriteoffDetail d, String operator) {
        BigDecimal amount = d.getWriteoffAmount() == null ? BigDecimal.ZERO : d.getWriteoffAmount();
        boolean isReceipt = !"付款单".equals(d.getBillType());
        if (amount.compareTo(BigDecimal.ZERO) > 0 && !"已冲销".equals(d.getSettledQty())) {
            // 按来源单当前可核销余额封顶：防止重复核销/超核销把单据卡在草稿（如来源单已被其它单据核销完）
            // 明细收款行（带来源明细行）按「行级未收余额」封顶（2026-10-12）
            BigDecimal cap = sourceRemainingCapacity(d);
            BigDecimal apply = amount.min(cap);
            if (apply.compareTo(amount) < 0) {
                log.warn("核销金额超出来源单可核销余额，按余额封顶: {}#{}(billId={}) 申请={} 可用={}",
                        d.getSourceType(), d.getSourceId(), d.getBillId(), amount, cap);
            }
            if (apply.compareTo(BigDecimal.ZERO) > 0) {
                if (isReceipt) {
                    switch (d.getSourceType()) {
                        case "预收款单":
                            SalePreReceiptItem item = new SalePreReceiptItem();
                            item.setSourceId(d.getBillId());
                            item.setSourceNo(d.getBillNo());
                            item.setSourceType("payment_receipt");
                            item.setAmount(apply);
                            salePreReceiptService.writeOff(d.getSourceId(), Collections.singletonList(item));
                            break;
                        case "销售订单":
                            // 明细收款（2026-10-12）：带来源明细行 → 行级已收 + 表头已收同步累加；否则整单汇总收款
                            if (d.getSourceItemId() != null) {
                                applySaleOrderItemWriteoff(d.getSourceItemId(), apply);
                            } else {
                                applySaleOrderWriteoff(d.getSourceId(), apply);
                                // 【2026-10-14 完结口径可配置】汇总收款按订单行净额占比分摊到行级已收
                                // （「销售发货+销售发票+收款单」完结模式按行级"已收金额 ≥ 行价税合计"判定）
                                SaleOrder so4alloc = saleOrderService.getById(d.getSourceId());
                                if (so4alloc != null) allocateOrderItemReceived(so4alloc, null, apply, true);
                            }
                            break;
                        case "应收单":
                            applyArWriteoff(d.getSourceId(), apply);
                            break;
                        case "销售出库单":
                        case "销售发票":
                            // 2026-11-28：出库单/发票本身没有「已收金额」列，但订单列表的“收款情况”需要看到。
                            // 沿「发票→出库明细→订单明细」两跳回写订单已收，订单列表/完结口径即可同步。
                            for (Map.Entry<Long, BigDecimal> e : splitReceiptToOrderItems(d, apply).entrySet()) {
                                applySaleOrderItemWriteoff(e.getKey(), e.getValue());
                            }
                            log.info("收款核销{}: 来源单#{} 明细行={} 本次={}",
                                    d.getSourceType(), d.getSourceId(), d.getSourceItemId(), apply);
                            break;
                        case "售后维修单":
                            // 维修单无已收列：核销事实以 bill_writeoff_detail 为准，不回写来源单
                            log.info("收款核销{}: 来源单#{} 明细行={} 本次={}",
                                    d.getSourceType(), d.getSourceId(), d.getSourceItemId(), apply);
                            break;
                        default:
                            log.warn("收款核销未知来源类型: {}", d.getSourceType());
                    }
                } else {
                    switch (d.getSourceType()) {
                        case "预付款单":
                            PurchasePrePaymentItem pit = new PurchasePrePaymentItem();
                            pit.setSourceId(d.getBillId());
                            pit.setSourceNo(d.getBillNo());
                            pit.setSourceType("payment_voucher");
                            pit.setAmount(apply);
                            purchasePrePaymentService.writeOff(d.getSourceId(), Collections.singletonList(pit));
                            break;
                        case "应付单":
                            AccountsPayable ap = apMapper.selectById(d.getSourceId());
                            if (ap != null) applyApPayment(ap, apply);
                            break;
                        default:
                            log.warn("付款核销未知来源类型: {}", d.getSourceType());
                    }
                }
            }
            // 落库实际生效金额（封顶后），保证多收/多付计算与来源单余额一致
            d.setWriteoffAmount(apply);
            d.setSettledQty("已冲销");
            d.setSettledAt(java.time.LocalDateTime.now());
        }
        if (d.getWaiveRemainder() != null && d.getWaiveRemainder() == 1) {
            waiveSourceRemainder(d.getSourceType(), d.getSourceId(), operator);
        }
        if (d.getWriteoffId() != null) billWriteoffDetailMapper.updateById(d);
    }

    /**
     * 来源单当前可核销余额（确认/冲销前校验用）：
     * 预收款单/预付款单取 remain_amount 且状态必须为已生效(2)；销售订单取 未收金额；应收/应付单取 balance_amount。
     * 返回 0 表示该来源单已无可核销额度（已被其它单据核销完/已作废），本次核销应跳过而非报错。
     */
    /** 单行核销的来源单可核销余额（2026-10-12）：明细收款行按「行级未收余额」封顶，其余按来源单余额 */
    private BigDecimal sourceRemainingCapacity(BillWriteoffDetail d) {
        if (d == null) return BigDecimal.ZERO;
        if ("销售订单".equals(d.getSourceType()) && d.getSourceItemId() != null) {
            try {
                SaleOrderItem item = saleOrderItemMapper.selectById(d.getSourceItemId());
                if (item == null) return BigDecimal.ZERO;
                BigDecimal lineNet = item.getNetAmount() == null ? BigDecimal.ZERO : item.getNetAmount();
                BigDecimal lineRec = item.getReceivedAmount() == null ? BigDecimal.ZERO : item.getReceivedAmount();
                return lineNet.subtract(lineRec).max(BigDecimal.ZERO);
            } catch (Exception ex) {
                log.warn("取订单明细行可核销余额失败: 行ID={} err={}", d.getSourceItemId(), ex.getMessage());
                return BigDecimal.ZERO;
            }
        }
        // 销售出库单 / 销售发票：明细行可收余额 = 行价税合计 − 该行已生效核销金额
        if (("销售出库单".equals(d.getSourceType()) || "销售发票".equals(d.getSourceType()))
                && d.getSourceItemId() != null) {
            try {
                BigDecimal lineNet;
                if ("销售出库单".equals(d.getSourceType())) {
                    SaleDeliveryItem item = saleDeliveryItemMapper.selectById(d.getSourceItemId());
                    if (item == null) return BigDecimal.ZERO;
                    lineNet = item.getNetAmount() == null ? BigDecimal.ZERO : item.getNetAmount();
                } else {
                    SaleInvoiceItem item = saleInvoiceItemMapper.selectById(d.getSourceItemId());
                    if (item == null) return BigDecimal.ZERO;
                    lineNet = item.getNetAmount() == null ? BigDecimal.ZERO : item.getNetAmount();
                }
                BigDecimal written = writtenOffAmount(d.getSourceType(), d.getSourceId(), d.getSourceItemId());
                return lineNet.subtract(written).max(BigDecimal.ZERO);
            } catch (Exception ex) {
                log.warn("取来源明细行可核销余额失败: {} 行ID={} err={}",
                        d.getSourceType(), d.getSourceItemId(), ex.getMessage());
                return BigDecimal.ZERO;
            }
        }
        return sourceRemainingCapacity(d.getSourceType(), d.getSourceId());
    }

    /**
     * 收款核销来源为出库单/发票时，把核销金额拆分到销售订单明细（2026-11-28 补）。
     * 正常链路是「订单→出库→发票」，发票/出库明细的 source_item_id 指向出库明细，必须两跳回溯到订单明细。
     * 无订单来源（手工出库/发票）返回空，保持原有只记核销明细的行为。
     */
    private Map<Long, BigDecimal> splitReceiptToOrderItems(BillWriteoffDetail d, BigDecimal amount) {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        if (d == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return shares;
        if ("销售出库单".equals(d.getSourceType())) {
            if (d.getSourceItemId() != null) {
                Long orderItemId = orderItemIdOfDeliveryItem(saleDeliveryItemMapper.selectById(d.getSourceItemId()));
                if (orderItemId != null) shares.put(orderItemId, amount);
                return shares;
            }
            List<SaleDeliveryItem> items = saleDeliveryItemMapper.selectList(
                    new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", d.getSourceId()));
            List<Long> ids = new ArrayList<>();
            List<BigDecimal> nets = new ArrayList<>();
            if (items != null) {
                for (SaleDeliveryItem it : items) {
                    Long orderItemId = orderItemIdOfDeliveryItem(it);
                    if (orderItemId != null) {
                        ids.add(orderItemId);
                        nets.add(it.getNetAmount() == null ? BigDecimal.ZERO : it.getNetAmount());
                    }
                }
            }
            return allocateReceiptByNet(ids, nets, amount);
        }
        if ("销售发票".equals(d.getSourceType())) {
            if (d.getSourceItemId() != null) {
                Long orderItemId = orderItemIdOfInvoiceItem(saleInvoiceItemMapper.selectById(d.getSourceItemId()));
                if (orderItemId != null) shares.put(orderItemId, amount);
                return shares;
            }
            List<SaleInvoiceItem> items = saleInvoiceItemMapper.selectList(
                    new QueryWrapper<SaleInvoiceItem>().eq("invoice_id", d.getSourceId()));
            List<Long> ids = new ArrayList<>();
            List<BigDecimal> nets = new ArrayList<>();
            if (items != null) {
                for (SaleInvoiceItem it : items) {
                    Long orderItemId = orderItemIdOfInvoiceItem(it);
                    if (orderItemId != null) {
                        ids.add(orderItemId);
                        nets.add(it.getNetAmount() == null ? BigDecimal.ZERO : it.getNetAmount());
                    }
                }
            }
            return allocateReceiptByNet(ids, nets, amount);
        }
        return shares;
    }

    /** 出库明细 → 订单明细（仅当来源确为销售订单） */
    private Long orderItemIdOfDeliveryItem(SaleDeliveryItem di) {
        if (di == null || di.getSourceItemId() == null) return null;
        String type = di.getSourceBillType();
        if (type != null && type.contains("销售订单")) return di.getSourceItemId();
        return null;
    }

    /** 发票明细 → 订单明细：直接订单来源，或经出库明细两跳回溯 */
    private Long orderItemIdOfInvoiceItem(SaleInvoiceItem ii) {
        if (ii == null || ii.getSourceItemId() == null) return null;
        String type = ii.getSourceBillType();
        if (type == null) return null;
        if (type.contains("销售订单")) return ii.getSourceItemId();
        if (type.contains("销售出库")) {
            return orderItemIdOfDeliveryItem(saleDeliveryItemMapper.selectById(ii.getSourceItemId()));
        }
        return null;
    }

    /** 按明细行价税合计占比拆分收款金额（末行吃尾差） */
    private Map<Long, BigDecimal> allocateReceiptByNet(List<Long> orderItemIds, List<BigDecimal> nets, BigDecimal amount) {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        if (orderItemIds == null || orderItemIds.isEmpty()) return shares;
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal net : nets) {
            if (net != null && net.compareTo(BigDecimal.ZERO) > 0) total = total.add(net);
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) return shares;
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < orderItemIds.size(); i++) {
            BigDecimal net = nets.get(i) == null ? BigDecimal.ZERO : nets.get(i);
            BigDecimal share = (i == orderItemIds.size() - 1)
                    ? amount.subtract(allocated)
                    : amount.multiply(net).divide(total, 2, java.math.RoundingMode.HALF_UP);
            if (share.compareTo(BigDecimal.ZERO) > 0) {
                shares.merge(orderItemIds.get(i), share, BigDecimal::add);
                allocated = allocated.add(share);
            }
        }
        return shares;
    }

    /** 收款回退：按订单明细回减已收（行封底 0，表头同步回减并重算完结） */
    private void reverseReceiptWritebackToOrder(BillWriteoffDetail d, BigDecimal amount, String action) {
        Map<Long, BigDecimal> shares = splitReceiptToOrderItems(d, amount);
        if (shares.isEmpty()) return;
        BigDecimal total = BigDecimal.ZERO;
        Long orderId = null;
        for (Map.Entry<Long, BigDecimal> e : shares.entrySet()) {
            SaleOrderItem item = saleOrderItemMapper.selectById(e.getKey());
            if (item == null) continue;
            if (orderId == null) orderId = item.getOrderId();
            BigDecimal lineRec = item.getReceivedAmount() == null ? BigDecimal.ZERO : item.getReceivedAmount();
            item.setReceivedAmount(lineRec.subtract(e.getValue()).max(BigDecimal.ZERO));
            saleOrderItemMapper.updateById(item);
            total = total.add(e.getValue());
            log.info("收款{}回减订单行: 行ID={}, 货品={}, 回减后行已收={}",
                    action, item.getOrderItemId(), item.getMaterialName(), item.getReceivedAmount());
        }
        if (orderId == null || total.compareTo(BigDecimal.ZERO) <= 0) return;
        SaleOrder order = saleOrderService.getById(orderId);
        if (order == null) return;
        BigDecimal currentReceived = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
        order.setReceivedAmount(currentReceived.subtract(total).max(BigDecimal.ZERO));
        saleOrderService.updateById(order);
        try {
            saleOrderService.recalcOrderCloseStatus(orderId);
        } catch (Exception ex) {
            log.warn("收款回减后完结重算失败: 订单={} err={}", order.getOrderNo(), ex.getMessage());
        }
    }

    /**
     * 明细收款回写（2026-10-12）：累加订单明细行已收金额 + 表头已收同步累加（沿用防超收校验）。     * 行级封顶已由 {@link #sourceRemainingCapacity(BillWriteoffDetail)} 完成。
     */
    private void applySaleOrderItemWriteoff(Long orderItemId, BigDecimal amount) {
        SaleOrderItem item = saleOrderItemMapper.selectById(orderItemId);
        if (item == null) throw new BusinessException("销售订单明细行不存在: " + orderItemId);
        BigDecimal lineRec = item.getReceivedAmount() == null ? BigDecimal.ZERO : item.getReceivedAmount();
        item.setReceivedAmount(lineRec.add(amount));
        saleOrderItemMapper.updateById(item);
        // 表头同步累加（防超收：超过订单金额会抛错，两条路径共享同一上限）
        applySaleOrderWriteoff(item.getOrderId(), amount);
        log.info("明细收款回写订单行: 行ID={}, 货品={}, 本次={}, 行累计已收={}",
                orderItemId, item.getMaterialName(), amount, item.getReceivedAmount());
    }

    /**
     * 发货收款回写销售订单（2026-10-12）：沿「应收单→来源订单号」关联，
     * 表头已收累加（超出订单金额按剩余可收封顶，不阻断收款确认），
     * 并把金额分摊到订单明细行已收（优先按出库明细行精确对应，无出库行时按订单行净额占比）。
     */
    private void writeBackArReceiptToOrder(AccountsReceivable ar, BigDecimal amount) {
        try {
            String orderNo = ar.getSourceOrderNo();
            if (orderNo == null || orderNo.isBlank() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
            SaleOrder order = saleOrderService.getOne(new QueryWrapper<SaleOrder>().eq("order_no", orderNo));
            if (order == null) {
                log.warn("发货收款回写订单失败: 应收单={} 来源订单号={} 未找到订单", ar.getArNo(), orderNo);
                return;
            }
            // 表头回写：按订单剩余可收封顶
            BigDecimal orderNet = order.getNetAmount() == null ? BigDecimal.ZERO : order.getNetAmount();
            BigDecimal received = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
            BigDecimal headroom = orderNet.subtract(received).max(BigDecimal.ZERO);
            BigDecimal apply = amount.min(headroom);
            if (apply.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("发货收款回写订单跳过: 订单={} 已收满(已收={}, 订单额={})", order.getOrderNo(), received, orderNet);
                return;
            }
            if (apply.compareTo(amount) < 0) {
                log.warn("发货收款回写订单超出订单未收余额，按余额封顶: 订单={} 申请={} 可收={}", order.getOrderNo(), amount, headroom);
            }
            order.setReceivedAmount(received.add(apply));
            saleOrderService.updateById(order);
            log.info("发货收款回写订单: 应收单={} 订单={} 本次={} 累计已收={}", ar.getArNo(), order.getOrderNo(), apply, order.getReceivedAmount());
            // 明细行分摊
            allocateOrderItemReceived(order, ar, apply, true);
            // 【2026-10-14 完结口径可配置】按「销售订单状态设置」重算完结状态
            try {
                saleOrderService.recalcOrderCloseStatus(order.getOrderId());
            } catch (Exception ex) {
                log.warn("发货收款回写后完结重算失败: 订单={} err={}", order.getOrderNo(), ex.getMessage());
            }
        } catch (Exception ex) {
            // 回写失败不阻断收款确认主流程，但必须留痕（金额口径以日志为准，人工核对）
            log.error("发货收款回写订单异常: 应收单={} err={}", ar.getArNo(), ex.getMessage(), ex);
        }
    }

    /**
     * 发货收款回退（与 {@link #writeBackArReceiptToOrder} 对称）：订单表头已收回减 + 明细行分摊回退。
     */
    private void reverseArReceiptFromOrder(AccountsReceivable ar, BigDecimal amount) {
        try {
            String orderNo = ar.getSourceOrderNo();
            if (orderNo == null || orderNo.isBlank() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
            SaleOrder order = saleOrderService.getOne(new QueryWrapper<SaleOrder>().eq("order_no", orderNo));
            if (order == null) return;
            BigDecimal received = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
            BigDecimal newReceived = received.subtract(amount).max(BigDecimal.ZERO);
            order.setReceivedAmount(newReceived);
            saleOrderService.updateById(order);
            log.info("发货收款回退订单: 应收单={} 订单={} 回减={} 回减后已收={}", ar.getArNo(), order.getOrderNo(), amount, newReceived);
            allocateOrderItemReceived(order, ar, amount, false);
            // 【2026-10-14 完结口径可配置】收款回退后按「销售订单状态设置」重算完结状态（可能自动解结案）
            try {
                saleOrderService.recalcOrderCloseStatus(order.getOrderId());
            } catch (Exception ex) {
                log.warn("发货收款回退后完结重算失败: 订单={} err={}", order.getOrderNo(), ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("发货收款回退订单异常: 应收单={} err={}", ar.getArNo(), ex.getMessage(), ex);
        }
    }

    /**
     * 订单明细行已收分摊（2026-10-12；2026-10-14 兜底改版）：
     * 优先按「应收单来源出库单的明细行 → 订单明细行（来源行号）」精确对应，权重=出库行价税合计（按权重瀑布分摊）；
     * 带不出出库行时按「行号顺序 FIFO」分摊（排序在上的行先收满、封顶本行净额；反向倒序回减、封底 0），
     * 与存量回填 ensureReceiptSummaryFifoAllocated 同口径——两种收款方式切换后行级账连续。
     *
     * @param add true=正向累加（收款确认） false=反向回减（重置草稿/作废）
     */
    private void allocateOrderItemReceived(SaleOrder order, AccountsReceivable ar, BigDecimal amount, boolean add) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        List<SaleOrderItem> orderItems = saleOrderItemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", order.getOrderId()).orderByAsc("line_no"));
        if (orderItems == null || orderItems.isEmpty()) return;
        // 权重表：目标行 → 权重（出库行净额 / 订单行净额）
        Map<SaleOrderItem, BigDecimal> weights = new LinkedHashMap<>();
        if (ar != null && "销售出库单".equals(ar.getSourceBillType()) && ar.getSourceBillNo() != null) {
            SaleDelivery delivery = saleDeliveryService.getOne(
                    new QueryWrapper<SaleDelivery>().eq("delivery_no", ar.getSourceBillNo()));
            if (delivery != null) {
                Map<Integer, SaleOrderItem> byLineNo = new HashMap<>();
                for (SaleOrderItem oi : orderItems) {
                    if (oi.getLineNo() != null) byLineNo.putIfAbsent(oi.getLineNo(), oi);
                }
                List<SaleDeliveryItem> dItems = saleDeliveryItemMapper.selectList(
                        new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", delivery.getDeliveryId()).orderByAsc("line_no"));
                for (SaleDeliveryItem di : dItems) {
                    SaleOrderItem oi = (di.getSourceLineNo() == null) ? null : byLineNo.get(di.getSourceLineNo());
                    if (oi != null) {
                        BigDecimal w = di.getNetAmount() == null ? BigDecimal.ZERO : di.getNetAmount();
                        if (w.compareTo(BigDecimal.ZERO) > 0) weights.merge(oi, w, BigDecimal::add);
                    }
                }
            }
        }
        if (weights.isEmpty()) {
            // 【2026-10-14 改版】兜底分摊统一为「行号顺序 FIFO」（用友 T+/金蝶同款行业标准）：
            // 排序在上的行先收满（封顶本行净额），再收下一行——与"明细收款"模式的行剩余可收无缝衔接，
            // 且与存量回填（SchemaUpgradeEnsurer.ensureReceiptSummaryFifoAllocated）同一把尺子，
            // 两种收款方式来回切换，行级已收始终是一套连续的账。反向（回退）倒序回减、封底 0。
            List<SaleOrderItem> touched = new ArrayList<>();
            List<SaleOrderItem> ordered = new ArrayList<>(orderItems);
            ordered.sort(java.util.Comparator.comparing(oi -> oi.getLineNo() == null ? Integer.MAX_VALUE : oi.getLineNo()));
            BigDecimal cursor = amount;
            if (add) {
                for (SaleOrderItem oi : ordered) {
                    if (cursor.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal lineNet = oi.getNetAmount() == null ? BigDecimal.ZERO : oi.getNetAmount();
                    BigDecimal lineRec = oi.getReceivedAmount() == null ? BigDecimal.ZERO : oi.getReceivedAmount();
                    BigDecimal room = lineNet.subtract(lineRec).max(BigDecimal.ZERO);
                    if (room.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal take = cursor.min(room);
                    oi.setReceivedAmount(lineRec.add(take));
                    cursor = cursor.subtract(take);
                    touched.add(oi);
                }
            } else {
                for (int i = ordered.size() - 1; i >= 0; i--) {
                    if (cursor.compareTo(BigDecimal.ZERO) <= 0) break;
                    SaleOrderItem oi = ordered.get(i);
                    BigDecimal lineRec = oi.getReceivedAmount() == null ? BigDecimal.ZERO : oi.getReceivedAmount();
                    BigDecimal take = cursor.min(lineRec);
                    if (take.compareTo(BigDecimal.ZERO) <= 0) continue;
                    oi.setReceivedAmount(lineRec.subtract(take));
                    cursor = cursor.subtract(take);
                    touched.add(oi);
                }
            }
            for (SaleOrderItem oi : touched) saleOrderItemMapper.updateById(oi);
            if (cursor.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("订单行级{}FIFO 分摊后仍有余量未落行: 订单={} 余量={}（行全部收满/回空）",
                        add ? "正向" : "反向", order.getOrderNo(), cursor);
            }
            return;
        }
        BigDecimal totalWeight = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) return;
        // 瀑布分摊：按权重顺序，余数并入最后一行
        BigDecimal distributed = BigDecimal.ZERO;
        List<SaleOrderItem> touched = new ArrayList<>();
        Object[] weightRows = weights.keySet().toArray();
        for (int i = 0; i < weightRows.length; i++) {
            SaleOrderItem oi = (SaleOrderItem) weightRows[i];
            BigDecimal share = (i == weightRows.length - 1)
                    ? amount.subtract(distributed)
                    : amount.multiply(weights.get(oi)).divide(totalWeight, 2, java.math.RoundingMode.HALF_UP);
            if (share.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal current = oi.getReceivedAmount() == null ? BigDecimal.ZERO : oi.getReceivedAmount();
            BigDecimal target = add ? current.add(share) : current.subtract(share);
            if (add) {
                BigDecimal lineNet = oi.getNetAmount() == null ? BigDecimal.ZERO : oi.getNetAmount();
                if (target.compareTo(lineNet) > 0) target = lineNet; // 行容量封顶
            } else {
                if (target.compareTo(BigDecimal.ZERO) < 0) target = BigDecimal.ZERO; // 封底为0
            }
            BigDecimal delta = target.subtract(current);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                oi.setReceivedAmount(target);
                touched.add(oi);
                distributed = distributed.add(delta);
            }
        }
        if (distributed.compareTo(amount) != 0) {
            log.warn("订单明细行分摊{}未足额: 订单={} 目标={} 实际分摊={}（行容量/封底截断）",
                    add ? "" : "回退", order.getOrderNo(), amount, distributed);
        }
        for (SaleOrderItem oi : touched) saleOrderItemMapper.updateById(oi);
    }

    private BigDecimal sourceRemainingCapacity(String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) return BigDecimal.ZERO;
        try {
            switch (sourceType) {
                case "预收款单": {
                    SalePreReceipt p = salePreReceiptService.getById(sourceId);
                    if (p == null || p.getStatus() == null || p.getStatus() != 2) return BigDecimal.ZERO;
                    return p.getRemainAmount() == null ? BigDecimal.ZERO : p.getRemainAmount();
                }
                case "预付款单": {
                    PurchasePrePayment p = purchasePrePaymentService.getById(sourceId);
                    if (p == null || p.getStatus() == null || p.getStatus() != 2) return BigDecimal.ZERO;
                    return p.getRemainAmount() == null ? BigDecimal.ZERO : p.getRemainAmount();
                }
                case "销售订单": {
                    SaleOrder o = saleOrderService.getById(sourceId);
                    if (o == null) return BigDecimal.ZERO;
                    BigDecimal net = o.getNetAmount() == null ? BigDecimal.ZERO : o.getNetAmount();
                    BigDecimal rec = o.getReceivedAmount() == null ? BigDecimal.ZERO : o.getReceivedAmount();
                    return net.subtract(rec).max(BigDecimal.ZERO);
                }
                case "销售出库单": {
                    SaleDelivery d = saleDeliveryService.getById(sourceId);
                    if (d == null) return BigDecimal.ZERO;
                    BigDecimal net = d.getNetAmount() == null ? BigDecimal.ZERO : d.getNetAmount();
                    return net.subtract(writtenOffAmount("销售出库单", sourceId, null)).max(BigDecimal.ZERO);
                }
                case "销售发票": {
                    SaleInvoice inv = saleInvoiceService.getById(sourceId);
                    if (inv == null) return BigDecimal.ZERO;
                    BigDecimal net = inv.getNetAmount() == null ? BigDecimal.ZERO : inv.getNetAmount();
                    return net.subtract(writtenOffAmount("销售发票", sourceId, null)).max(BigDecimal.ZERO);
                }
                case "售后维修单": {
                    // 维修单可收余额 = 合计费用 − 已生效核销金额（整单一条核销行，明细行为空）
                    BigDecimal totalFee = queryRepairTotalFee(sourceId);
                    if (totalFee == null) return BigDecimal.ZERO;
                    return totalFee.subtract(writtenOffAmount("售后维修单", sourceId, null)).max(BigDecimal.ZERO);
                }
                case "应收单": {
                    AccountsReceivable ar = arMapper.selectById(sourceId);
                    if (ar == null) return BigDecimal.ZERO;
                    return ar.getBalanceAmount() == null ? BigDecimal.ZERO : ar.getBalanceAmount();
                }
                case "应付单": {
                    AccountsPayable ap = apMapper.selectById(sourceId);
                    if (ap == null) return BigDecimal.ZERO;
                    return ap.getBalanceAmount() == null ? BigDecimal.ZERO : ap.getBalanceAmount();
                }
                default:
                    return BigDecimal.ZERO;
            }
        } catch (Exception ex) {
            log.warn("取来源单可核销余额失败: {}#{} err={}", sourceType, sourceId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** 余数不收：把来源单剩余金额标记结案（不再纳入后续收/付款候选） */
    private void waiveSourceRemainder(String sourceType, Long sourceId, String operator) {
        if (sourceId == null) return;
        try {
            switch (sourceType) {
                case "销售订单": {
                    SaleOrder so = saleOrderService.getById(sourceId);
                    if (so != null && (so.getCloseStatus() == null || so.getCloseStatus() != 1)) {
                        so.setCloseStatus(1);
                        so.setClosedBy(operator);
                        so.setClosedAt(java.time.LocalDateTime.now());
                        so.setClosedReason("余数不收（收款单核销尾差结案）");
                        saleOrderService.updateById(so);
                    }
                    break;
                }
                case "应收单": {
                    AccountsReceivable ar = arMapper.selectById(sourceId);
                    if (ar != null && (ar.getIsClosed() == null || ar.getIsClosed() != 1)) {
                        ar.setIsClosed((short) 1);
                        ar.setStatus("已结清");
                        arMapper.updateById(ar);
                    }
                    break;
                }
                case "应付单": {
                    AccountsPayable ap = apMapper.selectById(sourceId);
                    if (ap != null && (ap.getIsClosed() == null || ap.getIsClosed() != 1)) {
                        ap.setIsClosed((short) 1);
                        ap.setStatus("已结清");
                        apMapper.updateById(ap);
                    }
                    break;
                }
                case "售后维修单":
                    // 维修单不按收款尾差结案：余数不收只记录日志，不改维修单状态与费用
                    log.info("余数不收（售后维修单不结案）: repairId={} operator={}", sourceId, operator);
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            log.warn("余数不收结案失败: sourceType={}, sourceId={}, err={}", sourceType, sourceId, ex.getMessage());
        }
    }

    /** 销售订单收款回写：累计 received_amount + 防超收校验 */
    private void applySaleOrderWriteoff(Long orderId, BigDecimal amount) {
        SaleOrder order = saleOrderService.getById(orderId);
        if (order == null) throw new BusinessException("销售订单不存在: " + orderId);
        // 【2026-10-19 设计图 v3】系统不生成应收单，删除「已生成应收单禁止订单直收」的通道互斥守卫：
        // 收款单只有一条核销主线（来源=销售订单/销售出库单/销售发票），不再有第二条应收核销通道。
        BigDecimal currentReceived = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
        BigDecimal newReceived = currentReceived.add(amount);
        BigDecimal orderAmount = order.getNetAmount() == null ? BigDecimal.ZERO : order.getNetAmount();
        BigDecimal tolerance = new BigDecimal("0.01");
        if (newReceived.compareTo(orderAmount.add(tolerance)) > 0) {
            throw new BusinessException(String.format(
                    "收款金额超过订单未收余额。订单金额: %.2f，已收款: %.2f，本次收款: %.2f",
                    orderAmount, currentReceived, amount));
        }
        order.setReceivedAmount(newReceived);
        saleOrderService.updateById(order);
        log.info("收款回写订单: 订单={}, 本次收款={}, 累计已收={}", order.getOrderNo(), amount, newReceived);
        // 【2026-10-14 完结口径可配置】收款回写后按「销售订单状态设置」重算完结状态
        try {
            saleOrderService.recalcOrderCloseStatus(order.getOrderId());
        } catch (Exception ex) {
            log.warn("收款回写后完结重算失败: 订单={} err={}", order.getOrderNo(), ex.getMessage());
        }
    }

    /** 应收单核销收款：仅更新应收单已收/余额/状态，不重复生成收款单 */
    private void applyArWriteoff(Long arId, BigDecimal amount) {
        AccountsReceivable ar = arMapper.selectById(arId);
        if (ar == null) throw new BusinessException("应收单不存在: " + arId);
        // 【2026-10-13 收款唯一水位】订单累计已收（订单直收+应收核销+明细行合计）不得超过订单金额：
        // 订单直收路径在 applySaleOrderWriteoff 防超收，这里对称校验应收核销路径，
        // 杜绝"同一订单两条通道合计超收"（此前应收核销只按应收余额封顶，不感知订单已收水位）。
        if (ar.getSourceOrderNo() != null && !ar.getSourceOrderNo().isBlank()) {
            SaleOrder order = saleOrderService.getOne(new QueryWrapper<SaleOrder>().eq("order_no", ar.getSourceOrderNo()));
            if (order != null) {
                BigDecimal orderNet = order.getNetAmount() == null ? BigDecimal.ZERO : order.getNetAmount();
                BigDecimal received = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
                BigDecimal headroom = orderNet.subtract(received);
                if (amount.subtract(headroom).compareTo(new BigDecimal("0.01")) > 0) {
                    throw new BusinessException(String.format(
                            "收款超出订单未收余额（订单累计已收不允许超过订单金额）。订单: %s，订单金额: %.2f，已收: %.2f，本次核销: %.2f，还可收: %.2f",
                            order.getOrderNo(), orderNet, received, amount, headroom.max(BigDecimal.ZERO)));
                }
            }
        }
        BigDecimal newPaid = (ar.getPaidAmount() == null ? BigDecimal.ZERO : ar.getPaidAmount()).add(amount);
        ar.setPaidAmount(newPaid);
        ar.setBalanceAmount(ar.getTotalAmount().subtract(newPaid));
        if (newPaid.compareTo(ar.getTotalAmount()) >= 0) {
            ar.setStatus("已结清");
            ar.setIsClosed((short) 1);
        } else if (newPaid.compareTo(BigDecimal.ZERO) > 0) {
            ar.setStatus("部分收款");
        }
        arMapper.updateById(ar);
        log.info("收款核销应收单: 应收单={}, 本次={}, 累计={}", ar.getArNo(), amount, newPaid);
        // 发货收款回写销售订单（2026-10-12）：沿「应收单→来源订单号」回写订单表头+明细行已收
        writeBackArReceiptToOrder(ar, amount);
    }

    /**
     * 重置草稿收款（已确认→草稿），回减订单 received_amount
     */
    @Override
    @Transactional
    public void reverseReceipt(Long id, String operator) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款记录不存在");
        String st = receipt.getStatus();
        if (!"已确认".equals(st) && !"CONFIRMED".equals(st) && !"已生效".equals(st)) {
            throw new BusinessException("只有已确认状态可重置草稿，当前状态: " + st);
        }
        if (receipt.getCloseStatus() != null && receipt.getCloseStatus() == 1) {
            throw new BusinessException("收款单[" + receipt.getReceiptNo() + "]已结案，不能重置草稿");
        }

        // 回减 received_amount（与 confirmReceipt 回写对称）
        rollbackReceiptWriteback(receipt, "重置草稿");

        // updateById 默认忽略 null 字段，清空审批人/时间须用 UpdateWrapper 显式置空
        receiptMapper.update(null, Wrappers.<PaymentReceipt>lambdaUpdate()
                .eq(PaymentReceipt::getReceiptId, id)
                .set(PaymentReceipt::getStatus, "草稿")
                .set(PaymentReceipt::getApprovalStatus, "待审核")
                .set(PaymentReceipt::getApprovedBy, null)
                .set(PaymentReceipt::getApprovedAt, null));
        log.info("收款重置草稿: {}, 操作人: {}", receipt.getReceiptNo(), operator);
    }

    /**
     * 作废收款单：记录作废前状态（供反作废恢复），已确认单据回减订单 received_amount
     */
    @Override
    @Transactional
    public void voidReceipt(Long id, String operator, String reason) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款记录不存在");
        String st = receipt.getStatus();
        if ("已作废".equals(st) || "VOID".equals(st)) {
            throw new BusinessException("收款单已作废，不能重复作废");
        }
        if (receipt.getCloseStatus() != null && receipt.getCloseStatus() == 1) {
            throw new BusinessException("收款单[" + receipt.getReceiptNo() + "]已结案，如需作废请先反结案");
        }

        int beforeStatus = BillStatusUtils.toNumber(st); // 0草稿/1待审核/2已确认
        // 已确认单据作废需回减业务影响（订单已收款/应收单已收金额）
        if (beforeStatus == 2) {
            rollbackReceiptWriteback(receipt, "作废");
        }

        receipt.setStatus("已作废");
        receipt.setApprovalStatus("已作废");
        receipt.setBillBeforeStatus(beforeStatus);
        receipt.setVoidedBy(operator);
        receipt.setVoidedAt(java.time.LocalDateTime.now());
        receipt.setVoidReason(reason);
        receipt.setUpdatedBy(operator);
        receipt.setUpdatedAt(java.time.LocalDateTime.now());
        receiptMapper.updateById(receipt);
        log.info("收款作废: {}, 作废前状态: {}, 操作人: {}, 原因: {}", receipt.getReceiptNo(), st, operator, reason);
    }

    /**
     * 反作废收款单（已作废→恢复作废前状态）
     * 作废前为已确认时重新回写业务影响（与作废时的回减对称）
     */
    @Override
    @Transactional
    public void unvoidReceipt(Long id, String operator) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款记录不存在");
        String st = receipt.getStatus();
        if (!"已作废".equals(st) && !"VOID".equals(st)) {
            throw new BusinessException("只有已作废状态可反作废，当前状态: " + st);
        }

        Integer before = receipt.getBillBeforeStatus();
        int target = before == null ? 0 : before; // 未记录作废前状态时容错回草稿
        String targetLabel = financeStatusFromCode(target);

        receiptMapper.update(null, Wrappers.<PaymentReceipt>lambdaUpdate()
                .eq(PaymentReceipt::getReceiptId, id)
                .set(PaymentReceipt::getStatus, targetLabel)
                .set(PaymentReceipt::getApprovalStatus, target == 2 ? "已生效" : "待审核")
                .set(PaymentReceipt::getBillBeforeStatus, null)
                .set(PaymentReceipt::getVoidedBy, null)
                .set(PaymentReceipt::getVoidedAt, null)
                .set(PaymentReceipt::getVoidReason, null)
                .set(PaymentReceipt::getUpdatedBy, operator)
                .set(PaymentReceipt::getUpdatedAt, java.time.LocalDateTime.now()));

        // 作废前是已确认态 → 重新回写业务影响（与作废时的回减对称）
        if (target == 2) {
            applyReceiptWriteback(receipt, operator);
        }
        log.info("收款反作废: {}, 恢复状态: {}, 操作人: {}", receipt.getReceiptNo(), targetLabel, operator);
    }

    /**
     * 驳回收款单（待审核→草稿）
     */
    @Override
    @Transactional
    public void rejectReceipt(Long id, String rejector, String reason) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        String st = receipt.getStatus();
        if (!"待审核".equals(st) && !"PENDING".equals(st) && !"待审批".equals(st) && !"审批中".equals(st)) {
            throw new BusinessException("只有待审核状态可驳回，当前状态: " + st);
        }
        receipt.setStatus("草稿");
        receipt.setApprovalStatus("驳回");
        receipt.setUpdatedBy(rejector);
        receipt.setUpdatedAt(java.time.LocalDateTime.now());
        receiptMapper.updateById(receipt);
        log.info("收款单驳回: {}, 驳回人: {}, 原因: {}", receipt.getReceiptNo(), rejector, reason);
    }

    /**
     * 结案收款单（已确认→已结案，终态1）
     */
    @Override
    @Transactional
    public void closeReceipt(Long id, String operator, String reason) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        String st = receipt.getStatus();
        if (BillStatusUtils.toNumber(st) != 2) {
            throw new BusinessException("只有已确认状态可结案，当前状态: " + st);
        }
        if (receipt.getCloseStatus() != null && receipt.getCloseStatus() == 1) {
            throw new BusinessException("收款单[" + receipt.getReceiptNo() + "]已结案，不能重复结案");
        }
        receipt.setCloseStatus(1);
        receipt.setClosedBy(operator);
        receipt.setClosedAt(java.time.LocalDateTime.now());
        receipt.setClosedReason(reason);
        receiptMapper.updateById(receipt);
        log.info("收款单结案: {}, 操作人: {}, 原因: {}", receipt.getReceiptNo(), operator, reason);
    }

    /**
     * 反结案收款单（已结案→未结案）
     */
    @Override
    @Transactional
    public void reopenReceipt(Long id, String operator) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        if (receipt.getCloseStatus() == null || receipt.getCloseStatus() != 1) {
            throw new BusinessException("只有已结案状态可反结案");
        }
        receipt.setCloseStatus(0);
        receipt.setClosedBy(operator);
        receipt.setClosedAt(java.time.LocalDateTime.now());
        receiptMapper.updateById(receipt);
        log.info("收款单反结案: {}, 操作人: {}", receipt.getReceiptNo(), operator);
    }

    /**
     * 删除收款单（仅草稿状态可删）
     */
    @Override
    @Transactional
    public void deleteReceipt(Long id) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款记录不存在");
        String st = receipt.getStatus();
        if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(st))) {
            throw new BusinessException("非草稿不能删除（当前状态：" + st + "），请先重置草稿");
        }
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("cash_receipt", id);
        receiptMapper.deleteById(id);
        log.info("收款删除: {}", receipt.getReceiptNo());
    }

    /** 财务单据状态码→标签（0草稿/1待审核/2已确认，编码与通用 BillStatusUtils 一致） */
    private static String financeStatusFromCode(int code) {
        switch (code) {
            case 1: return "待审核";
            case 2: return "已确认";
            default: return "草稿";
        }
    }

    /**
     * 回减收款单核销明细（与 confirmReceipt 的回写逻辑对称）：预收款单/销售订单/应收单。
     */
    private void rollbackReceiptWriteback(PaymentReceipt receipt, String action) {
        // 多收自动生成的预收款单：已生效则阻止操作，草稿则删除
        rollbackOveragePreReceipt(receipt, action);
        List<BillWriteoffDetail> details = billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "收款单").eq("bill_id", receipt.getReceiptId()));
        if (details == null || details.isEmpty()) return;
        for (BillWriteoffDetail d : details) {
            // 只回减"已冲销"的行：草稿阶段未冲销的行未产生核销，回减会造成负数
            if (!"已冲销".equals(d.getSettledQty())) continue;
            BigDecimal amount = d.getWriteoffAmount() == null ? BigDecimal.ZERO : d.getWriteoffAmount();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                switch (d.getSourceType()) {
                    case "预收款单":
                        salePreReceiptService.reverseWriteOff(d.getSourceId(), amount);
                        break;
                    case "销售订单":
                        SaleOrder order = saleOrderService.getById(d.getSourceId());
                        // 明细收款行（2026-10-12）：先回减行级已收（封底0），再走表头回减
                        if (d.getSourceItemId() != null) {
                            SaleOrderItem item = saleOrderItemMapper.selectById(d.getSourceItemId());
                            if (item != null) {
                                BigDecimal lineRec = item.getReceivedAmount() == null ? BigDecimal.ZERO : item.getReceivedAmount();
                                item.setReceivedAmount(lineRec.subtract(amount).max(BigDecimal.ZERO));
                                saleOrderItemMapper.updateById(item);
                                log.info("收款{}回减订单行: 行ID={}, 货品={}, 回减后行已收={}",
                                        action, item.getOrderItemId(), item.getMaterialName(), item.getReceivedAmount());
                            }
                        }
                        if (order != null) {
                            BigDecimal currentReceived = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
                            BigDecimal newReceived = currentReceived.subtract(amount);
                            if (newReceived.compareTo(BigDecimal.ZERO) < 0) newReceived = BigDecimal.ZERO;
                            order.setReceivedAmount(newReceived);
                            saleOrderService.updateById(order);
                            log.info("收款{}回减订单: 订单={}, 本次回减={}, 回减后已收={}", action, order.getOrderNo(), amount, newReceived);
                            // 【2026-10-14 完结口径可配置】收款回减后重算完结状态（可能自动解结案）
                            try {
                                saleOrderService.recalcOrderCloseStatus(order.getOrderId());
                            } catch (Exception ex) {
                                log.warn("收款回减后完结重算失败: 订单={} err={}", order.getOrderNo(), ex.getMessage());
                            }
                        }
                        break;
                    case "销售出库单":
                    case "销售发票":
                        // 与确认时两跳回写对称：按订单明细回减已收（行封底 0，表头同步回减）
                        reverseReceiptWritebackToOrder(d, amount, action);
                        log.info("收款{}回退核销{}: 来源单#{} 本次={}", action, d.getSourceType(), d.getSourceId(), amount);
                        break;
                    case "售后维修单":
                        // 售后维修单：不改维修单金额与状态，仅记录日志
                        log.info("收款{}回退核销{}: 来源单#{} 本次={}", action, d.getSourceType(), d.getSourceId(), amount);
                        break;
                    case "应收单":
                        AccountsReceivable ar = arMapper.selectById(d.getSourceId());
                        if (ar != null) {
                            BigDecimal arPaid = ar.getPaidAmount() == null ? BigDecimal.ZERO : ar.getPaidAmount();
                            BigDecimal newPaid = arPaid.subtract(amount);
                            if (newPaid.compareTo(BigDecimal.ZERO) < 0) newPaid = BigDecimal.ZERO;
                            ar.setPaidAmount(newPaid);
                            ar.setBalanceAmount(ar.getTotalAmount().subtract(newPaid));
                            if (newPaid.compareTo(BigDecimal.ZERO) <= 0) {
                                ar.setStatus("待收款");
                                ar.setIsClosed((short) 0);
                            }
                            arMapper.updateById(ar);
                            // 发货收款回退（2026-10-12）：对称回减来源订单表头+明细行已收
                            reverseArReceiptFromOrder(ar, amount);
                        }
                        break;
                    default:
                        break;
                }
            }
            // 余数不收回滚：撤销来源单结案
            if (d.getWaiveRemainder() != null && d.getWaiveRemainder() == 1) {
                unwaiveSourceRemainder(d.getSourceType(), d.getSourceId(), action);
            }
            d.setSettledQty("未冲销");
            d.setSettledAt(null);
            billWriteoffDetailMapper.updateById(d);
        }
    }

    /** 多收自动生成的预收款单回滚：已生效阻止操作，草稿删除 */
    private void rollbackOveragePreReceipt(PaymentReceipt receipt, String action) {
        List<SalePreReceipt> list = salePreReceiptService.list(new QueryWrapper<SalePreReceipt>()
                .eq("source_type", "PAYMENT_RECEIPT_OVERAGE")
                .eq("source_bill_id", receipt.getReceiptId()));
        if (list == null || list.isEmpty()) return;
        for (SalePreReceipt p : list) {
            if (p.getStatus() != null && p.getStatus() != 0) {
                throw new BusinessException("收款单[" + receipt.getReceiptNo() + "]多收自动生成的预收款单["
                        + p.getReceiptNo() + "]已生效，请先处理该预收款单再操作");
            }
        }
        for (SalePreReceipt p : list) {
            salePreReceiptService.delete(p.getId());
            log.info("收款单[{}]{}：删除多收自动生成的草稿预收款单[{}]", receipt.getReceiptNo(), action, p.getReceiptNo());
        }
    }

    /** 余数不收回滚：撤销来源单结案（仅针对被本单结案的来源单） */
    private void unwaiveSourceRemainder(String sourceType, Long sourceId, String action) {
        if (sourceId == null) return;
        try {
            switch (sourceType) {
                case "销售订单": {
                    SaleOrder so = saleOrderService.getById(sourceId);
                    if (so != null && so.getCloseStatus() != null && so.getCloseStatus() == 1) {
                        so.setCloseStatus(0);
                        so.setClosedReason(null);
                        saleOrderService.updateById(so);
                    }
                    break;
                }
                case "应收单": {
                    AccountsReceivable ar = arMapper.selectById(sourceId);
                    if (ar != null && ar.getIsClosed() != null && ar.getIsClosed() == 1) {
                        ar.setIsClosed((short) 0);
                        arMapper.updateById(ar);
                    }
                    break;
                }
                case "应付单": {
                    AccountsPayable ap = apMapper.selectById(sourceId);
                    if (ap != null && ap.getIsClosed() != null && ap.getIsClosed() == 1) {
                        ap.setIsClosed((short) 0);
                        apMapper.updateById(ap);
                    }
                    break;
                }
                case "售后维修单":
                    // 维修单不参与「余数不收」结案，回滚时同样无需处理
                    log.info("{}：售后维修单无需回滚结案 repairId={}", action, sourceId);
                    break;
                default:
                    break;
            }
            log.info("{}：余数不收回滚来源单结案 sourceType={} sourceId={}", action, sourceType, sourceId);
        } catch (Exception ex) {
            log.warn("余数不收回滚失败: sourceType={}, sourceId={}, err={}", sourceType, sourceId, ex.getMessage());
        }
    }

    // ========== 付款记录 ==========

    @Override
    @Transactional
    public PaymentVoucher createVoucher(PaymentVoucher voucher) {
        // 编辑已存在的单据 → 更新；否则新增
        if (voucher.getPaymentId() != null && voucherMapper.selectById(voucher.getPaymentId()) != null) {
            PaymentVoucher existing = voucherMapper.selectById(voucher.getPaymentId());
            String est = existing.getStatus();
            if (!"草稿".equals(est) && !"DRAFT".equals(est)) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + est);
            }
            if (existing.getCloseStatus() != null && existing.getCloseStatus() == 1) {
                throw new BusinessException("付款单[" + existing.getPaymentNo() + "]已结案，不能修改");
            }
            voucherMapper.updateById(voucher);
            saveVoucherItems(voucher.getPaymentId(), voucher.getItems());
            saveWriteoffDetails("付款单", voucher.getPaymentId(), voucher.getPaymentNo(), voucher.getWriteOffItems());
            return voucher;
        }
        if (voucher.getPaymentNo() == null || voucher.getPaymentNo().isBlank()) {
            voucher.setPaymentNo(generateVoucherNo());
        }
        // 保存即草稿；是否需要审批由「提交」时校验（submitVoucher）
        if (voucher.getStatus() == null || voucher.getStatus().isBlank()) voucher.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"（前端表单会送空串，"待审核"只在提交后出现）
        if (voucher.getApprovalStatus() == null || voucher.getApprovalStatus().isBlank()
                || ("草稿".equals(voucher.getStatus()) && "待审核".equals(voucher.getApprovalStatus()))) {
            voucher.setApprovalStatus("草稿");
        }
        if (voucher.getPaymentDate() == null) voucher.setPaymentDate(LocalDate.now());
        if (voucher.getExchangeRate() == null) voucher.setExchangeRate(BigDecimal.ONE);
        if (voucher.getDiscountAmount() == null) voucher.setDiscountAmount(BigDecimal.ZERO);
        voucherMapper.insert(voucher);
        saveVoucherItems(voucher.getPaymentId(), voucher.getItems());
        saveWriteoffDetails("付款单", voucher.getPaymentId(), voucher.getPaymentNo(), voucher.getWriteOffItems());
        return voucher;
    }

    @Override
    @Transactional
    public void submitVoucher(Long id, String submittedBy) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款单不存在");
        String st = voucher.getStatus();
        if (!"草稿".equals(st) && !"DRAFT".equals(st) && !"待确认".equals(st)) {
            throw new BusinessException("只有草稿状态可提交，当前状态: " + st);
        }
        if (approvalGateHelper.needApproval("PAYMENT_VOUCHER")) {
            // 开启审批：落「待审核」并进审批引擎建待办（无可用审批流程时 startApproval 抛错 → 整事务回滚，单据留草稿）
            voucher.setStatus("待审核");
            voucher.setApprovalStatus("待审核");
            voucherMapper.updateById(voucher);
            billApprovalAdapter.submitToApproval("PAYMENT_VOUCHER", voucher.getPaymentId(), voucher.getPaymentNo(),
                    submittedBy != null && !submittedBy.isBlank() ? submittedBy : BillApprovalAdapter.currentSubmitter(), voucher);
            log.info("付款单提交审批并生成审批待办: {}", voucher.getPaymentNo());
        } else {
            // 免审批：提交即生效
            confirmVoucher(id, "系统(免审批)");
        }
    }

    @Override
    public PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        return pageVoucher(current, size, supplierName, status, voucherNo, null, null, null, scopeCtx, sortBy, sortOrder, null, null);
    }

    @Override
    public PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo, String keyword, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String startDate, String endDate) {
        QueryWrapper<PaymentVoucher> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用原默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, VOUCHER_SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PaymentVoucher.class);
        // 关键词：单号 + 供应商名称 多列匹配，强制成组 AND (payment_no LIKE ? OR supplier_name LIKE ?)
        // （原来是 supplierName / voucherNo 两条独立 like 且默认 AND → 前端把同一个词同时塞进两个参数，几乎永远搜不到；
        //   而平铺 OR 又会让命中单号的行绕过状态筛选与数据权限，故统一走 applyKeywordGroup）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "payment_no", "supplier_name");
        if (keyword == null || keyword.isBlank()) {
            // 兼容旧前端/旧链接：keyword 未传时仍支持 supplierName、voucherNo 两个旧参数（各一条 like，保持原语义）
            if (supplierName != null && !supplierName.isBlank()) wrapper.like("supplier_name", supplierName);
            if (voucherNo != null && !voucherNo.isBlank()) wrapper.like("payment_no", voucherNo);
        }
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        // 付款日期区间（通用搜索栏日期维度；payment_date 为 DATE 列，applyDateRange 内部绑定 LocalDate）
        ListQuerySupport.applyDateRange(wrapper, "payment_date", startDate, endDate);
        // 供应商维度（统一入参 supplierId → 本页真实列 supplier_id）
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        // 员工维度（统一入参 employeeId → 本页真实列 cashier_id，业务口径「出纳」）
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<PaymentVoucher> page = new Page<>(current, size);
        Page<PaymentVoucher> result = voucherMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo, String keyword, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String startDate, String endDate, Map<String, String> headerFilters) {
        QueryWrapper<PaymentVoucher> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, VOUCHER_SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PaymentVoucher.class);
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "payment_no", "supplier_name");
        if (keyword == null || keyword.isBlank()) {
            if (supplierName != null && !supplierName.isBlank()) wrapper.like("supplier_name", supplierName);
            if (voucherNo != null && !voucherNo.isBlank()) wrapper.like("payment_no", voucherNo);
        }
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        ListQuerySupport.applyDateRange(wrapper, "payment_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "paymentVoucher", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<PaymentVoucher> page = new Page<>(current, size);
        Page<PaymentVoucher> result = voucherMapper.selectPage(page, wrapper);
        List<PaymentVoucher> records = result.getRecords();
        fillVoucherSourceFromWriteoff(records);
        return PageResult.of(records, result.getTotal(), current, size);
    }

    /** 付款单列表来源列：与收款单同口径，以核销明细行为准，表头仅兜底 */
    private void fillVoucherSourceFromWriteoff(List<PaymentVoucher> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = new java.util.ArrayList<>();
        for (PaymentVoucher r : records) if (r.getPaymentId() != null) ids.add(r.getPaymentId());
        if (ids.isEmpty()) return;
        List<BillWriteoffDetail> rows = billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "付款单").in("bill_id", ids));
        Map<Long, java.util.LinkedHashSet<String>> nos = new HashMap<>();
        Map<Long, java.util.LinkedHashSet<String>> types = new HashMap<>();
        for (BillWriteoffDetail d : rows) {
            if (d.getBillId() == null || d.getSourceNo() == null || d.getSourceNo().isBlank()) continue;
            nos.computeIfAbsent(d.getBillId(), k -> new java.util.LinkedHashSet<>()).add(d.getSourceNo());
            if (d.getSourceType() != null && !d.getSourceType().isBlank()) {
                types.computeIfAbsent(d.getBillId(), k -> new java.util.LinkedHashSet<>()).add(d.getSourceType());
            }
        }
        for (PaymentVoucher r : records) {
            java.util.LinkedHashSet<String> n = nos.get(r.getPaymentId());
            if (n == null || n.isEmpty()) continue;
            r.setSourceBillNo(String.join("、", n));
            java.util.LinkedHashSet<String> ts = types.get(r.getPaymentId());
            if (ts != null && !ts.isEmpty()) r.setSourceBillType(String.join("、", ts));
        }
    }

    @Override
    @Transactional
    public void confirmVoucher(Long id, String confirmedBy) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款记录不存在");
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        // （由引擎回调 onApprovalApproved 触发的审核不受影响：引擎会先把任务置为已通过再回写单据）
        if (approvalRuntimeService.getRunningTask("PAYMENT_VOUCHER", id) != null) {
            throw new BusinessException("付款单已提交审批流程，请由指定审批人在审批中心处理");
        }
        String st = voucher.getStatus();
        if (!"草稿".equals(st) && !"DRAFT".equals(st) && !"待审核".equals(st) && !"PENDING".equals(st) && !"待确认".equals(st)) {
            throw new BusinessException("只有草稿或待审核状态可审核，当前状态: " + st);
        }
        if (voucher.getCloseStatus() != null && voucher.getCloseStatus() == 1) {
            throw new BusinessException("付款单[" + voucher.getPaymentNo() + "]已结案，不能审核");
        }

        // 装载明细 → 金额校验（核销需求不得超过可用资金：实付现金 + 预付款冲抵）
        loadVoucherDetails(voucher);
        validateVoucherAmount(voucher);

        voucher.setStatus("已确认");
        voucher.setApprovalStatus("已生效");
        // 免审批自动通过时 confirmedBy 为系统占位 → 审核人留空（用户 2026-10-28 口径）
        voucher.setApprovedBy(BillStatusUtils.normalizeApprover(confirmedBy));
        voucher.setApprovedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);

        // 更新关联应付单（只更新应付，不重复生成付款单——本单即是付款单）
        applyVoucherWriteback(voucher, confirmedBy);

        // 多付转预付：现金结余自动生成草稿采购预付款单（幂等）
        createOveragePrePayment(voucher, confirmedBy);

        log.info("付款确认: {}", voucher.getPaymentNo());
    }

    /** 装载付款单明细（付款明细 + 核销明细），用于金额校验与多付计算 */
    private void loadVoucherDetails(PaymentVoucher voucher) {
        voucher.setItems(voucherItemMapper.selectList(
                new QueryWrapper<PaymentVoucherItem>().eq("payment_id", voucher.getPaymentId())));
        voucher.setWriteOffItems(billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "付款单").eq("bill_id", voucher.getPaymentId())));
    }

    /** 付款金额三元组：ret[0]=实付现金(items合计) ret[1]=预付款单冲抵合计 ret[2]=核销需求(应付单) */
    private BigDecimal[] voucherAmounts(PaymentVoucher voucher) {
        BigDecimal cash = BigDecimal.ZERO;
        if (voucher.getItems() != null) {
            for (PaymentVoucherItem it : voucher.getItems()) {
                if (it.getAmount() != null) cash = cash.add(it.getAmount());
            }
        }
        BigDecimal preOffset = BigDecimal.ZERO;
        BigDecimal demand = BigDecimal.ZERO;
        if (voucher.getWriteOffItems() != null) {
            for (BillWriteoffDetail d : voucher.getWriteOffItems()) {
                BigDecimal amt = d.getWriteoffAmount() == null ? BigDecimal.ZERO : d.getWriteoffAmount();
                if ("预付款单".equals(d.getSourceType())) preOffset = preOffset.add(amt);
                else demand = demand.add(amt);
            }
        }
        return new BigDecimal[]{cash, preOffset, demand};
    }

    /** 付款金额校验：核销需求 ≤ 实付现金 + 预付款冲抵 */
    private void validateVoucherAmount(PaymentVoucher voucher) {
        BigDecimal[] a = voucherAmounts(voucher);
        BigDecimal available = a[0].add(a[1]);
        if (a[2].compareTo(available.add(new BigDecimal("0.01"))) > 0) {
            throw new BusinessException(String.format(
                    "核销金额超过付款可用金额：可用(实付%.2f + 预付冲抵%.2f)=%.2f，需核销%.2f",
                    a[0], a[1], available, a[2]));
        }
    }

    /** 多付转预付：现金结余 > 0 时生成草稿采购预付款单（幂等） */
    private void createOveragePrePayment(PaymentVoucher voucher, String operator) {
        BigDecimal[] a = voucherAmounts(voucher);
        BigDecimal cashUsed = a[2].subtract(a[1]).max(BigDecimal.ZERO);
        BigDecimal overage = a[0].subtract(cashUsed).setScale(2, java.math.RoundingMode.HALF_UP);
        if (overage.compareTo(new BigDecimal("0.01")) < 0) return;
        Long exist = purchasePrePaymentService.count(new QueryWrapper<PurchasePrePayment>()
                .eq("source_type", "PAYMENT_VOUCHER_OVERAGE")
                .eq("source_bill_id", voucher.getPaymentId()));
        if (exist != null && exist > 0) return;
        if (!sysSerialNoConfigService.isAutoGenerate("PURCHASE_PRE_PAYMENT")) {
            log.warn("采购预付款单已关闭自动编号，付款单[{}]多付{}未自动转预付", voucher.getPaymentNo(), overage);
            return;
        }
        PurchasePrePayment p = new PurchasePrePayment();
        p.setSupplierId(voucher.getSupplierId());
        p.setSupplierName(voucher.getSupplierName());
        p.setPaymentDate(LocalDate.now());
        p.setAmount(overage);
        BigDecimal rate = voucher.getExchangeRate() != null ? voucher.getExchangeRate() : BigDecimal.ONE;
        p.setExchangeRate(rate);
        p.setBaseAmount(overage.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
        p.setUsedAmount(BigDecimal.ZERO);
        p.setRemainAmount(overage);
        p.setStatus(0);
        p.setSourceType("PAYMENT_VOUCHER_OVERAGE");
        p.setSourceBillId(voucher.getPaymentId());
        p.setRemark("付款单[" + voucher.getPaymentNo() + "]多付自动转入");
        // 明细必须与主表一起落库：savePrePayment 只会落库 items，不造明细则打开预付款单「付款明细」为空白
        PurchasePrePaymentItem overageItem = new PurchasePrePaymentItem();
        overageItem.setAmount(overage);
        if (voucher.getItems() != null && !voucher.getItems().isEmpty()) {
            PaymentVoucherItem first = voucher.getItems().get(0);
            overageItem.setAccountId(first.getAccountId());
            overageItem.setAccountName(first.getAccountName());
            overageItem.setAccountNo(first.getAccountNo() != null ? first.getAccountNo() : first.getBankAccount());
        }
        List<PurchasePrePaymentItem> overageItems = new java.util.ArrayList<>();
        overageItems.add(overageItem);
        p.setItems(overageItems);
        purchasePrePaymentService.savePrePayment(p);
        log.info("付款单[{}]多付{}自动生成草稿采购预付款单[{}]", voucher.getPaymentNo(), overage, p.getPaymentNo());
    }

    /**
     * 付款确认后的业务回写（正向）：按核销明细分发（预付款单/应付单）。
     * 供 confirmVoucher 与 unvoidVoucher（恢复已确认态）复用，与 rollbackVoucherWriteback 对称。
     */
    private void applyVoucherWriteback(PaymentVoucher voucher, String operator) {
        List<BillWriteoffDetail> details = billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "付款单").eq("bill_id", voucher.getPaymentId()));
        if (details == null || details.isEmpty()) return;
        for (BillWriteoffDetail d : details) {
            applyWriteoffDetail(d, operator);
        }
    }

    /**
     * 回减付款单核销明细（与 confirmVoucher 的回写逻辑对称）：预付款单/应付单。
     */
    private void rollbackVoucherWriteback(PaymentVoucher voucher, String action) {
        // 多付自动生成的预付款单：已生效则阻止操作，草稿则删除
        rollbackOveragePrePayment(voucher);
        List<BillWriteoffDetail> details = billWriteoffDetailMapper.selectList(
                new QueryWrapper<BillWriteoffDetail>().eq("bill_type", "付款单").eq("bill_id", voucher.getPaymentId()));
        if (details == null || details.isEmpty()) return;
        for (BillWriteoffDetail d : details) {
            // 只回减"已冲销"的行：草稿阶段未冲销的行未产生核销，回减会造成负数
            if (!"已冲销".equals(d.getSettledQty())) continue;
            BigDecimal amount = d.getWriteoffAmount() == null ? BigDecimal.ZERO : d.getWriteoffAmount();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                switch (d.getSourceType()) {
                    case "预付款单":
                        purchasePrePaymentService.reverseWriteOff(d.getSourceId(), amount);
                        break;
                    case "应付单":
                        AccountsPayable ap = apMapper.selectById(d.getSourceId());
                        if (ap != null) {
                            BigDecimal paid = ap.getPaidAmount() == null ? BigDecimal.ZERO : ap.getPaidAmount();
                            BigDecimal total = ap.getTotalAmount() == null ? BigDecimal.ZERO : ap.getTotalAmount();
                            BigDecimal newPaid = paid.subtract(amount);
                            if (newPaid.compareTo(BigDecimal.ZERO) < 0) newPaid = BigDecimal.ZERO;
                            ap.setPaidAmount(newPaid);
                            ap.setBalanceAmount(total.subtract(newPaid));
                            if (newPaid.compareTo(BigDecimal.ZERO) <= 0) ap.setStatus("待付款"); else ap.setStatus("部分付款");
                            ap.setIsClosed((short) 0);
                            apMapper.updateById(ap);
                            log.info("付款{}回减应付单: 应付单={}, 本次回减={}, 回减后已付={}", action, ap.getApNo(), amount, newPaid);
                        }
                        break;
                    default:
                        break;
                }
            }
            // 余数不收回滚：撤销来源单结案
            if (d.getWaiveRemainder() != null && d.getWaiveRemainder() == 1) {
                unwaiveSourceRemainder(d.getSourceType(), d.getSourceId(), action);
            }
            d.setSettledQty("未冲销");
            d.setSettledAt(null);
            billWriteoffDetailMapper.updateById(d);
        }
    }

    /** 多付自动生成的预付款单回滚：已生效阻止操作，草稿删除 */
    private void rollbackOveragePrePayment(PaymentVoucher voucher) {
        List<PurchasePrePayment> list = purchasePrePaymentService.list(new QueryWrapper<PurchasePrePayment>()
                .eq("source_type", "PAYMENT_VOUCHER_OVERAGE")
                .eq("source_bill_id", voucher.getPaymentId()));
        if (list == null || list.isEmpty()) return;
        for (PurchasePrePayment p : list) {
            if (p.getStatus() != null && p.getStatus() != 0) {
                throw new BusinessException("付款单[" + voucher.getPaymentNo() + "]多付自动生成的预付款单["
                        + p.getPaymentNo() + "]已生效，请先处理该预付款单再操作");
            }
        }
        for (PurchasePrePayment p : list) {
            // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
            billCascadeDeleteService.deleteChildren("purchase_pre_payment", p.getId());
            purchasePrePaymentService.removeById(p.getId());
            log.info("付款单[{}]回滚：删除多付自动生成的草稿预付款单[{}]", voucher.getPaymentNo(), p.getPaymentNo());
        }
    }

    /**
     * 应付单正向付款：累计已付/余额/状态（部分付款/已结清），返回累计已付
     */
    private BigDecimal applyApPayment(AccountsPayable ap, BigDecimal amount) {
        BigDecimal paid = ap.getPaidAmount() == null ? BigDecimal.ZERO : ap.getPaidAmount();
        BigDecimal total = ap.getTotalAmount() == null ? BigDecimal.ZERO : ap.getTotalAmount();
        BigDecimal newPaid = paid.add(amount);
        ap.setPaidAmount(newPaid);
        ap.setBalanceAmount(total.subtract(newPaid));
        if (total.compareTo(BigDecimal.ZERO) > 0 && newPaid.compareTo(total) >= 0) {
            ap.setStatus("已结清");
            ap.setIsClosed((short) 1);
        } else if (newPaid.compareTo(BigDecimal.ZERO) > 0) {
            ap.setStatus("部分付款");
        }
        apMapper.updateById(ap);
        return newPaid;
    }

    /**
     * 驳回付款单（待审核→草稿）
     */
    @Override
    @Transactional
    public void rejectVoucher(Long id, String rejector, String reason) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款单不存在");
        String st = voucher.getStatus();
        if (!"待审核".equals(st) && !"PENDING".equals(st) && !"待审批".equals(st) && !"审批中".equals(st)) {
            throw new BusinessException("只有待审核状态可驳回，当前状态: " + st);
        }
        voucher.setStatus("草稿");
        voucher.setApprovalStatus("驳回");
        voucher.setUpdatedBy(rejector);
        voucher.setUpdatedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);
        log.info("付款单驳回: {}, 驳回人: {}, 原因: {}", voucher.getPaymentNo(), rejector, reason);
    }

    /**
     * 重置草稿付款单（已确认→草稿），回减应付单已付金额
     */
    @Override
    @Transactional
    public void reverseVoucher(Long id, String operator) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款记录不存在");
        String st = voucher.getStatus();
        if (!"已确认".equals(st) && !"CONFIRMED".equals(st) && !"已生效".equals(st)) {
            throw new BusinessException("只有已确认状态可重置草稿，当前状态: " + st);
        }
        if (voucher.getCloseStatus() != null && voucher.getCloseStatus() == 1) {
            throw new BusinessException("付款单[" + voucher.getPaymentNo() + "]已结案，不能重置草稿");
        }

        // 回减应付单已付金额（与 confirmVoucher 回写对称）
        rollbackVoucherWriteback(voucher, "重置草稿");

        voucherMapper.update(null, Wrappers.<PaymentVoucher>lambdaUpdate()
                .eq(PaymentVoucher::getPaymentId, id)
                .set(PaymentVoucher::getStatus, "草稿")
                .set(PaymentVoucher::getApprovalStatus, "待审核")
                .set(PaymentVoucher::getApprovedBy, null)
                .set(PaymentVoucher::getApprovedAt, null));
        log.info("付款重置草稿: {}, 操作人: {}", voucher.getPaymentNo(), operator);
    }

    /**
     * 作废付款单：记录作废前状态（供反作废恢复），已确认单据回减应付单已付金额
     */
    @Override
    @Transactional
    public void voidVoucher(Long id, String operator, String reason) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款记录不存在");
        String st = voucher.getStatus();
        if ("已作废".equals(st) || "VOID".equals(st)) {
            throw new BusinessException("付款单已作废，不能重复作废");
        }
        if (voucher.getCloseStatus() != null && voucher.getCloseStatus() == 1) {
            throw new BusinessException("付款单[" + voucher.getPaymentNo() + "]已结案，如需作废请先反结案");
        }

        int beforeStatus = BillStatusUtils.toNumber(st); // 0草稿/1待审核/2已确认
        // 已确认单据作废需回减业务影响（应付单已付金额）
        if (beforeStatus == 2) {
            rollbackVoucherWriteback(voucher, "作废");
        }

        voucher.setStatus("已作废");
        voucher.setApprovalStatus("已作废");
        voucher.setBillBeforeStatus(beforeStatus);
        voucher.setVoidedBy(operator);
        voucher.setVoidedAt(java.time.LocalDateTime.now());
        voucher.setVoidReason(reason);
        voucher.setUpdatedBy(operator);
        voucher.setUpdatedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);
        log.info("付款作废: {}, 作废前状态: {}, 操作人: {}, 原因: {}", voucher.getPaymentNo(), st, operator, reason);
    }

    /**
     * 反作废付款单（已作废→恢复作废前状态）
     * 作废前为已确认时重新回写应付单（与作废时的回减对称）
     */
    @Override
    @Transactional
    public void unvoidVoucher(Long id, String operator) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款记录不存在");
        String st = voucher.getStatus();
        if (!"已作废".equals(st) && !"VOID".equals(st)) {
            throw new BusinessException("只有已作废状态可反作废，当前状态: " + st);
        }

        Integer before = voucher.getBillBeforeStatus();
        int target = before == null ? 0 : before; // 未记录作废前状态时容错回草稿
        String targetLabel = financeStatusFromCode(target);

        voucherMapper.update(null, Wrappers.<PaymentVoucher>lambdaUpdate()
                .eq(PaymentVoucher::getPaymentId, id)
                .set(PaymentVoucher::getStatus, targetLabel)
                .set(PaymentVoucher::getApprovalStatus, target == 2 ? "已生效" : "待审核")
                .set(PaymentVoucher::getBillBeforeStatus, null)
                .set(PaymentVoucher::getVoidedBy, null)
                .set(PaymentVoucher::getVoidedAt, null)
                .set(PaymentVoucher::getVoidReason, null)
                .set(PaymentVoucher::getUpdatedBy, operator)
                .set(PaymentVoucher::getUpdatedAt, java.time.LocalDateTime.now()));

        // 作废前是已确认态 → 重新回写应付单（与作废时的回减对称）
        if (target == 2) {
            applyVoucherWriteback(voucher, operator);
        }
        log.info("付款反作废: {}, 恢复状态: {}, 操作人: {}", voucher.getPaymentNo(), targetLabel, operator);
    }

    /**
     * 结案付款单（已确认→已结案，终态1）
     */
    @Override
    @Transactional
    public void closeVoucher(Long id, String operator, String reason) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款单不存在");
        String st = voucher.getStatus();
        if (BillStatusUtils.toNumber(st) != 2) {
            throw new BusinessException("只有已确认状态可结案，当前状态: " + st);
        }
        if (voucher.getCloseStatus() != null && voucher.getCloseStatus() == 1) {
            throw new BusinessException("付款单[" + voucher.getPaymentNo() + "]已结案，不能重复结案");
        }
        voucher.setCloseStatus(1);
        voucher.setClosedBy(operator);
        voucher.setClosedAt(java.time.LocalDateTime.now());
        voucher.setClosedReason(reason);
        voucherMapper.updateById(voucher);
        log.info("付款单结案: {}, 操作人: {}, 原因: {}", voucher.getPaymentNo(), operator, reason);
    }

    /**
     * 反结案付款单（已结案→未结案）
     */
    @Override
    @Transactional
    public void reopenVoucher(Long id, String operator) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款单不存在");
        if (voucher.getCloseStatus() == null || voucher.getCloseStatus() != 1) {
            throw new BusinessException("只有已结案状态可反结案");
        }
        voucher.setCloseStatus(0);
        voucher.setClosedBy(operator);
        voucher.setClosedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);
        log.info("付款单反结案: {}, 操作人: {}", voucher.getPaymentNo(), operator);
    }

    /**
     * 删除付款单（仅草稿状态可删）
     */
    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款记录不存在");
        String st = voucher.getStatus();
        if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(st))) {
            throw new BusinessException("非草稿不能删除（当前状态：" + st + "），请先重置草稿");
        }
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("cash_payment", id);
        voucherMapper.deleteById(id);
        log.info("付款删除: {}", voucher.getPaymentNo());
    }

    // ========== 财务报表 ==========

    @Override
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        // 【2026-10-19 设计图 v3】系统不生成应收单：应收侧统计改按「销售订单未收金额」口径
        // （订单价税合计 net_amount − 订单已收 received_amount），不再读取应收单表。
        // 数据权限：与销售订单列表同口径（saler_id 归属）
        QueryWrapper<SaleOrder> soWrapper = new QueryWrapper<>();
        soWrapper.eq("status", "已生效");
        applySummaryDataScope(soWrapper, "saler_id");
        List<SaleOrder> soList = saleOrderService.list(soWrapper);
        BigDecimal totalAr = BigDecimal.ZERO;
        BigDecimal receivedAr = BigDecimal.ZERO;
        int arCount = 0;
        int overdueCount = 0;
        LocalDate today = LocalDate.now();
        for (SaleOrder so : soList) {
            BigDecimal net = so.getNetAmount() != null ? so.getNetAmount() : BigDecimal.ZERO;
            BigDecimal rec = so.getReceivedAmount() != null ? so.getReceivedAmount() : BigDecimal.ZERO;
            totalAr = totalAr.add(net);
            receivedAr = receivedAr.add(rec);
            arCount++;
            if (net.subtract(rec).compareTo(BigDecimal.ZERO) > 0) {
                LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
                if (base != null && base.isBefore(today)) overdueCount++;
            }
        }

        // 数据权限：与「应付账款」列表同口径（buyer_id 归属 + created_by 创建人）
        QueryWrapper<AccountsPayable> apWrapper = new QueryWrapper<>();
        applySummaryDataScope(apWrapper, "buyer_id");
        List<AccountsPayable> apList = apMapper.selectList(apWrapper);
        BigDecimal totalAp = BigDecimal.ZERO;
        BigDecimal paidAp = BigDecimal.ZERO;
        int apCount = 0;
        for (AccountsPayable ap : apList) {
            totalAp = totalAp.add(ap.getTotalAmount() != null ? ap.getTotalAmount() : BigDecimal.ZERO);
            paidAp = paidAp.add(ap.getPaidAmount() != null ? ap.getPaidAmount() : BigDecimal.ZERO);
            apCount++;
        }

        summary.put("totalAr", totalAr);
        summary.put("balanceAr", totalAr.subtract(receivedAr));
        summary.put("receivedAr", receivedAr);
        summary.put("arCount", arCount);
        summary.put("overdueArCount", overdueCount);

        summary.put("totalAp", totalAp);
        summary.put("balanceAp", totalAp.subtract(paidAp));
        summary.put("paidAp", paidAp);
        summary.put("apCount", apCount);

        summary.put("netReceivable", totalAr.subtract(receivedAr).subtract(totalAp.subtract(paidAp)));

        return summary;
    }

    @Override
    public List<Map<String, Object>> getAgingAnalysis() {
        List<Map<String, Object>> result = new ArrayList<>();

        // 【2026-10-19 设计图 v3】账龄分析改按销售订单口径：未收余额 = 订单价税合计 − 订单已收；
        // 账龄起算日 = 发货日期（无发货取订单日期，用户已确认）；不再读取应收单表。
        QueryWrapper<SaleOrder> soWrapper = new QueryWrapper<>();
        soWrapper.eq("status", "已生效");
        // 数据权限：与销售订单列表同口径（saler_id 归属）
        applySummaryDataScope(soWrapper, "saler_id");
        List<SaleOrder> soList = saleOrderService.list(soWrapper);

        int bucket0_30 = 0, bucket30_60 = 0, bucket60_90 = 0, bucket90_180 = 0, bucket180_plus = 0;
        BigDecimal amt0_30 = BigDecimal.ZERO, amt30_60 = BigDecimal.ZERO, amt60_90 = BigDecimal.ZERO,
                  amt90_180 = BigDecimal.ZERO, amt180_plus = BigDecimal.ZERO;

        LocalDate today = LocalDate.now();
        for (SaleOrder so : soList) {
            BigDecimal net = so.getNetAmount() != null ? so.getNetAmount() : BigDecimal.ZERO;
            BigDecimal rec = so.getReceivedAmount() != null ? so.getReceivedAmount() : BigDecimal.ZERO;
            BigDecimal bal = net.subtract(rec);
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue; // 已收完的不进账龄
            LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
            if (base == null) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(base, today);

            if (days <= 0) {
                amt0_30 = amt0_30.add(bal);
                bucket0_30++;
            } else if (days <= 30) {
                amt30_60 = amt30_60.add(bal);
                bucket30_60++;
            } else if (days <= 60) {
                amt60_90 = amt60_90.add(bal);
                bucket60_90++;
            } else if (days <= 180) {
                amt90_180 = amt90_180.add(bal);
                bucket90_180++;
            } else {
                amt180_plus = amt180_plus.add(bal);
                bucket180_plus++;
            }
        }

        result.add(Map.of("bucket", "1-30天", "count", bucket0_30, "amount", amt0_30));
        result.add(Map.of("bucket", "31-60天", "count", bucket30_60, "amount", amt30_60));
        result.add(Map.of("bucket", "61-90天", "count", bucket60_90, "amount", amt60_90));
        result.add(Map.of("bucket", "91-180天", "count", bucket90_180, "amount", amt90_180));
        result.add(Map.of("bucket", "180天以上", "count", bucket180_plus, "amount", amt180_plus));

        return result;
    }

    // ========== 单号生成 ==========

    private String generateArNo() {
        // 应收单由系统自动生成（无手工录入入口），不受"关闭自动编号"开关限制，始终生成
        try {
            // 带业务表防重抬底：以应收单真实已用流水续号，杜绝计数落后导致的重复
            return sysSerialNoConfigService.generateSerialNo("ACCOUNT_RECEIVABLE", this::nextArNo);
        } catch (Exception e) {
            String prefix = "AR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            QueryWrapper<AccountsReceivable> wrapper = new QueryWrapper<>();
            wrapper.likeRight("ar_no", prefix).orderByDesc("ar_no").last("LIMIT 1");
            AccountsReceivable last = arMapper.selectOne(wrapper);
            int seq = 1;
            if (last != null && last.getArNo() != null) {
                try { seq = Integer.parseInt(last.getArNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
            }
            return prefix + String.format("%04d", seq);
        }
    }

    /**
     * 防重续号探测：查询应收单表当前编号段已用最大单号，返回"下一张完整单号"。
     * 手动/历史产生的应收单不会推进自动编号计数，以业务表真实流水抬底续号。
     */
    private String nextArNo(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        QueryWrapper<AccountsReceivable> wrapper = new QueryWrapper<>();
        wrapper.likeRight("ar_no", prefix).orderByDesc("ar_no").last("LIMIT 1");
        AccountsReceivable last = arMapper.selectOne(wrapper);
        if (last == null || last.getArNo() == null) {
            return null;
        }
        try {
            return prefix + (Integer.parseInt(last.getArNo().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    private String generateApNo() {
        // 应付单由系统自动生成（无手工录入入口），不受"关闭自动编号"开关限制，始终生成
        try {
            // 带业务表防重抬底：以应付单真实已用流水续号，杜绝计数落后导致的重复
            return sysSerialNoConfigService.generateSerialNo("ACCOUNT_PAYABLE", this::nextApNo);
        } catch (Exception e) {
            String prefix = "AP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
            wrapper.likeRight("ap_no", prefix).orderByDesc("ap_no").last("LIMIT 1");
            AccountsPayable last = apMapper.selectOne(wrapper);
            int seq = 1;
            if (last != null && last.getApNo() != null) {
                try { seq = Integer.parseInt(last.getApNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
            }
            return prefix + String.format("%04d", seq);
        }
    }

    /**
     * 防重续号探测：查询应付单表当前编号段已用最大单号，返回"下一张完整单号"。
     * 手动/历史产生的应付单不会推进自动编号计数，以业务表真实流水抬底续号。
     */
    private String nextApNo(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
        wrapper.likeRight("ap_no", prefix).orderByDesc("ap_no").last("LIMIT 1");
        AccountsPayable last = apMapper.selectOne(wrapper);
        if (last == null || last.getApNo() == null) {
            return null;
        }
        try {
            return prefix + (Integer.parseInt(last.getApNo().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按主键读取收款单（含明细，明细可为空；供销售侧判断自动生成收款单的当前状态）。
     */
    @Override
    public PaymentReceipt getReceiptById(Long id) {
        if (id == null) return null;
        PaymentReceipt r = receiptMapper.selectById(id);
        if (r == null) return null;
        r.setItems(receiptItemMapper.selectList(new QueryWrapper<PaymentReceiptItem>()
                .eq("receipt_id", id).orderByAsc("sort_no", "item_id")));
        r.setWriteOffItems(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "收款单").eq("bill_id", id).orderByAsc("writeoff_id")));
        return r;
    }

    private String generateReceiptNo() {
        String docType = "RECEIPT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "REC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            QueryWrapper<PaymentReceipt> wrapper = new QueryWrapper<>();
            wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
            PaymentReceipt last = receiptMapper.selectOne(wrapper);
            int seq = 1;
            if (last != null && last.getReceiptNo() != null) {
                try { seq = Integer.parseInt(last.getReceiptNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
            }
            return prefix + String.format("%04d", seq);
        });
    }

    private String generateVoucherNo() {
        String docType = "PAYMENT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PAY" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            QueryWrapper<PaymentVoucher> wrapper = new QueryWrapper<>();
            wrapper.likeRight("payment_no", prefix).orderByDesc("payment_no").last("LIMIT 1");
            PaymentVoucher last = voucherMapper.selectOne(wrapper);
            int seq = 1;
            if (last != null && last.getPaymentNo() != null) {
                try { seq = Integer.parseInt(last.getPaymentNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public PageResult<Map<String, Object>> listArSourceBills(String sourceType, int current, int size, String keyword) {
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        if ("sale_delivery".equals(sourceType)) {
            // 查询已生效且未生成应收的出库单
            QueryWrapper<SaleDelivery> wrapper = new QueryWrapper<>();
            wrapper.eq("approval_status", "已生效").orderByDesc("delivery_date");
            if (keyword != null && !keyword.isBlank()) {
                wrapper.and(w -> w.like("delivery_no", keyword).or().like("customer_id", keyword));
            }
            // 数据权限：与「应收账款」列表同口径（saler_id 归属 + created_by 创建人）
            applySummaryDataScope(wrapper, "saler_id");
            List<SaleDelivery> list = saleDeliveryService.list(wrapper);
            int total = list.size();
            int fromIndex = (current - 1) * size;
            int toIndex = Math.min(fromIndex + size, total);
            if (fromIndex < total) {
                for (SaleDelivery d : list.subList(fromIndex, toIndex)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("billId", d.getDeliveryId());
                    m.put("billNo", d.getDeliveryNo());
                    m.put("billDate", d.getDeliveryDate());
                    m.put("customerId", d.getCustomerId());
                    m.put("totalAmount", d.getTotalAmount());
                    m.put("sourceType", "sale_delivery");
                    m.put("sourceTypeLabel", "销售出库单");
                    // 查客户名
                    if (d.getCustomerId() != null) {
                        Customer c = customerService.getById(d.getCustomerId());
                        m.put("customerName", c != null ? c.getCustomerName() : "");
                    } else {
                        m.put("customerName", "");
                    }
                    records.add(m);
                }
            }
            return PageResult.of(records, total, current, size);
        } else if ("sale_invoice".equals(sourceType)) {
            QueryWrapper<SaleInvoice> wrapper = new QueryWrapper<>();
            wrapper.eq("approval_status", "已生效").orderByDesc("invoice_date");
            if (keyword != null && !keyword.isBlank()) {
                wrapper.and(w -> w.like("invoice_no", keyword).or().like("customer_id", keyword));
            }
            // 数据权限：与「应收账款」列表同口径（saler_id 归属 + created_by 创建人）
            applySummaryDataScope(wrapper, "saler_id");
            List<SaleInvoice> list = saleInvoiceService.list(wrapper);
            int total = list.size();
            int fromIndex = (current - 1) * size;
            int toIndex = Math.min(fromIndex + size, total);
            if (fromIndex < total) {
                for (SaleInvoice inv : list.subList(fromIndex, toIndex)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("billId", inv.getInvoiceId());
                    m.put("billNo", inv.getInvoiceNo());
                    m.put("billDate", inv.getInvoiceDate());
                    m.put("customerId", inv.getCustomerId());
                    m.put("totalAmount", inv.getTotalAmount());
                    m.put("sourceType", "sale_invoice");
                    m.put("sourceTypeLabel", "销售发票");
                    if (inv.getCustomerId() != null) {
                        Customer c = customerService.getById(inv.getCustomerId());
                        m.put("customerName", c != null ? c.getCustomerName() : "");
                    } else {
                        m.put("customerName", "");
                    }
                    records.add(m);
                }
            }
            return PageResult.of(records, total, current, size);
        }
        return PageResult.of(records, 0, current, size);
    }

    // ========== 应收/应付 账款汇总报表 ==========

    /**
     * 应收账款汇总报表：按客户聚合
     * 字段说明：
     *   - 期初应收/期初预收：当前数据未做历史期间切分，统一置 0
     *   - 本期应收：所选区间内新增的应收总额
     *   - 本期已收：所选区间内的累计已收金额
     *   - 本期预收：预收款未单独建模，置 0
     *   - 应收结余(含预收) = 应收结余 = 本期应收 - 本期已收
     */
    @Override
    public Map<String, Object> getReceivableSummary(LocalDate startDate, LocalDate endDate,
                                                     Long currencyId, Long customerId, String customerType) {
        return getReceivableSummary(startDate, endDate, currencyId, customerId, customerType, null, null);
    }

    @Override
    public Map<String, Object> getReceivableSummary(LocalDate startDate, LocalDate endDate,
                                                     Long currencyId, Long customerId, String customerType,
                                                     Boolean overdueOnly, Integer agingOver) {
        // 【2026-10-19 设计图 v3】系统不生成应收单：应收汇总改按「销售订单」口径，
        // 本期发生=区间内订单价税合计，本期已收=订单累计已收，结余=价税合计−已收；不再读取应收单表。
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "已生效");
        if (startDate != null) wrapper.ge("order_date", startDate);
        if (endDate != null) wrapper.le("order_date", endDate);
        if (currencyId != null) wrapper.eq("currency_id", currencyId);
        if (customerId != null) wrapper.eq("customer_id", customerId);
        // 数据权限：与销售订单列表同口径（saler_id 归属），汇总/导出共用
        applySummaryDataScope(wrapper, "saler_id");
        SortHelper.orderByNewest(wrapper, SaleOrder.class);
        List<SaleOrder> soList = saleOrderService.list(wrapper);

        // 按客户聚合
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal receivedAmount = BigDecimal.ZERO;
        BigDecimal unreceivedAmount = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        for (SaleOrder so : soList) {
            BigDecimal total = so.getNetAmount() != null ? so.getNetAmount() : BigDecimal.ZERO;
            BigDecimal paid = so.getReceivedAmount() != null ? so.getReceivedAmount() : BigDecimal.ZERO;
            // 数据看板穿透：仅逾期 / 逾期≥N天（账龄起算日=发货日期，无发货取订单日期；不影响默认查询）
            if (Boolean.TRUE.equals(overdueOnly) || (agingOver != null && agingOver > 0)) {
                int days = agingOver != null && agingOver > 0 ? agingOver : 1;
                if (total.subtract(paid).compareTo(BigDecimal.ZERO) <= 0) continue;
                LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
                if (base == null || !base.isBefore(today.minusDays(days))) continue;
            }
            Long cid = so.getCustomerId() != null ? so.getCustomerId() : 0L;
            Map<String, Object> row = grouped.computeIfAbsent(cid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("customerId", cid);
                m.put("customerCode", "");
                m.put("customerName", so.getCustomerName() != null ? so.getCustomerName() : "");
                m.put("customerType", "");
                m.put("beginReceivable", BigDecimal.ZERO);
                m.put("beginPreReceived", BigDecimal.ZERO);
                m.put("currentReceivable", BigDecimal.ZERO);
                m.put("currentReceived", BigDecimal.ZERO);
                m.put("currentPreReceived", BigDecimal.ZERO);
                return m;
            });
            row.put("currentReceivable", ((BigDecimal) row.get("currentReceivable")).add(total));
            row.put("currentReceived", ((BigDecimal) row.get("currentReceived")).add(paid));

            totalAmount = totalAmount.add(total);
            receivedAmount = receivedAmount.add(paid);

            // 超期金额：起算日 < 今天 且 未付清
            BigDecimal balance = total.subtract(paid);
            LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
            if (balance.compareTo(BigDecimal.ZERO) > 0 && base != null && base.isBefore(today)) {
                overdueAmount = overdueAmount.add(balance);
            }
        }

        // 填充客户基础信息（编码/类型）并计算结余
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : grouped.values()) {
            Long cid = (Long) row.get("customerId");
            if (cid != null && cid > 0) {
                Customer c = customerService.getById(cid);
                if (c != null) {
                    if (c.getCustomerCode() != null) row.put("customerCode", c.getCustomerCode());
                    if (c.getCustomerType() != null) row.put("customerType", c.getCustomerType());
                    if (row.get("customerName") == null || "".equals(row.get("customerName"))) {
                        row.put("customerName", c.getCustomerName());
                    }
                }
            }
            // 客户类型过滤（需要在聚合后做，因为客户类型来自 customer 表）
            if (customerType != null && !customerType.isBlank()
                    && !customerType.equals(row.get("customerType"))) {
                continue;
            }
            BigDecimal currentReceivable = (BigDecimal) row.get("currentReceivable");
            BigDecimal currentReceived = (BigDecimal) row.get("currentReceived");
            BigDecimal endBalance = currentReceivable.subtract(currentReceived);
            row.put("endBalance", endBalance);
            row.put("endBalanceWithPre", endBalance); // 暂无预收单独数据
            unreceivedAmount = unreceivedAmount.add(endBalance);
            records.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", records.size());
        result.put("totalAmount", totalAmount);
        result.put("receivedAmount", receivedAmount);
        result.put("unreceivedAmount", unreceivedAmount);
        result.put("overdueAmount", overdueAmount);
        return result;
    }

    /**
     * 应付账款汇总报表：按供应商聚合
     */
    @Override
    public Map<String, Object> getPayableSummary(LocalDate startDate, LocalDate endDate,
                                                 Long currencyId, Long supplierId, String supplierType) {
        QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
        if (startDate != null) wrapper.ge("ap_date", startDate);
        if (endDate != null) wrapper.le("ap_date", endDate);
        if (currencyId != null) wrapper.eq("currency_id", currencyId);
        if (supplierId != null) wrapper.eq("supplier_id", supplierId);
        // 数据权限：与「应付账款」列表同口径（applyTo(wrapper, "buyer_id")），汇总/导出共用
        applySummaryDataScope(wrapper, "buyer_id");
        SortHelper.orderByNewest(wrapper, AccountsPayable.class);
        List<AccountsPayable> apList = apMapper.selectList(wrapper);

        // 按供应商聚合
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal unpaidAmount = BigDecimal.ZERO;

        for (AccountsPayable ap : apList) {
            Long sid = ap.getSupplierId() != null ? ap.getSupplierId() : 0L;
            Map<String, Object> row = grouped.computeIfAbsent(sid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("supplierId", sid);
                m.put("supplierCode", "");
                m.put("supplierName", ap.getSupplierName() != null ? ap.getSupplierName() : "");
                m.put("supplierType", "");
                m.put("beginPayable", BigDecimal.ZERO);
                m.put("beginPrePaid", BigDecimal.ZERO);
                m.put("currentPayable", BigDecimal.ZERO);
                m.put("currentPaid", BigDecimal.ZERO);
                m.put("currentPrePaid", BigDecimal.ZERO);
                return m;
            });
            BigDecimal total = ap.getTotalAmount() != null ? ap.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal paid = ap.getPaidAmount() != null ? ap.getPaidAmount() : BigDecimal.ZERO;
            row.put("currentPayable", ((BigDecimal) row.get("currentPayable")).add(total));
            row.put("currentPaid", ((BigDecimal) row.get("currentPaid")).add(paid));

            totalAmount = totalAmount.add(total);
            paidAmount = paidAmount.add(paid);
        }

        // 填充供应商基础信息并计算结余
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : grouped.values()) {
            Long sid = (Long) row.get("supplierId");
            if (sid != null && sid > 0) {
                Supplier s = supplierService.getById(sid);
                if (s != null) {
                    if (s.getSupplierCode() != null) row.put("supplierCode", s.getSupplierCode());
                    if (s.getSupplierType() != null) row.put("supplierType", s.getSupplierType());
                    if (row.get("supplierName") == null || "".equals(row.get("supplierName"))) {
                        row.put("supplierName", s.getSupplierName());
                    }
                }
            }
            // 供应商类型过滤
            if (supplierType != null && !supplierType.isBlank()
                    && !supplierType.equals(row.get("supplierType"))) {
                continue;
            }
            BigDecimal currentPayable = (BigDecimal) row.get("currentPayable");
            BigDecimal currentPaid = (BigDecimal) row.get("currentPaid");
            BigDecimal endBalance = currentPayable.subtract(currentPaid);
            row.put("endBalance", endBalance);
            row.put("endBalanceWithPre", endBalance);
            unpaidAmount = unpaidAmount.add(endBalance);
            records.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", records.size());
        result.put("totalAmount", totalAmount);
        result.put("paidAmount", paidAmount);
        result.put("unpaidAmount", unpaidAmount);
        return result;
    }

    // ============ 审批引擎回调桥接（BillApprovalCallback：收款单 + 付款单共用本 Service） ============

    /** 主身份编码：收款单（付款单见 supportedBillTypes） */
    @Override
    public String getBillType() {
        return "PAYMENT_RECEIPT";
    }

    /** 本服务承载两个审批身份：收款单 / 付款单（两表主键各自自增，回写必须按审批任务身份分流） */
    @Override
    public java.util.Collection<String> supportedBillTypes() {
        return List.of("PAYMENT_RECEIPT", "PAYMENT_VOUCHER");
    }

    /** 审批通过（无身份的旧调用，沿用收款单，保持向后兼容） */
    @Override
    @Transactional
    public void onApprovalApproved(Long billId, String approver, String comment) {
        onApprovalApproved(getBillType(), billId, approver, comment);
    }

    /**
     * 审批通过（带单据身份）→ 收款单走 confirmReceipt、付款单走 confirmVoucher（各自真正的生效动作）。
     * 身份由审批引擎传入（task.getBillType()）：cash_receipt.receipt_id 与 cash_payment.payment_id
     * 各自自增、存在同号，仅靠 billId 无法区分身份，禁止再按「刚终结的审批任务」猜测。
     */
    @Override
    @Transactional
    public void onApprovalApproved(String billType, Long billId, String approver, String comment) {
        String who = approver != null && !approver.isBlank() ? approver : "系统(自动通过)";
        if ("PAYMENT_VOUCHER".equals(billType)) {
            confirmVoucher(billId, who);
        } else {
            confirmReceipt(billId, who);
        }
    }

    /** 审批驳回（无身份的旧调用，沿用收款单，保持向后兼容） */
    @Override
    @Transactional
    public void onApprovalRejected(Long billId, String rejector, String comment) {
        onApprovalRejected(getBillType(), billId, rejector, comment);
    }

    /** 审批驳回（带单据身份）→ 收款单走 rejectReceipt、付款单走 rejectVoucher（均退回草稿 + approval_status=驳回） */
    @Override
    @Transactional
    public void onApprovalRejected(String billType, Long billId, String rejector, String comment) {
        if ("PAYMENT_VOUCHER".equals(billType)) {
            rejectVoucher(billId, rejector, comment);
        } else {
            rejectReceipt(billId, rejector, comment);
        }
    }
}
