package com.erp.finance.cost.calculation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.util.SortHelper;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.entity.CostCalculationItem;
import com.erp.finance.cost.calculation.entity.CostReportItem;
import com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper;
import com.erp.finance.cost.calculation.mapper.CostCalculationMapper;
import com.erp.finance.cost.calculation.service.CostCalculationService;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.entity.OperationExpenseAllocation;
import com.erp.finance.mapper.OperationExpenseAllocationMapper;
import com.erp.finance.mapper.OperationExpenseMapper;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.entity.WorkCenter;
import com.erp.production.entity.WorkOrder;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.WorkCenterMapper;
import com.erp.production.mapper.WorkOrderMapper;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostCalculationServiceImpl extends com.erp.common.bill.AbstractBillService<CostCalculationMapper, CostCalculation> implements CostCalculationService {

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.bill.BillRelationService billRelationService;

    /** 审批运行时引擎：仅用于「已有进行中审批任务时禁止绕过引擎直审」的防护判断 */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.approval.service.ApprovalRuntimeService approvalRuntimeService;


    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "calc_date",
            "calc_id",
            "calc_method",
            "calc_name",
            "calc_no",
            "confirmed_at",
            "confirmed_by",
            "created_at",
            "created_by",
            "direct_labor_cost",
            "direct_material_cost",
            "expense_date",
            "expense_id",
            "id",
            "in_quantity",
            "manufacturing_overhead",
            "material_code",
            "material_id",
            "material_name",
            "order_no",
            "out_quantity",
            "period",
            "production_order_id",
            "quantity",
            "reject_reason",
            "rejected_at",
            "remark",
            "requisition_id",
            "reverse_approved_at",
            "reverse_approver",
            "reversed_at",
            "reversed_by",
            "source_bill_no",
            "status",
            "total_cost",
            "trans_date",
            "unit_cost",
            "updated_at",
            "updated_by",
            "version",
            "void_reason",
            "voided_at",
            "voided_by",
            "work_order_id"
        );

    private final CostCalculationItemMapper itemMapper;
    private final WorkOrderMapper workOrderMapper;
    private final MaterialMapper materialMapper;
    private final MaterialRequisitionMapper materialRequisitionMapper;
    private final MaterialRequisitionItemMapper materialRequisitionItemMapper;
    private final OperationReportMapper operationReportMapper;
    /** 工作中心费率（人工/制造费用费率的唯一取数来源） */
    private final WorkCenterMapper workCenterMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final OperationExpenseMapper operationExpenseMapper;
    private final OperationExpenseAllocationMapper operationExpenseAllocationMapper;
    /** 生产入库（质检报废损失归集用，2026-11-07） */
    private final ProductionReceiptMapper productionReceiptMapper;
    private final ProductionReceiptItemMapper productionReceiptItemMapper;
    /** 采购入库 / 销售退货明细（费用分摊按行拆分用，2026-11-26） */
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final SaleReturnItemMapper saleReturnItemMapper;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;
    /** 设置中心业务规则校验器（含税业务开关 / 默认计税方式 / 默认税率） */
    private final SysParamValidator sysParamValidator;

    private static final int SCALE = 4;
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
    private int resolveHeaderTaxType(CostCalculation calc) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = calc != null ? calc.getTaxCalcMethod() : null;
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * 明细金额与含税/不含税价换算（口径对齐销售订单试点）：
     * 表头「计税方式」决定「单价」是含税价还是不含税价 → 换算另一列（落库 price_with_tax / price_without_tax）；
     * 金额一律按「不含税」口径参与（金额 = 数量 × 不含税单价）；不计税时含税价=不含税价=单价。
     * 本页明细无「税率%」列，故取设置中心「税率」默认值。
     */
    private void calculateItemAmount(CostCalculationItem item, int taxType) {
        BigDecimal qty = nvl(item.getQuantity());
        BigDecimal price = nvl(item.getUnitPrice());
        BigDecimal taxRate = nvl(sysParamValidator.getTaxRate());
        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal priceWithTax;
        BigDecimal priceWithoutTax;
        if (inclusive) {
            priceWithTax = price;
            priceWithoutTax = factor.compareTo(BigDecimal.ZERO) != 0
                    ? price.divide(factor, 6, RoundingMode.HALF_UP) : price;
        } else if (noTax) {
            priceWithTax = price;
            priceWithoutTax = price;
        } else {
            priceWithoutTax = price;
            priceWithTax = price.multiply(factor).setScale(6, RoundingMode.HALF_UP);
        }
        item.setPriceWithTax(priceWithTax.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(priceWithoutTax.setScale(6, RoundingMode.HALF_UP));
        BigDecimal amount = inclusive
                ? price.multiply(qty).divide(factor, SCALE, RoundingMode.HALF_UP)
                : price.multiply(qty).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 价税合计 = 含税价 × 数量（2026-11-23 补齐）
        item.setNetAmount(item.getPriceWithTax().multiply(qty).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 明细价税合计：优先落库值，老数据为空时按「含税价 × 数量」兜底 */
    private BigDecimal itemNetAmount(CostCalculationItem item) {
        if (item.getNetAmount() != null) return item.getNetAmount();
        BigDecimal withTax = item.getPriceWithTax() != null ? item.getPriceWithTax() : nvl(item.getUnitPrice());
        return withTax.multiply(nvl(item.getQuantity())).setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public PageResult<CostCalculation> pageQuery(int current, int size, String keyword, String status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<CostCalculation> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, CostCalculation.class);
        if (keyword != null && !keyword.isBlank()) {
            // 关键词多列 OR 必须成组：平铺 or() 会让后续 status 过滤与数据权限只绑定最后一列，
            // 命中 calc_no/calc_name 时绕过状态筛选与数据权限（历史同类越权坑，务必包 and(...)）
            wrapper.and(w -> w.like("calc_no", keyword).or().like("calc_name", keyword).or().like("material_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<CostCalculation> page = new Page<>(current, size);
        Page<CostCalculation> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public CostCalculation getDetail(Long id) {
        CostCalculation calc = getById(id);
        if (calc == null) throw new BusinessException("成本核算单不存在");
        List<CostCalculationItem> items = itemMapper.selectList(new QueryWrapper<CostCalculationItem>().eq("calc_id", id));
        calc.setItems(items);
        return calc;
    }

    @Override
    @Transactional
    public void saveCostCalculation(CostCalculation calc, List<CostCalculationItem> items) {
        boolean isNew = calc.getId() == null;

        if (calc.getCalcNo() == null || calc.getCalcNo().isBlank()) {
            calc.setCalcNo(generateCalcNo());
        }

        // 保存=草稿：状态初始化与新增落库统一走状态机，保存方法内不直接写状态
        if (isNew) {
            if (calc.getStatus() == null || calc.getStatus().isBlank()) setStatus(calc, "草稿");
            if (calc.getApprovalStatus() == null) setApprovalStatus(calc, "草稿");
        }
        if (calc.getCalcDate() == null) calc.setCalcDate(LocalDate.now());

        // 明细先算（表头价税合计依赖明细），再落表头与明细
        BigDecimal totalNet = BigDecimal.ZERO;
        int headerTaxType = resolveHeaderTaxType(calc);
        if (items != null) {
            for (CostCalculationItem item : items) {
                calculateItemAmount(item, headerTaxType);
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
            calc.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
        }

        if (isNew) {
            saveDraft(calc);
        } else {
            updateById(calc);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<CostCalculationItem>().eq("calc_id", calc.getId()));
            for (CostCalculationItem item : items) {
                item.setId(null);
                item.setCalcId(calc.getId());
                itemMapper.insert(item);
            }
        }

        log.info("成本核算单保存成功: {}", calc.getCalcNo());
    }

    @Override
    @Transactional
    public void calculateCost(Long id) {
        CostCalculation calc = getById(id);
        if (calc == null) throw new BusinessException("成本核算单不存在");

        List<CostCalculationItem> items = itemMapper.selectList(new QueryWrapper<CostCalculationItem>().eq("calc_id", id));

        // 如果明细为空，尝试从关联工单自动归集
        if (items == null || items.isEmpty()) {
            items = autoCollectItems(calc);
            if (items != null && !items.isEmpty()) {
                for (CostCalculationItem item : items) {
                    item.setCalcId(id);
                    itemMapper.insert(item);
                }
            }
        }

        BigDecimal directMaterialCost = BigDecimal.ZERO;
        BigDecimal directLaborCost = BigDecimal.ZERO;
        BigDecimal manufacturingOverhead = BigDecimal.ZERO;
        BigDecimal scrapLoss = BigDecimal.ZERO;

        BigDecimal totalNet = BigDecimal.ZERO;
        for (CostCalculationItem item : items) {
            BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            totalNet = totalNet.add(itemNetAmount(item));
            switch (item.getCostType()) {
                case "DIRECT_MATERIAL":
                    directMaterialCost = directMaterialCost.add(amount);
                    break;
                case "DIRECT_LABOR":
                    directLaborCost = directLaborCost.add(amount);
                    break;
                case "OVERHEAD":
                    manufacturingOverhead = manufacturingOverhead.add(amount);
                    break;
                case "SCRAP_LOSS":
                    scrapLoss = scrapLoss.add(amount);
                    break;
            }
        }

        BigDecimal totalCost = directMaterialCost.add(directLaborCost).add(manufacturingOverhead).add(scrapLoss);

        calc.setDirectMaterialCost(directMaterialCost.setScale(SCALE, RoundingMode.HALF_UP));
        calc.setDirectLaborCost(directLaborCost.setScale(SCALE, RoundingMode.HALF_UP));
        calc.setManufacturingOverhead(manufacturingOverhead.setScale(SCALE, RoundingMode.HALF_UP));
        calc.setScrapLoss(scrapLoss.setScale(SCALE, RoundingMode.HALF_UP));
        calc.setTotalCost(totalCost.setScale(SCALE, RoundingMode.HALF_UP));
        calc.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));

        BigDecimal quantity = calc.getQuantity() != null ? calc.getQuantity() : BigDecimal.ONE;
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            calc.setUnitCost(totalCost.divide(quantity, SCALE, RoundingMode.HALF_UP));
        } else {
            calc.setUnitCost(totalCost);
        }

        calc.setStatus("已核算");
        updateById(calc);

        log.info("成本核算完成: {} 总成本: {} 单位成本: {} 直接材料: {} 直接人工: {}",
                calc.getCalcNo(), totalCost, calc.getUnitCost(), directMaterialCost, directLaborCost);
    }

    /**
     * 自动从工单关联的领料单和报工单归集成本数据
     */
    private List<CostCalculationItem> autoCollectItems(CostCalculation calc) {
        // 尝试从 remark 中解析工单号: WO-xxx
        String remark = calc.getRemark();
        if (remark == null || remark.isBlank()) return null;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("WO-\\d{8}-\\d{4}");
        java.util.regex.Matcher matcher = pattern.matcher(remark);
        if (!matcher.find()) return null;

        String workOrderNo = matcher.group();
        WorkOrder workOrder = workOrderMapper.selectOne(
                new QueryWrapper<WorkOrder>().eq("order_no", workOrderNo));
        if (workOrder == null) return null;

        List<CostCalculationItem> items = new ArrayList<>();

        // 1. 归集领料单直接材料成本
        List<MaterialRequisition> requisitions = materialRequisitionMapper.selectList(
                new QueryWrapper<MaterialRequisition>()
                        .eq("work_order_id", workOrder.getOrderId())
                        .eq("status", "已生效"));

        for (MaterialRequisition req : requisitions) {
            List<MaterialRequisitionItem> reqItems = materialRequisitionItemMapper.selectList(
                    new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", req.getRequisitionId()));

            for (MaterialRequisitionItem reqItem : reqItems) {
                CostCalculationItem item = new CostCalculationItem();
                item.setCostType("DIRECT_MATERIAL");
                item.setCostName("直接材料 - " + reqItem.getMaterialName()
                        + ("SUPPLEMENT".equals(req.getBillType()) ? "(补料)" : ""));
                item.setMaterialId(reqItem.getMaterialId());
                item.setMaterialCode(reqItem.getMaterialCode());
                item.setMaterialName(reqItem.getMaterialName());
                item.setUnit("件"); // 领料单没有单位名称字段，使用默认值
                item.setQuantity(reqItem.getQuantity());
                item.setUnitPrice(reqItem.getUnitPrice());
                item.setAmount(reqItem.getAmount());
                item.setRemark("领料单: " + req.getRequisitionNo() + " 类型: " + req.getBillType());
                items.add(item);
            }
        }

        // 2. 归集报工单直接人工成本
        List<OperationReport> reports = operationReportMapper.selectList(
                new QueryWrapper<OperationReport>()
                        .eq("production_order_id", workOrder.getOrderId())
                        .eq("status", "已生效"));

        for (OperationReport report : reports) {
            BigDecimal workHours = report.getWorkHours() != null ? report.getWorkHours() : BigDecimal.ZERO;
            BigDecimal overtimeHours = report.getOvertimeHours() != null ? report.getOvertimeHours() : BigDecimal.ZERO;
            BigDecimal totalHours = workHours.add(overtimeHours);

            if (totalHours.compareTo(BigDecimal.ZERO) > 0) {
                // 人工费率取数（2026-09-27 第5期·去硬编码）：工作中心人工费率 → 设置中心默认费率 → 50
                BigDecimal laborRate = resolveLaborRate(report);
                BigDecimal laborCost = totalHours.multiply(laborRate).setScale(2, RoundingMode.HALF_UP);

                CostCalculationItem item = new CostCalculationItem();
                item.setCostType("DIRECT_LABOR");
                item.setCostName("直接人工 - " + report.getOperationName());
                item.setQuantity(totalHours);
                item.setUnitPrice(laborRate);
                item.setAmount(laborCost);
                item.setRemark("报工单: " + report.getReportNo() + " 工时: " + totalHours + "h 费率: " + laborRate);
                items.add(item);

                // 制造费用：工作中心制造费用率未配置时为 0 → 不产生明细（与改造前一致，不改变历史金额）
                BigDecimal overheadRate = resolveOverheadRate(report);
                if (overheadRate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal overheadCost = totalHours.multiply(overheadRate).setScale(2, RoundingMode.HALF_UP);
                    CostCalculationItem oh = new CostCalculationItem();
                    oh.setCostType("OVERHEAD");
                    oh.setCostName("制造费用 - " + report.getOperationName());
                    oh.setQuantity(totalHours);
                    oh.setUnitPrice(overheadRate);
                    oh.setAmount(overheadCost);
                    oh.setRemark("报工单: " + report.getReportNo() + " 工时: " + totalHours
                            + "h 工作中心制造费用率: " + overheadRate);
                    items.add(oh);
                }
            }
        }

        // 3. 归集质检报废损失：本工单已生效入库单中「进报废仓」的不合格品成本（2026-11-07）
        List<ProductionReceipt> scrapReceipts = productionReceiptMapper.selectList(
                new QueryWrapper<ProductionReceipt>()
                        .eq("production_order_id", workOrder.getOrderId())
                        .eq("status", "已生效"));
        for (ProductionReceipt receipt : scrapReceipts) {
            List<ProductionReceiptItem> rows = productionReceiptItemMapper.selectList(
                    new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", receipt.getReceiptId()));
            for (ProductionReceiptItem row : rows) {
                if (!"SCRAP".equals(row.getHandlingType())) continue;
                BigDecimal qty = row.getRejectedQuantity() != null ? row.getRejectedQuantity() : BigDecimal.ZERO;
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal unitCost = row.getUnitPrice() != null && row.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                        ? row.getUnitPrice()
                        : (row.getAvgCost() != null ? row.getAvgCost() : BigDecimal.ZERO);
                CostCalculationItem item = new CostCalculationItem();
                item.setCostType("SCRAP_LOSS");
                item.setCostName("质检报废损失 - " + row.getMaterialName());
                item.setMaterialId(row.getMaterialId());
                item.setMaterialCode(row.getMaterialCode());
                item.setMaterialName(row.getMaterialName());
                item.setQuantity(qty);
                item.setUnitPrice(unitCost);
                item.setAmount(qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP));
                item.setRemark("入库单: " + receipt.getReceiptNo() + " 质检不合格进报废仓");
                items.add(item);
            }
        }

        log.info("工单 {} 自动归集成本数据: {} 条明细", workOrderNo, items.size());
        return items.isEmpty() ? null : items;
    }

    // ================= 费用分摊拆分（2026-11-26） =================
    // 与「费用分摊单审核」同口径：按表头分摊方式把每行分摊金额拆到来源单据的每一行物料。

    /** 分摊目标：来源单据的一行物料（数量、金额作为分摊权重） */
    private record ExpenseTarget(Long materialId, String materialCode, String materialName,
                                 BigDecimal quantity, BigDecimal amount) {
    }

    /** 读取分摊对象（生产入库/采购入库/销售退货）的全部物料行；取不到明细时退回库存台账入库行 */
    private List<ExpenseTarget> loadExpenseTargets(OperationExpenseAllocation alloc) {
        List<ExpenseTarget> targets = new ArrayList<>();
        String orderType = alloc.getOrderType() == null ? "" : alloc.getOrderType();
        switch (orderType) {
            case "PRODUCTION_RECEIPT": {
                List<ProductionReceiptItem> items = productionReceiptItemMapper.selectList(
                        new QueryWrapper<ProductionReceiptItem>()
                                .eq("receipt_id", alloc.getOrderId()).orderByAsc("line_no"));
                for (ProductionReceiptItem it : items) {
                    addExpenseTarget(targets, it.getMaterialId(), it.getMaterialCode(), it.getMaterialName(),
                            it.effectiveBaseQuantity() != null ? it.effectiveBaseQuantity() : it.getQuantity(),
                            positiveExpenseAmount(it.getNetAmount(), it.getAmount()));
                }
                break;
            }
            case "PURCHASE_INBOUND": {
                List<PurchaseInboundItem> items = purchaseInboundItemMapper.selectList(
                        new QueryWrapper<PurchaseInboundItem>()
                                .eq("receipt_id", alloc.getOrderId()).orderByAsc("line_no"));
                for (PurchaseInboundItem it : items) {
                    addExpenseTarget(targets, it.getMaterialId(), it.getMaterialCode(), it.getMaterialName(),
                            it.effectiveBaseQuantity() != null ? it.effectiveBaseQuantity() : it.getQuantity(),
                            positiveExpenseAmount(it.getNetAmount(), it.getAmount()));
                }
                break;
            }
            case "SALE_RETURN": {
                List<SaleReturnItem> items = saleReturnItemMapper.selectList(
                        new QueryWrapper<SaleReturnItem>()
                                .eq("return_id", alloc.getOrderId()).orderByAsc("line_no"));
                for (SaleReturnItem it : items) {
                    addExpenseTarget(targets, it.getMaterialId(), it.getMaterialCode(), it.getMaterialName(),
                            it.effectiveBaseQuantity() != null ? it.effectiveBaseQuantity() : it.getQuantity(),
                            positiveExpenseAmount(it.getNetAmount(), it.getAmount()));
                }
                break;
            }
            default:
                break;
        }
        if (targets.isEmpty() && alloc.getOrderNo() != null && !alloc.getOrderNo().isBlank()) {
            List<InventoryTransaction> transactions = inventoryTransactionMapper.selectList(
                    new QueryWrapper<InventoryTransaction>()
                            .eq("source_bill_no", alloc.getOrderNo())
                            .gt("in_quantity", BigDecimal.ZERO));
            for (InventoryTransaction trans : transactions) {
                addExpenseTarget(targets, trans.getMaterialId(), trans.getMaterialCode(), trans.getMaterialName(),
                        trans.getInQuantity(), trans.getInAmount());
            }
        }
        return targets;
    }

    private void addExpenseTarget(List<ExpenseTarget> targets, Long materialId, String code, String name,
                                  BigDecimal qty, BigDecimal amount) {
        if (materialId == null) {
            return;
        }
        targets.add(new ExpenseTarget(materialId, code, name,
                qty != null ? qty : BigDecimal.ZERO, amount != null ? amount : BigDecimal.ZERO));
    }

    /** 金额优先取价税合计，为 0 时退回折后金额 */
    private BigDecimal positiveExpenseAmount(BigDecimal netAmount, BigDecimal amount) {
        if (netAmount != null && netAmount.signum() > 0) {
            return netAmount;
        }
        return amount != null ? amount : BigDecimal.ZERO;
    }

    /** 分摊权重：平均=1、按数量=数量、按金额/自定义=金额（金额为 0 时退回数量） */
    private BigDecimal expenseWeight(String method, ExpenseTarget target) {
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

    /** 费用类型 → 成本类型：人工费用=直接人工；制造费用/水电费/办公费=制造费用；其余为期间费用不归集 */
    private String expenseCostType(String expenseType) {
        if (expenseType == null) {
            return null;
        }
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

    // ================= 费率取数（2026-11-09 去除设置中心兜底） =================
    // 费率只认工作中心档案；工作中心未配置费率时，对应项按 0 处理，不再有全局默认费率。

    /** 直接人工费率（元/小时）：取工作中心配置，未配置返回 0 */
    private BigDecimal resolveLaborRate(OperationReport report) {
        WorkCenter wc = workCenterOf(report);
        if (wc != null && wc.getLaborRate() != null && wc.getLaborRate().compareTo(BigDecimal.ZERO) > 0) {
            return wc.getLaborRate();
        }
        return BigDecimal.ZERO;
    }

    /** 制造费用率（元/小时）：取工作中心配置，未配置返回 0 */
    private BigDecimal resolveOverheadRate(OperationReport report) {
        WorkCenter wc = workCenterOf(report);
        if (wc != null && wc.getOverheadRate() != null && wc.getOverheadRate().compareTo(BigDecimal.ZERO) > 0) {
            return wc.getOverheadRate();
        }
        return BigDecimal.ZERO;
    }

    private WorkCenter workCenterOf(OperationReport report) {
        if (report == null || report.getWorkCenterId() == null) return null;
        try {
            return workCenterMapper.selectById(report.getWorkCenterId());
        } catch (Exception e) {
            throw new BusinessException("成本计算失败：读取工作中心失败：" + e.getMessage());
        }
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "成本核算单"; }
    @Override protected String getApprovalBillType() { return "COST_CALCULATION"; }
    @Override protected String getBillNo(CostCalculation b) { return b.getCalcNo(); }
    @Override protected Object getId(CostCalculation b) { return b.getId(); }
    @Override protected String getStatus(CostCalculation b) { return toStatusLabel(b.getStatus()); }
    @Override protected void setStatus(CostCalculation b, String s) { b.setStatus(toStatusValue(s)); }
    @Override protected Integer getCloseStatus(CostCalculation b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(CostCalculation b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(CostCalculation b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(CostCalculation b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(CostCalculation b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(CostCalculation b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(CostCalculation b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(CostCalculation b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(CostCalculation b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(CostCalculation b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(CostCalculation b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(CostCalculation b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(CostCalculation b, String v) { b.setVoidReason(v); }

    /** 库内业务文案 ↔ 统一状态：已核算=待确认(待审核)，已确认=已生效 */
    private static String toStatusLabel(String v) {
        if (v == null || v.isBlank()) return "草稿";
        return switch (v) {
            case "已核算" -> "待审核";
            case "已确认", "已生效" -> "已生效";
            case "已作废" -> "已作废";
            default -> "草稿";
        };
    }

    private static String toStatusValue(String label) {
        if (label == null) return "草稿";
        return switch (label) {
            case "草稿", "已驳回", "已取消", "驳回" -> "草稿";
            case "待审核", "待审批", "审批中" -> "已核算";
            case "已作废", "作废" -> "已作废";
            default -> "已确认";
        };
    }

    @Override
    public List<String> listApprovedDownstreamBills(CostCalculation bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getCalcNo());
    }

    @Override
    protected void validateStrictSubmit(CostCalculation calc) {
        // 确认前无额外强校验；核算金额由 calculateCost 汇总
    }

    /** 确认（审核）：状态校验/状态迁移由统一状态机执行，确认留痕在 onApproved 钩子 */
    @Override
    @Transactional
    public void confirm(Long id) {
        CostCalculation calc = getById(id);
        if (calc == null) throw new BusinessException("成本核算单不存在");
        if (!"已核算".equals(calc.getStatus())) {
            throw new BusinessException("只有已核算状态可确认，当前状态: " + calc.getStatus());
        }
        approve(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
        log.info("成本核算单确认: {} 期间: {}", calc.getCalcNo(), calc.getPeriod());
    }

    /**
     * 反确认（业务动作，非「重置草稿」）：已确认 → 已核算，解锁后可重新核算/再次确认。
     * 保留原有审计口径：版本号递增、记录反确认时间/人。
     */
    @Override
    @Transactional
    public void reverseConfirm(Long id) {
        CostCalculation calc = getById(id);
        if (calc == null) throw new BusinessException("成本核算单不存在");
        if (!"已确认".equals(calc.getStatus())) {
            throw new BusinessException("只有已确认状态可反确认，当前状态: " + calc.getStatus());
        }
        int nextVersion = (calc.getVersion() == null ? 1 : calc.getVersion()) + 1;
        update(new UpdateWrapper<CostCalculation>().eq("id", id)
                .set("status", "已核算")
                .set("approval_status", "待审核")
                .set("confirmed_at", null)
                .set("confirmed_by", null)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("version", nextVersion)
                .set("reversed_at", LocalDateTime.now())
                .set("reversed_by", com.erp.common.bill.BillApprovalAdapter.currentSubmitter()));
        log.info("成本核算单反确认: {} 版本号递增至 {} 期间: {}", calc.getCalcNo(), nextVersion, calc.getPeriod());
    }

    /** 审核：审批在途禁止绕过引擎 + 状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("COST_CALCULATION", (Long) id) != null) {
            throw new BusinessException("成本核算单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 生效影响：写入「确认人/确认时间」业务字段（原 confirm 内的业务影响，迁移至统一钩子） */
    @Override
    public void onApproved(CostCalculation calc) {
        LocalDateTime now = LocalDateTime.now();
        String operator = calc.getApprovedBy();
        update(new UpdateWrapper<CostCalculation>().eq("id", calc.getId())
                .set("confirmed_at", now)
                .set("confirmed_by", operator)
                .set("reversed_at", null)
                .set("reversed_by", null));
        calc.setConfirmedAt(now);
        calc.setConfirmedBy(operator);
        calc.setReversedAt(null);
        calc.setReversedBy(null);
        log.info("成本核算单生效: {} 期间: {} 单位成本: {}", calc.getCalcNo(), calc.getPeriod(), calc.getUnitCost());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CostCalculation calc = getById(id);
        if (calc == null) throw new BusinessException("成本核算单不存在");
        if ("已确认".equals(calc.getStatus())) {
            throw new BusinessException("已确认的成本核算单不能删除，请先反确认");
        }
        // 草稿校验 + 下游校验 + 整单级联删除由统一状态机执行
        super.deleteBill(id);
        log.info("成本核算单删除: {} 期间: {}", calc.getCalcNo(), calc.getPeriod());
    }

    @Override
    public BigDecimal getUnitCost(Long materialId) {
        // 2026-11-03 修复：状态机口径生效状态是「已生效」，原写死「已确认」导致全库无一条命中、最新成本恒为 0；
        // 兼容读取两种状态（按核算日期取最新），不产生新状态。
        QueryWrapper<CostCalculation> wrapper = new QueryWrapper<>();
        wrapper.eq("material_id", materialId).in("status", "已生效", "已确认").orderByDesc("calc_date").last("LIMIT 1");
        List<CostCalculation> list = list(wrapper);
        CostCalculation calc = (list != null && !list.isEmpty()) ? list.get(0) : null;
        return calc != null ? calc.getUnitCost() : BigDecimal.ZERO;
    }

    // ==================== 当期核算 ====================

    @Override
    @Transactional
    public Map<String, Object> periodCalculate(String period) {
        // period 格式: 2026-07
        YearMonth ym = YearMonth.parse(period, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();

        int createdCount = 0;
        int updatedCount = 0;

        // 1. 归集领料单数据 - 按物料分组
        List<MaterialRequisition> requisitions = materialRequisitionMapper.selectList(
                new QueryWrapper<MaterialRequisition>()
                        .eq("status", "已生效")
                        .between("created_at", periodStart.atStartOfDay(), periodEnd.atTime(23, 59, 59)));

        // 物料ID -> 成本明细列表
        Map<Long, List<CostCalculationItem>> materialItemsMap = new HashMap<>();

        for (MaterialRequisition req : requisitions) {
            List<MaterialRequisitionItem> reqItems = materialRequisitionItemMapper.selectList(
                    new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", req.getRequisitionId()));

            for (MaterialRequisitionItem reqItem : reqItems) {
                Long matId = reqItem.getMaterialId();
                if (matId == null) continue;

                CostCalculationItem item = new CostCalculationItem();
                item.setCostType("DIRECT_MATERIAL");
                item.setCostName("直接材料 - " + reqItem.getMaterialName());
                item.setMaterialId(matId);
                item.setMaterialCode(reqItem.getMaterialCode());
                item.setMaterialName(reqItem.getMaterialName());
                item.setUnit("件");
                item.setQuantity(reqItem.getQuantity());
                item.setUnitPrice(reqItem.getUnitPrice());
                item.setAmount(reqItem.getAmount());
                item.setRemark("当期核算-领料单: " + req.getRequisitionNo());

                materialItemsMap.computeIfAbsent(matId, k -> new ArrayList<>()).add(item);
            }
        }

        // 2. 归集报工单数据 - 需要通过工单找到对应物料
        List<OperationReport> reports = operationReportMapper.selectList(
                new QueryWrapper<OperationReport>()
                        .eq("status", "已生效")
                        .between("created_at", periodStart.atStartOfDay(), periodEnd.atTime(23, 59, 59)));

        for (OperationReport report : reports) {
            Long workOrderId = report.getProductionOrderId();
            if (workOrderId == null) continue;

            WorkOrder wo = workOrderMapper.selectById(workOrderId);
            if (wo == null || wo.getMaterialId() == null) continue;

            Long matId = wo.getMaterialId();
            BigDecimal workHours = report.getWorkHours() != null ? report.getWorkHours() : BigDecimal.ZERO;
            BigDecimal overtimeHours = report.getOvertimeHours() != null ? report.getOvertimeHours() : BigDecimal.ZERO;
            BigDecimal totalHours = workHours.add(overtimeHours);

            if (totalHours.compareTo(BigDecimal.ZERO) > 0) {
                // 人工费率取数（2026-09-27 第5期·去硬编码）：工作中心人工费率 → 设置中心默认费率 → 50
                BigDecimal laborRate = resolveLaborRate(report);
                BigDecimal laborCost = totalHours.multiply(laborRate).setScale(2, RoundingMode.HALF_UP);

                CostCalculationItem item = new CostCalculationItem();
                item.setCostType("DIRECT_LABOR");
                item.setCostName("直接人工 - " + report.getOperationName());
                item.setQuantity(totalHours);
                item.setUnitPrice(laborRate);
                item.setAmount(laborCost);
                item.setRemark("当期核算-报工单: " + report.getReportNo() + " 费率: " + laborRate);

                // 设置物料信息
                Material mat = materialMapper.selectById(matId);
                if (mat != null) {
                    item.setMaterialId(matId);
                    item.setMaterialCode(mat.getMaterialCode());
                    item.setMaterialName(mat.getMaterialName());
                }

                materialItemsMap.computeIfAbsent(matId, k -> new ArrayList<>()).add(item);

                // 制造费用：工作中心制造费用率未配置时为 0 → 不产生明细（与改造前一致）
                BigDecimal overheadRate = resolveOverheadRate(report);
                if (overheadRate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal overheadCost = totalHours.multiply(overheadRate).setScale(2, RoundingMode.HALF_UP);
                    CostCalculationItem oh = new CostCalculationItem();
                    oh.setCostType("OVERHEAD");
                    oh.setCostName("制造费用 - " + report.getOperationName());
                    oh.setQuantity(totalHours);
                    oh.setUnitPrice(overheadRate);
                    oh.setAmount(overheadCost);
                    oh.setRemark("当期核算-报工单: " + report.getReportNo() + " 工作中心制造费用率: " + overheadRate);
                    if (mat != null) {
                        oh.setMaterialId(matId);
                        oh.setMaterialCode(mat.getMaterialCode());
                        oh.setMaterialName(mat.getMaterialName());
                    }
                    materialItemsMap.computeIfAbsent(matId, k -> new ArrayList<>()).add(oh);
                }
            }
        }

        // 3. 归集已生效的运营费用分摊数据
        List<OperationExpense> expenses = operationExpenseMapper.selectList(
                new QueryWrapper<OperationExpense>()
                        .eq("status", "已生效")
                        .between("expense_date", periodStart, periodEnd));

        for (OperationExpense expense : expenses) {
            List<OperationExpenseAllocation> allocations = operationExpenseAllocationMapper.selectList(
                    new QueryWrapper<OperationExpenseAllocation>().eq("expense_id", expense.getExpenseId()));

            for (OperationExpenseAllocation alloc : allocations) {
                String lineExpenseType = alloc.getExpenseType() != null ? alloc.getExpenseType() : expense.getExpenseType();
                String costType = expenseCostType(lineExpenseType);
                // 期间费用（出差/宴请/快递等）不归集产品成本，与费用单审核时的口径一致
                if (costType == null || alloc.getOrderId() == null) {
                    continue;
                }
                BigDecimal totalAmount = alloc.getAmount() != null ? alloc.getAmount() : BigDecimal.ZERO;
                if (totalAmount.signum() <= 0) {
                    continue;
                }
                List<ExpenseTarget> targets = loadExpenseTargets(alloc);
                if (targets.isEmpty()) {
                    log.warn("费用分摊单 {} 分摊明细[orderType={}, orderId={}]未找到可归集物料, 跳过",
                            expense.getExpenseNo(), alloc.getOrderType(), alloc.getOrderId());
                    continue;
                }
                // 按表头「分摊方式」把本行金额拆到每一行物料（2026-11-26 修复：原实现每个物料各记整笔金额，
                // 费用 400 会记成 400+400）；同一物料出现在多行时先按物料汇总份额
                String method = expense.getAllocationMethod();
                BigDecimal totalWeight = BigDecimal.ZERO;
                for (ExpenseTarget t : targets) {
                    totalWeight = totalWeight.add(expenseWeight(method, t));
                }
                Map<Long, BigDecimal> shareByMaterial = new LinkedHashMap<>();
                Map<Long, ExpenseTarget> materialInfo = new LinkedHashMap<>();
                BigDecimal allocated = BigDecimal.ZERO;
                for (int i = 0; i < targets.size(); i++) {
                    ExpenseTarget t = targets.get(i);
                    BigDecimal share;
                    if (i == targets.size() - 1) {
                        // 尾差进最后一行，保证合计等于分摊金额
                        share = totalAmount.subtract(allocated);
                    } else if (totalWeight.signum() > 0) {
                        share = totalAmount.multiply(expenseWeight(method, t))
                                .divide(totalWeight, 2, RoundingMode.HALF_UP);
                    } else {
                        share = totalAmount.divide(BigDecimal.valueOf(targets.size()), 2, RoundingMode.HALF_UP);
                    }
                    allocated = allocated.add(share);
                    shareByMaterial.merge(t.materialId(), share, BigDecimal::add);
                    materialInfo.putIfAbsent(t.materialId(), t);
                }
                for (Map.Entry<Long, BigDecimal> entry : shareByMaterial.entrySet()) {
                    if (entry.getValue().signum() <= 0) {
                        continue;
                    }
                    ExpenseTarget t = materialInfo.get(entry.getKey());
                    CostCalculationItem item = new CostCalculationItem();
                    item.setCostType(costType);
                    item.setCostName("DIRECT_LABOR".equals(costType) ? "直接人工 - " + lineExpenseType : "制造费用 - " + lineExpenseType);
                    item.setMaterialId(entry.getKey());
                    item.setMaterialCode(t.materialCode());
                    item.setMaterialName(t.materialName());
                    item.setQuantity(BigDecimal.ONE);
                    item.setUnitPrice(entry.getValue());
                    item.setAmount(entry.getValue());
                    item.setRemark("当期核算-费用分摊: " + expense.getExpenseNo());

                    materialItemsMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(item);
                }
            }
        }

        // 3.5 归集质检报废损失（2026-11-07）：区间内已生效生产入库单中「进报废仓」的不合格品成本
        List<ProductionReceipt> scrapReceipts = productionReceiptMapper.selectList(
                new QueryWrapper<ProductionReceipt>()
                        .eq("status", "已生效")
                        .between("receipt_date", periodStart, periodEnd));
        for (ProductionReceipt receipt : scrapReceipts) {
            List<ProductionReceiptItem> rows = productionReceiptItemMapper.selectList(
                    new QueryWrapper<ProductionReceiptItem>().eq("receipt_id", receipt.getReceiptId()));
            for (ProductionReceiptItem row : rows) {
                if (!"SCRAP".equals(row.getHandlingType()) || row.getMaterialId() == null) continue;
                BigDecimal qty = row.getRejectedQuantity() != null ? row.getRejectedQuantity() : BigDecimal.ZERO;
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal unitCost = row.getUnitPrice() != null && row.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                        ? row.getUnitPrice()
                        : (row.getAvgCost() != null ? row.getAvgCost() : BigDecimal.ZERO);
                CostCalculationItem item = new CostCalculationItem();
                item.setCostType("SCRAP_LOSS");
                item.setCostName("质检报废损失 - " + row.getMaterialName());
                item.setMaterialId(row.getMaterialId());
                item.setMaterialCode(row.getMaterialCode());
                item.setMaterialName(row.getMaterialName());
                item.setQuantity(qty);
                item.setUnitPrice(unitCost);
                item.setAmount(qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP));
                item.setRemark("当期核算-入库单: " + receipt.getReceiptNo() + " 质检不合格进报废仓");
                materialItemsMap.computeIfAbsent(row.getMaterialId(), k -> new ArrayList<>()).add(item);
            }
        }

        // 4. 为每个物料创建或更新成本核算单
        int totalMaterials = materialItemsMap.size();
        List<String> skippedConfirmed = new ArrayList<>();

        for (Map.Entry<Long, List<CostCalculationItem>> entry : materialItemsMap.entrySet()) {
            Long matId = entry.getKey();
            List<CostCalculationItem> items = entry.getValue();

            Material material = materialMapper.selectById(matId);
            if (material == null) continue;

            // 检查该物料在该期间是否已有生效快照（已确认/已生效） - 生效后的历史快照不参与重新核算
            // 2026-11-04：全站状态机的生效状态是「已生效」，原只认「已确认」→ 已审核生效的核算单会被当期核算覆盖改写
            CostCalculation confirmedCalc = getOne(new QueryWrapper<CostCalculation>()
                    .eq("material_id", matId)
                    .eq("period", period)
                    .in("status", "已确认", "已生效")
                    .last("LIMIT 1"), false);
            if (confirmedCalc != null) {
                skippedConfirmed.add(material.getMaterialCode() + " - " + material.getMaterialName());
                continue;
            }

            // 计算各成本类型合计
            BigDecimal directMaterialCost = BigDecimal.ZERO;
            BigDecimal directLaborCost = BigDecimal.ZERO;
            BigDecimal manufacturingOverhead = BigDecimal.ZERO;
            BigDecimal scrapLoss = BigDecimal.ZERO;

            for (CostCalculationItem item : items) {
                BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                switch (item.getCostType()) {
                    case "DIRECT_MATERIAL" -> directMaterialCost = directMaterialCost.add(amount);
                    case "DIRECT_LABOR" -> directLaborCost = directLaborCost.add(amount);
                    case "OVERHEAD" -> manufacturingOverhead = manufacturingOverhead.add(amount);
                    case "SCRAP_LOSS" -> scrapLoss = scrapLoss.add(amount);
                }
            }

            BigDecimal totalCost = directMaterialCost.add(directLaborCost).add(manufacturingOverhead).add(scrapLoss);

            // 获取该物料当期入库数量作为核算数量
            List<InventoryTransaction> matInTrans = inventoryTransactionMapper.selectList(
                    new QueryWrapper<InventoryTransaction>()
                            .eq("material_id", matId)
                            .between("trans_date", periodStart, periodEnd)
                            .gt("in_quantity", BigDecimal.ZERO));
            BigDecimal calcQuantity = matInTrans.stream()
                    .map(t -> t.getInQuantity() != null ? t.getInQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 如果没有入库数据，至少用1避免除以0
            if (calcQuantity.compareTo(BigDecimal.ZERO) == 0) {
                calcQuantity = BigDecimal.ONE;
            }

            // 查找该物料在当期是否已有核算单（草稿或已核算状态，已确认/已生效的已跳过）
            QueryWrapper<CostCalculation> existWrapper = new QueryWrapper<>();
            existWrapper.eq("material_id", matId)
                    .eq("period", period)
                    .notIn("status", "已确认", "已生效"); // 只更新未生效的
            CostCalculation existingCalc = getOne(existWrapper, false);

            if (existingCalc != null) {
                // 更新已有核算单
                existingCalc.setDirectMaterialCost(directMaterialCost.setScale(SCALE, RoundingMode.HALF_UP));
                existingCalc.setDirectLaborCost(directLaborCost.setScale(SCALE, RoundingMode.HALF_UP));
                existingCalc.setManufacturingOverhead(manufacturingOverhead.setScale(SCALE, RoundingMode.HALF_UP));
                existingCalc.setScrapLoss(scrapLoss.setScale(SCALE, RoundingMode.HALF_UP));
                existingCalc.setTotalCost(totalCost.setScale(SCALE, RoundingMode.HALF_UP));
                existingCalc.setQuantity(calcQuantity);

                // 单位成本 = 总成本 / 核算数量（加权平均）
                existingCalc.setUnitCost(totalCost.divide(calcQuantity, SCALE, RoundingMode.HALF_UP));
                existingCalc.setStatus("已核算");
                existingCalc.setCalcDate(periodEnd);
                updateById(existingCalc);

                // 删除旧明细并重新插入
                itemMapper.delete(new QueryWrapper<CostCalculationItem>().eq("calc_id", existingCalc.getId()));
                for (CostCalculationItem item : items) {
                    item.setId(null);
                    item.setCalcId(existingCalc.getId());
                    itemMapper.insert(item);
                }
                updatedCount++;
            } else {
                // 创建新核算单
                CostCalculation calc = new CostCalculation();
                calc.setCalcNo(generateCalcNo());
                calc.setCalcName(period + " 当期核算 - " + material.getMaterialName());
                calc.setMaterialId(matId);
                calc.setMaterialCode(material.getMaterialCode());
                calc.setMaterialName(material.getMaterialName());
                calc.setPeriod(period);
                calc.setCalcDate(periodEnd); // 使用期间末日期，确保按期间匹配
                calc.setCalcMethod("ACTUAL");
                calc.setQuantity(calcQuantity);
                calc.setDirectMaterialCost(directMaterialCost.setScale(SCALE, RoundingMode.HALF_UP));
                calc.setDirectLaborCost(directLaborCost.setScale(SCALE, RoundingMode.HALF_UP));
                calc.setManufacturingOverhead(manufacturingOverhead.setScale(SCALE, RoundingMode.HALF_UP));
                calc.setScrapLoss(scrapLoss.setScale(SCALE, RoundingMode.HALF_UP));
                calc.setTotalCost(totalCost.setScale(SCALE, RoundingMode.HALF_UP));
                // 单位成本 = 总成本 / 核算数量（加权平均法）
                calc.setUnitCost(totalCost.divide(calcQuantity, SCALE, RoundingMode.HALF_UP));
                calc.setStatus("已核算");
                calc.setVersion(1);
                calc.setRemark("当期核算自动生成 - " + period);
                save(calc);

                for (CostCalculationItem item : items) {
                    item.setId(null);
                    item.setCalcId(calc.getId());
                    itemMapper.insert(item);
                }
                createdCount++;
            }
        }

        log.info("当期核算完成: 期间={} 物料数={} 新建={} 更新={} 跳过已确认={}",
                period, totalMaterials, createdCount, updatedCount, skippedConfirmed.size());

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("totalMaterials", totalMaterials);
        result.put("createdCount", createdCount);
        result.put("updatedCount", updatedCount);
        result.put("skippedConfirmedCount", skippedConfirmed.size());
        result.put("skippedConfirmedMaterials", skippedConfirmed);
        return result;
    }

    // ==================== 成本报表 ====================

    @Override
    public List<CostReportItem> getCostReport(String period, String keyword) {
        YearMonth ym = YearMonth.parse(period, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();
        LocalDate prevPeriodEnd = periodStart.minusDays(1);

        // 收集所有需要展示的物料ID：有成本核算单的 + 有库存流水的
        Set<Long> materialIds = new java.util.HashSet<>();
        
        // 1. 查询该期间有成本核算单的物料（已生效=状态机生效态；已核算=未确认的当期核算）
        List<CostCalculation> calcs = list(new QueryWrapper<CostCalculation>()
                .in("status", "已核算", "已确认", "已生效")
                .ge("calc_date", periodStart)
                .le("calc_date", periodEnd));
        for (CostCalculation c : calcs) {
            if (c.getMaterialId() != null) materialIds.add(c.getMaterialId());
        }
        
        // 2. 查询该期间有库存流水的物料
        List<InventoryTransaction> allTrans = inventoryTransactionMapper.selectList(
                new QueryWrapper<InventoryTransaction>()
                        .le("trans_date", periodEnd)
                        .select("DISTINCT material_id"));
        for (InventoryTransaction t : allTrans) {
            // 所选列 material_id 为空的行，MyBatis 可能映射成 null 元素（同 WorkstationController 的坑）
            if (t != null && t.getMaterialId() != null) materialIds.add(t.getMaterialId());
        }

        // 获取物料详情
        List<Material> materials;
        if (materialIds.isEmpty()) {
            // 如果没有指定物料，返回所有启用物料（支持关键字过滤）
            QueryWrapper<Material> matWrapper = new QueryWrapper<>();
            if (keyword != null && !keyword.isBlank()) {
                matWrapper.and(w -> w.like("material_code", keyword).or().like("material_name", keyword));
            }
            matWrapper.eq("status", "启用").last("LIMIT 200");
            materials = materialMapper.selectList(matWrapper);
        } else {
            QueryWrapper<Material> matWrapper = new QueryWrapper<>();
            matWrapper.in("material_id", materialIds);
            if (keyword != null && !keyword.isBlank()) {
                matWrapper.and(w -> w.like("material_code", keyword).or().like("material_name", keyword));
            }
            materials = materialMapper.selectList(matWrapper);
        }

        List<CostReportItem> reportItems = new ArrayList<>();

        for (Material material : materials) {
            CostReportItem item = new CostReportItem();
            item.setMaterialId(material.getMaterialId());
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getMaterialName());
            item.setSpecification(material.getModel());
            item.setUnit(""); // 从单位表获取，简化处理

            // 查询库存台账获取期初/本期入库/本期出库/期末数据

            // 期初：上期期末的余额（截止到periodStart前一天的所有流水余额）
            InventoryTransaction lastPrevTrans = inventoryTransactionMapper.selectOne(
                    new QueryWrapper<InventoryTransaction>()
                            .eq("material_id", material.getMaterialId())
                            .le("trans_date", prevPeriodEnd)
                            .orderByDesc("trans_date")
                            .last("LIMIT 1"));

            if (lastPrevTrans != null) {
                item.setBeginQuantity(lastPrevTrans.getBalanceQuantity() != null ? lastPrevTrans.getBalanceQuantity() : BigDecimal.ZERO);
                item.setBeginAmount(lastPrevTrans.getBalanceAmount() != null ? lastPrevTrans.getBalanceAmount() : BigDecimal.ZERO);
            } else {
                item.setBeginQuantity(BigDecimal.ZERO);
                item.setBeginAmount(BigDecimal.ZERO);
            }

            // 本期入库：该期间内所有入库流水的汇总
            List<InventoryTransaction> inTrans = inventoryTransactionMapper.selectList(
                    new QueryWrapper<InventoryTransaction>()
                            .eq("material_id", material.getMaterialId())
                            .between("trans_date", periodStart, periodEnd)
                            .gt("in_quantity", BigDecimal.ZERO));

            BigDecimal inQty = inTrans.stream()
                    .map(t -> t.getInQuantity() != null ? t.getInQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal inAmt = inTrans.stream()
                    .map(t -> t.getInAmount() != null ? t.getInAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.setInQuantity(inQty);
            item.setInAmount(inAmt);

            // 本期出库：该期间内所有出库流水的汇总
            List<InventoryTransaction> outTrans = inventoryTransactionMapper.selectList(
                    new QueryWrapper<InventoryTransaction>()
                            .eq("material_id", material.getMaterialId())
                            .between("trans_date", periodStart, periodEnd)
                            .gt("out_quantity", BigDecimal.ZERO));

            BigDecimal outQty = outTrans.stream()
                    .map(t -> t.getOutQuantity() != null ? t.getOutQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outAmt = outTrans.stream()
                    .map(t -> t.getOutAmount() != null ? t.getOutAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.setOutQuantity(outQty);
            item.setOutAmount(outAmt);

            // 期末 = 期初 + 入库 - 出库
            BigDecimal endQty = item.getBeginQuantity().add(inQty).subtract(outQty);
            BigDecimal endAmt = item.getBeginAmount().add(inAmt).subtract(outAmt);
            item.setEndQuantity(endQty);
            item.setEndAmount(endAmt);

            // 从已核算或已生效（含历史已确认）的成本核算单中获取成本构成
            CostCalculation calc = getOne(new QueryWrapper<CostCalculation>()
                    .eq("material_id", material.getMaterialId())
                    .in("status", "已核算", "已确认", "已生效")
                    .ge("calc_date", periodStart)
                    .le("calc_date", periodEnd)
                    .orderByDesc("calc_date")
                    .last("LIMIT 1"), false);

            BigDecimal directMaterialCost = BigDecimal.ZERO;
            BigDecimal directLaborCost = BigDecimal.ZERO;
            BigDecimal manufacturingOverhead = BigDecimal.ZERO;
            if (calc != null) {
                directMaterialCost = calc.getDirectMaterialCost() != null ? calc.getDirectMaterialCost() : BigDecimal.ZERO;
                directLaborCost = calc.getDirectLaborCost() != null ? calc.getDirectLaborCost() : BigDecimal.ZERO;
                manufacturingOverhead = calc.getManufacturingOverhead() != null ? calc.getManufacturingOverhead() : BigDecimal.ZERO;
            }
            item.setDirectMaterialCost(directMaterialCost);
            item.setDirectLaborCost(directLaborCost);
            item.setManufacturingOverhead(manufacturingOverhead);

            // 单位成本 = 完全成本法（加权平均 + 期间费用平摊）
            // 单位成本 = (期初金额 + 本期入库金额 + 直接人工 + 制造费用) / (期初数量 + 本期入库数量)
            // 说明: 入库金额通常是采购价（已含直接材料采购成本），
            //       直接人工、制造费用作为期间生产费用按入库数量平摊到单位成本
            BigDecimal totalQty = item.getBeginQuantity().add(inQty);
            BigDecimal totalAmt = item.getBeginAmount().add(inAmt)
                    .add(directLaborCost)
                    .add(manufacturingOverhead);
            if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                item.setUnitCost(totalAmt.divide(totalQty, SCALE, RoundingMode.HALF_UP));
            } else if (endQty.compareTo(BigDecimal.ZERO) > 0) {
                // 期末有数量但无入库时，用期末金额/期末数量
                item.setUnitCost(endAmt.divide(endQty, SCALE, RoundingMode.HALF_UP));
            } else {
                // 没有库存数据时，用核算单的单位成本
                item.setUnitCost(BigDecimal.ZERO);
            }

            // 只包含有数据的物料（期初、入库、出库、成本核算单至少有一项不为零）
            // 注意：单位成本已使用完全成本法计算（加权平均+人工制造费用平摊），不再用核算单的unitCost覆盖
            if (item.getBeginQuantity().compareTo(BigDecimal.ZERO) != 0
                    || inQty.compareTo(BigDecimal.ZERO) != 0
                    || outQty.compareTo(BigDecimal.ZERO) != 0
                    || (calc != null && calc.getTotalCost() != null && calc.getTotalCost().compareTo(BigDecimal.ZERO) != 0)) {
                reportItems.add(item);
            }
        }

        return reportItems;
    }

    private String generateCalcNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "CC-" + dateStr + "-";
        QueryWrapper<CostCalculation> wrapper = new QueryWrapper<>();
        wrapper.likeRight("calc_no", prefix).orderByDesc("calc_no").last("LIMIT 1");
        CostCalculation last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getCalcNo() != null) {
            try {
                seq = Integer.parseInt(last.getCalcNo().substring(prefix.length())) + 1;
            } catch (Exception e) {
                log.warn("成本计算失败：生成计算批次编码失败: prefix={}, lastNo={}, err={}", prefix, last.getCalcNo(), e.getMessage());
                throw new BusinessException("成本计算失败：生成计算批次编码失败：" + e.getMessage());
            }
        }
        return prefix + String.format("%04d", seq);
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
        update(new UpdateWrapper<CostCalculation>().eq("id", id)
                .set("reject_reason", reason)
                .set("rejector", rejector)
                .set("rejected_at", LocalDateTime.now()));
        log.info("驳回成本核算单: {} 操作人: {} 原因: {}", id, rejector, reason);
    }

    /** 重置草稿：状态校验/下游校验/状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<CostCalculation>().eq("id", id)
                .set("approved_by", null).set("approved_at", null)
                .set("reverse_approver", operator)
                .set("reverse_approved_at", LocalDateTime.now()));
        log.info("重置草稿成本核算单: {} 操作人: {}", id, operator);
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 重置草稿影响：与 onApproved 严格镜像，清空「确认人/确认时间」，记录反确认痕迹 */
    @Override
    public void onReverseApproved(CostCalculation bill) {
        // 同步内存实体：confirmed_* / reversed_* 为 IGNORED 更新策略，基类随后的 updateById 会按实体值回写
        bill.setConfirmedAt(null);
        bill.setConfirmedBy(null);
        bill.setReversedAt(LocalDateTime.now());
        bill.setReversedBy(bill.getApprovedBy());
        update(new UpdateWrapper<CostCalculation>().eq("id", bill.getId())
                .set("confirmed_at", null)
                .set("confirmed_by", null)
                .set("reversed_at", bill.getReversedAt())
                .set("reversed_by", bill.getReversedBy()));
        log.info("成本核算单重置草稿冲回确认痕迹: {}", bill.getCalcNo());
    }

    /** 作废影响：与 onApproved 严格镜像 */
    @Override
    public void onVoided(CostCalculation bill) {
        onReverseApproved(bill);
        log.info("成本核算单作废: {} 期间: {}", bill.getCalcNo(), bill.getPeriod());
    }

    /**
     * 反作废影响（2026-11-09）：作废时已清空确认痕迹，反作废恢复到「已生效」必须重放正向过账，
     * 否则核算单生效却无确认人/确认时间。与 onVoided 严格镜像（重放都是幂等 UPDATE，无唯一流水号/通知）。
     */
    @Override
    public void onUnvoided(CostCalculation bill) {
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