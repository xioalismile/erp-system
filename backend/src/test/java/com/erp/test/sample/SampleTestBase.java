package com.erp.test.sample;

import com.erp.test.TestEnv;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 打样管理模块自动化测试基类
 *
 * 职责：
 *  1. 登录获取 JWT token（前置条件）
 *  2. 封装带鉴权的 HTTP 请求（GET/POST/PUT/DELETE）
 *  3. 提供测试数据生成器（保证用例隔离、避免编码冲突）
 *  4. 提供打样单创建工厂方法，返回新建实体ID
 *
 * 对应文档第五部分补充：打样管理模块 5.9 打样单
 */
public abstract class SampleTestBase {

    protected static String token;
    /** 目标环境地址（系统属性 erp.test.base-url / 环境变量 ERP_TEST_BASE_URL） */
    protected static final String BASE_URL = TestEnv.baseUrl();

    /** 预设基础资料 ID：@BeforeAll 时按目标租户库动态解析（独立租户库里未必存在写死的 1） */
    protected static Long CUSTOMER_ID = 1L;
    protected static String CUSTOMER_NAME = "测试客户";
    protected static Long MATERIAL_ID = 1L;
    protected static String MATERIAL_CODE = "MAT-001";
    protected static String MATERIAL_NAME = "测试物料A";
    protected static Long MATERIAL_ID_2 = 2L;
    protected static String MATERIAL_CODE_2 = "MAT-002";
    protected static String MATERIAL_NAME_2 = "测试物料B";
    protected static Long UNIT_ID = 1L;
    protected static Long DEPARTMENT_ID = 1L;
    protected static Long ENGINEER_ID = 1L;

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
        System.out.println("[SampleTestBase] 登录成功, token=" + token.substring(0, Math.min(20, token.length())) + "...");

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

    private static String strOf(Object o) {
        return (o == null || o.toString().isEmpty()) ? null : o.toString();
    }

    private static void warn(String msg) {
        System.out.println("[SampleTestBase] 警告: " + msg);
    }

    /**
     * 动态解析基础资料 ID（参照 ProductionFieldTestBase 的做法）。
     * 查不到记录时保留默认值并打印警告，不抛异常中断。
     */
    private static void loadReferenceIds() {
        List<Map<String, Object>> customers = listData("/api/basedata/customer/list");
        if (!customers.isEmpty()) {
            Long id = idOf(customers.get(0), "customerId", "id");
            if (id != null) CUSTOMER_ID = id;
            String name = strOf(customers.get(0).get("customerName"));
            if (name != null) CUSTOMER_NAME = name;
        } else {
            warn("客户列表为空，CUSTOMER_ID/CUSTOMER_NAME 沿用默认值");
        }

        List<Map<String, Object>> materials = listData("/api/basedata/material/list");
        if (!materials.isEmpty()) {
            Long id1 = idOf(materials.get(0), "materialId", "id");
            if (id1 != null) MATERIAL_ID = id1;
            String code1 = strOf(materials.get(0).get("materialCode"));
            String name1 = strOf(materials.get(0).get("materialName"));
            if (code1 != null) MATERIAL_CODE = code1;
            if (name1 != null) MATERIAL_NAME = name1;
            if (materials.size() > 1) {
                Long id2 = idOf(materials.get(1), "materialId", "id");
                if (id2 != null) MATERIAL_ID_2 = id2;
                String code2 = strOf(materials.get(1).get("materialCode"));
                String name2 = strOf(materials.get(1).get("materialName"));
                if (code2 != null) MATERIAL_CODE_2 = code2;
                if (name2 != null) MATERIAL_NAME_2 = name2;
            } else {
                warn("物料不足 2 条，MATERIAL_ID_2/MATERIAL_CODE_2/MATERIAL_NAME_2 沿用默认值");
            }
        } else {
            warn("物料列表为空，MATERIAL_ID/MATERIAL_CODE/MATERIAL_NAME 沿用默认值");
        }

        List<Map<String, Object>> units = listData("/api/basedata/unit/list");
        if (!units.isEmpty()) {
            Long unitId = idOf(units.get(0), "unitId", "id");
            if (unitId != null) UNIT_ID = unitId;
        } else {
            warn("单位列表为空，UNIT_ID 沿用默认值 " + UNIT_ID);
        }

        List<Map<String, Object>> depts = listData("/api/basedata/department/list");
        if (!depts.isEmpty()) {
            Long deptId = idOf(depts.get(0), "deptId", "departmentId", "id");
            if (deptId != null) DEPARTMENT_ID = deptId;
        } else {
            warn("部门列表为空，DEPARTMENT_ID 沿用默认值 " + DEPARTMENT_ID);
        }

        List<Map<String, Object>> employees = listData("/api/basedata/employee/list");
        if (!employees.isEmpty()) {
            Long empId = idOf(employees.get(0), "employeeId", "id");
            if (empId != null) ENGINEER_ID = empId;
        } else {
            warn("员工列表为空，ENGINEER_ID 沿用默认值 " + ENGINEER_ID);
        }

        System.out.println("[SampleTestBase] 基础资料: customer=" + CUSTOMER_ID + "/" + CUSTOMER_NAME
                + " material1=" + MATERIAL_ID + "/" + MATERIAL_CODE + " material2=" + MATERIAL_ID_2 + "/" + MATERIAL_CODE_2
                + " unit=" + UNIT_ID + " department=" + DEPARTMENT_ID + " engineer=" + ENGINEER_ID);
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

    protected Response noAuthGet(String path) {
        return RestAssured.given().when().get(path);
    }

    protected Response noAuthPost(String path, Object body) {
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().post(path);
    }

    // ===================== 测试数据生成器 =====================

    protected static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis()) + SEQ.incrementAndGet();
    }

    // ===================== 实体构造工厂 =====================

    /** 构造打样单创建请求体（含子件清单） */
    protected Map<String, Object> buildSampleBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("sampleName", "测试样品-" + uniqueSuffix());
        body.put("sampleType", "新产品");
        body.put("sampleStage", "原样");
        body.put("customerId", CUSTOMER_ID);
        body.put("customerName", CUSTOMER_NAME);
        body.put("contactPerson", "测试联系人");
        body.put("contactPhone", "13800138000");
        body.put("materialId", MATERIAL_ID);
        body.put("materialCode", MATERIAL_CODE);
        body.put("materialName", MATERIAL_NAME);
        body.put("specification", "规格A");
        body.put("unitId", UNIT_ID);
        body.put("sampleQty", new BigDecimal("5"));
        body.put("demandDate", LocalDate.now().toString());
        body.put("planFinishDate", LocalDate.now().plusDays(7).toString());
        body.put("departmentId", DEPARTMENT_ID);
        body.put("engineerId", ENGINEER_ID);
        body.put("priority", "普通");
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");

        // 子件清单
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("childMaterialId", MATERIAL_ID);
        item1.put("childMaterialCode", MATERIAL_CODE);
        item1.put("childMaterialName", MATERIAL_NAME);
        item1.put("specification", "规格A");
        item1.put("unitId", UNIT_ID);
        item1.put("baseQty", new BigDecimal("2"));
        item1.put("qtyType", "固定");
        item1.put("scrapRate", new BigDecimal("0"));
        item1.put("supplyType", "领料");
        item1.put("isKeyPart", 1);
        item1.put("unitPrice", new BigDecimal("10"));
        item1.put("amount", new BigDecimal("20"));
        item1.put("remark", "子件1");
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("childMaterialId", MATERIAL_ID_2);
        item2.put("childMaterialCode", MATERIAL_CODE_2);
        item2.put("childMaterialName", MATERIAL_NAME_2);
        item2.put("specification", "规格B");
        item2.put("unitId", UNIT_ID);
        item2.put("baseQty", new BigDecimal("3"));
        item2.put("qtyType", "固定");
        item2.put("scrapRate", new BigDecimal("5"));
        item2.put("supplyType", "领料");
        item2.put("isKeyPart", 0);
        item2.put("unitPrice", new BigDecimal("8"));
        item2.put("amount", new BigDecimal("24"));
        item2.put("remark", "子件2");
        items.add(item2);

        body.put("items", items);
        return body;
    }

    /** 创建打样单并返回 sampleId */
    protected Long createSampleAndGetId() {
        Response r = authPost("/api/sample/orders", buildSampleBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.sampleId")).longValue();
    }

    // ===================== 通用断言辅助 =====================

    protected void assertSuccess(Response r, String operation) {
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        System.out.println("  [PASS] " + operation);
    }

    protected void assertFailed(Response r, String operation) {
        int code = r.path("code");
        if (code != 200) {
            System.out.println("  [PASS] " + operation + " (预期失败, code=" + code + ")");
        } else {
            throw new AssertionError("预期操作应失败但成功了: " + operation);
        }
    }
}
