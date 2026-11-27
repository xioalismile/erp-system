package com.erp.subcontract.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.inbound.entity.SubcontractInboundItem;
import com.erp.subcontract.inbound.service.SubcontractInboundService;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.order.mapper.SubcontractOrderItemMapper;
import com.erp.subcontract.order.mapper.SubcontractOrderMapper;
import com.erp.subcontract.pick.entity.SubcontractPick;
import com.erp.subcontract.pick.entity.SubcontractPickItem;
import com.erp.subcontract.pick.service.SubcontractPickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import com.erp.common.util.CustomFieldSearchHelper;
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

public class SubcontractOrderServiceImpl extends AbstractBillService<SubcontractOrderMapper, SubcontractOrder>
        implements SubcontractOrderService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广 · 委外模块批次）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 供应商：按 ID 精确（前端下拉传主档 ID），Spec.column 写外键属性名 supplierId。
        // 注意：本单据状态列是数字（0 草稿/1 待审核/2 已生效/3 已下达/5 已完工/6 已结案/7 已作废），
        // 必须用 EQ_LONG（值解析成整数再精确比较）——EQ_STRING 会拿字符串比 integer 列，PG 直接报类型错。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("processItem", new BillHeaderFilterSupport.Spec("processItem", BillHeaderFilterSupport.Kind.TEXT));
        f.put("materialCode", new BillHeaderFilterSupport.Spec("materialCode", BillHeaderFilterSupport.Kind.TEXT));
        f.put("materialName", new BillHeaderFilterSupport.Spec("materialName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyCode", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（状态 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("orderDate", new BillHeaderFilterSupport.Spec("orderDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("expectDate", new BillHeaderFilterSupport.Spec("expectDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("actualDate", new BillHeaderFilterSupport.Spec("actualDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("finishedQuantity", new BillHeaderFilterSupport.Spec("finishedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("unitPrice", new BillHeaderFilterSupport.Spec("unitPrice", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxRate", new BillHeaderFilterSupport.Spec("taxRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("netAmount", new BillHeaderFilterSupport.Spec("netAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("subcontractOrder", SubcontractOrder.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "actual_date",
            "base_net_amount",
            "base_tax_amount",
            "base_total_amount",
            "bom_id",
            "close_status",
            "create_by",
            "create_time",
            "created_at",
            "currency_code",
            "exchange_rate",
            "expect_date",
            "finished_quantity",
            "id",
            "net_amount",
            "order_date",
            "order_id",
            "order_no",
            "process_item",
            "remark",
            "source_bill_no",
            "source_bill_type",
            "status",
            "supplier_id",
            "supplier_name",
            "tax_amount",
            "tax_rate",
            "total_amount",
            "total_quantity",
            "unit_price",
            "update_time",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SubcontractOrderItemMapper itemMapper;
    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final SubcontractPickService subcontractPickService;
    private final SubcontractInboundService subcontractInboundService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final MaterialService materialService;
    /** 设置中心业务规则校验器（含税业务开关 / 默认计税方式） */
    private final SysParamValidator sysParamValidator;

    /** 统一数据权限解析件：按角色 sys_role.data_scope（仅本人/本部门/全部）过滤 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游单据扫描件：删除/作废/重置草稿前的"下游已生效单据"校验（与销售/采购同口径） */
    @Autowired
    private BillRelationService billRelationService;

    public SubcontractOrderServiceImpl(SubcontractOrderItemMapper itemMapper,
                                       BomMapper bomMapper,
                                       BomItemMapper bomItemMapper,
                                       @Lazy SubcontractPickService subcontractPickService,
                                       @Lazy SubcontractInboundService subcontractInboundService,
                                       SysSerialNoConfigService sysSerialNoConfigService,
                                       SupplierService supplierService,
                                       MaterialService materialService,
                                       SysParamValidator sysParamValidator) {
        this.sysParamValidator = sysParamValidator;
        this.itemMapper = itemMapper;
        this.bomMapper = bomMapper;
        this.bomItemMapper = bomItemMapper;
        this.subcontractPickService = subcontractPickService;
        this.subcontractInboundService = subcontractInboundService;
        this.sysSerialNoConfigService = sysSerialNoConfigService;
        this.supplierService = supplierService;
        this.materialService = materialService;
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "委外加工单"; }
    @Override protected String getApprovalBillType() { return "SUBCONTRACT_ORDER"; }
    @Override protected String getBillNo(SubcontractOrder b) { return b.getOrderNo(); }
    @Override protected Object getId(SubcontractOrder b) { return b.getId(); }
    @Override protected String getStatus(SubcontractOrder b) { return BillStatusUtils.toString(b.getStatus()); }
    @Override protected void setStatus(SubcontractOrder b, String s) { b.setStatus(BillStatusUtils.toNumber(s)); }
    @Override protected Integer getCloseStatus(SubcontractOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SubcontractOrder b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SubcontractOrder b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SubcontractOrder b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SubcontractOrder b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SubcontractOrder b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SubcontractOrder b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SubcontractOrder b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SubcontractOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SubcontractOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SubcontractOrder b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SubcontractOrder b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SubcontractOrder b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SubcontractOrder bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getOrderNo());
    }

    /** 结案影响：明细行同步置"明细结案"（与结案动作同事务） */
    @Override
    public void onClosed(SubcontractOrder bill) {
        List<SubcontractOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractOrderItem>().eq("order_id", bill.getId()));
        if (items != null) {
            for (SubcontractOrderItem item : items) {
                item.setDetailCloseStatus(1);
                itemMapper.updateById(item);
            }
        }
    }

    /** 反结案影响：明细行恢复"未结案"（与 onClosed 严格镜像） */
    @Override
    public void onReopened(SubcontractOrder bill) {
        List<SubcontractOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractOrderItem>().eq("order_id", bill.getId()));
        if (items != null) {
            for (SubcontractOrderItem item : items) {
                item.setDetailCloseStatus(0);
                itemMapper.updateById(item);
            }
        }
    }

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /**
     * 由表头「计税方式」解析明细计税类型（口径对齐销售订单试点）。
     * 含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价；
     * 设置中心「启用含税业务」总开关关闭时，一律按「不计税」。
     */
    private int resolveHeaderTaxType(SubcontractOrder order) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = order != null ? order.getTaxCalcMethod() : null;
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    @Override
    @Transactional
    public void saveOrderWithItems(SubcontractOrder order, List<SubcontractOrderItem> items) {
        boolean isNew = order.getId() == null;

        if (!isNew) {
            SubcontractOrder existing = getById(order.getId());
            if (existing != null) {
                Integer st = existing.getStatus();
                if (st != null && (st == 2 || st == 3 || st == 4 || st == 5 || st == 6 || st == 7)) {
                    throw new BusinessException("当前状态不可修改，如需修改请先重置草稿");
                }
            }
        }

        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号）
        if (isNew && (order.getOrderNo() == null || order.getOrderNo().isBlank())) {
            order.setOrderNo(generateOrderNo());
        }

        if (order.getStatus() == null) order.setStatus(0);
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if (order.getStatus() != null && order.getStatus() == 0 && order.getApprovalStatus() == null) {
            order.setApprovalStatus("草稿");
        }
        if (order.getCloseStatus() == null) order.setCloseStatus(0);
        if (order.getExchangeRate() == null) order.setExchangeRate(BigDecimal.ONE);
        if (order.getTotalQuantity() == null) order.setTotalQuantity(BigDecimal.ZERO);
        if (order.getFinishedQuantity() == null) order.setFinishedQuantity(BigDecimal.ZERO);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SubcontractOrderItem> validItems = new ArrayList<>();
            for (SubcontractOrderItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        recalculateOrder(order, items);

        if (isNew) {
            save(order);
        } else {
            updateById(order);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SubcontractOrderItem>().eq("order_id", order.getId()));
            for (SubcontractOrderItem item : items) {
                item.setId(null);
                item.setOrderId(order.getId());
                if (item.getFinishedQty() == null) item.setFinishedQty(BigDecimal.ZERO);
                if (item.getDetailCloseStatus() == null) item.setDetailCloseStatus(0);
                calculateItemAmount(item, resolveHeaderTaxType(order), nvl(order.getTaxRate()));
                itemMapper.insert(item);
            }
        }

        log.info("委外加工单保存成功: {} 状态: {}", order.getOrderNo(), order.getStatus());
    }

    /**
     * 明细金额与含税/不含税价换算（口径对齐销售订单试点）：表头「计税方式」决定「单价」口径，
     * 换算另一列（落库 price_with_tax / price_without_tax）；金额一律按「不含税」口径。
     * 注：本页明细为发料清单（前端不填单价），unitPrice 通常为空 → 金额为 0，两列按行单价换算。
     */
    private void calculateItemAmount(SubcontractOrderItem item, int taxType, BigDecimal headerTaxRate) {
        BigDecimal unitPrice = nvl(item.getUnitPrice());
        BigDecimal quantity = nvl(item.getQuantity());
        BigDecimal taxRate = headerTaxRate != null ? headerTaxRate : nvl(item.getTaxRate());
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTaxRate(taxRate);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal priceWithTax;
        BigDecimal priceWithoutTax;
        if (inclusive) {
            priceWithTax = unitPrice;
            priceWithoutTax = factor.compareTo(BigDecimal.ZERO) != 0
                    ? unitPrice.divide(factor, 6, RoundingMode.HALF_UP) : unitPrice;
        } else if (noTax) {
            priceWithTax = unitPrice;
            priceWithoutTax = unitPrice;
        } else {
            priceWithoutTax = unitPrice;
            priceWithTax = unitPrice.multiply(factor).setScale(6, RoundingMode.HALF_UP);
        }
        item.setPriceWithTax(priceWithTax.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(priceWithoutTax.setScale(6, RoundingMode.HALF_UP));

        BigDecimal amount = inclusive
                ? unitPrice.multiply(quantity).divide(factor, SCALE, RoundingMode.HALF_UP)
                : unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（本单无折扣，统一口径见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, unitPrice, quantity, taxRate, BigDecimal.ZERO, inclusive, noTax);
        item.setTaxAmount(taxAmount);
        item.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * 表头汇总（口径对齐销售订单试点）：
     * 加工费总额按「委外数量 × 加工单价」经表头「计税方式」换算为不含税金额；
     * 税额 = 不含税金额 × 税率（不计税恒 0）；价税合计 = 不含税金额 + 税额；本位币按汇率折算。
     */
    private void recalculateOrder(SubcontractOrder order, List<SubcontractOrderItem> items) {
        int headerTaxType = resolveHeaderTaxType(order);
        BigDecimal headerTaxRate = nvl(order.getTaxRate());
        order.setTaxRate(headerTaxRate);

        BigDecimal grossAmount = nvl(order.getTotalQuantity()).multiply(nvl(order.getUnitPrice()));
        boolean noTax = headerTaxType == TAX_TYPE_NO_TAX;
        boolean inclusive = headerTaxType == TAX_TYPE_INCLUSIVE;
        BigDecimal factor = BigDecimal.ONE.add(headerTaxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal totalAmount = inclusive
                ? grossAmount.divide(factor, SCALE, RoundingMode.HALF_UP)
                : grossAmount.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalTaxAmount = noTax ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                : totalAmount.multiply(headerTaxRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal totalNetAmount = totalAmount.add(totalTaxAmount).setScale(SCALE, RoundingMode.HALF_UP);

        order.setTotalAmount(totalAmount);
        order.setTaxAmount(totalTaxAmount);
        order.setNetAmount(totalNetAmount);

        BigDecimal totalQuantity = BigDecimal.ZERO;
        if (items != null) {
            for (SubcontractOrderItem item : items) {
                calculateItemAmount(item, headerTaxType, headerTaxRate);
                totalQuantity = totalQuantity.add(nvl(item.getQuantity()));
            }
        }

        order.setTotalQuantity(totalQuantity);

        BigDecimal exchangeRate = nvl(order.getExchangeRate());
        if (exchangeRate.compareTo(BigDecimal.ZERO) == 0) exchangeRate = BigDecimal.ONE;
        order.setBaseTotalAmount(totalAmount.multiply(exchangeRate).setScale(SCALE, RoundingMode.HALF_UP));
        order.setBaseTaxAmount(totalTaxAmount.multiply(exchangeRate).setScale(SCALE, RoundingMode.HALF_UP));
        order.setBaseNetAmount(totalNetAmount.multiply(exchangeRate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<SubcontractOrder> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                  String startDate, String endDate, String supplierId) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, startDate, endDate, supplierId,
                null, java.util.Collections.emptyMap());
    }

    /** 老签名（仅表头字段过滤）委托新重载（customSearch 空 = 行为与改造前完全一致） */
    @Override
    public PageResult<SubcontractOrder> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                  String startDate, String endDate, String supplierId,
                                                  java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, startDate, endDate, supplierId, null, headerFilters);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤 + 自定义字段搜索」（2026-10-14 / 2026-11-24）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<SubcontractOrder> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                  String startDate, String endDate, String supplierId,
                                                  String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SubcontractOrder> wrapper = new QueryWrapper<>();
        // 排序：统一件 applySort（内部即 SortHelper 列名白名单），未传/非法时保留原有默认排序
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SubcontractOrder.class);
        // 关键词：单号 + 供应商名称，一律走 applyKeywordGroup 强制成组
        // （平铺 or() 会让命中第一列的行绕过后续状态筛选与数据权限）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "order_no", "supplier_name");
        // 委外日期区间（order_date 为 date 列，含端点，统一件 applyDateRange）
        ListQuerySupport.applyDateRange(wrapper, "order_date", startDate, endDate);
        // 委外供应商（统一入参 supplierId → supplier_id）
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 表头字段过滤（2026-10-14 筛选条件自定义）：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "subcontractOrder", headerFilters);
        // 数据权限（统一机制）：按角色 data_scope（仅本人/本部门/全部）过滤；无 Web 请求/全部数据时不加条件
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "subcontract_order", customSearch, CustomFieldSearchHelper.idColumnOf("subcontract_order"));
        Page<SubcontractOrder> page = new Page<>(current, size);
        Page<SubcontractOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SubcontractOrder getDetail(Long id) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        List<SubcontractOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractOrderItem>().eq("order_id", id).orderByAsc("id"));
        order.setItems(items);
        return order;
    }

    // ============ 统一状态机动作（全部 delegate 基类） ============

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        // 状态校验 + 严格校验 + 审批开关 + 审批任务创建全部由统一状态机执行
        super.submitForApproval(order, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（原 submitForApproval 校验整体迁移；保存草稿不触发） */
    @Override
    protected void validateStrictSubmit(SubcontractOrder order) {
        List<SubcontractOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractOrderItem>().eq("order_id", order.getId()));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有明细，请至少添加一行物料后再提交");
        }

        if (order.getSupplierId() == null) {
            throw new BusinessException("请选择委外供应商");
        }
        if (supplierService.getById(order.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + order.getSupplierId() + ")，请重新选择供应商");
        }

        for (int i = 0; i < items.size(); i++) {
            SubcontractOrderItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料ID不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matLabel = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                        + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
                throw new BusinessException("第" + lineNo + "行[" + matLabel.trim() + "]数量必须大于0");
            }
        }
    }

    /**
     * 覆盖基类 Object 签名：审批引擎回调（onApprovalApproved）在基类内部按 Object 签名调用，
     * 只有覆盖 Object 签名才能保证「绕过引擎直审」的防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直审
        // （由引擎回调 onApprovalApproved 触发的审核不受影响：引擎会先把任务置为已通过再回写单据）
        if (approvalRuntimeService.getRunningTask("SUBCONTRACT_ORDER", (Long) id) != null) {
            throw new BusinessException("委外加工单已提交审批流程，请由指定审批人在审批中心处理");
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
    public void reverseApprove(Long id, String operator) {
        SubcontractOrder bill = getById(id);
        if (bill == null) throw new BusinessException("委外加工单不存在");
        // 本单据特有守卫：只有原始状态"已生效(2)"可重置草稿（已下达/执行中/已完工 不允许回退）
        if (bill.getStatus() == null || bill.getStatus() != 2) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        // 状态迁移 + 下游校验 + 冲回钩子由统一状态机执行
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<SubcontractOrder> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        // 状态校验 + 终态守卫 + 下游校验 + 作废前状态记录 + 冲回钩子统一由基类执行
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 结案：统一状态机（已生效 + 未结案校验；明细结案在 onClosed 钩子） */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        super.close(id, closer, reason);
        // 本单据显示态特例：前端状态列用 6=已结案，close_status 仍由基类统一维护
        UpdateWrapper<SubcontractOrder> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("status", 6);
        update(uw);
        log.info("委外加工单结案: {} 操作人: {} 原因: {}", order.getOrderNo(), closer, reason);
    }

    /** 反结案：统一状态机（明细恢复在 onReopened 钩子） */
    @Override
    @Transactional
    public void unclose(Long id, String operator) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        super.reopen(id, operator);
        // 本单据显示态特例：反结案后回 5=已完工（close_status 仍由基类统一维护）
        UpdateWrapper<SubcontractOrder> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("status", 5);
        update(uw);
        log.info("委外加工单反结案: {} 操作人: {}", order.getOrderNo(), operator);
    }

    /** 作废：保留"仅草稿可作废"的原口径，状态迁移仍走统一状态机 */
    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException("只有草稿状态可作废，当前状态: " + order.getStatus());
        }
        voidBill(id, BillApprovalAdapter.currentSubmitter(), reason);
    }

    @Override
    @Transactional
    public void release(Long id) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        Integer st = order.getStatus();
        if (st == null || (st != 0 && st != 1 && st != 2)) {
            throw new BusinessException("当前状态不可下达，当前状态: " + st);
        }
        order.setStatus(3); // 已下达
        updateById(order);
        log.info("委外加工单下达: {}", order.getOrderNo());
    }

    @Override
    @Transactional
    public void complete(Long id) {
        SubcontractOrder order = getById(id);
        if (order == null) throw new BusinessException("委外加工单不存在");
        Integer st = order.getStatus();
        if (st == null || (st != 3 && st != 4)) {
            throw new BusinessException("只有已下达/执行中状态可完工，当前状态: " + st);
        }
        order.setStatus(5); // 已完工
        if (order.getActualDate() == null) {
            order.setActualDate(LocalDate.now());
        }
        updateById(order);
        log.info("委外加工单完工: {}", order.getOrderNo());
    }

    @Override
    public String generateOrderNo() {
        String docType = "SUBCONTRACT_ORDER";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SC-ORDER-" + dateStr + "-";
            QueryWrapper<SubcontractOrder> wrapper = new QueryWrapper<>();
            wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
            SubcontractOrder last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getOrderNo() != null) {
                try {
                    seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public List<SubcontractOrderItem> getBomMaterials(Long orderItemId) {
        SubcontractOrderItem orderItem = itemMapper.selectById(orderItemId);
        if (orderItem == null) {
            throw new BusinessException("委外加工单明细不存在");
        }

        Long bomId = orderItem.getBomId();
        if (bomId == null) {
            return new ArrayList<>();
        }

        Bom bom = bomMapper.selectById(bomId);
        if (bom == null) {
            return new ArrayList<>();
        }

        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bomId));

        List<SubcontractOrderItem> result = new ArrayList<>();
        for (BomItem bomItem : bomItems) {
            SubcontractOrderItem item = new SubcontractOrderItem();
            item.setMaterialId(bomItem.getMaterialId());
            item.setMaterialCode(bomItem.getMaterialCode());
            item.setMaterialName(bomItem.getMaterialName());
            item.setSpecification(bomItem.getSpecification());
            item.setQuantity(bomItem.getQuantity().multiply(orderItem.getQuantity()));
            result.add(item);
        }

        return result;
    }

    @Override
    @Transactional
    public SubcontractPick pushPick(Long orderId) {
        SubcontractOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("委外加工单不存在");
        }

        List<SubcontractOrderItem> orderItems = itemMapper.selectList(
                new QueryWrapper<SubcontractOrderItem>().eq("order_id", orderId).orderByAsc("id"));
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("委外加工单明细为空，无法下推发料单");
        }

        SubcontractPick pick = new SubcontractPick();
        pick.setOrderId(order.getId());
        pick.setOrderNo(order.getOrderNo());
        pick.setSupplierId(order.getSupplierId());
        pick.setSupplierName(order.getSupplierName());
        pick.setPickDate(LocalDate.now());
        pick.setStatus(0);

        List<SubcontractPickItem> pickItems = new ArrayList<>();
        for (SubcontractOrderItem orderItem : orderItems) {
            SubcontractPickItem pickItem = new SubcontractPickItem();
            pickItem.setOrderItemId(orderItem.getId());
            pickItem.setMaterialId(orderItem.getMaterialId());
            pickItem.setMaterialCode(orderItem.getMaterialCode());
            pickItem.setMaterialName(orderItem.getMaterialName());
            pickItem.setSpecification(orderItem.getSpecification());
            pickItem.setUnit(orderItem.getUnit());
            pickItem.setQuantity(orderItem.getQuantity());
            pickItems.add(pickItem);
        }

        subcontractPickService.savePickWithItems(pick, pickItems);
        log.info("委外加工单 {} 下推生成发料单 {}", order.getOrderNo(), pick.getPickNo());
        return pick;
    }

    @Override
    @Transactional
    public SubcontractInbound pushInbound(Long orderId) {
        SubcontractOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("委外加工单不存在");
        }

        List<SubcontractOrderItem> orderItems = itemMapper.selectList(
                new QueryWrapper<SubcontractOrderItem>().eq("order_id", orderId).orderByAsc("id"));
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("委外加工单明细为空，无法下推入库单");
        }

        SubcontractInbound inbound = new SubcontractInbound();
        inbound.setOrderId(order.getId());
        inbound.setOrderNo(order.getOrderNo());
        inbound.setSupplierId(order.getSupplierId());
        inbound.setSupplierName(order.getSupplierName());
        inbound.setInboundDate(LocalDate.now());
        inbound.setStatus(0);
        inbound.setProcessingFee(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);

        List<SubcontractInboundItem> inboundItems = new ArrayList<>();
        for (SubcontractOrderItem orderItem : orderItems) {
            SubcontractInboundItem inboundItem = new SubcontractInboundItem();
            inboundItem.setOrderItemId(orderItem.getId());
            inboundItem.setMaterialId(orderItem.getMaterialId());
            inboundItem.setMaterialCode(orderItem.getMaterialCode());
            inboundItem.setMaterialName(orderItem.getMaterialName());
            inboundItem.setSpecification(orderItem.getSpecification());
            inboundItem.setUnit(orderItem.getUnit());
            inboundItem.setQuantity(orderItem.getQuantity());
            inboundItem.setProcessingFee(orderItem.getAmount() != null ? orderItem.getAmount() : BigDecimal.ZERO);
            inboundItems.add(inboundItem);
        }

        subcontractInboundService.saveInboundWithItems(inbound, inboundItems);
        log.info("委外加工单 {} 下推生成入库单 {}", order.getOrderNo(), inbound.getInboundNo());
        return inbound;
    }

    /**
     * 整单删除（2026-09-20）：状态守卫 + 下游校验 + 级联清理全部由统一状态机执行。
     */
    @Override
    @Transactional
    public void deleteOrder(Long id) {
        super.deleteBill(id);
    }
}
