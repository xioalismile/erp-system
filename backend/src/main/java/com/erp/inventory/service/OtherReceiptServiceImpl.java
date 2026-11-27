package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.entity.OtherReceipt;
import com.erp.inventory.entity.OtherReceiptItem;
import com.erp.inventory.mapper.OtherReceiptItemMapper;
import com.erp.inventory.mapper.OtherReceiptMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class OtherReceiptServiceImpl extends AbstractBillService<OtherReceiptMapper, OtherReceipt> implements OtherReceiptService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 · 库存模块推广批次）。
        // 键 = 前端筛选条件定义键（与其他入库单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（由 MyBatis-Plus 元数据自动解析列名，防手写列名笔误）。
        // 经手人：列是员工外键（handler_id），按名称筛的下拉传的是员工 ID → 翻译成 handlerId 属性。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("receiptNo", new BillHeaderFilterSupport.Spec("receiptNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("receiptType", new BillHeaderFilterSupport.Spec("receiptType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("handlerName", new BillHeaderFilterSupport.Spec("handlerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("receiptDate", new BillHeaderFilterSupport.Spec("receiptDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("otherReceipt", OtherReceipt.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "attachment_urls",
            "created_at",
            "created_by",
            "currency_id",
            "exchange_rate",
            "handler_id",
            "line_no",
            "org_code",
            "receipt_date",
            "receipt_id",
            "receipt_no",
            "receipt_type",
            "remark",
            "source_bill_no",
            "source_bill_type",
            "status",
            "total_amount",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final OtherReceiptItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final MaterialCostService materialCostService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final WarehouseService warehouseService;
    /** 审批通用接入件（提交进入审批引擎 + 构建条件路由 billData） */
    private final BillApprovalAdapter billApprovalAdapter;

    /**
     * 审批运行时引擎：仅用于「已有进行中审批任务时禁止绕过引擎直审」的防护判断。
     * @Lazy 打破循环依赖：审批引擎按 BillApprovalCallback 收集全部单据Service，
     * 单据Service又需要审批引擎 —— 延迟代理避免启动期循环创建失败。
     */
    @Lazy
    @Autowired
    private ApprovalRuntimeService approvalRuntimeService;

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤，替代旧 SecurityUtils.getCreatedByFilter 硬编码 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /** 设置中心业务规则（含税业务开关 / 默认计税方式）：表头「计税方式」口径归一用 */
    private final com.erp.config.SysParamValidator sysParamValidator;
    /** 批次号规则（2026-11-06）：自动生成 / 重复入库 */
    private final com.erp.inventory.service.BatchNoRuleService batchNoRuleService;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "其他入库单"; }
    @Override protected String getApprovalBillType() { return "OTHER_RECEIPT"; }
    @Override protected String getBillNo(OtherReceipt b) { return b.getReceiptNo(); }
    @Override protected Object getId(OtherReceipt b) { return b.getReceiptId(); }
    @Override protected String getStatus(OtherReceipt b) { return b.getStatus(); }
    @Override protected void setStatus(OtherReceipt b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(OtherReceipt b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(OtherReceipt b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(OtherReceipt b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(OtherReceipt b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(OtherReceipt b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(OtherReceipt b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(OtherReceipt b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(OtherReceipt b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(OtherReceipt b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(OtherReceipt b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(OtherReceipt b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(OtherReceipt b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(OtherReceipt b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(OtherReceipt bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReceiptNo());
    }

    @Override
    @Transactional
    public void saveReceiptWithItems(OtherReceipt receipt, List<OtherReceiptItem> items) {
        boolean isNew = receipt.getReceiptId() == null;

        if (!isNew) {
            OtherReceipt existing = getById(receipt.getReceiptId());
            if (existing != null) {
                String st = existing.getStatus();
                if ("已生效".equals(st) || "已关闭".equals(st) || "已取消".equals(st)) {
                    throw new BusinessException("当前状态(" + st + ")不可修改，如需修改请先重置草稿");
                }
            }
        }

        if (receipt.getReceiptNo() == null || receipt.getReceiptNo().isBlank()) {
            receipt.setReceiptNo(generateReceiptNo());
        }

        // 保存=草稿：前端送空串时按空处理（与"未传/null"等价）
        if (receipt.getStatus() == null || receipt.getStatus().isBlank()) receipt.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"，"待审核"是提交后的状态
        if (receipt.getApprovalStatus() == null || receipt.getApprovalStatus().isBlank()
                || ("草稿".equals(receipt.getStatus()) && "待审核".equals(receipt.getApprovalStatus()))) {
            receipt.setApprovalStatus("草稿");
        }
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(LocalDate.now());
        if (receipt.getExchangeRate() == null) receipt.setExchangeRate(BigDecimal.ONE);
        if (receipt.getTotalAmount() == null) receipt.setTotalAmount(BigDecimal.ZERO);

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<OtherReceiptItem> validItems = new ArrayList<>();
            for (OtherReceiptItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        // 批次号规则（2026-11-06）：批次管理物料空批号自动生成；不允许修改时拦截手改
        List<OtherReceiptItem> oldBatchItems = isNew ? new ArrayList<>()
                : itemMapper.selectList(new QueryWrapper<OtherReceiptItem>()
                        .eq("receipt_id", receipt.getReceiptId()).orderByAsc("line_no"));
        applyBatchNoRules(items, oldBatchItems);

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            receipt.setTaxCalcMethod("不计税");
        } else if (receipt.getTaxCalcMethod() == null || receipt.getTaxCalcMethod().isBlank()) {
            receipt.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(receipt.getTaxCalcMethod());
        recalculate(receipt, items, headerTaxType);

        if (isNew) {
            save(receipt);
        } else {
            updateById(receipt);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<OtherReceiptItem>().eq("receipt_id", receipt.getReceiptId()));
            int lineNo = 1;
            for (OtherReceiptItem item : items) {
                item.setReceiptItemId(null);
                item.setReceiptId(receipt.getReceiptId());
                item.setLineNo(lineNo);
                calculateItemAmount(item, headerTaxType);
                // === 回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("其他入库单保存成功: {} 状态: {} 合计: {}", receipt.getReceiptNo(), receipt.getStatus(), receipt.getTotalAmount());
    }

    /**
     * 批次号规则落地（2026-11-06）：空批号按规则自动生成；规则不允许修改时拦截手改。
     * 其他入库保存会整表重建明细，故按「行顺序 + 物料一致」对比原批号。
     */
    private void applyBatchNoRules(List<OtherReceiptItem> items, List<OtherReceiptItem> oldItems) {
        if (items == null || items.isEmpty()) return;
        List<String> used = new ArrayList<>();
        for (OtherReceiptItem it : items) {
            if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) used.add(it.getBatchNo().trim());
        }
        for (int i = 0; i < items.size(); i++) {
            OtherReceiptItem it = items.get(i);
            if (it.getMaterialId() == null) continue;
            String oldNo = null;
            if (oldItems != null && oldItems.size() > i) {
                OtherReceiptItem old = oldItems.get(i);
                if (old != null && it.getMaterialId().equals(old.getMaterialId())) oldNo = old.getBatchNo();
            }
            batchNoRuleService.checkModifyAllowed(it.getMaterialId(), oldNo, it.getBatchNo(), "第" + (i + 1) + "行");
            String ensured = batchNoRuleService.ensureBatchNo(it.getMaterialId(), it.getBatchNo(), used);
            if (ensured != null && !ensured.isBlank()) {
                it.setBatchNo(ensured);
                if (!used.contains(ensured)) used.add(ensured);
            }
        }
    }

    /** 由表头「计税方式」解析明细计税类型：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。总开关关闭时一律不计税。 */
    private int resolveHeaderTaxType(String method) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String v = method == null ? null : method.trim();
        if (v == null || v.isBlank()) v = sysParamValidator.getDefaultTaxMethod();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 多单位换算：库存记账只认基本单位数量（baseQuantity）；无折算快照时回落单据数量 */
    private static BigDecimal stockQuantity(BigDecimal baseQuantity, BigDecimal quantity) {
        if (baseQuantity != null) return baseQuantity;
        return quantity != null ? quantity : BigDecimal.ZERO;
    }

    private void calculateItemAmount(OtherReceiptItem item, int headerTaxType) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
        BigDecimal discountRate = item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);
        item.setDiscountRate(discountRate);

        boolean noTax = headerTaxType == TAX_TYPE_NO_TAX;
        boolean inclusive = headerTaxType == TAX_TYPE_INCLUSIVE;
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
        BigDecimal discountAmount = originalAmount.multiply(discountRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        item.setDiscountAmount(discountAmount);
        BigDecimal amount = originalAmount.subtract(discountAmount).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 TaxAmountUtils）
        BigDecimal finalTaxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, discountRate, inclusive, noTax);
        item.setTaxAmount(finalTaxAmount);
        item.setNetAmount(amount.add(finalTaxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void recalculate(OtherReceipt receipt, List<OtherReceiptItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        if (items != null) {
            for (OtherReceiptItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()).add(nvl(item.getDiscountAmount())));
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
            }
        }
        receipt.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        receipt.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        receipt.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        receipt.setNetAmount(totalAmount.subtract(totalDiscount).add(totalTax).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<OtherReceipt> pageQuery(int current, int size, String keyword, String status,
                                             String startDate, String endDate, String employeeId,
                                             String sortBy, String sortOrder, String customSearch) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, java.util.Collections.emptyMap());
    }

    @Override
    public PageResult<OtherReceipt> pageQuery(int current, int size, String keyword, String status,
                                             String startDate, String endDate, String employeeId,
                                             String sortBy, String sortOrder, String customSearch,
                                             java.util.Map<String, String> headerFilters) {
        QueryWrapper<OtherReceipt> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, OtherReceipt.class);
        // 关键词（本页真实列 receipt_no）——多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 receipt_date）；员工=经办人（本页真实列 handler_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "handler_id");
        // 表头字段过滤（2026-10-14 列表页筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "otherReceipt", headerFilters);
        // 数据权限（按角色 data_scope 统一数据权限）：仅本人/本部门/全部数据，替代旧 SecurityUtils.getCreatedByFilter
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "other_receipt", customSearch, CustomFieldSearchHelper.idColumnOf("other_receipt"));
        Page<OtherReceipt> page = new Page<>(current, size);
        Page<OtherReceipt> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public OtherReceipt getDetail(Long id) {
        OtherReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("其他入库单不存在");
        List<OtherReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<OtherReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        receipt.setItems(items);
        return receipt;
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submitForApproval(Long id) {
        OtherReceipt bill = getById(id);
        if (bill == null) throw new BusinessException("其他入库单不存在");
        super.submitForApproval(bill, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(OtherReceipt receipt) {
        Long id = receipt.getReceiptId();
        List<OtherReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<OtherReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("其他入库单没有明细，请至少添加一行物料后再提交");
        }

        // 2026-11-07：表头仓库改为统一选择器，不再要求必填；入库以明细行仓库为准（逐行精确提示）
        for (int i = 0; i < items.size(); i++) {
            OtherReceiptItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行关联物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "") + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getStockId() == null) {
                throw new BusinessException("第" + lineNo + "行[" + matInfo + "]未选择仓库，请选择仓库");
            }
            if (warehouseService.getById(item.getStockId()) == null) {
                throw new BusinessException("第" + lineNo + "行[" + matInfo + "]所选仓库不存在，请重新选择");
            }
            if (item.getUnitId() != null && unitOfMeasureService.getById(item.getUnitId()) == null) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]关联单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]入库数量必须大于0");
            }
            // 2026-11-06：重复入库参数关闭时，同物料同批号已在库 → 拦截
            batchNoRuleService.checkRepeatInbound(item.getMaterialId(), item.getBatchNo(),
                    "第" + lineNo + "行物料[" + matInfo + "]");
        }

    }

    /** 审核：审批引擎防护 + 状态迁移全部由统一状态机执行，入库过账在 onApproved 钩子 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("OTHER_RECEIPT", (Long) id) != null) {
            throw new BusinessException("其他入库单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：增加库存（审核时先刷新最新核算成本） */
    @Override
    public void onApproved(OtherReceipt receipt) {
        Long id = receipt.getReceiptId();
        String approver = receipt.getApprovedBy();
        List<OtherReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<OtherReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (OtherReceiptItem item : items) {
                Long stockId = item.getStockId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    // 2026-11-07：原实现静默跳过（单据已生效、库存却没加），改为精确到行报错
                    throw new BusinessException("其他入库单第" + item.getLineNo()
                            + "行[" + (item.getMaterialCode() == null ? "" : item.getMaterialCode()) + " "
                            + (item.getMaterialName() == null ? "" : item.getMaterialName())
                            + "]未选择仓库或缺少物料，无法入库");
                }
                // 成本链路（2026-11-26）：冻结本次实际入库成本，供反审按原成本红字冲回
                item.setAvgCost(unitCost);
                itemMapper.updateById(item);
                inventoryService.addStock(
                        stockId,
                        item.getBinId(),
                        materialId,
                        item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity),
                        unitCost,
                        receipt.getReceiptNo(),
                        "OTHER_IN",
                        "OtherReceipt",
                        receipt.getReceiptNo(),
                        approver);
            }
        }

        log.info("其他入库单审核: {} 审核人: {}", receipt.getReceiptNo(), approver);
    }

    /** 重置草稿：本单据状态守卫保留，状态迁移/下游校验由统一状态机执行，入库冲回在 onReverseApproved 钩子 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        OtherReceipt bill = getById(id);
        if (bill == null) throw new BusinessException("其他入库单不存在");
        if (!"已生效".equals(bill.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<OtherReceipt>().eq("receipt_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 重置草稿影响：与 onApproved 严格镜像（扣减入库库存） */
    @Override
    public void onReverseApproved(OtherReceipt receipt) {
        Long id = receipt.getReceiptId();
        String operator = receipt.getApprovedBy();
        List<OtherReceiptItem> items = itemMapper.selectList(
                new QueryWrapper<OtherReceiptItem>().eq("receipt_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (OtherReceiptItem item : items) {
                Long stockId = item.getStockId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    // 2026-11-16：与审核入账（onApproved）同口径显式报错。原实现静默跳过 → 冲回悄悄丢失、
                    // 库存不扣回（121 实锤：9-24 老单重置草稿时明细行缺仓库，-846 红冲未发生，删除后成无源库存）
                    throw new BusinessException("其他入库单第" + item.getLineNo()
                            + "行[" + (item.getMaterialCode() == null ? "" : item.getMaterialCode())
                            + " " + (item.getMaterialName() == null ? "" : item.getMaterialName())
                            + "]未选择仓库或缺少物料，库存冲回无法执行，请先补全该行仓库与物料后重试");
                }
                BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                        ? item.getAvgCost()
                        : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                inventoryService.deductStockAtCost(
                        stockId,
                        item.getBinId(),
                        materialId,
                        item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity),
                        reverseUnitCost,
                        receipt.getReceiptNo(),
                        "OTHER_IN_REVERSE",
                        "OtherReceipt",
                        receipt.getReceiptNo(),
                        operator);
            }
        }
        log.info("其他入库单重置草稿回冲完成: {}", receipt.getReceiptNo());
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        super.close(id, closer, reason);
    }

    /** 结案影响：保留业务执行态双写 */
    @Override
    public void onClosed(OtherReceipt bill) {
        update(new UpdateWrapper<OtherReceipt>().eq("receipt_id", bill.getReceiptId())
                .set("bus_status", com.erp.common.bill.BusStatusEnum.CLOSED.getLabel()));
    }

    /** 反结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    /** 反结案影响：清空业务执行态 */
    @Override
    public void onReopened(OtherReceipt bill) {
        update(new UpdateWrapper<OtherReceipt>().eq("receipt_id", bill.getReceiptId())
                .set("bus_status", null));
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        OtherReceipt receipt = getById(id);
        if (receipt == null) throw new BusinessException("其他入库单不存在");
        if (!"草稿".equals(receipt.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + receipt.getStatus());
        }
        receipt.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            receipt.setRemark((receipt.getRemark() != null ? receipt.getRemark() + " | " : "") + "取消: " + reason);
        }
        updateById(receipt);
        log.info("其他入库单取消: {} 原因: {}", receipt.getReceiptNo(), reason);
    }

    @Override
    public String generateReceiptNo() {
        String docType = "OTHER_RECEIPT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "OIN-" + dateStr + "-";
            QueryWrapper<OtherReceipt> wrapper = new QueryWrapper<>();
            wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
            OtherReceipt last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReceiptNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReceiptNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 驳回：状态校验/审批引擎防护由统一状态机执行 */
    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行，冲回在 onVoided 钩子 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 作废影响：与重置草稿严格镜像（扣回入库库存） */
    @Override
    public void onVoided(OtherReceipt bill) {
        onReverseApproved(bill);
    }

    /**
     * 反作废影响（2026-11-09）：作废时已冲回库存，反作废恢复到「已生效」必须重新过账，
     * 否则账面库存少一笔（跨模块验收实锤）。与 onVoided 严格镜像。
     */
    @Override
    public void onUnvoided(OtherReceipt bill) {
        onApproved(bill);
    }

    /** 反作废：状态与作废痕迹清理由统一状态机执行 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Override
    @Transactional
    public void deleteBill(Long id) {
        // 草稿校验 + 下游校验 + 整单级联删除由统一状态机执行
        super.deleteBill(id);
    }
}
