package com.erp.test.crm;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CRM 模块 - 基础资料下拉选择关联字段专项测试
 *
 * 测试目标：
 *  验证 CRM 单据中所有依赖基础资料下拉选择的关联字段在保存后能正确存储，
 *  并在查询详情时能正确回显（验证字段值一致性）。
 *
 * 覆盖场景（参照标准 ERP 自动化测试规范）：
 *  1. 客户下拉选择 → 商机/投诉/调查的 customerId/customerName 存储与回显
 *  2. 销售员下拉选择 → 线索/商机/投诉/客户档案的 assignedSalesId 存储与回显
 *  3. 部门下拉选择 → 商机的 assignedDeptId 存储与回显
 *  4. 处理人下拉选择 → 投诉的 assignedHandlerId 存储与回显
 *  5. 付款条件下拉 → 客户档案的 paymentTermId 存储与回显
 *  6. 币种下拉 → 客户档案的 currencyId 存储与回显
 *  7. 价格等级下拉 → 客户档案的 defaultPriceLevelId 存储与回显
 *  8. 跨表关联一致性：下拉选项 API 与保存后查询的一致性
 *
 * 此测试对应前端页面如下拉选择后是否能正确存储并显示的场景。
 */
@DisplayName("CRM - 基础资料下拉选择关联字段专项测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrmBasedataAssociationTest extends CrmTestBase {

    // 缓存基础资料ID，从实际数据库读取
    private static Long existCustomerId;
    private static String existCustomerName;
    private static Long existEmployeeId;
    private static String existEmployeeName;
    private static Long existDeptId;
    private static String existDeptName;
    private static Long existPaymentTermId;
    private static Long existCurrencyId;
    private static Long existPriceLevelId;

    // 测试中创建的CRM单据ID
    private static Long testLeadId;
    private static Long testOpportunityId;
    private static Long testComplaintId;
    private static Long testSurveyId;
    private static Long testCrmCustomerId;

    // ===================== 0. 准备：加载基础资料数据 =====================

    @Test
    @Order(0)
    @DisplayName("TC-ASSOC-000 准备 - 加载基础资料数据")
    void testPrepareBasedata() {
        // 加载客户列表
        Response custResp = authGet("/api/basedata/customer/list");
        custResp.then().statusCode(200).body("code", equalTo(200));
        List<Map<String, Object>> customers = custResp.path("data");
        assertNotNull(customers, "客户列表不应为null");
        assertFalse(customers.isEmpty(), "客户列表不应为空（基础资料应有数据）");
        existCustomerId = ((Number) customers.get(0).get("customerId")).longValue();
        existCustomerName = (String) customers.get(0).get("customerName");
        System.out.println("[Prepare] 使用客户: id=" + existCustomerId + ", name=" + existCustomerName);

        // 加载员工列表
        Response empResp = authGet("/api/basedata/employee/list");
        empResp.then().statusCode(200).body("code", equalTo(200));
        List<Map<String, Object>> employees = empResp.path("data");
        assertNotNull(employees, "员工列表不应为null");
        assertFalse(employees.isEmpty(), "员工列表不应为空");
        existEmployeeId = ((Number) employees.get(0).get("employeeId")).longValue();
        existEmployeeName = (String) employees.get(0).get("employeeName");
        System.out.println("[Prepare] 使用员工: id=" + existEmployeeId + ", name=" + existEmployeeName);

        // 加载部门列表
        Response deptResp = authGet("/api/basedata/department/list");
        deptResp.then().statusCode(200).body("code", equalTo(200));
        List<Map<String, Object>> depts = deptResp.path("data");
        assertNotNull(depts, "部门列表不应为null");
        assertFalse(depts.isEmpty(), "部门列表不应为空");
        existDeptId = ((Number) depts.get(0).get("deptId")).longValue();
        existDeptName = (String) depts.get(0).get("deptName");
        System.out.println("[Prepare] 使用部门: id=" + existDeptId + ", name=" + existDeptName);

        // 加载付款条件
        Response ptResp = authGet("/api/basedata/payment-term/list");
        ptResp.then().statusCode(200).body("code", equalTo(200));
        List<Map<String, Object>> pts = ptResp.path("data");
        if (pts != null && !pts.isEmpty()) {
            existPaymentTermId = ((Number) pts.get(0).get("paymentTermId")).longValue();
            System.out.println("[Prepare] 使用付款条件: id=" + existPaymentTermId);
        }

        // 加载币种
        Response curResp = authGet("/api/basedata/currency/list");
        curResp.then().statusCode(200).body("code", equalTo(200));
        List<Map<String, Object>> curs = curResp.path("data");
        if (curs != null && !curs.isEmpty()) {
            existCurrencyId = ((Number) curs.get(0).get("currencyId")).longValue();
            System.out.println("[Prepare] 使用币种: id=" + existCurrencyId);
        }

        // 加载价格等级
        Response plResp = authGet("/api/basedata/price-level/list");
        plResp.then().statusCode(200).body("code", equalTo(200));
        List<Map<String, Object>> pls = plResp.path("data");
        if (pls != null && !pls.isEmpty()) {
            existPriceLevelId = ((Number) pls.get(0).get("priceLevelId")).longValue();
            System.out.println("[Prepare] 使用价格等级: id=" + existPriceLevelId);
        }
    }

    // ===================== 1. 销售线索 - assignedSalesId 测试 =====================

    @Test
    @Order(10)
    @DisplayName("TC-ASSOC-010 线索 - 销售员下拉选择存储正确")
    void testLead_AssignedSalesId_Store() {
        Map<String, Object> body = buildLeadBody();
        body.put("assignedSalesId", existEmployeeId);

        Response r = authPost("/api/crm/lead", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.assignedDate", notNullValue());

        testLeadId = ((Number) r.path("data.leadId")).longValue();
        System.out.println("[TC-ASSOC-010] 线索销售员存储成功: leadId=" + testLeadId);
    }

    @Test
    @Order(11)
    @DisplayName("TC-ASSOC-011 线索 - 销售员下拉选择回显一致")
    void testLead_AssignedSalesId_Echo() {
        Response r = authGet("/api/crm/lead/{id}", testLeadId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.leadStatus", equalTo("新线索"));

        // 验证销售员可以与基础资料中的员工匹配
        Map<String, Object> leadData = r.path("data");
        Long storedSalesId = ((Number) leadData.get("assignedSalesId")).longValue();
        assertEquals(existEmployeeId, storedSalesId, "线索的销售员ID应与下拉选择的员工ID一致");
    }

    @Test
    @Order(12)
    @DisplayName("TC-ASSOC-012 线索 - 修改销售员后存储正确")
    void testLead_AssignedSalesId_Update() {
        // 加载另一个员工
        Response empResp = authGet("/api/basedata/employee/list");
        List<Map<String, Object>> employees = empResp.path("data");
        Long secondEmployeeId = existEmployeeId;
        if (employees.size() > 1) {
            secondEmployeeId = ((Number) employees.get(1).get("employeeId")).longValue();
        }

        // 通过 assign 接口更新
        Map<String, Object> body = new HashMap<>();
        body.put("assignedSalesId", secondEmployeeId);

        authPut("/api/crm/lead/{id}/assign", body, testLeadId)
                .then().statusCode(200).body("code", equalTo(200));

        // 验证回显
        authGet("/api/crm/lead/{id}", testLeadId)
                .then().body("data.assignedSalesId", equalTo(secondEmployeeId.intValue()));
    }

    // ===================== 2. 商机 - customerId/assignedSalesId/assignedDeptId 测试 =====================

    @Test
    @Order(20)
    @DisplayName("TC-ASSOC-020 商机 - 客户下拉选择存储正确")
    void testOpportunity_CustomerId_Store() {
        Map<String, Object> body = buildOpportunityBody(existCustomerId);
        body.put("customerName", existCustomerName);
        body.put("assignedSalesId", existEmployeeId);
        body.put("assignedDeptId", existDeptId);

        Response r = authPost("/api/crm/opportunity", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.assignedDeptId", equalTo(existDeptId.intValue()));

        testOpportunityId = ((Number) r.path("data.opportunityId")).longValue();
        System.out.println("[TC-ASSOC-020] 商机关联存储成功: oppId=" + testOpportunityId);
    }

    @Test
    @Order(21)
    @DisplayName("TC-ASSOC-021 商机 - 客户/销售员/部门下拉选择回显一致")
    void testOpportunity_AllFields_Echo() {
        Response r = authGet("/api/crm/opportunity/{id}", testOpportunityId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.assignedDeptId", equalTo(existDeptId.intValue()));

        // 验证关联字段与基础资料一致
        Map<String, Object> oppData = r.path("data");
        assertEquals(existCustomerId, ((Number) oppData.get("customerId")).longValue());
        assertEquals(existEmployeeId, ((Number) oppData.get("assignedSalesId")).longValue());
        assertEquals(existDeptId, ((Number) oppData.get("assignedDeptId")).longValue());
        assertEquals(existCustomerName, oppData.get("customerName"));
    }

    @Test
    @Order(22)
    @DisplayName("TC-ASSOC-022 商机 - 列表查询关联字段显示正确")
    void testOpportunity_PageQuery_FieldsDisplay() {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        params.put("customerId", existCustomerId);

        Response r = authGetWithParams("/api/crm/opportunity/page", params);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.total", greaterThanOrEqualTo(1));

        List<Map<String, Object>> records = r.path("data.records");
        boolean found = false;
        for (Map<String, Object> rec : records) {
            Long recId = ((Number) rec.get("opportunityId")).longValue();
            if (recId.equals(testOpportunityId)) {
                assertEquals(existCustomerId, ((Number) rec.get("customerId")).longValue(),
                        "列表中客户ID应与下拉选择一致");
                assertEquals(existCustomerName, rec.get("customerName"),
                        "列表中客户名称应与下拉选择一致");
                found = true;
                break;
            }
        }
        assertTrue(found, "应能在列表中找到刚创建的商机记录");
    }

    // ===================== 3. 客户投诉 - customerId/assignedHandlerId 测试 =====================

    @Test
    @Order(30)
    @DisplayName("TC-ASSOC-030 投诉 - 客户和处理人下拉选择存储正确")
    void testComplaint_CustomerAndHandler_Store() {
        Map<String, Object> body = buildComplaintBody(existCustomerId, existCustomerName);
        body.put("assignedHandlerId", existEmployeeId);

        Response r = authPost("/api/crm/complaint", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.assignedHandlerId", equalTo(existEmployeeId.intValue()))
                .body("data.handleStatus", equalTo("新建"))
                .body("data.severityLevel", equalTo("中"));

        testComplaintId = ((Number) r.path("data.complaintId")).longValue();
        System.out.println("[TC-ASSOC-030] 投诉关联存储成功: complaintId=" + testComplaintId);
    }

    @Test
    @Order(31)
    @DisplayName("TC-ASSOC-031 投诉 - 客户和处理人下拉选择回显一致")
    void testComplaint_CustomerAndHandler_Echo() {
        Response r = authGet("/api/crm/complaint/{id}", testComplaintId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.assignedHandlerId", equalTo(existEmployeeId.intValue()));

        Map<String, Object> data = r.path("data");
        assertEquals(existCustomerId, ((Number) data.get("customerId")).longValue());
        assertEquals(existEmployeeId, ((Number) data.get("assignedHandlerId")).longValue());
        assertEquals(existCustomerName, data.get("customerName"));
    }

    @Test
    @Order(32)
    @DisplayName("TC-ASSOC-032 投诉 - assign接口修改处理人后回显正确")
    void testComplaint_AssignHandler_Update() {
        Map<String, Object> body = new HashMap<>();
        body.put("handlerId", existEmployeeId);

        authPut("/api/crm/complaint/{id}/assign", body, testComplaintId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/crm/complaint/{id}", testComplaintId)
                .then().body("data.assignedHandlerId", equalTo(existEmployeeId.intValue()))
                .body("data.handleStatus", equalTo("处理中"));
    }

    // ===================== 4. 满意度调查 - customerId 测试 =====================

    @Test
    @Order(40)
    @DisplayName("TC-ASSOC-040 调查 - 客户下拉选择存储正确")
    void testSurvey_CustomerId_Store() {
        Map<String, Object> body = buildSurveyBody(existCustomerId, existCustomerName);

        Response r = authPost("/api/crm/survey", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.status", equalTo("草稿"));

        testSurveyId = ((Number) r.path("data.surveyId")).longValue();
        System.out.println("[TC-ASSOC-040] 调查关联存储成功: surveyId=" + testSurveyId);
    }

    @Test
    @Order(41)
    @DisplayName("TC-ASSOC-041 调查 - 客户下拉选择回显一致")
    void testSurvey_CustomerId_Echo() {
        Response r = authGet("/api/crm/survey/{id}", testSurveyId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName));

        Map<String, Object> data = r.path("data");
        assertEquals(existCustomerId, ((Number) data.get("customerId")).longValue());
        assertEquals(existCustomerName, data.get("customerName"));
    }

    // ===================== 5. CRM客户档案 - 多个下拉字段测试 =====================

    @Test
    @Order(50)
    @DisplayName("TC-ASSOC-050 客户档案 - 销售员/付款条件/币种/价格等级下拉选择存储正确")
    void testCrmCustomer_AllDropdowns_Store() {
        Map<String, Object> body = buildCrmCustomerBody();
        body.put("assignedSalesId", existEmployeeId);
        body.put("defaultSalerId", existEmployeeId);
        if (existPaymentTermId != null) body.put("paymentTermId", existPaymentTermId);
        if (existCurrencyId != null) body.put("currencyId", existCurrencyId);
        if (existPriceLevelId != null) body.put("defaultPriceLevelId", existPriceLevelId);

        Response r = authPost("/api/customer", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.defaultSalerId", equalTo(existEmployeeId.intValue()));

        if (existPaymentTermId != null) {
            r.then().body("data.paymentTermId", equalTo(existPaymentTermId.intValue()));
        }
        if (existCurrencyId != null) {
            r.then().body("data.currencyId", equalTo(existCurrencyId.intValue()));
        }
        if (existPriceLevelId != null) {
            r.then().body("data.defaultPriceLevelId", equalTo(existPriceLevelId.intValue()));
        }

        testCrmCustomerId = ((Number) r.path("data.customerId")).longValue();
        System.out.println("[TC-ASSOC-050] 客户档案多下拉存储成功: customerId=" + testCrmCustomerId);
    }

    @Test
    @Order(51)
    @DisplayName("TC-ASSOC-051 客户档案 - 所有下拉字段回显一致")
    void testCrmCustomer_AllDropdowns_Echo() {
        Response r = authGet("/api/customer/{id}", testCrmCustomerId);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.defaultSalerId", equalTo(existEmployeeId.intValue()));

        Map<String, Object> data = r.path("data");
        assertEquals(existEmployeeId, ((Number) data.get("assignedSalesId")).longValue());
        assertEquals(existEmployeeId, ((Number) data.get("defaultSalerId")).longValue());

        if (existPaymentTermId != null) {
            assertEquals(existPaymentTermId, ((Number) data.get("paymentTermId")).longValue());
        }
        if (existCurrencyId != null) {
            assertEquals(existCurrencyId, ((Number) data.get("currencyId")).longValue());
        }
        if (existPriceLevelId != null) {
            assertEquals(existPriceLevelId, ((Number) data.get("defaultPriceLevelId")).longValue());
        }
    }

    @Test
    @Order(52)
    @DisplayName("TC-ASSOC-052 客户档案 - 修改销售员后存储正确")
    void testCrmCustomer_UpdateSales() {
        Response empResp = authGet("/api/basedata/employee/list");
        List<Map<String, Object>> employees = empResp.path("data");
        Long secondEmployeeId = existEmployeeId;
        if (employees.size() > 1) {
            secondEmployeeId = ((Number) employees.get(1).get("employeeId")).longValue();
        }

        Map<String, Object> body = buildCrmCustomerBody();
        body.put("customerId", testCrmCustomerId);
        body.put("customerCode", "TCUPD" + System.currentTimeMillis());
        body.put("customerName", uniqueCustomerName());
        body.put("assignedSalesId", secondEmployeeId);
        body.put("defaultSalerId", secondEmployeeId);

        authPut("/api/customer/{id}", body, testCrmCustomerId)
                .then().statusCode(200).body("code", equalTo(200));

        authGet("/api/customer/{id}", testCrmCustomerId)
                .then().body("data.assignedSalesId", equalTo(secondEmployeeId.intValue()))
                .body("data.defaultSalerId", equalTo(secondEmployeeId.intValue()));
    }

    // ===================== 6. 跨表关联一致性测试 =====================

    @Test
    @Order(60)
    @DisplayName("TC-ASSOC-060 跨表 - 客户下拉选项与基础资料一致")
    void testCrossTable_CustomerOptions() {
        // 1. 查询基础资料客户列表
        Response baseResp = authGet("/api/basedata/customer/list");
        List<Map<String, Object>> baseCustomers = baseResp.path("data");

        // 2. 商机/投诉/调查都使用同一个 customer/list API，确保数据一致
        for (Map<String, Object> c : baseCustomers) {
            assertNotNull(c.get("customerId"), "客户ID不应为null");
            assertNotNull(c.get("customerName"), "客户名称不应为null");
        }

        // 3. 用基础资料的客户ID创建商机，验证关联一致性
        if (!baseCustomers.isEmpty()) {
            Long cId = ((Number) baseCustomers.get(0).get("customerId")).longValue();
            String cName = (String) baseCustomers.get(0).get("customerName");

            Map<String, Object> body = buildOpportunityBody(cId);
            body.put("customerName", cName);

            authPost("/api/crm/opportunity", body)
                    .then().statusCode(200)
                    .body("code", equalTo(200))
                    .body("data.customerId", equalTo(cId.intValue()))
                    .body("data.customerName", equalTo(cName));
        }
    }

    @Test
    @Order(61)
    @DisplayName("TC-ASSOC-061 跨表 - 销售员下拉选项与基础资料一致")
    void testCrossTable_EmployeeOptions() {
        // 验证线索/商机/投诉/客户档案使用的销售员下拉都来自同一API
        Response empResp = authGet("/api/basedata/employee/list");
        List<Map<String, Object>> employees = empResp.path("data");

        // 用同一员工ID在不同单据中关联
        Long empId = ((Number) employees.get(0).get("employeeId")).longValue();

        // 线索
        Map<String, Object> leadBody = buildLeadBody();
        leadBody.put("assignedSalesId", empId);
        authPost("/api/crm/lead", leadBody)
                .then().body("data.assignedSalesId", equalTo(empId.intValue()));

        // 商机
        Map<String, Object> oppBody = buildOpportunityBody(existCustomerId);
        oppBody.put("assignedSalesId", empId);
        authPost("/api/crm/opportunity", oppBody)
                .then().body("data.assignedSalesId", equalTo(empId.intValue()));
    }

    // ===================== 7. 异常场景测试 =====================

    @Test
    @Order(70)
    @DisplayName("TC-ASSOC-070 商机 - 不存在的客户ID应失败")
    void testOpportunity_InvalidCustomerId() {
        Map<String, Object> body = buildOpportunityBody(999999L);
        body.put("customerName", "不存在的客户");
        authPost("/api/crm/opportunity", body)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(71)
    @DisplayName("TC-ASSOC-071 投诉 - 不存在的客户ID应失败")
    void testComplaint_InvalidCustomerId() {
        Map<String, Object> body = buildComplaintBody(999999L, "不存在的客户");
        authPost("/api/crm/complaint", body)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(72)
    @DisplayName("TC-ASSOC-072 线索 - 不存在的销售员ID应失败")
    void testLead_InvalidSalesId() {
        Map<String, Object> body = buildLeadBody();
        body.put("assignedSalesId", 999999L);
        authPost("/api/crm/lead", body)
                .then().body("code", not(equalTo(200)));
    }

    @Test
    @Order(73)
    @DisplayName("TC-ASSOC-073 客户档案 - 不存在的付款条件ID应失败")
    void testCustomer_InvalidPaymentTermId() {
        Map<String, Object> body = buildCrmCustomerBody();
        body.put("paymentTermId", 999999L);
        authPost("/api/customer", body)
                .then().body("code", not(equalTo(200)));
    }

    // ===================== 8. 完整字段保存测试 =====================

    @Test
    @Order(80)
    @DisplayName("TC-ASSOC-080 线索 - 全字段保存并回显")
    void testLead_AllFields_StoreAndEcho() {
        Map<String, Object> body = new HashMap<>();
        body.put("leadName", "全字段测试线索-" + System.currentTimeMillis());
        body.put("companyName", "全字段测试公司-" + System.currentTimeMillis());
        body.put("contactPerson", "测试联系人");
        body.put("contactPhone", genPhone());
        body.put("contactEmail", "test@example.com");
        body.put("contactPosition", "采购经理");
        body.put("sourceType", "线上广告");
        body.put("sourceDetail", "百度推广");
        body.put("leadType", "采购意向");
        body.put("industry", "IT");
        body.put("companyScale", "100-500人");
        body.put("demandDesc", "客户需要采购100套ERP系统");
        body.put("estimatedAmount", new BigDecimal("888888.88"));
        body.put("intentionLevel", "A");
        body.put("assignedSalesId", existEmployeeId);
        body.put("nextFollowUpDate", java.time.LocalDate.now().plusDays(3).toString());
        body.put("remark", "测试备注-全字段保存");

        Response r = authPost("/api/crm/lead", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.leadId", notNullValue())
                .body("data.leadNo", startsWith("L-"))
                .body("data.leadName", equalTo(body.get("leadName")))
                .body("data.companyName", equalTo(body.get("companyName")))
                .body("data.contactPerson", equalTo("测试联系人"))
                .body("data.contactPhone", equalTo(body.get("contactPhone")))
                .body("data.contactEmail", equalTo("test@example.com"))
                .body("data.contactPosition", equalTo("采购经理"))
                .body("data.sourceType", equalTo("线上广告"))
                .body("data.sourceDetail", equalTo("百度推广"))
                .body("data.leadType", equalTo("采购意向"))
                .body("data.industry", equalTo("IT"))
                .body("data.companyScale", equalTo("100-500人"))
                .body("data.intentionLevel", equalTo("A"))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.leadStatus", equalTo("新线索"))
                .body("data.remark", equalTo("测试备注-全字段保存"));

        Long leadId = ((Number) r.path("data.leadId")).longValue();

        // 查询详情验证所有字段
        Response detail = authGet("/api/crm/lead/{id}", leadId);
        detail.then().statusCode(200)
                .body("data.leadName", equalTo(body.get("leadName")))
                .body("data.contactEmail", equalTo("test@example.com"))
                .body("data.contactPosition", equalTo("采购经理"))
                .body("data.sourceDetail", equalTo("百度推广"))
                .body("data.demandDesc", equalTo("客户需要采购100套ERP系统"))
                .body("data.estimatedAmount", equalTo(888888.88f))
                .body("data.companyScale", equalTo("100-500人"));

        System.out.println("[TC-ASSOC-080] 全字段保存成功: leadId=" + leadId);
    }

    @Test
    @Order(81)
    @DisplayName("TC-ASSOC-081 商机 - 全字段保存并回显")
    void testOpportunity_AllFields_StoreAndEcho() {
        Map<String, Object> body = new HashMap<>();
        body.put("opportunityName", "全字段测试商机-" + System.currentTimeMillis());
        body.put("customerId", existCustomerId);
        body.put("customerName", existCustomerName);
        body.put("contactPerson", "测试联系人");
        body.put("contactPhone", genPhone());
        body.put("productCategory", "ERP软件");
        body.put("productDetail", "智能ERP系统完整版+实施服务");
        body.put("estimatedAmount", new BigDecimal("1280000.00"));
        body.put("expectedCloseDate", java.time.LocalDate.now().plusDays(60).toString());
        body.put("salesStage", "初步接触");
        body.put("competitor", "金蝶,用友");
        body.put("assignedSalesId", existEmployeeId);
        body.put("assignedDeptId", existDeptId);
        body.put("nextFollowUpDate", java.time.LocalDate.now().plusDays(5).toString());
        body.put("remark", "全字段商机备注");

        Response r = authPost("/api/crm/opportunity", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.opportunityId", notNullValue())
                .body("data.opportunityNo", startsWith("OPP-"))
                .body("data.opportunityName", equalTo(body.get("opportunityName")))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.contactPerson", equalTo("测试联系人"))
                .body("data.productCategory", equalTo("ERP软件"))
                .body("data.productDetail", equalTo("智能ERP系统完整版+实施服务"))
                .body("data.estimatedAmount", equalTo(1280000.00f))
                .body("data.probability", equalTo(10))
                .body("data.salesStage", equalTo("初步接触"))
                .body("data.competitor", equalTo("金蝶,用友"))
                .body("data.assignedSalesId", equalTo(existEmployeeId.intValue()))
                .body("data.assignedDeptId", equalTo(existDeptId.intValue()))
                .body("data.isClosed", equalTo(0));

        Long oppId = ((Number) r.path("data.opportunityId")).longValue();
        System.out.println("[TC-ASSOC-081] 商机全字段保存成功: oppId=" + oppId);
    }

    @Test
    @Order(82)
    @DisplayName("TC-ASSOC-082 投诉 - 全字段保存并回显")
    void testComplaint_AllFields_StoreAndEcho() {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", existCustomerId);
        body.put("customerName", existCustomerName);
        body.put("contactPerson", "投诉联系人");
        body.put("contactPhone", genPhone());
        body.put("complaintDate", java.time.LocalDate.now().toString());
        body.put("complaintType", "产品质量");
        body.put("complaintChannel", "电话");
        body.put("complaintContent", "产品在运输过程中出现破损，需要退换货处理。");
        body.put("severityLevel", "高");
        body.put("assignedHandlerId", existEmployeeId);
        body.put("remark", "投诉备注-加急处理");

        Response r = authPost("/api/crm/complaint", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.complaintId", notNullValue())
                .body("data.complaintNo", startsWith("C-"))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.contactPerson", equalTo("投诉联系人"))
                .body("data.complaintType", equalTo("产品质量"))
                .body("data.complaintChannel", equalTo("电话"))
                .body("data.severityLevel", equalTo("高"))
                .body("data.assignedHandlerId", equalTo(existEmployeeId.intValue()))
                .body("data.handleStatus", equalTo("新建"));

        Long complaintId = ((Number) r.path("data.complaintId")).longValue();

        // 详情验证
        authGet("/api/crm/complaint/{id}", complaintId)
                .then().statusCode(200)
                .body("data.complaintContent", equalTo("产品在运输过程中出现破损，需要退换货处理。"))
                .body("data.remark", equalTo("投诉备注-加急处理"));

        System.out.println("[TC-ASSOC-082] 投诉全字段保存成功: complaintId=" + complaintId);
    }

    @Test
    @Order(83)
    @DisplayName("TC-ASSOC-083 调查 - 全字段保存并回显")
    void testSurvey_AllFields_StoreAndEcho() {
        Map<String, Object> body = new HashMap<>();
        body.put("surveyName", "全字段满意度调查-" + System.currentTimeMillis());
        body.put("surveyType", "交易后");
        body.put("customerId", existCustomerId);
        body.put("customerName", existCustomerName);
        body.put("contactPerson", "调查联系人");
        body.put("surveyDate", java.time.LocalDate.now().toString());
        body.put("surveyMethod", "电话");
        body.put("overallScore", 5);
        body.put("productScore", 5);
        body.put("serviceScore", 4);
        body.put("priceScore", 4);
        body.put("deliveryScore", 5);
        body.put("afterSalesScore", 4);
        body.put("praiseContent", "产品质量优秀，交货及时，服务态度良好");
        body.put("criticismContent", "价格略高于竞品");
        body.put("improvementPlan", "提供更多优惠方案");
        body.put("remark", "调查备注");

        Response r = authPost("/api/crm/survey", body);
        r.then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.surveyId", notNullValue())
                .body("data.surveyNo", startsWith("SUR-"))
                .body("data.surveyName", equalTo(body.get("surveyName")))
                .body("data.surveyType", equalTo("交易后"))
                .body("data.customerId", equalTo(existCustomerId.intValue()))
                .body("data.customerName", equalTo(existCustomerName))
                .body("data.contactPerson", equalTo("调查联系人"))
                .body("data.surveyMethod", equalTo("电话"))
                .body("data.overallScore", equalTo(5))
                .body("data.productScore", equalTo(5))
                .body("data.serviceScore", equalTo(4))
                .body("data.priceScore", equalTo(4))
                .body("data.deliveryScore", equalTo(5))
                .body("data.afterSalesScore", equalTo(4))
                .body("data.status", equalTo("草稿"));

        Long surveyId = ((Number) r.path("data.surveyId")).longValue();

        // 详情验证
        authGet("/api/crm/survey/{id}", surveyId)
                .then().statusCode(200)
                .body("data.praiseContent", equalTo("产品质量优秀，交货及时，服务态度良好"))
                .body("data.criticismContent", equalTo("价格略高于竞品"))
                .body("data.improvementPlan", equalTo("提供更多优惠方案"));

        System.out.println("[TC-ASSOC-083] 调查全字段保存成功: surveyId=" + surveyId);
    }
}
