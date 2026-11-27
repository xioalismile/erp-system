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
 * 客户跟进记录模块自动化测试（文档 7.4）
 *
 * 测试范围：
 *  1. 功能测试：CRUD
 *  2. 编码规则测试：FU-yyyyMMdd-0001
 *  3. 来源类型测试：客户/线索/商机
 *  4. 数据联动测试：跟进后自动更新来源对象的最后跟进日期与跟进次数
 *  5. 边界值与异常测试：内容长度、必填字段
 *  6. 按来源查询、今日待跟进查询
 *  7. 仪表盘统计
 */
@DisplayName("7.4 客户跟进记录 - 自动化测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmFollowUpTest extends CrmTestBase {

    private static final String BASE = "/api/crm/follow-up";

    // ===================== 1. 功能测试 =====================

    @Test
    @Order(1)
    @DisplayName("TC-FU-001 创建跟进记录 - 来源=客户")
    void testCreateFollowUp_Customer() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildFollowUpBody("客户", customerId, "测试客户");

        Response r = authPost(BASE, body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.followUpId", notNullValue())
                .body("data.followUpNo", startsWith("FU-"))
                .body("data.sourceType", equalTo("客户"))
                .body("data.followUpDate", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("TC-FU-002 编号格式校验 - FU-yyyyMMdd-0001")
    void testFollowUpNoFormat() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildFollowUpBody("客户", customerId, "测试客户");
        Response r = authPost(BASE, body);
        String no = r.path("data.followUpNo");
        org.junit.jupiter.api.Assertions.assertTrue(
                no.matches("^FU-\\d{8}-\\d{4}$"),
                "跟进编号格式不正确: " + no);
    }

    @Test
    @Order(3)
    @DisplayName("TC-FU-003 创建跟进记录 - 来源=线索")
    void testCreateFollowUp_Lead() {
        Long leadId = createLeadAndGetId();
        Map<String, Object> body = buildFollowUpBody("线索", leadId, "测试线索");

        authPost(BASE, body)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.sourceType", equalTo("线索"));
    }

    @Test
    @Order(4)
    @DisplayName("TC-FU-004 创建跟进记录 - 来源=商机")
    void testCreateFollowUp_Opportunity() {
        Long customerId = createCustomerAndGetId();
        Long oppId = createOpportunityAndGetId(customerId);
        Map<String, Object> body = buildFollowUpBody("商机", oppId, "测试商机");

        authPost(BASE, body)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.sourceType", equalTo("商机"));
    }

    @Test
    @Order(5)
    @DisplayName("TC-FU-005 查询跟进记录详情")
    void testGetFollowUp() {
        Long customerId = createCustomerAndGetId();
        Long fuId = ((Number) authPost(BASE, buildFollowUpBody("客户", customerId, "测试客户"))
                .path("data.followUpId")).longValue();

        authGet(BASE + "/{id}", fuId)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.followUpId", equalTo(fuId.intValue()));
    }

    @Test
    @Order(6)
    @DisplayName("TC-FU-006 分页查询跟进记录")
    void testPageQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        authGetWithParams(BASE + "/page", params)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.records", notNullValue());
    }

    // ===================== 2. 数据联动测试 =====================

    @Test
    @Order(10)
    @DisplayName("TC-FU-010 跟进客户后 - 客户的跟进次数+1、最后联系日期更新")
    void testFollowUpCustomer_UpdatesCustomer() {
        Long customerId = createCustomerAndGetId();
        // 获取跟进前客户的跟进次数
        Response before = authGet("/api/customer/{id}", customerId);
        int countBefore = before.path("data.followUpCount") == null ? 0 : (int) before.path("data.followUpCount");

        // 创建跟进记录
        authPost(BASE, buildFollowUpBody("客户", customerId, "测试客户"))
                .then().body("code", equalTo(200));

        // 验证客户跟进次数+1、最后联系日期更新
        authGet("/api/customer/{id}", customerId)
                .then().body("data.followUpCount", equalTo(countBefore + 1))
                .body("data.lastContactDate", notNullValue());
    }

    @Test
    @Order(11)
    @DisplayName("TC-FU-011 跟进线索后 - 线索的跟进次数+1、最后跟进日期更新")
    void testFollowUpLead_UpdatesLead() {
        Long leadId = createLeadAndGetId();
        Response before = authGet("/api/crm/lead/{id}", leadId);
        int countBefore = before.path("data.followUpCount") == null ? 0 : (int) before.path("data.followUpCount");

        authPost(BASE, buildFollowUpBody("线索", leadId, "测试线索"))
                .then().body("code", equalTo(200));

        authGet("/api/crm/lead/{id}", leadId)
                .then().body("data.followUpCount", equalTo(countBefore + 1))
                .body("data.lastFollowUpDate", notNullValue());
    }

    @Test
    @Order(12)
    @DisplayName("TC-FU-012 跟进新线索后 - 状态自动从新线索变为跟进中")
    void testFollowUpLead_AutoStatusChange() {
        Long leadId = createLeadAndGetId();
        // 新创建的线索状态为"新线索"
        authGet("/api/crm/lead/{id}", leadId)
                .then().body("data.leadStatus", equalTo("新线索"));

        // 跟进一次
        authPost(BASE, buildFollowUpBody("线索", leadId, "测试线索"))
                .then().body("code", equalTo(200));

        // 状态应变为"跟进中"
        authGet("/api/crm/lead/{id}", leadId)
                .then().body("data.leadStatus", equalTo("跟进中"));
    }

    @Test
    @Order(13)
    @DisplayName("TC-FU-013 跟进商机后 - 商机的跟进次数+1、最后跟进日期更新")
    void testFollowUpOpp_UpdatesOpp() {
        Long customerId = createCustomerAndGetId();
        Long oppId = createOpportunityAndGetId(customerId);
        Response before = authGet("/api/crm/opportunity/{id}", oppId);
        int countBefore = before.path("data.followUpCount") == null ? 0 : (int) before.path("data.followUpCount");

        authPost(BASE, buildFollowUpBody("商机", oppId, "测试商机"))
                .then().body("code", equalTo(200));

        authGet("/api/crm/opportunity/{id}", oppId)
                .then().body("data.followUpCount", equalTo(countBefore + 1))
                .body("data.lastFollowUpDate", notNullValue());
    }

    // ===================== 3. 按来源查询 =====================

    @Test
    @Order(20)
    @DisplayName("TC-FU-020 按来源查询 - 客户的跟进历史")
    void testListBySource_Customer() {
        Long customerId = createCustomerAndGetId();
        // 创建2条跟进记录
        authPost(BASE, buildFollowUpBody("客户", customerId, "测试客户"));
        authPost(BASE, buildFollowUpBody("客户", customerId, "测试客户"));

        Map<String, Object> params = new HashMap<>();
        params.put("sourceType", "客户");
        params.put("sourceId", customerId);
        authGetWithParams(BASE + "/by-source", params)
                .then().statusCode(200)
                .body("code", equalTo(200));
    }

    @Test
    @Order(21)
    @DisplayName("TC-FU-021 今日待跟进任务查询")
    void testTodayFollowUps() {
        authGet(BASE + "/today")
                .then().statusCode(200)
                .body("code", equalTo(200));
    }

    // ===================== 4. 边界值与异常测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-FU-030 创建跟进 - 跟进内容为空应失败")
    void testCreateFollowUp_EmptyContent() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildFollowUpBody("客户", customerId, "测试客户");
        body.put("content", "");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(31)
    @DisplayName("TC-FU-031 创建跟进 - 来源类型非法应失败")
    void testCreateFollowUp_InvalidSourceType() {
        Long customerId = createCustomerAndGetId();
        Map<String, Object> body = buildFollowUpBody("客户", customerId, "测试客户");
        body.put("sourceType", "订单");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(32)
    @DisplayName("TC-FU-032 删除跟进记录 - 正常")
    void testDeleteFollowUp() {
        Long customerId = createCustomerAndGetId();
        Long fuId = ((Number) authPost(BASE, buildFollowUpBody("客户", customerId, "测试客户"))
                .path("data.followUpId")).longValue();

        authDelete(BASE + "/{id}", fuId)
                .then().body("code", equalTo(200));
    }

    // ===================== 5. 仪表盘测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-FU-040 仪表盘统计 - 接口可用")
    void testDashboard() {
        authGet(BASE + "/dashboard")
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.totalFollowUps", notNullValue())
                .body("data.todayTasks", notNullValue())
                .body("data.importantCount", notNullValue());
    }
}
