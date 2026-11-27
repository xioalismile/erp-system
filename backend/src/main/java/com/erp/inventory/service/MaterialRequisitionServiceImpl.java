package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.service.EmployeeService;
import com.erp.masterdata.service.MaterialService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderMaterialMapper;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor

public class MaterialRequisitionServiceImpl extends AbstractBillService<MaterialRequisitionMapper, MaterialRequisition> implements MaterialRequisitionService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 列表页筛选条件自定义 · 库存模块推广批次）。
        // 键 = 前端筛选条件定义键（与生产领料单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（由 MyBatis-Plus 元数据自动解析列名，防手写列名笔误）。
        // 领料人：列是员工外键（employee_id），按名称筛的下拉传的是员工 ID → 翻译成 employeeId 属性。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("requisitionNo", new BillHeaderFilterSupport.Spec("requisitionNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("workOrderNo", new BillHeaderFilterSupport.Spec("workOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 主档 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("employeeName", new BillHeaderFilterSupport.Spec("employeeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("requisitionDate", new BillHeaderFilterSupport.Spec("requisitionDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("materialRequisition", MaterialRequisition.class, f);
    }

    private final MaterialRequisitionItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper costCalculationItemMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderMaterialMapper workOrderMaterialMapper;
    private final MaterialCostService materialCostService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final EmployeeService employeeService;
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
    /** 一表三身份（领料/退料/补料）：提交期间把单据身份传给统一状态机 */
    private static final ThreadLocal<String> CURRENT_APPROVAL_TYPE = new ThreadLocal<>();

    @Override protected String getBillTypeLabel() { return "生产领料单"; }
    @Override protected String getApprovalBillType() {
        String t = CURRENT_APPROVAL_TYPE.get();
        return (t != null) ? t : "MATERIAL_REQUISITION";
    }
    @Override protected String getBillNo(MaterialRequisition b) { return b.getRequisitionNo(); }
    @Override protected Object getId(MaterialRequisition b) { return b.getRequisitionId(); }
    @Override protected String getStatus(MaterialRequisition b) { return b.getStatus(); }
    @Override protected void setStatus(MaterialRequisition b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(MaterialRequisition b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(MaterialRequisition b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(MaterialRequisition b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(MaterialRequisition b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(MaterialRequisition b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(MaterialRequisition b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(MaterialRequisition b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(MaterialRequisition b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(MaterialRequisition b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(MaterialRequisition b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(MaterialRequisition b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(MaterialRequisition b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(MaterialRequisition b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(MaterialRequisition bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getRequisitionNo());
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final java.util.Set<String> SORTABLE_COLUMNS = java.util.Set.of(
            "created_at",
            "created_by",
            "requisition_no",
            "requisition_date",
            "work_order_no",
            "total_amount");

    /**
     * 删除领料单 - 增强校验:
     * 1) 状态校验: 非草稿族（草稿/已驳回/已取消以外）不可删除
     * 2) 成本核算联动: 若该单据已用于成本核算，禁止删除，需先反确认核算单
     * 3) 级联删除明细
     */
    @Override
    @Transactional
    public boolean removeById(java.io.Serializable id) {
        MaterialRequisition existing = getById(id);
        if (existing == null) {
            throw new BusinessException("领料单不存在");
        }
        String st = existing.getStatus();
        if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(st))) {
            throw new BusinessException("非草稿不能删除（当前状态：" + st + "），请先重置草稿");
        }
        // 成本核算联动检查 - 若已在核算单明细中被引用，禁止删除
        Long usedCount = costCalculationItemMapper.selectCount(
                new QueryWrapper<com.erp.finance.cost.calculation.entity.CostCalculationItem>()
                        .like("remark", existing.getRequisitionNo()));
        if (usedCount != null && usedCount > 0) {
            throw new BusinessException("该领料单已用于成本核算(" + usedCount + "处引用)，"
                    + "请先反确认相关核算单后再删除");
        }
        // 级联删除明细
        itemMapper.delete(new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id));
        boolean ok = super.removeById(id);
        log.info("删除领料单: {} 结果: {}", existing.getRequisitionNo(), ok);
        return ok;
    }

    @Override
    @Transactional
    public void saveRequisitionWithItems(MaterialRequisition requisition, List<MaterialRequisitionItem> items) {
        boolean isNew = requisition.getRequisitionId() == null;

        if (!isNew) {
            MaterialRequisition existing = getById(requisition.getRequisitionId());
            if (existing != null) {
                String st = existing.getStatus();
                if ("已生效".equals(st) || "已关闭".equals(st) || "已取消".equals(st)) {
                    throw new BusinessException("当前状态(" + st + ")不可修改，如需修改请先重置草稿");
                }
            }
        }

        if (requisition.getRequisitionNo() == null || requisition.getRequisitionNo().isBlank()) {
            requisition.setRequisitionNo(generateRequisitionNo(requisition.getBillType()));
        }

        if (requisition.getBillType() == null || requisition.getBillType().isBlank()) {
            requisition.setBillType("PICK");
        }
        // 保存=草稿：前端新增页送空串时按空处理（与"未传/null"等价）
        if (requisition.getStatus() == null || requisition.getStatus().isBlank()) requisition.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"，"待审核"是提交后的状态
        if (requisition.getApprovalStatus() == null || requisition.getApprovalStatus().isBlank()
                || ("草稿".equals(requisition.getStatus()) && "待审核".equals(requisition.getApprovalStatus()))) {
            requisition.setApprovalStatus("草稿");
        }
        if (requisition.getExchangeRate() == null) requisition.setExchangeRate(BigDecimal.ONE);
        if (requisition.getTotalAmount() == null) requisition.setTotalAmount(BigDecimal.ZERO);
        if (requisition.getRequisitionDate() == null) requisition.setRequisitionDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<MaterialRequisitionItem> validItems = new ArrayList<>();
            for (MaterialRequisitionItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        // 生产参数「超过来源单数量」：转入工单用料时按设置中心策略校验（不控制/预警提示/不允许）
        validateProduceOverQty(requisition, items);
        // 2026-11-25：领料批量整数倍校验（设置中心开关，默认关）
        validateRequisitionBatchQty(items);

        // 表头「计税方式」口径归一（总开关关闭→不计税；为空→取设置中心默认）
        if (!sysParamValidator.isTaxEnabled()) {
            requisition.setTaxCalcMethod("不计税");
        } else if (requisition.getTaxCalcMethod() == null || requisition.getTaxCalcMethod().isBlank()) {
            requisition.setTaxCalcMethod(sysParamValidator.getDefaultTaxMethod());
        }
        int headerTaxType = resolveHeaderTaxType(requisition.getTaxCalcMethod());
        recalculateRequisition(requisition, items, headerTaxType);

        if (isNew) {
            save(requisition);
        } else {
            updateById(requisition);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", requisition.getRequisitionId()));
            int lineNo = 1;
            for (MaterialRequisitionItem item : items) {
                item.setRequisitionItemId(null);
                item.setRequisitionId(requisition.getRequisitionId());
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

        log.info("生产领料单保存成功: {} 状态: {} 合计: {}", requisition.getRequisitionNo(), requisition.getStatus(), requisition.getTotalAmount());
    }

    /**
     * 领料批量整数倍校验（2026-11-25，与采购订单同口径）。
     * 设置中心「领料单按采购批量校验」开启时，明细基本单位数量必须是物料采购批量的整数倍；
     * 开关关闭（默认）不做任何校验，与升级前行为一致。
     */
    private void validateRequisitionBatchQty(List<MaterialRequisitionItem> items) {
        if (items == null || items.isEmpty()) return;
        if (!sysConfigService.getBooleanValue("inventory.requisition.batch.check.enabled", false)) return;
        for (int i = 0; i < items.size(); i++) {
            MaterialRequisitionItem item = items.get(i);
            if (item.getMaterialId() == null) continue;
            com.erp.masterdata.entity.Material material = materialService.getById(item.getMaterialId());
            if (material == null) continue;
            BigDecimal batch = material.getPurchaseBatchQty();
            if (batch == null || batch.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal qty = item.getBaseQuantity() != null ? item.getBaseQuantity() : item.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (qty.divideAndRemainder(batch)[1].compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal suggest = qty.divide(batch, 0, RoundingMode.CEILING).multiply(batch);
                throw new BusinessException("第" + (i + 1) + "行[" + item.getMaterialName() + "]领料数量必须是采购批量 "
                        + batch.stripTrailingZeros().toPlainString() + " 的整数倍，建议数量 "
                        + suggest.stripTrailingZeros().toPlainString());
            }
        }
    }

    /**
     * 生产参数「超过来源单数量」落地（2026-11-09）。
     * 只有从工单转入、带工单用料行号的明细参与校验；手工加行不受影响。
     */
    private void validateProduceOverQty(MaterialRequisition requisition, List<MaterialRequisitionItem> items) {
        String policy = sysConfigService.getValue("produce.over.qty", "不控制");
        if (policy == null || policy.isBlank() || "不控制".equals(policy.trim())) return;
        if (items == null || items.isEmpty()) return;
        String orderNo = requisition.getWorkOrderNo();
        if (orderNo == null || orderNo.isBlank()) return;

        WorkOrder order = workOrderMapper.selectOne(new QueryWrapper<WorkOrder>()
                .eq("order_no", orderNo).last("LIMIT 1"));
        if (order == null) return;
        List<WorkOrderMaterial> woms = workOrderMaterialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));
        Map<Integer, BigDecimal> remainByLine = new HashMap<>();
        for (WorkOrderMaterial w : woms) {
            if (w.getLineNo() == null) continue;
            BigDecimal required = w.getRequiredQuantity() == null ? BigDecimal.ZERO : w.getRequiredQuantity();
            BigDecimal issued = w.getIssuedQuantity() == null ? BigDecimal.ZERO : w.getIssuedQuantity();
            remainByLine.put(w.getLineNo(), required.subtract(issued));
        }

        List<String> warned = new ArrayList<>();
        for (MaterialRequisitionItem item : items) {
            Integer lineNo = item.getWorkOrderLineNo() != null ? item.getWorkOrderLineNo() : item.getSourceLineNo();
            if (lineNo == null) continue;
            BigDecimal remain = remainByLine.get(lineNo);
            BigDecimal qty = item.getQuantity();
            if (remain == null || qty == null || qty.compareTo(remain) <= 0) continue;
            String name = item.getMaterialName() != null ? item.getMaterialName() : String.valueOf(item.getMaterialId());
            String msg = "物料 " + name + " 本次数量 " + qty.stripTrailingZeros().toPlainString()
                    + " 超过来源单可转数量 " + remain.stripTrailingZeros().toPlainString();
            if ("不允许".equals(policy.trim())) {
                throw new BusinessException("【设置中心-来源单数量】" + msg);
            }
            warned.add(name);
        }
        if (!warned.isEmpty()) {
            com.erp.config.RequestWarningUtils.warn(
                    "【设置中心-来源单数量】以下物料已超量转入：" + String.join("、", warned));
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

    private void calculateItemAmount(MaterialRequisitionItem item, int headerTaxType) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        // 领料单明细无税率列：含税价 = 不含税价 = 单价（口径仍按表头归一，保持全站一致的列）
        item.setPriceWithTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(unitPrice.setScale(6, RoundingMode.HALF_UP));
        BigDecimal amount = unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 价税合计 = 含税价 × 数量（含税价=不含税价=单价，故等于金额，2026-11-23 补齐）
        item.setNetAmount(item.getPriceWithTax().multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void recalculateRequisition(MaterialRequisition requisition, List<MaterialRequisitionItem> items, int headerTaxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        if (items != null) {
            for (MaterialRequisitionItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalAmount = totalAmount.add(nvl(item.getAmount()));
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
        }
        requisition.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        requisition.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<MaterialRequisition> pageQuery(int current, int size, String keyword, String status,
                                                     String startDate, String endDate, String employeeId,
                                                     String sortBy, String sortOrder, String customSearch) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, java.util.Collections.emptyMap());
    }

    @Override
    public PageResult<MaterialRequisition> pageQuery(int current, int size, String keyword, String status,
                                                     String startDate, String endDate, String employeeId,
                                                     String sortBy, String sortOrder, String customSearch,
                                                     java.util.Map<String, String> headerFilters) {
        QueryWrapper<MaterialRequisition> wrapper = new QueryWrapper<>();
        // 关键词（本页真实列 requisition_no）——多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "requisition_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 requisition_date）；员工=领料人（本页真实列 employee_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "requisition_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "employee_id");
        // 表头字段过滤（2026-10-14 列表页筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "materialRequisition", headerFilters);
        // 数据权限（按角色 data_scope 统一数据权限）：仅本人/本部门/全部数据，替代旧 SecurityUtils.getCreatedByFilter
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "material_requisition", customSearch, CustomFieldSearchHelper.idColumnOf("material_requisition"));
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, MaterialRequisition.class);
        Page<MaterialRequisition> page = new Page<>(current, size);
        Page<MaterialRequisition> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PageResult<MaterialRequisition> pageQueryByBillType(int current, int size, String keyword, String status, String billType,
                                                               String startDate, String endDate, String employeeId,
                                                               String sortBy, String sortOrder, String customSearch) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQueryByBillType(current, size, keyword, status, billType, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, java.util.Collections.emptyMap());
    }

    @Override
    public PageResult<MaterialRequisition> pageQueryByBillType(int current, int size, String keyword, String status, String billType,
                                                               String startDate, String endDate, String employeeId,
                                                               String sortBy, String sortOrder, String customSearch,
                                                               java.util.Map<String, String> headerFilters) {
        QueryWrapper<MaterialRequisition> wrapper = new QueryWrapper<>();
        // 关键词（本页真实列 requisition_no）——多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "requisition_no");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (billType != null && !billType.isBlank()) {
            wrapper.eq("bill_type", billType);
        }
        // 日期区间（本页真实列 requisition_date）；员工=领料人（本页真实列 employee_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "requisition_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "employee_id");
        // 表头字段过滤（2026-10-14 列表页筛选条件自定义）：白名单 + 参数化，非法/空白入参在支持类内部忽略
        BillHeaderFilterSupport.apply(wrapper, "materialRequisition", headerFilters);
        // 数据权限（按角色 data_scope 统一数据权限）：仅本人/本部门/全部数据，替代旧 SecurityUtils.getCreatedByFilter
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "material_requisition", customSearch, CustomFieldSearchHelper.idColumnOf("material_requisition"));
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, MaterialRequisition.class);
        Page<MaterialRequisition> page = new Page<>(current, size);
        Page<MaterialRequisition> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public MaterialRequisition getDetail(Long id) {
        MaterialRequisition requisition = getById(id);
        if (requisition == null) throw new BusinessException("生产领料单不存在");
        List<MaterialRequisitionItem> items = itemMapper.selectList(
                new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id).orderByAsc("line_no"));
        requisition.setItems(items);
        return requisition;
    }

    /** 提交审核：三身份（领料/退料/补料）审批开关仍按单据自身类型分流，状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void submitForApproval(Long id) {
        MaterialRequisition bill = getById(id);
        if (bill == null) throw new BusinessException("生产领料单不存在");
        // 一表三身份：审批开关/流程编码按单据自身 bill_type 解析（ThreadLocal 传给基类）
        String identity = resolveBillApprovalType(bill);
        CURRENT_APPROVAL_TYPE.set(identity);
        try {
            super.submitForApproval(bill, BillApprovalAdapter.currentSubmitter());
        } finally {
            CURRENT_APPROVAL_TYPE.remove();
        }
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(MaterialRequisition requisition) {
        Long id = requisition.getRequisitionId();
        List<MaterialRequisitionItem> items = itemMapper.selectList(
                new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("领料单没有明细，请至少添加一行物料后再提交");
        }

        if (requisition.getEmployeeId() != null && employeeService.getById(requisition.getEmployeeId()) == null) {
            throw new BusinessException("关联领料人不存在(employeeId=" + requisition.getEmployeeId() + ")，请重新选择领料人");
        }
        if (requisition.getWarehouseId() == null) {
            throw new BusinessException("领料仓库不能为空，请选择领料仓库");
        }
        Warehouse wh = warehouseService.getById(requisition.getWarehouseId());
        if (wh == null) {
            throw new BusinessException("关联仓库不存在(warehouseId=" + requisition.getWarehouseId() + ")，请重新选择仓库");
        }
        String whName = wh.getStockName() != null ? wh.getStockName() : ("ID:" + requisition.getWarehouseId());

        // 【2026-09-14】退料(RETURN)= 物料退回仓库（入库），与 approve() 的分流口径保持一致
        boolean isReturn = "RETURN".equals(requisition.getBillType());
        for (int i = 0; i < items.size(); i++) {
            MaterialRequisitionItem item = items.get(i);
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
            // 多单位：领料出库按「基本单位数量」校验库存（辅单位录入时行数量≠库存口径）
            BigDecimal qty = item.effectiveBaseQuantity() != null
                    ? item.effectiveBaseQuantity()
                    : (item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]领料数量必须大于0");
            }

            // 化妆品批记录·全链批号：批次管理物料领料必须填写批号（退料是回仓，不强制）
            if (!isReturn && mat.getIsLotControl() != null && mat.getIsLotControl() == 1) {
                if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                    throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]批次管理物料必须填写批次号");
                }
            }

            boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
            // 退料是入库，不校验现有库存是否充足；否则退料单提交会被"库存不足"误拦（approve 亦按 RETURN 分流）
            if (!negativeAllowed && !isReturn) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(mat.getMaterialId(), requisition.getWarehouseId());
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(qty) < 0) {
                    throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]库存不足，仓库[" + whName + "]可用 "
                            + available.setScale(2, RoundingMode.HALF_UP)
                            + "，需领料 " + qty.setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

    }

    /**
     * 单据记录自身类型 → 审批身份编码（三个身份共用同一套审核/驳回逻辑）：
     * RETURN → MATERIAL_RETURN（生产退料单）；SUPPLEMENT → MATERIAL_SUPPLEMENT（生产补料单）；
     * 其余（PICK 及历史空值）→ MATERIAL_REQUISITION（生产领料单）。
     */
    private String resolveBillApprovalType(MaterialRequisition requisition) {
        String t = requisition != null ? requisition.getBillType() : null;
        if ("RETURN".equals(t)) return "MATERIAL_RETURN";
        if ("SUPPLEMENT".equals(t)) return "MATERIAL_SUPPLEMENT";
        return "MATERIAL_REQUISITION";
    }

    /** 审核：三身份任务防护 + 状态迁移由统一状态机执行，领退料过账在 onApproved 钩子 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        MaterialRequisition bill = getById((Long) id);
        if (bill == null) throw new BusinessException("生产领料单不存在");
        if (approvalRuntimeService.getRunningTask(resolveBillApprovalType(bill), (Long) id) != null) {
            throw new BusinessException("生产领料/退料/补料单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：成本刷新 + 按领料/退料/补料分流出入库 + 回写工单用料与已领数量 */
    @Override
    public void onApproved(MaterialRequisition requisition) {
        Long id = requisition.getRequisitionId();
        String approver = requisition.getApprovedBy();
        List<MaterialRequisitionItem> items = itemMapper.selectList(
                new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (MaterialRequisitionItem item : items) {
                if (item.getMaterialId() != null) {
                    item.setAvgCost(materialCostService.getLatestCalcCost(item.getMaterialId()));
                    itemMapper.updateById(item);
                }
            }
        }

        // 审核通过后处理库存 (按单据类型分流):
        //  - PICK(领料)/SUPPLEMENT(补料): 扣减库存 (出库)
        //  - RETURN(退料): 增加库存 (物料退回仓库)
        String billType = requisition.getBillType();
        boolean isReturn = "RETURN".equals(billType);
        if (items != null) {
            for (MaterialRequisitionItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : requisition.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (materialId == null) {
                    throw new BusinessException("领料单第" + item.getLineNo() + "行缺少物料，无法审核");
                }
                if (stockId == null) {
                    // 【2026-09-13 修复】原实现此处是 continue：缺仓库时"静默跳过"——不扣库存、不归集成本、还不报错，
                    // 用户看到"审核成功"但成本恒为 0（实测 inventory_ledger 0 行）。改为显式报错让问题暴露。
                    throw new BusinessException("领料单第" + item.getLineNo()
                            + "行未指定仓库/库存地点，无法扣减库存与归集材料成本，请先在领料单上指定仓库");
                }
                if (isReturn) {
                    // 退料 = 物料退回仓库 = 增加库存；冻结退回成本，供反审按原成本扣回
                    BigDecimal returnUnitCost = item.getUnitPrice() != null && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                            ? item.getUnitPrice()
                            : (item.getAvgCost() != null ? item.getAvgCost() : BigDecimal.ZERO);
                    item.setAvgCost(returnUnitCost);
                    itemMapper.updateById(item);
                    inventoryService.addStock(
                            stockId,
                            item.getBinId(),
                            materialId,
                            item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), quantity),
                            returnUnitCost,
                            requisition.getRequisitionNo(),
                            "MATERIAL_RETURN",
                            "MaterialRequisition",
                            requisition.getRequisitionNo(),
                            approver);
                } else {
                    // 领料/补料 = 出库, 库存不足抛 BusinessException 触发回滚
                    // 【2026-09-11 材料成本归集】出库成本必须在扣减前取（FIFO/LIFO 依赖扣减前批次结存），
                    //   取到后回写本行 unit_price/amount/avg_cost，作为"本次领料材料成本"。
                    BigDecimal outUnitCost = inventoryService.getOutUnitCost(
                            stockId, item.getBinId(), materialId, item.getBatchNo());
                    inventoryService.deductStock(
                            stockId,
                            item.getBinId(),
                            materialId,
                            item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), quantity),
                            requisition.getRequisitionNo(),
                            "MATERIAL_REQUISITION",
                            "MaterialRequisition",
                            requisition.getRequisitionNo(),
                            approver);
                    item.setUnitPrice(outUnitCost);
                    item.setAmount(quantity.multiply(outUnitCost));
                    item.setAvgCost(outUnitCost);
                    itemMapper.updateById(item);
                }
            }
        }

        // 回写本单明细的实际领用数量: 审核生效即视为本行已按需求数量领用。
        // 原来只回写了工单用料明细，从不回写领料单自己 → 领料单页面/打印的"已领数量"恒为 0。
        if (items != null && !isReturn) {
            for (MaterialRequisitionItem item : items) {
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                item.setIssuedQuantity(qty);
                itemMapper.updateById(item);
            }
        }

        // 【2026-09-11 材料成本归集】同步表头金额 = Σ 明细金额。
        //   下推生成的领料单表头 total_amount 原恒为 0（明细有金额、表头没有），页面合计显示为 0。
        if (items != null && !items.isEmpty()) {
            BigDecimal newTotal = BigDecimal.ZERO;
            for (MaterialRequisitionItem item : items) {
                if (item.getAmount() != null) {
                    newTotal = newTotal.add(item.getAmount());
                }
            }
            if (newTotal.compareTo(BigDecimal.ZERO) > 0) {
                requisition.setTotalAmount(newTotal.setScale(2, java.math.RoundingMode.HALF_UP));
                updateById(requisition);
            }
        }

        // 回写工单用料明细:
        //  - 领料/补料: issued_quantity += 本次领料数量
        //  - 退料: returned_quantity += 本次退料数量
        if (requisition.getWorkOrderNo() != null && !requisition.getWorkOrderNo().isBlank()) {
            WorkOrder workOrder = workOrderMapper.selectOne(
                    new QueryWrapper<WorkOrder>().eq("order_no", requisition.getWorkOrderNo()).last("LIMIT 1"));
            if (workOrder != null && items != null) {
                for (MaterialRequisitionItem item : items) {
                    if (item.getWorkOrderLineNo() == null) {
                        continue;
                    }
                    WorkOrderMaterial wom = workOrderMaterialMapper.selectOne(
                            new QueryWrapper<WorkOrderMaterial>()
                                    .eq("order_id", workOrder.getOrderId())
                                    .eq("line_no", item.getWorkOrderLineNo())
                                    .last("LIMIT 1"));
                    if (wom != null) {
                        BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                        if (isReturn) {
                            BigDecimal currentReturned = wom.getReturnedQuantity() != null
                                    ? wom.getReturnedQuantity() : BigDecimal.ZERO;
                            wom.setReturnedQuantity(currentReturned.add(qty));
                        } else {
                            BigDecimal currentIssued = wom.getIssuedQuantity() != null
                                    ? wom.getIssuedQuantity() : BigDecimal.ZERO;
                            BigDecimal newIssued = currentIssued.add(qty);
                            wom.setIssuedQuantity(newIssued);
                            // 【2026-09-11 材料成本归集】累计该工单用料的材料成本并重算加权单位成本。
                            //   原实现只回写数量，unit_cost/total_cost 全库无任何写入点（恒 NULL），
                            //   导致生产入库时取不到产成品材料成本。
                            BigDecimal lineAmount = item.getAmount() != null
                                    ? item.getAmount()
                                    : (item.getUnitPrice() != null ? qty.multiply(item.getUnitPrice()) : BigDecimal.ZERO);
                            BigDecimal newTotal = (wom.getTotalCost() != null ? wom.getTotalCost() : BigDecimal.ZERO)
                                    .add(lineAmount);
                            wom.setTotalCost(newTotal);
                            if (newIssued.compareTo(BigDecimal.ZERO) > 0) {
                                wom.setUnitCost(newTotal.divide(newIssued, 4, java.math.RoundingMode.HALF_UP));
                            }
                        }
                        workOrderMaterialMapper.updateById(wom);
                    }
                }
                log.info("工单 {} 用料明细{}回写完成", workOrder.getOrderNo(), isReturn ? "退料数量" : "已领数量");
            }
        }

        log.info("生产领料单审核: {} 类型: {} 审核人: {}", requisition.getRequisitionNo(), billType, approver);
    }

    /** 重置草稿：本单据状态守卫保留，状态迁移/下游校验由统一状态机执行，领退料冲回在 onReverseApproved 钩子 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        MaterialRequisition bill = getById(id);
        if (bill == null) throw new BusinessException("生产领料单不存在");
        if (!"已生效".equals(bill.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<MaterialRequisition>().eq("requisition_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 重置草稿影响：与 onApproved 严格镜像（领料/补料回冲入库、退料扣回、工单用料反向回写） */
    @Override
    public void onReverseApproved(MaterialRequisition requisition) {
        Long id = requisition.getRequisitionId();
        String operator = requisition.getApprovedBy();
        String billType = requisition.getBillType();
        boolean isReturn = "RETURN".equals(billType);
        List<MaterialRequisitionItem> items = itemMapper.selectList(
                new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (MaterialRequisitionItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : requisition.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    continue;
                }
                if (isReturn) {
                    // 退料重置草稿 = 撤销入库 = 按退回时冻结成本扣减库存
                    BigDecimal returnCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                            ? item.getAvgCost()
                            : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                    inventoryService.deductStockAtCost(
                            stockId,
                            item.getBinId(),
                            materialId,
                            item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), quantity),
                            returnCost,
                            requisition.getRequisitionNo(),
                            "MATERIAL_RETURN_REVERSE",
                            "MaterialRequisition",
                            requisition.getRequisitionNo(),
                            operator);
                } else {
                    // 领料/补料重置草稿 = 撤销出库 = 按审核时冻结的出库成本加回库存
                    BigDecimal issueCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                            ? item.getAvgCost()
                            : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                    inventoryService.addStock(
                            stockId,
                            item.getBinId(),
                            materialId,
                            item.getBatchNo(),
                            stockQuantity(item.effectiveBaseQuantity(), quantity),
                            issueCost,
                            requisition.getRequisitionNo(),
                            "MATERIAL_REQUISITION_REVERSE",
                            "MaterialRequisition",
                            requisition.getRequisitionNo(),
                            operator);
                }
            }
        }
        // 状态迁移已由统一状态机完成

        // 重置草稿: 清空本单明细的实领数量(与库存回滚保持一致)
        if (items != null && !isReturn) {
            for (MaterialRequisitionItem item : items) {
                item.setIssuedQuantity(BigDecimal.ZERO);
                itemMapper.updateById(item);
            }
        }

        // 反向回写工单用料明细:
        //  - 领料/补料: issued_quantity -= 本次领料数量
        //  - 退料: returned_quantity -= 本次退料数量
        if (requisition.getWorkOrderNo() != null && !requisition.getWorkOrderNo().isBlank() && items != null) {
            WorkOrder workOrder = workOrderMapper.selectOne(
                    new QueryWrapper<WorkOrder>().eq("order_no", requisition.getWorkOrderNo()).last("LIMIT 1"));
            if (workOrder != null) {
                for (MaterialRequisitionItem item : items) {
                    if (item.getWorkOrderLineNo() == null) {
                        continue;
                    }
                    WorkOrderMaterial wom = workOrderMaterialMapper.selectOne(
                            new QueryWrapper<WorkOrderMaterial>()
                                    .eq("order_id", workOrder.getOrderId())
                                    .eq("line_no", item.getWorkOrderLineNo())
                                    .last("LIMIT 1"));
                    if (wom != null) {
                        BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                        if (isReturn) {
                            BigDecimal currentReturned = wom.getReturnedQuantity() != null
                                    ? wom.getReturnedQuantity() : BigDecimal.ZERO;
                            wom.setReturnedQuantity(currentReturned.subtract(qty));
                            if (wom.getReturnedQuantity().compareTo(BigDecimal.ZERO) < 0) {
                                wom.setReturnedQuantity(BigDecimal.ZERO);
                            }
                        } else {
                            BigDecimal currentIssued = wom.getIssuedQuantity() != null
                                    ? wom.getIssuedQuantity() : BigDecimal.ZERO;
                            wom.setIssuedQuantity(currentIssued.subtract(qty));
                            if (wom.getIssuedQuantity().compareTo(BigDecimal.ZERO) < 0) {
                                wom.setIssuedQuantity(BigDecimal.ZERO);
                            }
                        }
                        workOrderMaterialMapper.updateById(wom);
                    }
                }
                log.info("工单 {} 用料明细{}反向回写完成", workOrder.getOrderNo(), isReturn ? "退料数量" : "已领数量");
            }
        }

        log.info("生产领料单重置草稿: {} 类型: {} 操作人: {}", requisition.getRequisitionNo(), billType, operator);
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        super.close(id, closer, reason);
    }

    /** 结案影响：保留业务执行态双写 */
    @Override
    public void onClosed(MaterialRequisition bill) {
        update(new UpdateWrapper<MaterialRequisition>().eq("requisition_id", bill.getRequisitionId())
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
    public void onReopened(MaterialRequisition bill) {
        update(new UpdateWrapper<MaterialRequisition>().eq("requisition_id", bill.getRequisitionId())
                .set("bus_status", null));
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        MaterialRequisition requisition = getById(id);
        if (requisition == null) throw new BusinessException("生产领料单不存在");
        if (!"草稿".equals(requisition.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + requisition.getStatus());
        }
        requisition.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            requisition.setRemark((requisition.getRemark() != null ? requisition.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(requisition);
        log.info("生产领料单取消: {} 原因: {}", requisition.getRequisitionNo(), reason);
    }

    @Override
    public String generateRequisitionNo() {
        String docType = "MATERIAL_REQUISITION";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "MOPICK-" + dateStr + "-";
            QueryWrapper<MaterialRequisition> wrapper = new QueryWrapper<>();
            wrapper.likeRight("requisition_no", prefix).orderByDesc("requisition_no").last("LIMIT 1");
            MaterialRequisition last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getRequisitionNo() != null) {
                try {
                    seq = Integer.parseInt(last.getRequisitionNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public String generateRequisitionNo(String billType) {
        String docType = "MATERIAL_REQUISITION";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix;
            if ("RETURN".equals(billType)) {
                prefix = "MORET-" + dateStr + "-";
            } else if ("SUPPLEMENT".equals(billType)) {
                prefix = "MOSUP-" + dateStr + "-";
            } else {
                prefix = "MOPICK-" + dateStr + "-";
            }
            QueryWrapper<MaterialRequisition> wrapper = new QueryWrapper<>();
            wrapper.likeRight("requisition_no", prefix).orderByDesc("requisition_no").last("LIMIT 1");
            MaterialRequisition last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getRequisitionNo() != null) {
                try {
                    seq = Integer.parseInt(last.getRequisitionNo().substring(prefix.length())) + 1;
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

    // ============ 审批引擎回调桥接（BillApprovalCallback） ============
    // 【一表三身份】生产领料单/生产退料单/生产补料单共用 material_requisition 表与本服务，
    // 身份由单据记录自身的 bill_type 字段区分（PICK/RETURN/SUPPLEMENT）。
    // 提交时按身份选审批开关与流程；回写时三个身份共用同一套审核/驳回逻辑（approve 内部已按 RETURN 分流库存方向）。

    /** 主身份编码（保留给「未重写 supportedBillTypes 的调用方」使用，与设置中心开关编码一致） */
    @Override
    public String getBillType() {
        return "MATERIAL_REQUISITION";
    }

    /** 本服务支持的三个单据身份：领料 / 退料 / 补料（审批引擎按该集合把结果路由回本服务） */
    @Override
    public Collection<String> supportedBillTypes() {
        return List.of("MATERIAL_REQUISITION", "MATERIAL_RETURN", "MATERIAL_SUPPLEMENT");
    }

    /** 审批流程全部节点通过 → 复用本类 approve：置「已生效」+ 按 RETURN 分流出入库 */
    @Override
    @Transactional
    public void onApprovalApproved(Long billId, String approver, String comment) {
        approve(billId, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    /**
     * 审批流程全部节点通过（带单据身份）：领料/退料/补料三身份共用一个服务，
     * 身份以审批任务传入的 billType 为准（记录自身 bill_type 仅作兜底校验），
     * 避免"任务身份与记录身份不一致"时静默按错误方向走库存。
     */
    @Override
    @Transactional
    public void onApprovalApproved(String billType, Long billId, String operator, String comment) {
        checkIdentityAgainstTask(billId, billType);
        onApprovalApproved(billId, operator, comment);
    }

    /** 审批驳回 → 复用本类 reject：单据退回「草稿」且 approval_status=驳回 */
    @Override
    @Transactional
    public void onApprovalRejected(Long billId, String rejector, String comment) {
        reject(billId, rejector, comment);
    }

    /** 审批驳回（带单据身份） */
    @Override
    @Transactional
    public void onApprovalRejected(String billType, Long billId, String operator, String comment) {
        checkIdentityAgainstTask(billId, billType);
        onApprovalRejected(billId, operator, comment);
    }

    /**
     * 用审批任务传入的身份校验单据记录身份（三身份共表，记录 bill_type 为库存方向的权威来源，故不一致只告警不改数据）。
     */
    private void checkIdentityAgainstTask(Long billId, String approvalBillType) {
        if (billId == null || approvalBillType == null || approvalBillType.isBlank()) return;
        MaterialRequisition requisition = getById(billId);
        if (requisition == null) return;
        String recordType = resolveBillApprovalType(requisition);
        if (!approvalBillType.equals(recordType)) {
            log.warn("审批任务身份与单据记录身份不一致: requisitionId={}, 任务={}, 记录={}",
                    billId, approvalBillType, recordType);
        }
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行，冲回在 onVoided 钩子 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 作废影响：与重置草稿严格镜像（领退料库存与工单用量冲回） */
    @Override
    public void onVoided(MaterialRequisition bill) {
        onReverseApproved(bill);
    }

    /**
     * 反作废影响（2026-11-09）：作废时已冲回领料/退料/补料库存与工单用量，
     * 反作废恢复到「已生效」必须重新过账，否则库存与工单用量双少（跨模块验收实锤）。与 onVoided 严格镜像。
     */
    @Override
    public void onUnvoided(MaterialRequisition bill) {
        onApproved(bill);
    }

    /** 反作废：状态与作废痕迹清理由统一状态机执行 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 删除：草稿校验 + 下游校验 + 整单级联删除由统一状态机执行（成本核算引用守卫在 removeById 内保留） */
    @Override
    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
