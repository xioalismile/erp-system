package com.erp.ai.service;

import com.erp.ai.dto.EntityCrudConfig;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.sale.entity.SaleOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 查询自动注册机制断言（2026-11-22 立项一期）。
 * 覆盖：展示列扩充（交货日期自动可见）、筛选白名单自动扩充（列表筛选登记 → AI 可筛）、
 * 默认筛选兜底、字段中文名映射、派生列（超期天数/未收款金额）计算。
 */
class AiQuerySchemaEnhancerTest {

    private EntityCrudConfig saleOrderConfig(List<String> display, List<String> filters) {
        return EntityCrudConfig.builder()
                .intentPrefix("SALE_ORDER")
                .entityClass(SaleOrder.class)
                .primaryKeyField("orderId")
                .nameField("orderNo")
                .codeField("orderNo")
                .searchFields(List.of("orderNo"))
                .displayFields(display)
                .filterFields(filters)
                .isMasterData(false)
                .build();
    }

    @Test
    void manualDisplayKeptAndDeliveryDateAutoAddedWithLabels() {
        EntityCrudConfig cfg = saleOrderConfig(
                List.of("orderNo", "customerName", "totalAmount", "status", "orderDate"),
                List.of("status", "orderNo", "customerId", "orderDate", "totalAmount", "closeStatus"));
        Map<String, EntityCrudConfig> configs = new LinkedHashMap<>();
        configs.put("QUERY_SALE_ORDER", cfg);

        AiQuerySchemaEnhancer.Summary s = AiQuerySchemaEnhancer.enhanceAll(configs);

        // 手工列永远在前、不改名不重排
        List<String> display = cfg.getDisplayFields();
        assertEquals(List.of("orderNo", "customerName", "totalAmount", "status", "orderDate"),
                display.subList(0, 5), "手工展示列必须原样保留在最前");
        // 交货日期（实体注解 order=18）自动进入展示列
        assertTrue(display.contains("deliveryDate"), "交货日期应自动进入 AI 展示列");
        // 派生列紧跟手工列之后
        assertTrue(display.indexOf("overdueDays") >= 0, "派生列「超期天数」应进入展示列");
        assertTrue(display.indexOf("overdueDays") > display.indexOf("orderDate"), "派生列在手工列之后");
        // 交货日期自动进入筛选白名单（实体注解 LocalDate）
        assertTrue(cfg.getFilterFields().contains("deliveryDate"), "交货日期应自动进入 AI 筛选白名单");
        // 中文名映射：注解标签 + 派生列名 + 补全键
        assertEquals("交货日期", cfg.getLabels().get("deliveryDate"));
        assertEquals("超期天数", cfg.getLabels().get("overdueDays"));
        assertEquals("客户名称", cfg.getLabels().get("customerName"));
        // 主档 ID 不进展示列
        assertFalse(display.contains("customerId"));
        // 汇总：本次展示列与筛选列都有扩充
        assertTrue(s.displayExtended() > 0);
        assertTrue(s.filterExtended() > 0);
    }

    @Test
    void filterAutoAddedFromListFilterRegistration() {
        // 模拟列表页筛选登记（销售订单服务在启动期就是这么登记的）
        BillHeaderFilterSupport.registerProps("saleOrder", SaleOrder.class, Map.of(
                "deliveryDate", new BillHeaderFilterSupport.Spec("delivery_date", BillHeaderFilterSupport.Kind.DATE),
                "customerName", new BillHeaderFilterSupport.Spec("customer_id", BillHeaderFilterSupport.Kind.EQ_LONG)));
        // 手工白名单只登记了状态——交货日期应由登记处自动带出
        EntityCrudConfig cfg = saleOrderConfig(
                List.of("orderNo", "status"), List.of("status", "orderNo"));
        Map<String, EntityCrudConfig> configs = new LinkedHashMap<>();
        configs.put("QUERY_SALE_ORDER2", cfg);
        AiQuerySchemaEnhancer.enhanceAll(configs);

        assertTrue(cfg.getFilterFields().contains("deliveryDate"),
                "列表筛选登记的日期条件应自动进入 AI 筛选白名单");
        // "customerName" 在登记处映射到客户ID列——但实体属性 customerName 不是 Long，
        // 类型不匹配必须被剔除，杜绝"名字像、类型对不上"的假筛选
        assertFalse(cfg.getFilterFields().contains("customerName"),
                "键名与实体属性类型不匹配的登记项不得进入筛选白名单");
    }

    @Test
    void defaultFiltersWhenNothingRegistered() {
        // 未登记任何筛选且无手工白名单 → 默认筛选集兜底（新单据"至少能查"）。
        // 用打样单实体：单测 JVM 里没有任何列表筛选登记，且不受其他用例的静态登记污染
        EntityCrudConfig cfg = EntityCrudConfig.builder()
                .intentPrefix("SAMPLE_ORDER")
                .entityClass(com.erp.sample.entity.SampleOrder.class)
                .primaryKeyField("sampleId")
                .nameField("sampleNo")
                .codeField("sampleNo")
                .searchFields(List.of("sampleNo"))
                .displayFields(List.of("sampleNo", "status"))
                .filterFields(null)
                .isMasterData(false)
                .build();
        Map<String, EntityCrudConfig> configs = new LinkedHashMap<>();
        configs.put("QUERY_SAMPLE_ORDER_DEFAULTTEST", cfg);
        AiQuerySchemaEnhancer.Summary s = AiQuerySchemaEnhancer.enhanceAll(configs);

        assertTrue(!cfg.getFilterFields().isEmpty(), "空白名单必须给默认筛选集");
        assertTrue(cfg.getFilterFields().contains("status"), "默认筛选集应含单据状态");
        assertEquals(1, s.defaultFilterOnly());
    }

    @Test
    void derivedColumnsComputeOverdueAndUnreceived() {
        SaleOrder overdue = new SaleOrder();
        overdue.setOrderId(1L);
        overdue.setDeliveryDate(LocalDate.now().minusDays(3));
        overdue.setNetAmount(new BigDecimal("100.00"));
        overdue.setReceivedAmount(new BigDecimal("40.00"));

        SaleOrder notOverdue = new SaleOrder();
        notOverdue.setOrderId(2L);
        notOverdue.setDeliveryDate(LocalDate.now().plusDays(5));
        notOverdue.setNetAmount(new BigDecimal("50.00"));
        notOverdue.setReceivedAmount(null);

        SaleOrder undated = new SaleOrder();
        undated.setOrderId(3L);
        undated.setDeliveryDate(null);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(new LinkedHashMap<>());
        }
        AiDerivedColumns.apply("QUERY_SALE_ORDER",
                List.of(overdue, notOverdue, undated), rows);

        assertEquals(3, rows.get(0).get("overdueDays"), "交期已过 3 天应算出超期天数=3");
        assertEquals(new BigDecimal("60.00"), rows.get(0).get("unreceivedAmount"), "未收款=100−40");
        assertEquals("", rows.get(1).get("overdueDays"), "交期未到不显示超期天数");
        assertEquals(new BigDecimal("50.00"), rows.get(1).get("unreceivedAmount"), "未收款按 0 处理");
        assertEquals("", rows.get(2).get("overdueDays"), "未填交货日期不算超期");
    }
}
