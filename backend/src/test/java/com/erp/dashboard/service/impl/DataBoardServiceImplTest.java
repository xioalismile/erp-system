package com.erp.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.entity.SalesTargetCompany;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.crm.mapper.SalesTargetCompanyMapper;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.expense.purchase.entity.PurchaseExpense;
import com.erp.finance.expense.purchase.mapper.PurchaseExpenseMapper;
import com.erp.finance.expense.sale.entity.SaleExpense;
import com.erp.finance.expense.sale.mapper.SaleExpenseMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.OperationExpenseMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据看板核心口径回归测试（纯单元测试，Mock 全部 Mapper，不连数据库）。
 *
 * 覆盖的踩坑回归点（见知识库第十六章）：
 *   1. 订单头 net_amount 与明细 net_amount 重复累计 → 净营收翻倍 Bug（已修，防复发）
 *   2. 明细行 material_name 为空 → 热销物料名称显示 null（已修，兜底解析）
 *   3. sale_expense.status 为 integer 数字枚举，字符串口径查询报错（已修，IN (2,3)）
 *
 * 安全与健壮性覆盖：
 *   - period 白名单解析（null/quarter/year/非法值均不抛异常）
 *   - 全空数据除零保护（arTurnoverDays/targetRate/paybackRate/saleExpenseRate 返回 0）
 *   - 应收账龄 5 档分桶边界（1/30/31/60/61/180/181 天）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("数据看板 - 核心口径回归测试")
class DataBoardServiceImplTest {

    @Mock private SaleOrderMapper saleOrderMapper;
    @Mock private SaleOrderItemMapper saleOrderItemMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private MaterialMapper materialMapper;
    @Mock private MaterialCategoryMapper materialCategoryMapper;
    @Mock private AccountsReceivableMapper accountsReceivableMapper;
    @Mock private PaymentReceiptMapper paymentReceiptMapper;
    @Mock private PaymentVoucherMapper paymentVoucherMapper;
    @Mock private SaleExpenseMapper saleExpenseMapper;
    @Mock private PurchaseExpenseMapper purchaseExpenseMapper;
    @Mock private OperationExpenseMapper operationExpenseMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private SalesTargetCompanyMapper salesTargetCompanyMapper;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private InventoryTransactionMapper inventoryTransactionMapper;
    @Mock private MaterialCostService materialCostService;

    @InjectMocks
    private DataBoardServiceImpl service;

    // ==================== 测试数据构造 ====================

    private SaleOrder order(long id, String totalAmount, String netAmount, String taxAmount) {
        SaleOrder o = new SaleOrder();
        o.setOrderId(id);
        o.setOrderNo("SO" + id);
        o.setOrderDate(LocalDate.now());
        o.setStatus("已生效");
        o.setTotalAmount(new BigDecimal(totalAmount));
        o.setNetAmount(new BigDecimal(netAmount));
        o.setTaxAmount(new BigDecimal(taxAmount));
        return o;
    }

    private SaleOrderItem item(long orderId, Long materialId, String materialName,
                               String amount, String netAmount, String quantity, String avgCost) {
        SaleOrderItem it = new SaleOrderItem();
        it.setOrderId(orderId);
        it.setMaterialId(materialId);
        it.setMaterialName(materialName);
        it.setAmount(new BigDecimal(amount));
        it.setNetAmount(new BigDecimal(netAmount));
        it.setQuantity(new BigDecimal(quantity));
        it.setAvgCost(avgCost == null ? null : new BigDecimal(avgCost));
        return it;
    }

    private AccountsReceivable ar(String customerName, LocalDate dueDate,
                                  String totalAmount, String paidAmount, String balanceAmount) {
        AccountsReceivable a = new AccountsReceivable();
        a.setCustomerName(customerName);
        a.setDueDate(dueDate);
        a.setStatus("部分收款");
        a.setTotalAmount(new BigDecimal(totalAmount));
        a.setPaidAmount(new BigDecimal(paidAmount));
        a.setBalanceAmount(new BigDecimal(balanceAmount));
        return a;
    }

    private Material material(long id, String code, String name) {
        Material m = new Material();
        m.setMaterialId(id);
        m.setMaterialCode(code);
        m.setMaterialName(name);
        return m;
    }

    /** 财务看板通用空桩（各测试再按需覆盖具体返回值） */
    private void stubFinance(List<SaleOrder> orders, List<SaleOrderItem> items,
                             List<AccountsReceivable> ars, List<SalesTargetCompany> targets) {
        when(saleOrderMapper.selectList(any())).thenReturn(orders);
        when(saleOrderItemMapper.selectList(any())).thenReturn(items);
        when(accountsReceivableMapper.selectList(any())).thenReturn(ars);
        when(salesTargetCompanyMapper.selectList(any())).thenReturn(targets);
        when(materialMapper.selectList(any())).thenReturn(List.of());
        when(materialCategoryMapper.selectList(any())).thenReturn(List.of());
        when(saleExpenseMapper.selectList(any())).thenReturn(List.of());
        when(purchaseExpenseMapper.selectList(any())).thenReturn(List.of());
        when(operationExpenseMapper.selectList(any())).thenReturn(List.of());
        when(paymentReceiptMapper.selectList(any())).thenReturn(List.of());
        when(paymentVoucherMapper.selectList(any())).thenReturn(List.of());
        when(materialCostService.batchGetStockAvgCost(any())).thenReturn(Map.of());
        when(materialCostService.getStockAvgCost(any())).thenReturn(BigDecimal.ZERO);
    }

    /** 销售看板通用空桩 */
    private void stubSale(List<SaleOrder> orders, List<SaleOrderItem> items,
                          List<Customer> customers, List<Material> materials,
                          List<AccountsReceivable> ars) {
        when(saleOrderMapper.selectList(any())).thenReturn(orders);
        when(saleOrderItemMapper.selectList(any())).thenReturn(items);
        when(customerMapper.selectList(any())).thenReturn(customers);
        when(materialMapper.selectList(any())).thenReturn(materials);
        when(materialCategoryMapper.selectList(any())).thenReturn(List.of());
        when(accountsReceivableMapper.selectList(any())).thenReturn(ars);
        when(opportunityMapper.selectList(any())).thenReturn(List.<Opportunity>of());
        when(saleExpenseMapper.selectList(any())).thenReturn(List.of());
        when(salesTargetCompanyMapper.selectList(any())).thenReturn(List.of());
        when(materialCostService.batchGetStockAvgCost(any())).thenReturn(Map.of());
        when(materialCostService.getStockAvgCost(any())).thenReturn(BigDecimal.ZERO);
    }

    private static void assertValue(Object actual, String expected) {
        assertNotNull(actual, "字段值不应为 null");
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual),
                () -> "期望 " + expected + "，实际 " + actual);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object raw) {
        return (List<Map<String, Object>>) raw;
    }

    /**
     * MyBatis-Plus 的 SQL 片段为惰性求值：in/eq 只是注册 lambda，
     * formatParam 把参数写入 paramNameValuePairs 发生在 getSqlSegment() 调用时。
     * 因此校验 QueryWrapper 参数前必须先触发 getSqlSegment()。
     */
    private static <T> boolean wrapperHasParams(QueryWrapper<T> w, Object... expected) {
        w.getSqlSegment();
        Map<String, Object> params = w.getParamNameValuePairs();
        for (Object e : expected) {
            if (!params.containsValue(e)) return false;
        }
        return true;
    }

    // ==================== 财务看板 ====================

    @Nested
    @DisplayName("订单聚合口径")
    class OrderAggregation {

        @Test
        @DisplayName("有明细时净营收只取明细金额，防止头/明细重复累计（回归：净营收翻倍Bug）")
        void netRevenueTakesItemsOnlyWhenPresent() {
            // 订单头 total=1000 net=1000；明细 net=800 → 净营收应为 800 而非 1800
            SaleOrder o = order(1L, "1000", "1000", "13");
            SaleOrderItem it = item(1L, 10L, "测试物料", "1000", "800", "10", "2");
            stubFinance(List.of(o), List.of(it), List.of(), List.of());

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("periodRevenue"), "1000");   // 含税口径取订单头
            assertValue(data.get("periodNetRevenue"), "800"); // 不含税只取明细
            assertValue(data.get("periodCost"), "20");        // 2 × 10
            assertValue(data.get("grossProfit"), "780");      // 800 - 20
            assertValue(data.get("netProfit"), "767");        // 780 - 0费用 - 13税金
        }

        @Test
        @DisplayName("无明细订单兜底取订单头净金额")
        void netRevenueFallsBackToHeaderWhenNoItems() {
            SaleOrder o = order(1L, "500", "500", "0");
            stubFinance(List.of(o), List.of(), List.of(), List.of());

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("periodNetRevenue"), "500");
            assertValue(data.get("grossProfit"), "500");
        }

        @Test
        @DisplayName("成本取值优先级：明细avgCost优先，其次档案库存均价，最后0")
        void costPriorityAvgCostFirstThenStockAvg() {
            SaleOrder o = order(1L, "100", "100", "0");
            // item1: avgCost空 → 取档案均价5×4=20；item2: avgCost=3 → 3×2=6
            SaleOrderItem i1 = item(1L, 1L, "物料A", "60", "60", "4", null);
            SaleOrderItem i2 = item(1L, 2L, "物料B", "40", "40", "2", "3");
            stubFinance(List.of(o), List.of(i1, i2), List.of(), List.of());
            when(materialCostService.batchGetStockAvgCost(any()))
                    .thenReturn(Map.of(1L, new BigDecimal("5")));
            when(materialCostService.getStockAvgCost(any()))
                    .thenReturn(new BigDecimal("5"));

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("periodCost"), "26");
            assertValue(data.get("grossProfit"), "74");
        }
    }

    @Nested
    @DisplayName("费用口径与状态枚举")
    class ExpenseScope {

        @Test
        @DisplayName("销售/采购费用单状态为数字枚举IN(2,3)，经营费用为已生效（回归：integer=varchar类型不匹配Bug）")
        @SuppressWarnings("unchecked")
        void expenseStatusUsesNumericEnum() {
            SaleOrder o = order(1L, "100", "100", "0");
            stubFinance(List.of(o), List.of(), List.of(), List.of());

            SaleExpense se = new SaleExpense();
            se.setTotalAmount(new BigDecimal("100"));
            when(saleExpenseMapper.selectList(any())).thenReturn(List.of(se));
            PurchaseExpense pe = new PurchaseExpense();
            pe.setTotalAmount(new BigDecimal("50"));
            when(purchaseExpenseMapper.selectList(any())).thenReturn(List.of(pe));
            OperationExpense oe = new OperationExpense();
            oe.setAmount(new BigDecimal("30"));
            when(operationExpenseMapper.selectList(any())).thenReturn(List.of(oe));

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("expenseSale"), "100");
            assertValue(data.get("expensePurchase"), "50");
            assertValue(data.get("expenseManage"), "30");
            assertValue(data.get("expenseTotal"), "180");
            // 净利 = 毛利100 - 费用180 - 税金0 = -80
            assertValue(data.get("netProfit"), "-80");

            ArgumentCaptor<QueryWrapper<SaleExpense>> saleCap =
                    ArgumentCaptor.forClass((Class) QueryWrapper.class);
            verify(saleExpenseMapper, atLeastOnce()).selectList(saleCap.capture());
            assertTrue(saleCap.getAllValues().stream().anyMatch(w -> wrapperHasParams(w, 2, 3)),
                    "销售费用单状态必须为数字枚举 IN (2,3)，不允许字符串口径");

            ArgumentCaptor<QueryWrapper<PurchaseExpense>> purCap =
                    ArgumentCaptor.forClass((Class) QueryWrapper.class);
            verify(purchaseExpenseMapper, atLeastOnce()).selectList(purCap.capture());
            assertTrue(purCap.getAllValues().stream().anyMatch(w -> wrapperHasParams(w, 2, 3)),
                    "采购费用单状态必须为数字枚举 IN (2,3)");

            ArgumentCaptor<QueryWrapper<OperationExpense>> opCap =
                    ArgumentCaptor.forClass((Class) QueryWrapper.class);
            verify(operationExpenseMapper, atLeastOnce()).selectList(opCap.capture());
            assertTrue(opCap.getAllValues().stream().anyMatch(w -> wrapperHasParams(w, "已生效")),
                    "经营费用单状态为字符串枚举'已生效'");
        }
    }

    @Nested
    @DisplayName("应收账龄与风险")
    class ArAging {

        @Test
        @DisplayName("账龄五档分桶边界（1/30/31/60/61/180/181天）与逾期/风险金额")
        void agingBucketBoundaries() {
            LocalDate today = LocalDate.now();
            List<AccountsReceivable> ars = List.of(
                    ar("客户未到期", today.plusDays(10), "100", "0", "100"),   // 未到期
                    ar("客户A", today.minusDays(1), "200", "0", "200"),        // over=1 → 1-30
                    ar("客户B", today.minusDays(30), "20", "0", "20"),         // over=30 → 1-30（边界含）
                    ar("客户C", today.minusDays(31), "300", "0", "300"),       // over=31 → 31-60
                    ar("客户D", today.minusDays(60), "30", "0", "30"),         // over=60 → 31-60（边界含）
                    ar("客户E", today.minusDays(61), "400", "0", "400"),       // over=61 → 61-180
                    ar("客户F", today.minusDays(180), "40", "0", "40"),        // over=180 → 61-180（边界含）
                    ar("客户G", today.minusDays(181), "500", "0", "500")       // over=181 → 180+
            );
            stubFinance(List.of(), List.of(), ars, List.of());

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("arBalance"), "1590");
            assertValue(data.get("arOverdue"), "1490");
            assertValue(data.get("riskAmount"), "500"); // 仅 over>180 计风险

            List<Map<String, Object>> aging = list(data.get("arAging"));
            assertEquals(5, aging.size());
            assertValue(aging.get(0).get("value"), "100");  // 未到期
            assertValue(aging.get(1).get("value"), "220");  // 1-30：200+20
            assertValue(aging.get(2).get("value"), "330");  // 31-60：300+30
            assertValue(aging.get(3).get("value"), "440");  // 61-180：400+40
            assertValue(aging.get(4).get("value"), "500");  // 180+
        }

        @Test
        @DisplayName("到期日为空的应收：计入余额但不参与逾期与账龄分桶")
        void nullDueDateSkippedInOverdue() {
            AccountsReceivable a = ar("客户H", null, "777", "0", "777");
            stubFinance(List.of(), List.of(), List.of(a), List.of());

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("arBalance"), "777");
            assertValue(data.get("arOverdue"), "0");
            assertValue(data.get("riskAmount"), "0");
            list(data.get("arAging")).forEach(bucket -> assertValue(bucket.get("value"), "0"));
        }
    }

    @Nested
    @DisplayName("目标折算与预警")
    class Target {

        @Test
        @DisplayName("年度目标折算：月=年/12，季=年/4，年=年；达成率与预警开关正确")
        void annualTargetProration() {
            SalesTargetCompany t = new SalesTargetCompany();
            t.setTargetYear(LocalDate.now().getYear());
            t.setAnnualAmount(new BigDecimal("500000"));
            SaleOrder o = order(1L, "2000", "2000", "0");
            stubFinance(List.of(o), List.of(), List.of(), List.of(t));

            Map<String, Object> m = service.getFinanceBoard("month", null);
            assertValue(m.get("targetAmount"), "41666.67");
            assertValue(m.get("targetRate"), "4.8");
            assertEquals(Boolean.TRUE, m.get("targetWarn"), "达成率4.8%<85%应预警");

            Map<String, Object> q = service.getFinanceBoard("quarter", null);
            assertValue(q.get("targetAmount"), "125000");

            Map<String, Object> y = service.getFinanceBoard("year", null);
            assertValue(y.get("targetAmount"), "500000");
            assertValue(y.get("targetRate"), "0.4");
        }

        @Test
        @DisplayName("无年度目标时：目标为0、达成率0、不触发预警")
        void noTargetNoWarn() {
            SaleOrder o = order(1L, "2000", "2000", "0");
            stubFinance(List.of(o), List.of(), List.of(), List.of());

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("targetAmount"), "0");
            assertValue(data.get("targetRate"), "0");
            assertEquals(Boolean.FALSE, data.get("targetWarn"), "无目标不应预警");
        }
    }

    @Nested
    @DisplayName("期间解析与除零保护")
    class PeriodAndZeroGuard {

        @Test
        @DisplayName("period白名单：null/month/quarter/year/非法值均不抛异常且标签正确")
        void periodWhitelist() {
            stubFinance(List.of(), List.of(), List.of(), List.of());

            assertEquals("本月", service.getFinanceBoard(null, null).get("periodLabel"));
            assertEquals("本月", service.getFinanceBoard("month", null).get("periodLabel"));
            assertEquals("本季度", service.getFinanceBoard("quarter", null).get("periodLabel"));
            assertEquals("本年度", service.getFinanceBoard("year", null).get("periodLabel"));
            assertEquals("本月", service.getFinanceBoard("任意非法值", null).get("periodLabel"));
        }

        @Test
        @DisplayName("全空数据除零保护：周转天数/达成率/费用率均为0，无ArithmeticException")
        void zeroDivisionGuard() {
            stubFinance(List.of(), List.of(), List.of(), List.of());

            Map<String, Object> data = service.getFinanceBoard("month", null);

            assertValue(data.get("periodRevenue"), "0");
            assertValue(data.get("arTurnoverDays"), "0");
            assertValue(data.get("targetRate"), "0");
            assertValue(data.get("saleExpenseRate"), "0");
            assertValue(data.get("cashFlowNet"), "0");
            assertValue(data.get("grossProfit"), "0");
            assertValue(data.get("netProfit"), "0");
        }
    }

    // ==================== 销售看板 ====================

    @Nested
    @DisplayName("物料名称与排行")
    class MaterialNameAndRanking {

        @Test
        @DisplayName("物料名称四级兜底：明细名称 > 档案名称 > 档案编码 > 未知物料（回归：名称null Bug）")
        void materialNameFallbackChain() {
            SaleOrder o = order(1L, "1100", "1100", "0");
            List<SaleOrderItem> items = List.of(
                    item(1L, 1L, "明细名称", "500", "500", "5", "1"),   // 明细名称优先
                    item(1L, 2L, null, "300", "300", "3", "1"),          // 行空 → 档案名称
                    item(1L, 3L, "  ", "200", "200", "2", "1"),          // 行空白 → 档案无名称 → 编码
                    item(1L, 4L, null, "100", "100", "1", "1")           // 档案也无 → 未知物料
            );
            List<Material> materials = List.of(
                    material(1L, "C1", "档案名称一"),
                    material(2L, "C2", "档案名称二"),
                    material(3L, "COD3", null)                            // 有编码无名称
            );
            stubSale(List.of(o), items, List.of(), materials, List.of());

            Map<String, Object> data = service.getSaleBoard("month", null);

            List<Map<String, Object>> top = list(data.get("topMaterials"));
            assertEquals(4, top.size());
            assertEquals("明细名称", top.get(0).get("materialName"));
            assertEquals("档案名称二", top.get(1).get("materialName"));
            assertEquals("COD3", top.get(2).get("materialName"));
            assertEquals("未知物料", top.get(3).get("materialName"));
        }

        @Test
        @DisplayName("TOP物料降序、BOTTOM物料升序")
        void topDescBottomAsc() {
            SaleOrder o = order(1L, "600", "600", "0");
            List<SaleOrderItem> items = List.of(
                    item(1L, 1L, "物料低", "100", "100", "1", "1"),
                    item(1L, 2L, "物料高", "300", "300", "3", "1"),
                    item(1L, 3L, "物料中", "200", "200", "2", "1")
            );
            stubSale(List.of(o), items, List.of(), List.of(), List.of());

            Map<String, Object> data = service.getSaleBoard("month", null);

            List<Map<String, Object>> top = list(data.get("topMaterials"));
            assertEquals("物料高", top.get(0).get("materialName"));
            assertEquals("物料中", top.get(1).get("materialName"));
            assertEquals("物料低", top.get(2).get("materialName"));

            List<Map<String, Object>> bottom = list(data.get("bottomMaterials"));
            assertEquals("物料低", bottom.get(0).get("materialName"));
            assertEquals("物料高", bottom.get(2).get("materialName"));
        }
    }

    @Nested
    @DisplayName("客户聚合")
    class CustomerAggregation {

        @Test
        @DisplayName("新老客户按建档日期划分；客户等级为空归'未分级'；平均订单额=总额/单数")
        void newOldCustomerSplit() {
            Customer newC = new Customer();
            newC.setCustomerId(1L);
            newC.setCustomerLevel("A");
            newC.setCreatedAt(LocalDateTime.now());                       // 本月建档 → 新客户
            Customer oldC = new Customer();
            oldC.setCustomerId(2L);
            oldC.setCreatedAt(LocalDateTime.now().minusYears(1));         // 去年建档 → 老客户
            SaleOrder o1 = order(1L, "300", "300", "0");
            o1.setCustomerId(1L);
            SaleOrder o2 = order(2L, "700", "700", "0");
            o2.setCustomerId(2L);
            stubSale(List.of(o1, o2), List.of(), List.of(newC, oldC), List.of(), List.of());

            Map<String, Object> data = service.getSaleBoard("month", null);

            assertEquals(2, data.get("orderCount"));
            assertValue(data.get("avgOrderAmount"), "500");

            List<Map<String, Object>> newOld = list(data.get("newOldCustomer"));
            assertEquals(2, newOld.size());
            for (Map<String, Object> e : newOld) {
                if ("新客户".equals(e.get("name"))) {
                    assertValue(e.get("amount"), "300");
                    assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) e.get("orderCount")));
                } else if ("老客户".equals(e.get("name"))) {
                    assertValue(e.get("amount"), "700");
                    assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) e.get("orderCount")));
                } else {
                    throw new AssertionError("意外的新老客户分组：" + e.get("name"));
                }
            }

            List<Map<String, Object>> levelDist = list(data.get("customerLevelDist"));
            assertEquals(2, levelDist.size());
            assertEquals("A", levelDist.stream()
                    .anyMatch(e -> "A".equals(e.get("name"))) ? "A" : null);
            assertTrue(levelDist.stream().anyMatch(e -> "未分级".equals(e.get("name"))),
                    "等级为空的客户应归入'未分级'");
        }

        @Test
        @DisplayName("同客户多张应收：逾期金额累加、取最大逾期天数；回款率=已收/应收")
        void overdueAggregationByCustomer() {
            LocalDate today = LocalDate.now();
            List<AccountsReceivable> ars = List.of(
                    ar("客户X", today.minusDays(3), "600", "150", "100"),
                    ar("客户X", today.minusDays(10), "400", "100", "50"),
                    ar("客户Y", today.plusDays(5), "1000", "250", "999")   // 未逾期不计
            );
            stubSale(List.of(), List.of(), List.of(), List.of(), ars);

            Map<String, Object> data = service.getSaleBoard("month", null);

            assertValue(data.get("paybackRate"), "25"); // (150+100+250)/(600+400+1000)
            assertValue(data.get("arOverdue"), "150");  // 100+50

            List<Map<String, Object>> overdue = list(data.get("overdueByCustomer"));
            assertEquals(1, overdue.size(), "未逾期的客户Y不应出现");
            assertEquals("客户X", overdue.get(0).get("customerName"));
            assertValue(overdue.get(0).get("overdueAmount"), "150");
            assertValue(overdue.get(0).get("overdueDays"), "10");
        }

        @Test
        @DisplayName("销售看板空数据除零保护：回款率/费用率/平均订单额均为0")
        void saleBoardZeroGuard() {
            stubSale(List.of(), List.of(), List.of(), List.of(), List.of());

            Map<String, Object> data = service.getSaleBoard("month", null);

            assertEquals(0, data.get("orderCount"));
            assertValue(data.get("avgOrderAmount"), "0");
            assertValue(data.get("paybackRate"), "0");
            assertValue(data.get("saleExpenseRate"), "0");
            assertValue(data.get("targetRate"), "0");
            assertEquals(Boolean.FALSE, data.get("targetWarn"));
        }
    }
}
