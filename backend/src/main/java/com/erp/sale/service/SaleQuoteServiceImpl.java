package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.basedata.entity.Currency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.PaymentTermService;
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
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleQuote;
import com.erp.sale.entity.SaleQuoteItem;
import com.erp.sale.mapper.SaleQuoteItemMapper;
import com.erp.sale.mapper.SaleQuoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

public class SaleQuoteServiceImpl extends AbstractBillService<SaleQuoteMapper, SaleQuote> implements SaleQuoteService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 全面推广：销售报价）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值的列名优先按 SaleQuote 实体属性名解析（框架元数据自动转列名），
        // 仅「按名称筛但列是外键」处直接写数据库列名。字段解释见 BillHeaderFilterSupport。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("quoteNo", new BillHeaderFilterSupport.Spec("quoteNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerShortName", new BillHeaderFilterSupport.Spec("customerShortName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryAddress", new BillHeaderFilterSupport.Spec("deliveryAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("opportunityNo", new BillHeaderFilterSupport.Spec("opportunityNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("saler_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currency_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("priceLevelName", new BillHeaderFilterSupport.Spec("price_level_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("settleModeName", new BillHeaderFilterSupport.Spec("settle_mode_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("quoteDate", new BillHeaderFilterSupport.Spec("quoteDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("validFrom", new BillHeaderFilterSupport.Spec("validFrom", BillHeaderFilterSupport.Kind.DATE));
        f.put("validTo", new BillHeaderFilterSupport.Spec("validTo", BillHeaderFilterSupport.Kind.DATE));
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("deliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidRate", new BillHeaderFilterSupport.Spec("prepaidRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidAmount", new BillHeaderFilterSupport.Spec("prepaidAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("saleQuote", SaleQuote.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "attachment_urls",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "currency_id",
            "customer_id",
            "delivery_date",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "line_no",
            "net_amount",
            "opportunity_id",
            "opportunity_no",
            "org_code",
            "payment_term_id",
            "price_level_id",
            "quote_date",
            "quote_id",
            "quote_no",
            "remark",
            "saler_id",
            "source_bill_no",
            "status",
            "tax_amount",
            "total_amount",
            "updated_at",
            "updated_by",
            "valid_from",
            "valid_to",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SaleQuoteItemMapper itemMapper;
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final PaymentTermService paymentTermService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SaleOrderService saleOrderService;

    /** 下游引用校验（2026-11-09 统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    // 审批开关、审批引擎、自定义字段、整单删除级联均由统一状态机基类 protected 注入，本类不再重复注入

    private final SysParamValidator sysParamValidator;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售报价单"; }
    @Override protected String getApprovalBillType() { return "SALE_QUOTE"; }
    @Override protected String getBillNo(SaleQuote b) { return b.getQuoteNo(); }
    @Override protected Object getId(SaleQuote b) { return b.getQuoteId(); }
    @Override protected String getStatus(SaleQuote b) { return b.getStatus(); }
    @Override protected void setStatus(SaleQuote b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleQuote b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleQuote b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleQuote b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleQuote b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleQuote b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleQuote b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleQuote b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleQuote b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleQuote b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleQuote b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleQuote b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleQuote b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleQuote b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SaleQuote bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getQuoteNo());
    }

    @Override
    @Transactional
    public void saveQuoteWithItems(SaleQuote quote, List<SaleQuoteItem> items) {
        boolean isNew = quote.getQuoteId() == null;
        if (!isNew) {
            SaleQuote existing = getById(quote.getQuoteId());
            if (existing != null) {
                BillStatusUtils.assertDirectModifiable("销售报价单", existing.getQuoteNo(), existing.getStatus(), existing.getCloseStatus());
            }
        }

        if (quote.getQuoteNo() == null || quote.getQuoteNo().isBlank()) {
            quote.setQuoteNo(generateQuoteNo());
        }

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (quote.getStatus() == null || quote.getStatus().isBlank()) quote.setStatus("草稿");
        if (quote.getApprovalStatus() == null || quote.getApprovalStatus().isBlank()
                || ("草稿".equals(quote.getStatus()) && "待审核".equals(quote.getApprovalStatus()))) {
            quote.setApprovalStatus("草稿");
        }
        if (quote.getExchangeRate() == null) quote.setExchangeRate(BigDecimal.ONE);
        if (quote.getDiscountRate() == null) quote.setDiscountRate(BigDecimal.ZERO);
        if (quote.getDiscountAmount() == null) quote.setDiscountAmount(BigDecimal.ZERO);
        if (quote.getTotalAmount() == null) quote.setTotalAmount(BigDecimal.ZERO);
        if (quote.getTaxAmount() == null) quote.setTaxAmount(BigDecimal.ZERO);
        if (quote.getNetAmount() == null) quote.setNetAmount(BigDecimal.ZERO);
        if (quote.getQuoteDate() == null) quote.setQuoteDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SaleQuoteItem> validItems = new ArrayList<>();
            for (SaleQuoteItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        recalculateQuote(quote, items);

        if (isNew) {
            save(quote);
        } else {
            updateById(quote);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SaleQuoteItem>().eq("quote_id", quote.getQuoteId()));
            int lineNo = 1;
            int headerTaxType = resolveHeaderTaxType(quote);
            for (SaleQuoteItem item : items) {
                item.setQuoteItemId(null);
                item.setQuoteId(quote.getQuoteId());
                item.setLineNo(lineNo);
                if (item.getTaxRate() == null) item.setTaxRate(new BigDecimal("13"));
                if (item.getIsFree() == null) item.setIsFree(0);
                calculateItemAmount(item, headerTaxType);
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("销售报价单保存成功: {} 状态: {} 合计: {}", quote.getQuoteNo(), quote.getStatus(), quote.getTotalAmount());
    }

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /** 由表头「计税方式」解析明细计税类型（含税价/价内税→1、不计税→3、其它→2；总开关关闭→不计税） */
    private int resolveHeaderTaxType(SaleQuote quote) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = quote.getTaxCalcMethod();
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private void calculateItemAmount(SaleQuoteItem item, int taxType) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
        BigDecimal discountRate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;

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

        // 折扣额 = 原金额 × 折扣率 / 100
        BigDecimal discountAmount = originalAmount.multiply(discountRate)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setDiscountAmount(discountAmount);

        // 折后金额 = 原金额 - 折扣额
        BigDecimal amount = originalAmount.subtract(discountAmount).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);

        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, discountRate, inclusive, noTax);
        item.setTaxAmount(taxAmount);

        // 价税合计 = 折后金额 + 税额
        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void recalculateQuote(SaleQuote quote, List<SaleQuoteItem> items) {
        // 表头「计税方式」归一：总开关关闭→不计税；为空→取设置中心默认
        if (!sysParamValidator.isTaxEnabled()) {
            quote.setTaxCalcMethod("不计税");
        } else if (quote.getTaxCalcMethod() == null || quote.getTaxCalcMethod().isBlank()) {
            quote.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(quote);
        BigDecimal totalAmount = BigDecimal.ZERO;      // 折前合计
        BigDecimal totalDiscount = BigDecimal.ZERO;    // 折扣额合计
        BigDecimal totalTax = BigDecimal.ZERO;         // 税额合计

        if (items != null) {
            for (SaleQuoteItem item : items) {
                calculateItemAmount(item, headerTaxType);
                // totalAmount 累加的是折前金额（数量×单价）
                BigDecimal originalAmount = item.getAmount().add(item.getDiscountAmount());
                totalAmount = totalAmount.add(originalAmount);
                totalDiscount = totalDiscount.add(item.getDiscountAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        quote.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        quote.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        quote.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        // 价税合计 = 折前金额 - 折扣额 + 税额
        BigDecimal netAmount = quote.getTotalAmount()
                .subtract(quote.getDiscountAmount())
                .add(quote.getTaxAmount());
        quote.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<SaleQuote> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleQuote> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleQuote.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入报价单号/客户名称搜索」适配（说明书 2.2）：报价单号 OR 关联客户主键
        // （sale_quote 无客户名称列 → 客户名称先解析成 customer_id，作为同一 OR 分组的一支）
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("quote_no"),
                "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 quote_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "quote_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_quote", customSearch, CustomFieldSearchHelper.idColumnOf("sale_quote"));
        Page<SaleQuote> page = new Page<>(current, size);
        Page<SaleQuote> result = page(page, wrapper);
        for (SaleQuote q : result.getRecords()) {
            fillDisplayFields(q);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在标准过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<SaleQuote> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SaleQuote> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleQuote.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入报价单号/客户名称搜索」适配（说明书 2.2）：报价单号 OR 关联客户主键
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("quote_no"),
                "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 quote_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "quote_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 表头字段过滤（2026-10-14 筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "saleQuote", headerFilters);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_quote", customSearch, CustomFieldSearchHelper.idColumnOf("sale_quote"));
        Page<SaleQuote> page = new Page<>(current, size);
        Page<SaleQuote> result = page(page, wrapper);
        for (SaleQuote q : result.getRecords()) {
            fillDisplayFields(q);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleQuote getDetail(Long id) {
        SaleQuote quote = getById(id);
        if (quote == null) throw new BusinessException("报价单不存在");
        List<SaleQuoteItem> items = itemMapper.selectList(
                new QueryWrapper<SaleQuoteItem>().eq("quote_id", id).orderByAsc("line_no"));
        fillDisplayFields(quote);
        if (items != null) {
            for (SaleQuoteItem item : items) {
                fillItemDisplayFields(item);
            }
        }
        quote.setItems(items);
        return quote;
    }

    private void fillDisplayFields(SaleQuote q) {
        if (q.getCustomerId() != null) {
            Customer c = customerService.getById(q.getCustomerId());
            if (c != null) q.setCustomerName(c.getCustomerName());
        }
        if (q.getSalerId() != null) {
            Employee e = employeeService.getById(q.getSalerId());
            if (e != null) q.setSalerName(e.getEmployeeName());
        }
        if (q.getCurrencyId() != null) {
            Currency cur = currencyService.getById(q.getCurrencyId());
            if (cur != null) q.setCurrencyCode(cur.getCurrencyCode());
        }
        if (q.getPaymentTermId() != null) {
            PaymentTerm pt = paymentTermService.getById(q.getPaymentTermId());
            if (pt != null) q.setPaymentTermName(pt.getPaymentTermName());
        }
    }

    private void fillItemDisplayFields(SaleQuoteItem item) {
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
        SaleQuote quote = getById(id);
        if (quote == null) throw new BusinessException("销售报价单不存在");
        // 状态校验 / 严格校验 / 审批开关 / 审批任务创建 全部由统一状态机执行
        super.submitForApproval(quote, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前严格校验（原 submitForApproval 内的校验整体搬入，提交才触发，保存草稿不校验） */
    @Override
    protected void validateStrictSubmit(SaleQuote quote) {
        List<SaleQuoteItem> items = itemMapper.selectList(
            new QueryWrapper<SaleQuoteItem>().eq("quote_id", quote.getQuoteId()).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("销售报价单没有明细，请至少添加一行物料后再提交");
        }

        if (quote.getCustomerId() == null) {
            throw new BusinessException("客户为空，请选择客户后再提交");
        }
        Customer customer = customerService.getById(quote.getCustomerId());
        if (customer == null) {
            throw new BusinessException("关联客户不存在(customerId=" + quote.getCustomerId() + ")，请重新选择客户");
        }
        if (quote.getSalerId() != null) {
            Employee emp = employeeService.getById(quote.getSalerId());
            if (emp == null) {
                throw new BusinessException("关联销售员不存在(employeeId=" + quote.getSalerId() + ")，请重新选择销售员");
            }
        }
        if (quote.getCurrencyId() == null) {
            throw new BusinessException("币种为空，请选择币种后再提交");
        }
        Currency cur = currencyService.getById(quote.getCurrencyId());
        if (cur == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + quote.getCurrencyId() + ")，请重新选择币种");
        }

        for (int i = 0; i < items.size(); i++) {
            SaleQuoteItem item = items.get(i);
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
        }
    }

    /**
     * 统一状态机审核入口（含审批引擎防护）。
     * 覆盖 Object 签名：审批引擎在基类内部按 Object 签名回调，只有覆盖它才能保证防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SALE_QUOTE", (Long) id) != null) {
            throw new BusinessException("销售报价单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Override
    @Transactional
    public void close(Long id) {
        SaleQuote quote = getById(id);
        if (quote == null) throw new BusinessException("报价单不存在");
        if (!"已生效".equals(quote.getStatus())) {
            throw new BusinessException("只有已生效状态可关闭，当前状态: " + quote.getStatus());
        }
        quote.setStatus("已关闭");
        quote.setBusStatus(com.erp.common.bill.BusStatusEnum.CLOSED.getLabel());  // 双写：迁移期保持 status 不变，另写业务执行态
        updateById(quote);
        log.info("销售报价单关闭: {}", quote.getQuoteNo());
    }

    /** 结案: 已生效 → close_status=1（统一状态机执行，含已结案重复校验） */
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
    public void cancel(Long id, String reason) {
        SaleQuote quote = getById(id);
        if (quote == null) throw new BusinessException("报价单不存在");
        if (!"草稿".equals(quote.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + quote.getStatus());
        }
        quote.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            quote.setRemark((quote.getRemark() != null ? quote.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(quote);
        log.info("销售报价单取消: {} 原因: {}", quote.getQuoteNo(), reason);
    }

    @Override
    @Transactional
    public SaleOrder pushToOrder(Long id, String operator) {
        // 获取报价单及其明细
        SaleQuote quote = getDetail(id);
        if (quote == null) {
            throw new com.erp.config.BusinessException("报价单不存在");
        }
        if (!"已生效".equals(quote.getApprovalStatus())) {
            throw new com.erp.config.BusinessException("只有已生效的报价单才能下推");
        }
        List<SaleQuoteItem> quoteItems = quote.getItems();
        if (quoteItems == null || quoteItems.isEmpty()) {
            throw new com.erp.config.BusinessException("报价单无明细，无法下推");
        }

        // 构建销售订单
        SaleOrder order = new SaleOrder();
        order.setOrderNo(saleOrderService.generateOrderNo());
        order.setOrderDate(LocalDate.now());
        order.setCustomerId(quote.getCustomerId());
        order.setContactPerson(quote.getContactPerson());
        order.setContactPhone(quote.getContactPhone());
        order.setSalerId(quote.getSalerId());
        order.setPriceLevelId(quote.getPriceLevelId());
        order.setCurrencyId(quote.getCurrencyId());
        order.setExchangeRate(quote.getExchangeRate());
        order.setTotalAmount(quote.getTotalAmount());
        order.setTaxAmount(quote.getTaxAmount());
        order.setDiscountRate(quote.getDiscountRate());
        order.setDiscountAmount(quote.getDiscountAmount());
        order.setNetAmount(quote.getNetAmount());
        // 计税方式随单下推（2026-10-09）：带过报价单口径，避免下游按默认口径重算导致金额/税额不一致
        order.setTaxCalcMethod(quote.getTaxCalcMethod());
        order.setDeliveryDate(quote.getDeliveryDate());
        order.setPaymentTermId(quote.getPaymentTermId());
        order.setSourceBillType("销售报价单");
        order.setSourceBillNo(quote.getQuoteNo());
        order.setQuoteNo(quote.getQuoteNo());
        order.setRemark("由报价单 " + quote.getQuoteNo() + " 下推生成");
        order.setStatus("草稿");
        order.setApprovalStatus("待审核");
        String username = operator;
        if (username != null) order.setCreatedBy(username);

        // 构建销售订单明细
        List<SaleOrderItem> orderItems = new ArrayList<>();
        int lineNo = 1;
        for (SaleQuoteItem qi : quoteItems) {
            SaleOrderItem oi = new SaleOrderItem();
            oi.setLineNo(lineNo++);
            oi.setMaterialId(qi.getMaterialId());
            oi.setMaterialCode(qi.getMaterialCode());
            oi.setMaterialName(qi.getMaterialName());
            oi.setSpecification(qi.getSpecification());
            oi.setUnitId(qi.getUnitId());
            oi.setQuantity(qi.getQuantity());
            oi.setDeliveredQuantity(java.math.BigDecimal.ZERO);
            oi.setUnitPrice(qi.getUnitPrice());
            oi.setDiscountRate(qi.getDiscountRate());
            oi.setDiscountAmount(qi.getDiscountAmount());
            oi.setTaxRate(qi.getTaxRate());
            oi.setAmount(qi.getAmount());
            oi.setTaxAmount(qi.getTaxAmount());
            oi.setNetAmount(qi.getNetAmount());
            oi.setDeliveryDate(qi.getDeliveryDate());
            oi.setDeliveryDays(qi.getDeliveryDays());
            oi.setSourceBillType("销售报价单");
            oi.setSourceBillNo(quote.getQuoteNo());
            oi.setSourceLineNo(qi.getLineNo());
            oi.setSourceId(quote.getQuoteId());
            oi.setSourceItemId(qi.getQuoteItemId());
            oi.setSourceType("销售报价单");
            oi.setDetailCloseStatus(0);
            oi.setTurnedOutputQuantity(java.math.BigDecimal.ZERO);
            oi.setTurnedReturnQuantity(java.math.BigDecimal.ZERO);
            oi.setTurnedInvoiceQuantity(java.math.BigDecimal.ZERO);
            oi.setRemark(qi.getRemark());
            orderItems.add(oi);
        }

        saleOrderService.saveOrderWithItems(order, orderItems);
        // 开关判断：订单免审批时，下推生成后自动审核生效
        if (!approvalGateHelper.needApproval("SALE_ORDER")) {
            saleOrderService.approve(order.getOrderId(), username != null ? username : "系统(免审批)");
        }
        return order;
    }

    @Override
    public String generateQuoteNo() {
        String docType = "SALE_QUOTE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "QUT-" + dateStr + "-";
            QueryWrapper<SaleQuote> wrapper = new QueryWrapper<>();
            wrapper.likeRight("quote_no", prefix).orderByDesc("quote_no").last("LIMIT 1");
            SaleQuote last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getQuoteNo() != null) {
                try {
                    seq = Integer.parseInt(last.getQuoteNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
}

    /** 重置草稿: 已生效 → 草稿（状态/下游校验由基类执行，这里只补清审核人/时间） */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<SaleQuote> uw = new UpdateWrapper<>();
        uw.eq("quote_id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
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
}
