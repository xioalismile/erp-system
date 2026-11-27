package com.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.PaymentTermService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.approval.service.CustomerCreditCallback;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.credit.CreditGuardService;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.customfield.service.CustomFieldService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleContractItem;
import com.erp.sale.mapper.SaleContractItemMapper;
import com.erp.sale.mapper.SaleContractMapper;
import com.erp.sale.service.SaleAutoPushService;
import com.erp.sale.service.SaleContractService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.common.util.TaxAmountUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SaleContractServiceImpl extends AbstractBillService<SaleContractMapper, SaleContract>
        implements SaleContractService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 全面推广：销售合同）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值的列名优先按 SaleContract 实体属性名解析（框架元数据自动转列名），
        // 仅「按名称筛但列是外键」处直接写数据库列名。字段解释见 BillHeaderFilterSupport。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("contractNo", new BillHeaderFilterSupport.Spec("contractNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contractType", new BillHeaderFilterSupport.Spec("contractType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerNo", new BillHeaderFilterSupport.Spec("customerNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerShortName", new BillHeaderFilterSupport.Spec("customerShortName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("receiveAddress", new BillHeaderFilterSupport.Spec("receiveAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("projectNo", new BillHeaderFilterSupport.Spec("projectNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("customerOrderNo", new BillHeaderFilterSupport.Spec("customerOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("opportunityNo", new BillHeaderFilterSupport.Spec("opportunityNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("logisticsCompany", new BillHeaderFilterSupport.Spec("logisticsCompany", BillHeaderFilterSupport.Kind.TEXT));
        f.put("logisticsNo", new BillHeaderFilterSupport.Spec("logisticsNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("invoiceType", new BillHeaderFilterSupport.Spec("invoiceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("paymentMethod", new BillHeaderFilterSupport.Spec("paymentMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("transportMethod", new BillHeaderFilterSupport.Spec("transportMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("saler_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("department_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currency_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("priceLevelName", new BillHeaderFilterSupport.Spec("price_level_id", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("contractDate", new BillHeaderFilterSupport.Spec("contractDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("validityStart", new BillHeaderFilterSupport.Spec("validityStart", BillHeaderFilterSupport.Kind.DATE));
        f.put("validityEnd", new BillHeaderFilterSupport.Spec("validityEnd", BillHeaderFilterSupport.Kind.DATE));
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("deliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("depositAmount", new BillHeaderFilterSupport.Spec("depositAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("saleContract", SaleContract.class, f);
    }

    /**
     * 审批流程引擎、自定义字段、审批开关、整单删除级联均来自统一状态机基类（protected 注入），
     * 本类不再重复注入，避免"同一能力两套来源"。
     */

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "attachment_urls",
            "close_status",
            "contact_person",
            "contact_phone",
            "contract_date",
            "contract_id",
            "contract_no",
            "contract_type",
            "created_at",
            "currency_id",
            "customer_id",
            "customer_no",
            "customer_order_no",
            "customer_short_name",
            "delivery_date",
            "department_id",
            "deposit_amount",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "invoice_type",
            "is_invoiced",
            "is_tax",
            "logistics_company",
            "logistics_no",
            "net_amount",
            "opportunity_id",
            "opportunity_no",
            "payment_method",
            "payment_term_id",
            "price_level_id",
            "project_no",
            "receive_address",
            "remark",
            "saler_id",
            "sort_no",
            "source_bill_no",
            "source_bill_type",
            "status",
            "tax_amount",
            "tax_calc_method",
            "total_amount",
            "transport_method",
            "validity_end",
            "validity_start",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final SaleContractItemMapper itemMapper;
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final PaymentTermService paymentTermService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final MaterialCostService materialCostService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    /** 信用额度守卫（@Lazy：守卫→FinanceService→销售服务可能成环，懒注入解除） */
    @Lazy
    @Autowired
    private CreditGuardService creditGuardService;

    /** 自动下推单据（2026-11-08）：合同生效后按设置中心开关自动生成已生效销售订单 */
    @Lazy
    @Autowired
    private SaleAutoPushService saleAutoPushService;

    /** 下游引用校验（2026-11-09）：重置草稿前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    private final SysParamValidator sysParamValidator;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售合同"; }
    @Override protected String getApprovalBillType() { return "SALE_CONTRACT"; }
    @Override protected String getBillNo(SaleContract b) { return b.getContractNo(); }
    @Override protected Object getId(SaleContract b) { return b.getContractId(); }
    @Override protected String getStatus(SaleContract b) { return b.getStatus(); }
    @Override protected void setStatus(SaleContract b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleContract b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleContract b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleContract b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleContract b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleContract b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleContract b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleContract b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleContract b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleContract b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleContract b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleContract b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleContract b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleContract b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SaleContract bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getContractNo());
    }

    /** 审核通过影响：刷新成本 + 自动下推销售订单（统一状态机在审核生效后调用） */
    @Override
    public void onApproved(SaleContract contract) {
        List<SaleContractItem> items = itemMapper.selectList(
                new QueryWrapper<SaleContractItem>().eq("contract_id", contract.getContractId()).orderByAsc("sort_no"));
        if (items != null) {
            for (SaleContractItem item : items) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
        }
        log.info("销售合同审核: {} 审核人: {}", contract.getContractNo(), contract.getApprovedBy());
        saleAutoPushService.autoPushContractToOrder(contract, contract.getApprovedBy());
    }

    /** 审批条件快照：在基类（表头 + 自定义字段）之上补客户付款管控分类 */
    @Override
    protected Map<String, Object> buildBillData(SaleContract bill) {
        Map<String, Object> billData = super.buildBillData(bill);
        if (bill.getCustomerId() != null) {
            Customer customer = customerService.getById(bill.getCustomerId());
            if (customer != null && customer.getPaymentCategory() != null) {
                billData.put("paymentCategory", customer.getPaymentCategory());
                billData.put("payment_category", customer.getPaymentCategory());
            }
        }
        return billData;
    }

    @Override
    @Transactional
    public void saveContractWithItems(SaleContract contract, List<SaleContractItem> items) {
        boolean isNew = contract.getContractId() == null;
        if (!isNew) {
            SaleContract existing = getById(contract.getContractId());
            if (existing != null) {
                BillStatusUtils.assertDirectModifiable("销售合同", existing.getContractNo(), existing.getStatus(), existing.getCloseStatus());
            }
        }

        if (contract.getContractNo() == null || contract.getContractNo().isBlank()) {
            // 设置中心已关闭自动生成时，要求用户手动输入编号，不再兜底自动生成
            if (!sysSerialNoConfigService.isAutoGenerate("SALE_CONTRACT")) {
                throw new BusinessException("销售合同已关闭自动编号，请手动输入合同号");
            }
            contract.setContractNo(generateContractNo());
        }

        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (contract.getStatus() == null || contract.getStatus().isBlank()) contract.setStatus("草稿");
        if (contract.getApprovalStatus() == null || contract.getApprovalStatus().isBlank()
                || ("草稿".equals(contract.getStatus()) && "待审核".equals(contract.getApprovalStatus()))) {
            contract.setApprovalStatus("草稿");
        }
        if (contract.getExchangeRate() == null) contract.setExchangeRate(BigDecimal.ONE);
        if (contract.getDiscountRate() == null) contract.setDiscountRate(BigDecimal.ZERO);
        if (contract.getDiscountAmount() == null) contract.setDiscountAmount(BigDecimal.ZERO);
        if (contract.getTotalAmount() == null) contract.setTotalAmount(BigDecimal.ZERO);
        if (contract.getTaxAmount() == null) contract.setTaxAmount(BigDecimal.ZERO);
        if (contract.getNetAmount() == null) contract.setNetAmount(BigDecimal.ZERO);
        if (contract.getContractDate() == null) contract.setContractDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SaleContractItem> validItems = new ArrayList<>();
            for (SaleContractItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        recalculateContract(contract, items);

        if (isNew) {
            save(contract);
        } else {
            updateById(contract);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SaleContractItem>().eq("contract_id", contract.getContractId()));
            int sortNo = 1;
            int headerTaxType = resolveHeaderTaxType(contract);
            for (SaleContractItem item : items) {
                item.setItemId(null);
                item.setContractId(contract.getContractId());
                item.setSortNo(sortNo);
                calculateItemAmount(item, headerTaxType);
                // === 新增：回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                sortNo += 1;
            }
        }

        log.info("销售合同保存成功: {} 状态: {} 合计: {}", contract.getContractNo(), contract.getStatus(), contract.getTotalAmount());
    }

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /** 由表头「计税方式」解析明细计税类型（含税价/价内税→1、不计税→3、其它→2；总开关关闭→不计税） */
    private int resolveHeaderTaxType(SaleContract contract) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = contract.getTaxCalcMethod();
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private void calculateItemAmount(SaleContractItem item, int taxType) {
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

    private void recalculateContract(SaleContract contract, List<SaleContractItem> items) {
        // 表头「计税方式」归一：总开关关闭→不计税；为空→取设置中心默认
        if (!sysParamValidator.isTaxEnabled()) {
            contract.setTaxCalcMethod("不计税");
        } else if (contract.getTaxCalcMethod() == null || contract.getTaxCalcMethod().isBlank()) {
            contract.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(contract);
        BigDecimal totalAmount = BigDecimal.ZERO;      // 折前合计
        BigDecimal totalDiscount = BigDecimal.ZERO;    // 折扣额合计
        BigDecimal totalTax = BigDecimal.ZERO;         // 税额合计

        if (items != null) {
            for (SaleContractItem item : items) {
                calculateItemAmount(item, headerTaxType);
                // totalAmount 累加的是折前金额（数量×单价）
                BigDecimal originalAmount = item.getAmount().add(item.getDiscountAmount());
                totalAmount = totalAmount.add(originalAmount);
                totalDiscount = totalDiscount.add(item.getDiscountAmount());
                totalTax = totalTax.add(item.getTaxAmount());
            }
        }

        contract.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        contract.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        contract.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        // 价税合计 = 折前金额 - 折扣额 + 税额
        BigDecimal netAmount = contract.getTotalAmount()
                .subtract(contract.getDiscountAmount())
                .add(contract.getTaxAmount());
        contract.setNetAmount(netAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<SaleContract> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleContract> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleContract.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入合同号/客户名称搜索」适配（说明书 2.2）：合同号 + 客户编号 OR 关联客户主键
        // （sale_contract.customer_short_name 全表为空值，客户名称改用 customer_id 解析，纳入同一 OR 分组）
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword,
                List.of("contract_no", "customer_no"), "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 contract_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "contract_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_contract", customSearch, CustomFieldSearchHelper.idColumnOf("sale_contract"));
        Page<SaleContract> page = new Page<>(current, size);
        Page<SaleContract> result = page(page, wrapper);
        for (SaleContract c : result.getRecords()) {
            fillDisplayFields(c);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在标准过滤之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<SaleContract> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SaleContract> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleContract.class);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 关键词按列表页提示「输入合同号/客户名称搜索」适配（说明书 2.2）：合同号 + 客户编号 OR 关联客户主键
        List<Long> kwCustomerIds = customerService.findIdsByNameLike(keyword,
                com.erp.common.query.ListQuerySupport.ID_LOOKUP_LIMIT);
        com.erp.common.query.ListQuerySupport.applyKeywordGroupWithIds(wrapper, keyword,
                List.of("contract_no", "customer_no"), "customer_id", kwCustomerIds);
        // 日期区间（本页真实列 contract_date）；客户（customer_id）；员工=销售员（saler_id）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "contract_date", startDate, endDate);
        com.erp.common.query.ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        // 表头字段过滤（2026-10-14 筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "saleContract", headerFilters);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sale_contract", customSearch, CustomFieldSearchHelper.idColumnOf("sale_contract"));
        Page<SaleContract> page = new Page<>(current, size);
        Page<SaleContract> result = page(page, wrapper);
        for (SaleContract c : result.getRecords()) {
            fillDisplayFields(c);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleContract getDetail(Long id) {
        SaleContract contract = getById(id);
        if (contract == null) throw new BusinessException("合同不存在");
        List<SaleContractItem> items = itemMapper.selectList(
                new QueryWrapper<SaleContractItem>().eq("contract_id", id).orderByAsc("sort_no"));
        fillDisplayFields(contract);
        if (items != null) {
            for (SaleContractItem item : items) {
                fillItemDisplayFields(item);
            }
        }
        contract.setItems(items);
        return contract;
    }

    private void fillDisplayFields(SaleContract c) {
        if (c.getCustomerId() != null) {
            Customer cust = customerService.getById(c.getCustomerId());
            if (cust != null) c.setCustomerName(cust.getCustomerName());
        }
        if (c.getSalerId() != null) {
            Employee e = employeeService.getById(c.getSalerId());
            if (e != null) c.setSalerName(e.getEmployeeName());
        }
        if (c.getCurrencyId() != null) {
            Currency cur = currencyService.getById(c.getCurrencyId());
            if (cur != null) c.setCurrencyCode(cur.getCurrencyCode());
        }
        if (c.getPaymentTermId() != null) {
            PaymentTerm pt = paymentTermService.getById(c.getPaymentTermId());
            if (pt != null) c.setPaymentTermName(pt.getPaymentTermName());
        }
    }

    private void fillItemDisplayFields(SaleContractItem item) {
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
        submitForApproval(id, null);
    }

    @Override
    @Transactional
    public void submitForApproval(Long id, String submitter) {
        SaleContract contract = getById(id);
        if (contract == null) throw new BusinessException("销售合同不存在");
        if (!"草稿".equals(contract.getStatus())) {
            throw new BusinessException("只有草稿状态可提交审核，当前状态: " + contract.getStatus());
        }
        // 统一严格校验（客户/销售员/币种/明细/信用额度）——与基类提交同一入口
        validateStrictSubmit(contract);

        // 客户授信专用路由（月结）：不是逐单审批，而是先走「客户授信」一次性审批。
        // 该分支保留在本类，其余路径一律交回统一状态机。
        boolean needApproval = needApprovalNow();
        if (needApproval && contract.getCustomerId() != null) {
            Customer customer = customerService.getById(contract.getCustomerId());
            boolean monthly = customer != null && "月结".equals(customer.getPaymentCategory());
            boolean creditApproved = monthly
                    && Customer.CREDIT_STATUS_APPROVED.equals(customer.getCreditApprovalStatus());
            if (creditApproved) {
                // 授信已生效：合同不再逐单审批，挂待审核后走统一审核入口生效
                contract.setStatus("待审核");
                contract.setApprovalStatus("待审核");
                updateById(contract);
                approve(contract.getContractId(), "系统(授信免审)");
                log.info("销售合同授信客户免逐单审批直接生效: {} 客户: {}", contract.getContractNo(), customer.getCustomerName());
                return;
            }
            if (monthly) {
                // 月结且授信未生效：首次提交触发「客户授信一次性审批」替代逐单审批
                if (Customer.CREDIT_STATUS_PENDING.equals(customer.getCreditApprovalStatus())) {
                    throw new BusinessException(String.format(
                            "客户[%s]的授信审批进行中，请等待审批通过后再提交新销售合同", customer.getCustomerName()));
                }
                contract.setStatus("待审核");
                contract.setApprovalStatus("待审核");
                updateById(contract);
                customer.setCreditApprovalStatus(Customer.CREDIT_STATUS_PENDING);
                customerService.updateById(customer);
                approvalRuntimeService.startApproval(CustomerCreditCallback.BILL_TYPE,
                        customer.getCustomerId(), customer.getCustomerCode(), submitter,
                        buildCustomerCreditBillData(customer));
                log.info("销售合同提交触发客户授信审批: 合同 {} 客户 {} (月结首单)", contract.getContractNo(), customer.getCustomerName());
                return;
            }
        }

        // 其余情况走统一状态机：免审批 → 提交即生效；开启审批 → 待审核并创建审批任务
        super.submitForApproval(contract, submitter);
    }

    /** 统一严格校验（提交审核前执行）：客户/销售员/币种/明细 + 信用额度 */
    @Override
    protected void validateStrictSubmit(SaleContract contract) {
        List<SaleContractItem> items = itemMapper.selectList(
                new QueryWrapper<SaleContractItem>().eq("contract_id", contract.getContractId()).orderByAsc("sort_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("销售合同没有明细，请至少添加一行物料后再提交");
        }
        if (contract.getCustomerId() == null) {
            throw new BusinessException("客户为空，请选择客户后再提交");
        }
        Customer customer = customerService.getById(contract.getCustomerId());
        if (customer == null) {
            throw new BusinessException("关联客户不存在(customerId=" + contract.getCustomerId() + ")，请重新选择客户");
        }
        if (contract.getSalerId() != null) {
            Employee emp = employeeService.getById(contract.getSalerId());
            if (emp == null) {
                throw new BusinessException("关联销售员不存在(employeeId=" + contract.getSalerId() + ")，请重新选择销售员");
            }
        }
        if (contract.getCurrencyId() == null) {
            throw new BusinessException("币种为空，请选择币种后再提交");
        }
        Currency cur = currencyService.getById(contract.getCurrencyId());
        if (cur == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + contract.getCurrencyId() + ")，请重新选择币种");
        }
        for (int i = 0; i < items.size(); i++) {
            SaleContractItem item = items.get(i);
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
        creditGuardService.validateCreditLimit("销售合同", customer, contract.getNetAmount());
    }

    /**
     * 构建客户授信审批条件路由用的客户数据快照（billData）：供「客户授信(CUSTOMER_CREDIT)」
     * 审批流程的条件模板使用（如按客户等级/信用额度等路由到不同节点）。反射全部标量字段双写 camelCase/snake_case。
     */
    private Map<String, Object> buildCustomerCreditBillData(Customer customer) {
        Map<String, Object> billData = new HashMap<>();
        if (customer == null) return billData;
        for (Class<?> c = customer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (List.class.isAssignableFrom(ft) || Map.class.isAssignableFrom(ft)) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(customer);
                    billData.put(f.getName(), v);
                    billData.put(camelToSnake(f.getName()), v);
                } catch (Exception ignore) {
                    // 反射读取失败的字段跳过，不阻断授信审批提交
                }
            }
        }
        billData.put("billType", CustomerCreditCallback.BILL_TYPE);
        return billData;
    }

    // ============ 统一状态机审核入口（含审批引擎防护） ============

    /**
     * 覆盖基类 Object 签名：审批引擎回调（onApprovalApproved）在基类内部按 Object 签名调用，
     * 只有覆盖 Object 签名才能保证「绕过引擎直审」的防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SALE_CONTRACT", (Long) id) != null) {
            throw new BusinessException("销售合同已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 驳回：走统一状态机（含审批引擎防护 + 状态校验） */
    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        SaleContract contract = getById(id);
        if (contract == null) throw new BusinessException("合同不存在");
        if (!"草稿".equals(contract.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + contract.getStatus());
        }
        contract.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            contract.setRemark((contract.getRemark() != null ? contract.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(contract);
        log.info("销售合同取消: {} 原因: {}", contract.getContractNo(), reason);
    }

    @Override
    public String generateContractNo() {
        // 优先走设置中心自动编号规则（SALE_CONTRACT），未配置或规则异常时退回内置默认规则
        return sysSerialNoConfigService.generateOrDefault("SALE_CONTRACT", this::defaultContractNo);
    }

    @Override
    public String generateContractNoForSystem() {
        // 系统自动建单：设置中心关闭自动编号时仍按编号规则取号
        return sysSerialNoConfigService.generateForSystem("SALE_CONTRACT", this::defaultContractNo);
    }

    private String defaultContractNo() {
        // 文档规范: SC-yyyyMMdd-0001 (有分隔符)
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SC-" + dateStr + "-";
        QueryWrapper<SaleContract> wrapper = new QueryWrapper<>();
        wrapper.likeRight("contract_no", prefix).orderByDesc("contract_no").last("LIMIT 1");
        SaleContract last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getContractNo() != null) {
            try {
                seq = Integer.parseInt(last.getContractNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleContract bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        // 自动下推生成的合同与人工建单走同一套状态机：重置草稿不做额外拦截，
        // 下游引用校验（已生效的下游单据）由基类统一执行。
        // 状态校验 + 下游校验 + 状态迁移统一由基类执行
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<SaleContract> uw = new UpdateWrapper<>();
        uw.eq("contract_id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        // 状态校验 + 下游校验 + 作废前状态记录 + 冲回钩子统一由基类执行
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
        // 草稿校验 + 下游校验 + 整单级联删除统一由基类执行
        super.deleteBill(id);
    }

    /** 结案：统一状态机（已生效 + 未结案校验） */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案：统一状态机 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }
}
