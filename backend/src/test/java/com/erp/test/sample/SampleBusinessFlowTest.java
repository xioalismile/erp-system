package com.erp.test.sample;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 打样管理模块业务流程与转BOM自动化测试
 *
 * 核心测试：打样完成转BOM功能
 *  1. 完整业务流程：创建→提交→审核→开始→完成→确认→转BOM
 *  2. 转BOM后验证BOM数据正确性（父项物料、子件清单、版本、状态）
 *  3. 转BOM后打样单状态回写正确（is_bom_generated=1, generated_bom_id）
 *  4. 已转BOM不可重复转
 *  5. 成本计算验证
 *  6. 字段校验（数量必须>0、需求交期≤计划完成日期等）
 */
@DisplayName("打样管理模块 - 业务流程与转BOM测试")
public class SampleBusinessFlowTest extends SampleTestBase {

    /**
     * 核心：端到端完整业务流程测试
     * 创建打样单 → 提交 → 审核 → 开始打样 → 完成 → 确认 → 转BOM
     */
    @Test
    @DisplayName("端到端 - 完整业务流程：创建→提交→审核→开始→完成→确认→转BOM")
    void testFullBusinessFlow() {
        System.out.println("\n=== 打样单完整业务流程 ===");

        // Step 1: 创建打样单
        Long sampleId = createSampleAndGetId();
        System.out.println("Step 1: 创建打样单成功, sampleId=" + sampleId);

        // Step 2: 提交审核
        authPost("/api/sample/orders/{id}/submit", null, sampleId)
                .then().body("code", equalTo(200));
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("待审核"));
        System.out.println("Step 2: 提交审核成功, 状态=待审核");

        // Step 3: 审核
        authPost("/api/sample/orders/{id}/approve", null, sampleId)
                .then().body("code", equalTo(200));
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已生效"));
        System.out.println("Step 3: 审核成功, 状态=已生效");

        // Step 4: 开始打样
        authPost("/api/sample/orders/{id}/start", null, sampleId)
                .then().body("code", equalTo(200));
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("打样中"));
        System.out.println("Step 4: 开始打样成功, 状态=打样中");

        // Step 5: 完成打样
        authPost("/api/sample/orders/{id}/complete", null, sampleId)
                .then().body("code", equalTo(200));
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已完成"));
        System.out.println("Step 5: 完成打样成功, 状态=已完成");

        // Step 6: 确认样品
        authPost("/api/sample/orders/{id}/confirm", null, sampleId)
                .then().body("code", equalTo(200));
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已确认"));
        System.out.println("Step 6: 确认样品成功, 状态=已确认");

        // Step 7: 转BOM
        Response convertResp = authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId);
        convertResp.then().body("code", equalTo(200));
        Long bomId = ((Number) convertResp.path("data.bomId")).longValue();
        String bomCode = convertResp.path("data.bomCode");
        System.out.println("Step 7: 转BOM成功, bomId=" + bomId + ", bomCode=" + bomCode);

        // 验证打样单状态已回写
        Response sampleResp = authGet("/api/sample/orders/{id}", sampleId);
        sampleResp.then().body("data.status", equalTo("已转BOM"));
        sampleResp.then().body("data.isBomGenerated", equalTo(1));
        sampleResp.then().body("data.generatedBomId", equalTo(bomId.intValue()));
        System.out.println("Step 8: 打样单状态回写验证通过, status=已转BOM, isBomGenerated=1, generatedBomId=" + bomId);
        System.out.println("=== 完整业务流程验证通过 ===");
    }

    /**
     * 核心测试：转BOM后验证生成的BOM数据正确性
     */
    @Test
    @DisplayName("转BOM - 验证生成的BOM数据正确性(父项/子件/版本/状态)")
    void testConvertToBomDataCorrectness() {
        System.out.println("\n=== 转BOM数据正确性验证 ===");

        // 创建打样单并流转到已确认
        Long sampleId = createSampleAndGetId();
        Response createResp = authGet("/api/sample/orders/{id}", sampleId);
        String parentMaterialCode = createResp.path("data.materialCode");
        String parentMaterialName = createResp.path("data.materialName");
        int itemCount = ((java.util.List<?>) createResp.path("data.items")).size();
        System.out.println("  打样单父项物料: " + parentMaterialCode + " / " + parentMaterialName + ", 子件数: " + itemCount);

        // 流转到已确认
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        authPost("/api/sample/orders/{id}/complete", null, sampleId);
        authPost("/api/sample/orders/{id}/confirm", null, sampleId);

        // 转BOM
        Response convertResp = authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId);
        convertResp.then().body("code", equalTo(200));
        Long bomId = ((Number) convertResp.path("data.bomId")).longValue();
        String bomCode = convertResp.path("data.bomCode");

        // 验证BOM基本字段
        System.out.println("  验证BOM字段...");
        convertResp.then().body("data.bomId", notNullValue());
        convertResp.then().body("data.bomCode", startsWith("BOM-"));
        convertResp.then().body("data.bomName", containsString("BOM"));
        convertResp.then().body("data.bomVersion", equalTo("V1.0"));
        convertResp.then().body("data.materialId", notNullValue());
        convertResp.then().body("data.materialCode", equalTo(parentMaterialCode));
        convertResp.then().body("data.materialName", equalTo(parentMaterialName));
        convertResp.then().body("data.bomType", equalTo("标准BOM"));
        convertResp.then().body("data.status", equalTo("草稿"));
        System.out.println("  BOM基本字段验证通过");

        // 查询BOM详情验证子件
        Response bomDetail = authGet("/api/masterdata/bom/{id}", bomId);
        bomDetail.then().body("code", equalTo(200));
        bomDetail.then().body("data.items", notNullValue());
        bomDetail.then().body("data.items.size()", equalTo(itemCount));
        System.out.println("  BOM子件数量验证通过: " + itemCount + " 项");

        // 验证子件行号从10开始, 步长10 (BOM模块设计规范, 便于中间插入新行)
        java.util.List<Map<String, Object>> bomItems = bomDetail.path("data.items");
        for (int i = 0; i < bomItems.size(); i++) {
            Integer lineNo = (Integer) bomItems.get(i).get("lineNo");
            int expectedLineNo = (i + 1) * 10;
            org.junit.jupiter.api.Assertions.assertEquals(expectedLineNo, lineNo,
                    "BOM子件行号应从10开始步长10递增, 第" + (i + 1) + "行 lineNo=" + lineNo);
        }
        System.out.println("  BOM子件行号验证通过 (10, 20, ..., 共" + itemCount + "项)");

        // 验证BOM编码已回写到打样单
        authGet("/api/sample/orders/{id}", sampleId)
                .then().body("data.generatedBomId", equalTo(bomId.intValue()));
        System.out.println("  BOM ID回写打样单验证通过, generatedBomId=" + bomId);
        System.out.println("=== 转BOM数据正确性验证通过 ===");
    }

    /**
     * 核心测试：已转BOM的打样单不可重复转
     */
    @Test
    @DisplayName("转BOM - 已转BOM的打样单不可重复转")
    void testCannotConvertTwice() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        authPost("/api/sample/orders/{id}/complete", null, sampleId);
        authPost("/api/sample/orders/{id}/confirm", null, sampleId);

        // 第一次转BOM应成功
        authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId)
                .then().body("code", equalTo(200));
        System.out.println("  第一次转BOM成功");

        // 第二次转BOM应失败
        Response r = authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId);
        assertFailed(r, "重复转BOM");
        System.out.println("  第二次转BOM被拒绝, 符合预期");
    }

    /**
     * 测试：内部研发打样单可不填客户
     */
    @Test
    @DisplayName("业务规则 - 内部研发打样单可不填客户")
    void testInternalRdWithoutCustomer() {
        Map<String, Object> body = buildSampleBody();
        body.put("sampleType", "内部研发");
        body.put("customerId", null);
        body.put("customerName", null);
        Response r = authPost("/api/sample/orders", body);
        assertSuccess(r, "内部研发无客户创建");
        r.then().body("data.status", equalTo("草稿"));
        System.out.println("  内部研发打样单无客户创建成功");
    }

    /**
     * 测试：非内部研发打样单必须填写客户
     */
    @Test
    @DisplayName("业务规则 - 客户打样必须填写客户")
    void testCustomerSampleMustHaveCustomer() {
        Map<String, Object> body = buildSampleBody();
        body.put("sampleType", "新产品");
        body.put("customerId", null);
        body.put("customerName", null);
        Response r = authPost("/api/sample/orders", body);
        assertFailed(r, "客户打样无客户创建");
        System.out.println("  客户打样无客户被拒绝, 符合预期");
    }

    /**
     * 测试：打样数量必须 > 0
     */
    @Test
    @DisplayName("业务规则 - 打样数量必须大于0")
    void testSampleQtyMustBePositive() {
        Map<String, Object> body = buildSampleBody();
        body.put("sampleQty", new BigDecimal("0"));
        Response r = authPost("/api/sample/orders", body);
        assertFailed(r, "打样数量=0");
        System.out.println("  打样数量=0被拒绝, 符合预期");
    }

    @Test
    @DisplayName("业务规则 - 打样数量不可为负")
    void testSampleQtyCannotBeNegative() {
        Map<String, Object> body = buildSampleBody();
        body.put("sampleQty", new BigDecimal("-5"));
        Response r = authPost("/api/sample/orders", body);
        assertFailed(r, "打样数量=-5");
        System.out.println("  打样数量=-5被拒绝, 符合预期");
    }

    /**
     * 测试：需求交期 ≤ 计划完成日期
     */
    @Test
    @DisplayName("业务规则 - 计划完成日期不能早于需求交期")
    void testPlanDateCannotBeEarlierThanDemandDate() {
        Map<String, Object> body = buildSampleBody();
        body.put("demandDate", java.time.LocalDate.now().plusDays(10).toString());
        body.put("planFinishDate", java.time.LocalDate.now().plusDays(5).toString());
        Response r = authPost("/api/sample/orders", body);
        assertFailed(r, "计划完成日期早于需求交期");
        System.out.println("  计划完成日期早于需求交期被拒绝, 符合预期");
    }

    /**
     * 测试：子件用量必须 > 0
     */
    @Test
    @DisplayName("业务规则 - 子件基本用量必须大于0")
    void testItemQtyMustBePositive() {
        Map<String, Object> body = buildSampleBody();
        java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) body.get("items");
        items.get(0).put("baseQty", new BigDecimal("0"));
        Response r = authPost("/api/sample/orders", body);
        assertFailed(r, "子件用量=0");
        System.out.println("  子件用量=0被拒绝, 符合预期");
    }

    /**
     * 测试：成本计算验证（表体金额合计）
     */
    @Test
    @DisplayName("业务规则 - 成本金额汇总自表体(用量×单价)")
    void testCostCalculation() {
        Map<String, Object> body = buildSampleBody();
        // 子件1: 用量2 × 单价10 = 20
        // 子件2: 用量3 × 单价8 = 24
        // 合计 = 44
        Response r = authPost("/api/sample/orders", body);
        assertSuccess(r, "成本计算");
        r.then().body("data.costAmount", comparesEqualTo(44.0f));
        System.out.println("  成本计算验证通过: costAmount=" + r.path("data.costAmount") + " (预期44.00)");
    }

    /**
     * 测试：已生效状态不可直接修改（需先重置草稿）
     */
    @Test
    @DisplayName("业务规则 - 已生效状态不可直接修改")
    void testCannotModifyApprovedSample() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);

        Map<String, Object> body = buildSampleBody();
        body.put("sampleId", sampleId);
        body.put("sampleName", "尝试修改已生效");
        Response r = authPut("/api/sample/orders/{id}", body, sampleId);
        assertFailed(r, "修改已生效打样单");
        System.out.println("  已生效打样单修改被拒绝, 符合预期");
    }

    /**
     * 测试：转BOM后打样单不可取消
     */
    @Test
    @DisplayName("业务规则 - 已转BOM的打样单不可取消")
    void testCannotCancelConvertedSample() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        authPost("/api/sample/orders/{id}/complete", null, sampleId);
        authPost("/api/sample/orders/{id}/confirm", null, sampleId);
        authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId);

        Response r = authPost("/api/sample/orders/{id}/cancel", Map.of("reason", "尝试取消"), sampleId);
        assertFailed(r, "取消已转BOM的打样单");
        System.out.println("  已转BOM打样单取消被拒绝, 符合预期");
    }

    /**
     * 测试：重置草稿后可再次编辑
     */
    @Test
    @DisplayName("业务规则 - 重置草稿后可再次编辑")
    void testCanModifyAfterReverseApprove() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        // 重置草稿
        authPost("/api/sample/orders/{id}/reverse-approve", null, sampleId);
        // 重置草稿后状态应为草稿
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("草稿"));
        // 重置草稿后可编辑
        Map<String, Object> body = buildSampleBody();
        body.put("sampleId", sampleId);
        body.put("sampleName", "重置草稿后修改");
        Response r = authPut("/api/sample/orders/{id}", body, sampleId);
        assertSuccess(r, "重置草稿后修改");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.sampleName", equalTo("重置草稿后修改"));
        System.out.println("  重置草稿后修改成功, 符合预期");
    }
}
