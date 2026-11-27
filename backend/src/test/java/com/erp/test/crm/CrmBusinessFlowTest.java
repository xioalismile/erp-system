package com.erp.test.crm;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * CRM 业务流程端到端集成测试
 *
 * 测试场景：完整模拟 CRM 业务全流程
 *
 * 流程：销售线索 → 转化为客户 → 创建商机 → 跟进商机 → 阶段流转至成交
 *       → 客户投诉 → 投诉处理 → 满意度调查 → 客户满意度更新
 *
 * 此测试验证 5 个 CRM 子模块之间的数据联动与业务协同，
 * 是 ERP 软件验收测试中的关键端到端场景。
 */
@DisplayName("CRM 端到端业务流程 - 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmBusinessFlowTest extends CrmTestBase {

    private static Long leadId;
    private static Long customerId;
    private static Long opportunityId;
    private static Long complaintId;
    private static Long surveyId;

    @Test
    @Order(1)
    @DisplayName("TC-FLOW-01 创建销售线索")
    void step01_CreateLead() {
        Map<String, Object> body = buildLeadBody();
        body.put("intentionLevel", "A");
        body.put("estimatedAmount", new java.math.BigDecimal("500000.00"));

        Response r = authPost("/api/crm/lead", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.leadStatus", equalTo("新线索"));

        leadId = ((Number) r.path("data.leadId")).longValue();
        System.out.println("[FLOW-01] 线索创建: leadId=" + leadId);
    }

    @Test
    @Order(2)
    @DisplayName("TC-FLOW-02 跟进线索 - 状态自动转为跟进中")
    void step02_FollowUpLead() {
        Map<String, Object> body = buildFollowUpBody("线索", leadId, "测试线索");
        authPost("/api/crm/follow-up", body)
                .then().statusCode(200).body("code", equalTo(200));

        // 验证线索状态变为跟进中
        authGet("/api/crm/lead/{id}", leadId)
                .then().body("data.leadStatus", equalTo("跟进中"))
                .body("data.followUpCount", equalTo(1));
    }

    @Test
    @Order(3)
    @DisplayName("TC-FLOW-03 线索转化为正式客户")
    void step03_ConvertLeadToCustomer() {
        Response r = authPost("/api/crm/lead/{id}/convert", null, leadId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());

        customerId = ((Number) r.path("data")).longValue();
        org.junit.jupiter.api.Assertions.assertTrue(customerId > 0);

        // 验证线索状态变为已转化
        authGet("/api/crm/lead/{id}", leadId)
                .then().body("data.leadStatus", equalTo("已转化"))
                .body("data.convertedCustomerId", equalTo(customerId.intValue()));

        System.out.println("[FLOW-03] 线索转化: customerId=" + customerId);
    }

    @Test
    @Order(4)
    @DisplayName("TC-FLOW-04 为新客户创建商机")
    void step04_CreateOpportunity() {
        Map<String, Object> body = buildOpportunityBody(customerId);
        body.put("estimatedAmount", new java.math.BigDecimal("500000.00"));

        Response r = authPost("/api/crm/opportunity", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.salesStage", equalTo("初步接触"))
                .body("data.probability", equalTo(10));

        opportunityId = ((Number) r.path("data.opportunityId")).longValue();
        System.out.println("[FLOW-04] 商机创建: opportunityId=" + opportunityId);
    }

    @Test
    @Order(5)
    @DisplayName("TC-FLOW-05 跟进商机")
    void step05_FollowUpOpportunity() {
        Map<String, Object> body = buildFollowUpBody("商机", opportunityId, "测试商机");
        authPost("/api/crm/follow-up", body)
                .then().statusCode(200).body("code", equalTo(200));

        // 验证商机跟进次数+1
        authGet("/api/crm/opportunity/{id}", opportunityId)
                .then().body("data.followUpCount", equalTo(1));
    }

    @Test
    @Order(6)
    @DisplayName("TC-FLOW-06 商机阶段流转：初步接触 → 需求确认")
    void step06_StageToNeedConfirm() {
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "需求确认");

        authPut("/api/crm/opportunity/{id}/stage", body, opportunityId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/crm/opportunity/{id}", opportunityId)
                .then().body("data.salesStage", equalTo("需求确认"))
                .body("data.probability", equalTo(25));
    }

    @Test
    @Order(7)
    @DisplayName("TC-FLOW-07 商机阶段流转：需求确认 → 方案报价")
    void step07_StageToQuote() {
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "方案报价");

        authPut("/api/crm/opportunity/{id}/stage", body, opportunityId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/crm/opportunity/{id}", opportunityId)
                .then().body("data.salesStage", equalTo("方案报价"))
                .body("data.probability", equalTo(45));
    }

    @Test
    @Order(8)
    @DisplayName("TC-FLOW-08 商机阶段流转：方案报价 → 商务谈判")
    void step08_StageToNegotiation() {
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "商务谈判");

        authPut("/api/crm/opportunity/{id}/stage", body, opportunityId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/crm/opportunity/{id}", opportunityId)
                .then().body("data.salesStage", equalTo("商务谈判"))
                .body("data.probability", equalTo(70));
    }

    @Test
    @Order(9)
    @DisplayName("TC-FLOW-09 商机成交 - 阶段流转至成交，概率=100")
    void step09_StageToWon() {
        Map<String, Object> body = new HashMap<>();
        body.put("stage", "成交");
        body.put("winReason", "客户认可方案与价格，签订合同");

        authPut("/api/crm/opportunity/{id}/stage", body, opportunityId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/crm/opportunity/{id}", opportunityId)
                .then().body("data.salesStage", equalTo("成交"))
                .body("data.probability", equalTo(100))
                .body("data.actualCloseDate", notNullValue());
    }

    @Test
    @Order(10)
    @DisplayName("TC-FLOW-10 客户提出投诉")
    void step10_CreateComplaint() {
        Map<String, Object> body = buildComplaintBody(customerId, "测试客户");
        body.put("complaintType", "售后问题");
        body.put("severityLevel", "中");

        Response r = authPost("/api/crm/complaint", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.handleStatus", equalTo("新建"));

        complaintId = ((Number) r.path("data.complaintId")).longValue();
        System.out.println("[FLOW-10] 投诉创建: complaintId=" + complaintId);
    }

    @Test
    @Order(11)
    @DisplayName("TC-FLOW-11 投诉处理 - 新建→处理中→已解决→已关闭")
    void step11_ResolveComplaint() {
        // 指派处理人
        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("handlerId", 1);
        authPut("/api/crm/complaint/{id}/assign", assignBody, complaintId)
                .then().body("code", equalTo(200));

        // 处理完成
        Map<String, Object> resolveBody = new HashMap<>();
        resolveBody.put("handleResult", "已联系售后上门服务，问题解决");
        authPut("/api/crm/complaint/{id}/resolve", resolveBody, complaintId)
                .then().body("code", equalTo(200));

        // 关闭投诉
        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("satisfactionScore", 4);
        authPut("/api/crm/complaint/{id}/close", closeBody, complaintId)
                .then().body("code", equalTo(200));

        // 验证投诉状态
        authGet("/api/crm/complaint/{id}", complaintId)
                .then().body("data.handleStatus", equalTo("已关闭"))
                .body("data.satisfactionScore", equalTo(4));
    }

    @Test
    @Order(12)
    @DisplayName("TC-FLOW-12 验证投诉关闭后客户投诉次数+1")
    void step12_VerifyCustomerComplaintCount() {
        authGet("/api/customer/{id}", customerId)
                .then().body("data.complaintCount", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(13)
    @DisplayName("TC-FLOW-13 发起客户满意度调查")
    void step13_CreateSurvey() {
        Map<String, Object> body = buildSurveyBody(customerId, "测试客户");
        body.put("overallScore", 5);
        body.put("praiseContent", "产品质量优秀，服务到位");
        body.put("criticismContent", "建议增加在线客服渠道");

        Response r = authPost("/api/crm/survey", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.status", equalTo("草稿"));

        surveyId = ((Number) r.path("data.surveyId")).longValue();
    }

    @Test
    @Order(14)
    @DisplayName("TC-FLOW-14 完成满意度调查 - 客户满意度评分更新")
    void step14_CompleteSurvey() {
        authPut("/api/crm/survey/{id}/complete", null, surveyId)
                .then().statusCode(200).body("code", equalTo(200));

        // 验证调查状态
        authGet("/api/crm/survey/{id}", surveyId)
                .then().body("data.status", equalTo("已完成"));

        // 验证客户满意度评分=5（来自调查）
        authGet("/api/customer/{id}", customerId)
                .then().body("data.satisfactionScore", equalTo(5));
    }

    @Test
    @Order(15)
    @DisplayName("TC-FLOW-15 分析满意度调查")
    void step15_AnalyzeSurvey() {
        Map<String, Object> body = new HashMap<>();
        body.put("analyzeResult", "客户整体满意，建议持续优化售后渠道");

        authPut("/api/crm/survey/{id}/analyze", body, surveyId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/crm/survey/{id}", surveyId)
                .then().body("data.status", equalTo("已分析"))
                .body("data.analyzeResult", notNullValue());
    }

    @Test
    @Order(20)
    @DisplayName("TC-FLOW-20 全流程数据一致性校验")
    void step20_VerifyDataConsistency() {
        // 线索：已转化
        authGet("/api/crm/lead/{id}", leadId)
                .then().body("data.leadStatus", equalTo("已转化"))
                .body("data.convertedCustomerId", equalTo(customerId.intValue()));

        // 商机：成交
        authGet("/api/crm/opportunity/{id}", opportunityId)
                .then().body("data.salesStage", equalTo("成交"))
                .body("data.probability", equalTo(100));

        // 投诉：已关闭
        authGet("/api/crm/complaint/{id}", complaintId)
                .then().body("data.handleStatus", equalTo("已关闭"));

        // 调查：已分析
        authGet("/api/crm/survey/{id}", surveyId)
                .then().body("data.status", equalTo("已分析"));

        // 客户：投诉次数>=1，满意度=5
        authGet("/api/customer/{id}", customerId)
                .then().body("data.complaintCount", greaterThanOrEqualTo(1))
                .body("data.satisfactionScore", equalTo(5));

        System.out.println("[FLOW-20] 全流程数据一致性校验通过: leadId=" + leadId
                + " customerId=" + customerId + " oppId=" + opportunityId
                + " complaintId=" + complaintId + " surveyId=" + surveyId);
    }
}
