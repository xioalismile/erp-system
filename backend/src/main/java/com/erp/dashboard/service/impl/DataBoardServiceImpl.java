package com.erp.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import com.erp.basedata.mapper.UnitOfMeasureMapper;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.entity.SalesTargetCompany;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.crm.mapper.SalesTargetCompanyMapper;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.entity.CustomFieldValue;
import com.erp.customfield.mapper.CustomFieldMapper;
import com.erp.customfield.mapper.CustomFieldValueMapper;
import com.erp.dashboard.service.DataBoardService;
import com.erp.dashboard.service.SaleCostResolver;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.entity.OperationExpenseAllocation;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.expense.purchase.entity.PurchaseExpense;
import com.erp.finance.expense.purchase.mapper.PurchaseExpenseMapper;
import com.erp.finance.expense.sale.entity.SaleExpense;
import com.erp.finance.expense.sale.mapper.SaleExpenseMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.OperationExpenseAllocationMapper;
import com.erp.finance.mapper.OperationExpenseMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据看板实现。全部指标取自真实单据表，口径见 {@link DataBoardService}。
 *
 * <p>安全说明：无任何字符串拼接 SQL，全部 QueryWrapper 参数化；period 仅接受
 * month/quarter/year 白名单；金额一律 BigDecimal，除零统一返回 0。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataBoardServiceImpl implements DataBoardService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final CustomerMapper customerMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper materialCategoryMapper;
    private final AccountsReceivableMapper accountsReceivableMapper;
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final PaymentVoucherMapper paymentVoucherMapper;
    private final SaleExpenseMapper saleExpenseMapper;
    private final PurchaseExpenseMapper purchaseExpenseMapper;
    private final OperationExpenseMapper operationExpenseMapper;
    private final OperationExpenseAllocationMapper operationExpenseAllocationMapper;
    private final OpportunityMapper opportunityMapper;
    private final SalesTargetCompanyMapper salesTargetCompanyMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CustomFieldValueMapper customFieldValueMapper;
    private final CustomFieldMapper customFieldMapper;
    private final UnitOfMeasureMapper unitOfMeasureMapper;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;
    private final SaleCostResolver saleCostResolver;

    private static final String AUDITED = "已生效";
    private static final int TARGET_WARN_RATE = 85;
    /** 产品排行图数据上限：前端展示条数最多 30，取 50 保证切换条数时不用重新请求 */
    private static final int MATERIAL_RANK_LIMIT = 50;
    /** 产品排行固定可选展示字段：key + 中文名 */
    private static final String[][] MATERIAL_FIXED_FIELDS = {
            {"materialName", "物料名称"},
            {"materialCode", "物料编码"},
            {"model", "规格型号"},
            {"categoryName", "物料分类"},
            {"baseUnitName", "基本单位"}
    };

    // ==================== 财务看板 ====================

    @Override
    public Map<String, Object> getFinanceBoard(String period, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        LocalDate[] range = resolveRange(period);
        LocalDate start = range[0], end = range[1];
        int days = Math.max(1, (int) ChronoUnit.DAYS.between(start, end));

        // ---- 营收/成本/毛利（已生效订单，不含税口径算毛利） ----
        List<SaleOrder> periodOrders = auditedOrders(start, end, scopeCtx);
        OrderAgg agg = aggregateOrders(periodOrders);

        LocalDate today = LocalDate.now();
        BigDecimal todayRevenue = sumAmount(auditedOrders(today, today.plusDays(1), scopeCtx));
        BigDecimal monthRevenue = sumAmount(auditedOrders(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1), scopeCtx));

        // ---- 费用（三张费用单，已生效） ----
        BigDecimal saleExp = sumExpense(saleExpenseList(start, end, scopeCtx));
        BigDecimal purchaseExp = sumExpense(purchaseExpenseList(start, end, scopeCtx));
        List<OperationExpense> manageExpenses = operationExpenseList(start, end, scopeCtx);
        BigDecimal manageExp = sumOperationExpense(manageExpenses);

        // ---- 费用结构：按费用类型聚合（取自分摊明细 operation_expense_allocation，
        //      无分摊明细的费用单回退用单据上的 expense_type），以支撑"财务费用率"等按类型口径 ----
        List<Map<String, Object>> expenseByType = operationExpenseTypeDist(manageExpenses);
        BigDecimal financeExp = expenseTypeAmount(expenseByType, "财务费用");

        // ---- 净利润 = 毛利 - 销售费用 - 采购费用 - 税金 ----
        // 说明：成本为完全成本法（销售订单明细 avg_cost 取自成本核算单 unit_cost，已含运营费用分摊），
        // 故此处不再重复扣减运营(管理)费用，避免同一笔费用扣两次。
        BigDecimal taxAmount = periodOrders.stream()
                .map(o -> nvl(o.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netProfit = agg.grossProfit.subtract(saleExp).subtract(purchaseExp).subtract(taxAmount);

        // ---- 应收（【2026-10-19 设计图 v3】改按销售订单口径：未收余额 = 订单价税合计 − 订单已收，不再读应收单表） ----
        QueryWrapper<SaleOrder> arSoWrapper = new QueryWrapper<SaleOrder>().eq("status", "已生效");
        if (scopeCtx != null) scopeCtx.applyTo(arSoWrapper, "saler_id");
        List<SaleOrder> openArOrders = saleOrderMapper.selectList(arSoWrapper);
        BigDecimal arBalance = BigDecimal.ZERO, arOverdue = BigDecimal.ZERO, riskAmount = BigDecimal.ZERO;
        for (SaleOrder so : openArOrders) {
            BigDecimal bal = nvl(so.getNetAmount()).subtract(nvl(so.getReceivedAmount()));
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
            arBalance = arBalance.add(bal);
            LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
            if (base != null) {
                long over = ChronoUnit.DAYS.between(base, today);
                if (over > 0) arOverdue = arOverdue.add(bal);
                if (over > 180) riskAmount = riskAmount.add(bal);
            }
        }
        // 应收周转天数 = 应收余额 / (期间营收 / 天数)
        BigDecimal dailyRevenue = agg.revenueAmount.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal arTurnoverDays = dailyRevenue.compareTo(BigDecimal.ZERO) > 0
                ? arBalance.divide(dailyRevenue, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // ---- 现金流（收款单-付款单，已确认/已生效） ----
        BigDecimal cashIn = sumReceipt(start, end, scopeCtx);
        BigDecimal cashOut = sumPayment(start, end, scopeCtx);

        // ---- 目标达成率 ----
        BigDecimal target = periodTarget(period);
        BigDecimal targetRate = target.compareTo(BigDecimal.ZERO) > 0
                ? agg.revenueAmount.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ---- 图表：近12月营收/成本/毛利趋势 ----
        List<Map<String, Object>> revenueTrend = new ArrayList<>();
        YearMonth ym = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            LocalDate ms = ym.atDay(1), me = ym.plusMonths(1).atDay(1);
            OrderAgg a = aggregateOrders(auditedOrders(ms, me, scopeCtx));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym.toString());
            row.put("revenue", a.revenueAmount);
            row.put("cost", a.costAmount);
            row.put("gross", a.grossProfit);
            revenueTrend.add(row);
            ym = ym.plusMonths(1);
        }

        // ---- 图表：产品线毛利贡献 ----
        List<Map<String, Object>> productLineGross = buildProductLineAgg(periodOrders);

        // ---- 图表：各区域营收分布（客户自定义字段 region，无配置时前端显示空态引导） ----
        Map<String, BigDecimal> regionDist = new LinkedHashMap<>();
        Map<Long, String> regionByCustomer = customerRegionMap();
        for (SaleOrder o : periodOrders) {
            String region = o.getCustomerId() == null ? null : regionByCustomer.get(o.getCustomerId());
            regionDist.merge(region == null || region.isBlank() ? "未填写" : region,
                    nvl(o.getNetAmount()), BigDecimal::add);
        }

        // ---- 图表：账龄分布（复用财务账龄口径：销售订单未收余额，起算日=发货日期，无发货取订单日期） ----
        List<Map<String, Object>> arAging = buildArAgingOrders(openArOrders, today);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", period);
        data.put("periodLabel", periodLabel(period));
        data.put("todayRevenue", todayRevenue);
        data.put("monthRevenue", monthRevenue);
        data.put("periodRevenue", agg.revenueAmount);
        data.put("periodNetRevenue", agg.netRevenue);
        data.put("periodCost", agg.costAmount);
        data.put("grossBaseNet", agg.grossBaseNet);
        data.put("grossProfit", agg.grossProfit);
        data.put("grossMarginRate", rate(agg.grossProfit, agg.grossBaseNet));
        data.put("costStats", agg.costStats);
        data.put("netProfit", netProfit);
        // 净利润计算明细（口径透明化：毛利 − 销售费用 − 采购费用 − 税金；运营费用已计入成本不重复扣）
        Map<String, Object> netProfitDetail = new LinkedHashMap<>();
        netProfitDetail.put("grossProfit", agg.grossProfit);
        netProfitDetail.put("saleExpense", saleExp);
        netProfitDetail.put("purchaseExpense", purchaseExp);
        netProfitDetail.put("taxAmount", taxAmount);
        netProfitDetail.put("manageExpenseInCost", manageExp); // 参考值：已随完全成本法进入销售成本，不再单列扣减
        data.put("netProfitDetail", netProfitDetail);
        data.put("expenseSale", saleExp);
        data.put("expensePurchase", purchaseExp);
        data.put("expenseManage", manageExp);
        data.put("expenseTotal", saleExp.add(purchaseExp).add(manageExp));
        data.put("saleExpenseRate", rate(saleExp, agg.netRevenue));
        data.put("manageExpenseRate", rate(manageExp, agg.netRevenue));
        data.put("financeExpense", financeExp);
        data.put("financeExpenseRate", rate(financeExp, agg.netRevenue));
        data.put("taxAmount", taxAmount);
        data.put("arBalance", arBalance);
        data.put("arOverdue", arOverdue);
        data.put("riskAmount", riskAmount);
        data.put("riskRate", rate(riskAmount, arBalance));
        data.put("arTurnoverDays", arTurnoverDays);
        data.put("cashIn", cashIn);
        data.put("cashOut", cashOut);
        data.put("cashFlowNet", cashIn.subtract(cashOut));
        data.put("targetAmount", target);
        data.put("targetRate", targetRate);
        data.put("targetWarn", target.compareTo(BigDecimal.ZERO) > 0
                && targetRate.compareTo(BigDecimal.valueOf(TARGET_WARN_RATE)) < 0);
        data.put("revenueTrend", revenueTrend);
        data.put("productLineGross", productLineGross);
        data.put("productLineRef", productLineOptions());
        data.put("regionDist", toNameValueList(regionDist));
        data.put("expenseStructure", buildExpenseStructure(saleExp, purchaseExp, expenseByType));
        data.put("expenseByType", expenseByType);
        data.put("arAging", arAging);
        return data;
    }

    // ==================== 销售看板 ====================

    @Override
    public Map<String, Object> getSaleBoard(String period, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        LocalDate[] range = resolveRange(period);
        LocalDate start = range[0], end = range[1];
        LocalDate today = LocalDate.now();

        List<SaleOrder> periodOrders = auditedOrders(start, end, scopeCtx);
        OrderAgg agg = aggregateOrders(periodOrders);
        BigDecimal periodSale = sumAmount(periodOrders);

        BigDecimal todaySale = sumAmount(auditedOrders(today, today.plusDays(1), scopeCtx));
        BigDecimal monthSale = sumAmount(auditedOrders(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1), scopeCtx));
        int orderCount = periodOrders.size();
        BigDecimal avgOrderAmount = orderCount > 0
                ? periodSale.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 目标
        BigDecimal target = periodTarget(period);
        BigDecimal targetRate = target.compareTo(BigDecimal.ZERO) > 0
                ? periodSale.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 回款率 = 已生效销售订单的 已收/应收（【2026-10-19 设计图 v3】改按订单口径，不再读应收单表；数据权限过滤）
        QueryWrapper<SaleOrder> allSoWrapper = new QueryWrapper<SaleOrder>().eq("status", "已生效");
        if (scopeCtx != null) scopeCtx.applyTo(allSoWrapper, "saler_id");
        List<SaleOrder> allSo = saleOrderMapper.selectList(allSoWrapper);
        // 客户名称解析（订单行冗余可能为空，从客户档案兜底）
        Map<Long, String> soCustName = customerMapper.selectList(null).stream()
                .filter(c -> c.getCustomerId() != null)
                .collect(Collectors.toMap(Customer::getCustomerId, c -> c.getCustomerName() == null ? "未知客户" : c.getCustomerName(), (a, b) -> a));
        BigDecimal arTotal = BigDecimal.ZERO, arPaid = BigDecimal.ZERO, arOverdue = BigDecimal.ZERO;
        Map<String, BigDecimal[]> overdueByCust = new LinkedHashMap<>(); // name -> [amount, maxDays]
        for (SaleOrder so : allSo) {
            BigDecimal net = nvl(so.getNetAmount());
            BigDecimal rec = nvl(so.getReceivedAmount());
            arTotal = arTotal.add(net);
            arPaid = arPaid.add(rec);
            BigDecimal bal = net.subtract(rec);
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
            LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
            if (base != null) {
                long over = ChronoUnit.DAYS.between(base, today);
                if (over > 0) {
                    arOverdue = arOverdue.add(bal);
                    String cname = so.getCustomerId() != null
                            ? soCustName.getOrDefault(so.getCustomerId(), "未知客户")
                            : (so.getCustomerName() == null || so.getCustomerName().isBlank() ? "未知客户" : so.getCustomerName());
                    BigDecimal[] v = overdueByCust.computeIfAbsent(cname, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    v[0] = v[0].add(bal);
                    if (BigDecimal.valueOf(over).compareTo(v[1]) > 0) v[1] = BigDecimal.valueOf(over);
                }
            }
        }
        List<Map<String, Object>> overdueTop = overdueByCust.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerName", e.getKey());
                    m.put("overdueAmount", e.getValue()[0]);
                    m.put("overdueDays", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());

        // 商机储备（未关闭，数据权限过滤）
        QueryWrapper<Opportunity> oppWrapper = new QueryWrapper<Opportunity>().eq("is_closed", 0);
        if (scopeCtx != null) scopeCtx.applyTo(oppWrapper, "assigned_sales_id");
        List<Opportunity> opps = opportunityMapper.selectList(oppWrapper);
        BigDecimal pipeline = opps.stream().map(o -> nvl(o.getEstimatedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 客户档案（等级 + 建档时间，用于等级分布和新老客户）
        List<Customer> customers = customerMapper.selectList(null);
        Map<Long, Customer> custById = customers.stream()
                .filter(c -> c.getCustomerId() != null)
                .collect(Collectors.toMap(Customer::getCustomerId, c -> c, (a, b) -> a));

        // 产品线分布 + 新老客户 + TOP/BOTTOM 物料
        Map<Long, String> lineByMaterial = productLineByMaterial();
        Map<String, BigDecimal> lineDist = new LinkedHashMap<>();
        Map<String, BigDecimal> levelDist = new LinkedHashMap<>();
        Map<String, BigDecimal> regionDist = new LinkedHashMap<>();
        Map<Long, String> regionByCustomer = customerRegionMap();
        Map<String, BigDecimal[]> newOld = new LinkedHashMap<>(); // name -> [amount, count]
        Map<Long, BigDecimal[]> matAgg = new LinkedHashMap<>();   // materialId -> [amount, qty]
        Map<Long, String> matName = new HashMap<>();
        // materialId -> 排行图可展示字段（名称/编码/规格/分类/单位），供前端按用户勾选拼标签
        Map<Long, Map<String, String>> matDisplay = new HashMap<>();

        List<SaleOrderItem> items = itemsOfOrders(periodOrders);
        // 物料名称解析：明细行名称可能为空（历史数据），从物料档案兜底
        Map<Long, Material> matById = materialMapper.selectList(null).stream()
                .filter(m -> m.getMaterialId() != null)
                .collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
        // 分类 / 单位名称解析（物料档案只存ID），供排行图展示字段兜底
        Map<Long, String> categoryNameById = materialCategoryMapper.selectList(null).stream()
                .filter(c -> c.getCategoryId() != null && c.getCategoryName() != null)
                .collect(Collectors.toMap(MaterialCategory::getCategoryId, MaterialCategory::getCategoryName, (a, b) -> a));
        Map<Long, String> unitNameById = unitOfMeasureMapper.selectList(null).stream()
                .filter(u -> u.getUnitId() != null && u.getUnitName() != null)
                .collect(Collectors.toMap(UnitOfMeasure::getUnitId, UnitOfMeasure::getUnitName, (a, b) -> a));
        Map<Long, String> orderCustomer = periodOrders.stream()
                .filter(o -> o.getOrderId() != null && o.getCustomerId() != null)
                .collect(Collectors.toMap(SaleOrder::getOrderId, o -> String.valueOf(o.getCustomerId()), (a, b) -> a));

        for (SaleOrder o : periodOrders) {
            Customer c = o.getCustomerId() == null ? null : custById.get(o.getCustomerId());
            String level = c != null && c.getCustomerLevel() != null && !c.getCustomerLevel().isBlank()
                    ? c.getCustomerLevel() : "未分级";
            levelDist.merge(level, nvl(o.getTotalAmount()), BigDecimal::add);
            String region = o.getCustomerId() == null ? null : regionByCustomer.get(o.getCustomerId());
            regionDist.merge(region == null || region.isBlank() ? "未填写" : region,
                    nvl(o.getTotalAmount()), BigDecimal::add);
            boolean isNew = c != null && c.getCreatedAt() != null && !c.getCreatedAt().toLocalDate().isBefore(start);
            String key = isNew ? "新客户" : "老客户";
            BigDecimal[] v = newOld.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(nvl(o.getTotalAmount()));
            v[1] = v[1].add(BigDecimal.ONE);
        }
        for (SaleOrderItem it : items) {
            String line = it.getMaterialId() == null ? "未分类" : lineByMaterial.getOrDefault(it.getMaterialId(), "未分类");
            lineDist.merge(line, nvl(it.getAmount()), BigDecimal::add);
            BigDecimal[] v = matAgg.computeIfAbsent(it.getMaterialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(nvl(it.getAmount()));
            v[1] = v[1].add(nvl(it.getQuantity()));
            if (it.getMaterialId() != null) {
                Material mat = matById.get(it.getMaterialId());
                String name = it.getMaterialName() != null && !it.getMaterialName().isBlank()
                        ? it.getMaterialName()
                        : (mat != null && mat.getMaterialName() != null ? mat.getMaterialName()
                        : (mat != null && mat.getMaterialCode() != null ? mat.getMaterialCode() : "未知物料"));
                matName.put(it.getMaterialId(), name);
                // 排行图标签字段：明细行优先，物料档案兜底（同名不同规格/颜色的产品靠这些字段区分）
                Map<String, String> fields = matDisplay.computeIfAbsent(it.getMaterialId(), k -> new LinkedHashMap<>());
                fields.put("materialName", name);
                putIfNotBlank(fields, "materialCode",
                        firstNonBlank(it.getMaterialCode(), mat == null ? null : mat.getMaterialCode()));
                putIfNotBlank(fields, "model",
                        firstNonBlank(it.getSpecification(), mat == null ? null : mat.getModel()));
                putIfNotBlank(fields, "categoryName",
                        mat == null || mat.getCategoryId() == null ? null : categoryNameById.get(mat.getCategoryId()));
                putIfNotBlank(fields, "baseUnitName",
                        mat == null || mat.getBaseUnitId() == null ? null : unitNameById.get(mat.getBaseUnitId()));
            }
        }

        List<Map<String, Object>> topMaterials = topMaterials(matAgg, matName, matDisplay, true, MATERIAL_RANK_LIMIT);
        List<Map<String, Object>> bottomMaterials = topMaterials(matAgg, matName, matDisplay, false, MATERIAL_RANK_LIMIT);
        // 物料自定义字段值（EAV）：只取本次排行命中的物料，供前端"展示字段"勾选颜色等
        Set<Long> rankMaterialIds = new LinkedHashSet<>();
        for (Map<String, Object> row : topMaterials) {
            if (row.get("materialId") instanceof Long id) rankMaterialIds.add(id);
        }
        for (Map<String, Object> row : bottomMaterials) {
            if (row.get("materialId") instanceof Long id) rankMaterialIds.add(id);
        }
        Map<Long, Map<String, String>> matCustomFields = materialCustomValues(rankMaterialIds);
        topMaterials.forEach(row -> attachCustomFields(row, matCustomFields));
        bottomMaterials.forEach(row -> attachCustomFields(row, matCustomFields));

        // 销售趋势（近12月）
        List<Map<String, Object>> saleTrend = new ArrayList<>();
        YearMonth ym = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            LocalDate ms = ym.atDay(1), me = ym.plusMonths(1).atDay(1);
            List<SaleOrder> os = auditedOrders(ms, me, scopeCtx);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym.toString());
            row.put("amount", sumAmount(os));
            row.put("orderCount", os.size());
            saleTrend.add(row);
            ym = ym.plusMonths(1);
        }

        // 销售费用率
        BigDecimal saleExp = sumExpense(saleExpenseList(start, end, scopeCtx));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", period);
        data.put("periodLabel", periodLabel(period));
        data.put("todaySale", todaySale);
        data.put("monthSale", monthSale);
        data.put("periodSale", periodSale);
        data.put("orderCount", orderCount);
        data.put("avgOrderAmount", avgOrderAmount);
        data.put("targetAmount", target);
        data.put("targetRate", targetRate);
        data.put("targetWarn", target.compareTo(BigDecimal.ZERO) > 0
                && targetRate.compareTo(BigDecimal.valueOf(TARGET_WARN_RATE)) < 0);
        data.put("paybackRate", rate(arPaid, arTotal));
        data.put("arOverdue", arOverdue);
        data.put("pipelineAmount", pipeline);
        data.put("pipelineCount", opps.size());
        data.put("saleExpenseRate", rate(saleExp, agg.netRevenue));
        data.put("saleTrend", saleTrend);
        data.put("productLineDist", toNameValueList(lineDist));
        data.put("productLineRef", productLineOptions());
        data.put("customerLevelDist", toNameValueList(levelDist));
        data.put("regionDist", toNameValueList(regionDist));
        data.put("topMaterials", topMaterials);
        data.put("bottomMaterials", bottomMaterials);
        // 产品排行图「展示字段」候选清单：固定字段 + 物料档案已启用的自定义字段（如颜色）
        data.put("materialFieldOptions", materialFieldOptions());
        data.put("newOldCustomer", newOld.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("amount", e.getValue()[0]);
            m.put("orderCount", e.getValue()[1]);
            return m;
        }).collect(Collectors.toList()));
        data.put("overdueByCustomer", overdueTop);
        return data;
    }

    // ==================== 仓储/库存看板 ====================

    @Override
    public Map<String, Object> getInventoryBoard(String period, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        LocalDate[] range = resolveRange(period);
        LocalDate start = range[0], end = range[1];
        int days = Math.max(1, (int) ChronoUnit.DAYS.between(start, end));
        LocalDate today = LocalDate.now();

        // 仓库数据权限（2026-09-02）：可见仓库 = 数据范围仓库 ∪ 员工白名单仓库；无可见仓库 → 全部指标为0
        List<Long> visibleStockIds = scopeCtx == null ? null : dataScopeResolver.visibleWarehouseIds(scopeCtx);
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            return emptyInventoryBoard(period);
        }
        QueryWrapper<Inventory> stockWrapper = new QueryWrapper<>();
        if (visibleStockIds != null) stockWrapper.in("stock_id", visibleStockIds);
        List<Inventory> stocks = inventoryMapper.selectList(stockWrapper);
        BigDecimal stockTotalAmount = BigDecimal.ZERO, longAgeAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal> qtyByMaterial = new HashMap<>();
        Map<Long, Object[]> longAgeByMaterial = new LinkedHashMap<>(); // materialId -> [amount, code, name]
        for (Inventory inv : stocks) {
            BigDecimal cost = nvl(inv.getTotalCost());
            stockTotalAmount = stockTotalAmount.add(cost);
            qtyByMaterial.merge(inv.getMaterialId(), nvl(inv.getQuantity()), BigDecimal::add);
            if (inv.getLastInDate() != null && ChronoUnit.DAYS.between(inv.getLastInDate(), today) > 90) {
                longAgeAmount = longAgeAmount.add(cost);
                Object[] v = longAgeByMaterial.computeIfAbsent(inv.getMaterialId(),
                        k -> new Object[]{BigDecimal.ZERO,
                                inv.getMaterialCode() == null ? "" : inv.getMaterialCode(),
                                inv.getMaterialName() == null ? "" : inv.getMaterialName()});
                v[0] = ((BigDecimal) v[0]).add(cost);
            }
        }

        // 呆滞物料：库龄超180天 且 从未出库（按可见仓库过滤流水）
        BigDecimal staleAmount = BigDecimal.ZERO;
        List<Map<String, Object>> staleList = new ArrayList<>();
        QueryWrapper<InventoryTransaction> everOutWrapper = new QueryWrapper<InventoryTransaction>().gt("out_quantity", 0);
        if (visibleStockIds != null) everOutWrapper.in("stock_id", visibleStockIds);
        Set<Long> everOutMaterials = inventoryTransactionMapper.selectList(everOutWrapper).stream()
                .map(InventoryTransaction::getMaterialId).collect(Collectors.toSet());
        for (Map.Entry<Long, Object[]> e : longAgeByMaterial.entrySet()) {
            BigDecimal amt = (BigDecimal) e.getValue()[0];
            if (ChronoUnit.DAYS.between(
                    stocks.stream().filter(s -> e.getKey().equals(s.getMaterialId()))
                            .map(Inventory::getLastInDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today),
                    today) > 180 && !everOutMaterials.contains(e.getKey())) {
                staleAmount = staleAmount.add(amt);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialCode", e.getValue()[1]);
                m.put("materialName", e.getValue()[2]);
                m.put("amount", amt);
                staleList.add(m);
            }
        }

        // 期间出库金额/数量（库存台账，按可见仓库过滤）
        QueryWrapper<InventoryTransaction> ledgerWrapper = new QueryWrapper<InventoryTransaction>()
                .ge("trans_date", start).lt("trans_date", end);
        if (visibleStockIds != null) ledgerWrapper.in("stock_id", visibleStockIds);
        List<InventoryTransaction> periodLedger = inventoryTransactionMapper.selectList(ledgerWrapper);
        BigDecimal periodOutAmount = periodLedger.stream().map(t -> nvl(t.getOutAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal periodOutQty = periodLedger.stream().map(t -> nvl(t.getOutQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal periodInAmount = periodLedger.stream().map(t -> nvl(t.getInAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 平均库存金额 = (期初 + 期末) / 2；期初 = 期末 − (期间入库金额 − 期间出库金额)
        BigDecimal beginStockAmount = stockTotalAmount.subtract(periodInAmount).add(periodOutAmount);
        BigDecimal avgStockAmount = beginStockAmount.add(stockTotalAmount)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        // 周转率（年化）= 期间出库金额 × (365/期间天数) / 平均库存金额
        BigDecimal turnoverRate = avgStockAmount.compareTo(BigDecimal.ZERO) > 0
                ? periodOutAmount.multiply(BigDecimal.valueOf(365))
                    .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP)
                    .divide(avgStockAmount, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal turnoverDays = turnoverRate.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(365).divide(turnoverRate, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 平均可销天数 = 当前库存数量 / 日均销量（销售订单明细数量口径，替代原"日均出库"）
        BigDecimal totalQty = qtyByMaterial.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal periodSaleQty = itemsOfOrders(auditedOrders(start, end, scopeCtx)).stream()
                .map(it -> nvl(it.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailySale = periodSaleQty.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal avgSaleableDays = dailySale.compareTo(BigDecimal.ZERO) > 0
                ? totalQty.divide(dailySale, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 缺货SKU：material.min_stock > 0 且 现存合计 < 安全库存
        List<Material> materials = materialMapper.selectList(
                new QueryWrapper<Material>().isNotNull("min_stock").gt("min_stock", 0));
        List<Map<String, Object>> shortageList = new ArrayList<>();
        for (Material m : materials) {
            BigDecimal qty = qtyByMaterial.getOrDefault(m.getMaterialId(), BigDecimal.ZERO);
            if (qty.compareTo(nvl(m.getMinStock())) < 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("materialCode", m.getMaterialCode());
                row.put("materialName", m.getMaterialName());
                row.put("stockQuantity", qty);
                row.put("minStock", m.getMinStock());
                row.put("gap", nvl(m.getMinStock()).subtract(qty));
                shortageList.add(row);
            }
        }
        shortageList.sort((a, b) -> ((BigDecimal) b.get("gap")).compareTo((BigDecimal) a.get("gap")));

        // 库存结构：按物料类型 + 按产品线
        Map<Long, Material> matById = materialMapper.selectList(null).stream()
                .filter(m -> m.getMaterialId() != null)
                .collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
        Map<Long, String> lineByMaterial = productLineByMaterial();
        Map<String, BigDecimal> typeDist = new LinkedHashMap<>();
        Map<String, BigDecimal> lineDist = new LinkedHashMap<>();
        Map<String, BigDecimal> rawVsProduct = new LinkedHashMap<>();
        for (Inventory inv : stocks) {
            Material m = matById.get(inv.getMaterialId());
            String type = m != null && m.getMaterialType() != null ? m.getMaterialType() : "未分类";
            String line = m == null ? "未分类" : lineByMaterial.getOrDefault(m.getMaterialId(), "未分类");
            typeDist.merge(type, nvl(inv.getTotalCost()), BigDecimal::add);
            lineDist.merge(line, nvl(inv.getTotalCost()), BigDecimal::add);
            if ("原材料".equals(type) || "包材".equals(type)) {
                rawVsProduct.merge("原材料及包材", nvl(inv.getTotalCost()), BigDecimal::add);
            } else if ("成品".equals(type) || "半成品".equals(type)) {
                rawVsProduct.merge("成品及半成品", nvl(inv.getTotalCost()), BigDecimal::add);
            }
        }

        // 近6月 库存出库金额 vs 销售额 趋势（按可见仓库过滤）
        List<Map<String, Object>> stockVsSale = new ArrayList<>();
        YearMonth ym = YearMonth.now().minusMonths(5);
        for (int i = 0; i < 6; i++) {
            LocalDate ms = ym.atDay(1), me = ym.plusMonths(1).atDay(1);
            QueryWrapper<InventoryTransaction> trendWrapper = new QueryWrapper<InventoryTransaction>()
                    .ge("trans_date", ms).lt("trans_date", me);
            if (visibleStockIds != null) trendWrapper.in("stock_id", visibleStockIds);
            BigDecimal out = inventoryTransactionMapper.selectList(trendWrapper).stream()
                    .map(t -> nvl(t.getOutAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sale = sumAmount(auditedOrders(ms, me, scopeCtx));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym.toString());
            row.put("stockOutAmount", out);
            row.put("saleAmount", sale);
            stockVsSale.add(row);
            ym = ym.plusMonths(1);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", period);
        data.put("periodLabel", periodLabel(period));
        data.put("stockTotalAmount", stockTotalAmount);
        data.put("stockTotalQuantity", totalQty);
        data.put("periodOutAmount", periodOutAmount);
        data.put("periodSaleQty", periodSaleQty);
        data.put("beginStockAmount", beginStockAmount);
        data.put("avgStockAmount", avgStockAmount);
        data.put("turnoverRate", turnoverRate);
        data.put("turnoverDays", turnoverDays);
        data.put("longAgeAmount", longAgeAmount);
        data.put("longAgeCount", longAgeByMaterial.size());
        data.put("staleAmount", staleAmount);
        data.put("staleCount", staleList.size());
        data.put("shortageSkuCount", shortageList.size());
        data.put("avgSaleableDays", avgSaleableDays);
        data.put("typeDist", toNameValueList(typeDist));
        data.put("lineDist", toNameValueList(lineDist));
        data.put("productLineRef", productLineOptions());
        data.put("rawVsProduct", toNameValueList(rawVsProduct));
        data.put("stockVsSaleTrend", stockVsSale);
        data.put("shortageList", shortageList.size() > 10 ? shortageList.subList(0, 10) : shortageList);
        data.put("staleList", staleList.size() > 10 ? staleList.subList(0, 10) : staleList);
        return data;
    }

    // ==================== 公共辅助 ====================

    /** period 白名单解析为 [start, end)：day=今日，week=本周(周一起)，month/quarter/year 同上 */
    private LocalDate[] resolveRange(String period) {
        LocalDate now = LocalDate.now();
        switch (period == null ? "month" : period) {
            case "day": {
                return new LocalDate[]{now, now.plusDays(1)};
            }
            case "week": {
                LocalDate start = now.with(DayOfWeek.MONDAY);
                return new LocalDate[]{start, start.plusWeeks(1)};
            }
            case "quarter": {
                LocalDate start = now.withDayOfMonth(1).withMonth(((now.getMonthValue() - 1) / 3) * 3 + 1);
                return new LocalDate[]{start, start.plusMonths(3)};
            }
            case "year": {
                LocalDate start = now.withDayOfYear(1);
                return new LocalDate[]{start, start.plusYears(1)};
            }
            default: {
                LocalDate start = now.withDayOfMonth(1);
                return new LocalDate[]{start, start.plusMonths(1)};
            }
        }
    }

    private String periodLabel(String period) {
        switch (period == null ? "month" : period) {
            case "day": return "今日";
            case "week": return "本周";
            case "quarter": return "本季度";
            case "year": return "本年度";
            default: return "本月";
        }
    }

    private List<SaleOrder> auditedOrders(LocalDate start, LocalDate end, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<SaleOrder>()
                .eq("status", AUDITED).ge("order_date", start).lt("order_date", end);
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/经手的订单；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        return saleOrderMapper.selectList(wrapper);
    }

    private List<SaleOrderItem> itemsOfOrders(List<SaleOrder> orders) {
        List<Long> ids = orders.stream().map(SaleOrder::getOrderId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyList();
        List<SaleOrderItem> items = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            items.addAll(saleOrderItemMapper.selectList(
                    new QueryWrapper<SaleOrderItem>().in("order_id", ids.subList(i, Math.min(i + 500, ids.size())))));
        }
        return items;
    }

    /** 订单聚合：含税金额/不含税金额/成本/毛利。
     *  不含税营收以明细为准（订单头 net_amount = 明细合计，二者只取其一防重复累计）；
     *  无明细的订单兜底取订单头。
     *  销售成本按「单据成本 → 核算成本 → 库存成本 → 采购价」三级兜底，缺失行不参与毛利计算。 */
    private OrderAgg aggregateOrders(List<SaleOrder> orders) {
        OrderAgg agg = new OrderAgg();
        agg.revenueAmount = sumAmount(orders);
        BigDecimal headerNet = orders.stream().map(o -> nvl(o.getNetAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SaleOrderItem> items = itemsOfOrders(orders);
        if (items.isEmpty()) {
            agg.netRevenue = headerNet;
            agg.grossBaseNet = headerNet;
            agg.grossProfit = BigDecimal.ZERO;
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalRows", orders.size());
            stats.put("missingRows", orders.size());
            stats.put("abnormalRows", 0);
            stats.put("sourceCounts", Collections.emptyMap());
            stats.put("allMissing", !orders.isEmpty());
            agg.costStats = stats;
            return agg;
        }
        SaleCostResolver.Resolution cost = saleCostResolver.resolve(items);
        for (int i = 0; i < items.size(); i++) {
            SaleOrderItem it = items.get(i);
            agg.netRevenue = agg.netRevenue.add(nvl(it.getNetAmount()));
            if (!cost.included.get(i)) continue;
            agg.grossBaseNet = agg.grossBaseNet.add(nvl(it.getNetAmount()));
            BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
            agg.costAmount = agg.costAmount.add(cost.unitCosts.get(i).multiply(qty));
        }
        agg.grossProfit = agg.grossBaseNet.subtract(agg.costAmount);
        agg.costStats = costStatsOf(cost);
        return agg;
    }

    /** 成本统计（供界面显示“成本缺失 / 部分缺失”，避免用 0 或 100% 冒充） */
    private Map<String, Object> costStatsOf(SaleCostResolver.Resolution cost) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRows", cost.totalRows);
        stats.put("missingRows", cost.missingRows);
        stats.put("abnormalRows", cost.abnormalRows);
        stats.put("sourceCounts", cost.sourceCounts);
        stats.put("allMissing", cost.isAllMissing());
        return stats;
    }

    private BigDecimal sumAmount(List<SaleOrder> orders) {
        return orders.stream().map(o -> nvl(o.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 产品线（一级分类）毛利/营收聚合：成本按三级兜底，缺失行不参与毛利 */
    private List<Map<String, Object>> buildProductLineAgg(List<SaleOrder> orders) {
        Map<Long, String> lineByMaterial = productLineByMaterial();
        Map<String, BigDecimal[]> byLine = new LinkedHashMap<>(); // line -> [netRevenue, cost]
        List<SaleOrderItem> items = itemsOfOrders(orders);
        SaleCostResolver.Resolution cost = saleCostResolver.resolve(items);
        for (int i = 0; i < items.size(); i++) {
            SaleOrderItem it = items.get(i);
            if (!cost.included.get(i)) continue;
            String line = it.getMaterialId() == null ? "未分类" : lineByMaterial.getOrDefault(it.getMaterialId(), "未分类");
            BigDecimal[] v = byLine.computeIfAbsent(line, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(nvl(it.getNetAmount()));
            v[1] = v[1].add(cost.unitCosts.get(i).multiply(nvl(it.getQuantity())));
        }
        return byLine.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("netRevenue", e.getValue()[0]);
            m.put("cost", e.getValue()[1]);
            m.put("gross", e.getValue()[0].subtract(e.getValue()[1]));
            m.put("marginRate", rate(e.getValue()[0].subtract(e.getValue()[1]), e.getValue()[0]));
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("gross")).compareTo((BigDecimal) a.get("gross")))
                .collect(Collectors.toList());
    }

    /** 物料 -> 产品线（material_category 一级分类，向上回溯父级） */
    private Map<Long, String> productLineByMaterial() {
        List<MaterialCategory> cats = materialCategoryMapper.selectList(null);
        Map<Long, MaterialCategory> byId = cats.stream()
                .filter(c -> c.getCategoryId() != null)
                .collect(Collectors.toMap(MaterialCategory::getCategoryId, c -> c, (a, b) -> a));
        Map<Long, String> lineNameByCat = new HashMap<>();
        for (MaterialCategory c : cats) {
            MaterialCategory cur = c;
            int guard = 0;
            while (cur != null && guard++ < 5) {
                if (cur.getCategoryLevel() != null && cur.getCategoryLevel() == 1) break;
                cur = cur.getParentId() == null ? null : byId.get(cur.getParentId());
            }
            lineNameByCat.put(c.getCategoryId(), cur != null && cur.getCategoryName() != null
                    ? cur.getCategoryName() : (c.getCategoryName() != null ? c.getCategoryName() : "未分类"));
        }
        Map<Long, String> result = new HashMap<>();
        for (Material m : materialMapper.selectList(null)) {
            if (m.getMaterialId() == null) continue;
            result.put(m.getMaterialId(), m.getCategoryId() == null ? "未分类"
                    : lineNameByCat.getOrDefault(m.getCategoryId(), "未分类"));
        }
        return result;
    }

    /** 一级分类（产品线）选项：{categoryId, name}，供前端"按产品线穿透"用 */
    private List<Map<String, Object>> productLineOptions() {
        return materialCategoryMapper.selectList(null).stream()
                .filter(c -> c.getCategoryLevel() != null && c.getCategoryLevel() == 1 && c.getCategoryId() != null)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categoryId", c.getCategoryId());
                    m.put("name", c.getCategoryName() == null ? "未分类" : c.getCategoryName());
                    return m;
                })
                .sorted((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))))
                .collect(Collectors.toList());
    }

    /** 客户 -> 区域：取自定义字段值表 sys_custom_field_value（entity_type=customer, field_key=region） */
    private Map<Long, String> customerRegionMap() {
        List<CustomFieldValue> values = customFieldValueMapper.selectList(
                new QueryWrapper<CustomFieldValue>()
                        .eq("entity_type", "customer").eq("field_key", "region"));
        Map<Long, String> map = new HashMap<>();
        for (CustomFieldValue v : values) {
            if (v.getEntityId() == null) continue;
            String val = v.getFieldValue() == null ? "" : v.getFieldValue().trim();
            if (!val.isEmpty()) map.put(v.getEntityId(), val);
        }
        return map;
    }

    private List<Map<String, Object>> buildArAgingOrders(List<SaleOrder> orders, LocalDate today) {
        BigDecimal a0 = BigDecimal.ZERO, a30 = BigDecimal.ZERO, a60 = BigDecimal.ZERO,
                a90 = BigDecimal.ZERO, a180 = BigDecimal.ZERO;
        for (SaleOrder so : orders) {
            BigDecimal bal = nvl(so.getNetAmount()).subtract(nvl(so.getReceivedAmount()));
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
            LocalDate base = so.getDeliveryDate() != null ? so.getDeliveryDate() : so.getOrderDate();
            if (base == null) continue;
            long over = ChronoUnit.DAYS.between(base, today);
            if (over <= 0) a0 = a0.add(bal);
            else if (over <= 30) a30 = a30.add(bal);
            else if (over <= 60) a60 = a60.add(bal);
            else if (over <= 180) a90 = a90.add(bal);
            else a180 = a180.add(bal);
        }
        return Arrays.asList(
                nameValue("未到期", a0),
                nameValue("逾期1-30天", a30),
                nameValue("逾期31-60天", a60),
                nameValue("逾期61-180天", a90),
                nameValue("逾期180天以上", a180));
    }

    private List<Map<String, Object>> topMaterials(Map<Long, BigDecimal[]> matAgg, Map<Long, String> matName,
                                                   Map<Long, Map<String, String>> matDisplay,
                                                   boolean desc, int limit) {
        Comparator<Map.Entry<Long, BigDecimal[]>> cmp = Comparator.comparing(e -> e.getValue()[0]);
        return matAgg.entrySet().stream()
                .sorted(desc ? cmp.reversed() : cmp)
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    String name = matName.getOrDefault(e.getKey(), "未知物料");
                    m.put("materialId", e.getKey());
                    m.put("materialName", name);
                    m.put("amount", e.getValue()[0]);
                    m.put("quantity", e.getValue()[1]);
                    // fields：排行图标签候选值（名称必在），前端按用户勾选的字段拼「名称 · 颜色」
                    Map<String, String> fields = new LinkedHashMap<>();
                    Map<String, String> ext = e.getKey() == null ? null : matDisplay.get(e.getKey());
                    if (ext != null) fields.putAll(ext);
                    fields.putIfAbsent("materialName", name);
                    m.put("fields", fields);
                    return m;
                }).collect(Collectors.toList());
    }

    /** 物料自定义字段值：entity_type=material，仅取本次排行命中的物料（EAV 值表） */
    private Map<Long, Map<String, String>> materialCustomValues(Collection<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return Collections.emptyMap();
        List<CustomFieldValue> values = customFieldValueMapper.selectList(
                new QueryWrapper<CustomFieldValue>()
                        .eq("entity_type", "material")
                        .in("entity_id", materialIds));
        Map<Long, Map<String, String>> map = new HashMap<>();
        for (CustomFieldValue v : values) {
            if (v.getEntityId() == null || v.getFieldKey() == null) continue;
            String val = v.getFieldValue() == null ? "" : v.getFieldValue().trim();
            if (val.isEmpty()) continue;
            map.computeIfAbsent(v.getEntityId(), k -> new LinkedHashMap<>()).put(v.getFieldKey(), val);
        }
        return map;
    }

    /** 把物料自定义字段值并入排行行的 fields（键 = fieldKey，与「展示字段」候选项一致） */
    @SuppressWarnings("unchecked")
    private void attachCustomFields(Map<String, Object> row, Map<Long, Map<String, String>> matCustomFields) {
        if (row == null || !(row.get("materialId") instanceof Long id)) return;
        Map<String, String> custom = matCustomFields.get(id);
        if (custom == null || custom.isEmpty()) return;
        Object fieldsObj = row.get("fields");
        if (fieldsObj instanceof Map) ((Map<String, String>) fieldsObj).putAll(custom);
    }

    /** 产品排行「展示字段」候选：固定字段 + 物料档案已启用的自定义字段（按设置中心排序） */
    private List<Map<String, Object>> materialFieldOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String[] f : MATERIAL_FIXED_FIELDS) {
            options.add(fieldOption(f[0], f[1], false));
            used.add(f[0]);
        }
        List<CustomField> defs = customFieldMapper.selectList(
                new QueryWrapper<CustomField>()
                        .eq("entity_type", "material")
                        .eq("is_enabled", 1)
                        .orderByAsc("sort_order")
                        .orderByAsc("field_id"));
        for (CustomField f : defs) {
            if (f.getFieldKey() == null || f.getFieldKey().isBlank() || used.contains(f.getFieldKey())) continue;
            options.add(fieldOption(f.getFieldKey(),
                    f.getFieldLabel() == null || f.getFieldLabel().isBlank() ? f.getFieldKey() : f.getFieldLabel(), true));
            used.add(f.getFieldKey());
        }
        return options;
    }

    private static Map<String, Object> fieldOption(String key, String label, boolean custom) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("custom", custom);
        return m;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private static void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private List<SaleExpense> saleExpenseList(LocalDate start, LocalDate end, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        // sale_expense.status 为 integer 数字枚举：0草稿/1待审核/2已生效/3已分摊/4作废
        // 有效口径 = 已生效 + 已分摊（已分摊单据金额仍然发生）
        QueryWrapper<SaleExpense> wrapper = new QueryWrapper<SaleExpense>()
                .in("status", 2, 3).ge("expense_date", start).lt("expense_date", end);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        return saleExpenseMapper.selectList(wrapper);
    }

    private List<PurchaseExpense> purchaseExpenseList(LocalDate start, LocalDate end, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        // purchase_expense.status 同为 integer 数字枚举，口径与 saleExpenseList 一致
        QueryWrapper<PurchaseExpense> wrapper = new QueryWrapper<PurchaseExpense>()
                .in("status", 2, 3).ge("expense_date", start).lt("expense_date", end);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        return purchaseExpenseMapper.selectList(wrapper);
    }

    private List<OperationExpense> operationExpenseList(LocalDate start, LocalDate end, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<OperationExpense> wrapper = new QueryWrapper<OperationExpense>()
                .eq("status", AUDITED).ge("expense_date", start).lt("expense_date", end);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "applicant_name");
        return operationExpenseMapper.selectList(wrapper);
    }

    private BigDecimal sumExpense(List<? extends Object> expenses) {
        // SaleExpense/PurchaseExpense 均有 getTotalAmount()
        return expenses.stream().map(e -> {
            if (e instanceof SaleExpense se) return nvl(se.getTotalAmount());
            if (e instanceof PurchaseExpense pe) return nvl(pe.getTotalAmount());
            return BigDecimal.ZERO;
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumOperationExpense(List<OperationExpense> list) {
        return list.stream().map(e -> nvl(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 运营费用按"费用类型"聚合（支撑费用结构与财务费用率）。
     * 口径：优先取分摊明细 {@code operation_expense_allocation.expense_type} 的金额；
     * 该费用单无分摊明细（或分摊金额合计为 0）时，回退用单据上的 {@code operation_expense.expense_type} + amount。
     * 全部 QueryWrapper 参数化，无字符串拼接。
     */
    private List<Map<String, Object>> operationExpenseTypeDist(List<OperationExpense> expenses) {
        if (expenses == null || expenses.isEmpty()) return Collections.emptyList();
        List<Long> ids = expenses.stream().map(OperationExpense::getExpenseId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Map<String, BigDecimal>> allocByExpense = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 500) {
            List<OperationExpenseAllocation> allocs = operationExpenseAllocationMapper.selectList(
                    new QueryWrapper<OperationExpenseAllocation>()
                            .in("expense_id", ids.subList(i, Math.min(i + 500, ids.size()))));
            for (OperationExpenseAllocation a : allocs) {
                if (a.getExpenseId() == null) continue;
                String type = a.getExpenseType() == null || a.getExpenseType().isBlank()
                        ? "未分类" : a.getExpenseType().trim();
                allocByExpense.computeIfAbsent(a.getExpenseId(), k -> new LinkedHashMap<>())
                        .merge(type, nvl(a.getAmount()), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> dist = new LinkedHashMap<>();
        for (OperationExpense e : expenses) {
            Map<String, BigDecimal> alloc = e.getExpenseId() == null ? null : allocByExpense.get(e.getExpenseId());
            BigDecimal allocSum = alloc == null ? BigDecimal.ZERO
                    : alloc.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (alloc != null && !alloc.isEmpty() && allocSum.compareTo(BigDecimal.ZERO) != 0) {
                alloc.forEach((k, v) -> dist.merge(k, v, BigDecimal::add));
            } else {
                String type = e.getExpenseType() == null || e.getExpenseType().isBlank()
                        ? "未分类" : e.getExpenseType().trim();
                dist.merge(type, nvl(e.getAmount()), BigDecimal::add);
            }
        }
        return dist.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(en -> nameValue(en.getKey(), en.getValue()))
                .collect(Collectors.toList());
    }

    /** 从"名称-金额"列表中取指定类型的金额，不存在返回 0 */
    private BigDecimal expenseTypeAmount(List<Map<String, Object>> dist, String typeName) {
        if (dist == null) return BigDecimal.ZERO;
        for (Map<String, Object> m : dist) {
            if (typeName.equals(m.get("name"))) return nvl((BigDecimal) m.get("value"));
        }
        return BigDecimal.ZERO;
    }

    /** 费用结构饼图数据：销售费用 / 采购费用（>0 才展示） + 运营费用各类型 */
    private List<Map<String, Object>> buildExpenseStructure(BigDecimal saleExp, BigDecimal purchaseExp,
                                                            List<Map<String, Object>> expenseByType) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (nvl(saleExp).compareTo(BigDecimal.ZERO) > 0) list.add(nameValue("销售费用", saleExp));
        if (nvl(purchaseExp).compareTo(BigDecimal.ZERO) > 0) list.add(nameValue("采购费用", purchaseExp));
        if (expenseByType != null) list.addAll(expenseByType);
        return list;
    }

    private BigDecimal sumReceipt(LocalDate start, LocalDate end, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<PaymentReceipt> wrapper = new QueryWrapper<PaymentReceipt>()
                .in("status", "已确认", AUDITED)
                .ge("receipt_date", start).lt("receipt_date", end);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        return paymentReceiptMapper.selectList(wrapper)
                .stream().map(r -> nvl(r.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPayment(LocalDate start, LocalDate end, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<PaymentVoucher> wrapper = new QueryWrapper<PaymentVoucher>()
                .in("status", "已确认", AUDITED)
                .ge("payment_date", start).lt("payment_date", end);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        return paymentVoucherMapper.selectList(wrapper)
                .stream().map(p -> nvl(p.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 无可见仓库时返回全 0 库存看板（保持前端结构不变） */
    private Map<String, Object> emptyInventoryBoard(String period) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", period);
        data.put("periodLabel", periodLabel(period));
        data.put("stockTotalAmount", BigDecimal.ZERO);
        data.put("stockTotalQuantity", BigDecimal.ZERO);
        data.put("periodOutAmount", BigDecimal.ZERO);
        data.put("periodSaleQty", BigDecimal.ZERO);
        data.put("beginStockAmount", BigDecimal.ZERO);
        data.put("avgStockAmount", BigDecimal.ZERO);
        data.put("turnoverRate", BigDecimal.ZERO);
        data.put("turnoverDays", BigDecimal.ZERO);
        data.put("longAgeAmount", BigDecimal.ZERO);
        data.put("longAgeCount", 0);
        data.put("staleAmount", BigDecimal.ZERO);
        data.put("staleCount", 0);
        data.put("shortageSkuCount", 0);
        data.put("avgSaleableDays", BigDecimal.ZERO);
        data.put("typeDist", List.of());
        data.put("lineDist", List.of());
        data.put("productLineRef", List.of());
        data.put("rawVsProduct", List.of());
        data.put("stockVsSaleTrend", List.of());
        data.put("shortageList", List.of());
        data.put("staleList", List.of());
        return data;
    }

    /** 期间销售目标 = 年度目标折算（月=年/12，季=年/4，年=年） */
    private BigDecimal periodTarget(String period) {
        int year = LocalDate.now().getYear();
        List<SalesTargetCompany> targets = salesTargetCompanyMapper.selectList(
                new QueryWrapper<SalesTargetCompany>().eq("target_year", year));
        if (targets.isEmpty()) return BigDecimal.ZERO;
        BigDecimal annual = targets.stream().map(t -> nvl(t.getAnnualAmount()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        switch (period == null ? "month" : period) {
            case "day": return annual.divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
            case "week": return annual.divide(BigDecimal.valueOf(52), 2, RoundingMode.HALF_UP);
            case "quarter": return annual.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
            case "year": return annual;
            default: return annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private static BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> nameValue(String name, BigDecimal value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private static List<Map<String, Object>> toNameValueList(Map<String, BigDecimal> map) {
        return map.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> nameValue(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** 订单聚合结果 */
    private static class OrderAgg {
        BigDecimal revenueAmount = BigDecimal.ZERO; // 含税销售额
        BigDecimal netRevenue = BigDecimal.ZERO;    // 不含税营收（订单头+明细，明细为准）
        BigDecimal grossBaseNet = BigDecimal.ZERO;  // 参与毛利计算的不含税营收（成本可用的明细）
        BigDecimal costAmount = BigDecimal.ZERO;    // 销售成本
        BigDecimal grossProfit = BigDecimal.ZERO;   // 毛利
        Map<String, Object> costStats;              // 成本缺失/异常统计
    }
}
