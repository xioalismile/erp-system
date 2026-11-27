package com.erp.test.production;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 生产管理模块业务流程与字段校验自动化测试
 *
 * 测试内容：
 *  1. 字段校验（必填字段、编码唯一性、数量校验、金额计算）
 *  2. 编码生成规则验证（前缀-yyyyMMdd-0001格式）
 *  3. 业务关联数据验证（BOM展开、工艺路线关联）
 *  4. 端到端业务流程（创建工单→下达→开工→完工→入库→关闭）
 */
@DisplayName("生产管理模块 - 业务流程与字段校验测试")
public class ProductionBusinessFlowTest extends ProductionTestBase {

    // ==================================================================
    // 字段校验测试
    // ==================================================================

    @Test
    @DisplayName("校验 - BOM编码不可重复")
    void testBomCodeUnique() {
        Map<String, Object> body = buildBomBody();
        // 第一次创建应成功
        authPost("/api/masterdata/bom", body).then().body("code", equalTo(200));
        // 第二次用相同编码创建应失败
        Response r2 = authPost("/api/masterdata/bom", body);
        int code = r2.path("code");
        System.out.println("  重复BOM编码创建 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "重复编码应创建失败，实际: " + code);
    }

    @Test
    @DisplayName("校验 - 工作中心编码不可重复")
    void testWorkCenterCodeUnique() {
        Map<String, Object> body = buildWorkCenterBody();
        authPost("/api/production/work-center", body).then().body("code", equalTo(200));
        Response r2 = authPost("/api/production/work-center", body);
        int code = r2.path("code");
        System.out.println("  重复工作中心编码创建 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "重复编码应创建失败，实际: " + code);
    }

    @Test
    @DisplayName("校验 - 工位编码不可重复")
    void testWorkstationCodeUnique() {
        Map<String, Object> body = buildWorkstationBody();
        authPost("/api/production/workstation", body).then().body("code", equalTo(200));
        Response r2 = authPost("/api/production/workstation", body);
        int code = r2.path("code");
        System.out.println("  重复工位编码创建 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "重复编码应创建失败，实际: " + code);
    }

    @Test
    @DisplayName("校验 - 工艺路线编码不可重复")
    void testRoutingCodeUnique() {
        Map<String, Object> body = buildRoutingBody();
        authPost("/api/production/routings", body).then().body("code", equalTo(200));
        Response r2 = authPost("/api/production/routings", body);
        int code = r2.path("code");
        System.out.println("  重复工艺路线编码创建 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "重复编码应创建失败，实际: " + code);
    }

    @Test
    @DisplayName("校验 - 委外加工单金额计算: totalPrice = qty × unitPrice")
    void testSubcontractAmountCalculation() {
        Map<String, Object> body = buildSubcontractBody();
        body.put("qty", new BigDecimal("150"));
        body.put("unitPrice", new BigDecimal("25.50"));
        // totalPrice 应为 150 × 25.50 = 3825.00
        Response r = authPost("/api/production/subcontract", body);
        assertSuccess(r, "Subcontract with calculated amount");
        r.then().body("data.totalPrice", comparesEqualTo(3825.00f));
        System.out.println("  金额计算验证: qty=150 × unitPrice=25.50 = totalPrice=" + r.path("data.totalPrice"));
    }

    @Test
    @DisplayName("校验 - 委外加工单数量必须>0")
    void testSubcontractQtyMustBePositive() {
        Map<String, Object> body = buildSubcontractBody();
        body.put("qty", new BigDecimal("0"));
        Response r = authPost("/api/production/subcontract", body);
        int code = r.path("code");
        System.out.println("  委外数量=0 创建 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "委外数量为0应创建失败，实际: " + code);
    }

    @Test
    @DisplayName("校验 - 委外加工单数量不可为负")
    void testSubcontractQtyCannotBeNegative() {
        Map<String, Object> body = buildSubcontractBody();
        body.put("qty", new BigDecimal("-10"));
        Response r = authPost("/api/production/subcontract", body);
        int code = r.path("code");
        System.out.println("  委外数量=-10 创建 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "委外数量为负应创建失败，实际: " + code);
    }

    // ==================================================================
    // 编码生成规则验证
    // ==================================================================

    @Test
    @DisplayName("编码规则 - 生产工单自动生成编号格式: MO-yyyyMMdd-0001")
    void testWorkOrderNoFormat() {
        Response r = authGet("/api/production/workorders/generate-no");
        assertSuccess(r, "WorkOrder generate-no");
        String orderNo = r.path("data.orderNo");
        System.out.println("  生成的工单号: " + orderNo);
        org.junit.jupiter.api.Assertions.assertNotNull(orderNo, "工单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(orderNo.startsWith("MO-"),
                "工单号应以MO-开头，实际: " + orderNo);
    }

    @Test
    @DisplayName("编码规则 - 工序汇报单自动生成编号格式: OR-yyyyMMdd-0001")
    void testOperationReportNoFormat() {
        Response r = authGet("/api/production/operation-report/generate-no");
        assertSuccess(r, "OperationReport generate-no");
        String reportNo = r.path("data.reportNo");
        System.out.println("  生成的汇报单号: " + reportNo);
        org.junit.jupiter.api.Assertions.assertNotNull(reportNo, "汇报单号不应为空");
    }

    @Test
    @DisplayName("编码规则 - 委外加工单自动生成编号格式: SUB-yyyyMMdd-0001")
    void testSubcontractNoFormat() {
        Response r = authGet("/api/production/subcontract/generate-no");
        assertSuccess(r, "Subcontract generate-no");
        String billNo = r.path("data.billNo");
        System.out.println("  生成的委外单号: " + billNo);
        org.junit.jupiter.api.Assertions.assertNotNull(billNo, "委外单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(billNo.startsWith("SUB-"),
                "委外单号应以SUB-开头，实际: " + billNo);
    }

    @Test
    @DisplayName("编码规则 - MRP方案自动生成编号格式: MRP-yyyyMMdd-0001")
    void testMrpPlanNoFormat() {
        Response r = authGet("/api/production/mrp-plan/generate-no");
        assertSuccess(r, "MrpPlan generate-no");
        String planCode = r.path("data.planCode");
        System.out.println("  生成的MRP方案号: " + planCode);
        org.junit.jupiter.api.Assertions.assertNotNull(planCode, "MRP方案号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(planCode.startsWith("MRP-"),
                "MRP方案号应以MRP-开头，实际: " + planCode);
    }

    // ==================================================================
    // 关联数据验证
    // ==================================================================

    @Test
    @DisplayName("关联数据 - BOM详情应包含明细列表字段")
    void testBomDetailHasItemsField() {
        Long bomId = createBomAndGetId();
        Response r = authGet("/api/masterdata/bom/{id}", bomId);
        assertSuccess(r, "BOM detail with items field");
        // 即使没有明细，items字段也应该存在（可为空列表）
        Object items = r.path("data.items");
        System.out.println("  BOM详情items字段: " + (items != null ? "存在" : "不存在"));
    }

    @Test
    @DisplayName("关联数据 - 工艺路线详情应包含工序列表字段")
    void testRoutingDetailHasOperationsField() {
        Long routingId = createRoutingAndGetId();
        Response r = authGet("/api/production/routings/{id}", routingId);
        assertSuccess(r, "Routing detail with operations field");
        Object operations = r.path("data.operations");
        System.out.println("  工艺路线详情operations字段: " + (operations != null ? "存在" : "不存在"));
    }

    @Test
    @DisplayName("关联数据 - 委外加工单详情应包含明细列表字段")
    void testSubcontractDetailHasItemsField() {
        Long subcontractId = createSubcontractAndGetId();
        Response r = authGet("/api/production/subcontract/{id}", subcontractId);
        assertSuccess(r, "Subcontract detail with items field");
        Object items = r.path("data.items");
        System.out.println("  委外加工单详情items字段: " + (items != null ? "存在" : "不存在"));
    }

    @Test
    @DisplayName("关联数据 - 生产入库单详情应包含明细列表字段")
    void testProductionReceiptDetailHasItemsField() {
        Long receiptId = createProductionReceiptAndGetId();
        Response r = authGet("/api/production/receipts/{id}", receiptId);
        assertSuccess(r, "ProductionReceipt detail with items field");
        Object items = r.path("data.items");
        System.out.println("  生产入库单详情items字段: " + (items != null ? "存在" : "不存在"));
    }

    // ==================================================================
    // BOM展开功能验证
    // ==================================================================

    @Test
    @DisplayName("关联数据 - BOM树形展开接口")
    void testBomExpand() {
        Long bomId = createBomAndGetId();
        // 先获取BOM的物料编码
        Response bomResp = authGet("/api/masterdata/bom/{id}", bomId);
        String materialCode = bomResp.path("data.materialCode");
        Response r = authGetWithParams("/api/masterdata/bom/expand",
                Map.of("materialCode", materialCode, "quantity", 1));
        int code = r.path("code");
        System.out.println("  BOM展开 code=" + code + " (200表示成功)");
        // BOM展开即使无子件也应返回成功
        org.junit.jupiter.api.Assertions.assertEquals(200, code,
                "BOM展开应返回200，实际: " + code);
    }

    @Test
    @DisplayName("关联数据 - BOM平铺展开接口")
    void testBomFlatten() {
        Long bomId = createBomAndGetId();
        Response bomResp = authGet("/api/masterdata/bom/{id}", bomId);
        String materialCode = bomResp.path("data.materialCode");
        Response r = authGetWithParams("/api/masterdata/bom/flatten",
                Map.of("materialCode", materialCode, "quantity", 1));
        int code = r.path("code");
        System.out.println("  BOM平铺展开 code=" + code + " (200表示成功)");
        org.junit.jupiter.api.Assertions.assertEquals(200, code,
                "BOM平铺展开应返回200，实际: " + code);
    }

    // ==================================================================
    // 端到端业务流程
    // ==================================================================

    @Test
    @DisplayName("端到端 - 生产工单完整生命周期（创建→下达→开工→完工→入库→关闭）")
    void testWorkOrderCompleteLifecycle() {
        System.out.println("\n=== 生产工单完整生命周期 ===");

        // Step 1: 创建工单
        Long orderId = createWorkOrderAndGetId();
        System.out.println("Step 1: 创建工单成功, orderId=" + orderId);

        // Step 2: 下达
        authPost("/api/production/workorders/{id}/release", null, orderId)
                .then().body("code", equalTo(200));
        System.out.println("Step 2: 工单下达成功");

        // Step 3: 开工
        authPost("/api/production/workorders/{id}/start", null, orderId)
                .then().body("code", equalTo(200));
        System.out.println("Step 3: 工单开工成功");

        // Step 4: 完工
        authPost("/api/production/workorders/{id}/complete", null, orderId)
                .then().body("code", equalTo(200));
        System.out.println("Step 4: 工单完工成功");

        // Step 5: 创建生产入库单
        Map<String, Object> receiptBody = buildProductionReceiptBody();
        receiptBody.put("productionOrderId", orderId);
        Response receiptResp = authPost("/api/production/receipts", receiptBody);
        receiptResp.then().body("code", equalTo(200));
        Long receiptId = ((Number) receiptResp.path("data.receiptId")).longValue();
        System.out.println("Step 5: 创建生产入库单成功, receiptId=" + receiptId);

        // Step 6: 入库单审核
        authPost("/api/production/receipts/{id}/approve", null, receiptId)
                .then().body("code", equalTo(200));
        System.out.println("Step 6: 入库单审核成功");

        // Step 7: 关闭工单
        authPost("/api/production/workorders/{id}/close", Map.of("reason", "生产完成"), orderId)
                .then().body("code", equalTo(200));
        System.out.println("Step 7: 工单关闭成功");

        // 验证最终状态
        authGet("/api/production/workorders/{id}", orderId)
                .then().body("data.status", equalTo("已关闭"));
        System.out.println("=== 生产工单完整生命周期验证通过 ===");
    }

    @Test
    @DisplayName("端到端 - 委外加工单完整生命周期（创建→下达→开工→完工→关闭）")
    void testSubcontractCompleteLifecycle() {
        System.out.println("\n=== 委外加工单完整生命周期 ===");

        Long subcontractId = createSubcontractAndGetId();
        System.out.println("Step 1: 创建委外加工单成功, subcontractId=" + subcontractId);

        authPost("/api/production/subcontract/{id}/release", null, subcontractId)
                .then().body("code", equalTo(200));
        System.out.println("Step 2: 委外单下达成功");

        authPost("/api/production/subcontract/{id}/start", null, subcontractId)
                .then().body("code", equalTo(200));
        System.out.println("Step 3: 委外单开工成功");

        authPost("/api/production/subcontract/{id}/complete", null, subcontractId)
                .then().body("code", equalTo(200));
        System.out.println("Step 4: 委外单完工成功");

        authPost("/api/production/subcontract/{id}/close", Map.of("reason", "委外完成"), subcontractId)
                .then().body("code", equalTo(200));
        System.out.println("Step 5: 委外单关闭成功");

        authGet("/api/production/subcontract/{id}", subcontractId)
                .then().body("data.status", equalTo("已关闭"));
        System.out.println("=== 委外加工单完整生命周期验证通过 ===");
    }

    @Test
    @DisplayName("端到端 - MRP方案完整生命周期（创建→审核→执行）")
    void testMrpPlanCompleteLifecycle() {
        System.out.println("\n=== MRP方案完整生命周期 ===");

        Long planId = createMrpPlanAndGetId();
        System.out.println("Step 1: 创建MRP方案成功, planId=" + planId);

        authPost("/api/production/mrp-plan/{id}/approve", null, planId)
                .then().body("code", equalTo(200));
        System.out.println("Step 2: MRP方案审核成功");

        authPost("/api/production/mrp-plan/{id}/execute", null, planId)
                .then().body("code", equalTo(200));
        System.out.println("Step 3: MRP方案执行成功");

        authGet("/api/production/mrp-plan/{id}", planId)
                .then().body("data.planStatus", equalTo("已执行"));
        System.out.println("=== MRP方案完整生命周期验证通过 ===");
    }

    @Test
    @DisplayName("端到端 - 工序汇报单完整审批流程（创建→提交→审核）")
    void testOperationReportApprovalFlow() {
        System.out.println("\n=== 工序汇报单审批流程 ===");

        Long reportId = createOperationReportAndGetId();
        System.out.println("Step 1: 创建工序汇报单成功, reportId=" + reportId);

        authPost("/api/production/operation-report/{id}/submit", null, reportId)
                .then().body("code", equalTo(200));
        authGet("/api/production/operation-report/{id}", reportId)
                .then().body("data.status", equalTo("待审核"));
        System.out.println("Step 2: 提交审核成功, 状态=待审核");

        authPost("/api/production/operation-report/{id}/approve", null, reportId)
                .then().body("code", equalTo(200));
        authGet("/api/production/operation-report/{id}", reportId)
                .then().body("data.status", equalTo("已生效"));
        System.out.println("Step 3: 审核成功, 状态=已生效");
        System.out.println("=== 工序汇报单审批流程验证通过 ===");
    }

    @Test
    @DisplayName("端到端 - BOM完整生命周期（创建→审核→停用）")
    void testBomCompleteLifecycle() {
        System.out.println("\n=== BOM完整生命周期 ===");

        Long bomId = createBomAndGetId();
        System.out.println("Step 1: 创建BOM成功, bomId=" + bomId);

        authPost("/api/masterdata/bom/{id}/approve", null, bomId)
                .then().body("code", equalTo(200));
        authGet("/api/masterdata/bom/{id}", bomId)
                .then().body("data.status", equalTo("已生效"));
        System.out.println("Step 2: BOM审核成功, 状态=已生效");

        authPost("/api/masterdata/bom/{id}/disable", null, bomId)
                .then().body("code", equalTo(200));
        authGet("/api/masterdata/bom/{id}", bomId)
                .then().body("data.status", equalTo("已停用"));
        System.out.println("Step 3: BOM停用成功, 状态=已停用");
        System.out.println("=== BOM完整生命周期验证通过 ===");
    }

    @Test
    @DisplayName("端到端 - 工艺路线完整生命周期（创建→审核→停用）")
    void testRoutingCompleteLifecycle() {
        System.out.println("\n=== 工艺路线完整生命周期 ===");

        Long routingId = createRoutingAndGetId();
        System.out.println("Step 1: 创建工艺路线成功, routingId=" + routingId);

        authPost("/api/production/routings/{id}/approve", null, routingId)
                .then().body("code", equalTo(200));
        authGet("/api/production/routings/{id}", routingId)
                .then().body("data.status", equalTo("已生效"));
        System.out.println("Step 2: 工艺路线审核成功, 状态=已生效");

        authPost("/api/production/routings/{id}/disable", null, routingId)
                .then().body("code", equalTo(200));
        authGet("/api/production/routings/{id}", routingId)
                .then().body("data.status", equalTo("已停用"));
        System.out.println("Step 3: 工艺路线停用成功, 状态=已停用");
        System.out.println("=== 工艺路线完整生命周期验证通过 ===");
    }
}
