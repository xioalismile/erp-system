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
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.entity.OtherIssueItem;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.mapper.OtherIssueItemMapper;
import com.erp.inventory.mapper.OtherIssueMapper;
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

public class OtherIssueServiceImpl extends AbstractBillService<OtherIssueMapper, OtherIssue> implements OtherIssueService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 · 库存模块推广批次）。
        // 键 = 前端筛选条件定义键（与其他出库单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（由 MyBatis-Plus 元数据自动解析列名，防手写列名笔误）。
        // 经手人：列是员工外键（handler_id），按名称筛的下拉传的是员工 ID → 翻译成 handlerId 属性。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("issueNo", new BillHeaderFilterSupport.Spec("issueNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("issueType", new BillHeaderFilterSupport.Spec("issueType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("handlerName", new BillHeaderFilterSupport.Spec("handlerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("issueDate", new BillHeaderFilterSupport.Spec("issueDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("discountAmount", new BillHeaderFilterSupport.Spec("discountAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("otherIssue", OtherIssue.class, f);
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
            "department_id",
            "exchange_rate",
            "handler_id",
            "issue_date",
            "issue_id",
            "issue_no",
            "issue_type",
            "line_no",
            "org_code",
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

    private final OtherIssueItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final MaterialCostService materialCostService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final WarehouseService warehouseService;
    private final SysConfigService sysConfigService;
    private final ApprovalGateHelper approvalGateHelper;
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
    @Override protected String getBillTypeLabel() { return "其他出库单"; }
    @Override protected String getApprovalBillType() { return "OTHER_ISSUE"; }
    @Override protected String getBillNo(OtherIssue b) { return b.getIssueNo(); }
    @Override protected Object getId(OtherIssue b) { return b.getIssueId(); }
    @Override protected String getStatus(OtherIssue b) { return b.getStatus(); }
    @Override protected void setStatus(OtherIssue b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(OtherIssue b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(OtherIssue b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(OtherIssue b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(OtherIssue b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(OtherIssue b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(OtherIssue b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(OtherIssue b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(OtherIssue b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(OtherIssue b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(OtherIssue b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(OtherIssue b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(OtherIssue b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(OtherIssue b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(OtherIssue bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getIssueNo());
    }

    @Override
    @Transactional
    public void saveIssueWithItems(OtherIssue issue, List<OtherIssueItem> items) {
        boolean isNew = issue.getIssueId() == null;

        if (!isNew) {
            OtherIssue existing = getById(issue.getIssueId());
            if (existing != null) {
                String st = existing.getStatus();
                if ("已生效".equals(st) || "已关闭".equals(st) || "已取消".equals(st)) {
                    throw new BusinessException("当前状态(" + st + ")不可修改，如需修改请先重置草稿");
                }
            }
        }

        if (issue.getIssueNo() == null || issue.getIssueNo().isBlank()) {
            issue.setIssueNo(generateIssueNo());
        }

        // 保存=草稿：前端送空串时按空处理（与"未传/null"等价）
        if (issue.getStatus() == null || issue.getStatus().isBlank()) issue.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"，"待审核"是提交后的状态
        if (issue.getApprovalStatus() == null || issue.getApprovalStatus().isBlank()
                || ("草稿".equals(issue.getStatus()) && "待审核".equals(issue.getApprovalStatus()))) {
            issue.setApprovalStatus("草稿");
        }
        if (issue.getExchangeRate() == null) issue.setExchangeRate(BigDecimal.ONE);
        if (issue.getTotalAmount() == null) issue.setTotalAmount(BigDecimal.ZERO);
        if (issue.getIssueDate() == null) issue.setIssueDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<OtherIssueItem> validItems = new ArrayList<>();
            for (OtherIssueItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            issue.setTaxCalcMethod("不计税");
        } else if (issue.getTaxCalcMethod() == null || issue.getTaxCalcMethod().isBlank()) {
            issue.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(issue.getTaxCalcMethod());
        recalculateIssue(issue, items, headerTaxType);

        if (isNew) {
            save(issue);
        } else {
            updateById(issue);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<OtherIssueItem>().eq("issue_id", issue.getIssueId()));
            int lineNo = 1;
            for (OtherIssueItem item : items) {
                item.setIssueItemId(null);
                item.setIssueId(issue.getIssueId());
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

        log.info("其他出库单保存成功: {} 状态: {} 合计: {}", issue.getIssueNo(), issue.getStatus(), issue.getTotalAmount());
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

    private void calculateItemAmount(OtherIssueItem item, int headerTaxType) {
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

    private void recalculateIssue(OtherIssue issue, List<OtherIssueItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        if (items != null) {
            for (OtherIssueItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()).add(nvl(item.getDiscountAmount())));
                totalDiscount = totalDiscount.add(nvl(item.getDiscountAmount()));
                totalTax = totalTax.add(nvl(item.getTaxAmount()));
            }
        }
        issue.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        issue.setDiscountAmount(totalDiscount.setScale(SCALE, RoundingMode.HALF_UP));
        issue.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        issue.setNetAmount(totalAmount.subtract(totalDiscount).add(totalTax).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<OtherIssue> pageQuery(int current, int size, String keyword, String status,
                                            String startDate, String endDate, String employeeId,
                                            String sortBy, String sortOrder, String customSearch) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, java.util.Collections.emptyMap());
    }

    @Override
    public PageResult<OtherIssue> pageQuery(int current, int size, String keyword, String status,
                                            String startDate, String endDate, String employeeId,
                                            String sortBy, String sortOrder, String customSearch,
                                            java.util.Map<String, String> headerFilters) {
        QueryWrapper<OtherIssue> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, OtherIssue.class);
        // 关键词（本页真实列 issue_no）——多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "issue_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 issue_date）；员工=经办人（本页真实列 handler_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "issue_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "handler_id");
        // 表头字段过滤（2026-10-14 列表页筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "otherIssue", headerFilters);
        // 数据权限（按角色 data_scope 统一数据权限）：仅本人/本部门/全部数据，替代旧 SecurityUtils.getCreatedByFilter
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "other_issue", customSearch, CustomFieldSearchHelper.idColumnOf("other_issue"));
        Page<OtherIssue> page = new Page<>(current, size);
        Page<OtherIssue> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public OtherIssue getDetail(Long id) {
        OtherIssue issue = getById(id);
        if (issue == null) throw new BusinessException("其他出库单不存在");
        List<OtherIssueItem> items = itemMapper.selectList(
                new QueryWrapper<OtherIssueItem>().eq("issue_id", id).orderByAsc("line_no"));
        issue.setItems(items);
        return issue;
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submitForApproval(Long id) {
        OtherIssue bill = getById(id);
        if (bill == null) throw new BusinessException("其他出库单不存在");
        super.submitForApproval(bill, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(OtherIssue issue) {
        Long id = issue.getIssueId();
        List<OtherIssueItem> items = itemMapper.selectList(
                new QueryWrapper<OtherIssueItem>().eq("issue_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("其他出库单没有明细，请至少添加一行物料后再提交");
        }

        if (issue.getWarehouseId() == null) {
            throw new BusinessException("出库仓库不能为空，请选择出库仓库");
        }
        Warehouse wh = warehouseService.getById(issue.getWarehouseId());
        if (wh == null) {
            throw new BusinessException("关联仓库不存在(warehouseId=" + issue.getWarehouseId() + ")，请重新选择仓库");
        }
        String whName = wh.getStockName() != null ? wh.getStockName() : ("ID:" + issue.getWarehouseId());

        for (int i = 0; i < items.size(); i++) {
            OtherIssueItem item = items.get(i);
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
            // 多单位（2026-11-10）：库存校验必须按「基本单位数量」，辅单位录入时行数量≠库存口径，
            // 否则正常出库会被误判"库存不足"（实测：5 件库存、出 20 盒=2 件被判不足）
            BigDecimal qty = item.effectiveBaseQuantity() != null
                    ? item.effectiveBaseQuantity()
                    : (item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]出库数量必须大于0");
            }

            boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
            if (!negativeAllowed) {
                Long stockId = item.getStockId() != null ? item.getStockId() : issue.getWarehouseId();
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(mat.getMaterialId(), stockId);
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(qty) < 0) {
                    throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]库存不足，仓库[" + whName + "]可用 "
                            + available.setScale(2, RoundingMode.HALF_UP)
                            + "，需出库 " + qty.setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

    }

    /** 审核：审批引擎防护 + 状态迁移全部由统一状态机执行，出库过账在 onApproved 钩子 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("OTHER_ISSUE", (Long) id) != null) {
            throw new BusinessException("其他出库单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：扣减库存（审核时先刷新最新核算成本） */
    @Override
    public void onApproved(OtherIssue issue) {
        Long id = issue.getIssueId();
        String approver = issue.getApprovedBy();
        List<OtherIssueItem> items = itemMapper.selectList(
                new QueryWrapper<OtherIssueItem>().eq("issue_id", id).orderByAsc("line_no"));
        if (items != null) {
            // === 审核时刷新avg_cost为最新核算成本（审核后冻结） ===
            for (OtherIssueItem item : items) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
            for (OtherIssueItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : issue.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    continue;
                }
                // 成本链路（2026-11-26）：出库前冻结实际成本，反审按该成本回补
                BigDecimal stockQty = stockQuantity(item.effectiveBaseQuantity(), quantity);
                BigDecimal outUnitCost = inventoryService.getOutUnitCost(
                        stockId, item.getBinId(), materialId, item.getBatchNo());
                item.setAvgCost(outUnitCost);
                itemMapper.updateById(item);
                inventoryService.deductStock(
                        stockId,
                        item.getBinId(),
                        materialId,
                        item.getBatchNo(),
                        stockQty,
                        issue.getIssueNo(),
                        "OTHER_OUT",
                        "OtherIssue",
                        issue.getIssueNo(),
                        approver);
            }
        }

        log.info("其他出库单审核: {} 审核人: {}", issue.getIssueNo(), approver);
    }

    /** 重置草稿：本单据状态守卫保留，状态迁移/下游校验由统一状态机执行，出库冲回在 onReverseApproved 钩子 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        OtherIssue bill = getById(id);
        if (bill == null) throw new BusinessException("其他出库单不存在");
        if (!"已生效".equals(bill.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<OtherIssue>().eq("issue_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 重置草稿影响：与 onApproved 严格镜像（补回出库库存） */
    @Override
    public void onReverseApproved(OtherIssue issue) {
        Long id = issue.getIssueId();
        String operator = issue.getApprovedBy();
        List<OtherIssueItem> items = itemMapper.selectList(
                new QueryWrapper<OtherIssueItem>().eq("issue_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (OtherIssueItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : issue.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    continue;
                }
                BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                        ? item.getAvgCost()
                        : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                inventoryService.addStock(
                        stockId,
                        item.getBinId(),
                        materialId,
                        item.getBatchNo(),
                        stockQuantity(item.effectiveBaseQuantity(), quantity),
                        reverseUnitCost,
                        issue.getIssueNo(),
                        "OTHER_OUT_REVERSE",
                        "OtherIssue",
                        issue.getIssueNo(),
                        operator);
            }
        }
        log.info("其他出库单重置草稿回冲完成: {}", issue.getIssueNo());
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        super.close(id, closer, reason);
    }

    /** 结案影响：保留业务执行态双写 */
    @Override
    public void onClosed(OtherIssue bill) {
        update(new UpdateWrapper<OtherIssue>().eq("issue_id", bill.getIssueId())
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
    public void onReopened(OtherIssue bill) {
        update(new UpdateWrapper<OtherIssue>().eq("issue_id", bill.getIssueId())
                .set("bus_status", null));
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        OtherIssue issue = getById(id);
        if (issue == null) throw new BusinessException("其他出库单不存在");
        if (!"草稿".equals(issue.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + issue.getStatus());
        }
        issue.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            issue.setRemark((issue.getRemark() != null ? issue.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(issue);
        log.info("其他出库单取消: {} 原因: {}", issue.getIssueNo(), reason);
    }

    @Override
    public String generateIssueNo() {
        String docType = "OTHER_ISSUE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "OOUT-" + dateStr + "-";
            QueryWrapper<OtherIssue> wrapper = new QueryWrapper<>();
            wrapper.likeRight("issue_no", prefix).orderByDesc("issue_no").last("LIMIT 1");
            OtherIssue last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getIssueNo() != null) {
                try {
                    seq = Integer.parseInt(last.getIssueNo().substring(prefix.length())) + 1;
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

    /** 作废影响：与重置草稿严格镜像（补回出库库存） */
    @Override
    public void onVoided(OtherIssue bill) {
        onReverseApproved(bill);
    }

    /**
     * 反作废影响（2026-11-09）：作废时已冲回库存，反作废恢复到「已生效」必须重新过账，
     * 否则账面库存少一笔（跨模块验收实锤）。与 onVoided 严格镜像。
     */
    @Override
    public void onUnvoided(OtherIssue bill) {
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
