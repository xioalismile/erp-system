package com.erp.finance.wage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Department;
import com.erp.basedata.mapper.DepartmentMapper;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.finance.wage.dto.EmployeeRateDTO;
import com.erp.finance.wage.dto.WageSummaryDTO;
import com.erp.finance.wage.entity.WageSettlement;
import com.erp.finance.wage.entity.WageSettlementItem;
import com.erp.finance.wage.mapper.WageSettlementItemMapper;
import com.erp.finance.wage.mapper.WageSettlementMapper;
import com.erp.finance.wage.service.WageSettlementService;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.RoutingOperationMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;

/**
 * 工序工资核算服务实现。
 *
 * <p>数据源 = 已生效的工序汇报单 operation_report（status = 已生效，report_date 落在期间内），
 * 按 employee_id 聚合：</p>
 * <ul>
 *   <li>合格数量 = Σ qualified_quantity</li>
 *   <li>计件工资 = Σ(qualified_quantity × 计件单价)；计件单价取数顺序：
 *       work_order_operation(order_id+operation_id).piece_rate → routing_operation(operation_id).piece_rate → 0（不报错）</li>
 *   <li>计时工资 = Σ(work_hours + overtime_hours) × employee.hourly_rate（无费率或为 0 按 0 计，不报错）</li>
 *   <li>合计 = 计件工资 + 计时工资</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WageSettlementServiceImpl extends com.erp.common.bill.AbstractBillService<WageSettlementMapper, WageSettlement>
        implements WageSettlementService {

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.bill.BillRelationService billRelationService;


    private static final String SOURCE_STATUS = "已生效";
    private static final int MONEY_SCALE = 2;
    private static final int QTY_SCALE = 4;
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final WageSettlementItemMapper itemMapper;
    private final OperationReportMapper operationReportMapper;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final RoutingOperationMapper routingOperationMapper;
    private final DepartmentMapper departmentMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义 · 财务推广批）。
        // 键 = 前端筛选条件定义键（与「工序工资核算单明细报表」引擎筛选键同键）；值 = 实体属性名。
        // 注意：部门列是 dept_id（**不是** department_id），按名称筛但列是外键 → Spec.column 写外键属性名。
        Map<String, BillHeaderFilterSupport.Spec> f = new LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("settlementNo", new BillHeaderFilterSupport.Spec("settlementNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("createdBy", new BillHeaderFilterSupport.Spec("createdBy", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("period", new BillHeaderFilterSupport.Spec("period", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("deptName", new BillHeaderFilterSupport.Spec("deptId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("employeeName", new BillHeaderFilterSupport.Spec("employeeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 / 数值区间 ──
        f.put("createdAt", new BillHeaderFilterSupport.Spec("createdAt", BillHeaderFilterSupport.Kind.DATE));
        f.put("qualifiedQuantity", new BillHeaderFilterSupport.Spec("qualifiedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("pieceAmount", new BillHeaderFilterSupport.Spec("pieceAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("timeAmount", new BillHeaderFilterSupport.Spec("timeAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("wageSettlement", WageSettlement.class, f);
    }

    /** 计算上下文（一次加载，避免逐行 N+1 查询） */
    private static class CalcContext {
        /** employeeId → 工时费率/部门 */
        Map<Long, EmployeeRateDTO> employeeRates = new HashMap<>();
        /** deptId → 部门名称 */
        Map<Long, String> deptNames = new HashMap<>();
        /** orderId + "_" + operationId → 工单工序（计件单价 + 工序编码） */
        Map<String, WorkOrderOperation> workOrderOperations = new HashMap<>();
        /** operationId → 工艺路线工序（计件单价 + 工序编码，兜底） */
        Map<Long, RoutingOperation> routingOperations = new HashMap<>();
    }

    @Override
    public List<WageSummaryDTO> summary(String period, Long deptId, Long employeeId) {
        YearMonth ym = parsePeriod(period);
        List<OperationReport> reports = loadReports(ym);
        if (reports.isEmpty()) {
            return Collections.emptyList();
        }
        CalcContext ctx = buildContext(reports);
        List<WageSettlementItem> lines = buildLines(ctx, reports, period, deptId, employeeId);

        Map<Long, WageSummaryDTO> agg = new LinkedHashMap<>();
        for (WageSettlementItem line : lines) {
            WageSummaryDTO dto = agg.computeIfAbsent(line.getEmployeeId(), k -> {
                WageSummaryDTO d = new WageSummaryDTO();
                d.setPeriod(period);
                d.setEmployeeId(line.getEmployeeId());
                d.setEmployeeName(line.getEmployeeName());
                EmployeeRateDTO emp = ctx.employeeRates.get(line.getEmployeeId());
                Long empDeptId = emp != null ? emp.getDeptId() : null;
                d.setDeptId(empDeptId);
                d.setDeptName(empDeptId != null ? ctx.deptNames.getOrDefault(empDeptId, "") : "");
                d.setQualifiedQuantity(BigDecimal.ZERO);
                d.setPieceAmount(BigDecimal.ZERO);
                d.setTimeAmount(BigDecimal.ZERO);
                d.setTotalAmount(BigDecimal.ZERO);
                return d;
            });
            dto.setQualifiedQuantity(dto.getQualifiedQuantity().add(nvl(line.getQualifiedQuantity())));
            dto.setPieceAmount(dto.getPieceAmount().add(nvl(line.getPieceAmount())));
            dto.setTimeAmount(dto.getTimeAmount().add(nvl(line.getTimeAmount())));
            dto.setTotalAmount(dto.getTotalAmount().add(nvl(line.getAmount())));
        }
        return new ArrayList<>(agg.values());
    }

    @Override
    public PageResult<WageSettlement> pageQuery(int current, int size, String period, String status, String keyword) {
        QueryWrapper<WageSettlement> wrapper = new QueryWrapper<>();
        if (period != null && !period.isBlank()) {
            wrapper.eq("period", period.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("settlement_no", keyword).or().like("employee_name", keyword));
        }
        wrapper.orderByDesc("settlement_id");
        Page<WageSettlement> page = new Page<>(current, size);
        Page<WageSettlement> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<WageSettlement> pageQuery(int current, int size, String period, String status, String keyword, Map<String, String> headerFilters) {
        QueryWrapper<WageSettlement> wrapper = new QueryWrapper<>();
        if (period != null && !period.isBlank()) {
            wrapper.eq("period", period.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("settlement_no", keyword).or().like("employee_name", keyword));
        }
        // 表头字段过滤：f_* → 白名单翻译（排序口径与上一重载一致）
        BillHeaderFilterSupport.apply(wrapper, "wageSettlement", headerFilters);
        wrapper.orderByDesc("settlement_id");
        Page<WageSettlement> page = new Page<>(current, size);
        Page<WageSettlement> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public WageSettlement getDetail(Long id) {
        WageSettlement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("工资核算单不存在");
        }
        bill.setItems(getItems(id));
        return bill;
    }

    @Override
    public List<WageSettlementItem> getItems(Long id) {
        return itemMapper.selectList(new QueryWrapper<WageSettlementItem>()
                .eq("settlement_id", id)
                .orderByAsc("line_no"));
    }

    @Override
    @Transactional
    public WageSettlement generate(String period, Long deptId, Long employeeId, String operator) {
        YearMonth ym = parsePeriod(period);
        List<OperationReport> reports = loadReports(ym);
        if (reports.isEmpty()) {
            throw new BusinessException("该期间没有已生效的工序汇报数据，无法生成工资核算单");
        }
        CalcContext ctx = buildContext(reports);
        List<WageSettlementItem> lines = buildLines(ctx, reports, period, deptId, employeeId);
        if (lines.isEmpty()) {
            throw new BusinessException("该期间没有符合条件的已生效工序汇报数据，无法生成工资核算单");
        }

        BigDecimal qualifiedQty = BigDecimal.ZERO;
        BigDecimal pieceAmount = BigDecimal.ZERO;
        BigDecimal timeAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (WageSettlementItem line : lines) {
            qualifiedQty = qualifiedQty.add(nvl(line.getQualifiedQuantity()));
            pieceAmount = pieceAmount.add(nvl(line.getPieceAmount()));
            timeAmount = timeAmount.add(nvl(line.getTimeAmount()));
            totalAmount = totalAmount.add(nvl(line.getAmount()));
        }

        WageSettlement bill = new WageSettlement();
        bill.setSettlementNo(generateNo());
        bill.setPeriod(period);
        bill.setDeptId(deptId);
        bill.setDeptName(deptId != null ? ctx.deptNames.getOrDefault(deptId, "") : "");
        bill.setEmployeeId(employeeId);
        bill.setEmployeeName(employeeId != null && ctx.employeeRates.get(employeeId) != null
                ? ctx.employeeRates.get(employeeId).getEmployeeName() : "");
        bill.setQualifiedQuantity(qualifiedQty.setScale(QTY_SCALE, RoundingMode.HALF_UP));
        bill.setPieceAmount(pieceAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        bill.setTimeAmount(timeAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        bill.setTotalAmount(totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        setStatus(bill, "草稿");
        setApprovalStatus(bill, "草稿");
        bill.setRemark(period + " 工序工资核算（操作人：" + (operator == null ? "" : operator) + "）");
        saveDraft(bill);

        for (WageSettlementItem line : lines) {
            line.setItemId(null);
            line.setSettlementId(bill.getSettlementId());
            itemMapper.insert(line);
        }

        log.info("生成工序工资核算单: {} 期间: {} 明细: {} 条 合计: {}",
                bill.getSettlementNo(), period, lines.size(), bill.getTotalAmount());
        return getDetail(bill.getSettlementId());
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "工序工资核算单"; }
    @Override protected String getApprovalBillType() { return "WAGE_SETTLEMENT"; }
    @Override protected String getBillNo(WageSettlement b) { return b.getSettlementNo(); }
    @Override protected Object getId(WageSettlement b) { return b.getSettlementId(); }
    @Override protected String getStatus(WageSettlement b) { return b.getStatus(); }
    @Override protected void setStatus(WageSettlement b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(WageSettlement b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(WageSettlement b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(WageSettlement b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(WageSettlement b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(WageSettlement b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(WageSettlement b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(WageSettlement b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(WageSettlement b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(WageSettlement b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(WageSettlement b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(WageSettlement b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(WageSettlement b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(WageSettlement b, String v) { b.setVoidReason(v); }

    @Override
    public List<String> listApprovedDownstreamBills(WageSettlement bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getSettlementNo());
    }

    @Override
    protected void validateStrictSubmit(WageSettlement bill) {
        // 原提交入口无额外强校验；保持行为不变
    }

    /** 提交：状态校验/审批开关/状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id, String operator) {
        WageSettlement bill = getById(id);
        if (bill == null) throw new BusinessException("工资核算单不存在");
        super.submitForApproval(bill, operator != null && !operator.isBlank() ? operator
                : com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
    }

    @Override
    public void onApproved(WageSettlement bill) {
        log.info("工序工资核算单生效: {} 期间: {} 合计: {}", bill.getSettlementNo(), bill.getPeriod(), bill.getTotalAmount());
    }

    /** 审核：审批在途禁止绕过引擎 + 状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("WAGE_SETTLEMENT", (Long) id) != null) {
            throw new BusinessException("工资核算单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<WageSettlement>().eq("settlement_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 作废：本单据草稿不允许作废（须直接删除）；其余由统一状态机执行 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        WageSettlement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("工资核算单不存在");
        }
        if ("草稿".equals(bill.getStatus())) {
            throw new BusinessException("草稿状态单据无需作废");
        }
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    /** 删除：状态校验/下游校验由统一状态机执行；工资明细表未登记在通用级联表，删除主表前显式清理明细 */
    @Transactional
    public void deleteBill(Long id) {
        itemMapper.delete(new QueryWrapper<WageSettlementItem>().eq("settlement_id", id));
        super.deleteBill(id);
    }

    // ==================== 内部实现 ====================

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new BusinessException("核算期间不能为空");
        }
        try {
            return YearMonth.parse(period.trim(), PERIOD_FMT);
        } catch (Exception e) {
            throw new BusinessException("核算期间格式应为 yyyy-MM");
        }
    }

    /** 期间内已生效的工序汇报单（状态 + 汇报日期） */
    private List<OperationReport> loadReports(YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<OperationReport> reports = operationReportMapper.selectList(new QueryWrapper<OperationReport>()
                .eq("status", SOURCE_STATUS)
                .between("report_date", start, end));
        return reports == null ? Collections.emptyList() : reports;
    }

    private CalcContext buildContext(List<OperationReport> reports) {
        CalcContext ctx = new CalcContext();
        ctx.employeeRates = loadEmployeeRates();
        ctx.deptNames = loadDeptNames();

        Set<Long> orderIds = new HashSet<>();
        Set<Long> operationIds = new HashSet<>();
        for (OperationReport r : reports) {
            if (r.getProductionOrderId() != null && r.getOperationId() != null) {
                orderIds.add(r.getProductionOrderId());
            }
            if (r.getOperationId() != null) {
                operationIds.add(r.getOperationId());
            }
        }

        if (!orderIds.isEmpty()) {
            List<WorkOrderOperation> list = workOrderOperationMapper.selectList(
                    new QueryWrapper<WorkOrderOperation>().in("order_id", orderIds));
            if (list != null) {
                for (WorkOrderOperation w : list) {
                    if (w.getOrderId() != null && w.getOperationId() != null) {
                        ctx.workOrderOperations.put(keyOf(w.getOrderId(), w.getOperationId()), w);
                    }
                }
            }
        }
        if (!operationIds.isEmpty()) {
            List<RoutingOperation> list = routingOperationMapper.selectList(
                    new QueryWrapper<RoutingOperation>().in("operation_id", operationIds));
            if (list != null) {
                for (RoutingOperation r : list) {
                    if (r.getOperationId() != null) {
                        ctx.routingOperations.put(r.getOperationId(), r);
                    }
                }
            }
        }
        return ctx;
    }

    /** 员工工时费率（列缺失/查询异常时降级为空 → 计时工资按 0 计，不阻断核算） */
    private Map<Long, EmployeeRateDTO> loadEmployeeRates() {
        Map<Long, EmployeeRateDTO> map = new HashMap<>();
        try {
            List<EmployeeRateDTO> list = baseMapper.selectEmployeeRates();
            if (list != null) {
                for (EmployeeRateDTO e : list) {
                    if (e.getEmployeeId() != null) {
                        map.put(e.getEmployeeId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取员工工时费率失败（employee.hourly_rate 可能未补列），计时工资按 0 计: {}", e.getMessage());
        }
        return map;
    }

    private Map<Long, String> loadDeptNames() {
        Map<Long, String> map = new HashMap<>();
        try {
            List<Department> list = departmentMapper.selectList(null);
            if (list != null) {
                for (Department d : list) {
                    if (d.getDeptId() != null) {
                        map.put(d.getDeptId(), d.getDeptName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取部门名称失败，部门列按空显示: {}", e.getMessage());
        }
        return map;
    }

    /** 逐条汇报单计算明细行（employee_id 为空的行跳过；dept/employee 过滤在此生效） */
    private List<WageSettlementItem> buildLines(CalcContext ctx, List<OperationReport> reports,
                                                String period, Long deptId, Long employeeId) {
        List<WageSettlementItem> lines = new ArrayList<>();
        int lineNo = 1;
        for (OperationReport r : reports) {
            Long empId = r.getEmployeeId();
            if (empId == null) {
                continue;
            }
            EmployeeRateDTO emp = ctx.employeeRates.get(empId);
            Long empDeptId = emp != null ? emp.getDeptId() : null;
            if (deptId != null && !deptId.equals(empDeptId)) {
                continue;
            }
            if (employeeId != null && !employeeId.equals(empId)) {
                continue;
            }

            BigDecimal qty = nvl(r.getQualifiedQuantity());
            WorkOrderOperation w = null;
            if (r.getProductionOrderId() != null && r.getOperationId() != null) {
                w = ctx.workOrderOperations.get(keyOf(r.getProductionOrderId(), r.getOperationId()));
            }
            RoutingOperation ro = r.getOperationId() != null ? ctx.routingOperations.get(r.getOperationId()) : null;

            BigDecimal pieceRate = resolvePieceRate(w, ro);
            BigDecimal pieceAmount = qty.multiply(pieceRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            BigDecimal hours = nvl(r.getWorkHours()).add(nvl(r.getOvertimeHours()));
            BigDecimal hourlyRate = (emp != null && emp.getHourlyRate() != null) ? emp.getHourlyRate() : BigDecimal.ZERO;
            BigDecimal timeAmount = hours.multiply(hourlyRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            WageSettlementItem line = new WageSettlementItem();
            line.setLineNo(lineNo++);
            line.setOrderNo(r.getProductionOrderNo());
            line.setOperationCode(w != null ? w.getOperationCode() : (ro != null ? ro.getOperationCode() : null));
            line.setOperationName(r.getOperationName());
            line.setEmployeeId(empId);
            line.setEmployeeName(r.getEmployeeName() != null ? r.getEmployeeName()
                    : (emp != null ? emp.getEmployeeName() : null));
            line.setQualifiedQuantity(qty);
            line.setPieceRate(pieceRate);
            line.setPieceAmount(pieceAmount);
            line.setWorkHours(hours);
            line.setHourlyRate(hourlyRate);
            line.setTimeAmount(timeAmount);
            line.setAmount(pieceAmount.add(timeAmount));
            line.setRemark("工序汇报单: " + r.getReportNo());
            lines.add(line);
        }
        return lines;
    }

    /** 计件单价：工单工序快照 → 工艺路线 → 0 */
    private BigDecimal resolvePieceRate(WorkOrderOperation w, RoutingOperation ro) {
        if (w != null && w.getPieceRate() != null) {
            return w.getPieceRate();
        }
        if (ro != null && ro.getPieceRate() != null) {
            return ro.getPieceRate();
        }
        return BigDecimal.ZERO;
    }

    private String keyOf(Long orderId, Long operationId) {
        return orderId + "_" + operationId;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 单据号 WG-yyyyMMdd-0001（优先序列号服务配置，未配置时按当天已存在单号 +1） */
    private String generateNo() {
        return sysSerialNoConfigService.generateOrDefault("WAGE_SETTLEMENT", () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "WG-" + dateStr + "-";
            QueryWrapper<WageSettlement> wrapper = new QueryWrapper<>();
            wrapper.likeRight("settlement_no", prefix).orderByDesc("settlement_no").last("LIMIT 1");
            WageSettlement last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getSettlementNo() != null) {
                try {
                    seq = Integer.parseInt(last.getSettlementNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
