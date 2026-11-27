package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillApprovalAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.entity.CostCalculationItem;
import com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper;
import com.erp.finance.cost.calculation.mapper.CostCalculationMapper;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.entity.OperationExpenseAllocation;
import com.erp.finance.mapper.OperationExpenseAllocationMapper;
import com.erp.finance.mapper.OperationExpenseMapper;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.sale.entity.SaleReturnItem;
import com.erp.sale.mapper.SaleReturnItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.util.SortHelper;

/**
 * 运营费用服务实现
 * 状态流转: 草稿 → 待审核 → 已生效
 *
 * 审核通过后, 关联入库类单据(生产入库/采购入库/销售退货)的分摊明细
 * 会自动汇总到对应物料的成本核算单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationExpenseServiceImpl extends com.erp.common.bill.AbstractBillService<OperationExpenseMapper, OperationExpense>
        implements OperationExpenseService {

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.bill.BillRelationService billRelationService;


    /**
     * 列头排序白名单（operation_expense 表真实列名，防注入）。
     * 逐列核对 OperationExpense 实体 @TableField：expense_no / expense_date / applicant_name / department_name / remark
     * （与前端可排序列一一对应：expenseNo / expenseDate / applicantName / departmentName / remark）
     */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "expense_no",
            "expense_date",
            "applicant_name",
            "department_name",
            "remark"
    );

    private final OperationExpenseAllocationMapper allocationMapper;
    private final CostCalculationMapper costCalculationMapper;
    private final CostCalculationItemMapper costCalculationItemMapper;
    private final ProductionReceiptItemMapper productionReceiptItemMapper;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final SaleReturnItemMapper saleReturnItemMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
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

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义 · 财务推广批）。
        // 键 = 前端筛选条件定义键（与「费用分摊单明细报表」引擎筛选键同键）；值 = 实体属性名。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("expenseNo", new BillHeaderFilterSupport.Spec("expenseNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("allocationMethod", new BillHeaderFilterSupport.Spec("allocationMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("relatedOrderType", new BillHeaderFilterSupport.Spec("relatedOrderType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("relatedOrderNo", new BillHeaderFilterSupport.Spec("relatedOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("expenseType", new BillHeaderFilterSupport.Spec("expenseType", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("applicantName", new BillHeaderFilterSupport.Spec("applicantId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("closeStatus", new BillHeaderFilterSupport.Spec("closeStatus", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 / 数值区间 ──
        f.put("expenseDate", new BillHeaderFilterSupport.Spec("expenseDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("operationExpense", OperationExpense.class, f);
    }

    @Override
    public PageResult<OperationExpense> pageList(int current, int size, String keyword, String expenseType,
                                                  String status, String startDate, String endDate,
                                                  String employeeId,
                                                  com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                                  String sortBy, String sortOrder) {
        QueryWrapper<OperationExpense> wrapper = new QueryWrapper<>();
        // 列头排序：列名白名单校验（未传/未命中 → 走原默认排序：最新在前）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) {
            SortHelper.orderByNewest(wrapper, OperationExpense.class);
        }
        // 关键词：费用单号 + 申请人 + 关联单据号 + 备注，多列匹配强制成组（保持原整体成组语义）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "expense_no", "applicant_name", "related_order_no", "remark");
        if (expenseType != null && !expenseType.isBlank()) {
            wrapper.eq("expense_type", expenseType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 费用日期区间（本页真实列 expense_date，含端点；非法/为空忽略）
        ListQuerySupport.applyDateRange(wrapper, "expense_date", startDate, endDate);
        // 员工维度（统一入参 employeeId → 本页真实列 applicant_id，业务口径「申请人」）
        ListQuerySupport.applyEmployee(wrapper, employeeId, "applicant_id");
        // 数据权限（2026-09-02 配置驱动）：operation_expense 表无 created_by 列，
        // 使用 applicant_name 作为创建人过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "applicant_name");
        Page<OperationExpense> page = new Page<>(current, size);
        Page<OperationExpense> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<OperationExpense> pageList(int current, int size, String keyword, String expenseType,
                                                  String status, String startDate, String endDate,
                                                  String employeeId,
                                                  com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                                  String sortBy, String sortOrder, Map<String, String> headerFilters) {
        QueryWrapper<OperationExpense> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) {
            SortHelper.orderByNewest(wrapper, OperationExpense.class);
        }
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "expense_no", "applicant_name", "related_order_no", "remark");
        if (expenseType != null && !expenseType.isBlank()) {
            wrapper.eq("expense_type", expenseType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        ListQuerySupport.applyDateRange(wrapper, "expense_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "applicant_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "operationExpense", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "applicant_name");
        Page<OperationExpense> page = new Page<>(current, size);
        Page<OperationExpense> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public OperationExpense getById(Long id) {
        OperationExpense expense = super.getById(id);
        if (expense == null) {
            throw new BusinessException("运营费用单不存在");
        }
        // 加载分摊明细
        expense.setAllocations(loadAllocations(id));
        // 将JSON字段转为前端使用的expenseLines
        if (expense.getExpenseLinesJson() != null && !expense.getExpenseLinesJson().isEmpty()) {
            expense.setExpenseLines(expense.getExpenseLinesJson());
        }
        return expense;
    }

    @Override
    @Transactional
    public OperationExpense saveEntity(OperationExpense expense) {
        // 同步前端expenseLines到expenseLinesJson
        syncExpenseLines(expense);

        // 保存=草稿宽松：原"费用类型不能为空 / 费用金额必须大于0"等业务校验已挪到提交(submit)时执行

        // 自动生成单号
        if (expense.getExpenseNo() == null || expense.getExpenseNo().isBlank()) {
            expense.setExpenseNo(generateExpenseNo());
        }
        // 默认值：状态初始化与落库统一走状态机（保存=草稿），保存方法内不直接写状态
        if (expense.getStatus() == null) setStatus(expense, "草稿");
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if (expense.getApprovalStatus() == null) setApprovalStatus(expense, "草稿");
        if (expense.getExpenseDate() == null) expense.setExpenseDate(LocalDate.now());
        if (expense.getCreatedAt() == null) expense.setCreatedAt(LocalDateTime.now());
        if (expense.getUpdatedAt() == null) expense.setUpdatedAt(LocalDateTime.now());

        saveDraft(expense);
        // 保存分摊明细
        saveAllocations(expense);
        log.info("新增运营费用单: {} 金额: {} 类型: {} 分摊明细: {}",
                expense.getExpenseNo(), expense.getAmount(), expense.getExpenseType(),
                expense.getAllocations() == null ? 0 : expense.getAllocations().size());
        return expense;
    }

    @Override
    @Transactional
    public OperationExpense updateEntity(OperationExpense expense) {
        if (expense.getExpenseId() == null) {
            throw new BusinessException("费用ID不能为空");
        }
        OperationExpense existing = super.getById(expense.getExpenseId());
        if (existing == null) {
            throw new BusinessException("运营费用单不存在");
        }
        if (!"草稿".equals(existing.getStatus())) {
            throw new BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
        }

        // 同步前端expenseLines到expenseLinesJson
        syncExpenseLines(expense);

        // 保存=草稿宽松：业务校验挪到提交(submit)时执行

        expense.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(expense);
        // 先删除旧分摊明细, 再批量插入新的
        allocationMapper.delete(new QueryWrapper<OperationExpenseAllocation>().eq("expense_id", expense.getExpenseId()));
        saveAllocations(expense);
        log.info("修改运营费用单: {}", expense.getExpenseNo());
        return expense;
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        OperationExpense existing = super.getById(id);
        if (existing == null) {
            return true;
        }
        // 成本核算联动检查 - 若已在核算单明细中被引用，禁止删除
        Long usedCount = costCalculationItemMapper.selectCount(
                new QueryWrapper<CostCalculationItem>()
                        .like("remark", existing.getExpenseNo()));
        if (usedCount != null && usedCount > 0) {
            throw new BusinessException("该运营费用单已用于成本核算(" + usedCount + "处引用)，"
                    + "请先反确认相关核算单后再删除");
        }
        // 草稿校验 + 下游校验 + 整单级联删除由统一状态机执行
        super.deleteBill(id);
        log.info("删除运营费用单: {}", existing.getExpenseNo());
        return true;
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "费用分摊单"; }
    @Override protected String getApprovalBillType() { return "OPERATION_EXPENSE"; }
    @Override protected String getBillNo(OperationExpense b) { return b.getExpenseNo(); }
    @Override protected Object getId(OperationExpense b) { return b.getExpenseId(); }
    @Override protected String getStatus(OperationExpense b) { return b.getStatus(); }
    @Override protected void setStatus(OperationExpense b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(OperationExpense b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(OperationExpense b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(OperationExpense b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(OperationExpense b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(OperationExpense b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(OperationExpense b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(OperationExpense b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(OperationExpense b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(OperationExpense b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(OperationExpense b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(OperationExpense b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(OperationExpense b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(OperationExpense b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(OperationExpense bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getExpenseNo());
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id, String operator) {
        OperationExpense expense = super.getById(id);
        if (expense == null) {
            throw new BusinessException("运营费用单不存在");
        }
        super.submitForApproval(expense, operator != null && !operator.isBlank()
                ? operator : BillApprovalAdapter.currentSubmitter());
    }

    /** 提交前严格校验：费用类型必填 + 金额大于 0（保存时宽松） */
    @Override
    protected void validateStrictSubmit(OperationExpense expense) {
        validateExpense(expense);
    }

    /** 审核：审批在途禁止绕过引擎 + 状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("OPERATION_EXPENSE", (Long) id) != null) {
            throw new BusinessException("费用分摊单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 审核通过影响：分摊明细自动汇总到成本核算单（原审核逻辑） */
    @Override
    public void onApproved(OperationExpense expense) {
        expense.setAllocations(loadAllocations(expense.getExpenseId()));
        summarizeToCostCalculation(expense);
    }

    /** 重置草稿影响：与 onApproved 严格镜像，删除审核时自动汇总的成本明细行 */
    @Override
    public void onReverseApproved(OperationExpense expense) {
        costCalculationItemMapper.delete(new QueryWrapper<CostCalculationItem>()
                .likeRight("cost_name", expense.getExpenseNo() + "-")
                .like("remark", "费用分摊单自动汇总"));
        log.info("费用分摊单重置草稿冲回成本明细: {}", expense.getExpenseNo());
    }

    // ========== 分摊明细持久化 ==========

    /** 加载分摊明细 */
    private List<OperationExpenseAllocation> loadAllocations(Long expenseId) {
        return allocationMapper.selectList(new QueryWrapper<OperationExpenseAllocation>()
                .eq("expense_id", expenseId).orderByAsc("allocation_id"));
    }

    /** 批量保存分摊明细(主表 insert 后调用, 已带 expenseId) */
    private void saveAllocations(OperationExpense expense) {
        List<OperationExpenseAllocation> allocations = expense.getAllocations();
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        for (OperationExpenseAllocation alloc : allocations) {
            alloc.setAllocationId(null);
            alloc.setExpenseId(expense.getExpenseId());
            if (alloc.getCreatedAt() == null) alloc.setCreatedAt(LocalDateTime.now());
            allocationMapper.insert(alloc);
        }
    }

    // ========== 审核后自动汇总到成本核算单 ==========

    /**
     * 审核后自动汇总: 遍历分摊明细, 对关联到入库类单据(生产入库/采购入库/销售退货)的明细,
     * 根据入库单查出物料信息, 在对应物料的草稿状态成本核算单中新增成本明细行.
     * 期间费用(出差/宴请/快递/其他)不归集到成本核算单.
     *
     * 重构后: 每个分摊明细行有自己的expenseType, 按行获取费用类型而非从表头获取
     */
    private void summarizeToCostCalculation(OperationExpense expense) {
        if (expense.getAllocations() == null || expense.getAllocations().isEmpty()) {
            return;
        }

        int summarized = 0;
        for (OperationExpenseAllocation alloc : expense.getAllocations()) {
            if (alloc.getOrderType() == null) continue;

            // 优先使用分摊明细行的费用类型, 若为空则回退到表头费用类型
            String lineExpenseType = alloc.getExpenseType() != null ? alloc.getExpenseType() : expense.getExpenseType();
            String costType = mapExpenseTypeToCostType(lineExpenseType);
            if (costType == null) {
                log.info("运营费用单 {} 分摊明细类型[{}]为期间费用, 不归集成本", expense.getExpenseNo(), lineExpenseType);
                continue;
            }

            // 成本链路（2026-11-26）：按表头“分摊方式”把本行分摊金额拆到入库单的每一行物料，
            // 不再整笔归集到第一行；支持平均 / 按数量 / 按金额 / 自定义（自定义按金额权重，金额为空按数量）。
            List<AllocationTarget> targets = loadAllocationTargets(alloc);
            if (targets.isEmpty()) {
                log.warn("运营费用单 {} 分摊明细[orderType={}, orderId={}]未找到可归集物料, 跳过",
                        expense.getExpenseNo(), alloc.getOrderType(), alloc.getOrderId());
                continue;
            }
            BigDecimal totalAmount = alloc.getAmount() != null ? alloc.getAmount() : BigDecimal.ZERO;
            if (totalAmount.signum() <= 0) {
                continue;
            }
            List<BigDecimal> weights = new ArrayList<>();
            BigDecimal totalWeight = BigDecimal.ZERO;
            String method = expense.getAllocationMethod();
            for (AllocationTarget t : targets) {
                BigDecimal w = allocationWeight(method, t);
                weights.add(w);
                totalWeight = totalWeight.add(w);
            }
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < targets.size(); i++) {
                BigDecimal share;
                if (i == targets.size() - 1) {
                    share = totalAmount.subtract(allocated);
                } else if (totalWeight.signum() > 0) {
                    share = totalAmount.multiply(weights.get(i))
                            .divide(totalWeight, 2, RoundingMode.HALF_UP);
                } else {
                    share = totalAmount.divide(BigDecimal.valueOf(targets.size()), 2, RoundingMode.HALF_UP);
                }
                if (share.signum() <= 0) {
                    continue;
                }
                AllocationTarget t = targets.get(i);
                CostCalculation calc = findOrCreateCostCalculation(t.materialId, t.materialCode, t.materialName);
                CostCalculationItem item = new CostCalculationItem();
                item.setCalcId(calc.getId());
                item.setCostType(costType);
                item.setCostName(expense.getExpenseNo() + "-" + lineExpenseType);
                item.setMaterialId(t.materialId);
                item.setMaterialCode(t.materialCode);
                item.setMaterialName(t.materialName);
                item.setQuantity(BigDecimal.ONE);
                item.setUnitPrice(share);
                item.setAmount(share);
                item.setRemark("费用分摊单自动汇总: " + alloc.getOrderNo());
                costCalculationItemMapper.insert(item);
                allocated = allocated.add(share);
                summarized++;
            }
        }
        log.info("运营费用单 {} 审核汇总完成, 新增成本明细 {} 条", expense.getExpenseNo(), summarized);
    }

    /**
     * 分摊目标（入库单每一行物料）：材料、数量、金额三项作为分摊权重来源。
     */
    private record AllocationTarget(Long materialId, String materialCode, String materialName,
                                    BigDecimal quantity, BigDecimal amount) {
    }

    /** 读取分摊对象（生产入库/采购入库/销售退货）的全部物料行；不再只取第一行 */
    private List<AllocationTarget> loadAllocationTargets(OperationExpenseAllocation alloc) {
        List<AllocationTarget> targets = new ArrayList<>();
        if (alloc == null || alloc.getOrderId() == null) {
            return targets;
        }
        switch (alloc.getOrderType() == null ? "" : alloc.getOrderType()) {
            case "PRODUCTION_RECEIPT": {
                List<ProductionReceiptItem> items = productionReceiptItemMapper.selectList(
                        new QueryWrapper<ProductionReceiptItem>()
                                .eq("receipt_id", alloc.getOrderId()).orderByAsc("line_no"));
                for (ProductionReceiptItem it : items) {
                    addTarget(targets, it.getMaterialId(), it.getMaterialCode(), it.getMaterialName(),
                            it.effectiveBaseQuantity() != null ? it.effectiveBaseQuantity() : it.getQuantity(),
                            positiveOrNull(it.getNetAmount(), it.getAmount()));
                }
                break;
            }
            case "PURCHASE_INBOUND": {
                List<PurchaseInboundItem> items = purchaseInboundItemMapper.selectList(
                        new QueryWrapper<PurchaseInboundItem>()
                                .eq("receipt_id", alloc.getOrderId()).orderByAsc("line_no"));
                for (PurchaseInboundItem it : items) {
                    addTarget(targets, it.getMaterialId(), it.getMaterialCode(), it.getMaterialName(),
                            it.effectiveBaseQuantity() != null ? it.effectiveBaseQuantity() : it.getQuantity(),
                            positiveOrNull(it.getNetAmount(), it.getAmount()));
                }
                break;
            }
            case "SALE_RETURN": {
                List<SaleReturnItem> items = saleReturnItemMapper.selectList(
                        new QueryWrapper<SaleReturnItem>()
                                .eq("return_id", alloc.getOrderId()).orderByAsc("line_no"));
                for (SaleReturnItem it : items) {
                    addTarget(targets, it.getMaterialId(), it.getMaterialCode(), it.getMaterialName(),
                            it.effectiveBaseQuantity() != null ? it.effectiveBaseQuantity() : it.getQuantity(),
                            positiveOrNull(it.getNetAmount(), it.getAmount()));
                }
                break;
            }
            default:
                break;
        }
        return targets;
    }

    private void addTarget(List<AllocationTarget> targets, Long materialId, String code, String name,
                           BigDecimal qty, BigDecimal amount) {
        if (materialId == null) {
            return;
        }
        targets.add(new AllocationTarget(materialId, code, name,
                qty != null ? qty : BigDecimal.ZERO, amount != null ? amount : BigDecimal.ZERO));
    }

    /** 金额优先取价税合计，为 0 时退回折后金额 */
    private BigDecimal positiveOrNull(BigDecimal netAmount, BigDecimal amount) {
        if (netAmount != null && netAmount.signum() > 0) {
            return netAmount;
        }
        return amount != null ? amount : BigDecimal.ZERO;
    }

    /** 分摊权重：平均=1、按数量=数量、按金额/自定义=金额（金额为 0 时退回数量） */
    private BigDecimal allocationWeight(String method, AllocationTarget target) {
        if ("平均分摊".equals(method)) {
            return BigDecimal.ONE;
        }
        if ("按数量分摊".equals(method)) {
            return target.quantity();
        }
        if (target.amount().signum() > 0) {
            return target.amount();
        }
        return target.quantity();
    }

    /**
     * 费用类型 → 成本类型映射
     * 人工费用 → DIRECT_LABOR(直接人工)
     * 制造费用/水电费/办公费 → OVERHEAD(制造费用)
     * 出差/宴请/快递/其他 → 期间费用, 返回 null 不归集
     */
    private String mapExpenseTypeToCostType(String expenseType) {
        if (expenseType == null) return null;
        switch (expenseType) {
            case "人工费用":
                return "DIRECT_LABOR";
            case "制造费用":
            case "水电费":
            case "办公费":
                return "OVERHEAD";
            default:
                return null;
        }
    }

    /** 查找或创建成本核算单(按 materialId 匹配草稿状态, 没有则新建) */
    private CostCalculation findOrCreateCostCalculation(Long materialId, String materialCode, String materialName) {
        QueryWrapper<CostCalculation> wrapper = new QueryWrapper<>();
        wrapper.eq("material_id", materialId).eq("status", "草稿")
                .orderByAsc("created_at is null").orderByDesc("created_at").last("LIMIT 1");
        CostCalculation calc = costCalculationMapper.selectOne(wrapper);
        if (calc != null) {
            return calc;
        }
        calc = new CostCalculation();
        calc.setCalcNo(generateCalcNo());
        calc.setCalcName(materialName != null ? materialName + "成本核算" : "成本核算");
        calc.setMaterialId(materialId);
        calc.setMaterialCode(materialCode);
        calc.setMaterialName(materialName);
        calc.setCalcDate(LocalDate.now());
        calc.setStatus("草稿");
        calc.setQuantity(BigDecimal.ZERO);
        calc.setDirectMaterialCost(BigDecimal.ZERO);
        calc.setDirectLaborCost(BigDecimal.ZERO);
        calc.setManufacturingOverhead(BigDecimal.ZERO);
        calc.setTotalCost(BigDecimal.ZERO);
        calc.setUnitCost(BigDecimal.ZERO);
        costCalculationMapper.insert(calc);
        log.info("自动创建成本核算单: {} 物料: {}", calc.getCalcNo(), materialCode);
        return calc;
    }

    /**
     * 生成成本核算单号: CC-yyyyMMdd-0001
     * 同一天内按已存在单号+1递增
     */
    private String generateCalcNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "CC-" + dateStr + "-";
        QueryWrapper<CostCalculation> wrapper = new QueryWrapper<>();
        wrapper.likeRight("calc_no", prefix).orderByDesc("calc_no").last("LIMIT 1");
        CostCalculation last = costCalculationMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getCalcNo() != null) {
            try {
                seq = Integer.parseInt(last.getCalcNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }

    // ========== 私有方法 ==========

    /** 同步前端expenseLines到expenseLinesJson(持久化) */
    private void syncExpenseLines(OperationExpense expense) {
        if (expense.getExpenseLines() != null && !expense.getExpenseLines().isEmpty()) {
            expense.setExpenseLinesJson(expense.getExpenseLines());
        }
    }

    /** 业务规则校验 */
    private void validateExpense(OperationExpense expense) {
        if (expense.getExpenseType() == null || expense.getExpenseType().isBlank()) {
            throw new BusinessException("费用类型不能为空");
        }
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("费用金额必须大于0");
        }
    }

    /**
     * 生成费用单号: OE-yyyyMMdd-0001
     * 同一天内按已存在单号+1递增
     */
    private String generateExpenseNo() {
        String docType = "OPERATION_EXPENSE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "OE-" + dateStr + "-";
            QueryWrapper<OperationExpense> wrapper = new QueryWrapper<>();
            wrapper.likeRight("expense_no", prefix).orderByDesc("expense_no").last("LIMIT 1");
            OperationExpense last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getExpenseNo() != null) {
                try {
                    seq = Integer.parseInt(last.getExpenseNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 驳回：状态迁移由统一状态机执行，驳回原因/人/时间单独留痕 */
    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
        update(new UpdateWrapper<OperationExpense>().eq("expense_id", id)
                .set("reject_reason", reason)
                .set("rejector", rejector)
                .set("rejected_at", LocalDateTime.now()));
        log.info("驳回运营费用单: {} 操作人: {} 原因: {}", id, rejector, reason);
    }

    /** 重置草稿：状态校验/下游校验/状态迁移/冲回由统一状态机执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<OperationExpense>().eq("expense_id", id)
                .set("approved_by", null).set("approved_at", null)
                .set("reverse_approver", operator)
                .set("reverse_approved_at", LocalDateTime.now()));
        log.info("重置草稿运营费用单: {} 操作人: {}", id, operator);
    }

    /** 作废：本单据草稿不允许作废（须直接删除）；其余状态校验/下游校验/冲回由统一状态机执行 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        OperationExpense bill = super.getById(id);
        if (bill == null) {
            throw new BusinessException("运营费用单不存在");
        }
        if (BillStatusUtils.isDraft(bill.getStatus())) {
            throw new BusinessException("草稿状态单据无需作废，请直接删除");
        }
        super.voidBill(id, operator, reason);
    }

    /** 作废影响：与重置草稿严格镜像（冲回已汇总的成本明细） */
    @Override
    public void onVoided(OperationExpense bill) {
        onReverseApproved(bill);
    }

    /**
     * 反作废影响（2026-11-09）：作废时已删除自动汇总的成本明细，反作废恢复到「已生效」必须重新汇总，
     * 否则费用分摊跑到成本里丢失。与 onVoided 严格镜像（重放为重新插入分摊成本明细，无唯一流水号/通知）。
     */
    @Override
    public void onUnvoided(OperationExpense bill) {
        onApproved(bill);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
