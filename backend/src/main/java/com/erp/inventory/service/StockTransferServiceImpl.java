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
import com.erp.config.service.SysConfigService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.entity.StockTransferItem;
import com.erp.inventory.mapper.StockTransferItemMapper;
import com.erp.inventory.mapper.StockTransferMapper;
import com.erp.basedata.entity.Warehouse;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
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

public class StockTransferServiceImpl extends AbstractBillService<StockTransferMapper, StockTransfer> implements StockTransferService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 · 库存模块推广批次）。
        // 键 = 前端筛选条件定义键（与库存调拨单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（由 MyBatis-Plus 元数据自动解析列名，防手写列名笔误）。
        // 经手人：列是员工外键（handler_id），按名称筛的下拉传的是员工 ID → 翻译成 handlerId 属性。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("transferNo", new BillHeaderFilterSupport.Spec("transferNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("transferType", new BillHeaderFilterSupport.Spec("transferType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("handlerName", new BillHeaderFilterSupport.Spec("handlerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("transferDate", new BillHeaderFilterSupport.Spec("transferDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("stockTransfer", StockTransfer.class, f);
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
            "from_stock_id",
            "handler_id",
            "line_no",
            "org_code",
            "remark",
            "status",
            "to_stock_id",
            "total_amount",
            "transfer_date",
            "transfer_id",
            "transfer_no",
            "transfer_type",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final StockTransferItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final MaterialCostService materialCostService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final WarehouseService warehouseService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final SysConfigService sysConfigService;
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

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "库存调拨单"; }
    @Override protected String getApprovalBillType() { return "STOCK_TRANSFER"; }
    @Override protected String getBillNo(StockTransfer b) { return b.getTransferNo(); }
    @Override protected Object getId(StockTransfer b) { return b.getTransferId(); }
    @Override protected String getStatus(StockTransfer b) { return b.getStatus(); }
    @Override protected void setStatus(StockTransfer b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(StockTransfer b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(StockTransfer b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(StockTransfer b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(StockTransfer b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(StockTransfer b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(StockTransfer b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(StockTransfer b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(StockTransfer b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(StockTransfer b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(StockTransfer b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(StockTransfer b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(StockTransfer b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(StockTransfer b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(StockTransfer bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getTransferNo());
    }

    @Override
    @Transactional
    public void saveTransferWithItems(StockTransfer transfer, List<StockTransferItem> items) {
        boolean isNew = transfer.getTransferId() == null;

        if (!isNew) {
            StockTransfer existing = getById(transfer.getTransferId());
            if (existing != null) {
                String st = existing.getStatus();
                if ("已生效".equals(st) || "已关闭".equals(st) || "已取消".equals(st)) {
                    throw new BusinessException("当前状态(" + st + ")不可修改，如需修改请先重置草稿");
                }
            }
        }

        if (transfer.getTransferNo() == null || transfer.getTransferNo().isBlank()) {
            transfer.setTransferNo(generateTransferNo());
        }

        // 保存=草稿：前端送空串时按空处理（与"未传/null"等价）
        if (transfer.getStatus() == null || transfer.getStatus().isBlank()) transfer.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"，"待审核"是提交后的状态
        if (transfer.getApprovalStatus() == null || transfer.getApprovalStatus().isBlank()
                || ("草稿".equals(transfer.getStatus()) && "待审核".equals(transfer.getApprovalStatus()))) {
            transfer.setApprovalStatus("草稿");
        }
        if (transfer.getTransferDate() == null) transfer.setTransferDate(LocalDate.now());
        if (transfer.getTransferType() == null) transfer.setTransferType("同价调拨");
        if (transfer.getExchangeRate() == null) transfer.setExchangeRate(BigDecimal.ONE);
        if (transfer.getTotalAmount() == null) transfer.setTotalAmount(BigDecimal.ZERO);

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<StockTransferItem> validItems = new ArrayList<>();
            for (StockTransferItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            transfer.setTaxCalcMethod("不计税");
        } else if (transfer.getTaxCalcMethod() == null || transfer.getTaxCalcMethod().isBlank()) {
            transfer.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(transfer.getTaxCalcMethod());
        recalculate(transfer, items, headerTaxType);

        if (isNew) {
            save(transfer);
        } else {
            updateById(transfer);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<StockTransferItem>().eq("transfer_id", transfer.getTransferId()));
            int lineNo = 1;
            for (StockTransferItem item : items) {
                item.setTransferItemId(null);
                item.setTransferId(transfer.getTransferId());
                item.setLineNo(lineNo);
                if (item.getFromStockId() == null) item.setFromStockId(transfer.getFromStockId());
                if (item.getToStockId() == null) item.setToStockId(transfer.getToStockId());
                calculateItemAmount(item, headerTaxType);
                // === 回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("库存调拨单保存成功: {} 状态: {} 合计: {}", transfer.getTransferNo(), transfer.getStatus(), transfer.getTotalAmount());
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

    private void calculateItemAmount(StockTransferItem item, int headerTaxType) {
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

    private void recalculate(StockTransfer transfer, List<StockTransferItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        if (items != null) {
            for (StockTransferItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()).add(nvl(item.getDiscountAmount())));
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
            }
        }
        transfer.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        transfer.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        transfer.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        transfer.setNetAmount(totalAmount.subtract(totalDiscount).add(totalTax).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<StockTransfer> pageQuery(int current, int size, String keyword, String status,
                                              String startDate, String endDate, String employeeId,
                                              String sortBy, String sortOrder, String customSearch) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, java.util.Collections.emptyMap());
    }

    @Override
    public PageResult<StockTransfer> pageQuery(int current, int size, String keyword, String status,
                                              String startDate, String endDate, String employeeId,
                                              String sortBy, String sortOrder, String customSearch,
                                              java.util.Map<String, String> headerFilters) {
        QueryWrapper<StockTransfer> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, StockTransfer.class);
        // 关键词（本页真实列 transfer_no）——多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "transfer_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 transfer_date）；员工=经办人（本页真实列 handler_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "transfer_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "handler_id");
        // 表头字段过滤（2026-10-14 列表页筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "stockTransfer", headerFilters);
        // 数据权限（按角色 data_scope 统一数据权限）：仅本人/本部门/全部数据，替代旧 SecurityUtils.getCreatedByFilter
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "stock_transfer", customSearch, CustomFieldSearchHelper.idColumnOf("stock_transfer"));
        Page<StockTransfer> page = new Page<>(current, size);
        Page<StockTransfer> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public StockTransfer getDetail(Long id) {
        StockTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("库存调拨单不存在");
        List<StockTransferItem> items = itemMapper.selectList(
                new QueryWrapper<StockTransferItem>().eq("transfer_id", id).orderByAsc("line_no"));
        transfer.setItems(items);
        return transfer;
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submitForApproval(Long id) {
        StockTransfer bill = getById(id);
        if (bill == null) throw new BusinessException("库存调拨单不存在");
        super.submitForApproval(bill, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(StockTransfer transfer) {
        Long id = transfer.getTransferId();
        List<StockTransferItem> items = itemMapper.selectList(
                new QueryWrapper<StockTransferItem>().eq("transfer_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("调拨单没有明细，请至少添加一行物料后再提交");
        }

        if (transfer.getFromStockId() == null) {
            throw new BusinessException("调出仓库不能为空，请选择调出仓库");
        }
        Warehouse fromWh = warehouseService.getById(transfer.getFromStockId());
        if (fromWh == null) {
            throw new BusinessException("关联调出仓库不存在(fromStockId=" + transfer.getFromStockId() + ")，请重新选择调出仓库");
        }
        if (transfer.getToStockId() == null) {
            throw new BusinessException("调入仓库不能为空，请选择调入仓库");
        }
        if (warehouseService.getById(transfer.getToStockId()) == null) {
            throw new BusinessException("关联调入仓库不存在(toStockId=" + transfer.getToStockId() + ")，请重新选择调入仓库");
        }
        // 校验: 调出仓库与调入仓库不能相同（原在保存方法，保存宽松化后挪到提交严格校验）
        if (transfer.getFromStockId() != null && transfer.getFromStockId().equals(transfer.getToStockId())) {
            throw new BusinessException("调出仓库与调入仓库不能相同");
        }
        if (transfer.getHandlerId() != null && employeeService.getById(transfer.getHandlerId()) == null) {
            throw new BusinessException("关联经办人不存在(handlerId=" + transfer.getHandlerId() + ")，请重新选择经办人");
        }
        if (transfer.getCurrencyId() != null && currencyService.getById(transfer.getCurrencyId()) == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + transfer.getCurrencyId() + ")，请重新选择币种");
        }
        String fromWhName = fromWh.getStockName() != null ? fromWh.getStockName() : ("ID:" + transfer.getFromStockId());

        for (int i = 0; i < items.size(); i++) {
            StockTransferItem item = items.get(i);
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
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]调拨数量必须大于0");
            }
            // 【2026-11-10 全单据验收修复】库存是基本单位口径，行数量可能是辅单位（如 30 套 = 3 件）；
            // 原实现拿辅单位数量比库存 → 库存足够却被判不足。校验改用折算后的基本单位数量。
            BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) stockQty = qty;

            boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
            if (!negativeAllowed) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(mat.getMaterialId(), transfer.getFromStockId());
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(stockQty) < 0) {
                    throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]调出库存不足，调出仓库[" + fromWhName + "]可用 "
                            + available.setScale(2, RoundingMode.HALF_UP)
                            + "，需调出 " + stockQty.setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

    }

    /** 审核：审批引擎防护 + 状态迁移全部由统一状态机执行，库存过账在 onApproved 钩子 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("STOCK_TRANSFER", (Long) id) != null) {
            throw new BusinessException("库存调拨单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：调出仓库扣减 + 调入仓库增加（审核时先刷新最新核算成本） */
    @Override
    public void onApproved(StockTransfer transfer) {
        Long id = transfer.getTransferId();
        String approver = transfer.getApprovedBy();
        List<StockTransferItem> items = itemMapper.selectList(
                new QueryWrapper<StockTransferItem>().eq("transfer_id", id).orderByAsc("line_no"));
        if (items != null) {
            // === 审核时刷新avg_cost为最新核算成本（审核后冻结） ===
            for (StockTransferItem item : items) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
            for (StockTransferItem item : items) {
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                Long fromStockId = item.getFromStockId() != null ? item.getFromStockId() : transfer.getFromStockId();
                Long toStockId = item.getToStockId() != null ? item.getToStockId() : transfer.getToStockId();
                if (materialId == null || fromStockId == null || toStockId == null) {
                    continue;
                }
                // 成本链路（2026-11-26）：调拨前冻结调出仓实际成本，调入仓按同一成本入库；
                // 不使用单据单价，避免“单价≠库存成本”时把库存金额改掉。
                BigDecimal outUnitCost = inventoryService.getOutUnitCost(
                        fromStockId, item.getFromBinId(), materialId, item.getBatchNo());
                item.setAvgCost(outUnitCost);
                itemMapper.updateById(item);
                // 调出仓库扣减
                inventoryService.deductStock(
                        fromStockId, item.getFromBinId(), materialId, item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity),
                        transfer.getTransferNo(), "STOCK_TRANSFER_OUT",
                        "StockTransfer", transfer.getTransferNo(), approver);
                // 调入仓库增加
                inventoryService.addStock(
                        toStockId, item.getToBinId(), materialId, item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity), outUnitCost,
                        transfer.getTransferNo(), "STOCK_TRANSFER_IN",
                        "StockTransfer", transfer.getTransferNo(), approver);
            }
        }

        log.info("库存调拨单审核: {} 审核人: {}", transfer.getTransferNo(), approver);
    }

    /** 重置草稿：本单据状态守卫保留，状态迁移/下游校验由统一状态机执行，库存冲回在 onReverseApproved 钩子 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        StockTransfer bill = getById(id);
        if (bill == null) throw new BusinessException("库存调拨单不存在");
        if (!"已生效".equals(bill.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<StockTransfer>().eq("transfer_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 重置草稿影响：与 onApproved 严格镜像（调入仓库扣减 + 调出仓库增加） */
    @Override
    public void onReverseApproved(StockTransfer transfer) {
        Long id = transfer.getTransferId();
        String operator = transfer.getApprovedBy();
        List<StockTransferItem> items = itemMapper.selectList(
                new QueryWrapper<StockTransferItem>().eq("transfer_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (StockTransferItem item : items) {
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                Long fromStockId = item.getFromStockId() != null ? item.getFromStockId() : transfer.getFromStockId();
                Long toStockId = item.getToStockId() != null ? item.getToStockId() : transfer.getToStockId();
                if (materialId == null || fromStockId == null || toStockId == null) {
                    continue;
                }
                // 反向: 调入仓库扣减（按调拨时冻结成本精确冲回）
                BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                        ? item.getAvgCost()
                        : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                inventoryService.deductStockAtCost(
                        toStockId, item.getToBinId(), materialId, item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity),
                        reverseUnitCost,
                        transfer.getTransferNo(), "STOCK_TRANSFER_IN_REVERSE",
                        "StockTransfer", transfer.getTransferNo(), operator);
                // 反向: 调出仓库增加（按审核时冻结的实际成本回冲，不能用单据单价）
                inventoryService.addStock(
                        fromStockId, item.getFromBinId(), materialId, item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity), reverseUnitCost,
                        transfer.getTransferNo(), "STOCK_TRANSFER_OUT_REVERSE",
                        "StockTransfer", transfer.getTransferNo(), operator);
            }
        }
        log.info("库存调拨单重置草稿回冲完成: {}", transfer.getTransferNo());
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        super.close(id, closer, reason);
    }

    /** 结案影响：保留业务执行态双写 */
    @Override
    public void onClosed(StockTransfer bill) {
        update(new UpdateWrapper<StockTransfer>().eq("transfer_id", bill.getTransferId())
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
    public void onReopened(StockTransfer bill) {
        update(new UpdateWrapper<StockTransfer>().eq("transfer_id", bill.getTransferId())
                .set("bus_status", null));
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        StockTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("库存调拨单不存在");
        if (!"草稿".equals(transfer.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + transfer.getStatus());
        }
        transfer.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            transfer.setRemark((transfer.getRemark() != null ? transfer.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(transfer);
        log.info("库存调拨单取消: {} 原因: {}", transfer.getTransferNo(), reason);
    }

    @Override
    public String generateTransferNo() {
        String docType = "STOCK_TRANSFER";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "TR-" + dateStr + "-";
            QueryWrapper<StockTransfer> wrapper = new QueryWrapper<>();
            wrapper.likeRight("transfer_no", prefix).orderByDesc("transfer_no").last("LIMIT 1");
            StockTransfer last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getTransferNo() != null) {
                try {
                    seq = Integer.parseInt(last.getTransferNo().substring(prefix.length())) + 1;
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

    /** 作废影响：与重置草稿严格镜像（冲回调拨库存） */
    @Override
    public void onVoided(StockTransfer bill) {
        onReverseApproved(bill);
    }

    /**
     * 反作废影响（2026-11-09）：作废时已冲回调拨库存，反作废恢复到「已生效」必须重新过账，
     * 否则调出/调入两侧库存都少一笔（跨模块验收实锤）。与 onVoided 严格镜像。
     */
    @Override
    public void onUnvoided(StockTransfer bill) {
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
