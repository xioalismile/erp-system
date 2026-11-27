package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.basedata.service.EmployeeService;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import com.erp.common.util.CustomFieldSearchHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service

public class OperationReportServiceImpl extends AbstractBillService<OperationReportMapper, OperationReport> implements OperationReportService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与工序汇报单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 报工员工：列是员工外键（employee_id），按名称筛的下拉传的是员工 ID → 翻译成 employeeId 属性。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("reportNo", new BillHeaderFilterSupport.Spec("reportNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("productionOrderNo", new BillHeaderFilterSupport.Spec("productionOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("processNo", new BillHeaderFilterSupport.Spec("processNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("operationName", new BillHeaderFilterSupport.Spec("operationName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("workCenterName", new BillHeaderFilterSupport.Spec("workCenterName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("reportType", new BillHeaderFilterSupport.Spec("reportType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("shiftType", new BillHeaderFilterSupport.Spec("shiftType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("employeeName", new BillHeaderFilterSupport.Spec("employeeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("reportDate", new BillHeaderFilterSupport.Spec("reportDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("qualifiedQuantity", new BillHeaderFilterSupport.Spec("qualifiedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("rejectedQuantity", new BillHeaderFilterSupport.Spec("rejectedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("workHours", new BillHeaderFilterSupport.Spec("workHours", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("overtimeHours", new BillHeaderFilterSupport.Spec("overtimeHours", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("operationReport", OperationReport.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "bom_id",
            "created_at",
            "created_by",
            "employee_id",
            "employee_name",
            "is_primary",
            "line_no",
            "material_id",
            "operation_id",
            "operation_name",
            "order_id",
            "overtime_hours",
            "process_no",
            "production_order_id",
            "production_order_no",
            "qualified_quantity",
            "rejected_quantity",
            "remark",
            "report_date",
            "report_id",
            "report_no",
            "report_type",
            "shift_type",
            "source_bill_no",
            "source_bill_type",
            "status",
            "total_quantity",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by",
            "work_center_id",
            "work_hours",
            "workstation_id"
        );

    private final WorkOrderMapper workOrderMapper;
    /** 补料领料单生成器（唯一入口）：报工报废 / 工序回退判废下推 共用同一套口径 */
    private final ProductionSupplementHelper supplementHelper;
    private final com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper costCalculationItemMapper;
    private final ProductionReceiptService productionReceiptService;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final EmployeeService employeeService;

    /**
     * 工序状态流转统一入口（2026-09-23 第 2 期）：报工审核驱动的工序状态必须经它，
     * 保证状态取值与"人工点按钮"完全同一套口径，并自动写入工序操作日志。
     */
    private final com.erp.production.service.WorkOrderOperationService workOrderOperationService;

    /** 下游引用校验（重置草稿/作废/删除前禁止存在已生效下游） */
    @Autowired
    private BillRelationService billRelationService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤工序汇报单列表 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    public OperationReportServiceImpl(WorkOrderMapper workOrderMapper,
                                       ProductionSupplementHelper supplementHelper,
                                       com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper costCalculationItemMapper,
                                       @Lazy ProductionReceiptService productionReceiptService,
                                       WorkOrderOperationMapper workOrderOperationMapper,
                                       SysSerialNoConfigService sysSerialNoConfigService,
                                       EmployeeService employeeService,
                                       com.erp.production.service.WorkOrderOperationService workOrderOperationService) {
        this.workOrderMapper = workOrderMapper;
        this.supplementHelper = supplementHelper;
        this.costCalculationItemMapper = costCalculationItemMapper;
        this.productionReceiptService = productionReceiptService;
        this.workOrderOperationMapper = workOrderOperationMapper;
        this.sysSerialNoConfigService = sysSerialNoConfigService;
        this.employeeService = employeeService;
        this.workOrderOperationService = workOrderOperationService;
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "工序汇报单"; }
    @Override protected String getApprovalBillType() { return "OPERATION_REPORT"; }
    @Override protected String getBillNo(OperationReport b) { return b.getReportNo(); }
    @Override protected Object getId(OperationReport b) { return b.getReportId(); }
    @Override protected String getStatus(OperationReport b) { return b.getStatus(); }
    @Override protected void setStatus(OperationReport b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(OperationReport b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(OperationReport b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(OperationReport b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(OperationReport b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(OperationReport b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(OperationReport b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(OperationReport b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(OperationReport b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(OperationReport b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(OperationReport b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(OperationReport b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(OperationReport b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(OperationReport b, String v) { b.setVoidReason(v); }

    @Override
    public List<String> listApprovedDownstreamBills(OperationReport bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReportNo());
    }

    /** 提交审核前严格校验（原 submit 内的校验整体搬入） */
    @Override
    protected void validateStrictSubmit(OperationReport report) {
        if (report.getEmployeeId() != null && employeeService.getById(report.getEmployeeId()) == null) {
            throw new BusinessException("关联汇报员工不存在(employeeId=" + report.getEmployeeId() + ")，请重新选择员工");
        }
        if (report.getProductionOrderId() != null && workOrderMapper.selectById(report.getProductionOrderId()) == null) {
            throw new BusinessException("关联生产工单不存在(productionOrderId=" + report.getProductionOrderId() + ")，请重新选择工单");
        }
        BigDecimal total = report.getTotalQuantity() != null ? report.getTotalQuantity() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("加工总数必须大于0，请填写加工总数");
        }
        validateQuantities(report);
    }

    /** 审核生效影响：工单已报工合格量累加 + 工单工序实例状态/数量驱动 */
    @Override
    public void onApproved(OperationReport report) {
        BigDecimal qualifiedQty = report.getQualifiedQuantity() != null ? report.getQualifiedQuantity() : BigDecimal.ZERO;
        if (report.getProductionOrderId() != null && qualifiedQty.compareTo(BigDecimal.ZERO) > 0) {
            WorkOrder workOrder = workOrderMapper.selectById(report.getProductionOrderId());
            if (workOrder != null) {
                BigDecimal currentReported = workOrder.getReportedQuantity() != null
                        ? workOrder.getReportedQuantity() : BigDecimal.ZERO;
                workOrder.setReportedQuantity(currentReported.add(qualifiedQty));
                workOrderMapper.updateById(workOrder);
                log.info("工单 {} 已报工合格量更新为: {}", workOrder.getOrderNo(), workOrder.getReportedQuantity());
            }
        }
        updateWorkOrderOperationOnApprove(report, qualifiedQty);
    }

    /** 重置草稿冲回：工单已报工合格量扣减 + 工单工序实例状态回退（与 onApproved 严格镜像） */
    @Override
    public void onReverseApproved(OperationReport report) {
        reverseReportedQuantity(report);
    }

    /** 作废冲回：作废前为已生效时，按 onApproved 的反向口径冲回 */
    @Override
    public void onVoided(OperationReport report) {
        reverseReportedQuantity(report);
    }

    /** 反作废恢复：恢复为已生效时，重新释放生效影响 */
    @Override
    public void onUnvoided(OperationReport report) {
        onApproved(report);
    }

    /** 已报工合格量/工序实例数量的反向冲回（重置草稿与作废共用同一口径） */
    private void reverseReportedQuantity(OperationReport report) {
        BigDecimal qualifiedQty = report.getQualifiedQuantity() != null ? report.getQualifiedQuantity() : BigDecimal.ZERO;
        if (report.getProductionOrderId() != null && qualifiedQty.compareTo(BigDecimal.ZERO) > 0) {
            WorkOrder workOrder = workOrderMapper.selectById(report.getProductionOrderId());
            if (workOrder != null) {
                BigDecimal currentReported = workOrder.getReportedQuantity() != null
                        ? workOrder.getReportedQuantity() : BigDecimal.ZERO;
                workOrder.setReportedQuantity(currentReported.subtract(qualifiedQty));
                if (workOrder.getReportedQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    workOrder.setReportedQuantity(BigDecimal.ZERO);
                }
                workOrderMapper.updateById(workOrder);
                log.info("工单 {} 已报工合格量反向扣减为: {}", workOrder.getOrderNo(), workOrder.getReportedQuantity());
            }
        }
        updateWorkOrderOperationOnReverseApprove(report, qualifiedQty);
    }

    /**
     * 删除报工单 - 增强校验:
     * 1) 状态校验: 非草稿族（草稿/已驳回/已取消以外）不可删除
     * 2) 成本核算联动: 若该单据已用于成本核算，禁止删除，需先反确认核算单
     */
    @Override
    @Transactional
    public boolean removeById(java.io.Serializable id) {
        OperationReport existing = getById(id);
        if (existing == null) {
            throw new BusinessException("报工单不存在");
        }
        String st = existing.getStatus();
        if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(st))) {
            throw new BusinessException("非草稿不能删除（当前状态：" + st + "），请先重置草稿");
        }
        // 成本核算联动检查
        Long usedCount = costCalculationItemMapper.selectCount(
                new QueryWrapper<com.erp.finance.cost.calculation.entity.CostCalculationItem>()
                        .like("remark", existing.getReportNo()));
        if (usedCount != null && usedCount > 0) {
            throw new BusinessException("该报工单已用于成本核算(" + usedCount + "处引用)，"
                    + "请先反确认相关核算单后再删除");
        }
        boolean ok = super.removeById(id);
        log.info("删除报工单: {} 结果: {}", existing.getReportNo(), ok);
        return ok;
    }

    @Override
    @Transactional
    public void saveReport(OperationReport report) {
        boolean isNew = report.getReportId() == null;
        if (!isNew) {
            OperationReport existing = getById(report.getReportId());
            if (existing != null) {
                BillStatusUtils.assertDirectModifiable("生产补料单", existing.getReportNo(), existing.getStatus(), existing.getCloseStatus());
            }
        }

        if (report.getReportNo() == null || report.getReportNo().isBlank()) {
            report.setReportNo(generateReportNo());
        }
        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (report.getStatus() == null || report.getStatus().isBlank()) report.setStatus("草稿");
        if (report.getApprovalStatus() == null || report.getApprovalStatus().isBlank()
                || ("草稿".equals(report.getStatus()) && "待审核".equals(report.getApprovalStatus()))) {
            report.setApprovalStatus("草稿");
        }
        if (report.getReportDate() == null) report.setReportDate(LocalDate.now());
        if (report.getReportType() == null) report.setReportType("工序汇报");
        if (report.getShiftType() == null) report.setShiftType("白班");
        if (report.getTotalQuantity() == null) report.setTotalQuantity(BigDecimal.ZERO);
        if (report.getQualifiedQuantity() == null) report.setQualifiedQuantity(BigDecimal.ZERO);
        if (report.getRejectedQuantity() == null) report.setRejectedQuantity(BigDecimal.ZERO);
        if (report.getWorkHours() == null) report.setWorkHours(BigDecimal.ZERO);
        if (report.getOvertimeHours() == null) report.setOvertimeHours(BigDecimal.ZERO);

        // 保存=草稿宽松：数量一致性校验（「合格数量 + 报废数量 = 加工总数」）不在保存时执行，
        // 统一在提交(submit)/审核(approve)时由 validateQuantities 校验
        BigDecimal rejected = report.getRejectedQuantity() != null ? report.getRejectedQuantity() : BigDecimal.ZERO;

        if (isNew) {
            save(report);
        } else {
            updateById(report);
        }

        // 更新工单累计报废数量 (编辑时先扣减旧值再加新值, 避免双重累加)
        if (report.getProductionOrderId() != null) {
            WorkOrder workOrder = workOrderMapper.selectById(report.getProductionOrderId());
            if (workOrder != null) {
                BigDecimal currentRejected = workOrder.getRejectedQuantity() != null ? workOrder.getRejectedQuantity() : BigDecimal.ZERO;
                if (!isNew) {
                    // 编辑时扣减旧报废数量
                    OperationReport oldReport = getById(report.getReportId());
                    if (oldReport != null && oldReport.getRejectedQuantity() != null) {
                        currentRejected = currentRejected.subtract(oldReport.getRejectedQuantity());
                        if (currentRejected.compareTo(BigDecimal.ZERO) < 0) currentRejected = BigDecimal.ZERO;
                    }
                }
                currentRejected = currentRejected.add(rejected);
                if (currentRejected.compareTo(BigDecimal.ZERO) < 0) currentRejected = BigDecimal.ZERO;
                workOrder.setRejectedQuantity(currentRejected);
                workOrderMapper.updateById(workOrder);
                log.info("工单 {} 累计报废数量更新为: {}", workOrder.getOrderNo(), workOrder.getRejectedQuantity());
            }
        }

        // 如果有报废，自动生成补料领料单
        // （口径与"工序回退判废 → 下推补料"完全一致，统一走 ProductionSupplementHelper，杜绝两处各算一套数量）
        if (rejected.compareTo(BigDecimal.ZERO) > 0) {
            try {
                WorkOrder wo = report.getProductionOrderId() == null
                        ? null : workOrderMapper.selectById(report.getProductionOrderId());
                supplementHelper.generateSupplement(wo, rejected,
                        "工序[" + report.getOperationName() + "]报废" + rejected + "件，自动补料",
                        "报工单 " + report.getReportNo() + " 工序[" + report.getOperationName()
                                + "]报废" + rejected + "件，系统自动生成补料单");
            } catch (Exception e) {
                log.warn("自动补料领料单生成失败，不影响报工: {}", e.getMessage());
            }
        }

        // 口径统一（2026-09-21 用户指令「按标准来」）：保存=草稿，保存链路里绝不自动审核。
        // 历史写法是「免审批时保存后自动审核生效」，导致用户只点一次保存就已入账/已生效、且不能再修改，
        // 与全站口径「保存=草稿、提交才生效」冲突。生效动作一律交给提交侧（免审批时提交即生效）。

        log.info("工序汇报单{}: {}", isNew ? "新增" : "编辑", report.getReportNo());
    }

    /**
     * 补料领料单生成已收口到 {@link ProductionSupplementHelper}（唯一入口）：
     * 报工报废与工序回退判废下推共用同一套口径（数量 = 报废/判废数 × 主BOM 子件用量、单据 = 补料单草稿）。
     */

    @Override
    public PageResult<OperationReport> pageQuery(int current, int size, String keyword, String status,
                                                 String startDate, String endDate, String employeeId,
                                                 String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder,
                null, java.util.Collections.emptyMap());
    }

    /** 老签名（仅表头字段过滤）委托新重载（customSearch 空 = 行为与改造前完全一致） */
    @Override
    public PageResult<OperationReport> pageQuery(int current, int size, String keyword, String status,
                                                 String startDate, String endDate, String employeeId,
                                                 String sortBy, String sortOrder,
                                                 java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤 + 自定义字段搜索」（2026-10-14 / 2026-11-24）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<OperationReport> pageQuery(int current, int size, String keyword, String status,
                                                 String startDate, String endDate, String employeeId,
                                                 String sortBy, String sortOrder, String customSearch,
                                                 java.util.Map<String, String> headerFilters) {
        QueryWrapper<OperationReport> wrapper = new QueryWrapper<>();
        // 关键词（本页真实列 report_no / production_order_no / operation_name / employee_name）
        // —— 多列匹配统一走 applyKeywordGroup（强制成组，禁止平铺 or()：平铺会让命中第一列的行绕过状态筛选与数据权限）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword,
                "report_no", "production_order_no", "operation_name", "employee_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 report_date）；员工=报工员工（本页真实列 employee_id，统一入参 employeeId）
        ListQuerySupport.applyDateRange(wrapper, "report_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "employee_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "operationReport", headerFilters);
        // 数据权限：按角色 data_scope（统一数据权限）
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "operation_report", customSearch, CustomFieldSearchHelper.idColumnOf("operation_report"));
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, OperationReport.class);
        Page<OperationReport> page = new Page<>(current, size);
        Page<OperationReport> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public OperationReport getDetail(Long id) {
        OperationReport report = getById(id);
        if (report == null) throw new BusinessException("工序汇报单不存在");
        return report;
    }

    @Override
    @Transactional
    public void submit(Long id, String operator) {
        // 提交校验 + 审批开关 + 免审批直通 + 创建审批待办，全部由统一状态机执行
        super.submitForApprovalById(id, operator);
    }

    /** 审核: 草稿/待审核 → 已生效（含「审批在途禁止绕过」防护，业务影响由 onApproved 释放） */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("OPERATION_REPORT", (Long) id) != null) {
            throw new BusinessException("工序汇报单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 重置草稿: 已生效 → 草稿（状态/下游校验由基类执行，回冲由 onReverseApproved 执行） */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<OperationReport> uw = new UpdateWrapper<>();
        uw.eq("report_id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    /**
     * 报工审核通过时, 同步更新对应的工单工序实例:
     *  - 累加 reportedQuantity
     *  - 累加 rejectedQuantity
     *  - 如果 reportedQuantity >= planQuantity, 状态改为"已完成", 记录 actualEndDate
     *  - 否则状态改为"进行中", 若无 actualStartDate 则记录
     */
    private void updateWorkOrderOperationOnApprove(OperationReport report, BigDecimal qualifiedQty) {
        if (report.getProductionOrderId() == null || report.getOperationId() == null) {
            return;
        }
        WorkOrderOperation op = findWorkOrderOperation(report.getProductionOrderId(), report.getOperationId());
        if (op == null) {
            return;
        }
        BigDecimal rejectedQty = report.getRejectedQuantity() != null ? report.getRejectedQuantity() : BigDecimal.ZERO;
        BigDecimal currentReported = op.getReportedQuantity() != null ? op.getReportedQuantity() : BigDecimal.ZERO;
        BigDecimal currentRejected = op.getRejectedQuantity() != null ? op.getRejectedQuantity() : BigDecimal.ZERO;
        op.setReportedQuantity(currentReported.add(qualifiedQty));
        op.setRejectedQuantity(currentRejected.add(rejectedQty));

        BigDecimal planQty = op.getPlanQuantity() != null ? op.getPlanQuantity() : BigDecimal.ZERO;
        String fromStatus = op.getOperationStatus();
        if (planQty.compareTo(BigDecimal.ZERO) > 0 && op.getReportedQuantity().compareTo(planQty) >= 0) {
            // 需质检工序：报工量到位后先落"完工待检"，检验通过才算已完工（2026-09-23 第 2 期口径）
            boolean qc = op.getIsQcRequired() != null && op.getIsQcRequired() == 1;
            op.setOperationStatus(qc
                    ? com.erp.production.service.WorkOperationStatus.WAIT_QC
                    : com.erp.production.service.WorkOperationStatus.FINISHED);
            if (!qc && op.getActualEndDate() == null) {
                op.setActualEndDate(LocalDate.now());
            }
        } else {
            op.setOperationStatus(com.erp.production.service.WorkOperationStatus.RUNNING);
            if (op.getActualStartDate() == null) {
                op.setActualStartDate(LocalDate.now());
            }
        }
        op.setOutputQuantity(op.getReportedQuantity());
        workOrderOperationMapper.updateById(op);
        // 单据驱动也留痕（守则 11：单据回写才是真状态，必须可追溯）
        workOrderOperationService.recordDriven(op, fromStatus, op.getOperationStatus(), "工序汇报单审核", null);
        log.info("工单工序实例[{}][{}] 报工审核驱动状态: {} 已报工: {}",
                op.getOrderNo(), op.getOperationName(), op.getOperationStatus(), op.getReportedQuantity());
    }

    /** 报工重置草稿时, 反向扣减工单工序实例的累计数量并回退状态 */
    private void updateWorkOrderOperationOnReverseApprove(OperationReport report, BigDecimal qualifiedQty) {
        if (report.getProductionOrderId() == null || report.getOperationId() == null) {
            return;
        }
        WorkOrderOperation op = findWorkOrderOperation(report.getProductionOrderId(), report.getOperationId());
        if (op == null) {
            return;
        }
        BigDecimal rejectedQty = report.getRejectedQuantity() != null ? report.getRejectedQuantity() : BigDecimal.ZERO;
        BigDecimal currentReported = op.getReportedQuantity() != null ? op.getReportedQuantity() : BigDecimal.ZERO;
        BigDecimal currentRejected = op.getRejectedQuantity() != null ? op.getRejectedQuantity() : BigDecimal.ZERO;
        op.setReportedQuantity(currentReported.subtract(qualifiedQty));
        if (op.getReportedQuantity().compareTo(BigDecimal.ZERO) < 0) {
            op.setReportedQuantity(BigDecimal.ZERO);
        }
        op.setRejectedQuantity(currentRejected.subtract(rejectedQty));
        if (op.getRejectedQuantity().compareTo(BigDecimal.ZERO) < 0) {
            op.setRejectedQuantity(BigDecimal.ZERO);
        }
        // 若扣减后已报工量小于计划量, 则状态回退为"进行中"
        BigDecimal planQty = op.getPlanQuantity() != null ? op.getPlanQuantity() : BigDecimal.ZERO;
        String fromStatus = op.getOperationStatus();
        if ((com.erp.production.service.WorkOperationStatus.FINISHED.equals(op.getOperationStatus())
                || com.erp.production.service.WorkOperationStatus.WAIT_QC.equals(op.getOperationStatus()))
                && (planQty.compareTo(BigDecimal.ZERO) == 0 || op.getReportedQuantity().compareTo(planQty) < 0)) {
            op.setOperationStatus(com.erp.production.service.WorkOperationStatus.RUNNING);
            op.setActualEndDate(null);
        }
        if (op.getReportedQuantity().compareTo(BigDecimal.ZERO) == 0) {
            op.setOperationStatus(com.erp.production.service.WorkOperationStatus.PENDING_ASSIGN);
            op.setActualStartDate(null);
            op.setActualEndDate(null);
        }
        op.setOutputQuantity(op.getReportedQuantity());
        workOrderOperationMapper.updateById(op);
        workOrderOperationService.recordDriven(op, fromStatus, op.getOperationStatus(), "工序汇报单重置草稿", null);
        log.info("工单工序实例[{}][{}] 报工重置草稿回退状态: {} 已报工: {}",
                op.getOrderNo(), op.getOperationName(), op.getOperationStatus(), op.getReportedQuantity());
    }

    /** 按工单ID+工序ID查找工单工序实例 */
    private WorkOrderOperation findWorkOrderOperation(Long orderId, Long operationId) {
        return workOrderOperationMapper.selectOne(
                new QueryWrapper<WorkOrderOperation>()
                        .eq("order_id", orderId)
                        .eq("operation_id", operationId)
                        .last("LIMIT 1"));
    }

    /** 校验: 合格数量 + 报废数量 = 加工总数 */
    private void validateQuantities(OperationReport report) {
        BigDecimal qualified = report.getQualifiedQuantity() != null ? report.getQualifiedQuantity() : BigDecimal.ZERO;
        BigDecimal rejected = report.getRejectedQuantity() != null ? report.getRejectedQuantity() : BigDecimal.ZERO;
        BigDecimal total = report.getTotalQuantity() != null ? report.getTotalQuantity() : BigDecimal.ZERO;
        if (qualified.add(rejected).compareTo(total) != 0) {
            throw new BusinessException("合格数量(" + qualified + ") + 报废数量(" + rejected
                    + ") 必须等于加工总数(" + total + ")");
        }
    }

    @Override
    public String generateReportNo() {
        String docType = "OPERATION_REPORT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "PRP-" + dateStr + "-";
            QueryWrapper<OperationReport> wrapper = new QueryWrapper<>();
            wrapper.likeRight("report_no", prefix).orderByDesc("report_no").last("LIMIT 1");
            OperationReport last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReportNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReportNo().substring(prefix.length())) + 1;
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
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 结案: 已生效 → close_status=1 (status 保持已生效, 前端按 closeStatus 渲染已结案) */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案: close_status=0 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    /** 删除报工单(仅草稿族可删且无下游已生效单据; 成本核算联动校验在 removeById 内保留) */
    @Override
    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
