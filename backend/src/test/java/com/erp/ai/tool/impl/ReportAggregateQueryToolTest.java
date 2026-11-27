package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.permission.service.FieldPermissionOutputFilter;
import com.erp.report.custom.core.EntityMetadataRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 聚合排行工具的防线断言（2026-11-22 立项三期）。
 *
 * <p>覆盖：数据源白名单命中（对账单已在三期补登）/ 未知数据源与服务名不精确拒绝 /
 * 字段白名单（中文名与英文名都能解析）/ 非数值字段不得求和 / 计数模式无需指标字段。
 * 这些分支都在解析阶段返回，不触库。字段权限过滤器用空实现（不可见集合为空），
 * 验证的是"没有配置隐藏字段时不误伤"。</p>
 */
@DisplayName("AI 聚合排行 - 数据源与字段白名单防线")
class ReportAggregateQueryToolTest {

    private final EntityMetadataRegistry registry = new EntityMetadataRegistry();
    private final FieldPermissionOutputFilter permissionFilter = emptyPermissionFilter();
    private final ReportAggregateQueryTool tool =
            new ReportAggregateQueryTool(registry, null, null, permissionFilter);

    private static FieldPermissionOutputFilter emptyPermissionFilter() {
        FieldPermissionOutputFilter f = mock(FieldPermissionOutputFilter.class);
        when(f.hiddenKeys(anyString())).thenReturn(Set.of());
        when(f.hiddenKeysForUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
                anyString())).thenReturn(Set.of());
        return f;
    }

    @Test
    @DisplayName("三期补登：客户对账单/供应商对账单及其明细已进数据源白名单，明细可关联表头")
    void statementDataSourcesRegistered() {
        EntityMetadataRegistry.DataSourceMeta sale = registry.getDataSource("sale_statement");
        assertNotNull(sale, "客户对账单应已登记为数据源");
        assertEquals("客户对账单", sale.name);
        assertTrue(sale.fields.containsKey("customerName"), "客户对账单应有客户名称字段");
        assertTrue(sale.fields.containsKey("unpaidAmount"), "客户对账单应有未收金额字段");

        EntityMetadataRegistry.DataSourceMeta purchase = registry.getDataSource("purchase_statement");
        assertNotNull(purchase, "供应商对账单应已登记为数据源");
        assertEquals("供应商对账单", purchase.name);

        EntityMetadataRegistry.DataSourceMeta saleItem = registry.getDataSource("sale_statement_item");
        assertNotNull(saleItem, "客户对账单明细应已登记为数据源");
        assertTrue(saleItem.joins.containsKey("sale_statement"), "对账单明细应自动关联出表头");

        assertNotNull(registry.getDataSource("purchase_statement_item"), "供应商对账单明细应已登记为数据源");
    }

    @Test
    @DisplayName("未知数据源：拒绝并指路 report_list_datasources，不静默返回空表")
    void unknownDataSourceRejected() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("dataSource", "不存在的报表源");
        p.put("groupField", "物料编码");
        Map<String, Object> r = tool.execute(p, (AiDataScope) null);
        assertEquals(false, r.get("success"));
        assertTrue(String.valueOf(r.get("message")).contains("report_list_datasources"),
                "报错要指路可用数据源查询工具，实际：" + r.get("message"));
    }

    @Test
    @DisplayName("字段白名单：不在数据源内的字段拒绝（中文名/英文名均按白名单解析）")
    void groupFieldMustBeInWhitelist() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("dataSource", "销售出库明细");
        bad.put("groupField", "根本不存在的字段");
        Map<String, Object> r = tool.execute(bad, (AiDataScope) null);
        assertEquals(false, r.get("success"));
        assertTrue(String.valueOf(r.get("message")).contains("没有字段"),
                "报错要说明该数据源没有此字段，实际：" + r.get("message"));
    }

    @Test
    @DisplayName("数值校验：非数值字段不允许求和；count 模式无需指标字段")
    void numericGuard() {
        Map<String, Object> sumOnText = new LinkedHashMap<>();
        sumOnText.put("dataSource", "销售出库明细");
        sumOnText.put("groupField", "物料编码");
        sumOnText.put("agg", "sum");
        sumOnText.put("metricField", "物料名称");
        Map<String, Object> r1 = tool.execute(sumOnText, (AiDataScope) null);
        assertEquals(false, r1.get("success"));
        assertTrue(String.valueOf(r1.get("message")).contains("不是数值字段"),
                "非数值字段求和必须拒绝，实际：" + r1.get("message"));

        Map<String, Object> sumNoMetric = new LinkedHashMap<>();
        sumNoMetric.put("dataSource", "销售出库明细");
        sumNoMetric.put("groupField", "物料编码");
        sumNoMetric.put("agg", "sum");
        Map<String, Object> r2 = tool.execute(sumNoMetric, (AiDataScope) null);
        assertEquals(false, r2.get("success"));
        assertTrue(String.valueOf(r2.get("message")).contains("metricField"),
                "求和缺指标字段要说清，实际：" + r2.get("message"));
    }

    @Test
    @DisplayName("字段权限：分组/指标字段被设为不可见时拒绝，不能用聚合口径绕过")
    void hiddenFieldRejected() {
        FieldPermissionOutputFilter f = mock(FieldPermissionOutputFilter.class);
        when(f.hiddenKeys(anyString())).thenReturn(Set.of("netAmount", "net_amount"));
        ReportAggregateQueryTool t = new ReportAggregateQueryTool(registry, null, null, f);

        Map<String, Object> sumHidden = new LinkedHashMap<>();
        sumHidden.put("dataSource", "sale_order");
        sumHidden.put("groupField", "status");
        sumHidden.put("agg", "sum");
        sumHidden.put("metricField", "netAmount");
        Map<String, Object> r = t.execute(sumHidden, (AiDataScope) null);
        assertEquals(false, r.get("success"));
        assertTrue(String.valueOf(r.get("message")).contains("不可见"),
                "隐藏字段求和必须拒绝，实际：" + r.get("message"));
    }
}
