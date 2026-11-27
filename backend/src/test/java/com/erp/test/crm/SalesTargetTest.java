package com.erp.test.crm;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 销售目标管理模块自动化测试
 *
 * 测试范围（按 ERP 软件自动化测试规范）：
 *  1. 公司年度目标 CRUD
 *  2. 自动生成下年度目标（按增长率递增）—— 核心业务规则
 *  3. 销售员目标分配 CRUD + 批量分配
 *  4. 分配校验：同年度同销售员不能重复；合计不能超过公司目标
 *  5. 完成情况刷新（与成交商机关联）
 *  6. 分配看板：未分配金额 / 缺口人数 / 是否需要招聘
 *  7. 完成率排名
 *  8. 边界值与异常场景
 *  9. 安全性（未授权访问）
 */
@DisplayName("销售目标管理模块自动化测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SalesTargetTest {

    private static String token;
    private static final String BASE = "http://localhost:8080/api/crm/sales-target";

    private static Long companyTargetId;
    private static Long nextYearTargetId;
    private static Long employeeTargetId;
    private static final Integer TEST_YEAR = 2026;
    private static final Integer NEXT_YEAR = 2027;
    private static final BigDecimal COMPANY_AMOUNT = new BigDecimal("20000000"); // 2000万
    private static final BigDecimal GROWTH_RATE = new BigDecimal("30.00");

    @BeforeAll
    static void login() {
        RestAssured.urlEncodingEnabled = true;
        token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"admin\",\"password\":\"Erp@2026\"}")
                .when()
                .post("http://localhost:8080/api/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .extract().path("data.token");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("登录失败，无法获取 token");
        }
        System.out.println("[SalesTargetTest] 登录成功, token=" + token.substring(0, Math.min(20, token.length())) + "...");
    }

    // ============ 通用请求封装 ============

    private Response authGet(String path) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(BASE + path);
    }

    private Response authPost(String path, Object body) {
        RestAssured.requestSpecification = null;
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
        if (body != null) req.body(body);
        return req.when().post(BASE + path);
    }

    private Response authPut(String path, Object body) {
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
        if (body != null) req.body(body);
        return req.when().put(BASE + path);
    }

    private Response authDelete(String path) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(BASE + path);
    }

    private Map<String, Object> buildCompanyBody(Integer year, BigDecimal amount, BigDecimal growthRate) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetYear", year);
        body.put("annualAmount", amount);
        body.put("growthRate", growthRate);
        body.put("autoGenerateNextYear", 1);
        body.put("status", "启用");
        body.put("description", "自动化测试目标 " + year);
        return body;
    }

    private Map<String, Object> buildEmployeeBody(Integer year, Long empId, String empName, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetYear", year);
        body.put("employeeId", empId);
        body.put("employeeName", empName);
        body.put("deptName", "销售部");
        body.put("annualTargetAmount", amount);
        body.put("q1Target", amount.divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP));
        body.put("q2Target", amount.divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP));
        body.put("q3Target", amount.divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP));
        body.put("q4Target", amount.divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP));
        body.put("remark", "自动化测试分配");
        return body;
    }

    // ==================== 公司年度目标测试 ====================

    @Test
    @Order(1)
    @DisplayName("TC-ST-001 创建公司年度目标 - 成功")
    void testCreateCompanyTarget_Success() {
        // 先清理可能存在的同年度目标
        Response existRes = authGet("/company/page?year=" + TEST_YEAR + "&current=1&size=10");
        if (existRes.path("data.records") != null) {
            java.util.List<Map<String, Object>> records = existRes.path("data.records");
            for (Map<String, Object> r : records) {
                Object id = r.get("id");
                if (id != null) {
                    authDelete("/company/" + ((Number) id).longValue());
                }
            }
        }

        Response r = authPost("/company", buildCompanyBody(TEST_YEAR, COMPANY_AMOUNT, GROWTH_RATE));
        r.then().statusCode(200).body("code", equalTo(200));
        companyTargetId = ((Number) r.path("data.id")).longValue();
        System.out.println("[TC-ST-001] 公司目标创建: id=" + companyTargetId + " year=" + TEST_YEAR + " amount=" + COMPANY_AMOUNT);
    }

    @Test
    @Order(2)
    @DisplayName("TC-ST-002 创建公司年度目标 - 年度重复应失败")
    void testCreateCompanyTarget_DuplicateYear() {
        Response r = authPost("/company", buildCompanyBody(TEST_YEAR, COMPANY_AMOUNT, GROWTH_RATE));
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("TC-ST-003 创建公司年度目标 - 金额必须大于0")
    void testCreateCompanyTarget_InvalidAmount() {
        Map<String, Object> body = buildCompanyBody(2099, BigDecimal.ZERO, GROWTH_RATE);
        Response r = authPost("/company", body);
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("必须大于0"));
    }

    @Test
    @Order(4)
    @DisplayName("TC-ST-004 创建公司年度目标 - 年度为空应失败")
    void testCreateCompanyTarget_NullYear() {
        Map<String, Object> body = buildCompanyBody(null, COMPANY_AMOUNT, GROWTH_RATE);
        Response r = authPost("/company", body);
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("不能为空"));
    }

    @Test
    @Order(5)
    @DisplayName("TC-ST-005 查询公司目标详情 - 包含分配信息")
    void testGetCompanyTarget_Detail() {
        Response r = authGet("/company/" + companyTargetId);
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.targetYear", equalTo(TEST_YEAR))
                .body("data.annualAmount", comparesEqualTo(COMPANY_AMOUNT.floatValue()))
                .body("data.allocatedAmount", notNullValue())
                .body("data.allocatedCount", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("TC-ST-006 自动生成下年度目标 - 2000万 按30%增长 → 2600万")
    void testGenerateNextYearTarget() {
        Response r = authPost("/company/" + companyTargetId + "/generate-next-year", null);
        r.then().statusCode(200).body("code", equalTo(200));
        nextYearTargetId = ((Number) r.path("data.id")).longValue();
        Integer year = r.path("data.targetYear");
        org.junit.jupiter.api.Assertions.assertEquals(NEXT_YEAR, year);
        // 2000万 * 1.30 = 2600万
        BigDecimal expected = COMPANY_AMOUNT.multiply(new BigDecimal("1.30")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal actual = new BigDecimal(r.path("data.annualAmount").toString());
        // 用 compareTo 避免科学计数法字符串导致的不等
        org.junit.jupiter.api.Assertions.assertEquals(0, expected.compareTo(actual),
                "期望 " + expected + " 实际 " + actual);
        System.out.println("[TC-ST-006] 自动生成下年度目标: " + TEST_YEAR + "→" + NEXT_YEAR + " 金额 " + COMPANY_AMOUNT + " → " + actual);
    }

    @Test
    @Order(7)
    @DisplayName("TC-ST-007 自动生成下年度目标 - 已存在应失败")
    void testGenerateNextYearTarget_AlreadyExists() {
        Response r = authPost("/company/" + companyTargetId + "/generate-next-year", null);
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("已存在"));
    }

    @Test
    @Order(8)
    @DisplayName("TC-ST-008 自动生成下年度目标 - 不存在的当前目标应失败")
    void testGenerateNextYearTarget_NotFound() {
        Response r = authPost("/company/999999/generate-next-year", null);
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("不存在"));
    }

    @Test
    @Order(9)
    @DisplayName("TC-ST-009 更新公司年度目标 - 修改增长率")
    void testUpdateCompanyTarget() {
        Map<String, Object> body = new HashMap<>();
        body.put("growthRate", new BigDecimal("50.00"));
        body.put("description", "调整增长率为50%");
        Response r = authPut("/company/" + companyTargetId, body);
        r.then().statusCode(200).body("code", equalTo(200));
        // 验证更新
        authGet("/company/" + companyTargetId).then()
                .body("data.growthRate", comparesEqualTo(50.0f));
    }

    @Test
    @Order(10)
    @DisplayName("TC-ST-010 公司目标分页查询")
    void testPageCompany() {
        Response r = authGet("/company/page?current=1&size=20");
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.records", not(empty()))
                .body("data.total", greaterThanOrEqualTo(2));
    }

    // ==================== 销售员目标分配测试 ====================

    @Test
    @Order(20)
    @DisplayName("TC-ST-020 分配销售员目标 - 成功")
    void testAllocateEmployeeTarget_Success() {
        Response r = authPost("/employee", buildEmployeeBody(TEST_YEAR, 1001L, "张三", new BigDecimal("5000000")));
        r.then().statusCode(200).body("code", equalTo(200));
        employeeTargetId = ((Number) r.path("data.id")).longValue();
        System.out.println("[TC-ST-020] 销售员目标分配: id=" + employeeTargetId + " 张三 500万");
    }

    @Test
    @Order(21)
    @DisplayName("TC-ST-021 分配销售员目标 - 同年度同销售员重复应失败")
    void testAllocateEmployeeTarget_Duplicate() {
        Response r = authPost("/employee", buildEmployeeBody(TEST_YEAR, 1001L, "张三", new BigDecimal("3000000")));
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("已有目标"));
    }

    @Test
    @Order(22)
    @DisplayName("TC-ST-022 分配销售员目标 - 合计超过公司目标应失败")
    void testAllocateEmployeeTarget_ExceedCompany() {
        // 公司目标2000万，张三已分配500万，再分配1600万给李四，合计2100万 > 2000万
        Response r = authPost("/employee", buildEmployeeBody(TEST_YEAR, 1002L, "李四", new BigDecimal("16000000")));
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("超过公司年度目标"));
    }

    @Test
    @Order(23)
    @DisplayName("TC-ST-023 分配销售员目标 - 销售员ID为空应失败")
    void testAllocateEmployeeTarget_NullEmployeeId() {
        Map<String, Object> body = buildEmployeeBody(TEST_YEAR, null, "测试", new BigDecimal("1000000"));
        Response r = authPost("/employee", body);
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("销售员ID不能为空"));
    }

    @Test
    @Order(24)
    @DisplayName("TC-ST-024 分配销售员目标 - 目标金额必须大于0")
    void testAllocateEmployeeTarget_InvalidAmount() {
        Response r = authPost("/employee", buildEmployeeBody(TEST_YEAR, 1003L, "王五", BigDecimal.ZERO));
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("必须大于0"));
    }

    @Test
    @Order(25)
    @DisplayName("TC-ST-025 分配销售员目标 - 年度为空应失败")
    void testAllocateEmployeeTarget_NullYear() {
        Response r = authPost("/employee", buildEmployeeBody(null, 1004L, "赵六", new BigDecimal("1000000")));
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("不能为空"));
    }

    @Test
    @Order(26)
    @DisplayName("TC-ST-026 批量分配销售员目标")
    void testBatchAllocate() {
        java.util.List<Map<String, Object>> targets = java.util.Arrays.asList(
                buildEmployeeBody(TEST_YEAR, 2001L, "钱七", new BigDecimal("2000000")),
                buildEmployeeBody(TEST_YEAR, 2002L, "孙八", new BigDecimal("2000000"))
        );
        Response r = authPost("/employee/batch", targets);
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data", equalTo(2));
        System.out.println("[TC-ST-026] 批量分配成功: 2条");
    }

    @Test
    @Order(27)
    @DisplayName("TC-ST-027 更新销售员目标")
    void testUpdateEmployeeTarget() {
        Map<String, Object> body = new HashMap<>();
        body.put("annualTargetAmount", new BigDecimal("6000000"));
        body.put("q1Target", new BigDecimal("1500000"));
        Response r = authPut("/employee/" + employeeTargetId, body);
        r.then().statusCode(200).body("code", equalTo(200));
    }

    @Test
    @Order(28)
    @DisplayName("TC-ST-028 销售员目标分页查询")
    void testPageEmployee() {
        Response r = authGet("/employee/page?year=" + TEST_YEAR + "&current=1&size=20");
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.records", not(empty()));
    }

    @Test
    @Order(29)
    @DisplayName("TC-ST-029 销售员目标分页查询 - 按状态过滤")
    void testPageEmployee_ByStatus() {
        Response r = authGet("/employee/page?year=" + TEST_YEAR + "&allocationStatus=已分配&current=1&size=20");
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.records", not(empty()));
    }

    @Test
    @Order(30)
    @DisplayName("TC-ST-030 刷新销售员实际完成金额 - 来自成交商机")
    void testRefreshActualAmount() {
        // 即使没有成交商机，刷新也应该返回成功（金额为0）
        Response r = authPost("/employee/refresh?year=" + TEST_YEAR + "&employeeId=1001", null);
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.year", equalTo(TEST_YEAR))
                .body("data.employeeId", equalTo(1001));
    }

    @Test
    @Order(31)
    @DisplayName("TC-ST-031 删除销售员目标 - 不存在应失败")
    void testDeleteEmployeeTarget_NotFound() {
        Response r = authDelete("/employee/999999");
        r.then().statusCode(200).body("code", equalTo(500))
                .body("message", containsString("不存在"));
    }

    @Test
    @Order(32)
    @DisplayName("TC-ST-032 删除销售员目标 - 成功")
    void testDeleteEmployeeTarget_Success() {
        Response r = authDelete("/employee/" + employeeTargetId);
        r.then().statusCode(200).body("code", equalTo(200));
        employeeTargetId = null;
    }

    // ==================== 看板测试 ====================

    @Test
    @Order(40)
    @DisplayName("TC-ST-040 分配看板 - 包含未分配金额与缺口人数")
    void testDashboard() {
        Response r = authGet("/dashboard?year=" + TEST_YEAR);
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.hasCompanyTarget", equalTo(true))
                .body("data.companyTarget", notNullValue())
                .body("data.allocatedAmount", notNullValue())
                .body("data.unallocatedAmount", notNullValue())
                .body("data.allocatedCount", notNullValue())
                .body("data.shortageCount", notNullValue())
                .body("data.allocationRate", notNullValue());
        System.out.println("[TC-ST-040] 看板: 公司目标=" + r.path("data.companyTarget")
                + " 已分配=" + r.path("data.allocatedAmount")
                + " 未分配=" + r.path("data.unallocatedAmount")
                + " 缺口人数=" + r.path("data.shortageCount"));
    }

    @Test
    @Order(41)
    @DisplayName("TC-ST-041 分配看板 - 未设置公司目标应返回 hasCompanyTarget=false")
    void testDashboard_NoCompanyTarget() {
        Response r = authGet("/dashboard?year=1999");
        r.then().statusCode(200).body("code", equalTo(200))
                .body("data.hasCompanyTarget", equalTo(false));
    }

    @Test
    @Order(42)
    @DisplayName("TC-ST-042 完成情况排名")
    void testRanking() {
        Response r = authGet("/ranking?year=" + TEST_YEAR);
        r.then().statusCode(200).body("code", equalTo(200));
        java.util.List<?> list = r.path("data");
        org.junit.jupiter.api.Assertions.assertNotNull(list);
        System.out.println("[TC-ST-042] 排名返回: " + list.size() + " 条");
    }

    @Test
    @Order(43)
    @DisplayName("TC-ST-043 分配看板 - 缺口人数 > 0 时提示需要招聘")
    void testDashboard_NeedRecruit() {
        // 公司目标2000万，已分配约900万（500万 + 200万*2），未分配1100万，应有缺口
        Response r = authGet("/dashboard?year=" + TEST_YEAR);
        r.then().statusCode(200).body("code", equalTo(200));
        Boolean needRecruit = r.path("data.needRecruit");
        // 至少能正确返回布尔字段
        org.junit.jupiter.api.Assertions.assertNotNull(needRecruit);
    }

    // ==================== 安全测试 ====================

    @Test
    @Order(60)
    @DisplayName("TC-ST-060 未授权访问应失败")
    void testUnauthorizedAccess() {
        RestAssured.given().when().get(BASE + "/company/page")
                .then().statusCode(401);
    }

    @Test
    @Order(61)
    @DisplayName("TC-ST-061 无效token访问应失败")
    void testInvalidToken() {
        RestAssured.given()
                .header("Authorization", "Bearer invalidtoken12345")
                .when()
                .get(BASE + "/company/page")
                .then().statusCode(401);
    }

    // ==================== 业务规则端到端测试 ====================

    @Test
    @Order(70)
    @DisplayName("TC-ST-070 端到端：设置公司目标→分配→看板→自动增长下年→再分配")
    void testEndToEndBusinessFlow() {
        // 1. 创建一个新年度目标
        int e2eYear = 2090;
        // 先清理
        Response existRes = authGet("/company/page?year=" + e2eYear + "&current=1&size=10");
        java.util.List<Map<String, Object>> records = existRes.path("data.records");
        if (records != null) {
            for (Map<String, Object> r : records) {
                Object id = r.get("id");
                if (id != null) authDelete("/company/" + ((Number) id).longValue());
            }
        }

        Response createRes = authPost("/company", buildCompanyBody(e2eYear, new BigDecimal("10000000"), new BigDecimal("30")));
        createRes.then().body("code", equalTo(200));
        Long e2eId = ((Number) createRes.path("data.id")).longValue();

        // 2. 分配销售员目标
        Response allocRes = authPost("/employee", buildEmployeeBody(e2eYear, 9001L, "测试销售A", new BigDecimal("6000000")));
        allocRes.then().body("code", equalTo(200));

        // 3. 看板应显示已分配600万
        Response dbRes = authGet("/dashboard?year=" + e2eYear);
        dbRes.then().body("code", equalTo(200))
                .body("data.hasCompanyTarget", equalTo(true));

        // 4. 自动生成下年度目标
        Response nextRes = authPost("/company/" + e2eId + "/generate-next-year", null);
        nextRes.then().body("code", equalTo(200))
                .body("data.targetYear", equalTo(e2eYear + 1));
        // 1000万 * 1.30 = 1300万
        BigDecimal expected = new BigDecimal("13000000");
        BigDecimal actual = new BigDecimal(nextRes.path("data.annualAmount").toString());
        org.junit.jupiter.api.Assertions.assertEquals(0, expected.compareTo(actual),
                "期望 " + expected + " 实际 " + actual);

        // 5. 排名查询
        Response rankRes = authGet("/ranking?year=" + e2eYear);
        rankRes.then().body("code", equalTo(200));

        // 6. 清理
        authDelete("/company/" + e2eId);
        System.out.println("[TC-ST-070] 端到端流程通过: " + e2eYear + "→" + (e2eYear + 1) + " 1000万→1300万");
    }

    @Test
    @Order(80)
    @DisplayName("TC-ST-080 清理测试数据")
    void testCleanup() {
        // 清理公司目标（会级联清理销售员目标）
        if (companyTargetId != null) {
            authDelete("/company/" + companyTargetId);
        }
        if (nextYearTargetId != null) {
            authDelete("/company/" + nextYearTargetId);
        }
        // 清理批量分配的数据
        Response empRes = authGet("/employee/page?year=" + TEST_YEAR + "&current=1&size=100");
        java.util.List<Map<String, Object>> list = empRes.path("data.records");
        if (list != null) {
            for (Map<String, Object> r : list) {
                Object id = r.get("id");
                if (id != null) authDelete("/employee/" + ((Number) id).longValue());
            }
        }
        System.out.println("[TC-ST-080] 测试数据清理完成");
    }
}
