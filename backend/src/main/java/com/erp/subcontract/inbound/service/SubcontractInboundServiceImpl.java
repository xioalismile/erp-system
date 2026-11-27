package com.erp.subcontract.inbound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.inventory.service.WarehouseService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.inbound.entity.SubcontractInboundItem;
import com.erp.subcontract.inbound.mapper.SubcontractInboundItemMapper;
import com.erp.subcontract.inbound.mapper.SubcontractInboundMapper;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.order.mapper.SubcontractOrderItemMapper;
import com.erp.subcontract.order.service.SubcontractOrderService;
import com.erp.subcontract.pick.entity.SubcontractPickItem;
import com.erp.subcontract.pick.mapper.SubcontractPickItemMapper;
import com.erp.subcontract.returnn.entity.SubcontractReturnItem;
import com.erp.subcontract.supplement.entity.SubcontractSupplementItem;
import com.erp.subcontract.supplement.mapper.SubcontractSupplementItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import com.erp.common.util.CustomFieldSearchHelper;

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

public class SubcontractInboundServiceImpl extends AbstractBillService<SubcontractInboundMapper, SubcontractInbound>
        implements SubcontractInboundService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广 · 委外模块批次）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键）；值 = 实体属性名（防手写列名笔误）。
        // 供应商：按 ID 精确（前端下拉传主档 ID），Spec.column 写外键属性名 supplierId。
        // 状态列是数字（0 草稿/1 待审核/2 已生效）→ EQ_LONG（EQ_STRING 拿字符串比 integer 列会报类型错）。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        f.put("inboundNo", new BillHeaderFilterSupport.Spec("inboundNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("inboundDate", new BillHeaderFilterSupport.Spec("inboundDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("processingFee", new BillHeaderFilterSupport.Spec("processingFee", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("subcontractInbound", SubcontractInbound.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "ap_no",
            "create_by",
            "create_time",
            "created_at",
            "id",
            "inbound_date",
            "inbound_id",
            "inbound_no",
            "order_id",
            "order_no",
            "processing_fee",
            "remark",
            "source_bill_no",
            "source_bill_type",
            "status",
            "supplier_id",
            "supplier_name",
            "total_quantity",
            "update_time",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SubcontractInboundItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final MaterialCostService materialCostService;
    private final SubcontractPickItemMapper subcontractPickItemMapper;
    private final SubcontractSupplementItemMapper subcontractSupplementItemMapper;
    private final SubcontractOrderService subcontractOrderService;
    private final SubcontractOrderItemMapper orderItemMapper;
    private final AccountsPayableMapper accountsPayableMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    /** 入库单质检（2026-11-07）：审核前必须完成质检、按处理方式拆库存 */
    private final com.erp.qc.service.InboundQcService inboundQcService;
    /** 质检不合格自动退货：生成委外退货单草稿（2026-11-07） */
    private final com.erp.subcontract.returnn.service.SubcontractReturnService subcontractReturnService;
    private final com.erp.subcontract.returnn.mapper.SubcontractReturnMapper subcontractReturnMapper;
    private final com.erp.subcontract.returnn.mapper.SubcontractReturnItemMapper subcontractReturnItemMapper;

    /** 统一数据权限解析件：按角色 sys_role.data_scope（仅本人/本部门/全部）过滤 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游单据扫描件：作废/重置草稿/删除前的"下游已生效单据"校验 */
    @Autowired
    private BillRelationService billRelationService;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "委外入库单"; }
    @Override protected String getApprovalBillType() { return "SUBCONTRACT_RECEIPT"; }
    @Override protected String getBillNo(SubcontractInbound b) { return b.getInboundNo(); }
    @Override protected Object getId(SubcontractInbound b) { return b.getId(); }
    @Override protected String getStatus(SubcontractInbound b) { return BillStatusUtils.toString(b.getStatus()); }
    @Override protected void setStatus(SubcontractInbound b, String s) { b.setStatus(BillStatusUtils.toNumber(s)); }
    @Override protected Integer getCloseStatus(SubcontractInbound b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SubcontractInbound b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SubcontractInbound b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SubcontractInbound b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SubcontractInbound b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SubcontractInbound b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SubcontractInbound b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SubcontractInbound b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SubcontractInbound b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SubcontractInbound b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SubcontractInbound b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SubcontractInbound b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SubcontractInbound b, String v) { b.setVoidReason(v); }

    /**
     * 下游引用校验：统一状态机在重置草稿/作废/删除前调用。
     * 本单据审核时自动生成的"应付账款单"属于本单自身的业务产物（重置/作废时由 onReverseApproved/onVoided 一并删除），
     * 不能当作外部下游引用拦截，故从结果中剔除；其余外部下游单据照常拦截。
     */
    @Override
    public List<String> listApprovedDownstreamBills(SubcontractInbound bill) {
        List<String> result = new ArrayList<>(
                billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getInboundNo()));
        result.removeIf(s -> s != null && s.startsWith("应付账款单:"));
        return result;
    }

    @Override
    @Transactional
    public void saveInboundWithItems(SubcontractInbound inbound, List<SubcontractInboundItem> items) {
        boolean isNew = inbound.getId() == null;

        if (!isNew) {
            SubcontractInbound existing = getById(inbound.getId());
            if (existing != null) {
                Integer st = existing.getStatus();
                if (st != null && st == 2) {
                    throw new BusinessException("当前状态不可修改，如需修改请先重置草稿");
                }
            }
        }

        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号，页面显示与实际不一致）
        if (isNew && (inbound.getInboundNo() == null || inbound.getInboundNo().isBlank())) {
            inbound.setInboundNo(generateInboundNo());
        }

        if (inbound.getStatus() == null) inbound.setStatus(0);
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if (inbound.getStatus() != null && inbound.getStatus() == 0 && inbound.getApprovalStatus() == null) {
            inbound.setApprovalStatus("草稿");
        }
        if (inbound.getCloseStatus() == null) inbound.setCloseStatus(0);
        if (inbound.getTotalQuantity() == null) inbound.setTotalQuantity(BigDecimal.ZERO);
        if (inbound.getProcessingFee() == null) inbound.setProcessingFee(BigDecimal.ZERO);
        if (inbound.getInboundDate() == null) inbound.setInboundDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SubcontractInboundItem> validItems = new ArrayList<>();
            for (SubcontractInboundItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        recalculateInbound(inbound, items);

        if (isNew) {
            save(inbound);
        } else {
            updateById(inbound);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SubcontractInboundItem>().eq("inbound_id", inbound.getId()));
            for (SubcontractInboundItem item : items) {
                item.setId(null);
                item.setInboundId(inbound.getId());
                if (item.getProcessingFee() == null) item.setProcessingFee(BigDecimal.ZERO);
                itemMapper.insert(item);
            }
        }

        log.info("委外入库单保存成功: {} 状态: {}", inbound.getInboundNo(), inbound.getStatus());
    }

    private void recalculateInbound(SubcontractInbound inbound, List<SubcontractInboundItem> items) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalProcessingFee = BigDecimal.ZERO;
        if (items != null) {
            for (SubcontractInboundItem item : items) {
                totalQuantity = totalQuantity.add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
                totalProcessingFee = totalProcessingFee.add(item.getProcessingFee() != null ? item.getProcessingFee() : BigDecimal.ZERO);
            }
        }
        inbound.setTotalQuantity(totalQuantity);
        inbound.setProcessingFee(totalProcessingFee);
    }

    @Override
    public PageResult<SubcontractInbound> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, java.util.Collections.emptyMap());
    }

    /** 老签名（仅表头字段过滤）委托新重载（customSearch 空 = 行为与改造前完全一致） */
    @Override
    public PageResult<SubcontractInbound> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                    java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤 + 自定义字段搜索」（2026-10-14 / 2026-11-24）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<SubcontractInbound> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                    String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SubcontractInbound> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SubcontractInbound.class);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("inbound_no", keyword);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 表头字段过滤（2026-10-14 筛选条件自定义）：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "subcontractInbound", headerFilters);
        // 数据权限（统一机制）：按角色 data_scope（仅本人/本部门/全部）过滤；无 Web 请求/全部数据时不加条件
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "subcontract_inbound", customSearch, CustomFieldSearchHelper.idColumnOf("subcontract_inbound"));
        Page<SubcontractInbound> page = new Page<>(current, size);
        Page<SubcontractInbound> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SubcontractInbound getDetail(Long id) {
        SubcontractInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("委外入库单不存在");
        List<SubcontractInboundItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractInboundItem>().eq("inbound_id", id).orderByAsc("id"));
        inbound.setItems(items);
        return inbound;
    }

    // ============ 统一状态机动作（全部 delegate 基类） ============

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SubcontractInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("委外入库单不存在");
        super.submitForApproval(inbound, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（原 submitForApproval 校验整体迁移；保存草稿不触发） */
    @Override
    protected void validateStrictSubmit(SubcontractInbound inbound) {
        List<SubcontractInboundItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractInboundItem>().eq("inbound_id", inbound.getId()));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有明细，请至少添加一行物料后再提交");
        }

        if (inbound.getSupplierId() == null) {
            throw new BusinessException("请选择委外供应商");
        }
        if (supplierService.getById(inbound.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + inbound.getSupplierId() + ")，请重新选择供应商");
        }
        if (inbound.getOrderId() != null && subcontractOrderService.getById(inbound.getOrderId()) == null) {
            throw new BusinessException("关联委外加工单不存在(orderId=" + inbound.getOrderId() + ")，请重新选择加工单");
        }

        for (int i = 0; i < items.size(); i++) {
            SubcontractInboundItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料ID不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            if (item.getWarehouseId() == null) {
                throw new BusinessException("第" + lineNo + "行仓库ID不能为空");
            }
            if (warehouseService.getById(item.getWarehouseId()) == null) {
                throw new BusinessException("第" + lineNo + "行仓库不存在(warehouseId=" + item.getWarehouseId() + ")，请重新选择仓库");
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
     * 覆盖基类 Object 签名：审批引擎回调在基类内部按 Object 签名调用，
     * 只有覆盖 Object 签名才能保证「绕过引擎直审」的防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SUBCONTRACT_RECEIPT", (Long) id) != null) {
            throw new BusinessException("委外入库单已提交审批流程，请由指定审批人在审批中心处理");
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
        SubcontractInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("委外入库单不存在");
        // 本单据特有守卫：只有原始状态"已生效(2)"可重置草稿
        if (inbound.getStatus() == null || inbound.getStatus() != 2) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + inbound.getStatus());
        }
        // 守卫①：质检不合格自动生成的委外退货单已生效时，必须先处理退货单
        for (com.erp.subcontract.returnn.entity.SubcontractReturn ret : listQcAutoReturns(inbound)) {
            if (ret.getStatus() != null && ret.getStatus() != 0) {
                throw new BusinessException("入库单质检自动生成的委外退货单 " + ret.getReturnNo()
                        + " 已生效，请先重置/作废该退货单后再重置本入库单");
            }
        }
        // 守卫②：已生成应付单且已有付款记录的不允许重置草稿
        assertNoPaidPayable(inbound, "重置草稿");
        // 状态迁移 + 下游校验 + 冲回钩子由统一状态机执行
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<SubcontractInbound> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        SubcontractInbound inbound = getById(id);
        if (inbound == null) throw new BusinessException("委外入库单不存在");
        // 已生效单据作废前，与重置草稿同款守卫（避免留下已付款应付单/已生效自动退货单）
        if (BillStatusUtils.isApprovedClass(inbound.getStatus())) {
            for (com.erp.subcontract.returnn.entity.SubcontractReturn ret : listQcAutoReturns(inbound)) {
                if (ret.getStatus() != null && ret.getStatus() != 0) {
                    throw new BusinessException("入库单质检自动生成的委外退货单 " + ret.getReturnNo()
                            + " 已生效，请先重置/作废该退货单后再作废本入库单");
                }
            }
            assertNoPaidPayable(inbound, "作废");
        }
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 审核通过影响：质检卡点 + 按合格/报废入库 + 回写加工单已完成量 + 生成加工费应付 + 质检不合格自动退货草稿 */
    @Override
    public void onApproved(SubcontractInbound inbound) {
        Long id = inbound.getId();
        String approver = inbound.getApprovedBy();

        // 质检卡点：开启质检的单据必须完成质检才能生效
        inboundQcService.assertInspectionComplete(com.erp.qc.service.InboundQcService.BILL_SUBCONTRACT, id);

        List<SubcontractInboundItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractInboundItem>().eq("inbound_id", id).orderByAsc("id"));
        boolean qcOn = inbound.getQcEnabled() != null && inbound.getQcEnabled() == 1;
        Long defaultScrapId = null;
        List<com.erp.subcontract.returnn.entity.SubcontractReturnItem> autoReturnItems = new ArrayList<>();
        // 成本链路（2026-11-26）：按加工单行汇总“净材料成本 = 发料 + 补料 - 退料”，
        // 入库时按该行计划数量折算单位材料成本，再加本行加工费，形成成品入库成本。
        Map<Long, BigDecimal> netMaterialCostByOrderItem = loadNetMaterialCost(inbound.getOrderId());
        if (items != null) {
            for (SubcontractInboundItem item : items) {
                Long warehouseId = item.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = nvl(item.getQuantity());
                if (materialId == null) {
                    throw new BusinessException("委外入库单明细缺少物料，无法审核");
                }
                if (warehouseId == null) {
                    throw new BusinessException("第" + itemLabel(item) + "未选择仓库，请选择仓库");
                }
                BigDecimal materialUnitCost = resolveMaterialUnitCost(item, netMaterialCostByOrderItem);
                BigDecimal feeUnitCost = resolveProcessingFeeUnitCost(inbound, item, quantity);
                BigDecimal unitCost = materialUnitCost.add(feeUnitCost).setScale(6, RoundingMode.HALF_UP);
                BigDecimal lineBaseQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : quantity;
                item.setUnitCost(unitCost);
                item.setMaterialCost(materialUnitCost.multiply(lineBaseQty).setScale(2, RoundingMode.HALF_UP));
                itemMapper.updateById(item);
                BigDecimal qualified = qcOn ? nvl(item.getQualifiedQuantity()) : quantity;
                BigDecimal rejected = qcOn ? nvl(item.getRejectedQuantity()) : BigDecimal.ZERO;
                String handling = qcOn ? normalizeHandling(item.getHandlingType(), rejected)
                        : com.erp.qc.service.InboundQcService.HANDLE_RELEASE;

                BigDecimal enterQty;
                BigDecimal scrapQty = BigDecimal.ZERO;
                Long scrapWarehouseId = null;
                if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)) {
                    enterQty = qualified;
                } else if (com.erp.qc.service.InboundQcService.HANDLE_SCRAP.equals(handling)) {
                    enterQty = qualified;
                    scrapQty = rejected;
                    scrapWarehouseId = item.getScrapWarehouseId();
                    if (scrapQty.compareTo(BigDecimal.ZERO) > 0 && scrapWarehouseId == null) {
                        if (defaultScrapId == null) defaultScrapId = inboundQcService.defaultScrapWarehouseId();
                        scrapWarehouseId = defaultScrapId;
                    }
                    if (scrapQty.compareTo(BigDecimal.ZERO) > 0 && scrapWarehouseId == null) {
                        throw new BusinessException("物料[" + itemLabel(item)
                                + "]选择进报废仓，但系统没有可用报废仓，请先在设置中心指定默认报废仓");
                    }
                } else {
                    enterQty = quantity;
                }
                if (enterQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.addStock(
                            warehouseId, null, materialId, item.getBatchNo(),
                            toStockQty(enterQty, quantity, item.effectiveBaseQuantity()), unitCost,
                            inbound.getInboundNo(), "SUBCONTRACT_INBOUND",
                            "SubcontractInbound", inbound.getInboundNo(), approver);
                }
                if (scrapQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.addStock(
                            scrapWarehouseId, null, materialId, item.getBatchNo(),
                            toStockQty(scrapQty, quantity, item.effectiveBaseQuantity()), unitCost,
                            inbound.getInboundNo(), "SUBCONTRACT_INBOUND_SCRAP",
                            "SubcontractInbound", inbound.getInboundNo(), approver);
                }
                if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)
                        && rejected.compareTo(BigDecimal.ZERO) > 0) {
                    autoReturnItems.add(buildQcReturnItem(inbound, item, rejected));
                }
            }
        }

        if (inbound.getOrderId() != null) {
            SubcontractOrder order = subcontractOrderService.getById(inbound.getOrderId());
            if (order != null) {
                BigDecimal totalFinishedQty = BigDecimal.ZERO;
                if (items != null) {
                    for (SubcontractInboundItem item : items) {
                        if (item.getOrderItemId() != null) {
                            SubcontractOrderItem orderItem = orderItemMapper.selectById(item.getOrderItemId());
                            if (orderItem != null) {
                                BigDecimal effectiveQty = effectiveQualified(item, qcOn);
                                BigDecimal newFinishedQty = orderItem.getFinishedQty() != null
                                        ? orderItem.getFinishedQty().add(effectiveQty)
                                        : effectiveQty;
                                orderItem.setFinishedQty(newFinishedQty);
                                orderItemMapper.updateById(orderItem);
                                totalFinishedQty = totalFinishedQty.add(effectiveQty);
                            }
                        }
                    }
                }

                BigDecimal newFinishedQuantity = (order.getFinishedQuantity() != null ? order.getFinishedQuantity() : BigDecimal.ZERO)
                        .add(totalFinishedQty);
                order.setFinishedQuantity(newFinishedQuantity);

                if (newFinishedQuantity.compareTo(order.getTotalQuantity() != null ? order.getTotalQuantity() : BigDecimal.ZERO) >= 0) {
                    order.setStatus(5);
                    order.setActualDate(LocalDate.now());
                } else if (order.getStatus() == null || order.getStatus() == 3) {
                    order.setStatus(4);
                }

                subcontractOrderService.updateById(order);
            }
        }

        // 自动生成加工费应付单
        generateApForInbound(inbound);

        // 质检不合格自动退货：生成委外退货单草稿（不产生库存变动，仅单据与财务冲减）
        if (!autoReturnItems.isEmpty()) {
            createQcAutoReturnDraft(inbound, autoReturnItems);
        }

        log.info("委外入库单审核: {} 审核人: {}", inbound.getInboundNo(), approver);
    }

    /** 重置草稿影响：与 onApproved 严格镜像（冲回库存/回退加工单已完成量/删除应付单/清理质检自动退货草稿） */
    @Override
    public void onReverseApproved(SubcontractInbound inbound) {
        Long id = inbound.getId();
        String operator = inbound.getApprovedBy();

        List<com.erp.subcontract.returnn.entity.SubcontractReturn> autoReturns = listQcAutoReturns(inbound);

        List<SubcontractInboundItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractInboundItem>().eq("inbound_id", id).orderByAsc("id"));
        boolean qcOn = inbound.getQcEnabled() != null && inbound.getQcEnabled() == 1;
        if (items != null) {
            for (SubcontractInboundItem item : items) {
                Long warehouseId = item.getWarehouseId();
                Long materialId = item.getMaterialId();
                if (warehouseId == null || materialId == null) {
                    continue;
                }
                BigDecimal quantity = nvl(item.getQuantity());
                BigDecimal qualified = qcOn ? nvl(item.getQualifiedQuantity()) : quantity;
                BigDecimal rejected = qcOn ? nvl(item.getRejectedQuantity()) : BigDecimal.ZERO;
                String handling = qcOn ? normalizeHandling(item.getHandlingType(), rejected)
                        : com.erp.qc.service.InboundQcService.HANDLE_RELEASE;
                BigDecimal enterQty;
                BigDecimal scrapQty = BigDecimal.ZERO;
                Long scrapWarehouseId = null;
                if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)) {
                    enterQty = qualified;
                } else if (com.erp.qc.service.InboundQcService.HANDLE_SCRAP.equals(handling)) {
                    enterQty = qualified;
                    scrapQty = rejected;
                    scrapWarehouseId = item.getScrapWarehouseId();
                    if (scrapQty.compareTo(BigDecimal.ZERO) > 0 && scrapWarehouseId == null) {
                        scrapWarehouseId = inboundQcService.defaultScrapWarehouseId();
                    }
                } else {
                    enterQty = quantity;
                }
                if (enterQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.deductStockAtCost(
                            warehouseId, null, materialId, item.getBatchNo(),
                            toStockQty(enterQty, quantity, item.effectiveBaseQuantity()),
                            item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO,
                            inbound.getInboundNo(), "SUBCONTRACT_INBOUND_REVERSE",
                            "SubcontractInbound", inbound.getInboundNo(), operator);
                }
                if (scrapWarehouseId != null && scrapQty.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryService.deductStockAtCost(
                            scrapWarehouseId, null, materialId, item.getBatchNo(),
                            toStockQty(scrapQty, quantity, item.effectiveBaseQuantity()),
                            item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO,
                            inbound.getInboundNo(), "SUBCONTRACT_INBOUND_SCRAP_REVERSE",
                            "SubcontractInbound", inbound.getInboundNo(), operator);
                }
            }
        }

        // 清理质检自动生成的委外退货单草稿（明细 + 主表）
        for (com.erp.subcontract.returnn.entity.SubcontractReturn ret : autoReturns) {
            subcontractReturnItemMapper.delete(new QueryWrapper<com.erp.subcontract.returnn.entity.SubcontractReturnItem>()
                    .eq("return_id", ret.getId()));
            subcontractReturnMapper.deleteById(ret.getId());
            log.info("重置草稿同步清理质检自动委外退货单: 入库单={} 退货单={}",
                    inbound.getInboundNo(), ret.getReturnNo());
        }

        if (inbound.getOrderId() != null) {
            SubcontractOrder order = subcontractOrderService.getById(inbound.getOrderId());
            if (order != null) {
                BigDecimal totalReverseQty = BigDecimal.ZERO;
                if (items != null) {
                    for (SubcontractInboundItem item : items) {
                        if (item.getOrderItemId() != null) {
                            SubcontractOrderItem orderItem = orderItemMapper.selectById(item.getOrderItemId());
                            if (orderItem != null) {
                                BigDecimal effectiveQty = effectiveQualified(item, qcOn);
                                BigDecimal newFinishedQty = (orderItem.getFinishedQty() != null ? orderItem.getFinishedQty() : BigDecimal.ZERO)
                                        .subtract(effectiveQty);
                                if (newFinishedQty.compareTo(BigDecimal.ZERO) < 0) {
                                    newFinishedQty = BigDecimal.ZERO;
                                }
                                orderItem.setFinishedQty(newFinishedQty);
                                orderItemMapper.updateById(orderItem);
                                totalReverseQty = totalReverseQty.add(effectiveQty);
                            }
                        }
                    }
                }

                BigDecimal newFinishedQuantity = (order.getFinishedQuantity() != null ? order.getFinishedQuantity() : BigDecimal.ZERO)
                        .subtract(totalReverseQty);
                if (newFinishedQuantity.compareTo(BigDecimal.ZERO) < 0) {
                    newFinishedQuantity = BigDecimal.ZERO;
                }
                order.setFinishedQuantity(newFinishedQuantity);

                if (newFinishedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    order.setStatus(3);
                    order.setActualDate(null);
                } else {
                    order.setStatus(4);
                }

                subcontractOrderService.updateById(order);
            }
        }

        // 删除自动生成的加工费应付单（付款记录守卫已在 reverseApprove/voidBill 前置完成）
        AccountsPayable apToDelete = accountsPayableMapper.selectOne(
                new QueryWrapper<AccountsPayable>()
                        .eq("source_bill_type", "委外入库单")
                        .eq("source_bill_no", inbound.getInboundNo()));
        if (apToDelete != null) {
            accountsPayableMapper.deleteById(apToDelete.getApId());
            log.info("委外入库单 {} 重置草稿，删除自动生成的应付单 {}", inbound.getInboundNo(), apToDelete.getApNo());
        }

        log.info("委外入库单重置草稿: {} 操作人: {}", inbound.getInboundNo(), operator);
    }

    /** 作废影响：与 onApproved 严格镜像（冲回库存/回退加工单/删除应付单/清理自动退货草稿） */
    @Override
    public void onVoided(SubcontractInbound inbound) {
        onReverseApproved(inbound);
    }

    /** 反作废影响：重新释放审核影响（与 onApproved 一致，含应付单与自动退货草稿重建） */
    @Override
    public void onUnvoided(SubcontractInbound inbound) {
        onApproved(inbound);
    }

    /** 查询本入库单质检自动生成的委外退货单（来源类型 + 来源单号） */
    private List<com.erp.subcontract.returnn.entity.SubcontractReturn> listQcAutoReturns(SubcontractInbound inbound) {
        return subcontractReturnMapper.selectList(
                new QueryWrapper<com.erp.subcontract.returnn.entity.SubcontractReturn>()
                        .eq("source_bill_type", "委外入库单")
                        .eq("source_bill_no", inbound.getInboundNo()));
    }

    /** 已生成应付单且已有付款记录 → 禁止重置草稿/作废/删除 */
    private void assertNoPaidPayable(SubcontractInbound inbound, String opName) {
        AccountsPayable ap = accountsPayableMapper.selectOne(
                new QueryWrapper<AccountsPayable>()
                        .eq("source_bill_type", "委外入库单")
                        .eq("source_bill_no", inbound.getInboundNo()));
        if (ap != null && ap.getPaidAmount() != null && ap.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("该入库单已生成应付单且已有付款记录，无法" + opName);
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 明细行展示用「编码 名称」 */
    private static String itemLabel(SubcontractInboundItem item) {
        String code = item.getMaterialCode() != null ? item.getMaterialCode() : "";
        String name = item.getMaterialName() != null ? item.getMaterialName() : "";
        return (code + " " + name).trim();
    }

    /** 不合格处理方式归一：无不合格数量按放行处理 */
    private static String normalizeHandling(String handling, BigDecimal rejected) {
        if (rejected == null || rejected.compareTo(BigDecimal.ZERO) <= 0) {
            return com.erp.qc.service.InboundQcService.HANDLE_RELEASE;
        }
        if (handling == null || handling.isBlank()) {
            return com.erp.qc.service.InboundQcService.HANDLE_RELEASE;
        }
        return handling;
    }

    /** 有效合格数量：放行=全量、自动退货/报废=质检合格数、未开质检=全量 */
    private static BigDecimal effectiveQualified(SubcontractInboundItem item, boolean qcOn) {
        BigDecimal quantity = nvl(item.getQuantity());
        if (!qcOn) return quantity;
        BigDecimal rejected = nvl(item.getRejectedQuantity());
        String handling = normalizeHandling(item.getHandlingType(), rejected);
        if (com.erp.qc.service.InboundQcService.HANDLE_RETURN.equals(handling)
                || com.erp.qc.service.InboundQcService.HANDLE_SCRAP.equals(handling)) {
            return nvl(item.getQualifiedQuantity());
        }
        return quantity;
    }

    /** 构造质检自动退货单的一行（数量 = 不合格数量） */
    private com.erp.subcontract.returnn.entity.SubcontractReturnItem buildQcReturnItem(
            SubcontractInbound inbound, SubcontractInboundItem item, BigDecimal rejected) {
        com.erp.subcontract.returnn.entity.SubcontractReturnItem ri =
                new com.erp.subcontract.returnn.entity.SubcontractReturnItem();
        ri.setOrderItemId(item.getOrderItemId());
        ri.setMaterialId(item.getMaterialId());
        ri.setMaterialCode(item.getMaterialCode());
        ri.setMaterialName(item.getMaterialName());
        ri.setSpecification(item.getSpecification());
        ri.setUnit(item.getUnit());
        ri.setQuantity(rejected);
        ri.setWarehouseId(item.getWarehouseId());
        ri.setWarehouseName(item.getWarehouseName());
        ri.setSourceBillType("委外入库单");
        ri.setSourceBillNo(inbound.getInboundNo());
        ri.setSourceDocId(inbound.getId());
        ri.setRemark("质检不合格自动退货");
        return ri;
    }

    /** 质检不合格自动生成委外退货单草稿（不产生库存变动，仅单据与财务冲减） */
    private void createQcAutoReturnDraft(SubcontractInbound inbound,
                                         List<com.erp.subcontract.returnn.entity.SubcontractReturnItem> returnItems) {
        com.erp.subcontract.returnn.entity.SubcontractReturn ret =
                new com.erp.subcontract.returnn.entity.SubcontractReturn();
        ret.setOrderId(inbound.getOrderId());
        ret.setOrderNo(inbound.getOrderNo());
        ret.setSupplierId(inbound.getSupplierId());
        ret.setSupplierName(inbound.getSupplierName());
        ret.setReturnDate(LocalDate.now());
        ret.setSourceBillType("委外入库单");
        ret.setSourceBillNo(inbound.getInboundNo());
        ret.setStatus(0);
        ret.setApprovalStatus("草稿");
        ret.setStockEffect(0);
        ret.setRemark("由委外入库单 " + inbound.getInboundNo() + " 质检不合格自动生成");
        subcontractReturnService.saveReturnWithItems(ret, returnItems);
        log.info("质检不合格自动生成委外退货单草稿: 入库单={} 退货单={} 行数={}",
                inbound.getInboundNo(), ret.getReturnNo(), returnItems.size());
    }

    /**
     * 汇总一张委外加工单的净材料成本：发料 + 补料 - 退料，按加工单行（order_item_id）归集。
     * 成本优先取单据明细上冻结的实际出入库成本，历史单据无成本时回落物料最新核算成本/采购价。
     */
    private Map<Long, BigDecimal> loadNetMaterialCost(Long orderId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (orderId == null) {
            return map;
        }
        try {
            List<SubcontractPickItem> picks = subcontractPickItemMapper.selectList(
                    new QueryWrapper<SubcontractPickItem>()
                            .inSql("pick_id", "select id from subcontract_pick where order_id = " + orderId));
            for (SubcontractPickItem p : picks) {
                BigDecimal qty = p.effectiveBaseQuantity() != null ? p.effectiveBaseQuantity() : p.getQuantity();
                accumulateNet(map, p.getOrderItemId(), resolveItemCost(p.getMaterialId(), p.getAvgCost()), qty, 1);
            }
        } catch (Exception e) {
            log.warn("[委外成本] 汇总发料成本失败 orderId={}: {}", orderId, e.getMessage());
        }
        try {
            List<SubcontractSupplementItem> supplements = subcontractSupplementItemMapper.selectList(
                    new QueryWrapper<SubcontractSupplementItem>()
                            .inSql("supplement_id", "select id from subcontract_supplement where order_id = " + orderId));
            for (SubcontractSupplementItem s : supplements) {
                BigDecimal qty = s.effectiveBaseQuantity() != null ? s.effectiveBaseQuantity() : s.getQuantity();
                accumulateNet(map, s.getOrderItemId(), resolveItemCost(s.getMaterialId(), s.getAvgCost()), qty, 1);
            }
        } catch (Exception e) {
            log.warn("[委外成本] 汇总补料成本失败 orderId={}: {}", orderId, e.getMessage());
        }
        try {
            List<SubcontractReturnItem> returns = subcontractReturnItemMapper.selectList(
                    new QueryWrapper<SubcontractReturnItem>()
                            .inSql("return_id", "select id from subcontract_return where order_id = " + orderId));
            for (SubcontractReturnItem r : returns) {
                BigDecimal qty = r.effectiveBaseQuantity() != null ? r.effectiveBaseQuantity() : r.getQuantity();
                accumulateNet(map, r.getOrderItemId(), resolveItemCost(r.getMaterialId(), r.getAvgCost()), qty, -1);
            }
        } catch (Exception e) {
            log.warn("[委外成本] 汇总退料成本失败 orderId={}: {}", orderId, e.getMessage());
        }
        return map;
    }

    private void accumulateNet(Map<Long, BigDecimal> map, Long orderItemId, BigDecimal unitCost, BigDecimal qty, int sign) {
        if (orderItemId == null || unitCost == null || qty == null || qty.signum() <= 0) {
            return;
        }
        BigDecimal amount = unitCost.multiply(qty);
        map.merge(orderItemId, sign > 0 ? amount : amount.negate(), BigDecimal::add);
    }

    /** 明细成本：冻结成本优先，取不到回落物料最新核算成本，再回落采购价 */
    private BigDecimal resolveItemCost(Long materialId, BigDecimal frozen) {
        if (frozen != null && frozen.compareTo(BigDecimal.ZERO) > 0) {
            return frozen;
        }
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal latest = materialCostService.getLatestCalcCost(materialId);
        if (latest != null && latest.compareTo(BigDecimal.ZERO) > 0) {
            return latest;
        }
        try {
            Material material = materialService.getById(materialId);
            if (material != null && material.getPurchasePrice() != null) {
                return material.getPurchasePrice();
            }
        } catch (Exception ignored) {
        }
        return BigDecimal.ZERO;
    }

    /** 行数量 → 基本单位数量（多单位：按整行基本数量同比例折算） */
    private BigDecimal toStockQty(BigDecimal lineQty, BigDecimal totalLineQty, BigDecimal baseQty) {
        if (lineQty == null) {
            return BigDecimal.ZERO;
        }
        if (baseQty == null || totalLineQty == null || totalLineQty.signum() == 0) {
            return lineQty;
        }
        return lineQty.multiply(baseQty).divide(totalLineQty, 4, RoundingMode.HALF_UP);
    }

    /** 成品单位材料成本 = 该加工单行净材料成本 ÷ 计划数量；无关联行时回落成品最新核算成本 */
    private BigDecimal resolveMaterialUnitCost(SubcontractInboundItem item, Map<Long, BigDecimal> netCostMap) {
        Long orderItemId = item.getOrderItemId();
        if (orderItemId != null) {
            BigDecimal net = netCostMap.get(orderItemId);
            SubcontractOrderItem orderItem = orderItemMapper.selectById(orderItemId);
            BigDecimal planned = null;
            if (orderItem != null) {
                planned = orderItem.effectiveBaseQuantity() != null
                        ? orderItem.effectiveBaseQuantity()
                        : orderItem.getQuantity();
            }
            if (net != null && planned != null && planned.compareTo(BigDecimal.ZERO) > 0) {
                return net.max(BigDecimal.ZERO).divide(planned, 6, RoundingMode.HALF_UP);
            }
        }
        BigDecimal latest = materialCostService.getLatestCalcCost(item.getMaterialId());
        return latest != null && latest.compareTo(BigDecimal.ZERO) > 0 ? latest : BigDecimal.ZERO;
    }

    /** 本行加工费单位成本：行加工费 ÷ 行基本数量；行未填时用表头加工费 ÷ 总数量 */
    private BigDecimal resolveProcessingFeeUnitCost(SubcontractInbound inbound, SubcontractInboundItem item, BigDecimal quantity) {
        BigDecimal fee = item.getProcessingFee();
        BigDecimal baseQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : quantity;
        if (fee != null && fee.compareTo(BigDecimal.ZERO) > 0 && baseQty != null && baseQty.compareTo(BigDecimal.ZERO) > 0) {
            return fee.divide(baseQty, 6, RoundingMode.HALF_UP);
        }
        BigDecimal headerFee = inbound.getProcessingFee();
        BigDecimal totalQty = inbound.getTotalQuantity();
        if (headerFee != null && headerFee.compareTo(BigDecimal.ZERO) > 0
                && totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0) {
            return headerFee.divide(totalQty, 6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 委外入库单审核通过后自动生成加工费应付单
     */
    private void generateApForInbound(SubcontractInbound inbound) {
        // 检查是否已生成过应付单（通过 sourceBillType + sourceBillNo 查重）
        AccountsPayable existing = accountsPayableMapper.selectOne(
                new QueryWrapper<AccountsPayable>()
                        .eq("source_bill_type", "委外入库单")
                        .eq("source_bill_no", inbound.getInboundNo()));
        if (existing != null) {
            log.info("委外入库单 {} 已生成过加工费应付单 {}，跳过", inbound.getInboundNo(), existing.getApNo());
            return;
        }

        // 应付金额：质检退货时扣减不合格部分（payable_amount 由质检回写），未质检/未回写时用加工费
        BigDecimal payable = inbound.getPayableAmount() != null
                ? inbound.getPayableAmount()
                : (inbound.getProcessingFee() != null ? inbound.getProcessingFee() : BigDecimal.ZERO);

        AccountsPayable ap = new AccountsPayable();
        ap.setApNo(generateApNo());
        ap.setApDate(LocalDate.now());
        ap.setSupplierId(inbound.getSupplierId());
        ap.setSupplierName(inbound.getSupplierName());
        ap.setTotalAmount(payable);
        ap.setTaxAmount(BigDecimal.ZERO);
        ap.setNetAmount(payable);
        ap.setPaidAmount(BigDecimal.ZERO);
        ap.setBalanceAmount(payable);
        ap.setSourceBillType("委外入库单");
        ap.setSourceBillNo(inbound.getInboundNo());
        ap.setIsAutoGen((short) 1);
        ap.setIsClosed((short) 0);
        ap.setStatus("待付款");
        ap.setApprovalStatus("已生效");

        accountsPayableMapper.insert(ap);
        log.info("委外入库单 {} 自动生成加工费应付单 {} 金额: {}", inbound.getInboundNo(), ap.getApNo(), payable);
    }

    /**
     * 生成应付单号，格式: AP-yyyyMMdd-0001
     */
    private String generateApNo() {
        String docType = "ACCOUNT_PAYABLE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "AP-" + dateStr + "-";
            QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
            wrapper.likeRight("ap_no", prefix).orderByDesc("ap_no").last("LIMIT 1");
            AccountsPayable last = accountsPayableMapper.selectOne(wrapper);
            int seq = 1;
            if (last != null && last.getApNo() != null) {
                try {
                    seq = Integer.parseInt(last.getApNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public String generateInboundNo() {
        String docType = "SUBCONTRACT_INBOUND";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SC-IN-" + dateStr + "-";
            QueryWrapper<SubcontractInbound> wrapper = new QueryWrapper<>();
            wrapper.likeRight("inbound_no", prefix).orderByDesc("inbound_no").last("LIMIT 1");
            SubcontractInbound last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getInboundNo() != null) {
                try {
                    seq = Integer.parseInt(last.getInboundNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 删除（统一状态机）：桥接接口的 Long 签名，逻辑全部走基类（草稿直接删；已生效先对冲库存再删） */
    @Override
    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }

    /**
     * 删除前守卫：与「重置草稿 / 作废」同口径。
     * 删除已生效单据会走 onReverseApproved（冲回库存并删除自动生成的应付单），
     * 因此重置草稿/作废里的两道守卫必须同样生效，否则会把已有付款的应付单、已生效的质检自动退货单删掉。
     */
    @Override
    protected void validateBeforeDelete(SubcontractInbound inbound) {
        if (!BillStatusUtils.isApprovedClass(inbound.getStatus())) return;
        for (com.erp.subcontract.returnn.entity.SubcontractReturn ret : listQcAutoReturns(inbound)) {
            if (ret.getStatus() != null && ret.getStatus() != 0) {
                throw new BusinessException("入库单质检自动生成的委外退货单 " + ret.getReturnNo()
                        + " 已生效，请先重置/作废该退货单后再删除本入库单");
            }
        }
        assertNoPaidPayable(inbound, "删除");
    }
}
