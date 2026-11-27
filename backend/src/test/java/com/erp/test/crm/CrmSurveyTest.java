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
 * 客户满意度调查模块自动化测试（文档 7.6）
 *
 * 测试范围：
 *  1. 功能测试：CRUD
 *  2. 编码规则测试：SUR-yyyyMMdd-0001
 *  3. 状态流转测试：草稿 → 进行中 → 已完成 → 已分析
 *  4. 业务规则测试：完成调查后联动客户满意度
 *  5. 边界值与异常测试：评分越界
 *  6. 仪表盘统计
 */
@DisplayName("7.6 客户满意度调查 - 自动化测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmSurveyTest extends CrmTestBase {

    private static final String BASE = "/api/crm/survey";

    // ===================== 1. 功能测试 =====================

    @Test
    @Order(1)
    @DisplayName("TC-SUR-001 创建调查 - 正常流程")
    void testCreateSurvey_Success() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");

        Response r = authPost(BASE, body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.surveyId", notNullValue())
                .body("data.surveyNo", startsWith("SUR-"))
                .body("data.status", equalTo("草稿"))
                .body("data.overallScore", equalTo(4));
    }

    @Test
    @Order(2)
    @DisplayName("TC-SUR-002 编号格式校验 - SUR-yyyyMMdd-0001")
    void testSurveyNoFormat() {
        Long customerId = createCustomerAndGetId();
        Response r = authPost(BASE, buildSurveyBody(customerId, "测试客户"));
        String no = r.path("data.surveyNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^SUR-\\d{8}-\\d{4}$"),
                "调查编号格式不正确: " + no);
    }

    @Test
    @Order(3)
    @DisplayName("TC-SUR-003 查询调查详情")
    void testGetSurvey() {
        Long customerId = createCustomerAndGetId();
        Long surveyId = ((Number) authPost(BASE, buildSurveyBody(customerId, "测试客户"))
                .path("data.surveyId")).longValue();

        authGet(BASE + "/{id}", surveyId)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.surveyId", equalTo(surveyId.intValue()));
    }

    @Test
    @Order(4)
    @DisplayName("TC-SUR-004 分页查询调查列表")
    void testPageQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        authGetWithParams(BASE + "/page", params)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.records", notNullValue());
    }

    // ===================== 2. 状态流转测试 =====================

    @Test
    @Order(10)
    @DisplayName("TC-SUR-010 状态流转 - 草稿 → 进行中")
    void testStatusTransition_InProgress() {
        Long customerId = createCustomerAndGetId();
        Long surveyId = ((Number) authPost(BASE, buildSurveyBody(customerId, "测试客户"))
                .path("data.surveyId")).longValue();

        Map<String, Object> body = new HashMap<>();
        body.put("status", "进行中");
        authPut(BASE + "/{id}/status", body, surveyId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", surveyId)
                .then().body("data.status", equalTo("进行中"));
    }

    @Test
    @Order(11)
    @DisplayName("TC-SUR-011 状态流转 - 草稿 → 已完成")
    void testStatusTransition_Complete() {
        Long customerId = createCustomerAndGetId();
        Long surveyId = ((Number) authPost(BASE, buildSurveyBody(customerId, "测试客户"))
                .path("data.surveyId")).longValue();

        authPut(BASE + "/{id}/complete", null, surveyId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", surveyId)
                .then().body("data.status", equalTo("已完成"));
    }

    @Test
    @Order(12)
    @DisplayName("TC-SUR-012 状态流转 - 已完成 → 已分析")
    void testStatusTransition_Analyze() {
        Long customerId = createCustomerAndGetId();
        Long surveyId = ((Number) authPost(BASE, buildSurveyBody(customerId, "测试客户"))
                .path("data.surveyId")).longValue();

        // 先完成
        authPut(BASE + "/{id}/complete", null, surveyId);

        // 再分析
        Map<String, Object> body = new HashMap<>();
        body.put("analyzeResult", "客户总体满意，售后服务需改进");
        authPut(BASE + "/{id}/analyze", body, surveyId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", surveyId)
                .then().body("data.status", equalTo("已分析"))
                .body("data.analyzeResult", notNullValue());
    }

    @Test
    @Order(13)
    @DisplayName("TC-SUR-013 状态流转 - 草稿直接分析应失败")
    void testStatusTransition_SkipComplete() {
        Long customerId = createCustomerAndGetId();
        Long surveyId = ((Number) authPost(BASE, buildSurveyBody(customerId, "测试客户"))
                .path("data.surveyId")).longValue();

        Map<String, Object> body = new HashMap<>();
        body.put("analyzeResult", "测试分析");
        authPut(BASE + "/{id}/analyze", body, surveyId)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 3. 数据联动测试 =====================

    @Test
    @Order(20)
    @DisplayName("TC-SUR-020 完成调查后 - 客户满意度评分更新")
    void testCompleteSurvey_UpdatesCustomer() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");
        body.put("overallScore", 5);  // 设置总体评分=5

        Long surveyId = ((Number) authPost(BASE, body).path("data.surveyId")).longValue();

        authPut(BASE + "/{id}/complete", null, surveyId)
                .then().body("code", equalTo(200));

        // 验证客户满意度评分=5
        authGet("/api/customer/{id}", customerId)
                .then().body("data.satisfactionScore", equalTo(5));
    }

    // ===================== 4. 边界值与异常测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-SUR-030 创建调查 - 调查名称为空应失败")
    void testCreateSurvey_EmptyName() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");
        body.put("surveyName", "");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(31)
    @DisplayName("TC-SUR-031 创建调查 - 调查类型非法应失败")
    void testCreateSurvey_InvalidType() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");
        body.put("surveyType", "非法类型");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(32)
    @DisplayName("TC-SUR-032 创建调查 - 总体评分越界(>5)应失败")
    void testCreateSurvey_OverallScoreOverRange() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");
        body.put("overallScore", 6);
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(33)
    @DisplayName("TC-SUR-033 创建调查 - 总体评分为负数应失败")
    void testCreateSurvey_OverallScoreNegative() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");
        body.put("overallScore", -1);
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(34)
    @DisplayName("TC-SUR-034 删除调查 - 正常")
    void testDeleteSurvey() {
        Long customerId = createCustomerAndGetId();
        Long surveyId = ((Number) authPost(BASE, buildSurveyBody(customerId, "测试客户"))
                .path("data.surveyId")).longValue();
        authDelete(BASE + "/{id}", surveyId)
                .then().body("code", equalTo(200));
    }

    // ===================== 5. 仪表盘测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-SUR-040 仪表盘统计 - 接口可用")
    void testDashboard() {
        authGet(BASE + "/dashboard")
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.totalSurveys", notNullValue())
                .body("data.avgOverall", notNullValue())
                .body("data.lowScoreCount", notNullValue())
                .body("data.statusDist", notNullValue());
    }
}
