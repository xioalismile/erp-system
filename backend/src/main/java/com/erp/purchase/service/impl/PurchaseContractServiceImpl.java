package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.purchase.entity.PurchaseContract;
import com.erp.purchase.entity.PurchaseContractItem;
import com.erp.purchase.mapper.PurchaseContractItemMapper;
import com.erp.purchase.mapper.PurchaseContractMapper;
import com.erp.purchase.service.PurchaseContractService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;
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

public class PurchaseContractServiceImpl extends AbstractBillService<PurchaseContractMapper, PurchaseContract> implements PurchaseContractService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（供应商/采购员/部门/币种）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        // 注：supplierName/buyerName/currencyCode 在实体里是回显字段（非库列），条件值按外键 ID 精确匹配。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("contractNo", new BillHeaderFilterSupport.Spec("contractNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contractType", new BillHeaderFilterSupport.Spec("contractType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("supplierNo", new BillHeaderFilterSupport.Spec("supplierNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("supplierShortName", new BillHeaderFilterSupport.Spec("supplierShortName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("receiveAddress", new BillHeaderFilterSupport.Spec("receiveAddress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("transportMethod", new BillHeaderFilterSupport.Spec("transportMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("paymentMethod", new BillHeaderFilterSupport.Spec("paymentMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("projectNo", new BillHeaderFilterSupport.Spec("projectNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("buyerName", new BillHeaderFilterSupport.Spec("buyerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("contractDate", new BillHeaderFilterSupport.Spec("contractDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("validityStart", new BillHeaderFilterSupport.Spec("validityStart", BillHeaderFilterSupport.Kind.DATE));
        f.put("validityEnd", new BillHeaderFilterSupport.Spec("validityEnd", BillHeaderFilterSupport.Kind.DATE));
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("deliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountRate", new BillHeaderFilterSupport.Spec("discountRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("depositAmount", new BillHeaderFilterSupport.Spec("depositAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchaseContract", PurchaseContract.class, f);
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
            "buyer_id",
            "close_status",
            "closed_at",
            "closed_by",
            "closed_reason",
            "contact_person",
            "contact_phone",
            "contract_date",
            "contract_id",
            "contract_no",
            "contract_type",
            "created_at",
            "created_by",
            "currency_id",
            "delivery_date",
            "department_id",
            "deposit_amount",
            "discount_amount",
            "discount_rate",
            "exchange_rate",
            "invoice_type",
            "is_invoiced",
            "is_tax",
            "line_no",
            "logistics_company",
            "logistics_no",
            "net_amount",
            "org_code",
            "payment_method",
            "payment_term_id",
            "project_no",
            "receive_address",
            "remark",
            "status",
            "supplier_id",
            "supplier_no",
            "supplier_order_no",
            "supplier_short_name",
            "tax_amount",
            "tax_calc_method",
            "total_amount",
            "transport_method",
            "updated_at",
            "updated_by",
            "validity_end",
            "validity_start",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final PurchaseContractItemMapper itemMapper;
    private final SupplierService supplierService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final WarehouseService warehouseService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    /** 下游引用校验（统一状态机重置草稿/作废/删除前使用） */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    /** 设置中心业务规则校验器（来源单号必填等开关） */
    private final SysParamValidator sysParamValidator;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "采购合同"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_CONTRACT"; }
    @Override protected String getBillNo(PurchaseContract b) { return b.getContractNo(); }
    @Override protected Object getId(PurchaseContract b) { return b.getContractId(); }
    @Override protected String getStatus(PurchaseContract b) { return b.getStatus(); }
    @Override protected void setStatus(PurchaseContract b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(PurchaseContract b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchaseContract b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(PurchaseContract b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchaseContract b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchaseContract b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchaseContract b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchaseContract b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchaseContract b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchaseContract b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchaseContract b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchaseContract b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchaseContract b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchaseContract b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(PurchaseContract bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getContractNo());
    }

    @Override
    @Transactional
    public void saveContractWithItems(PurchaseContract contract, List<PurchaseContractItem> items) {
        boolean isNew = contract.getContractId() == null;

        // 业务规则: 已生效的采购合同不可直接修改
        if (!isNew) {
            PurchaseContract existing = getById(contract.getContractId());
            if (existing != null) {
                if ("已生效".equals(existing.getStatus())) {
                    throw new BusinessException("已生效的采购合同不可直接修改，请先重置草稿");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的采购合同不可修改");
                }
            }
        }

        // 业务规则: 合同编号按编码规则自动生成；设置中心关闭自动生成时需手动输入
        if (contract.getContractNo() == null || contract.getContractNo().isBlank()) {
            // 设置中心已关闭自动生成时，要求用户手动输入编号，不再兜底自动生成
            if (!sysSerialNoConfigService.isAutoGenerate("PURCHASE_CONTRACT")) {
                throw new BusinessException("采购合同已关闭自动编号，请手动输入合同号");
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
        if (contract.getDepositAmount() == null) contract.setDepositAmount(BigDecimal.ZERO);
        if (contract.getIsTax() == null) contract.setIsTax(1);
        if (contract.getIsInvoiced() == null) contract.setIsInvoiced(0);
        if (contract.getContractDate() == null) contract.setContractDate(LocalDate.now());
        // createdBy/updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）
        if (contract.getCreatedAt() == null) contract.setCreatedAt(LocalDateTime.now());
        if (contract.getUpdatedAt() == null) contract.setUpdatedAt(LocalDateTime.now());

        // 【2026-09-15 新增 · 2026-09-20 保存宽松化】设置中心-系统参数设置-采购参数：「采购合同必须有来源单号才能提交」。
        // 该校验已从保存路径移到 submit()（保存=存草稿不再拦截）；口径（用户 2026-09-15 定）：
        //   ① 判定取【明细行】source_bill_no —— 采购合同表头无来源单号列（来源只存在明细行，
        //      由「选单」或上游单据「下推」写入），任一行有非空来源单号即视为满足；
        //   ② 每次提交都校验（不限新建）；
        //   ③ 开关关闭 / 配置缺失 → 不校验（与升级前行为一致）；
        //   ④ Excel 批量导入走本方法（保存落草稿），启用开关时提交同样受此约束。
        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<PurchaseContractItem> validItems = new ArrayList<>();
            for (PurchaseContractItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            contract.setTaxCalcMethod("不计税");
        } else if (contract.getTaxCalcMethod() == null || contract.getTaxCalcMethod().isBlank()) {
            contract.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(contract.getTaxCalcMethod());
        // 金额、税额、价税合计由系统按表头「计税方式」自动计算
        recalculateContract(contract, items, headerTaxType);

        if (isNew) {
            save(contract);
        } else {
            updateById(contract);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<PurchaseContractItem>().eq("contract_id", contract.getContractId()));
            int lineNo = 1;
            for (PurchaseContractItem item : items) {
                item.setContractItemId(null);
                item.setContractId(contract.getContractId());
                item.setLineNo(lineNo);
                if (item.getReceivedQuantity() == null) item.setReceivedQuantity(BigDecimal.ZERO);
                if (item.getInvoicedQuantity() == null) item.setInvoicedQuantity(BigDecimal.ZERO);
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                calculateItemAmount(item, headerTaxType);
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("采购合同保存成功: {} 状态: {} 合计: {}", contract.getContractNo(), contract.getStatus(), contract.getTotalAmount());

        // 【2026-09-14 修复】此处原先还有一段"免审批（审批开关关闭）：新建合同保存后自动生效"
        // 直接调 approve()。已删除，原因：
        //   1. 与既定状态机制冲突 —— 保存应落到「草稿」，生效应由「提交」触发；
        //   2. 全系统唯一 —— 其他 9 个模块（调拨/盘点/其他入出库/领料/样品/费用/付款/BOM）的免审批分支
        //      都在 submit 里，只有采购合同把 save 也做了，造成同模块行为不一致；
        //   3. 实际后果（E2E 实证 2026-09-14）—— 保存后单据变「已生效」，明细数量框因 :disabled="!isDraft"
        //      被禁用，用户想改明细必须先「重置草稿」。
        // 免审批「提交即生效」的能力由下方 submit() 中已有的分支保留，未丢失。
    }

    /**
     * 取"任一非空的明细来源单号"，供「采购合同必须有来源单号才能保存」校验使用。
     * <p>
     * 采购合同表头没有来源单号字段，来源信息按系统既有设计挂在明细行
     * （purchase_contract_item.source_bill_no，由「选单」或上游单据「下推」写入）。
     * items 为空时回查库内既有明细，避免"不带 items 的部分更新"绕过校验，
     * 也保证"每次保存都拦"的口径一致。
     *
     * @return 第一个非空的明细来源单号；一个都没有则返回 null
     */
    private String resolveAnySourceBillNo(PurchaseContract contract, List<PurchaseContractItem> items) {
        List<PurchaseContractItem> sourceItems = items;
        if (sourceItems == null || sourceItems.isEmpty()) {
            if (contract == null || contract.getContractId() == null) return null;
            sourceItems = itemMapper.selectList(
                    new QueryWrapper<PurchaseContractItem>().eq("contract_id", contract.getContractId()));
        }
        if (sourceItems == null) return null;
        for (PurchaseContractItem item : sourceItems) {
            String sourceBillNo = item.getSourceBillNo();
            if (sourceBillNo != null && !sourceBillNo.isBlank()) return sourceBillNo;
        }
        return null;
    }

    private void validateContract(PurchaseContract contract, List<PurchaseContractItem> items) {
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                PurchaseContractItem item = items.get(i);
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("第" + (i + 1) + "行数量必须大于0");
                }
                if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("第" + (i + 1) + "行单价不可为负数");
                }
                if (item.getDeliveryDate() != null && contract.getContractDate() != null
                        && contract.getContractDate().isAfter(item.getDeliveryDate())) {
                    throw new BusinessException("合同日期不可晚于第" + (i + 1) + "行的交货日期");
                }
            }
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 由表头「计税方式」解析明细计税类型：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。总开关关闭时一律不计税。 */
    private int resolveHeaderTaxType(String method) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String v = method == null ? null : method.trim();
        if (v == null || v.isBlank()) v = sysParamValidator.getDefaultTaxMethod();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    /** 逐行计算：按表头「计税方式」由「单价」换算含税价/不含税价，并计算折扣额/金额/税额/价税合计 */
    private void calculateItemAmount(PurchaseContractItem item, int headerTaxType) {
        BigDecimal unitPrice = nvl(item.getUnitPrice());
        BigDecimal quantity = nvl(item.getQuantity());
        BigDecimal taxRate = nvl(item.getTaxRate());
        BigDecimal discountRate = nvl(item.getDiscountRate());
        int taxType = headerTaxType;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);
        item.setDiscountRate(discountRate);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

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

        // 折前金额按「不含税」口径参与折扣与税额计算
        BigDecimal originalAmount = inclusive
                ? unitPrice.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discountAmount = originalAmount.multiply(discountRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setDiscountAmount(discountAmount);
        BigDecimal amount = originalAmount.subtract(discountAmount).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, discountRate, inclusive, noTax);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 汇总金额/税额/价税合计，并按表头汇率折算本位币 */
    private void recalculateContract(PurchaseContract contract, List<PurchaseContractItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        if (items != null) {
            for (PurchaseContractItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()).add(nvl(item.getDiscountAmount())));
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
        }

        contract.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        contract.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        contract.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        contract.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        BigDecimal rate = contract.getExchangeRate() != null ? contract.getExchangeRate() : BigDecimal.ONE;
        contract.setBaseTotalAmount(contract.getTotalAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        contract.setBaseTaxAmount(contract.getTaxAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
        contract.setBaseNetAmount(contract.getNetAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseContract> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<PurchaseContract> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseContract.class);
        // 【2026-09-15 修复 · 2026-09-16 收敛到统一件】关键词两支必须成组：
        // 原写法 like(..).or().like(..) 平铺在最外层时，MP 不会为 OR 分组补括号，而 SQL 里 AND 优先级高于 OR，
        // 结果是后续的 status / 数据权限 / 自定义字段 EXISTS 只作用在「供应商简称」那一支 →
        // 命中「合同号」的行会绕过全部后续条件（含数据权限，可越权看到他人的合同）。
        // 现统一走 ListQuerySupport.applyKeywordGroup（本项目唯一允许的多列关键词写法）。
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "contract_no", "supplier_short_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 contract_date）；供应商（本页真实列 supplier_id）；员工=采购员（本页真实列 buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "contract_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "purchase_contract", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_contract"));
        Page<PurchaseContract> page = new Page<>(current, size);
        Page<PurchaseContract> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<PurchaseContract> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<PurchaseContract> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseContract.class);
        // 关键词：合同号 + 供应商简称 成组匹配（与列表页搜索提示一致，口径同上一重载）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "contract_no", "supplier_short_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（contract_date）；供应商（supplier_id）；员工=采购员（buyer_id）
        ListQuerySupport.applyDateRange(wrapper, "contract_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "buyer_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "purchaseContract", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "buyer_id");
        CustomFieldSearchHelper.apply(wrapper, "purchase_contract", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_contract"));
        Page<PurchaseContract> page = new Page<>(current, size);
        Page<PurchaseContract> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseContract getDetail(Long id) {
        PurchaseContract contract = getById(id);
        if (contract == null) throw new BusinessException("采购合同不存在");
        List<PurchaseContractItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseContractItem>().eq("contract_id", id).orderByAsc("line_no"));
        contract.setItems(items);
        return contract;
    }

    /** 提交审核：状态校验/严格校验/审批开关/审批任务统一由统一状态机基类执行 */
    @Override
    @Transactional
    public void submit(Long id, String submitter) {
        PurchaseContract contract = getById(id);
        if (contract == null) throw new BusinessException("采购合同不存在");
        super.submitForApproval(contract, submitter != null && !submitter.isBlank()
                ? submitter : BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（明细/供应商/采购员/币种/仓库/来源单号/数量单价） */
    @Override
    protected void validateStrictSubmit(PurchaseContract contract) {
        Long id = contract.getContractId();
        List<PurchaseContractItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseContractItem>().eq("contract_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购合同没有明细，请至少添加一行物料后再提交");
        }

        if (contract.getSupplierId() == null) {
            throw new BusinessException("供应商不能为空，请选择供应商");
        }
        if (supplierService.getById(contract.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + contract.getSupplierId() + ")，请重新选择供应商");
        }
        if (contract.getBuyerId() != null && employeeService.getById(contract.getBuyerId()) == null) {
            throw new BusinessException("关联采购员不存在(buyerId=" + contract.getBuyerId() + ")，请重新选择采购员");
        }
        if (contract.getCurrencyId() == null) {
            throw new BusinessException("币种不能为空，请选择币种");
        }
        if (currencyService.getById(contract.getCurrencyId()) == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + contract.getCurrencyId() + ")，请重新选择币种");
        }
        if (contract.getWarehouseId() != null && warehouseService.getById(contract.getWarehouseId()) == null) {
            throw new BusinessException("关联仓库不存在(warehouseId=" + contract.getWarehouseId() + ")，请重新选择仓库");
        }

        for (int i = 0; i < items.size(); i++) {
            PurchaseContractItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行关联物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "") + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getUnitId() != null && unitOfMeasureService.getById(item.getUnitId()) == null) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]关联单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]数量必须大于0");
            }
        }

        // 提交=严格校验：来源单号（设置中心开关）+ 明细业务校验（数量/单价/合同日期与交货日期）
        sysParamValidator.validateRequireSource("purchase.contract.require.source", "采购合同",
                resolveAnySourceBillNo(contract, items));
        validateContract(contract, items);
    }

    // ================= 审核入口（统一状态机 + 审批引擎防绕过） =================

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PURCHASE_CONTRACT", (Long) id) != null) {
            throw new BusinessException("采购合同已提交审批流程，请由指定审批人在审批中心处理");
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
    public void cancel(Long id, String reason) {
        PurchaseContract contract = getById(id);
        if (contract == null) throw new BusinessException("采购合同不存在");
        if (!"草稿".equals(contract.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + contract.getStatus());
        }
        contract.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            contract.setRemark((contract.getRemark() != null ? contract.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(contract);
        log.info("采购合同取消: {} 原因: {}", contract.getContractNo(), reason);
    }

    @Override
    public String generateContractNo() {
        // 优先走设置中心自动编号规则（PURCHASE_CONTRACT），未配置或规则异常时退回内置默认规则
        String docType = "PURCHASE_CONTRACT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
            QueryWrapper<PurchaseContract> wrapper = new QueryWrapper<>();
            wrapper.likeRight("contract_no", prefix).orderByDesc("contract_no").last("LIMIT 1");
            PurchaseContract last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getContractNo() != null) {
                try {
                    seq = Integer.parseInt(last.getContractNo().substring(prefix.length())) + 1;
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
        PurchaseContract bill = getById(id);
        if (bill == null) throw new BusinessException("采购合同不存在");
        // 状态校验 + 下游校验 + 状态迁移统一由基类执行
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<PurchaseContract> uw = new UpdateWrapper<>();
        uw.eq("contract_id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    /** 结案：统一状态机（已生效 + 未结案校验，写 close_status/closed_*） */
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

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        // 状态/结案/下游校验、作废前状态记录、作废审计字段全部由基类执行
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
        // 草稿校验 + 下游校验 + 整单级联删除由基类执行
        super.deleteBill(id);
    }
}
