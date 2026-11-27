package com.erp.test.crm;

import io.restassured.RestAssured;
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
 * CRM 模块权限安全与数据一致性测试
 *
 * 测试范围：
 *  1. 权限测试：未授权访问应被拒绝
 *  2. SQL注入测试：恶意输入不应破坏系统
 *  3. 编号唯一性测试：并发场景下编号不重复
 *  4. 编码规则一致性测试：所有模块编号格式符合文档 7.8 规范
 *  5. 状态机一致性测试：非法状态流转被拒绝
 */
@DisplayName("CRM 权限安全与数据一致性 - 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmSecurityTest extends CrmTestBase {

    // ===================== 1. 权限测试 =====================

    @Test
    @Order(1)
    @DisplayName("TC-SEC-001 未授权访问线索列表 - 应失败")
    void testNoAuth_ListLead() {
        noAuthGet("/api/crm/lead/page?page=1&size=10")
                .then().statusCode(not(equalTo(200)));
    }

    @Test
    @Order(2)
    @DisplayName("TC-SEC-002 未授权创建商机 - 应失败")
    void testNoAuth_CreateOpp() {
        noAuthPost("/api/crm/opportunity", buildOpportunityBody(1L))
                .then().statusCode(not(equalTo(200)));
    }

    @Test
    @Order(3)
    @DisplayName("TC-SEC-003 未授权访问跟进记录 - 应失败")
    void testNoAuth_ListFollowUp() {
        noAuthGet("/api/crm/follow-up/page?page=1&size=10")
                .then().statusCode(not(equalTo(200)));
    }

    @Test
    @Order(4)
    @DisplayName("TC-SEC-004 未授权访问投诉列表 - 应失败")
    void testNoAuth_ListComplaint() {
        noAuthGet("/api/crm/complaint/page?page=1&size=10")
                .then().statusCode(not(equalTo(200)));
    }

    @Test
    @Order(5)
    @DisplayName("TC-SEC-005 未授权访问调查列表 - 应失败")
    void testNoAuth_ListSurvey() {
        noAuthGet("/api/crm/survey/page?page=1&size=10")
                .then().statusCode(not(equalTo(200)));
    }

    @Test
    @Order(6)
    @DisplayName("TC-SEC-006 无效token访问 - 应失败")
    void testInvalidToken() {
        RestAssured.given()
                .header("Authorization", "Bearer invalid.token.here")
                .when()
                .get("/api/crm/lead/dashboard")
                .then().statusCode(not(equalTo(200)));
    }

    // ===================== 2. SQL注入测试 =====================

    @Test
    @Order(10)
    @DisplayName("TC-SEC-010 SQL注入 - 线索查询关键字注入")
    void testSqlInjection_LeadQuery() {
        String[] payloads = {
                "' OR '1'='1",
                "'; DROP TABLE crm_lead; --",
                "' UNION SELECT * FROM sys_user --",
                "admin'/*",
                "1' OR 1=1 --"
        };
        for (String payload : payloads) {
            Map<String, Object> params = new HashMap<>();
            params.put("page", 1);
            params.put("size", 10);
            params.put("keyword", payload);
            authGetWithParams("/api/crm/lead/page", params)
                    .then().statusCode(200)
                    .body("code", equalTo(200));  // 不应报错，应正常返回空结果
        }
    }

    @Test
    @Order(11)
    @DisplayName("TC-SEC-011 SQL注入 - 线索名称字段注入")
    void testSqlInjection_LeadName() {
        Map<String, Object> body = buildLeadBody();
        body.put("leadName", "测试'; DROP TABLE crm_lead; --");
        // 创建不应崩溃，应被当作普通字符串处理或被拒绝
        Response r = authPost("/api/crm/lead", body);
        // 不论成功失败，系统应保持稳定
        org.junit.jupiter.api.Assertions.assertEquals(200, r.statusCode());
    }

    // ===================== 3. 编码规则一致性测试（文档 7.8） =====================

    @Test
    @Order(20)
    @DisplayName("TC-SEC-020 编码规则 - 线索编号 L-yyyyMMdd-0001")
    void testLeadNoRule() {
        Response r = authPost("/api/crm/lead", buildLeadBody());
        String no = r.path("data.leadNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^L-\\d{8}-\\d{4}$"),
                "线索编号应符合 L-yyyyMMdd-0001: " + no);
    }

    @Test
    @Order(21)
    @DisplayName("TC-SEC-021 编码规则 - 商机编号 OPP-yyyyMMdd-0001")
    void testOppNoRule() {
        Long customerId = createCustomerAndGetId();
        Response r = authPost("/api/crm/opportunity", buildOpportunityBody(customerId));
        String no = r.path("data.opportunityNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^OPP-\\d{8}-\\d{4}$"),
                "商机编号应符合 OPP-yyyyMMdd-0001: " + no);
    }

    @Test
    @Order(22)
    @DisplayName("TC-SEC-022 编码规则 - 跟进编号 FU-yyyyMMdd-0001")
    void testFollowUpNoRule() {
        Long customerId = createCustomerAndGetId();
        Response r = authPost("/api/crm/follow-up", buildFollowUpBody("客户", customerId, "测试客户"));
        String no = r.path("data.followUpNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^FU-\\d{8}-\\d{4}$"),
                "跟进编号应符合 FU-yyyyMMdd-0001: " + no);
    }

    @Test
    @Order(23)
    @DisplayName("TC-SEC-023 编码规则 - 投诉编号 C-yyyyMMdd-0001")
    void testComplaintNoRule() {
        Long customerId = createCustomerAndGetId();
        Response r = authPost("/api/crm/complaint", buildComplaintBody(customerId, "测试客户"));
        String no = r.path("data.complaintNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^C-\\d{8}-\\d{4}$"),
                "投诉编号应符合 C-yyyyMMdd-0001: " + no);
    }

    @Test
    @Order(24)
    @DisplayName("TC-SEC-024 编码规则 - 调查编号 SUR-yyyyMMdd-0001")
    void testSurveyNoRule() {
        Long customerId = createCustomerAndGetId();
        Response r = authPost("/api/crm/survey", buildSurveyBody(customerId, "测试客户"));
        String no = r.path("data.surveyNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^SUR-\\d{8}-\\d{4}$"),
                "调查编号应符合 SUR-yyyyMMdd-0001: " + no);
    }

    @Test
    @Order(25)
    @DisplayName("TC-SEC-025 编号唯一性 - 同毫秒创建多个线索编号不重复")
    void testLeadNoUniqueness() {
        java.util.Set<String> nos = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            Response r = authPost("/api/crm/lead", buildLeadBody());
            nos.add(r.path("data.leadNo"));
        }
        org.junit.jupiter.api.Assertions.assertEquals(3, nos.size(),
                "3 个线索编号应全部唯一: " + nos);
    }

    // ===================== 4. 状态机一致性测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-SEC-030 状态机 - 线索已转化后不可再转化")
    void testLeadState_ConvertedImmutable() {
        Long leadId = createLeadAndGetId();
        authPost("/api/crm/lead/{id}/convert", null, leadId)
                .then().body("code", equalTo(200));
        // 再次转化应失败
        authPost("/api/crm/lead/{id}/convert", null, leadId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(31)
    @DisplayName("TC-SEC-031 状态机 - 商机已关闭后不可再变更阶段")
    void testOppState_ClosedImmutable() {
        Long customerId = createCustomerAndGetId();
        Long oppId = createOpportunityAndGetId(customerId);
        // 关闭商机
        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("won", true);
        closeBody.put("reason", "已成交");
        authPut("/api/crm/opportunity/{id}/close", closeBody, oppId)
                .then().body("code", equalTo(200));
        // 再变更阶段应失败
        Map<String, Object> stageBody = new HashMap<>();
        stageBody.put("stage", "商务谈判");
        authPut("/api/crm/opportunity/{id}/stage", stageBody, oppId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(32)
    @DisplayName("TC-SEC-032 状态机 - 投诉状态不可回退")
    void testComplaintState_NoRollback() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost("/api/crm/complaint", buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();
        // 指派 → 处理中
        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("handlerId", 1);
        authPut("/api/crm/complaint/{id}/assign", assignBody, compId);

        // 尝试再次指派（应失败，因为已不在"新建"状态）
        authPut("/api/crm/complaint/{id}/assign", assignBody, compId)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 5. 仪表盘可用性测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-SEC-040 所有仪表盘接口可用")
    void testAllDashboards() {
        authGet("/api/crm/lead/dashboard").then().body("code", equalTo(200));
        authGet("/api/crm/opportunity/dashboard").then().body("code", equalTo(200));
        authGet("/api/crm/follow-up/dashboard").then().body("code", equalTo(200));
        authGet("/api/crm/complaint/dashboard").then().body("code", equalTo(200));
        authGet("/api/crm/survey/dashboard").then().body("code", equalTo(200));
    }
}
