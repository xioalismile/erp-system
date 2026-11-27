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
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleInvoiceItem;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.entity.SaleReturnItem;
import com.erp.sale.mapper.SaleInvoiceMapper;
import com.erp.sale.mapper.SaleReturnItemMapper;
import com.erp.sale.mapper.SaleReturnMapper;
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

public class SaleReturnServiceImpl extends AbstractBillService<SaleReturnMapper, SaleReturn> implements SaleReturnService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 全面推广：销售退货）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值的列名优先按 SaleReturn 实体属性名解析（框架元数据自动转列名），
        // 仅「按名称筛但列是外键」处直接写数据库列名。字段解释见 BillHeaderFilterSupport。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("returnNo", new BillHeaderFilterSupport.Spec("returnNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryNo", new BillHeaderFilterSupport.Spec("deliveryNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceDeliveryNo", new BillHeaderFilterSupport.Spec("sourceDeliveryNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceNo", new BillHeaderFilterSupport.Spec("invoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("originalInvoiceNo", new BillHeaderFilterSupport.Spec("originalInvoiceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("returnReason", new BillHeaderFilterSupport.Spec("returnReason", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("deliveryAddress", new BillHeaderFilterSupport.Spec("deliveryAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("saler_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currency_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("priceLevelName", new BillHeaderFilterSupport.Spec("price_level_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("returnDate", new BillHeaderFilterSupport.Spec("returnDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidRate", new BillHeaderFilterSupport.Spec("prepaidRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("prepaidAmount", new BillHeaderFilterSupport.Spec("prepaidAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("saleReturn", SaleReturn.class, f);
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
            "delivery_no",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "invoice_no",
            "is_red_invoice",
            "line_no",
            "net_amount",
            "org_code",
            "original_invoice_id",
            "original_invoice_no",
            "remark",
            "return_date",
            "return_id",
            "return_no",
            "return_reason",
            "saler_id",
            "source_bill_no",
            "source_bill_type",
            "source_delivery_no",
            "status",
            "tax_amount",
            "total_amount",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final SaleReturnItemMapper itemMapper;
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final WarehouseService warehouseService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SaleOrderService saleOrderService;
    private final SaleInvoiceMapper saleInvoiceMapper;
    /** 出库明细 Mapper（2026-10-14）：从出库单退货时，经出库明细回溯源订单明细再回写订单已退货数量 */
    private final com.erp.sale.mapper.SaleDeliveryItemMapper saleDeliveryItemMapper;
    private final SysParamValidator sysParamValidator;
    private final SaleOverQtyGuardService saleOverQtyGuardService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    /** 下游引用校验（2026-11-09 统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    /** 替换货（2026-11-17）：退货审核时冲抵换货单待退回 / 记录退回替换货 */
    @Lazy
    @Autowired
    private SaleExchangeService saleExchangeService;

    // 审批开关、审批引擎、自定义字段、整单删除级联均由统一状态机基类 protected 注入，本类不再重复注入

    @Autowired
    @Lazy
    private SaleInvoiceService saleInvoiceService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售退货单"; }
    @Override protected String getApprovalBillType() { return "SALE_RETURN"; }
    @Override protected String getBillNo(SaleReturn b) { return b.getReturnNo(); }
    @Override protected Object getId(SaleReturn b) { return b.getReturnId(); }
    @Override protected String getStatus(SaleReturn b) { return b.getStatus(); }
    @Override protected void setStatus(SaleReturn b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleReturn b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleReturn b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleReturn b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleReturn b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleReturn b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleReturn b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleReturn b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleReturn b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleReturn b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleReturn b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleReturn b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleReturn b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleReturn b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SaleReturn bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReturnNo());
    }

    @Override
    @Transactional
    public void saveReturnWithItems(SaleReturn saleReturn, List<SaleReturnItem> items) {
        boolean isNew = saleReturn.getReturnId() == null;
        if (!isNew) {
            SaleReturn existing = getById(saleReturn.getReturnId());
            if (existing != null) {
                BillStatusUtils.assertDirectModifiable("销售退货单", existing.getReturnNo(), existing.getStatus(), existing.getCloseStatus());
            }
        }

        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号）
        if (isNew && (saleReturn.getReturnNo() == null || saleReturn.getReturnNo().isBlank())) {
            saleReturn.setReturnNo(generateReturnNo());
        }

        // === 设置中心：单据号查重（sys.bill.no.duplicate.check）===
        QueryWrapper<SaleReturn> dupQw = new QueryWrapper<SaleReturn>().eq("return_no", saleReturn.getReturnNo());
        if (!isNew) {
            dupQw.ne("return_id", saleReturn.getReturnId());
        }
        sysParamValidator.validateBillNoDuplicate(saleReturn.getReturnNo(), "销售退货单", count(dupQw) > 0);

        // === 设置中心：锁单据日期校验（sys.lock.bill.date）===
        if (!isNew) {
            SaleReturn existingForDate = getById(saleReturn.getReturnId());
            if (existingForDate != null) {
                sysParamValidator.validateLockBillDate("销售退货单", existingForDate.getReturnDate(), saleReturn.getReturnDate(), existingForDate.getStatus());
            }
        }

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (saleReturn.getStatus() == null || saleReturn.getStatus().isBlank()) saleReturn.setStatus("草稿");
        if (saleReturn.getApprovalStatus() == null || saleReturn.getApprovalStatus().isBlank()
                || ("草稿".equals(saleReturn.getStatus()) && "待审核".equals(saleReturn.getApprovalStatus()))) {
            saleReturn.setApprovalStatus("草稿");
        }
        if (saleReturn.getExchangeRate() == null) saleReturn.setExchangeRate(BigDecimal.ONE);
        if (saleReturn.getDiscountRate() == null) saleReturn.setDiscountRate(BigDecimal.ZERO);
        if (saleReturn.getDiscountAmount() == null) saleReturn.setDiscountAmount(BigDecimal.ZERO);
        if (saleReturn.getTotalAmount() == null) saleReturn.setTotalAmount(BigDecimal.ZERO);
        if (saleReturn.getTaxAmount() == null) saleReturn.setTaxAmount(BigDecimal.ZERO);
        if (saleReturn.getNetAmount() == null) saleReturn.setNetAmount(BigDecimal.ZERO);
        if (saleReturn.getReturnDate() == null) saleReturn.setReturnDate(LocalDate.now());
        if (saleReturn.getSourceBillType() == null || saleReturn.getSourceBillType().isBlank()) {
            saleReturn.setSourceBillType("销售退货");
        }

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SaleReturnItem> validItems = new ArrayList<>();
            for (SaleReturnItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        recalculateReturn(saleReturn, items);

        if (isNew) {
            save(saleReturn);
        } else {
            updateById(saleReturn);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SaleReturnItem>().eq("return_id", saleReturn.getReturnId()));
            int lineNo = 1;
            int headerTaxType = resolveHeaderTaxType(saleReturn);
            for (SaleReturnItem item : items) {
                item.setReturnItemId(null);
                item.setReturnId(saleReturn.getReturnId());
                item.setLineNo(lineNo);
                calculateItemAmount(item, headerTaxType);
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("销售退货单保存成功: {} 状态: {} 合计: {}", saleReturn.getReturnNo(), saleReturn.getStatus(), saleReturn.getTotalAmount());
    }

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /** 由表头「计税方式」解析明细计税类型（含税价/价内税→1、不计税→3、其它→2；总开关关闭→不计税） */
    private int resolveHeaderTaxType(SaleReturn saleReturn) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = saleReturn.getTaxCalcMethod();
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private void calculateItemAmount(SaleReturnItem item, int taxType) {
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

    private void recalculateReturn(SaleReturn saleReturn, List<SaleReturnItem> items) {
        // 表头「计税方式」归一：总开关关闭→不计税；为空→取设置中心默认
        if (!sysParamValidator.isTaxEnabled()) {
            saleReturn.setTaxCalcMethod("不计税");
        } else if (saleReturn.getTaxCalcMethod() == null || saleReturn.getTaxCalcMethod().isBlank()) {
            saleReturn.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(saleReturn);
        BigDecimal totalAmount = BigDecimal.ZERO;      // 折前合计
        BigDecimal totalDiscount = BigDecimal.ZERO;    // 折扣额合计
        BigDecimal totalTax = BigDecimal.ZERO;         // 税额合计

        if (items != null) {
            for (SaleReturnItem item : items) {
                calculateItemAmount(item, headerTaxType);
                // totalAmount 累加的是折前金额（数量×单价）
                BigDecimal originalAmount = item.getAmount().add(item.getDiscountAmount());
                totalAmount = totalAmount.add(originalAmount);
                totalDiscount = totalDiscount.add(item.getDiscountAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        saleReturn.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        saleReturn.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        saleReturn.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        // 价税合计 = 折前金额 - 折扣额 + 税额
        BigDecimal netAmount = saleReturn.getTotalAmount()
                .subtract(saleReturn.getDiscountAmount())
                .add(saleReturn.getTaxAmount());
        saleReturn.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<SaleReturn> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleReturn> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleReturn.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入退货单号/客户名称搜索」适配（说明书 2.2）：退货单号 OR 关联客户主键
        // （sale_return 无客户名称列 → 客户名称先解析成 customer_id，作为同一 OR 分组的一支）
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("return_no"),
                "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 return_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "return_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_return", customSearch, CustomFieldSearchHelper.idColumnOf("sale_return"));
        Page<SaleReturn> page = new Page<>(current, size);
        Page<SaleReturn> result = page(page, wrapper);
        for (SaleReturn r : result.getRecords()) {
            fillDisplayFields(r);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在标准过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<SaleReturn> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SaleReturn> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleReturn.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入退货单号/客户名称搜索」适配（说明书 2.2）：退货单号 OR 关联客户主键
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword, List.of("return_no"),
                "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 return_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "return_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 表头字段过滤（2026-10-14 筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "saleReturn", headerFilters);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_return", customSearch, CustomFieldSearchHelper.idColumnOf("sale_return"));
        Page<SaleReturn> page = new Page<>(current, size);
        Page<SaleReturn> result = page(page, wrapper);
        for (SaleReturn r : result.getRecords()) {
            fillDisplayFields(r);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleReturn getDetail(Long id) {
        SaleReturn saleReturn = getById(id);
        if (saleReturn == null) throw new BusinessException("退货单不存在");
        List<SaleReturnItem> items = itemMapper.selectList(
                new QueryWrapper<SaleReturnItem>().eq("return_id", id).orderByAsc("line_no"));
        fillDisplayFields(saleReturn);
        if (items != null) {
            for (SaleReturnItem item : items) {
                fillItemDisplayFields(item);
            }
        }
        saleReturn.setItems(items);
        return saleReturn;
    }

    private void fillDisplayFields(SaleReturn r) {
        if (r.getCustomerId() != null) {
            Customer c = customerService.getById(r.getCustomerId());
            if (c != null) r.setCustomerName(c.getCustomerName());
        }
        if (r.getSalerId() != null) {
            Employee e = employeeService.getById(r.getSalerId());
            if (e != null) r.setSalerName(e.getEmployeeName());
        }
        if (r.getCurrencyId() != null) {
            Currency cur = currencyService.getById(r.getCurrencyId());
            if (cur != null) r.setCurrencyCode(cur.getCurrencyCode());
        }
        if (r.getWarehouseId() != null) {
            Warehouse w = warehouseService.getById(r.getWarehouseId());
            if (w != null) r.setWarehouseName(w.getStockName());
        }
    }

    private void fillItemDisplayFields(SaleReturnItem item) {
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
        SaleReturn saleReturn = getById(id);
        if (saleReturn == null) throw new BusinessException("销售退货单不存在");
        // 状态校验 / 严格校验 / 审批开关 / 审批任务创建 全部由统一状态机执行
        super.submitForApproval(saleReturn, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前严格校验（原 submitForApproval 内的校验整体搬入，提交才触发，保存草稿不校验） */
    @Override
    protected void validateStrictSubmit(SaleReturn saleReturn) {
        List<SaleReturnItem> items = itemMapper.selectList(
            new QueryWrapper<SaleReturnItem>().eq("return_id", saleReturn.getReturnId()).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("销售退货单没有明细，请至少添加一行物料后再提交");
        }

        if (saleReturn.getCustomerId() == null) {
            throw new BusinessException("客户为空，请选择客户后再提交");
        }
        Customer customer = customerService.getById(saleReturn.getCustomerId());
        if (customer == null) {
            throw new BusinessException("关联客户不存在(customerId=" + saleReturn.getCustomerId() + ")，请重新选择客户");
        }
        if (saleReturn.getSalerId() != null) {
            Employee emp = employeeService.getById(saleReturn.getSalerId());
            if (emp == null) {
                throw new BusinessException("关联销售员不存在(employeeId=" + saleReturn.getSalerId() + ")，请重新选择销售员");
            }
        }
        if (saleReturn.getCurrencyId() == null) {
            throw new BusinessException("币种为空，请选择币种后再提交");
        }
        Currency cur = currencyService.getById(saleReturn.getCurrencyId());
        if (cur == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + saleReturn.getCurrencyId() + ")，请重新选择币种");
        }
        // 2026-11-07：表头仓库改为统一选择器，不再要求必填；以明细行仓库为准
        // === 提交严格校验（保存宽松：以下校验从保存方法移到提交）===
        // 设置中心：来源单号必填（sale.return.require.source）
        sysParamValidator.validateRequireSource("sale.return.require.source", "销售退货单", saleReturn.getSourceBillNo());

        for (int i = 0; i < items.size(); i++) {
            SaleReturnItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行未选择物料，请选择物料后再提交");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                    + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getStockId() == null) {
                throw new BusinessException("第" + lineNo + "行[" + matInfo.trim() + "]未选择仓库，请选择仓库");
            }
            if (warehouseService.getById(item.getStockId()) == null) {
                throw new BusinessException("第" + lineNo + "行[" + matInfo.trim() + "]所选仓库不存在，请重新选择");
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
            // 设置中心：允许单价为0校验（sale.return.allow.zero.price）
            sysParamValidator.validateAllowZeroPrice("sale.return.allow.zero.price", "销售退货单", item.getUnitPrice(), lineNo);
        }
        // 设置中心：超过来源单数量退货（不控制 / 预警提示 / 不允许）
        saleOverQtyGuardService.validateSaleReturn(saleReturn, items);
    }

    /**
     * 统一状态机审核入口（含审批引擎防护 + 超量退货兜底校验）。
     * 覆盖 Object 签名：审批引擎在基类内部按 Object 签名回调，只有覆盖它才能保证防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        SaleReturn saleReturn = getById((Long) id);
        if (saleReturn == null) throw new BusinessException("退货单不存在");
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        // （由引擎回调 onApprovalApproved 触发的审核不受影响：引擎会先把任务置为已通过再回写单据）
        if (approvalRuntimeService.getRunningTask("SALE_RETURN", (Long) id) != null) {
            throw new BusinessException("销售退货单已提交审批流程，请由指定审批人在审批中心处理");
        }
        // 设置中心：超过来源单数量退货（不控制 / 预警提示 / 不允许）——审核兜底，防绕过提交直接审核
        List<SaleReturnItem> guardItems = itemMapper.selectList(
            new QueryWrapper<SaleReturnItem>().eq("return_id", (Long) id).orderByAsc("line_no"));
        saleOverQtyGuardService.validateSaleReturn(saleReturn, guardItems);
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：回写订单已退货数量 + 自动生成红字发票 */
    @Override
    public void onApproved(SaleReturn saleReturn) {
        Long id = saleReturn.getReturnId();
        List<SaleReturnItem> items = itemMapper.selectList(
            new QueryWrapper<SaleReturnItem>().eq("return_id", id));

        for (SaleReturnItem item : items) {
            if (item.getSourceId() != null && item.getSourceItemId() != null) {
                // 【2026-10-14 修复】按来源分支：从出库单退货时 sourceItemId 是出库明细ID，
                //   必须经出库明细回溯源订单明细再回写（此前直接当订单明细ID查 → "订单明细不存在"，退货冲减从未生效）。
                if ("销售出库单".equals(item.getSourceBillType()) || "销售出库".equals(item.getSourceBillType())) {
                    SaleDeliveryItem di = saleDeliveryItemMapper.selectById(item.getSourceItemId());
                    if (di != null && di.getSourceId() != null && di.getSourceItemId() != null) {
                        saleOrderService.updateTurnedQuantity(
                            di.getSourceId(), di.getSourceItemId(),
                            item.getQuantity(), "return");
                    }
                } else {
                    saleOrderService.updateTurnedQuantity(
                        item.getSourceId(), item.getSourceItemId(),
                        item.getQuantity(), "return");
                }
            }
        }

        // === 审核通过后自动生成红字发票（关联原蓝字发票） ===
        try {
            generateRedInvoiceForReturn(saleReturn, items, saleReturn.getApprovedBy());
        } catch (Exception e) {
            log.error("退货审核自动生成红字发票失败，退货单: {}", saleReturn.getReturnNo(), e);
            throw new BusinessException("自动生成红字发票失败: " + e.getMessage());
        }

        // 【2026-11-17 替换货】冲抵换货单：退原货冲抵待退回、退替换货只记退回替换货
        // 失败不得影响退货本身成功（退货、红冲已完成），仅记录日志供补偿
        try {
            saleExchangeService.offsetReturn(null, items, saleReturn.getApprovedBy());
        } catch (Exception e) {
            log.warn("退货冲抵换货单待退回失败，退货单: {}", saleReturn.getReturnNo(), e);
        }

        log.info("销售退货单审核: {} 审核人: {}", saleReturn.getReturnNo(), saleReturn.getApprovedBy());
    }

    /**
     * 退货审核后自动生成红字发票：
     * 1. 查找原蓝字发票（按 original_invoice_id 或 source 推导）
     * 2. 创建红字 SaleInvoice，isRedInvoice=1
     * 3. 复制退货明细为发票明细（数量为正，金额由 calculateItemAmount 计算）
     * 4. 关联原蓝字发票 original_invoice_id/original_invoice_no
     * 5. 自动审核红字发票（触发应收冲减）
     */
    private void generateRedInvoiceForReturn(SaleReturn saleReturn, List<SaleReturnItem> returnItems, String approver) {
        if (returnItems == null || returnItems.isEmpty()) {
            return;
        }

        // 1. 查找原蓝字发票
        SaleInvoice originalInvoice = findOriginalBlueInvoice(saleReturn);

        // 2. 构建红字发票主体
        SaleInvoice redInvoice = new SaleInvoice();
        redInvoice.setInvoiceNo(saleInvoiceService.generateInvoiceNo());
        redInvoice.setInvoiceDate(LocalDate.now());
        redInvoice.setInvoiceType("红字销售发票");
        redInvoice.setCustomerId(saleReturn.getCustomerId());
        redInvoice.setSalerId(saleReturn.getSalerId());
        redInvoice.setCurrencyId(saleReturn.getCurrencyId());
        redInvoice.setExchangeRate(saleReturn.getExchangeRate() != null ? saleReturn.getExchangeRate() : BigDecimal.ONE);
        redInvoice.setDiscountRate(saleReturn.getDiscountRate() != null ? saleReturn.getDiscountRate() : BigDecimal.ZERO);
        redInvoice.setDiscountAmount(BigDecimal.ZERO);
        redInvoice.setTotalAmount(BigDecimal.ZERO);
        redInvoice.setTaxAmount(BigDecimal.ZERO);
        redInvoice.setNetAmount(BigDecimal.ZERO);
        redInvoice.setSourceBillType("销售退货单");
        redInvoice.setSourceBillNo(saleReturn.getReturnNo());
        redInvoice.setRemark("退货审核自动生成，关联退货单: " + saleReturn.getReturnNo());
        redInvoice.setIsRedInvoice((short) 1);
        // 计税方式随单下推（2026-10-09）：红字发票表头口径必须随退货单，否则按默认口径重算 → 冲减应收金额错误
        redInvoice.setTaxCalcMethod(saleReturn.getTaxCalcMethod());

        if (originalInvoice != null) {
            redInvoice.setOriginalInvoiceId(originalInvoice.getInvoiceId());
            redInvoice.setOriginalInvoiceNo(originalInvoice.getInvoiceNo());
            // 同步退货单的原发票关联
            saleReturn.setOriginalInvoiceId(originalInvoice.getInvoiceId());
            saleReturn.setOriginalInvoiceNo(originalInvoice.getInvoiceNo());
            updateById(saleReturn);
        }

        // 3. 构建发票明细（从退货明细复制）
        List<SaleInvoiceItem> invoiceItems = new ArrayList<>();
        int lineNo = 1;
        for (SaleReturnItem ri : returnItems) {
            SaleInvoiceItem ii = new SaleInvoiceItem();
            ii.setLineNo(lineNo);
            ii.setMaterialId(ri.getMaterialId());
            ii.setMaterialCode(ri.getMaterialCode());
            ii.setMaterialName(ri.getMaterialName());
            ii.setSpecification(ri.getSpecification());
            ii.setUnitId(ri.getUnitId());
            ii.setQuantity(ri.getQuantity());
            ii.setUnitPrice(ri.getUnitPrice());
            ii.setDiscountRate(ri.getDiscountRate() != null ? ri.getDiscountRate() : BigDecimal.ZERO);
            ii.setTaxRate(ri.getTaxRate() != null ? ri.getTaxRate() : BigDecimal.ZERO);
            ii.setTaxType(ri.getTaxType() != null ? ri.getTaxType() : 2);
            // 源单指向退货单
            ii.setSourceBillType("销售退货单");
            ii.setSourceBillNo(saleReturn.getReturnNo());
            ii.setSourceId(saleReturn.getReturnId());
            ii.setSourceItemId(ri.getReturnItemId());
            ii.setSourceLineNo(ri.getLineNo());
            ii.setRemark("退货红字发票自动生成");
            invoiceItems.add(ii);
            lineNo++;
        }

        // 4. 保存红字发票（跳过互斥校验：isRedInvoice=1）
        saleInvoiceService.saveInvoiceWithItems(redInvoice, invoiceItems);

        // 5. 提交审核：免审批（销售发票单据级开关注销）时 submitForApproval 内部已自动审核生效并触发应收冲减；
        //    启用审批时红字发票进入「待审核」由审批人在审批中心处理。
        //    【2026-09-16 修复】原实现紧接一次 approve()，在上述两种情形下都会失败
        //    （免审批已被自动审核 → 报"只有草稿或待审核状态可审核，当前审核状态: 已生效"；
        //     启用审批 → 已有在途任务，属绕过引擎直审），导致销售退货单审核整单回滚。
        saleInvoiceService.submitForApproval(redInvoice.getInvoiceId());

        log.info("退货单 {} 自动生成红字发票 {} 关联原发票: {}",
            saleReturn.getReturnNo(), redInvoice.getInvoiceNo(),
            originalInvoice != null ? originalInvoice.getInvoiceNo() : "无");
    }

    /**
     * 查找原蓝字发票：
     * 1. 优先用 saleReturn.originalInvoiceId 直接查
     * 2. 其次用 saleReturn.originalInvoiceNo 按发票号查
     * 3. 否则按退货单的源单据（销售订单/销售出库单）反查发票
     */
    private SaleInvoice findOriginalBlueInvoice(SaleReturn saleReturn) {
        // 1. 直接按 ID
        if (saleReturn.getOriginalInvoiceId() != null) {
            SaleInvoice inv = saleInvoiceMapper.selectById(saleReturn.getOriginalInvoiceId());
            if (inv != null && (inv.getIsRedInvoice() == null || inv.getIsRedInvoice() == 0)) {
                return inv;
            }
        }
        // 2. 按发票号
        if (saleReturn.getOriginalInvoiceNo() != null && !saleReturn.getOriginalInvoiceNo().isBlank()) {
            SaleInvoice inv = saleInvoiceMapper.selectOne(
                new QueryWrapper<SaleInvoice>().eq("invoice_no", saleReturn.getOriginalInvoiceNo()).last("LIMIT 1"));
            if (inv != null && (inv.getIsRedInvoice() == null || inv.getIsRedInvoice() == 0)) {
                return inv;
            }
        }
        // 3. 按退货单源单据反查
        String sourceType = saleReturn.getSourceBillType();
        String sourceNo = saleReturn.getSourceBillNo();
        if (sourceType != null && sourceNo != null && !sourceNo.isBlank()) {
            String invoiceSourceType;
            if (sourceType.contains("订单")) {
                invoiceSourceType = "销售订单";
            } else if (sourceType.contains("出库")) {
                invoiceSourceType = "销售出库单";
            } else if (sourceType.contains("发票")) {
                // 退货源就是发票，直接按 sourceNo 查
                SaleInvoice inv = saleInvoiceMapper.selectOne(
                    new QueryWrapper<SaleInvoice>().eq("invoice_no", sourceNo).last("LIMIT 1"));
                if (inv != null && (inv.getIsRedInvoice() == null || inv.getIsRedInvoice() == 0)) {
                    return inv;
                }
                return null;
            } else {
                return null;
            }
            SaleInvoice inv = saleInvoiceMapper.selectOne(
                new QueryWrapper<SaleInvoice>()
                    .eq("source_bill_type", invoiceSourceType)
                    .eq("source_bill_no", sourceNo)
                    .eq("is_red_invoice", 0)
                    .last("LIMIT 1"));
            return inv;
        }
        return null;
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        SaleReturn saleReturn = getById(id);
        if (saleReturn == null) throw new BusinessException("退货单不存在");
        if (!"草稿".equals(saleReturn.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + saleReturn.getStatus());
        }
        saleReturn.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            saleReturn.setRemark((saleReturn.getRemark() != null ? saleReturn.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(saleReturn);
        log.info("销售退货单取消: {} 原因: {}", saleReturn.getReturnNo(), reason);
    }

    @Override
    public String generateReturnNo() {
        String docType = "SALE_RETURN";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SRT-" + dateStr + "-";
            QueryWrapper<SaleReturn> wrapper = new QueryWrapper<>();
            wrapper.likeRight("return_no", prefix).orderByDesc("return_no").last("LIMIT 1");
            SaleReturn last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReturnNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReturnNo().substring(prefix.length())) + 1;
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
    public void reverseApprove(Long id, String operator) {
        SaleReturn bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        // 状态校验 + 下游校验 + 状态迁移由基类执行；勾稽回冲在 onReverseApproved 钩子
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式置空 approved_by/approved_at
        UpdateWrapper<SaleReturn> uw = new UpdateWrapper<>();
        uw.eq("return_id", id)
                .set("approved_by", null)
                .set("approved_at", null);
        update(uw);
    }

    /** 重置草稿影响：负数回冲订单「已退货数量」（与审核回写逐字镜像，防止净发货被多扣） */
    @Override
    public void onReverseApproved(SaleReturn bill) {
        List<SaleReturnItem> items = itemMapper.selectList(
            new QueryWrapper<SaleReturnItem>().eq("return_id", bill.getReturnId()));
        if (items != null) {
            for (SaleReturnItem item : items) {
                if (item.getSourceId() == null || item.getSourceItemId() == null) continue;
                java.math.BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : java.math.BigDecimal.ZERO;
                // 【2026-10-14】与审核回写同口径：按来源分支，出库来源经出库明细回溯订单明细后负数回冲
                if ("销售出库单".equals(item.getSourceBillType()) || "销售出库".equals(item.getSourceBillType())) {
                    SaleDeliveryItem di = saleDeliveryItemMapper.selectById(item.getSourceItemId());
                    if (di != null && di.getSourceId() != null && di.getSourceItemId() != null) {
                        saleOrderService.updateTurnedQuantity(
                            di.getSourceId(), di.getSourceItemId(),
                            qty.negate(), "return");
                    }
                } else {
                    saleOrderService.updateTurnedQuantity(
                        item.getSourceId(), item.getSourceItemId(),
                        qty.negate(), "return");
                }
            }
        }
        log.info("销售退货单重置草稿勾稽回冲完成: {}", bill.getReturnNo());
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
