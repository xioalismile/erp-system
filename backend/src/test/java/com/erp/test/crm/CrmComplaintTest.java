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
 * 客户投诉管理模块自动化测试（文档 7.5）
 *
 * 测试范围：
 *  1. 功能测试：CRUD
 *  2. 编码规则测试：C-yyyyMMdd-0001
 *  3. 状态流转测试：新建 → 处理中 → 已解决 → 已关闭
 *  4. 业务规则测试：指派、处理、关闭
 *  5. 数据联动测试：关闭投诉后客户投诉次数+1、满意度更新
 *  6. 边界值与异常测试
 *  7. 仪表盘统计
 */
@DisplayName("7.5 客户投诉管理 - 自动化测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmComplaintTest extends CrmTestBase {

    private static final String BASE = "/api/crm/complaint";

    // ===================== 1. 功能测试 =====================

    @Test
    @Order(1)
    @DisplayName("TC-COMP-001 创建投诉 - 正常流程")
    void testCreateComplaint_Success() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildComplaintBody(customerId, "测试客户");

        Response r = authPost(BASE, body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.complaintId", notNullValue())
                .body("data.complaintNo", startsWith("C-"))
                .body("data.handleStatus", equalTo("新建"))
                .body("data.severityLevel", equalTo("中"));
    }

    @Test
    @Order(2)
    @DisplayName("TC-COMP-002 编号格式校验 - C-yyyyMMdd-0001")
    void testComplaintNoFormat() {
        Long customerId = createCustomerAndGetId();
        Response r = authPost(BASE, buildComplaintBody(customerId, "测试客户"));
        String no = r.path("data.complaintNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^C-\\d{8}-\\d{4}$"),
                "投诉编号格式不正确: " + no);
    }

    @Test
    @Order(3)
    @DisplayName("TC-COMP-003 查询投诉详情")
    void testGetComplaint() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();

        authGet(BASE + "/{id}", compId)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.complaintId", equalTo(compId.intValue()));
    }

    @Test
    @Order(4)
    @DisplayName("TC-COMP-004 分页查询投诉列表")
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
    @DisplayName("TC-COMP-010 状态流转 - 新建 → 处理中（指派处理人）")
    void testStatusTransition_Assign() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();

        Map<String, Object> body = new HashMap<>();
        body.put("handlerId", 1);
        authPut(BASE + "/{id}/assign", body, compId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", compId)
                .then().body("data.handleStatus", equalTo("处理中"))
                .body("data.assignedHandlerId", equalTo(1))
                .body("data.assignedDate", notNullValue());
    }

    @Test
    @Order(11)
    @DisplayName("TC-COMP-011 状态流转 - 处理中 → 已解决（填写处理结果）")
    void testStatusTransition_Resolve() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();

        // 先指派
        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("handlerId", 1);
        authPut(BASE + "/{id}/assign", assignBody, compId);

        // 再处理完成
        Map<String, Object> resolveBody = new HashMap<>();
        resolveBody.put("handleResult", "已联系客户道歉并改进服务流程");
        authPut(BASE + "/{id}/resolve", resolveBody, compId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", compId)
                .then().body("data.handleStatus", equalTo("已解决"))
                .body("data.handleResult", notNullValue())
                .body("data.handleDate", notNullValue());
    }

    @Test
    @Order(12)
    @DisplayName("TC-COMP-012 状态流转 - 已解决 → 已关闭（带满意度评分）")
    void testStatusTransition_Close() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();

        // 指派
        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("handlerId", 1);
        authPut(BASE + "/{id}/assign", assignBody, compId);
        // 处理
        Map<String, Object> resolveBody = new HashMap<>();
        resolveBody.put("handleResult", "已解决");
        authPut(BASE + "/{id}/resolve", resolveBody, compId);
        // 关闭
        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("satisfactionScore", 4);
        authPut(BASE + "/{id}/close", closeBody, compId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", compId)
                .then().body("data.handleStatus", equalTo("已关闭"))
                .body("data.closeDate", notNullValue())
                .body("data.satisfactionScore", equalTo(4));
    }

    @Test
    @Order(13)
    @DisplayName("TC-COMP-013 状态流转 - 新建直接关闭应失败（需先指派+处理）")
    void testStatusTransition_SkipResolve() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();

        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("satisfactionScore", 5);
        authPut(BASE + "/{id}/close", closeBody, compId)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 3. 数据联动测试 =====================

    @Test
    @Order(20)
    @DisplayName("TC-COMP-020 关闭投诉后 - 客户投诉次数+1、满意度更新")
    void testCloseComplaint_UpdatesCustomer() {
        Long customerId = createCustomerAndGetId();
        // 获取投诉前客户的投诉次数
        Response before = authGet("/api/customer/{id}", customerId);
        int countBefore = before.path("data.complaintCount") == null ? 0 : (int) before.path("data.complaintCount");

        // 创建并完整流转一个投诉
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();
        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("handlerId", 1);
        authPut(BASE + "/{id}/assign", assignBody, compId);
        Map<String, Object> resolveBody = new HashMap<>();
        resolveBody.put("handleResult", "已解决");
        authPut(BASE + "/{id}/resolve", resolveBody, compId);
        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("satisfactionScore", 5);
        authPut(BASE + "/{id}/close", closeBody, compId);

        // 验证客户投诉次数+1、满意度=5
        authGet("/api/customer/{id}", customerId)
                .then().body("data.complaintCount", equalTo(countBefore + 1))
                .body("data.satisfactionScore", equalTo(5));
    }

    // ===================== 4. 边界值与异常测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-COMP-030 创建投诉 - 投诉内容为空应失败")
    void testCreateComplaint_EmptyContent() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildComplaintBody(customerId, "测试客户");
        body.put("complaintContent", "");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(31)
    @DisplayName("TC-COMP-031 创建投诉 - 投诉类型非法应失败")
    void testCreateComplaint_InvalidType() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildComplaintBody(customerId, "测试客户");
        body.put("complaintType", "非法类型");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(32)
    @DisplayName("TC-COMP-032 创建投诉 - 严重程度非法应失败")
    void testCreateComplaint_InvalidSeverity() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildComplaintBody(customerId, "测试客户");
        body.put("severityLevel", "紧急");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(33)
    @DisplayName("TC-COMP-033 关闭投诉 - 满意度评分越界（>5）应失败")
    void testCloseComplaint_InvalidScore() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();
        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("handlerId", 1);
        authPut(BASE + "/{id}/assign", assignBody, compId);
        Map<String, Object> resolveBody = new HashMap<>();
        resolveBody.put("handleResult", "已解决");
        authPut(BASE + "/{id}/resolve", resolveBody, compId);

        Map<String, Object> closeBody = new HashMap<>();
        closeBody.put("satisfactionScore", 6);  // 越界
        authPut(BASE + "/{id}/close", closeBody, compId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(34)
    @DisplayName("TC-COMP-034 删除投诉 - 正常")
    void testDeleteComplaint() {
        Long customerId = createCustomerAndGetId();
        Long compId = ((Number) authPost(BASE, buildComplaintBody(customerId, "测试客户"))
                .path("data.complaintId")).longValue();
        authDelete(BASE + "/{id}", compId)
                .then().body("code", equalTo(200));
    }

    // ===================== 5. 仪表盘测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-COMP-040 仪表盘统计 - 接口可用")
    void testDashboard() {
        authGet(BASE + "/dashboard")
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.totalComplaints", notNullValue())
                .body("data.highSeverityPending", notNullValue())
                .body("data.statusDist", notNullValue());
    }
}
