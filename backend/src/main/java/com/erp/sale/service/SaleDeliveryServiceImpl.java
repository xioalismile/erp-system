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
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.config.KgwBusinessRules;
import com.erp.config.SysParamValidator;
import com.erp.config.service.SysConfigService;
import com.erp.finance.service.FinanceService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleInvoiceItem;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleOrderSubstitutePlan;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.entity.SaleReturnItem;
import com.erp.sale.mapper.SaleDeliveryItemMapper;
import com.erp.sale.mapper.SaleDeliveryMapper;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleReturnItemMapper;
import com.erp.sale.mapper.SaleReturnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SaleDeliveryServiceImpl extends AbstractBillService<SaleDeliveryMapper, SaleDelivery> implements SaleDeliveryService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 全面推广：销售出库）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值的列名优先按 SaleDelivery 实体属性名解析（框架元数据自动转列名），
        // 仅「按名称筛但列是外键」处直接写数据库列名。字段解释见 BillHeaderFilterSupport。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("deliveryNo", new BillHeaderFilterSupport.Spec("deliveryNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactAddress", new BillHeaderFilterSupport.Spec("contactAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("expressCompany", new BillHeaderFilterSupport.Spec("expressCompany", BillHeaderFilterSupport.Kind.TEXT));
        f.put("expressNo", new BillHeaderFilterSupport.Spec("expressNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("carrier", new BillHeaderFilterSupport.Spec("carrier", BillHeaderFilterSupport.Kind.TEXT));
        f.put("trackingNo", new BillHeaderFilterSupport.Spec("trackingNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceNo", new BillHeaderFilterSupport.Spec("invoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("opportunityNo", new BillHeaderFilterSupport.Spec("opportunityNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("saler_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currency_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("deliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("freight", new BillHeaderFilterSupport.Spec("freight", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidRate", new BillHeaderFilterSupport.Spec("prepaidRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidAmount", new BillHeaderFilterSupport.Spec("prepaidAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("saleDelivery", SaleDelivery.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "attachment_urls",
            "base_net_amount",
            "base_tax_amount",
            "base_total_amount",
            "carrier",
            "contact_address",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "currency_id",
            "customer_id",
            "freight",
            "delivery_date",
            "delivery_id",
            "delivery_no",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "express_company",
            "express_no",
            "invoice_no",
            "line_no",
            "net_amount",
            "opportunity_id",
            "opportunity_no",
            "order_no",
            "org_code",
            "remark",
            "sale_delivery",
            "sale_order",
            "saler_id",
            "source_bill_no",
            "source_bill_type",
            "status",
            "tax_amount",
            "total_amount",
            "tracking_no",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final SaleDeliveryItemMapper itemMapper;
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final WarehouseService warehouseService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SaleOrderService saleOrderService;
    @Autowired
    private SaleOrderItemMapper saleOrderItemMapper;
    /** 替换发货指示（2026-11-27）：出库校验、已执行量回写 */
    @Autowired
    private SaleOrderSubstitutePlanService saleOrderSubstitutePlanService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SaleReturnItemMapper saleReturnItemMapper;
    @Autowired
    private SaleReturnMapper saleReturnMapper;

    /** 替换货（2026-11-17）：H2 硬卡需要读取换货单行 */
    @Autowired
    private com.erp.sale.mapper.SaleExchangeItemMapper saleExchangeItemMapper;
    @Autowired
    private com.erp.sale.mapper.SaleExchangeMapper saleExchangeMapper;
    private final InventoryService inventoryService;
    private final com.erp.inventory.service.VirtualStockService virtualStockService;
    private final MaterialCostService materialCostService;
    private final KgwBusinessRules kgwRules;
    private final SysParamValidator sysParamValidator;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SaleInvoiceService saleInvoiceService;

    /** 【2026-11-06 化妆品批记录】未放行批次出库控制（设置中心三档：允许/预警提示/不允许） */
    private final com.erp.inventory.batch.service.BatchReleaseGuardService batchReleaseGuardService;

    /** 下游引用校验（2026-11-09 统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    @Autowired
    @Lazy
    private FinanceService financeService;

    @Autowired
    private SysConfigService sysConfigService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售出库单"; }
    @Override protected String getApprovalBillType() { return "SALE_DELIVERY"; }
    @Override protected String getBillNo(SaleDelivery b) { return b.getDeliveryNo(); }
    @Override protected Object getId(SaleDelivery b) { return b.getDeliveryId(); }
    @Override protected String getStatus(SaleDelivery b) { return b.getStatus(); }
    @Override protected void setStatus(SaleDelivery b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleDelivery b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleDelivery b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleDelivery b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleDelivery b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleDelivery b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleDelivery b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleDelivery b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleDelivery b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleDelivery b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleDelivery b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleDelivery b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleDelivery b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleDelivery b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SaleDelivery bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getDeliveryNo());
    }

    @Override
    @Transactional
    public void saveDeliveryWithItems(SaleDelivery delivery, List<SaleDeliveryItem> items) {
        boolean isNew = delivery.getDeliveryId() == null;

        // 【2026-09-14 修复】补状态守卫（与采购入库单同口径）：
        // 原实现只校验锁单日期，已生效的出库单可被 PUT 直接改写表头+明细，
        // 而审核时已扣减库存并回写订单已出库数量，改写后账实永久不一致且不会回冲。
        if (!isNew) {
            SaleDelivery existing = getById(delivery.getDeliveryId());
            if (existing != null) {
                if ("已生效".equals(existing.getStatus())) {
                    throw new BusinessException("已生效的出库单不可直接修改，请先重置草稿");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的出库单不可修改");
                }
            }
        }

        if (delivery.getDeliveryNo() == null || delivery.getDeliveryNo().isBlank()) {
            delivery.setDeliveryNo(generateDeliveryNo());
        }

        // === 设置中心：单据号查重（sys.bill.no.duplicate.check）===
        QueryWrapper<SaleDelivery> dupQw = new QueryWrapper<SaleDelivery>().eq("delivery_no", delivery.getDeliveryNo());
        if (!isNew) {
            dupQw.ne("delivery_id", delivery.getDeliveryId());
        }
        sysParamValidator.validateBillNoDuplicate(delivery.getDeliveryNo(), "销售出库单", count(dupQw) > 0);

        // === 设置中心：锁单据日期校验（sys.lock.bill.date）===
        if (!isNew) {
            SaleDelivery existingForDate = getById(delivery.getDeliveryId());
            if (existingForDate != null) {
                sysParamValidator.validateLockBillDate("销售出库单", existingForDate.getDeliveryDate(), delivery.getDeliveryDate(), existingForDate.getStatus());
            }
        }

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (delivery.getStatus() == null || delivery.getStatus().isBlank()) delivery.setStatus("草稿");
        if (delivery.getApprovalStatus() == null || delivery.getApprovalStatus().isBlank()
                || ("草稿".equals(delivery.getStatus()) && "待审核".equals(delivery.getApprovalStatus()))) {
            delivery.setApprovalStatus("草稿");
        }
        if (delivery.getExchangeRate() == null) delivery.setExchangeRate(BigDecimal.ONE);
        if (delivery.getDiscountRate() == null) delivery.setDiscountRate(BigDecimal.ZERO);
        if (delivery.getDiscountAmount() == null) delivery.setDiscountAmount(BigDecimal.ZERO);
        if (delivery.getTotalAmount() == null) delivery.setTotalAmount(BigDecimal.ZERO);
        if (delivery.getTaxAmount() == null) delivery.setTaxAmount(BigDecimal.ZERO);
        if (delivery.getNetAmount() == null) delivery.setNetAmount(BigDecimal.ZERO);
        if (delivery.getDeliveryDate() == null) delivery.setDeliveryDate(LocalDate.now());
        if (delivery.getSourceBillType() == null || delivery.getSourceBillType().isBlank()) {
            delivery.setSourceBillType("手动出库");
        }

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SaleDeliveryItem> validItems = new ArrayList<>();
            for (SaleDeliveryItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        recalculateDelivery(delivery, items);

        // 统一状态机口径（2026-11-09）：保存=草稿，不得在保存动作里把单据改成待审核；
        // 审批升级只允许发生在提交动作（基类按设置中心开关决定待审核或直接生效）。

        if (isNew) {
            save(delivery);
        } else {
            updateById(delivery);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", delivery.getDeliveryId()));
            int lineNo = 1;
            for (SaleDeliveryItem item : items) {
                item.setDeliveryItemId(null);
                item.setDeliveryId(delivery.getDeliveryId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                // === 新增：回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("销售出库单保存成功: {} 状态: {} 合计: {}", delivery.getDeliveryNo(), delivery.getStatus(), delivery.getTotalAmount());
    }

    private void calculateItemAmount(SaleDeliveryItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
        BigDecimal discountRate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;
        // 销售出库单为行级口径：优先按行级「计税方式」（价内税/含税价→1、不计税→3、其它→2）；
        // 未传行级方法时退回 item.taxType；含税业务总开关关闭时一律按不计税
        int taxType;
        if (!sysParamValidator.isTaxEnabled()) {
            taxType = 3;
        } else {
            String m = item.getTaxCalcMethod();
            if (m == null || m.isBlank()) {
                taxType = item.getTaxType() != null ? item.getTaxType() : 2;
            } else if ("不计税".equals(m.trim())) {
                taxType = 3;
            } else if ("含税价".equals(m.trim()) || "价内税".equals(m.trim())) {
                taxType = 1;
            } else {
                taxType = 2;
            }
        }

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

        boolean noTax = taxType == 3;        // 不计税
        boolean inclusive = taxType == 1;    // 含税价
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        // 含税价 / 不含税价 两列（落库）：按行级「计税方式」由「单价」换算
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

    private void recalculateDelivery(SaleDelivery delivery, List<SaleDeliveryItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;      // 折前合计
        BigDecimal totalDiscount = BigDecimal.ZERO;    // 折扣额合计
        BigDecimal totalTax = BigDecimal.ZERO;         // 税额合计

        if (items != null) {
            for (SaleDeliveryItem item : items) {
                calculateItemAmount(item);
                // totalAmount 累加的是折前金额（数量×单价）
                BigDecimal originalAmount = item.getAmount().add(item.getDiscountAmount());
                totalAmount = totalAmount.add(originalAmount);
                totalDiscount = totalDiscount.add(item.getDiscountAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        delivery.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        delivery.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        delivery.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        // 价税合计 = 折前金额 - 折扣额 + 税额
        BigDecimal netAmount = delivery.getTotalAmount()
                .subtract(delivery.getDiscountAmount())
                .add(delivery.getTaxAmount());
        delivery.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));

        // 本位币换算
        BigDecimal rate = delivery.getExchangeRate() != null ? delivery.getExchangeRate() : BigDecimal.ONE;
        delivery.setBaseTotalAmount(delivery.getTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        delivery.setBaseTaxAmount(delivery.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        delivery.setBaseNetAmount(delivery.getNetAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 是否由销售换货单生成的出库单（替换货，2026-11-17） */
    private static boolean isExchangeSourced(SaleDelivery delivery) {
        return delivery != null && delivery.getSourceBillType() != null
                && delivery.getSourceBillType().contains("销售换货单");
    }

    /**
     * 【2026-11-17 替换货】H2 硬卡（不依赖设置中心三态开关）：
     * 出库行数量 ≤ 对应换货单行「数量 − 已下推数量」，且货品必须等于换货单行的替换货品。
     * 换货单只能自动生成出库单，因此这里同时防"手工加行/改货品"。
     */
    private void validateExchangeHardCap(SaleDelivery delivery, List<SaleDeliveryItem> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("换货出库单没有明细，无法提交");
        String exchangeNo = delivery.getSourceBillNo();
        if (exchangeNo == null || exchangeNo.isBlank()) {
            throw new BusinessException("换货出库单缺少来源换货单号，无法校验可下推数量");
        }
        com.erp.sale.entity.SaleExchange exchange = saleExchangeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.sale.entity.SaleExchange>()
                        .eq("exchange_no", exchangeNo).last("LIMIT 1"));
        if (exchange == null) throw new BusinessException("来源销售换货单不存在：" + exchangeNo);

        List<com.erp.sale.entity.SaleExchangeItem> exchangeItems = saleExchangeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.sale.entity.SaleExchangeItem>()
                        .eq("exchange_id", exchange.getExchangeId()).orderByAsc("line_no"));
        if (exchangeItems.isEmpty()) throw new BusinessException("来源销售换货单没有明细：" + exchangeNo);

        // 换货单行 → 本出库单累计数量（同一换货行可能被拆成多条出库明细）
        java.util.Map<Long, BigDecimal> sumByExchangeItem = new java.util.HashMap<>();
        for (SaleDeliveryItem di : items) {
            if (di.getSourceItemId() == null) {
                throw new BusinessException("换货出库单第" + di.getLineNo() + "行缺少来源订单行，禁止手工加行");
            }
            com.erp.sale.entity.SaleExchangeItem matched = null;
            for (com.erp.sale.entity.SaleExchangeItem ei : exchangeItems) {
                if (di.getSourceItemId().equals(ei.getSourceItemId())
                        && di.getMaterialId() != null && di.getMaterialId().equals(ei.getMaterialId())) {
                    matched = ei;
                    break;
                }
            }
            if (matched == null) {
                throw new BusinessException("换货出库单第" + di.getLineNo() + "行的货品与换货单不一致，禁止改货品或加行");
            }
            sumByExchangeItem.merge(matched.getExchangeItemId(), nvlBig(di.getQuantity()), BigDecimal::add);
        }
        for (com.erp.sale.entity.SaleExchangeItem ei : exchangeItems) {
            BigDecimal used = sumByExchangeItem.get(ei.getExchangeItemId());
            if (used == null) continue;
            BigDecimal allow = nvlBig(ei.getQuantity());
            if (used.compareTo(allow) > 0) {
                throw new BusinessException("换货单第" + ei.getLineNo() + "行可下推数量 " + allow.stripTrailingZeros().toPlainString()
                        + "，本出库单累计 " + used.stripTrailingZeros().toPlainString() + "，超出换货单数量");
            }
        }
    }

    /**
     * 替换发货硬校验（2026-11-27）：
     * 1) 替换行必须有来源订单行；替换自=订单行原货品；替换品≠原货品；基本单位一致。
     * 2) 销售指示行：指示有效、归属/替代品匹配、已执行+本次 ≤ 指示数量。
     * 3) 仓库直接替代行：该订单行不能有有效销售指示。
     * 4) 订单行普通出库净发 ≤ 订购数量（不读超量三态开关）。
     * 换货单生成的出库单走 validateExchangeHardCap，不进本方法。
     */
    private void validateSubstituteExecution(SaleDelivery delivery, List<SaleDeliveryItem> items) {
        if (items == null || items.isEmpty()) return;
        Map<Long, BigDecimal> planQty = new LinkedHashMap<>();
        Set<Long> checkedOrderItems = new HashSet<>();
        for (SaleDeliveryItem item : items) {
            boolean replacement = item.getReplacedFromMaterialId() != null || item.getReplacePlanId() != null;
            if (!replacement) continue;
            if (item.getSourceItemId() == null) {
                throw new BusinessException("第" + item.getLineNo() + "行替换发货缺少来源订单行，不能提交");
            }
            SaleOrderItem oi = saleOrderItemMapper.selectById(item.getSourceItemId());
            if (oi == null) {
                throw new BusinessException("第" + item.getLineNo() + "行来源订单明细不存在");
            }
            if (item.getSourceId() != null && !item.getSourceId().equals(oi.getOrderId())) {
                throw new BusinessException("第" + item.getLineNo() + "行来源订单与订单明细不一致");
            }
            if (item.getReplacedFromMaterialId() == null
                    || !item.getReplacedFromMaterialId().equals(oi.getMaterialId())) {
                throw new BusinessException("第" + item.getLineNo() + "行替换自与原订单行货品不一致");
            }
            if (item.getMaterialId() == null || item.getMaterialId().equals(oi.getMaterialId())) {
                throw new BusinessException("第" + item.getLineNo() + "行替换货品不能与原货品相同");
            }
            Material origin = oi.getMaterialId() == null ? null : materialService.getById(oi.getMaterialId());
            Material sub = materialService.getById(item.getMaterialId());
            if (origin != null && sub != null && origin.getBaseUnitId() != null && sub.getBaseUnitId() != null
                    && !origin.getBaseUnitId().equals(sub.getBaseUnitId())) {
                throw new BusinessException("第" + item.getLineNo() + "行替换货品与原货品基本单位不一致");
            }
            BigDecimal qty = nvlBig(item.getQuantity());
            if (qty.signum() <= 0) {
                throw new BusinessException("第" + item.getLineNo() + "行替换数量必须大于 0");
            }
            if (item.getReplacePlanId() != null) {
                SaleOrderSubstitutePlan plan = saleOrderSubstitutePlanService.getById(item.getReplacePlanId());
                if (plan == null) {
                    throw new BusinessException("第" + item.getLineNo() + "行来源替换发货指示不存在");
                }
                if (!"有效".equals(plan.getStatus())) {
                    throw new BusinessException("第" + item.getLineNo() + "行来源替换发货指示已取消");
                }
                if (!item.getSourceItemId().equals(plan.getOrderItemId())
                        || !item.getMaterialId().equals(plan.getSubstituteMaterialId())) {
                    throw new BusinessException("第" + item.getLineNo() + "行与替换发货指示不匹配");
                }
                planQty.merge(plan.getPlanId(), qty, BigDecimal::add);
            } else {
                if (saleOrderSubstitutePlanService.hasActivePendingPlan(item.getSourceItemId())) {
                    throw new BusinessException("第" + item.getLineNo() + "行所在订单行已有销售替换指示，仓库不能直接替代；请按指示执行或让销售取消指示");
                }
            }
            if (checkedOrderItems.add(item.getSourceItemId())) {
                BigDecimal used = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(di.quantity),0) FROM sale_delivery_item di "
                                + "JOIN sale_delivery dh ON dh.delivery_id = di.delivery_id "
                                + "WHERE di.source_item_id = ? AND dh.status NOT IN ('已作废','已取消') "
                                + "AND (dh.source_bill_type IS NULL OR dh.source_bill_type NOT LIKE '%销售换货单%')",
                        BigDecimal.class, item.getSourceItemId());
                BigDecimal returned = nvlBig(oi.getTurnedReturnQuantity());
                if (nvlBig(used).subtract(returned).compareTo(nvlBig(oi.getQuantity())) > 0) {
                    throw new BusinessException("第" + item.getLineNo() + "行所在订单行净发超过订购数量 "
                            + nvlBig(oi.getQuantity()).stripTrailingZeros().toPlainString()
                            + "，替换发货不能超发");
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> e : planQty.entrySet()) {
            SaleOrderSubstitutePlan plan = saleOrderSubstitutePlanService.getById(e.getKey());
            if (plan == null) continue;
            BigDecimal turned = nvlBig(plan.getTurnedDeliveryQuantity());
            if (turned.add(e.getValue()).compareTo(nvlBig(plan.getQuantity())) > 0) {
                throw new BusinessException("替换发货指示可执行数量不足（指示 "
                        + nvlBig(plan.getQuantity()).stripTrailingZeros().toPlainString()
                        + "，已执行 " + turned.stripTrailingZeros().toPlainString()
                        + "，本次 " + e.getValue().stripTrailingZeros().toPlainString() + "）");
            }
        }
    }

    private static BigDecimal nvlBig(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * 【2026-10-31 超量发货比例】销售出库提交/审核逐行校验：
     * 最高发货 = 订单数量 + 订单数量 × 比例/100（比例按百分比，10=10%）；
     * 已下推 = 该订单明细下所有未作废/未取消销售出库（生效+草稿/待审核）
     *          - 已生效销售退货 + 本次发货。
     */
    private void validateOverShipment(SaleDelivery delivery, List<SaleDeliveryItem> items, Long currentDeliveryId) {
        if (items == null || items.isEmpty()) return;
        // 设置中心「超过来源单数量出库」= 不控制：整段来源数量限制都不生效
        if (sysParamValidator.isOverQtyNoControl(SaleOverQtyGuardService.KEY_SALE_DELIVERY)) return;
        List<OverShipmentIssue> issues = collectOverShipmentIssues(items, currentDeliveryId);
        for (OverShipmentIssue issue : issues) {
            // 用户口径：预警只保留最短必要信息，不堆订单数量/比例/已下推等明细
            sysParamValidator.applyOverQtyPolicy(
                    SaleOverQtyGuardService.KEY_SALE_DELIVERY, "销售出库单", issue.lineNo, true,
                    String.format("物料[%s] 超出最高发货数量：最高 %s",
                            issue.materialLabel(), fmtQty(issue.maxQty)));
        }
    }

    /**
     * 提交/审核前的只读超量预检：前端据此在提交前弹出「取消 / 继续提交」确认框。
     * 返回 policy + exceeded + issues + message（不改变单据状态、不抛“不允许”异常）。
     */
    @Override
    public Map<String, Object> checkOverShipment(Long id) {
        SaleDelivery delivery = getById(id);
        if (delivery == null) throw new BusinessException("出库单不存在");
        List<SaleDeliveryItem> items = itemMapper.selectList(
                new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", id).orderByAsc("line_no"));
        String policy = sysParamValidator.getOverQtyPolicy(SaleOverQtyGuardService.KEY_SALE_DELIVERY);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("policy", policy);

        List<Map<String, Object>> issueMaps = new java.util.ArrayList<>();
        List<String> lines = new java.util.ArrayList<>();
        if (!SysParamValidator.OVER_QTY_NO_CONTROL.equals(policy) && items != null && !items.isEmpty()) {
            for (OverShipmentIssue issue : collectOverShipmentIssues(items, id)) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("lineNo", issue.lineNo);
                row.put("materialCode", issue.materialCode);
                row.put("materialName", issue.materialName);
                row.put("orderQty", issue.orderQty);
                row.put("ratio", issue.ratio);
                row.put("maxQty", issue.maxQty);
                row.put("priorDelivered", issue.priorDelivered);
                row.put("returned", issue.returned);
                row.put("currentQty", issue.currentQty);
                row.put("total", issue.total);
                issueMaps.add(row);
                lines.add(String.format("销售出库单第%d行物料[%s] 超出最高发货数量：最高 %s",
                        issue.lineNo, issue.materialLabel(), fmtQty(issue.maxQty)));
            }
        }
        boolean exceeded = !lines.isEmpty();
        String message = String.join("；", lines);
        if (exceeded && SysParamValidator.OVER_QTY_DENY.equals(policy)) {
            message = message + "，不允许保存";
        }
        result.put("exceeded", exceeded);
        result.put("message", message);
        result.put("issues", issueMaps);
        return result;
    }

    /** 汇总超量行：订单数量×(1+比例/100) vs 已下推-已生效退货+本次 */
    private List<OverShipmentIssue> collectOverShipmentIssues(List<SaleDeliveryItem> items, Long currentDeliveryId) {
        List<OverShipmentIssue> issues = new java.util.ArrayList<>();
        Map<Long, BigDecimal> currentQtyByOrderItem = new java.util.LinkedHashMap<>();
        Map<Long, SaleDeliveryItem> sampleByOrderItem = new java.util.LinkedHashMap<>();
        for (SaleDeliveryItem item : items) {
            if (item.getSourceItemId() == null) continue;
            BigDecimal q = nvl(item.getQuantity());
            if (q.signum() <= 0) continue;
            currentQtyByOrderItem.merge(item.getSourceItemId(), q, BigDecimal::add);
            sampleByOrderItem.putIfAbsent(item.getSourceItemId(), item);
        }

        for (Map.Entry<Long, BigDecimal> entry : currentQtyByOrderItem.entrySet()) {
            Long orderItemId = entry.getKey();
            SaleOrderItem orderItem = saleOrderItemMapper.selectById(orderItemId);
            if (orderItem == null || orderItem.getOrderId() == null) continue;
            SaleOrder order = saleOrderService.getById(orderItem.getOrderId());
            if (order == null) continue;
            BigDecimal orderQty = nvl(orderItem.getQuantity());
            if (orderQty.signum() <= 0) continue;

            BigDecimal ratio = nvl(order.getOvershipRatio());
            BigDecimal maxQty = orderQty.add(orderQty.multiply(ratio)
                    .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));

            BigDecimal priorDelivered = BigDecimal.ZERO;
            for (SaleDeliveryItem prior : itemMapper.selectList(
                    new QueryWrapper<SaleDeliveryItem>().eq("source_item_id", orderItemId))) {
                if (currentDeliveryId != null && currentDeliveryId.equals(prior.getDeliveryId())) continue;
                SaleDelivery head = getById(prior.getDeliveryId());
                if (head == null || BillStatusUtils.isVoided(head.getStatus())
                        || "已取消".equals(head.getStatus())) continue;
                priorDelivered = priorDelivered.add(nvl(prior.getQuantity()));
            }

            BigDecimal returned = BigDecimal.ZERO;
            for (SaleReturnItem returnItem : saleReturnItemMapper.selectList(
                    new QueryWrapper<SaleReturnItem>().eq("source_item_id", orderItemId))) {
                SaleReturn returnHead = saleReturnMapper.selectById(returnItem.getReturnId());
                if (returnHead == null || BillStatusUtils.isVoided(returnHead.getStatus())
                        || "已取消".equals(returnHead.getStatus())) continue;
                if (!BillStatusUtils.isApprovedClass(returnHead.getStatus())) continue;
                returned = returned.add(nvl(returnItem.getQuantity()));
            }

            BigDecimal total = priorDelivered.subtract(returned).add(entry.getValue());
            if (total.compareTo(maxQty) > 0) {
                SaleDeliveryItem sample = sampleByOrderItem.get(orderItemId);
                OverShipmentIssue issue = new OverShipmentIssue();
                issue.lineNo = sample != null && sample.getLineNo() != null ? sample.getLineNo() : 1;
                issue.materialCode = sample == null ? null : sample.getMaterialCode();
                issue.materialName = sample == null ? null : sample.getMaterialName();
                issue.orderQty = orderQty;
                issue.ratio = ratio;
                issue.maxQty = maxQty;
                issue.priorDelivered = priorDelivered;
                issue.returned = returned;
                issue.currentQty = entry.getValue();
                issue.total = total;
                issues.add(issue);
            }
        }
        return issues;
    }

    private static class OverShipmentIssue {
        int lineNo;
        String materialCode;
        String materialName;
        BigDecimal orderQty;
        BigDecimal ratio;
        BigDecimal maxQty;
        BigDecimal priorDelivered;
        BigDecimal returned;
        BigDecimal currentQty;
        BigDecimal total;

        String materialLabel() {
            String code = materialCode == null ? "" : materialCode.trim();
            String name = materialName == null ? "" : materialName.trim();
            if (code.isEmpty()) return name;
            if (name.isEmpty()) return code;
            return code + " " + name;
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String fmtQty(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    /**
     * 销售出库明细校验：根据物料配置的控制选项进行校验
     * - 批次管理物料必须填写批次号
     * - 序列号管理物料必须填写序列号
     * - 货位管理物料必须填写货位
     * - 保质期管理物料出库时检查有效期
     */
    private void validateDeliveryItems(List<SaleDeliveryItem> items) {
        for (int i = 0; i < items.size(); i++) {
            SaleDeliveryItem item = items.get(i);
            if (item.getMaterialId() != null) {
                Material material = materialService.getById(item.getMaterialId());
                if (material != null) {
                    // 批次管理物料必须填写批次号
                    if (material.getIsLotControl() != null && material.getIsLotControl() == 1) {
                        if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]批次管理物料必须填写批次号");
                        }
                        // 化妆品批记录：未放行批次出库控制（允许/预警提示/不允许，设置中心可切）
                        batchReleaseGuardService.enforceOutbound(material.getMaterialId(), item.getBatchNo(),
                                "第" + (i + 1) + "行[" + material.getMaterialCode() + "]");
                    }
                    // 序列号管理物料必须填写序列号
                    if (material.getIsSnControl() != null && material.getIsSnControl() == 1) {
                        if (item.getSerialNo() == null || item.getSerialNo().isBlank()) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]序列号管理物料必须填写序列号");
                        }
                    }
                    // 货位管理物料必须填写货位
                    if (material.getIsBinControl() != null && material.getIsBinControl() == 1) {
                        if (item.getBinId() == null) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]货位管理物料必须选择货位");
                        }
                    }
                    // 保质期管理物料出库时检查有效期
                    if (material.getIsExpiryControl() != null && material.getIsExpiryControl() == 1) {
                        if (item.getExpiryDate() != null && item.getExpiryDate().isBefore(LocalDate.now())) {
                            throw new BusinessException("第" + (i + 1) + "行[" + material.getMaterialCode() + "]物料已过期，无法出库");
                        }
                    }
                }
            }
        }
    }

    /**
     * KGW 设置中心业务规则统一校验
     * - 客户必填 / 货品必填 / 仓库必填
     * - 库存预警：库存不足时阻止出库
     */
    private void applyKgwBusinessRules(SaleDelivery delivery, List<SaleDeliveryItem> items) {
        if (delivery == null || items == null || items.isEmpty()) return;

        // 1. 客户必填
        if (kgwRules.isCustomerRequired() && delivery.getCustomerId() == null) {
            throw new BusinessException("【设置中心-客户必填】客户不能为空，请在销售出库单中选择客户");
        }
        // 2. 仓库必填
        if (kgwRules.isWarehouseRequired() && delivery.getWarehouseId() == null) {
            throw new BusinessException("【设置中心-仓库必填】出库仓库不能为空");
        }

        // 3. 库存预警校验
        boolean inventoryWarnOn = kgwRules.isInventoryWarningEnabled();
        for (int i = 0; i < items.size(); i++) {
            SaleDeliveryItem item = items.get(i);
            int lineNo = i + 1;

            if (kgwRules.isMaterialRequired() && item.getMaterialId() == null) {
                throw new BusinessException("【设置中心-货品必填】第" + lineNo + "行货品不能为空");
            }

            // 设置中心：允许单价为0校验（sale.outbound.allow.zero.price）
            sysParamValidator.validateAllowZeroPrice("sale.outbound.allow.zero.price", "销售出库单", item.getUnitPrice(), lineNo);

            // 库存预警：检查物料库存是否足够
            if (inventoryWarnOn && item.getMaterialId() != null && item.getQuantity() != null
                && delivery.getWarehouseId() != null) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(
                    item.getMaterialId(), delivery.getWarehouseId());
                BigDecimal available = BigDecimal.ZERO;
                if (invList != null) {
                    for (com.erp.inventory.entity.Inventory inv : invList) {
                        if (inv != null && inv.getAvailableQuantity() != null) {
                            available = available.add(inv.getAvailableQuantity());
                        }
                    }
                }
                available = applyStockCheckScope(available, item.getMaterialId(), delivery.getSourceBillType(), delivery.getSourceBillNo());
                if (available.compareTo(item.getQuantity()) < 0) {
                    throw new BusinessException(String.format(
                        "【设置中心-库存预警】第%d行物料库存不足，可用 %.2f，需出库 %.2f（校验口径：%s）",
                        lineNo, available, item.getQuantity(), getStockCheckScopeLabel()));
                }
            }
        }

        // 4. 收款管控校验（按来源销售订单上选定的发货收款管控，详见 checkPaymentBeforeDelivery）
        // 移至 recalculateDelivery 之后执行，确保使用重算后的本单净额做额度校验
    }

    /**
     * 发货前收款管控校验（2026-10-19 下放单据）
     * 模式来源：来源销售订单上的「发货收款管控」（sale_order.payment_control）—— 源头单据说明一切，
     *   出库环节不可改、绕不过；订单未选（历史单据为空）时按设置中心「发货收款管控模式」兜底：
     *   none    - 不管控
     *   quota   - 收多少款发多少货：累计已发货（含本次）净额不超过已收款
     *   full    - 全额收款才能发货
     *   advance - 定金模式：收款达到订单定金后不限量发货；订单未设定金时需至少有一笔收款
     * 审批放行（sale.delivery.payment.check.bypass.approved）：
     *   true - 审批通过的发货单跳过收款校验
     * 注：原「发货收款管控范围」（按客户付款分类分流）已取消——管控方式由做单人在源头订单上直接选择。
     */
    private void checkPaymentBeforeDelivery(SaleDelivery delivery) {
        // 必须关联销售订单才能做收款校验
        if (delivery.getSourceBillNo() == null || delivery.getSourceBillNo().isBlank()) return;
        if (!"sale_order".equals(delivery.getSourceBillType()) && !"销售订单".equals(delivery.getSourceBillType())) return;

        SaleOrder order = saleOrderService.getOne(
            new QueryWrapper<SaleOrder>()
                .eq("order_no", delivery.getSourceBillNo()));

        // 收款管控模式：来源销售订单上选定的值优先，订单为空/未选时按设置中心默认兜底（旧行为一致）
        String controlMode = (order != null && order.getPaymentControl() != null && !order.getPaymentControl().isBlank())
            ? order.getPaymentControl().trim()
            : sysConfigService.getValue("sale.delivery.payment.control", "none");
        if ("none".equalsIgnoreCase(controlMode)) return;

        // 审批通过的发货单是否跳过收款校验
        boolean bypassIfApproved = sysConfigService.getBooleanValue("sale.delivery.payment.check.bypass.approved", false);
        if (bypassIfApproved && "已生效".equals(delivery.getApprovalStatus())) {
            log.info("发货单[{}]已审批通过，跳过收款校验", delivery.getDeliveryNo());
            return;
        }

        if (order == null) {
            throw new BusinessException("来源销售订单[" + delivery.getSourceBillNo() + "]不存在，无法进行收款校验");
        }

        BigDecimal orderAmount = order.getNetAmount() == null ? BigDecimal.ZERO : order.getNetAmount();
        BigDecimal receivedAmount = order.getReceivedAmount() == null ? BigDecimal.ZERO : order.getReceivedAmount();
        BigDecimal tolerance = new BigDecimal(sysConfigService.getValue("sale.delivery.payment.advance.tolerance", "0"));

        if ("full".equalsIgnoreCase(controlMode)) {
            // 全额收款校验
            if (receivedAmount.add(tolerance).compareTo(orderAmount) < 0) {
                throw new BusinessException(String.format(
                    "【收款管控】发货前需全额收款。订单金额: %.2f，已收款: %.2f，差额: %.2f",
                    orderAmount, receivedAmount, orderAmount.subtract(receivedAmount)));
            }
        } else if ("quota".equalsIgnoreCase(controlMode)) {
            // 收多少款发多少货：累计已发货（不含本次，排除已作废）+ 本次发货净额 不超过 已收款+容差
            BigDecimal deliveredAmount = getDeliveredAmount(order.getOrderNo(), delivery.getDeliveryId());
            BigDecimal currentAmount = delivery.getNetAmount() == null ? BigDecimal.ZERO : delivery.getNetAmount();
            BigDecimal totalDeliver = deliveredAmount.add(currentAmount);
            if (totalDeliver.compareTo(receivedAmount.add(tolerance)) > 0) {
                throw new BusinessException(String.format(
                    "【收款管控】收多少款发多少货。订单金额: %.2f，已收款: %.2f，已发货(含本次): %.2f，超出可发额度: %.2f",
                    orderAmount, receivedAmount, totalDeliver, totalDeliver.subtract(receivedAmount)));
            }
        } else if ("advance".equalsIgnoreCase(controlMode)) {
            // 定金模式：收款达到订单定金后不限量发货；未设定金时需已有收款记录
            BigDecimal requiredAdvance = order.getDepositAmount();
            if (requiredAdvance != null && requiredAdvance.compareTo(BigDecimal.ZERO) > 0) {
                if (receivedAmount.add(tolerance).compareTo(requiredAdvance) < 0) {
                    throw new BusinessException(String.format(
                        "【收款管控】定金模式：发货前收款需达到订单定金。订单定金: %.2f，已收款: %.2f，差额: %.2f",
                        requiredAdvance, receivedAmount, requiredAdvance.subtract(receivedAmount)));
                }
            } else {
                if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("【收款管控】定金模式：订单未设定金，发货前需至少有一笔收款记录");
                }
            }
        }
    }

    /**
     * 查询订单累计已发货净额（价税合计）
     * 统计口径：来源为该销售订单的**已生效/待审核**发货单，排除已作废、已取消、草稿（2026-10-19 用户拍板：
     * 草稿不记入累积——被拦后遗留的草稿不再占用额度）；排除指定发货单自身（编辑场景避免重复计算）
     */
    private BigDecimal getDeliveredAmount(String orderNo, Long excludeDeliveryId) {
        QueryWrapper<SaleDelivery> qw = new QueryWrapper<SaleDelivery>()
                .select("net_amount")
                .eq("source_bill_no", orderNo)
                .in("source_bill_type", "sale_order", "销售订单")
                .notIn("status", "已作废", "草稿", "已取消");
        if (excludeDeliveryId != null) {
            qw.ne("delivery_id", excludeDeliveryId);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SaleDelivery d : list(qw)) {
            if (d != null && d.getNetAmount() != null) {
                total = total.add(d.getNetAmount());
            }
        }
        return total;
    }

    /**
     * 设置中心库存校验口径（inv.stock.check.scope）：
     * - actual（默认）：仅按实际可用库存校验
     * - virtual：实际可用库存 - 生效销售订单未发货占用（界面口径名：预计可用量）
     * 出库单来源于销售订单时，排除该订单自身的剩余占用（本单即履行该占用）
     */
    private BigDecimal applyStockCheckScope(BigDecimal available, Long materialId,
                                            String sourceBillType, String sourceBillNo) {
        if (!isVirtualStockScope() || materialId == null) {
            return available;
        }
        String excludeOrderNo = "sale_order".equals(sourceBillType) ? sourceBillNo : null;
        BigDecimal reserved = virtualStockService.getReservedQuantity(materialId, excludeOrderNo);
        return available.subtract(reserved);
    }

    private boolean isVirtualStockScope() {
        return "virtual".equalsIgnoreCase(sysConfigService.getValue("inv.stock.check.scope", "actual"));
    }

    private String getStockCheckScopeLabel() {
        return isVirtualStockScope() ? "预计可用量（扣订单占用）" : "实际库存";
    }

    @Override
    public PageResult<SaleDelivery> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleDelivery> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleDelivery.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入出库单号/来源订单号/客户名称搜索」适配（说明书 2.2）：出库单号 + 来源订单号 OR 关联客户主键
        // （sale_delivery 无客户名称列 → 客户名称先解析成 customer_id，作为同一 OR 分组的一支）
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword,
                List.of("delivery_no", "order_no"), "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 delivery_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "delivery_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_delivery", customSearch, CustomFieldSearchHelper.idColumnOf("sale_delivery"));
        Page<SaleDelivery> page = new Page<>(current, size);
        Page<SaleDelivery> result = page(page, wrapper);
        for (SaleDelivery d : result.getRecords()) {
            fillDisplayFields(d);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在标准过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<SaleDelivery> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SaleDelivery> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleDelivery.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入出库单号/来源订单号/客户名称搜索」适配（说明书 2.2）：出库单号 + 来源订单号 OR 关联客户主键
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword,
                List.of("delivery_no", "order_no"), "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 delivery_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "delivery_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 表头字段过滤（2026-10-14 筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "saleDelivery", headerFilters);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_delivery", customSearch, CustomFieldSearchHelper.idColumnOf("sale_delivery"));
        Page<SaleDelivery> page = new Page<>(current, size);
        Page<SaleDelivery> result = page(page, wrapper);
        for (SaleDelivery d : result.getRecords()) {
            fillDisplayFields(d);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleDelivery getDetail(Long id) {
        SaleDelivery delivery = getById(id);
        if (delivery == null) throw new BusinessException("出库单不存在");
        List<SaleDeliveryItem> items = itemMapper.selectList(
                new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", id).orderByAsc("line_no"));
        fillDisplayFields(delivery);
        if (items != null) {
            for (SaleDeliveryItem item : items) {
                fillItemDisplayFields(item);
            }
            // 该订单行是否有待执行的销售替换指示（前端据此隐藏仓库替代按钮）
            java.util.Set<Long> srcIds = new java.util.HashSet<>();
            for (SaleDeliveryItem item : items) {
                if (item.getSourceItemId() != null) srcIds.add(item.getSourceItemId());
            }
            java.util.Set<Long> activePlanItems = saleOrderSubstitutePlanService.activePendingItemIds(srcIds);
            for (SaleDeliveryItem item : items) {
                item.setOrderHasActivePlan(item.getSourceItemId() != null
                        && activePlanItems.contains(item.getSourceItemId()));
            }
        }
        delivery.setItems(items);
        return delivery;
    }

    private void fillDisplayFields(SaleDelivery d) {
        if (d.getCustomerId() != null) {
            Customer c = customerService.getById(d.getCustomerId());
            if (c != null) d.setCustomerName(c.getCustomerName());
        }
        if (d.getSalerId() != null) {
            Employee e = employeeService.getById(d.getSalerId());
            if (e != null) d.setSalerName(e.getEmployeeName());
        }
        if (d.getCurrencyId() != null) {
            Currency cur = currencyService.getById(d.getCurrencyId());
            if (cur != null) d.setCurrencyCode(cur.getCurrencyCode());
        }
        if (d.getWarehouseId() != null) {
            Warehouse w = warehouseService.getById(d.getWarehouseId());
            if (w != null) d.setWarehouseName(w.getStockName());
        }
    }

    private void fillItemDisplayFields(SaleDeliveryItem item) {
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
        // 来源订单行原货品（替换弹窗判断"替换自"用）
        if (item.getSourceItemId() != null) {
            SaleOrderItem oi = saleOrderItemMapper.selectById(item.getSourceItemId());
            if (oi != null) {
                item.setSourceMaterialId(oi.getMaterialId());
                item.setSourceMaterialCode(oi.getMaterialCode());
                item.setSourceMaterialName(oi.getMaterialName());
            }
        }
    }

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SaleDelivery delivery = getById(id);
        if (delivery == null) throw new BusinessException("出库单不存在");
        // 状态校验 / 严格校验 / 审批开关 / 审批任务创建 全部由统一状态机执行
        super.submitForApproval(delivery, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前严格校验（原 submitForApproval 内的校验整体搬入，提交才触发，保存草稿不校验） */
    @Override
    protected void validateStrictSubmit(SaleDelivery delivery) {
        Long id = delivery.getDeliveryId();
        List<SaleDeliveryItem> items = itemMapper.selectList(
            new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("出库单没有明细，请至少添加一行物料后再提交");
        }

        if (delivery.getCustomerId() == null) {
            throw new BusinessException("客户为空，请选择客户后再提交");
        }
        Customer customer = customerService.getById(delivery.getCustomerId());
        if (customer == null) {
            throw new BusinessException("关联客户不存在(customerId=" + delivery.getCustomerId() + ")，请重新选择客户");
        }
        if (delivery.getSalerId() != null) {
            Employee emp = employeeService.getById(delivery.getSalerId());
            if (emp == null) {
                throw new BusinessException("关联销售员不存在(employeeId=" + delivery.getSalerId() + ")，请重新选择销售员");
            }
        }
        if (delivery.getCurrencyId() == null) {
            throw new BusinessException("币种为空，请选择币种后再提交");
        }
        Currency cur = currencyService.getById(delivery.getCurrencyId());
        if (cur == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + delivery.getCurrencyId() + ")，请重新选择币种");
        }
        if (delivery.getWarehouseId() == null) {
            throw new BusinessException("出库仓库为空，请选择仓库后再提交");
        }
        Warehouse wh = warehouseService.getById(delivery.getWarehouseId());
        if (wh == null) {
            throw new BusinessException("关联仓库不存在(warehouseId=" + delivery.getWarehouseId() + ")，请重新选择仓库");
        }

        boolean inventoryWarnOn = kgwRules.isInventoryWarningEnabled();
        boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);

        for (int i = 0; i < items.size(); i++) {
            SaleDeliveryItem item = items.get(i);
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
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matCode = mat != null ? mat.getMaterialCode() : "物料";
                throw new BusinessException("第" + lineNo + "行[" + matCode + "]数量必须大于 0");
            }
            // 【2026-11-10 全单据验收修复】库存是基本单位口径，行数量可能是辅单位（如 30 套 = 3 件）；
            // 原实现直接拿辅单位数量比库存 → 库存足够却被判不足 / 不足却被放行。校验改用折算后的基本单位数量。
            BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) stockQty = qty;
            if (!negativeAllowed && inventoryWarnOn) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(
                    item.getMaterialId(), delivery.getWarehouseId());
                BigDecimal available = BigDecimal.ZERO;
                if (invList != null) {
                    for (com.erp.inventory.entity.Inventory inv : invList) {
                        if (inv != null && inv.getAvailableQuantity() != null) {
                            available = available.add(inv.getAvailableQuantity());
                        }
                    }
                }
                available = applyStockCheckScope(available, item.getMaterialId(), delivery.getSourceBillType(), delivery.getSourceBillNo());
                if (available.compareTo(stockQty) < 0) {
                    String mc = mat != null ? mat.getMaterialCode() : "";
                    String mn = mat != null ? mat.getMaterialName() : "";
                    String sn = wh != null ? wh.getStockName() : "";
                    throw new BusinessException(String.format(
                        "第%d行物料[%s %s]库存不足，仓库[%s]可用 %.2f，需出库 %.2f（校验口径：%s）",
                        lineNo, mc, mn, sn, available, stockQty, getStockCheckScopeLabel()));
                }
            }
        }

        // === 提交严格校验（保存宽松：以下校验从保存方法移到提交）===
        // 设置中心：来源单号必填（sale.outbound.require.source）
        sysParamValidator.validateRequireSource("sale.outbound.require.source", "销售出库单", delivery.getSourceBillNo());
        // 明细级业务校验：批次/序列号/货位/保质期（明细已落库，提交时以库中明细为准）
        validateDeliveryItems(items);
        // 【2026-10-31 超量发货比例】逐行校验最高发货数量（生效+草稿下推 + 本次 - 已生效退货）
        // 【2026-11-17 替换货】换货生成的出库单不读三态开关（先补发时订单已发数量会暂超），改走 H2 硬卡
        if (isExchangeSourced(delivery)) {
            validateExchangeHardCap(delivery, items);
        } else {
            validateOverShipment(delivery, items, id);
            validateSubstituteExecution(delivery, items);
        }
        // KGW 设置中心业务规则（客户/货品必填、允许单价为0开关、库存预警）
        applyKgwBusinessRules(delivery, items);
        // 收款管控校验（quota 模式依赖本单已落库的净额）
        checkPaymentBeforeDelivery(delivery);
    }

    /**
     * 统一状态机审核入口（含审批引擎防护 + 下推联动/超量兜底校验）。
     * 覆盖 Object 签名：审批引擎在基类内部按 Object 签名回调，只有覆盖它才能保证防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        SaleDelivery delivery = getById((Long) id);
        if (delivery == null) throw new BusinessException("出库单不存在");
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        // （由引擎回调 onApprovalApproved 触发的审核不受影响：引擎会先把任务置为已通过再回写单据）
        if (approvalRuntimeService.getRunningTask("SALE_DELIVERY", (Long) id) != null) {
            throw new BusinessException("销售出库单已提交审批流程，请由指定审批人在审批中心处理");
        }
        // 【2026-10-14 下推联动】来源销售订单已完结（已结案）不允许再出库：
        // 拦在回写勾稽之前，防止"订单已完结后再提交在途出库单"绕过下推联动
        List<SaleDeliveryItem> approveItems = itemMapper.selectList(
                new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", (Long) id).orderByAsc("line_no"));
        for (SaleDeliveryItem guardItem : approveItems) {
            // 【2026-11-17 替换货】换货是售后动作：订单发货完成会自动结案，不能因此拦住换货出库单
            if (isExchangeSourced(delivery)) break;
            if (guardItem.getSourceId() != null && saleOrderService.isOrderClosed(guardItem.getSourceId())) {
                throw new BusinessException("来源销售订单已完结（已结案），出库单[" + delivery.getDeliveryNo() + "]不能生效，请先反结案该订单");
            }
        }
        // 【2026-10-31 超量发货比例】审核兜底校验：防止绕过提交直接审核
        // 【2026-11-17 替换货】换货出库单走 H2 硬卡，不参与三态超量
        if (isExchangeSourced(delivery)) {
            validateExchangeHardCap(delivery, approveItems);
        } else {
            validateOverShipment(delivery, approveItems, (Long) id);
            validateSubstituteExecution(delivery, approveItems);
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：刷新成本 + 扣减库存 + 回写订单已出库数量 + 按配置生成收款单草稿 */
    @Override
    public void onApproved(SaleDelivery delivery) {
        String approver = delivery.getApprovedBy();
        applyStockAndOrderImpact(delivery);
        createAutoReceiptIfConfigured(delivery);
        log.info("销售出库单审核: {} 审核人: {}", delivery.getDeliveryNo(), approver);
    }

    /** 反作废：恢复到已生效，重新应用库存/订单/成本/指示，并按配置恢复自动收款单草稿 */
    @Override
    public void onUnvoided(SaleDelivery delivery) {
        if (delivery == null || !"已生效".equals(delivery.getStatus())) return;
        applyStockAndOrderImpact(delivery);
        createAutoReceiptIfConfigured(delivery);
        log.info("销售出库单反作废恢复完成: {}", delivery.getDeliveryNo());
    }

    /** 作废已生效单据：回冲库存/订单/成本/指示 + 清理自动收款单草稿（标准功能，2026-11-27） */
    @Override
    public void onVoided(SaleDelivery delivery) {
        if (delivery == null) return;
        reverseStockAndOrderImpact(delivery);
        financeService.rollbackAutoReceiptBySource("销售出库单", delivery.getDeliveryNo());
        log.info("销售出库单作废回冲完成: {}", delivery.getDeliveryNo());
    }

    /** 按设置中心「收款单生成方式」自动生成收款单草稿（幂等：已有未作废收款单则不重复生成） */
    private void createAutoReceiptIfConfigured(SaleDelivery delivery) {
        try {
            String arSource = sysConfigService.getValue("finance.ar.source_bill_type", "none");
            if ("sale_delivery".equals(arSource)) {
                financeService.createReceiptDraftFromSource("销售出库单", delivery.getDeliveryId());
            }
        } catch (Exception e) {
            log.warn("出库单审核生成收款单草稿失败，出库单: {}", delivery.getDeliveryNo(), e);
        }
    }

    /**
     * 应用出库影响（审核 / 反作废共用）：
     * 刷新成本、扣库存、回写订单已出库数量与成本、销售替换指示已执行数量 +。
     */
    private void applyStockAndOrderImpact(SaleDelivery delivery) {
        Long id = delivery.getDeliveryId();
        String approver = delivery.getApprovedBy();
        // === 审核时刷新avg_cost为最新核算成本（审核后冻结） ===
        List<SaleDeliveryItem> refreshItems = itemMapper.selectList(
            new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", id).orderByAsc("line_no"));
        if (refreshItems != null) {
            for (SaleDeliveryItem item : refreshItems) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
        }

        List<SaleDeliveryItem> items = itemMapper.selectList(
            new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", id).orderByAsc("line_no"));
        Map<Long, BigDecimal> planQty = new LinkedHashMap<>();
        if (items != null) {
            for (SaleDeliveryItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : delivery.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                // 多单位换算（2026-11-09）：库存记账只认基本单位数量；未启用换算时回落到本行数量
                BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
                // 扣减库存
                if (stockId != null && materialId != null) {
                    // 【2026-09-11 材料成本归集】出库成本必须在扣减前取（FIFO/LIFO 依赖扣减前批次结存），
                    //   取到后作为"本次实际出库成本"冻结到 avg_cost（不动 unit_price/amount，那是售价）。
                    BigDecimal outUnitCost = inventoryService.getOutUnitCost(
                            stockId, item.getBinId(), materialId, item.getBatchNo());
                    inventoryService.deductStock(
                        stockId, item.getBinId(), materialId, item.getBatchNo(),
                        stockQty,
                        delivery.getDeliveryNo(), "SALE_OUT",
                        "SaleDelivery", delivery.getDeliveryNo(), approver);
                    if (outUnitCost.compareTo(BigDecimal.ZERO) > 0) {
                        item.setAvgCost(outUnitCost);
                        itemMapper.updateById(item);
                    }
                } else {
                    throw new BusinessException("出库单第" + item.getLineNo()
                            + "行未指定仓库/库存地点，无法扣减库存与核算出库成本，请先指定仓库");
                }
                // 回写销售订单已出库数量
                if (item.getSourceId() != null && item.getSourceItemId() != null) {
                    saleOrderService.updateTurnedQuantity(
                        item.getSourceId(), item.getSourceItemId(),
                        item.getQuantity(), "output");
                }
                // 【2026-11-17 替换货】成本回写订单原货行：先快照回写前成本，再覆盖为替换货的实际出库成本
                if (item.getReplacedFromMaterialId() != null && item.getSourceItemId() != null) {
                    SaleOrderItem orderItem = saleOrderItemMapper.selectById(item.getSourceItemId());
                    if (orderItem != null) {
                        if (item.getOrderCostBefore() == null) {
                            item.setOrderCostBefore(orderItem.getAvgCost());
                            itemMapper.updateById(item);
                        }
                        BigDecimal actualCost = item.getAvgCost() != null ? item.getAvgCost() : BigDecimal.ZERO;
                        orderItem.setAvgCost(actualCost);
                        saleOrderItemMapper.updateById(orderItem);
                    }
                }
                // 【2026-11-27 替换发货指示】已执行数量 +
                if (item.getReplacePlanId() != null) {
                    planQty.merge(item.getReplacePlanId(), qty, BigDecimal::add);
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> e : planQty.entrySet()) {
            saleOrderSubstitutePlanService.addTurned(e.getKey(), e.getValue());
        }
    }

    /**
     * 重置草稿: 已生效 → 草稿
     * - 回冲库存(即时库存增加)
     * - 减少销售订单的已出库数量
     * - 使用 UpdateWrapper.set(field, null) 清空 approved_by/approved_at
     */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleDelivery delivery = getById(id);
        if (delivery == null) throw new BusinessException("出库单不存在");
        // 状态校验 + 下游校验 + 状态迁移由基类执行；业务冲回在 onReverseApproved 钩子
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式置空 approved_by/approved_at
        UpdateWrapper<SaleDelivery> uw = new UpdateWrapper<>();
        uw.eq("delivery_id", id)
            .set("approved_by", null)
            .set("approved_at", null)
            .set("updated_at", LocalDateTime.now())
            .set("updated_by", operator);
        update(uw);
    }

    /** 重置草稿影响：回冲库存（按审核冻结的实际出库成本）+ 减少销售订单已出库数量 + 指示已执行 - + 清理自动收款单草稿 */
    @Override
    public void onReverseApproved(SaleDelivery delivery) {
        reverseStockAndOrderImpact(delivery);
        financeService.rollbackAutoReceiptBySource("销售出库单", delivery.getDeliveryNo());
        log.info("销售出库单重置草稿回冲完成: {}", delivery.getDeliveryNo());
    }

    /**
     * 回冲出库影响（重置草稿 / 作废 / 删除已生效单据共用）：
     * 回冲库存、减少订单已发、恢复订单成本、销售替换指示已执行数量 -。
     */
    private void reverseStockAndOrderImpact(SaleDelivery delivery) {
        Long id = delivery.getDeliveryId();
        List<SaleDeliveryItem> items = itemMapper.selectList(
            new QueryWrapper<SaleDeliveryItem>().eq("delivery_id", id).orderByAsc("line_no"));
        Map<Long, BigDecimal> planQty = new LinkedHashMap<>();
        if (items != null) {
            for (SaleDeliveryItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : delivery.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                // 多单位换算（2026-11-09）：库存记账只认基本单位数量；未启用换算时回落到本行数量
                BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
                // 回冲库存
                if (stockId != null && materialId != null) {
                    // 【2026-09-14 修复】回冲必须用审核时冻结的"本次实际出库成本"(avg_cost)；
                    // 原实现直接用 unit_price(售价) 回冲 → 库存单位成本/库存金额被虚增毛利额。
                    BigDecimal unitCost = item.getAvgCost() != null ? item.getAvgCost() : BigDecimal.ZERO;
                    if (unitCost.compareTo(BigDecimal.ZERO) <= 0) {
                        unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                    }
                    inventoryService.addStock(
                        stockId, item.getBinId(), materialId, item.getBatchNo(),
                        stockQty, unitCost,
                        delivery.getDeliveryNo(), "SALE_OUT_REVERSE",
                        "SaleDelivery", delivery.getDeliveryNo(), delivery.getApprovedBy());
                }
                // 减少销售订单已出库数量
                if (item.getSourceId() != null && item.getSourceItemId() != null) {
                    saleOrderService.updateTurnedQuantity(
                        item.getSourceId(), item.getSourceItemId(),
                        qty.negate(), "output");
                }
                // 【2026-11-17 替换货】回退镜像：订单行成本恢复为回写前快照
                if (item.getReplacedFromMaterialId() != null && item.getSourceItemId() != null
                        && item.getOrderCostBefore() != null) {
                    SaleOrderItem orderItem = saleOrderItemMapper.selectById(item.getSourceItemId());
                    if (orderItem != null) {
                        orderItem.setAvgCost(item.getOrderCostBefore());
                        saleOrderItemMapper.updateById(orderItem);
                    }
                }
                // 【2026-11-27 替换发货指示】已执行数量 -
                if (item.getReplacePlanId() != null) {
                    planQty.merge(item.getReplacePlanId(), qty, BigDecimal::add);
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> e : planQty.entrySet()) {
            saleOrderSubstitutePlanService.addTurned(e.getKey(), e.getValue().negate());
        }
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        SaleDelivery delivery = getById(id);
        if (delivery == null) throw new BusinessException("出库单不存在");
        if (!"草稿".equals(delivery.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + delivery.getStatus());
        }
        delivery.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            delivery.setRemark((delivery.getRemark() != null ? delivery.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(delivery);
        log.info("销售出库单取消: {} 原因: {}", delivery.getDeliveryNo(), reason);
    }

    @Override
    public String generateDeliveryNo() {
        String docType = "SALE_DELIVERY";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SOUT-" + dateStr + "-";
            QueryWrapper<SaleDelivery> wrapper = new QueryWrapper<>();
            wrapper.likeRight("delivery_no", prefix).orderByDesc("delivery_no").last("LIMIT 1");
            SaleDelivery last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getDeliveryNo() != null) {
                try {
                    seq = Integer.parseInt(last.getDeliveryNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
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
        // 仅草稿可删 + 下游校验 + 整单级联删除 由基类执行
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

    @Override
    @Transactional
    public SaleInvoice pushToInvoice(Long id, String operator) {
        SaleDelivery delivery = getDetail(id);
        if (delivery == null) {
            throw new BusinessException("销售出库单不存在");
        }
        if (!"已生效".equals(delivery.getApprovalStatus())) {
            throw new BusinessException("只有已生效的销售出库单才能下推");
        }
        List<SaleDeliveryItem> deliveryItems = delivery.getItems();
        if (deliveryItems == null || deliveryItems.isEmpty()) {
            throw new BusinessException("销售出库单无明细，无法下推");
        }

        SaleInvoice invoice = new SaleInvoice();
        invoice.setInvoiceNo(saleInvoiceService.generateInvoiceNo());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setCustomerId(delivery.getCustomerId());
        invoice.setSalerId(delivery.getSalerId());
        invoice.setCurrencyId(delivery.getCurrencyId());
        invoice.setExchangeRate(delivery.getExchangeRate());
        invoice.setTotalAmount(delivery.getTotalAmount() != null ? delivery.getTotalAmount() : BigDecimal.ZERO);
        invoice.setTaxAmount(delivery.getTaxAmount() != null ? delivery.getTaxAmount() : BigDecimal.ZERO);
        invoice.setDiscountRate(delivery.getDiscountRate() != null ? delivery.getDiscountRate() : BigDecimal.ZERO);
        invoice.setDiscountAmount(delivery.getDiscountAmount() != null ? delivery.getDiscountAmount() : BigDecimal.ZERO);
        invoice.setNetAmount(delivery.getNetAmount() != null ? delivery.getNetAmount() : BigDecimal.ZERO);
        // 计税方式随单下推（2026-10-09）：出库单为行级口径、无表头列，按首个明细的计税类型反推；
        // 取不到则保持为空 → 交回默认口径（与升级前行为一致）
        String invTaxMethod = null;
        for (SaleDeliveryItem di : deliveryItems) {
            Integer t = di.getTaxType();
            if (t != null) { invTaxMethod = (t == 1) ? "含税价" : (t == 3 ? "不计税" : "不含税价"); break; }
        }
        invoice.setTaxCalcMethod(invTaxMethod);
        invoice.setSourceBillType("销售出库单");
        invoice.setSourceBillNo(delivery.getDeliveryNo());
        invoice.setDeliveryNo(delivery.getDeliveryNo());
        invoice.setRemark("由销售出库单 " + delivery.getDeliveryNo() + " 下推生成");
        invoice.setStatus("草稿");
        // 下推统一口径（2026-11-28）：下推只生成草稿，审核动作由用户单独完成
        invoice.setApprovalStatus("草稿");

        List<SaleInvoiceItem> invoiceItems = new ArrayList<>();
        int lineNo = 1;
        for (SaleDeliveryItem di : deliveryItems) {
            SaleInvoiceItem ii = new SaleInvoiceItem();
            ii.setLineNo(lineNo++);
            ii.setMaterialId(di.getMaterialId());
            ii.setMaterialCode(di.getMaterialCode());
            ii.setMaterialName(di.getMaterialName());
            ii.setSpecification(di.getSpecification());
            ii.setUnitId(di.getUnitId());
            ii.setQuantity(di.getQuantity());
            ii.setUnitPrice(di.getUnitPrice() != null ? di.getUnitPrice() : BigDecimal.ZERO);
            ii.setDiscountRate(di.getDiscountRate() != null ? di.getDiscountRate() : BigDecimal.ZERO);
            ii.setDiscountAmount(di.getDiscountAmount() != null ? di.getDiscountAmount() : BigDecimal.ZERO);
            ii.setTaxRate(di.getTaxRate() != null ? di.getTaxRate() : BigDecimal.ZERO);
            ii.setAmount(di.getAmount() != null ? di.getAmount() : BigDecimal.ZERO);
            ii.setTaxAmount(di.getTaxAmount() != null ? di.getTaxAmount() : BigDecimal.ZERO);
            ii.setNetAmount(di.getNetAmount() != null ? di.getNetAmount() : BigDecimal.ZERO);
            ii.setSourceBillType("销售出库单");
            ii.setSourceBillNo(delivery.getDeliveryNo());
            ii.setSourceLineNo(di.getLineNo());
            ii.setSourceId(delivery.getDeliveryId());
            ii.setSourceItemId(di.getDeliveryItemId());
            ii.setSourceType("销售出库单");
            ii.setRemark(di.getRemark());
            invoiceItems.add(ii);
        }

        saleInvoiceService.saveInvoiceWithItems(invoice, invoiceItems);
        // 下推统一口径（2026-11-28）：只生成草稿，不再按免审批开关自动审核生效
        return invoice;
    }
}
