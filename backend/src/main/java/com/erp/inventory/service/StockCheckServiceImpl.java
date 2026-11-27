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
import com.erp.inventory.entity.StockCheck;
import com.erp.inventory.entity.StockCheckItem;
import com.erp.inventory.mapper.StockCheckItemMapper;
import com.erp.inventory.mapper.StockCheckMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.UnitConversionService;
import com.erp.basedata.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;

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

public class StockCheckServiceImpl extends AbstractBillService<StockCheckMapper, StockCheck> implements StockCheckService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 · 库存模块推广批次）。
        // 键 = 前端筛选条件定义键（与库存盘点单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（由 MyBatis-Plus 元数据自动解析列名，防手写列名笔误）。
        // 盘点人：列是员工外键（handler_id），按名称筛的下拉传的是员工 ID → 翻译成 handlerId 属性。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("checkNo", new BillHeaderFilterSupport.Spec("checkNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("stockType", new BillHeaderFilterSupport.Spec("stockType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("handlerName", new BillHeaderFilterSupport.Spec("handlerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("checkDate", new BillHeaderFilterSupport.Spec("checkDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("bookAmount", new BillHeaderFilterSupport.Spec("bookAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("checkAmount", new BillHeaderFilterSupport.Spec("checkAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("profitLossAmount", new BillHeaderFilterSupport.Spec("profitLossAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("stockCheck", StockCheck.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "attachment_urls",
            "book_amount",
            "check_amount",
            "check_date",
            "check_id",
            "check_no",
            "created_at",
            "created_by",
            "currency_id",
            "exchange_rate",
            "handler_id",
            "line_no",
            "org_code",
            "profit_loss_amount",
            "remark",
            "status",
            "stock_type",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "warehouse_id"
        );

    private final StockCheckItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final MaterialCostService materialCostService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final UnitConversionService unitConversionService;
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

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "库存盘点单"; }
    @Override protected String getApprovalBillType() { return "STOCK_CHECK"; }
    @Override protected String getBillNo(StockCheck b) { return b.getCheckNo(); }
    @Override protected Object getId(StockCheck b) { return b.getCheckId(); }
    @Override protected String getStatus(StockCheck b) { return b.getStatus(); }
    @Override protected void setStatus(StockCheck b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(StockCheck b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(StockCheck b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(StockCheck b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(StockCheck b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(StockCheck b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(StockCheck b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(StockCheck b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(StockCheck b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(StockCheck b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(StockCheck b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(StockCheck b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(StockCheck b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(StockCheck b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(StockCheck bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getCheckNo());
    }

    @Override
    @Transactional
    public void saveCheckWithItems(StockCheck check, List<StockCheckItem> items) {
        boolean isNew = check.getCheckId() == null;

        if (!isNew) {
            StockCheck existing = getById(check.getCheckId());
            if (existing != null) {
                String st = existing.getStatus();
                if ("已生效".equals(st) || "已关闭".equals(st) || "已取消".equals(st)) {
                    throw new BusinessException("当前状态(" + st + ")不可修改，如需修改请先重置草稿");
                }
            }
        }

        if (check.getCheckNo() == null || check.getCheckNo().isBlank()) {
            check.setCheckNo(generateCheckNo());
        }

        // 保存=草稿：前端新增页送的是空串而非 null，必须按空处理，否则状态会落成空字符串
        if (check.getStatus() == null || check.getStatus().isBlank()) check.setStatus("草稿");
        // 单据处于草稿态时审批状态只能是"草稿"（"待审核"是提交后的状态，否则草稿单会误入工作台待审批待办）
        if (check.getApprovalStatus() == null || check.getApprovalStatus().isBlank()
                || ("草稿".equals(check.getStatus()) && "待审核".equals(check.getApprovalStatus()))) {
            check.setApprovalStatus("草稿");
        }
        if (check.getCheckDate() == null) check.setCheckDate(LocalDate.now());
        if (check.getStockType() == null) check.setStockType("动态盘点");
        if (check.getExchangeRate() == null) check.setExchangeRate(BigDecimal.ONE);
        if (check.getBookAmount() == null) check.setBookAmount(BigDecimal.ZERO);
        if (check.getCheckAmount() == null) check.setCheckAmount(BigDecimal.ZERO);
        if (check.getProfitLossAmount() == null) check.setProfitLossAmount(BigDecimal.ZERO);

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<StockCheckItem> validItems = new ArrayList<>();
            for (StockCheckItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        recalculateCheck(check, items);

        if (isNew) {
            save(check);
        } else {
            updateById(check);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<StockCheckItem>().eq("check_id", check.getCheckId()));
            int lineNo = 1;
            for (StockCheckItem item : items) {
                item.setCheckItemId(null);
                item.setCheckId(check.getCheckId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                // === 回填最新核算成本 ===
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                }
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("库存盘点单保存成功: {} 状态: {} 盘点金额: {}", check.getCheckNo(), check.getStatus(), check.getCheckAmount());
    }

    /**
     * 计算明细盈亏:
     * diff = checkQuantity - bookQuantity
     * diff > 0 盘盈; diff < 0 盘亏; diff == 0 盘平
     */
    private void calculateItemAmount(StockCheckItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal bookQuantity = item.getBookQuantity() != null ? item.getBookQuantity() : BigDecimal.ZERO;
        BigDecimal checkQuantity = item.getCheckQuantity() != null ? item.getCheckQuantity() : BigDecimal.ZERO;

        item.setUnitPrice(unitPrice);
        item.setBookQuantity(bookQuantity);
        item.setCheckQuantity(checkQuantity);

        // 含税价 / 不含税价 两列（落库）：盘点单无税率列，二者均等于盘点单价（口径与全站一致、占位列）
        item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));

        BigDecimal bookAmount = bookQuantity.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal checkAmount = checkQuantity.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP);
        item.setBookAmount(bookAmount);
        item.setCheckAmount(checkAmount);
        // 价税合计 = 含税价 × 盘点数量（含税价=不含税价=盘点单价，故等于盘点金额，2026-11-23 补齐）
        item.setNetAmount(item.getPriceWithTax().multiply(checkQuantity).setScale(SCALE, RoundingMode.HALF_UP));

        BigDecimal diff = checkQuantity.subtract(bookQuantity);
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            item.setProfitQuantity(diff);
            item.setProfitAmount(diff.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP));
            item.setLossQuantity(BigDecimal.ZERO);
            item.setLossAmount(BigDecimal.ZERO);
            item.setCheckResult("盘盈");
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal lossQty = diff.negate();
            item.setLossQuantity(lossQty);
            item.setLossAmount(lossQty.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP));
            item.setProfitQuantity(BigDecimal.ZERO);
            item.setProfitAmount(BigDecimal.ZERO);
            item.setCheckResult("盘亏");
        } else {
            item.setProfitQuantity(BigDecimal.ZERO);
            item.setProfitAmount(BigDecimal.ZERO);
            item.setLossQuantity(BigDecimal.ZERO);
            item.setLossAmount(BigDecimal.ZERO);
            item.setCheckResult("盘平");
        }
    }

    private void recalculateCheck(StockCheck check, List<StockCheckItem> items) {
        BigDecimal totalBook = BigDecimal.ZERO;
        BigDecimal totalCheck = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        if (items != null) {
            for (StockCheckItem item : items) {
                calculateItemAmount(item);
                totalBook = totalBook.add(item.getBookAmount());
                totalCheck = totalCheck.add(item.getCheckAmount());
                totalNet = totalNet.add(item.getNetAmount() != null ? item.getNetAmount() : BigDecimal.ZERO);
            }
        }

        check.setBookAmount(totalBook.setScale(SCALE, RoundingMode.HALF_UP));
        check.setCheckAmount(totalCheck.setScale(SCALE, RoundingMode.HALF_UP));
        check.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        check.setProfitLossAmount(
                check.getCheckAmount().subtract(check.getBookAmount()).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 盘点差异的库存记账数量：明细没有「数量」列，折算快照不会自动生成，
     * 这里按本行所选单位把盘盈/盘亏数量折算成基本单位数量后再入/出库。
     * 换算率优先用行上快照，没有则按基础资料维护值现算；查不到时按 1 处理（与旧版一致）。
     */
    private BigDecimal stockQuantity(StockCheckItem item, BigDecimal differenceQuantity) {
        if (differenceQuantity == null) return BigDecimal.ZERO;
        BigDecimal rate = item.getConversionRate();
        if (rate == null && item.getMaterialId() != null) {
            Material material = materialService.getById(item.getMaterialId());
            rate = unitConversionService.resolveRate(material, item.getUnitId());
        }
        if (rate == null) rate = BigDecimal.ONE;
        return differenceQuantity.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public PageResult<StockCheck> pageQuery(int current, int size, String keyword, String status,
                                           String startDate, String endDate, String employeeId,
                                           String sortBy, String sortOrder, String customSearch) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, java.util.Collections.emptyMap());
    }

    @Override
    public PageResult<StockCheck> pageQuery(int current, int size, String keyword, String status,
                                           String startDate, String endDate, String employeeId,
                                           String sortBy, String sortOrder, String customSearch,
                                           java.util.Map<String, String> headerFilters) {
        QueryWrapper<StockCheck> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, StockCheck.class);
        // 关键词（本页真实列 check_no）——多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "check_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 check_date）；员工=经办人（本页真实列 handler_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "check_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "handler_id");
        // 表头字段过滤（2026-10-14 列表页筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "stockCheck", headerFilters);
        // 数据权限（按角色 data_scope 统一数据权限）：仅本人/本部门/全部数据，替代旧 SecurityUtils.getCreatedByFilter
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "stock_check", customSearch, CustomFieldSearchHelper.idColumnOf("stock_check"));
        Page<StockCheck> page = new Page<>(current, size);
        Page<StockCheck> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public StockCheck getDetail(Long id) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        List<StockCheckItem> items = itemMapper.selectList(
                new QueryWrapper<StockCheckItem>().eq("check_id", id).orderByAsc("line_no"));
        check.setItems(items);
        return check;
    }

    @Override
    @Transactional
    public void startCheck(Long id) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        if (!"草稿".equals(check.getStatus())) {
            throw new BusinessException("只有草稿状态可开始盘点，当前状态: " + check.getStatus());
        }
        check.setStatus("盘点中");
        updateById(check);
        log.info("盘点单开始盘点: {}", check.getCheckNo());
    }

    @Override
    @Transactional
    public void finishCheck(Long id) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        if (!"盘点中".equals(check.getStatus())) {
            throw new BusinessException("只有盘点中状态可完成盘点，当前状态: " + check.getStatus());
        }
        check.setStatus("已盘点");
        updateById(check);
        log.info("盘点单完成盘点: {}", check.getCheckNo());
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submitForApproval(Long id) {
        StockCheck bill = getById(id);
        if (bill == null) throw new BusinessException("库存盘点单不存在");
        // 盘点业务态「已盘点」提交时归一为草稿入参（不改库），后续状态迁移仍由统一状态机完成
        if ("已盘点".equals(bill.getStatus())) {
            bill.setStatus("草稿");
        }
        super.submitForApproval(bill, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(StockCheck check) {
        Long id = check.getCheckId();
        List<StockCheckItem> items = itemMapper.selectList(
                new QueryWrapper<StockCheckItem>().eq("check_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("盘点单没有明细，请至少添加一行物料后再提交");
        }

        if (check.getWarehouseId() == null) {
            throw new BusinessException("盘点仓库不能为空，请选择盘点仓库");
        }
        if (warehouseService.getById(check.getWarehouseId()) == null) {
            throw new BusinessException("关联仓库不存在(warehouseId=" + check.getWarehouseId() + ")，请重新选择仓库");
        }

        for (int i = 0; i < items.size(); i++) {
            StockCheckItem item = items.get(i);
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
            BigDecimal qty = item.getCheckQuantity() != null ? item.getCheckQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]盘点数量不能为负数");
            }
        }

    }

    /** 审核：审批引擎防护 + 状态迁移全部由统一状态机执行，盈亏过账在 onApproved 钩子 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("STOCK_CHECK", (Long) id) != null) {
            throw new BusinessException("库存盘点单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：盘盈入库 + 盘亏出库（审核时先刷新最新核算成本） */
    @Override
    public void onApproved(StockCheck check) {
        Long id = check.getCheckId();
        String approver = check.getApprovedBy();
        List<StockCheckItem> items = itemMapper.selectList(
                new QueryWrapper<StockCheckItem>().eq("check_id", id).orderByAsc("line_no"));
        if (items != null) {
            // 成本链路（2026-11-26）：审核时冻结当前库存实际成本；盘盈按该成本入库，盘亏按该成本出库，
            // 反审按冻结成本回冲，避免“单据单价≠库存成本”造成金额失真。
            for (StockCheckItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : check.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal currentCost = BigDecimal.ZERO;
                if (stockId != null && materialId != null) {
                    currentCost = inventoryService.getOutUnitCost(
                            stockId, item.getBinId(), materialId, item.getBatchNo());
                }
                if (currentCost.compareTo(BigDecimal.ZERO) <= 0) {
                    BigDecimal latest = materialCostService.getLatestCalcCost(materialId);
                    if (latest != null && latest.compareTo(BigDecimal.ZERO) > 0) {
                        currentCost = latest;
                    } else if (item.getUnitPrice() != null) {
                        currentCost = item.getUnitPrice();
                    }
                }
                item.setAvgCost(currentCost);
                itemMapper.updateById(item);
            }
            for (StockCheckItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : check.getWarehouseId();
                Long materialId = item.getMaterialId();
                if (stockId == null || materialId == null) {
                    continue;
                }
                String result = item.getCheckResult();
                BigDecimal unitCost = item.getAvgCost() != null ? item.getAvgCost() : BigDecimal.ZERO;
                if ("盘盈".equals(result)) {
                    BigDecimal profitQty = item.getProfitQuantity() != null ? item.getProfitQuantity() : BigDecimal.ZERO;
                    inventoryService.addStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQuantity(item, profitQty), unitCost,
                            check.getCheckNo(), "STOCK_CHECK_PROFIT",
                            "StockCheck", check.getCheckNo(), approver);
                } else if ("盘亏".equals(result)) {
                    BigDecimal lossQty = item.getLossQuantity() != null ? item.getLossQuantity() : BigDecimal.ZERO;
                    inventoryService.deductStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQuantity(item, lossQty),
                            check.getCheckNo(), "STOCK_CHECK_LOSS",
                            "StockCheck", check.getCheckNo(), approver);
                }
                // 盘平: 跳过
            }
        }

        log.info("库存盘点单审核: {} 审核人: {}", check.getCheckNo(), approver);
    }

    /** 重置草稿：本单据状态守卫保留，状态迁移/下游校验由统一状态机执行，盈亏冲回在 onReverseApproved 钩子 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        StockCheck bill = getById(id);
        if (bill == null) throw new BusinessException("库存盘点单不存在");
        if (!"已生效".equals(bill.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<StockCheck>().eq("check_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 重置草稿影响：与 onApproved 严格镜像（盘盈扣减 / 盘亏增加） */
    @Override
    public void onReverseApproved(StockCheck check) {
        Long id = check.getCheckId();
        String operator = check.getApprovedBy();
        List<StockCheckItem> items = itemMapper.selectList(
                new QueryWrapper<StockCheckItem>().eq("check_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (StockCheckItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : check.getWarehouseId();
                Long materialId = item.getMaterialId();
                if (stockId == null || materialId == null) {
                    continue;
                }
                String result = item.getCheckResult();
                if ("盘盈".equals(result)) {
                    BigDecimal profitQty = item.getProfitQuantity() != null ? item.getProfitQuantity() : BigDecimal.ZERO;
                    BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                            ? item.getAvgCost()
                            : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                    inventoryService.deductStockAtCost(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQuantity(item, profitQty), reverseUnitCost,
                            check.getCheckNo(), "STOCK_CHECK_PROFIT_REVERSE",
                            "StockCheck", check.getCheckNo(), operator);
                } else if ("盘亏".equals(result)) {
                    BigDecimal lossQty = item.getLossQuantity() != null ? item.getLossQuantity() : BigDecimal.ZERO;
                    BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                            ? item.getAvgCost()
                            : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                    inventoryService.addStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            stockQuantity(item, lossQty), reverseUnitCost,
                            check.getCheckNo(), "STOCK_CHECK_LOSS_REVERSE",
                            "StockCheck", check.getCheckNo(), operator);
                }
            }
        }
        log.info("库存盘点单重置草稿回冲完成: {}", check.getCheckNo());
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        super.close(id, closer, reason);
    }

    /** 结案影响：保留业务执行态双写 */
    @Override
    public void onClosed(StockCheck bill) {
        update(new UpdateWrapper<StockCheck>().eq("check_id", bill.getCheckId())
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
    public void onReopened(StockCheck bill) {
        update(new UpdateWrapper<StockCheck>().eq("check_id", bill.getCheckId())
                .set("bus_status", null));
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        if (!"草稿".equals(check.getStatus()) && !"盘点中".equals(check.getStatus())) {
            throw new BusinessException("只有草稿或盘点中状态可取消，当前状态: " + check.getStatus());
        }
        check.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            check.setRemark((check.getRemark() != null ? check.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(check);
        log.info("库存盘点单取消: {} 原因: {}", check.getCheckNo(), reason);
    }

    @Override
    public String generateCheckNo() {
        String docType = "STOCK_CHECK";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "INV-" + dateStr + "-";
            QueryWrapper<StockCheck> wrapper = new QueryWrapper<>();
            wrapper.likeRight("check_no", prefix).orderByDesc("check_no").last("LIMIT 1");
            StockCheck last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getCheckNo() != null) {
                try {
                    seq = Integer.parseInt(last.getCheckNo().substring(prefix.length())) + 1;
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

    /** 作废前真实业务状态（盘点中/已盘点按状态工具会归为已生效类，但未过账，不能冲回库存） */
    private static final ThreadLocal<String> VOID_PRE_STATUS = new ThreadLocal<>();

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行，冲回在 onVoided 钩子 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        StockCheck bill = getById(id);
        if (bill != null) VOID_PRE_STATUS.set(bill.getStatus());
        try {
            super.voidBill(id, operator, reason);
        } finally {
            VOID_PRE_STATUS.remove();
        }
    }

    /** 作废影响：仅已生效单据已过账需冲回；盘点中/已盘点尚未过账，不冲回 */
    @Override
    public void onVoided(StockCheck bill) {
        if ("已生效".equals(VOID_PRE_STATUS.get())) {
            onReverseApproved(bill);
        }
    }

    /**
     * 反作废影响（2026-11-09）：作废时已冲回盘点盈亏库存，反作废恢复到「已生效」必须重新过账，
     * 否则盘盈盘亏结果丢失（跨模块验收实锤）。与 onVoided 严格镜像。
     */
    @Override
    public void onUnvoided(StockCheck bill) {
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
