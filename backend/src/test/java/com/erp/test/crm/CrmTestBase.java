package com.erp.test.crm;

import com.erp.test.TestEnv;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRM 模块自动化测试基类
 *
 * 职责：
 *  1. 登录获取 JWT token（前置条件）
 *  2. 封装带鉴权的 HTTP 请求（GET/POST/PUT/DELETE）
 *  3. 提供测试数据生成器（保证用例隔离、避免编码冲突）
 *  4. 提供通用断言辅助方法
 *
 * 设计原则（参照市面 ERP 软件自动化测试规范）：
 *  - 用例之间数据隔离，每个用例生成独立编码
 *  - 编号生成使用时间戳 + 自增序号，避免并发冲突
 *  - 所有请求默认带 Authorization 头
 */
public abstract class CrmTestBase {

    protected static String token;
    /** 目标环境地址（系统属性 erp.test.base-url / 环境变量 ERP_TEST_BASE_URL） */
    protected static final String BASE_URL = TestEnv.baseUrl();

    /** 预设基础资料 ID：@BeforeAll 时按目标租户库动态解析（独立租户库里未必存在写死的 1） */
    protected static Long ASSIGNED_SALES_ID = 1L;
    protected static Long ASSIGNED_DEPT_ID = 1L;
    protected static Long ASSIGNED_HANDLER_ID = 1L;

    /** 自增序号，保证同毫秒生成的编码不冲突 */
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    @org.junit.jupiter.api.BeforeAll
    static void loginAndGetToken() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.urlEncodingEnabled = true;

        // 登录 → 选租户（三步闸门）；禁止平台超管 admin（其 token 无 tenantId，业务请求会落主库 erp_db）
        token = TestEnv.loginAndSelectTenant(TestEnv.tenantAdminUser(), TestEnv.tenantAdminPassword());

        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("登录失败，无法获取 token，请确认后端已启动 (" + BASE_URL + ")");
        }
        System.out.println("[CrmTestBase] 登录成功, token=" + token.substring(0, Math.min(20, token.length())) + "...");

        loadReferenceIds();
    }

    // ===================== 基础资料动态解析 =====================

    /** 查询基础资料列表（兼容 data 直接是数组 / data.records 两种返回；失败返回空列表，不中断测试） */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listData(String path) {
        try {
            Response r = RestAssured.given()
                    .header("Authorization", "Bearer " + token)
                    .when().get(path);
            if (r.statusCode() != 200) {
                warn(path + " 返回 HTTP " + r.statusCode() + "，相关基础资料 ID 沿用默认值");
                return List.of();
            }
            Object data = r.jsonPath().get("data");
            if (data instanceof List) return (List<Map<String, Object>>) data;
            Object records = r.jsonPath().get("data.records");
            if (records instanceof List) return (List<Map<String, Object>>) records;
            return List.of();
        } catch (Exception e) {
            warn(path + " 查询异常 " + e.getMessage() + "，相关基础资料 ID 沿用默认值");
            return List.of();
        }
    }

    /** 从记录中按候选字段名取 ID，取不到返回 null */
    private static Long idOf(Map<String, Object> rec, String... keys) {
        if (rec == null) return null;
        for (String k : keys) {
            Object v = rec.get(k);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v != null && !v.toString().isEmpty()) {
                try {
                    return Long.parseLong(v.toString());
                } catch (NumberFormatException ignore) {
                    // 非数字字段，继续尝试下一个候选键
                }
            }
        }
        return null;
    }

    private static void warn(String msg) {
        System.out.println("[CrmTestBase] 警告: " + msg);
    }

    /**
     * 动态解析基础资料 ID（参照 ProductionFieldTestBase 的做法）。
     * 查不到记录时保留默认值并打印警告，不抛异常中断。
     */
    private static void loadReferenceIds() {
        List<Map<String, Object>> employees = listData("/api/basedata/employee/list");
        if (!employees.isEmpty()) {
            Long empId = idOf(employees.get(0), "employeeId", "id");
            if (empId != null) {
                ASSIGNED_SALES_ID = empId;
                ASSIGNED_HANDLER_ID = empId;
            }
        } else {
            warn("员工列表为空，ASSIGNED_SALES_ID/ASSIGNED_HANDLER_ID 沿用默认值 " + ASSIGNED_SALES_ID);
        }

        List<Map<String, Object>> depts = listData("/api/basedata/department/list");
        if (!depts.isEmpty()) {
            Long deptId = idOf(depts.get(0), "deptId", "departmentId", "id");
            if (deptId != null) ASSIGNED_DEPT_ID = deptId;
        } else {
            warn("部门列表为空，ASSIGNED_DEPT_ID 沿用默认值 " + ASSIGNED_DEPT_ID);
        }

        System.out.println("[CrmTestBase] 基础资料: assignedSalesId=" + ASSIGNED_SALES_ID
                + " assignedDeptId=" + ASSIGNED_DEPT_ID + " assignedHandlerId=" + ASSIGNED_HANDLER_ID);
    }

    // ===================== 请求封装 =====================

    protected Response authGet(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(path, pathParams);
    }

    protected Response authGetWithParams(String path, Map<String, ?> params) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .queryParams(params)
                .when()
                .get(path);
    }

    protected Response authPost(String path, Object body, Object... pathParams) {
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().post(path, pathParams);
    }

    protected Response authPut(String path, Object body, Object... pathParams) {
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().put(path, pathParams);
    }

    protected Response authDelete(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(path, pathParams);
    }

    /** 无 token 请求（用于权限测试） */
    protected Response noAuthGet(String path) {
        return RestAssured.given().when().get(path);
    }

    protected Response noAuthPost(String path, Object body) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(path);
    }

    // ===================== 测试数据生成器 =====================

    /** 生成唯一线索名称 */
    protected static String uniqueLeadName() {
        return "测试线索-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
    }

    /** 生成唯一公司名称 */
    protected static String uniqueCompanyName() {
        return "测试公司-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
    }

    /** 生成唯一商机名称 */
    protected static String uniqueOppName() {
        return "测试商机-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
    }

    /** 生成唯一客户编码 */
    protected static String uniqueCustomerCode() {
        return "TC" + System.currentTimeMillis() + SEQ.incrementAndGet();
    }

    /** 生成唯一客户名称 */
    protected static String uniqueCustomerName() {
        return "测试客户-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
    }

    /** 生成合法手机号 */
    protected static String genPhone() {
        return "138" + String.format("%08d", System.currentTimeMillis() % 100000000);
    }

    /** 生成随机金额 */
    protected static BigDecimal genAmount() {
        return new BigDecimal(10000 + (System.currentTimeMillis() % 90000)).setScale(2);
    }

    // ===================== 实体构造工厂 =====================

    /** 构造线索创建请求体（仅必填字段，可被覆盖） */
    protected Map<String, Object> buildLeadBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("leadName", uniqueLeadName());
        body.put("companyName", uniqueCompanyName());
        body.put("contactPerson", "张三");
        body.put("contactPhone", genPhone());
        body.put("sourceType", "线上广告");
        body.put("leadType", "采购意向");
        body.put("industry", "IT");
        body.put("intentionLevel", "A");
        body.put("estimatedAmount", genAmount());
        body.put("assignedSalesId", ASSIGNED_SALES_ID);
        return body;
    }

    /** 构造商机创建请求体 */
    protected Map<String, Object> buildOpportunityBody(Long customerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("opportunityName", uniqueOppName());
        body.put("customerId", customerId);
        body.put("customerName", "测试客户");
        body.put("estimatedAmount", genAmount());
        body.put("expectedCloseDate", LocalDate.now().plusDays(30).toString());
        body.put("assignedSalesId", ASSIGNED_SALES_ID);
        body.put("assignedDeptId", ASSIGNED_DEPT_ID);
        return body;
    }

    /** 构造跟进记录请求体 */
    protected Map<String, Object> buildFollowUpBody(String sourceType, Long sourceId, String sourceName) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        body.put("sourceName", sourceName);
        body.put("followUpType", "电话");
        body.put("contactPerson", "李四");
        body.put("content", "电话沟通客户需求，客户表示对产品很感兴趣，希望进一步了解方案细节和报价。");
        body.put("summary", "客户对产品感兴趣");
        body.put("result", "需要进一步跟进");
        body.put("nextFollowUpDate", LocalDate.now().plusDays(7).toString());
        return body;
    }

    /** 构造投诉请求体 */
    protected Map<String, Object> buildComplaintBody(Long customerId, String customerName) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("customerName", customerName);
        body.put("contactPerson", "王五");
        body.put("contactPhone", genPhone());
        body.put("complaintType", "服务态度");
        body.put("complaintChannel", "电话");
        body.put("complaintContent", "客服响应速度过慢，多次电话无人接听，希望改进服务流程提升响应效率。");
        body.put("severityLevel", "中");
        body.put("assignedHandlerId", ASSIGNED_HANDLER_ID);
        return body;
    }

    /** 构造满意度调查请求体 */
    protected Map<String, Object> buildSurveyBody(Long customerId, String customerName) {
        Map<String, Object> body = new HashMap<>();
        body.put("surveyName", "客户满意度调查-" + System.currentTimeMillis());
        body.put("surveyType", "交易后");
        body.put("customerId", customerId);
        body.put("customerName", customerName);
        body.put("contactPerson", "赵六");
        body.put("surveyMethod", "电话");
        body.put("overallScore", 4);
        body.put("productScore", 4);
        body.put("serviceScore", 3);
        body.put("priceScore", 4);
        body.put("deliveryScore", 5);
        body.put("afterSalesScore", 4);
        body.put("praiseContent", "产品质量不错，交货及时");
        body.put("criticismContent", "售后服务有待改进");
        return body;
    }

    /** 构造 CRM 客户创建请求体（用于测试前置数据） */
    protected Map<String, Object> buildCrmCustomerBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("customerCode", uniqueCustomerCode());
        body.put("customerName", uniqueCustomerName());
        body.put("customerType", "企业客户");
        body.put("customerLevel", "普通客户");
        body.put("contactPerson", "测试联系人");
        body.put("contactPhone", genPhone());
        body.put("crmStatus", "意向客户");
        body.put("industry", "IT");
        return body;
    }

    // ===================== 业务辅助方法 =====================

    /** 创建 CRM 客户并返回 customerId */
    protected Long createCustomerAndGetId() {
        Response r = authPost("/api/customer", buildCrmCustomerBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.customerId")).longValue();
    }

    /** 创建线索并返回 leadId */
    protected Long createLeadAndGetId() {
        Response r = authPost("/api/crm/lead", buildLeadBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.leadId")).longValue();
    }

    /** 创建商机并返回 opportunityId */
    protected Long createOpportunityAndGetId(Long customerId) {
        Response r = authPost("/api/crm/opportunity", buildOpportunityBody(customerId));
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.opportunityId")).longValue();
    }
}
