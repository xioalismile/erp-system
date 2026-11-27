package com.erp.test.crm;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 商机管理模块自动化测试（文档 7.3）
 *
 * 测试范围：
 *  1. 功能测试：CRUD 完整性
 *  2. 阶段流转测试：初步接触 → 需求确认 → 方案报价 → 商务谈判 → 成交 / 已关闭
 *  3. 概率联动测试：阶段变更后概率自动映射
 *  4. 商机关闭测试：赢单/丢单，关闭后不可修改
 *  5. 编码规则测试：OPP-yyyyMMdd-0001
 *  6. 边界值与异常测试
 *  7. 仪表盘统计测试
 */
@DisplayName("7.3 商机管理 - 自动化测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmOpportunityTest extends CrmTestBase {

    private static final String BASE = "/api/crm/opportunity";
    private static Long testOppId;
    private static Long testCustomerId;

    @Test
    @Order(0)
    @DisplayName("TC-OPP-000 准备测试客户")
    void prepareCustomer() {
        testCustomerId = createCustomerAndGetId();
        System.out.println("[TC-OPP-000] 测试客户 customerId=" + testCustomerId);
    }

    // ===================== 1. 功能测试 =====================

    @Test
    @Order(1)
    @DisplayName("TC-OPP-001 创建商机 - 正常流程")
    void testCreateOpp_Success() {
        Map<String, Object> body = buildOpportunityBody(testCustomerId);
        Response r = authPost(BASE, body);

        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.opportunityId", notNullValue())
                .body("data.opportunityNo", startsWith("OPP-"))
                .body("data.salesStage", equalTo("初步接触"))
                .body("data.probability", equalTo(10))
                .body("data.isClosed", equalTo(0));

        testOppId = ((Number) r.path("data.opportunityId")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("TC-OPP-002 编号格式校验 - OPP-yyyyMMdd-0001")
    void testOppNoFormat() {
        Response r = authPost(BASE, buildOpportunityBody(testCustomerId));
        String no = r.path("data.opportunityNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^OPP-\\d{8}-\\d{4}$"),
                "商机编号格式不正确: " + no);
    }

    @Test
    @Order(3)
    @DisplayName("TC-OPP-003 查询商机详情")
    void testGetOpp_Success() {
        Response r = authGet(BASE + "/{id}", testOppId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.opportunityId", equalTo(testOppId.intValue()));
    }

    @Test
    @Order(4)
    @DisplayName("TC-OPP-004 分页查询商机列表")
    void testPageQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        authGetWithParams(BASE + "/page", params)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.records", notNullValue());
    }

    // ===================== 2. 阶段流转与概率联动测试 =====================

    @Test
    @Order(10)
    @DisplayName("TC-OPP-010 阶段流转 - 初步接触→需求确认，概率 10→25")
    void testStageTransition_NeedConfirm() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "需求确认");

        authPut(BASE + "/{id}/stage", body, oppId)
                .then().statusCode(200).body("code", equalTo(200));

        // 验证阶段与概率
        authGet(BASE + "/{id}", oppId)
                .then().body("data.salesStage", equalTo("需求确认"))
                .body("data.probability", equalTo(25))
                .body("data.stageUpdateDate", notNullValue());
    }

    @Test
    @Order(11)
    @DisplayName("TC-OPP-011 阶段流转 - 方案报价，概率=45")
    void testStageTransition_Quote() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "方案报价");

        authPut(BASE + "/{id}/stage", body, oppId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", oppId)
                .then().body("data.salesStage", equalTo("方案报价"))
                .body("data.probability", equalTo(45));
    }

    @Test
    @Order(12)
    @DisplayName("TC-OPP-012 阶段流转 - 商务谈判，概率=70")
    void testStageTransition_Negotiation() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "商务谈判");

        authPut(BASE + "/{id}/stage", body, oppId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", oppId)
                .then().body("data.salesStage", equalTo("商务谈判"))
                .body("data.probability", equalTo(70));
    }

    @Test
    @Order(13)
    @DisplayName("TC-OPP-013 阶段流转 - 成交，概率=100，设置实际成交日期")
    void testStageTransition_Won() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "成交");
        body.put("winReason", "客户认可方案与价格");

        authPut(BASE + "/{id}/stage", body, oppId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", oppId)
                .then().body("data.salesStage", equalTo("成交"))
                .body("data.probability", equalTo(100))
                .body("data.actualCloseDate", notNullValue());
    }

    @Test
    @Order(14)
    @DisplayName("TC-OPP-014 阶段流转 - 非法阶段应失败")
    void testStageTransition_InvalidStage() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "非法阶段");

        authPut(BASE + "/{id}/stage", body, oppId)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 3. 商机关闭测试 =====================

    @Test
    @Order(20)
    @DisplayName("TC-OPP-020 关闭商机 - 赢单")
    void testCloseOpportunity_Won() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("won", true);
        body.put("reason", "竞争对手报价更高，我方中标");

        authPut(BASE + "/{id}/close", body, oppId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", oppId)
                .then().body("data.isClosed", equalTo(1))
                .body("data.closeDate", notNullValue())
                .body("data.winReason", notNullValue());
    }

    @Test
    @Order(21)
    @DisplayName("TC-OPP-021 关闭商机 - 丢单")
    void testCloseOpportunity_Lost() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("won", false);
        body.put("reason", "客户选择竞品，价格更低");

        authPut(BASE + "/{id}/close", body, oppId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", oppId)
                .then().body("data.isClosed", equalTo(1))
                .body("data.loseReason", notNullValue());
    }

    @Test
    @Order(22)
    @DisplayName("TC-OPP-022 关闭商机 - 重复关闭应失败")
    void testCloseOpportunity_Duplicate() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        Map<String, Object> body = new HashMap<>();
        body.put("won", true);
        body.put("reason", "已成交");

        // 第一次关闭成功
        authPut(BASE + "/{id}/close", body, oppId)
                .then().body("code", equalTo(200));
        // 第二次关闭失败
        authPut(BASE + "/{id}/close", body, oppId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(23)
    @DisplayName("TC-OPP-023 已关闭商机不可再变更阶段")
    void testClosedOppCannotChangeStage() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        // 先关闭
        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("won", true);
        closeBody.put("reason", "已成交");
        authPut(BASE + "/{id}/close", closeBody, oppId)
                .then().body("code", equalTo(200));

        // 再变更阶段应失败
        Map<String, Object> stageBody = new HashMap<>();
        stageBody.put("stage", "商务谈判");
        authPut(BASE + "/{id}/stage", stageBody, oppId)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 4. 边界值与异常测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-OPP-030 创建商机 - 商机名称为空应失败")
    void testCreateOpp_EmptyName() {
        Map<String, Object> body = buildOpportunityBody(testCustomerId);
        body.put("opportunityName", "");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(31)
    @DisplayName("TC-OPP-031 创建商机 - 客户ID为空应失败")
    void testCreateOpp_NoCustomer() {
        Map<String, Object> body = buildOpportunityBody(testCustomerId);
        body.remove("customerId");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(32)
    @DisplayName("TC-OPP-032 删除商机 - 正常")
    void testDeleteOpp_Success() {
        Long oppId = createOpportunityAndGetId(testCustomerId);
        authDelete(BASE + "/{id}", oppId)
                .then().body("code", equalTo(200));
    }

    // ===================== 5. 仪表盘测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-OPP-040 仪表盘统计 - 接口可用")
    void testDashboard() {
        authGet(BASE + "/dashboard")
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.totalOpportunities", notNullValue())
                .body("data.closedCount", notNullValue())
                .body("data.wonCount", notNullValue())
                .body("data.stageDist", notNullValue());
    }
}
