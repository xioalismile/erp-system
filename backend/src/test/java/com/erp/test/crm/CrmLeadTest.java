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
 * 销售线索管理模块自动化测试（文档 7.2）
 *
 * 测试范围（参照 ERP 软件测试规范）：
 *  1. 功能测试：CRUD 完整性
 *  2. 状态流转测试：新线索 → 跟进中 → 已转化/已放弃
 *  3. 业务规则测试：线索转化、分配销售员、放弃需原因
 *  4. 编码规则测试：L-yyyyMMdd-0001 格式
 *  5. 边界值测试：必填字段缺失、字段长度
 *  6. 异常测试：非法状态、重复操作
 *  7. 仪表盘统计测试
 */
@DisplayName("7.2 销售线索管理 - 自动化测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmLeadTest extends CrmTestBase {

    private static final String BASE = "/api/crm/lead";
    private static Long testLeadId;

    // ===================== 1. 功能测试：CRUD =====================

    @Test
    @Order(1)
    @DisplayName("TC-LEAD-001 创建线索 - 正常流程")
    void testCreateLead_Success() {
        Map<String, Object> body = buildLeadBody();
        Response r = authPost(BASE, body);

        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.leadId", notNullValue())
                .body("data.leadNo", startsWith("L-"))
                .body("data.leadName", equalTo(body.get("leadName")))
                .body("data.leadStatus", equalTo("新线索"))
                .body("data.intentionLevel", equalTo("A"))
                .body("data.leadType", equalTo("采购意向"))
                .body("data.assignedDate", notNullValue());

        testLeadId = ((Number) r.path("data.leadId")).longValue();
        System.out.println("[TC-LEAD-001] 创建线索成功, leadId=" + testLeadId + ", leadNo=" + r.path("data.leadNo"));
    }

    @Test
    @Order(2)
    @DisplayName("TC-LEAD-002 编号格式校验 - L-yyyyMMdd-0001")
    void testLeadNoFormat() {
        Response r = authPost(BASE, buildLeadBody());
        String leadNo = r.path("data.leadNo");

        // 校验格式: L-yyyyMMdd-4位数字
        org.junit.jupiter.api.Assertions.assertTrue(
                leadNo.matches("^L-\\d{8}-\\d{4}$"),
                "线索编号格式不正确: " + leadNo);
    }

    @Test
    @Order(3)
    @DisplayName("TC-LEAD-003 查询线索详情 - 正常")
    void testGetLead_Success() {
        Response r = authGet(BASE + "/{id}", testLeadId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.leadId", equalTo(testLeadId.intValue()))
                .body("data.leadStatus", equalTo("新线索"));
    }

    @Test
    @Order(4)
    @DisplayName("TC-LEAD-004 查询线索 - 不存在")
    void testGetLead_NotFound() {
        authGet(BASE + "/{id}", 999999L)
                .then().statusCode(200)
                .body("code", not(equalTo(200)));
    }

    @Test
    @Order(5)
    @DisplayName("TC-LEAD-005 分页查询线索列表")
    void testPageQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        authGetWithParams(BASE + "/page", params)
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.total", greaterThanOrEqualTo(1))
                .body("data.records", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("TC-LEAD-006 分页查询 - 按状态筛选")
    void testPageQuery_FilterByStatus() {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        params.put("leadStatus", "新线索");
        authGetWithParams(BASE + "/page", params)
                .then().statusCode(200)
                .body("code", equalTo(200));
        // 所有返回的记录状态都应为"新线索"
    }

    @Test
    @Order(7)
    @DisplayName("TC-LEAD-007 修改线索 - 正常")
    void testUpdateLead_Success() {
        Map<String, Object> body = buildLeadBody();
        body.put("leadName", "修改后线索名称");
        body.put("demandDesc", "更新需求描述");

        authPut(BASE + "/{id}", body, testLeadId)
                .then().statusCode(200)
                .body("code", equalTo(200));
    }

    // ===================== 2. 状态流转测试 =====================

    @Test
    @Order(10)
    @DisplayName("TC-LEAD-010 状态流转 - 新线索 → 跟进中")
    void testStatusTransition_ToFollowing() {
        Long leadId = createLeadAndGetId();
        Map<String, Object> body = new HashMap<>();
        body.put("status", "跟进中");

        authPut(BASE + "/{id}/status", body, leadId)
                .then().statusCode(200)
                .body("code", equalTo(200));

        // 验证状态已变更
        authGet(BASE + "/{id}", leadId)
                .then().body("data.leadStatus", equalTo("跟进中"));
    }

    @Test
    @Order(11)
    @DisplayName("TC-LEAD-011 状态流转 - 已转化不可再变更")
    void testStatusTransition_ConvertedCannotChange() {
        Long leadId = createLeadAndGetId();
        // 先转化为客户
        authPost(BASE + "/{id}/convert", null, leadId)
                .then().statusCode(200).body("code", equalTo(200));

        // 再尝试变更状态应失败
        Map<String, Object> body = new HashMap<>();
        body.put("status", "跟进中");
        authPut(BASE + "/{id}/status", body, leadId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(12)
    @DisplayName("TC-LEAD-012 状态流转 - 已放弃不可再变更")
    void testStatusTransition_AbandonedCannotChange() {
        Long leadId = createLeadAndGetId();
        Map<String, Object> body = new HashMap<>();
        body.put("reason", "客户明确表示无需求");
        authPut(BASE + "/{id}/abandon", body, leadId)
                .then().statusCode(200).body("code", equalTo(200));

        // 再尝试变更状态应失败
        Map<String, Object> statusBody = new HashMap<>();
        statusBody.put("status", "跟进中");
        authPut(BASE + "/{id}/status", statusBody, leadId)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 3. 业务规则测试 =====================

    @Test
    @Order(20)
    @DisplayName("TC-LEAD-020 线索转化为客户 - 正常")
    void testConvertToCustomer_Success() {
        Long leadId = createLeadAndGetId();
        Response r = authPost(BASE + "/{id}/convert", null, leadId);

        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());

        Long newCustomerId = ((Number) r.path("data")).longValue();
        org.junit.jupiter.api.Assertions.assertNotNull(newCustomerId);
        org.junit.jupiter.api.Assertions.assertTrue(newCustomerId > 0);

        // 验证线索状态已变为"已转化"
        authGet(BASE + "/{id}", leadId)
                .then().body("data.leadStatus", equalTo("已转化"))
                .body("data.convertedCustomerId", equalTo(newCustomerId.intValue()))
                .body("data.convertedDate", notNullValue());
    }

    @Test
    @Order(21)
    @DisplayName("TC-LEAD-021 线索转化 - 重复转化应失败")
    void testConvertToCustomer_Duplicate() {
        Long leadId = createLeadAndGetId();
        // 第一次转化
        authPost(BASE + "/{id}/convert", null, leadId)
                .then().body("code", equalTo(200));
        // 第二次转化应失败
        authPost(BASE + "/{id}/convert", null, leadId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(22)
    @DisplayName("TC-LEAD-022 分配销售员 - 正常")
    void testAssignSales_Success() {
        Long leadId = createLeadAndGetId();
        Map<String, Object> body = new HashMap<>();
        body.put("assignedSalesId", 1);

        authPut(BASE + "/{id}/assign", body, leadId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", leadId)
                .then().body("data.assignedSalesId", equalTo(1));
    }

    @Test
    @Order(23)
    @DisplayName("TC-LEAD-023 放弃线索 - 缺少原因应失败")
    void testAbandon_NoReason() {
        Long leadId = createLeadAndGetId();
        Map<String, Object> body = new HashMap<>();
        // 不传 reason
        authPut(BASE + "/{id}/abandon", body, leadId)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(24)
    @DisplayName("TC-LEAD-024 放弃线索 - 带原因正常")
    void testAbandon_WithReason() {
        Long leadId = createLeadAndGetId();
        Map<String, Object> body = new HashMap<>();
        body.put("reason", "客户预算不足，暂时搁置");

        authPut(BASE + "/{id}/abandon", body, leadId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet(BASE + "/{id}", leadId)
                .then().body("data.leadStatus", equalTo("已放弃"))
                .body("data.abandonReason", equalTo("客户预算不足，暂时搁置"));
    }

    // ===================== 4. 边界值与异常测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-LEAD-030 创建线索 - 线索名称为空应失败")
    void testCreateLead_EmptyName() {
        Map<String, Object> body = buildLeadBody();
        body.put("leadName", "");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(31)
    @DisplayName("TC-LEAD-031 创建线索 - 公司名称为空应失败")
    void testCreateLead_EmptyCompany() {
        Map<String, Object> body = buildLeadBody();
        body.put("companyName", "");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(32)
    @DisplayName("TC-LEAD-032 创建线索 - 来源类型非法应失败")
    void testCreateLead_InvalidSourceType() {
        Map<String, Object> body = buildLeadBody();
        body.put("sourceType", "非法来源");
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(33)
    @DisplayName("TC-LEAD-033 创建线索 - 意向等级非法应失败")
    void testCreateLead_InvalidIntentionLevel() {
        Map<String, Object> body = buildLeadBody();
        body.put("intentionLevel", "E");  // 只允许 A/B/C
        authPost(BASE, body).then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(34)
    @DisplayName("TC-LEAD-034 删除线索 - 正常")
    void testDeleteLead_Success() {
        Long leadId = createLeadAndGetId();
        authDelete(BASE + "/{id}", leadId)
                .then().statusCode(200).body("code", equalTo(200));
    }

    @Test
    @Order(35)
    @DisplayName("TC-LEAD-035 删除线索 - 不存在")
    void testDeleteLead_NotFound() {
        authDelete(BASE + "/{id}", 999999L)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 5. 仪表盘测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-LEAD-040 仪表盘统计 - 接口可用")
    void testDashboard() {
        authGet(BASE + "/dashboard")
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.totalLeads", notNullValue())
                .body("data.todayFollowUps", notNullValue())
                .body("data.statusDistribution", notNullValue());
    }
}
