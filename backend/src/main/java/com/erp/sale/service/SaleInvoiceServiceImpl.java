package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.config.service.SysConfigService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.finance.service.FinanceService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleInvoiceItem;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleReturnItem;
import com.erp.sale.mapper.SaleDeliveryItemMapper;
import com.erp.sale.mapper.SaleInvoiceItemMapper;
import com.erp.sale.mapper.SaleInvoiceMapper;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleReturnItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.common.util.TaxAmountUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SaleInvoiceServiceImpl extends AbstractBillService<SaleInvoiceMapper, SaleInvoice> implements SaleInvoiceService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 全面推广：销售发票）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值的列名优先按 SaleInvoice 实体属性名解析（框架元数据自动转列名），
        // 仅「按名称筛但列是外键」处直接写数据库列名。字段解释见 BillHeaderFilterSupport。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("invoiceNo", new BillHeaderFilterSupport.Spec("invoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxInvoiceNo", new BillHeaderFilterSupport.Spec("taxInvoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceType", new BillHeaderFilterSupport.Spec("invoiceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxNo", new BillHeaderFilterSupport.Spec("taxNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("address", new BillHeaderFilterSupport.Spec("address", BillHeaderFilterSupport.Kind.TEXT));
        f.put("bankName", new BillHeaderFilterSupport.Spec("bankName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("bankAccount", new BillHeaderFilterSupport.Spec("bankAccount", BillHeaderFilterSupport.Kind.TEXT));
        f.put("arNo", new BillHeaderFilterSupport.Spec("arNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryNo", new BillHeaderFilterSupport.Spec("deliveryNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("originalInvoiceNo", new BillHeaderFilterSupport.Spec("originalInvoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("opportunityNo", new BillHeaderFilterSupport.Spec("opportunityNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("saler_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currency_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("settleModeName", new BillHeaderFilterSupport.Spec("settle_mode_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("invoiceDate", new BillHeaderFilterSupport.Spec("invoiceDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxRateAvg", new BillHeaderFilterSupport.Spec("taxRateAvg", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidRate", new BillHeaderFilterSupport.Spec("prepaidRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidAmount", new BillHeaderFilterSupport.Spec("prepaidAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("saleInvoice", SaleInvoice.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "address",
            "approval_status",
            "approved_at",
            "approved_by",
            "ar_no",
            "attachment_urls",
            "bank_account",
            "bank_name",
            "created_at",
            "created_by",
            "currency_id",
            "customer_id",
            "delivery_no",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "generate_ar",
            "invoice_date",
            "invoice_id",
            "invoice_no",
            "invoice_type",
            "is_red_invoice",
            "line_no",
            "net_amount",
            "opportunity_id",
            "opportunity_no",
            "org_code",
            "original_invoice_id",
            "original_invoice_no",
            "remark",
            "sale_invoice",
            "sale_order",
            "saler_id",
            "source_bill_no",
            "source_bill_type",
            "source_item_id",
            "status",
            "tax_amount",
            "tax_no",
            "tax_rate_avg",
            "total_amount",
            "turned_invoice_quantity",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SaleInvoiceItemMapper itemMapper;
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SaleOrderService saleOrderService;
    private final SaleDeliveryItemMapper saleDeliveryItemMapper;
    private final SaleReturnItemMapper saleReturnItemMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    /** 下游引用校验（2026-11-09 统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    // 审批开关、审批引擎、自定义字段、整单删除级联均由统一状态机基类 protected 注入，本类不再重复注入

    @Autowired
    @Lazy
    private FinanceService financeService;

    @Autowired
    private SysConfigService sysConfigService;

    private final SysParamValidator sysParamValidator;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售发票"; }
    @Override protected String getApprovalBillType() { return "SALE_INVOICE"; }
    @Override protected String getBillNo(SaleInvoice b) { return b.getInvoiceNo(); }
    @Override protected Object getId(SaleInvoice b) { return b.getInvoiceId(); }
    @Override protected String getStatus(SaleInvoice b) { return b.getStatus(); }
    @Override protected void setStatus(SaleInvoice b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleInvoice b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleInvoice b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleInvoice b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleInvoice b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleInvoice b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleInvoice b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleInvoice b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleInvoice b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleInvoice b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleInvoice b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleInvoice b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleInvoice b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleInvoice b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SaleInvoice bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getInvoiceNo());
    }

    @Override
    @Transactional
    public void saveInvoiceWithItems(SaleInvoice saleInvoice, List<SaleInvoiceItem> items) {
        boolean isNew = saleInvoice.getInvoiceId() == null;
        if (!isNew) {
            SaleInvoice existing = getById(saleInvoice.getInvoiceId());
            if (existing != null) {
                BillStatusUtils.assertDirectModifiable("销售发票", existing.getInvoiceNo(), existing.getStatus(), existing.getCloseStatus());
            }
        }

        if (saleInvoice.getInvoiceNo() == null || saleInvoice.getInvoiceNo().isBlank()) {
            saleInvoice.setInvoiceNo(generateInvoiceNo());
        }

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (saleInvoice.getStatus() == null || saleInvoice.getStatus().isBlank()) saleInvoice.setStatus("草稿");
        if (saleInvoice.getApprovalStatus() == null || saleInvoice.getApprovalStatus().isBlank()
                || ("草稿".equals(saleInvoice.getStatus()) && "待审核".equals(saleInvoice.getApprovalStatus()))) {
            saleInvoice.setApprovalStatus("草稿");
        }
        if (saleInvoice.getExchangeRate() == null) saleInvoice.setExchangeRate(BigDecimal.ONE);
        if (saleInvoice.getDiscountRate() == null) saleInvoice.setDiscountRate(BigDecimal.ZERO);
        if (saleInvoice.getDiscountAmount() == null) saleInvoice.setDiscountAmount(BigDecimal.ZERO);
        if (saleInvoice.getTotalAmount() == null) saleInvoice.setTotalAmount(BigDecimal.ZERO);
        if (saleInvoice.getTaxAmount() == null) saleInvoice.setTaxAmount(BigDecimal.ZERO);
        if (saleInvoice.getNetAmount() == null) saleInvoice.setNetAmount(BigDecimal.ZERO);
        if (saleInvoice.getInvoiceDate() == null) saleInvoice.setInvoiceDate(LocalDate.now());
        if (saleInvoice.getIsRedInvoice() == null) saleInvoice.setIsRedInvoice((short) 0);

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SaleInvoiceItem> validItems = new ArrayList<>();
            for (SaleInvoiceItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        recalculateInvoice(saleInvoice, items);

        // 金额模式：保存即校验「剩余可开金额」（不等提交），超额直接拦下
        if (sysParamValidator.isInvoiceAmountMode()
                && (saleInvoice.getIsRedInvoice() == null || saleInvoice.getIsRedInvoice() == 0)
                && items != null && !items.isEmpty()) {
            validateSourceAmountMutex(saleInvoice, items, saleInvoice.getInvoiceId());
        }

        if (isNew) {
            save(saleInvoice);
        } else {
            updateById(saleInvoice);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SaleInvoiceItem>().eq("invoice_id", saleInvoice.getInvoiceId()));
            int lineNo = 1;
            for (SaleInvoiceItem item : items) {
                item.setInvoiceItemId(null);
                item.setInvoiceId(saleInvoice.getInvoiceId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("销售发票保存成功: {} 状态: {} 合计: {} 红字: {}", saleInvoice.getInvoiceNo(), saleInvoice.getStatus(), saleInvoice.getTotalAmount(), saleInvoice.getIsRedInvoice());
    }

    /**
     * 校验发票选单互斥规则（方案A：单一路径互斥）：
     * 销售订单与销售出库单"二选一"——只要其中一条路径已被任何发票引用过，另一条路径就互斥不能开票。
     *
     * 1. 销售订单作为源单：
     *    - 互斥：该订单明细的任一出库明细已被开票（sale_delivery_item.turned_invoice_quantity > 0）→ 禁止直接开票
     *    - 超量：本次 + 已开票 <= 订单数量
     * 2. 销售出库单作为源单：
     *    - 互斥：源销售订单明细已被任何发票直接引用（sale_order_item.turned_invoice_quantity > 0）→ 禁止从出库开票
     *    - 超量：本次 + 已开票 <= 出库数量
     * 3. 销售退货单作为源单：仅超量校验
     */
    private void validateInvoiceSourceMutex(SaleInvoice saleInvoice, List<SaleInvoiceItem> items, Long currentInvoiceId) {
        // 金额模式（对齐金蝶「发票只校验金额」）：不校验数量，改按金额核对来源单剩余可开金额
        if (sysParamValidator.isInvoiceAmountMode()) {
            validateSourceAmountMutex(saleInvoice, items, currentInvoiceId);
            return;
        }
        for (SaleInvoiceItem item : items) {
            if (item.getSourceId() == null || item.getSourceItemId() == null) {
                continue;
            }
            String sourceType = item.getSourceBillType();
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if ("销售订单".equals(sourceType)) {
                SaleOrderItem orderItem = saleOrderItemMapper.selectById(item.getSourceItemId());
                if (orderItem == null) {
                    throw new BusinessException("源销售订单明细不存在: " + item.getSourceItemId());
                }
                // 【2026-10-14 下推联动】来源订单已完结（已结案）不允许再开票
                if (orderItem.getOrderId() != null && saleOrderService.isOrderClosed(orderItem.getOrderId())) {
                    throw new BusinessException("销售订单【" + item.getSourceBillNo() + "】已完结（已结案），不能下推生成销售发票");
                }
                // 互斥：检查该订单明细的任一出库明细是否已被开票
                Long invoicedDeliveryCount = saleDeliveryItemMapper.selectCount(
                    new QueryWrapper<SaleDeliveryItem>()
                        .eq("source_item_id", orderItem.getOrderItemId())
                        .gt("turned_invoice_quantity", 0));
                if (invoicedDeliveryCount != null && invoicedDeliveryCount > 0) {
                    throw new BusinessException("销售订单【" + item.getSourceBillNo() + "】已通过销售出库单开过票，不能再从订单直接开票");
                }
                // 超量校验：本次开票数量 + 已开票数量 <= 订单数量
                BigDecimal turnedInvoice = orderItem.getTurnedInvoiceQuantity() != null ? orderItem.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                BigDecimal orderQty = orderItem.getQuantity() != null ? orderItem.getQuantity() : BigDecimal.ZERO;
                if (turnedInvoice.add(qty).compareTo(orderQty) > 0) {
                    throw new BusinessException("销售订单【" + item.getSourceBillNo() + "】明细开票数量超过订单数量（订单: " + orderQty + "，已开票: " + turnedInvoice + "，本次: " + qty + "）");
                }
            } else if ("销售出库单".equals(sourceType) || "销售出库".equals(sourceType)) {
                SaleDeliveryItem deliveryItem = saleDeliveryItemMapper.selectById(item.getSourceItemId());
                if (deliveryItem == null) {
                    throw new BusinessException("源销售出库明细不存在: " + item.getSourceItemId());
                }
                // 互斥：源销售订单已被任何发票直接引用 → 不能从出库开票
                if (deliveryItem.getSourceItemId() != null) {
                    SaleOrderItem orderItem = saleOrderItemMapper.selectById(deliveryItem.getSourceItemId());
                    if (orderItem != null) {
                        // 【2026-10-14 下推联动】来源订单已完结（已结案）不允许再开票
                        if (orderItem.getOrderId() != null && saleOrderService.isOrderClosed(orderItem.getOrderId())) {
                            throw new BusinessException("销售订单【" + deliveryItem.getSourceBillNo() + "】已完结（已结案），不能下推生成销售发票");
                        }
                        BigDecimal orderTurnedInvoice = orderItem.getTurnedInvoiceQuantity() != null ? orderItem.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                        if (orderTurnedInvoice.compareTo(BigDecimal.ZERO) > 0) {
                            throw new BusinessException("源销售订单【" + deliveryItem.getSourceBillNo() + "】已直接开过票，不能再从销售出库单开票");
                        }
                    }
                }
                // 超量校验：出库明细本次开票 + 已开票 <= 出库数量
                BigDecimal deliveryTurnedInvoice = deliveryItem.getTurnedInvoiceQuantity() != null ? deliveryItem.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                BigDecimal deliveryQty = deliveryItem.getQuantity() != null ? deliveryItem.getQuantity() : BigDecimal.ZERO;
                if (deliveryTurnedInvoice.add(qty).compareTo(deliveryQty) > 0) {
                    throw new BusinessException("销售出库单【" + item.getSourceBillNo() + "】明细开票数量超过出库数量（出库: " + deliveryQty + "，已开票: " + deliveryTurnedInvoice + "，本次: " + qty + "）");
                }
            } else if ("销售退货单".equals(sourceType)) {
                SaleReturnItem returnItem = saleReturnItemMapper.selectById(item.getSourceItemId());
                if (returnItem == null) {
                    throw new BusinessException("源销售退货明细不存在: " + item.getSourceItemId());
                }
                BigDecimal returnTurnedInvoice = returnItem.getTurnedInvoiceQuantity() != null ? returnItem.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                BigDecimal returnQty = returnItem.getQuantity() != null ? returnItem.getQuantity() : BigDecimal.ZERO;
                if (returnTurnedInvoice.add(qty).compareTo(returnQty) > 0) {
                    throw new BusinessException("销售退货单【" + item.getSourceBillNo() + "】明细开票数量超过退货数量（退货: " + returnQty + "，已开票: " + returnTurnedInvoice + "，本次: " + qty + "）");
                }
            }
        }
    }

    /**
     * 金额模式来源校验（对齐金蝶「发票只校验金额」）：
     * 同一来源明细「本次开票金额合计 + 已生效发票已开金额」不得超过来源明细的价税合计。
     * 数量不参与校验；来源订单已完结仍不允许继续开票。
     */
    private void validateSourceAmountMutex(SaleInvoice saleInvoice, List<SaleInvoiceItem> items, Long currentInvoiceId) {
        Map<String, BigDecimal> thisAmount = new java.util.LinkedHashMap<>();
        Map<String, SaleInvoiceItem> sample = new java.util.LinkedHashMap<>();
        for (SaleInvoiceItem item : items) {
            if (item.getSourceId() == null || item.getSourceItemId() == null) continue;
            String key = item.getSourceBillType() + "#" + item.getSourceItemId();
            thisAmount.merge(key, nvlAmount(item.getNetAmount()), BigDecimal::add);
            sample.putIfAbsent(key, item);
        }
        for (Map.Entry<String, BigDecimal> entry : thisAmount.entrySet()) {
            SaleInvoiceItem item = sample.get(entry.getKey());
            String sourceType = item.getSourceBillType();
            BigDecimal sourceTotal;
            if ("销售订单".equals(sourceType)) {
                SaleOrderItem oi = saleOrderItemMapper.selectById(item.getSourceItemId());
                if (oi == null) throw new BusinessException("源销售订单明细不存在: " + item.getSourceItemId());
                if (oi.getOrderId() != null && saleOrderService.isOrderClosed(oi.getOrderId())) {
                    throw new BusinessException("销售订单【" + item.getSourceBillNo() + "】已完结（已结案），不能下推生成销售发票");
                }
                sourceTotal = nvlAmount(oi.getNetAmount());
            } else if ("销售出库单".equals(sourceType) || "销售出库".equals(sourceType)) {
                SaleDeliveryItem di = saleDeliveryItemMapper.selectById(item.getSourceItemId());
                if (di == null) throw new BusinessException("源销售出库明细不存在: " + item.getSourceItemId());
                sourceTotal = nvlAmount(di.getNetAmount());
            } else if ("销售退货单".equals(sourceType)) {
                SaleReturnItem ri = saleReturnItemMapper.selectById(item.getSourceItemId());
                if (ri == null) throw new BusinessException("源销售退货明细不存在: " + item.getSourceItemId());
                sourceTotal = nvlAmount(ri.getNetAmount());
            } else {
                continue;
            }
            BigDecimal invoiced = sumInvoicedAmount(sourceType, item.getSourceItemId(), currentInvoiceId);
            BigDecimal remain = sourceTotal.subtract(invoiced);
            if (entry.getValue().compareTo(remain) > 0) {
                throw new BusinessException("来源单【" + item.getSourceBillNo() + "】开票金额超过剩余可开金额（来源金额: "
                        + sourceTotal + "，已开票: " + invoiced + "，剩余可开: " + remain + "，本次: " + entry.getValue() + "）");
            }
        }
    }

    /** 某来源明细「已生效发票已开金额」合计（红字发票金额为负自动抵扣；排除当前发票自身） */
    private BigDecimal sumInvoicedAmount(String sourceType, Long sourceItemId, Long excludeInvoiceId) {
        QueryWrapper<SaleInvoiceItem> qw = new QueryWrapper<>();
        qw.eq("source_bill_type", sourceType).eq("source_item_id", sourceItemId);
        qw.inSql("invoice_id", "select invoice_id from sale_invoice where status = '已生效'");
        if (excludeInvoiceId != null) qw.ne("invoice_id", excludeInvoiceId);
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleInvoiceItem it : itemMapper.selectList(qw)) {
            sum = sum.add(nvlAmount(it.getNetAmount()));
        }
        return sum;
    }

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /** 发票行金额空值兜底（「已开票金额」回写用） */
    private static java.math.BigDecimal nvlAmount(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    /** 由表头「计税方式」解析明细计税类型（含税价/价内税→1、不计税→3、其它→2；总开关关闭→不计税） */
    private int resolveHeaderTaxType(SaleInvoice saleInvoice) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = saleInvoice.getTaxCalcMethod();
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private void calculateItemAmount(SaleInvoiceItem item) {
        // 金额模式（设置中心「发票录入口径」=按发票金额）：金额说了算，数量单价只读并反算
        if (sysParamValidator.isInvoiceAmountMode()) {
            calculateItemAmountByEnteredAmount(item);
            return;
        }
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
        BigDecimal discountRate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;
        Integer taxType = item.getTaxType() != null ? item.getTaxType() : 2;

        // 数值边界校验
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("数量不能为负数");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("单价不能为负数");
        }
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(HUNDRED) > 0) {
            throw new BusinessException("税率必须在 0~100 之间");
        }
        if (discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(HUNDRED) > 0) {
            throw new BusinessException("折扣率必须在 0~100 之间");
        }

        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);
        item.setDiscountRate(discountRate);
        item.setTaxType(taxType);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;        // 不计税
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE; // 含税价
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        // 含税价 / 不含税价 两列（落库）：按表头「计税方式」由「单价」换算
        BigDecimal priceWithTax;
        BigDecimal priceWithoutTax;
        if (inclusive) {
            priceWithTax = unitPrice;
            priceWithoutTax = unitPrice.divide(taxFactor, 6, RoundingMode.HALF_UP);
        } else if (noTax) {
            priceWithTax = unitPrice;
            priceWithoutTax = unitPrice;
        } else {
            priceWithoutTax = unitPrice;
            priceWithTax = unitPrice.multiply(taxFactor).setScale(6, RoundingMode.HALF_UP);
        }
        item.setPriceWithTax(priceWithTax.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(priceWithoutTax.setScale(6, RoundingMode.HALF_UP));

        // 折前金额一律按「不含税」口径参与折扣与税额计算
        BigDecimal originalAmount = inclusive
                ? unitPrice.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal discountAmount = originalAmount.multiply(discountRate)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setDiscountAmount(discountAmount);

        BigDecimal amount = originalAmount.subtract(discountAmount).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);

        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, discountRate, inclusive, noTax);
        item.setTaxAmount(taxAmount);

        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 金额模式逐行计算（设置中心「发票录入口径」=按发票金额，对齐金蝶「发票只校验金额」）：
     * - 不含税价：以录入的「开票金额(amount)」为准，税额=金额×税率，价税合计=金额+税额；
     * - 含税价  ：以录入的「开票价税合计(netAmount)」为准，金额=合计/(1+税率)，税额=合计-金额；
     * - 不计税  ：以录入的「开票金额」为准，税额=0，价税合计=金额。
     * 单价按「折前金额 ÷ 数量」反算（数量为 0 时保留原单价），含税/不含税单价按口径换算。
     */
    private void calculateItemAmountByEnteredAmount(SaleInvoiceItem item) {
        BigDecimal quantity = nvlAmount(item.getQuantity());
        BigDecimal taxRate = nvlAmount(item.getTaxRate());
        int taxType = item.getTaxType() != null ? item.getTaxType() : TAX_TYPE_EXCLUSIVE;
        BigDecimal discountAmount = nvlAmount(item.getDiscountAmount());
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal amount;
        BigDecimal taxAmount;
        BigDecimal netAmount;
        if (taxType == TAX_TYPE_NO_TAX) {
            amount = nvlAmount(item.getAmount()).setScale(SCALE, RoundingMode.HALF_UP);
            taxAmount = BigDecimal.ZERO.setScale(SCALE);
            netAmount = amount;
        } else if (taxType == TAX_TYPE_INCLUSIVE) {
            netAmount = nvlAmount(item.getNetAmount()).setScale(SCALE, RoundingMode.HALF_UP);
            amount = netAmount.divide(taxFactor, SCALE, RoundingMode.HALF_UP);
            taxAmount = netAmount.subtract(amount).setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            amount = nvlAmount(item.getAmount()).setScale(SCALE, RoundingMode.HALF_UP);
            taxAmount = amount.multiply(taxRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            netAmount = amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP);
        }

        item.setAmount(amount);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(netAmount);
        item.setDiscountAmount(discountAmount);

        BigDecimal originalAmount = amount.add(discountAmount);
        BigDecimal unitPrice = quantity.compareTo(BigDecimal.ZERO) > 0
                ? originalAmount.divide(quantity, 6, RoundingMode.HALF_UP)
                : nvlAmount(item.getUnitPrice());
        item.setUnitPrice(unitPrice);
        if (taxType == TAX_TYPE_INCLUSIVE) {
            item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
            item.setPriceWithoutTax(unitPrice.divide(taxFactor, 6, RoundingMode.HALF_UP));
        } else if (taxType == TAX_TYPE_NO_TAX) {
            item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
            item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        } else {
            item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
            item.setPriceWithTax(unitPrice.multiply(taxFactor).setScale(6, RoundingMode.HALF_UP));
        }
    }

    private void recalculateInvoice(SaleInvoice saleInvoice, List<SaleInvoiceItem> items) {
        // 表头「计税方式」归一：总开关关闭→不计税；为空→取设置中心默认
        if (!sysParamValidator.isTaxEnabled()) {
            saleInvoice.setTaxCalcMethod("不计税");
        } else if (saleInvoice.getTaxCalcMethod() == null || saleInvoice.getTaxCalcMethod().isBlank()) {
            saleInvoice.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(saleInvoice);
        BigDecimal totalAmount = BigDecimal.ZERO;      // 折前合计
        BigDecimal totalDiscount = BigDecimal.ZERO;    // 折扣额合计
        BigDecimal totalTax = BigDecimal.ZERO;         // 税额合计

        if (items != null) {
            for (SaleInvoiceItem item : items) {
                item.setTaxType(headerTaxType);
                calculateItemAmount(item);
                // 累加一律按空值兜底（金额模式下 discountAmount/taxAmount 可能未传，避免 NPE）
                BigDecimal originalAmount = nvlAmount(item.getAmount()).add(nvlAmount(item.getDiscountAmount()));
                totalAmount = totalAmount.add(originalAmount);
                totalDiscount = totalDiscount.add(nvlAmount(item.getDiscountAmount()));
                totalTax = totalTax.add(nvlAmount(item.getTaxAmount()));
            }
        }

        saleInvoice.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        saleInvoice.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        saleInvoice.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        // 价税合计 = 折前金额 - 折扣额 + 税额
        BigDecimal netAmount = saleInvoice.getTotalAmount()
                .subtract(saleInvoice.getDiscountAmount())
                .add(saleInvoice.getTaxAmount());
        saleInvoice.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));

        // 【2026-09-11 修复】平均税率此前从未赋值，落库恒为 0（明细 13% 但表头 0）。
        // 口径：平均税率 = 税额合计 ÷ 不含税金额合计 × 100（不含税金额合计 = 折前金额 − 折扣额）
        BigDecimal taxableAmount = saleInvoice.getTotalAmount().subtract(saleInvoice.getDiscountAmount());
        if (taxableAmount.compareTo(BigDecimal.ZERO) > 0) {
            saleInvoice.setTaxRateAvg(totalTax.multiply(HUNDRED)
                    .divide(taxableAmount, 2, RoundingMode.HALF_UP));
        } else {
            saleInvoice.setTaxRateAvg(BigDecimal.ZERO);
        }
    }

    @Override
    public PageResult<SaleInvoice> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleInvoice> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleInvoice.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入发票号/来源单号/客户名称搜索」适配（说明书 2.2）：发票号 + 来源单据号 OR 关联客户主键
        // （sale_invoice 无客户名称列 → 客户名称先解析成 customer_id，作为同一 OR 分组的一支）
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword,
                List.of("invoice_no", "source_bill_no"), "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 invoice_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "invoice_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_invoice", customSearch, CustomFieldSearchHelper.idColumnOf("sale_invoice"));
        Page<SaleInvoice> page = new Page<>(current, size);
        Page<SaleInvoice> result = page(page, wrapper);
        for (SaleInvoice i : result.getRecords()) {
            fillDisplayFields(i);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在标准过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<SaleInvoice> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SaleInvoice> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleInvoice.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入发票号/来源单号/客户名称搜索」适配（说明书 2.2）：发票号 + 来源单据号 OR 关联客户主键
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword,
                List.of("invoice_no", "source_bill_no"), "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 invoice_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "invoice_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 表头字段过滤（2026-10-14 筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "saleInvoice", headerFilters);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_invoice", customSearch, CustomFieldSearchHelper.idColumnOf("sale_invoice"));
        Page<SaleInvoice> page = new Page<>(current, size);
        Page<SaleInvoice> result = page(page, wrapper);
        for (SaleInvoice i : result.getRecords()) {
            fillDisplayFields(i);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleInvoice getDetail(Long id) {
        SaleInvoice saleInvoice = getById(id);
        if (saleInvoice == null) throw new BusinessException("发票不存在");
        List<SaleInvoiceItem> items = itemMapper.selectList(
                new QueryWrapper<SaleInvoiceItem>().eq("invoice_id", id).orderByAsc("line_no"));
        fillDisplayFields(saleInvoice);
        if (items != null) {
            for (SaleInvoiceItem item : items) {
                fillItemDisplayFields(item);
            }
        }
        saleInvoice.setItems(items);
        return saleInvoice;
    }

    private void fillDisplayFields(SaleInvoice i) {
        if (i.getCustomerId() != null) {
            Customer c = customerService.getById(i.getCustomerId());
            if (c != null) i.setCustomerName(c.getCustomerName());
        }
        if (i.getSalerId() != null) {
            Employee e = employeeService.getById(i.getSalerId());
            if (e != null) i.setSalerName(e.getEmployeeName());
        }
        if (i.getCurrencyId() != null) {
            Currency cur = currencyService.getById(i.getCurrencyId());
            if (cur != null) i.setCurrencyCode(cur.getCurrencyCode());
        }
    }

    private void fillItemDisplayFields(SaleInvoiceItem item) {
        if (item.getMaterialId() != null) {
            Material m = materialService.getById(item.getMaterialId());
            if (m != null) {
                item.setMaterialCode(m.getMaterialCode());
                item.setMaterialName(m.getMaterialName());
                item.setSpecification(m.getModel());
            }
        }
        if (item.getUnitId() != null) {
            UnitOfMeasure u = unitOfMeasureService.getById(item.getUnitId());
            if (u != null) item.setUnit(u.getUnitName());
        }
    }

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SaleInvoice saleInvoice = getById(id);
        if (saleInvoice == null) throw new BusinessException("销售发票不存在");
        // 状态校验 / 严格校验 / 审批开关 / 审批任务创建 全部由统一状态机执行
        super.submitForApproval(saleInvoice, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前严格校验（原 submitForApproval 内的校验整体搬入，提交才触发，保存草稿不校验） */
    @Override
    protected void validateStrictSubmit(SaleInvoice saleInvoice) {
        List<SaleInvoiceItem> items = itemMapper.selectList(
            new QueryWrapper<SaleInvoiceItem>().eq("invoice_id", saleInvoice.getInvoiceId()).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("销售发票没有明细，请至少添加一行物料后再提交");
        }

        if (saleInvoice.getCustomerId() == null) {
            throw new BusinessException("客户为空，请选择客户后再提交");
        }
        Customer customer = customerService.getById(saleInvoice.getCustomerId());
        if (customer == null) {
            throw new BusinessException("关联客户不存在(customerId=" + saleInvoice.getCustomerId() + ")，请重新选择客户");
        }
        if (saleInvoice.getSalerId() != null) {
            Employee emp = employeeService.getById(saleInvoice.getSalerId());
            if (emp == null) {
                throw new BusinessException("关联销售员不存在(employeeId=" + saleInvoice.getSalerId() + ")，请重新选择销售员");
            }
        }
        if (saleInvoice.getCurrencyId() == null) {
            throw new BusinessException("币种为空，请选择币种后再提交");
        }
        Currency cur = currencyService.getById(saleInvoice.getCurrencyId());
        if (cur == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + saleInvoice.getCurrencyId() + ")，请重新选择币种");
        }

        for (int i = 0; i < items.size(); i++) {
            SaleInvoiceItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行未选择物料，请选择物料后再提交");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            if (item.getUnitId() != null) {
                UnitOfMeasure uom = unitOfMeasureService.getById(item.getUnitId());
                if (uom == null) {
                    throw new BusinessException("第" + lineNo + "行计量单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
                }
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (sysParamValidator.isInvoiceAmountMode()) {
                // 金额模式（对齐金蝶「发票只校验金额」）：数量允许为 0/空，只校验开票金额
                BigDecimal lineAmount = item.getNetAmount() != null ? item.getNetAmount()
                        : (item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
                if (lineAmount.compareTo(BigDecimal.ZERO) == 0) {
                    throw new BusinessException("第" + lineNo + "行开票金额不能为 0");
                }
            } else if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matCode = mat != null ? mat.getMaterialCode() : "物料";
                throw new BusinessException("第" + lineNo + "行[" + matCode + "]数量必须大于 0");
            }
        }

        // === 互斥与超量校验（仅对蓝字发票生效，红字发票由退货审核自动生成不校验） ===
        if (saleInvoice.getIsRedInvoice() == null || saleInvoice.getIsRedInvoice() == 0) {
            validateInvoiceSourceMutex(saleInvoice, items, saleInvoice.getInvoiceId());
        }
    }

    /**
     * 统一状态机审核入口（含审批引擎防护）。
     * 覆盖 Object 签名：审批引擎在基类内部按 Object 签名回调，只有覆盖它才能保证防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        SaleInvoice invoice = getById((Long) id);
        if (invoice == null) throw new BusinessException("发票不存在");
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        // （由引擎回调 onApprovalApproved 触发的审核不受影响：引擎会先把任务置为已通过再回写单据）
        if (approvalRuntimeService.getRunningTask("SALE_INVOICE", (Long) id) != null) {
            throw new BusinessException("销售发票已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：回写源单已开票数量/金额 + 按配置生成收款单草稿 */
    @Override
    public void onApproved(SaleInvoice saleInvoice) {
        Long id = saleInvoice.getInvoiceId();
        List<SaleInvoiceItem> items = itemMapper.selectList(
            new QueryWrapper<SaleInvoiceItem>().eq("invoice_id", id));

        boolean isRed = saleInvoice.getIsRedInvoice() != null && saleInvoice.getIsRedInvoice() == 1;

        // 金额模式（设置中心「发票录入口径」=按发票金额）：只按金额对冲，不回写数量
        boolean amountMode = sysParamValidator.isInvoiceAmountMode();

        for (SaleInvoiceItem item : items) {
            if (item.getSourceId() == null || item.getSourceItemId() == null) {
                continue;
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            String sourceType = item.getSourceBillType();

            // === 回写源单的 turned_invoice_quantity（针对销售出库单/销售退货单） ===
            // 红字发票数量为负数（冲减），蓝字发票为正数（累加）
            BigDecimal delta = isRed ? qty.negate() : qty;

            if ("销售出库单".equals(sourceType) || "销售出库".equals(sourceType)) {
                // 回写出库明细的已开票数量（金额模式不回写数量）
                SaleDeliveryItem di = saleDeliveryItemMapper.selectById(item.getSourceItemId());
                if (di != null && !amountMode) {
                    BigDecimal current = di.getTurnedInvoiceQuantity() != null ? di.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                    di.setTurnedInvoiceQuantity(current.add(delta));
                    saleDeliveryItemMapper.updateById(di);
                }
                // 继续向上回写到销售订单（保持原有逻辑）
                if (di != null && di.getSourceId() != null && di.getSourceItemId() != null) {
                    if (!amountMode) {
                        saleOrderService.updateTurnedQuantity(
                            di.getSourceId(), di.getSourceItemId(),
                            qty, "invoice");
                    }
                    // 【2026-10-14 完结口径可配置】同步回写订单明细「已开票金额」（完结「销售发票」模式判定用），
                    // 与 reverseApprove 的负回写逐字镜像
                    saleOrderService.updateTurnedInvoiceAmount(
                        di.getSourceId(), di.getSourceItemId(), nvlAmount(item.getNetAmount()));
                }
            } else if ("销售退货单".equals(sourceType)) {
                // 回写退货明细的已开票数量（红字发票；金额模式不回写数量）
                SaleReturnItem ri = saleReturnItemMapper.selectById(item.getSourceItemId());
                if (ri != null && !amountMode) {
                    BigDecimal current = ri.getTurnedInvoiceQuantity() != null ? ri.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                    ri.setTurnedInvoiceQuantity(current.add(qty));
                    saleReturnItemMapper.updateById(ri);
                }
                // 【2026-09-14 修复】此处原另有一处 updateTurnedQuantity(..., "return")，
                //   导致订单"已退货数量"重复累加：退货单审核时已按退货明细回写过一次
                //   （见 SaleReturnServiceImpl.approve 的 "return" 回写）。
                //   红字发票只是退货的财务确认单据，只回写退货明细的"已开票数量"，不再回写订单"已退货数量"。
                //   唯一口径（红线）：订单"已退货数量"只由退货单审核回写，发票侧不得重复回写；
                //   否则 turned_return_quantity 虚增 → totalTurned 虚高 → 明细/订单被提前置为已结案。
            } else if ("销售订单".equals(sourceType)) {
                // 销售订单直接开票，回写 turned_invoice_quantity（金额模式不回写数量）
                if (!amountMode) {
                    saleOrderService.updateTurnedQuantity(
                        item.getSourceId(), item.getSourceItemId(),
                        qty, "invoice");
                }
                // 【2026-10-14 完结口径可配置】同步回写订单明细「已开票金额」
                saleOrderService.updateTurnedInvoiceAmount(
                    item.getSourceId(), item.getSourceItemId(), nvlAmount(item.getNetAmount()));
            }
        }

        // === 按配置直接生成收款单草稿（系统不生成应收单，设计图 v3；红字发票金额为负，不生成） ===
        try {
            String arSource = sysConfigService.getValue("finance.ar.source_bill_type", "none");
            if ("sale_invoice".equals(arSource) && !isRed) {
                financeService.createReceiptDraftFromSource("销售发票", id);
            }
        } catch (Exception e) {
            log.warn("发票审核生成收款单草稿失败，发票: {}", saleInvoice.getInvoiceNo(), e);
        }

        log.info("销售发票审核: {} 审核人: {} 红字: {}", saleInvoice.getInvoiceNo(), saleInvoice.getApprovedBy(), isRed);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        SaleInvoice saleInvoice = getById(id);
        if (saleInvoice == null) throw new BusinessException("发票不存在");
        if (!"草稿".equals(saleInvoice.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + saleInvoice.getStatus());
        }
        saleInvoice.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            saleInvoice.setRemark((saleInvoice.getRemark() != null ? saleInvoice.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(saleInvoice);
        log.info("销售发票取消: {} 原因: {}", saleInvoice.getInvoiceNo(), reason);
    }

    @Override
    public String generateInvoiceNo() {
        String docType = "SALE_INVOICE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SINV-" + dateStr + "-";
            QueryWrapper<SaleInvoice> wrapper = new QueryWrapper<>();
            wrapper.likeRight("invoice_no", prefix).orderByDesc("invoice_no").last("LIMIT 1");
            SaleInvoice last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getInvoiceNo() != null) {
                try {
                    seq = Integer.parseInt(last.getInvoiceNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public List<Long> checkMutex(String sourceType, List<Long> sourceItemIds) {
        if (sourceItemIds == null || sourceItemIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> locked = new java.util.ArrayList<>();

        if ("销售订单".equals(sourceType)) {
            // 销售订单作为源单：查该订单明细的任一对应出库明细是否已被开票
            // 出库明细的 source_item_id 指向订单明细ID
            List<SaleDeliveryItem> deliveryItems = saleDeliveryItemMapper.selectList(
                new QueryWrapper<SaleDeliveryItem>()
                    .in("source_item_id", sourceItemIds)
                    .gt("turned_invoice_quantity", 0));
            // 收集被锁定的订单明细ID（即出库明细的 source_item_id）
            for (SaleDeliveryItem di : deliveryItems) {
                if (di.getSourceItemId() != null && sourceItemIds.contains(di.getSourceItemId())
                        && !locked.contains(di.getSourceItemId())) {
                    locked.add(di.getSourceItemId());
                }
            }
        } else if ("销售出库单".equals(sourceType) || "销售出库".equals(sourceType)) {
            // 销售出库单作为源单：查出库明细的源订单是否已被任何发票直接引用
            List<SaleDeliveryItem> deliveryItems = saleDeliveryItemMapper.selectBatchIds(sourceItemIds);
            // 收集出库明细对应的源订单明细ID
            List<Long> orderItemIds = new java.util.ArrayList<>();
            java.util.Map<Long, Long> deliveryToOrderMap = new java.util.HashMap<>();
            for (SaleDeliveryItem di : deliveryItems) {
                if (di.getSourceItemId() != null) {
                    orderItemIds.add(di.getSourceItemId());
                    deliveryToOrderMap.put(di.getDeliveryItemId(), di.getSourceItemId());
                }
            }
            if (!orderItemIds.isEmpty()) {
                List<SaleOrderItem> orderItems = saleOrderItemMapper.selectBatchIds(orderItemIds);
                for (SaleOrderItem oi : orderItems) {
                    if (oi.getTurnedInvoiceQuantity() != null
                            && oi.getTurnedInvoiceQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        // 该订单明细已开票 → 对应的所有出库明细都被锁定
                        for (java.util.Map.Entry<Long, Long> e : deliveryToOrderMap.entrySet()) {
                            if (e.getValue().equals(oi.getOrderItemId())
                                    && sourceItemIds.contains(e.getKey())
                                    && !locked.contains(e.getKey())) {
                                locked.add(e.getKey());
                            }
                        }
                    }
                }
            }
        }
        // 销售退货单源单：仅超量校验，无互斥锁定
        return locked;
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleInvoice bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        // 状态校验 + 下游校验 + 状态迁移由基类执行；勾稽回滚在 onReverseApproved 钩子
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式置空 approved_by/approved_at
        UpdateWrapper<SaleInvoice> uw = new UpdateWrapper<>();
        uw.eq("invoice_id", id)
                .set("approved_by", null)
                .set("approved_at", null);
        update(uw);
    }

    /**
     * 重置草稿影响：按审核时的同口径回滚"已开票数量/金额"（与 onApproved 逐字镜像）。
     *   原实现只把状态改回草稿、不回滚任何勾稽数量 → 重置草稿后再审核，数量被累加两次。
     */
    @Override
    public void onReverseApproved(SaleInvoice bill) {
        Long id = bill.getInvoiceId();
        List<SaleInvoiceItem> items = itemMapper.selectList(
            new QueryWrapper<SaleInvoiceItem>().eq("invoice_id", id));
        boolean isRed = bill.getIsRedInvoice() != null && bill.getIsRedInvoice() == 1;
        // 金额模式：与 onApproved 镜像 —— 审核不回写数量，这里也不回滚数量，只回滚金额
        boolean amountMode = sysParamValidator.isInvoiceAmountMode();

        for (SaleInvoiceItem item : items) {
            if (item.getSourceId() == null || item.getSourceItemId() == null) {
                continue;
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            String sourceType = item.getSourceBillType();
            BigDecimal delta = isRed ? qty.negate() : qty;

            if ("销售出库单".equals(sourceType) || "销售出库".equals(sourceType)) {
                SaleDeliveryItem di = saleDeliveryItemMapper.selectById(item.getSourceItemId());
                if (di != null && !amountMode) {
                    BigDecimal current = di.getTurnedInvoiceQuantity() != null ? di.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                    di.setTurnedInvoiceQuantity(current.subtract(delta));
                    saleDeliveryItemMapper.updateById(di);
                }
                if (di != null && di.getSourceId() != null && di.getSourceItemId() != null) {
                    if (!amountMode) {
                        saleOrderService.updateTurnedQuantity(
                            di.getSourceId(), di.getSourceItemId(),
                            qty.negate(), "invoice");
                    }
                    // 【2026-10-14 完结口径可配置】镜像回滚「已开票金额」（onApproved 的 +net_amount 对应这里的负回写）
                    saleOrderService.updateTurnedInvoiceAmount(
                        di.getSourceId(), di.getSourceItemId(), nvlAmount(item.getNetAmount()).negate());
                }
            } else if ("销售退货单".equals(sourceType)) {
                SaleReturnItem ri = saleReturnItemMapper.selectById(item.getSourceItemId());
                if (ri != null && !amountMode) {
                    BigDecimal current = ri.getTurnedInvoiceQuantity() != null ? ri.getTurnedInvoiceQuantity() : BigDecimal.ZERO;
                    ri.setTurnedInvoiceQuantity(current.subtract(qty));
                    saleReturnItemMapper.updateById(ri);
                }
            } else if ("销售订单".equals(sourceType)) {
                if (!amountMode) {
                    saleOrderService.updateTurnedQuantity(
                        item.getSourceId(), item.getSourceItemId(),
                        qty.negate(), "invoice");
                }
                // 【2026-10-14 完结口径可配置】镜像回滚「已开票金额」
                saleOrderService.updateTurnedInvoiceAmount(
                    item.getSourceId(), item.getSourceItemId(), nvlAmount(item.getNetAmount()).negate());
            }
        }
        // 清理按配置自动生成的收款单草稿（已生效/待审核的收款单会抛业务异常阻止回退）
        financeService.rollbackAutoReceiptBySource("销售发票", bill.getInvoiceNo());
        log.info("销售发票重置草稿(已回滚已开票数量): {} 操作人: {}", bill.getInvoiceNo(), bill.getApprovedBy());
    }

    /** 作废（作废前为已生效）：与重置草稿严格镜像，回滚已开票数量/金额并清理自动收款单草稿 */
    @Override
    @Transactional
    public void onVoided(SaleInvoice bill) {
        onReverseApproved(bill);
        log.info("销售发票作废回滚完成: {}", bill.getInvoiceNo());
    }

    /** 反作废（恢复为已生效）：与审核严格镜像，重新回写已开票数量/金额并按配置恢复收款单草稿 */
    @Override
    @Transactional
    public void onUnvoided(SaleInvoice bill) {
        onApproved(bill);
        log.info("销售发票反作废恢复完成: {}", bill.getInvoiceNo());
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        // 状态校验 + 下游校验 + 作废前状态记录 由基类执行
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Override
    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }

    /** 结案: 已生效 → close_status=1（统一状态机执行） */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案: close_status=0（统一状态机执行） */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }
}
