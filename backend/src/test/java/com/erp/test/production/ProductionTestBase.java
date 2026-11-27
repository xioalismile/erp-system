package com.erp.test.production;

import com.erp.test.TestEnv;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产管理模块自动化测试基类
 *
 * 职责：
 *  1. 登录获取 JWT token（前置条件）
 *  2. 封装带鉴权的 HTTP 请求（GET/POST/PUT/DELETE）
 *  3. 提供测试数据生成器（保证用例隔离、避免编码冲突）
 *  4. 提供各子模块的创建工厂方法，返回新建实体ID
 *
 * 设计原则（参照市面 ERP 软件自动化测试规范）：
 *  - 用例之间数据隔离，每个用例生成独立编码
 *  - 编号生成使用时间戳 + 自增序号，避免并发冲突
 *  - 所有请求默认带 Authorization 头
 *  - 状态流转端点带 X-Operator 头
 *
 * 对应文档第五部分生产管理模块 5.1-5.8
 */
public abstract class ProductionTestBase {

    protected static String token;
    /** 目标环境地址（系统属性 erp.test.base-url / 环境变量 ERP_TEST_BASE_URL） */
    protected static final String BASE_URL = TestEnv.baseUrl();

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
        System.out.println("[ProductionTestBase] 登录成功, token=" + token.substring(0, Math.min(20, token.length())) + "...");
    }

    // ===================== 请求封装 =====================

    protected Response authGet(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .when()
                .get(path, pathParams);
    }

    protected Response authGetWithParams(String path, Map<String, ?> params) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .queryParams(params)
                .when()
                .get(path);
    }

    protected Response authPost(String path, Object body, Object... pathParams) {
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().post(path, pathParams);
    }

    protected Response authPut(String path, Object body, Object... pathParams) {
        io.restassured.specification.RequestSpecification req = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
                .contentType(ContentType.JSON);
        if (body != null) {
            req.body(body);
        }
        return req.when().put(path, pathParams);
    }

    protected Response authDelete(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", TestEnv.operatorName())
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

    /** 生成唯一编码后缀 */
    protected static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis()) + SEQ.incrementAndGet();
    }

    /** 生成唯一 BOM 编码 */
    protected static String uniqueBomCode() {
        return "BOM-T-" + uniqueSuffix();
    }

    /** 生成唯一工作中心编码 */
    protected static String uniqueWorkCenterCode() {
        return "WC-T-" + uniqueSuffix();
    }

    /** 生成唯一工艺路线编码 */
    protected static String uniqueRoutingCode() {
        return "RT-T-" + uniqueSuffix();
    }

    /** 生成唯一工位编码 */
    protected static String uniqueWorkstationCode() {
        return "WS-T-" + uniqueSuffix();
    }

    /** 生成唯一生产工单编码（如果不通过 generate-no 接口） */
    protected static String uniqueWorkOrderNo() {
        return "MO-T-" + uniqueSuffix();
    }

    /** 生成唯一工序汇报单编码 */
    protected static String uniqueReportNo() {
        return "OR-T-" + uniqueSuffix();
    }

    /** 生成唯一委外加工单编码 */
    protected static String uniqueSubcontractNo() {
        return "SUB-T-" + uniqueSuffix();
    }

    /** 生成唯一 MRP 方案编码 */
    protected static String uniqueMrpPlanCode() {
        return "MRP-T-" + uniqueSuffix();
    }

    /** 生成唯一生产入库单编码 */
    protected static String uniqueReceiptNo() {
        return "PR-T-" + uniqueSuffix();
    }

    // ===================== 实体构造工厂 =====================

    /** 构造 BOM 创建请求体（5.1 BOM物料清单） */
    protected Map<String, Object> buildBomBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("bomCode", uniqueBomCode());
        body.put("bomName", "测试BOM-" + uniqueSuffix());
        body.put("bomVersion", "V1.0");
        body.put("materialId", 1);
        body.put("materialCode", "MAT-001");
        body.put("materialName", "测试物料A");
        body.put("quantity", new BigDecimal("1"));
        body.put("bomType", "标准BOM");
        body.put("isPrimary", 1);
        body.put("isEnabled", 1);
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造工作中心创建请求体（5.3 工作中心） */
    protected Map<String, Object> buildWorkCenterBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("workCenterCode", uniqueWorkCenterCode());
        body.put("workCenterName", "测试工作中心-" + uniqueSuffix());
        body.put("workCenterType", "生产线");
        body.put("capacityPerDay", new BigDecimal("100"));
        body.put("capacityUnit", "件");
        body.put("laborRate", new BigDecimal("50"));
        body.put("overheadRate", new BigDecimal("30"));
        body.put("status", "启用");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造工艺路线创建请求体（5.2 工艺路线） */
    protected Map<String, Object> buildRoutingBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("routingCode", uniqueRoutingCode());
        body.put("routingName", "测试工艺路线-" + uniqueSuffix());
        body.put("routingVersion", "V1.0");
        body.put("materialId", 1);
        body.put("materialCode", "MAT-001");
        body.put("materialName", "测试物料A");
        body.put("isPrimary", 1);
        body.put("isEnabled", 1);
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造工位创建请求体（5.3 工位） */
    protected Map<String, Object> buildWorkstationBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("workstationCode", uniqueWorkstationCode());
        body.put("workstationName", "测试工位-" + uniqueSuffix());
        body.put("workCenterId", 1);
        body.put("workstationType", "生产工位");
        body.put("status", "启用");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造生产工单创建请求体（5.4 生产工单） */
    protected Map<String, Object> buildWorkOrderBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", uniqueWorkOrderNo());
        body.put("orderDate", LocalDate.now().toString());
        body.put("materialId", 1);
        body.put("materialCode", "MAT-001");
        body.put("materialName", "测试物料A");
        body.put("quantity", new BigDecimal("100"));
        body.put("unitId", 1);
        body.put("warehouseId", 1);
        body.put("planStartDate", LocalDate.now().toString());
        body.put("planEndDate", LocalDate.now().plusDays(7).toString());
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造工序汇报单创建请求体（5.6 工序汇报单） */
    protected Map<String, Object> buildOperationReportBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("reportNo", uniqueReportNo());
        body.put("reportDate", LocalDate.now().toString());
        body.put("productionOrderNo", "MO-TEST-001");
        body.put("processNo", "10");
        body.put("workCenterId", 1);
        body.put("operationName", "工序1-加工");
        body.put("employeeId", 1);
        body.put("employeeName", "测试员工");
        body.put("reportType", "工序汇报");
        body.put("totalQuantity", new BigDecimal("100"));
        body.put("qualifiedQuantity", new BigDecimal("95"));
        body.put("rejectedQuantity", new BigDecimal("5"));
        body.put("workHours", new BigDecimal("8"));
        body.put("shiftType", "白班");
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造委外加工单创建请求体（5.7 委外加工单） */
    protected Map<String, Object> buildSubcontractBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("billNo", uniqueSubcontractNo());
        body.put("orderDate", LocalDate.now().toString());
        body.put("materialId", 1);
        body.put("materialCode", "MAT-001");
        body.put("materialName", "测试物料A");
        body.put("qty", new BigDecimal("100"));
        body.put("supplierId", 1);
        body.put("supplierName", "测试供应商");
        body.put("unitPrice", new BigDecimal("10"));
        body.put("totalPrice", new BigDecimal("1000"));
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造 MRP 运算方案创建请求体（5.8 MRP运算方案） */
    protected Map<String, Object> buildMrpPlanBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("planCode", uniqueMrpPlanCode());
        body.put("planName", "测试MRP方案-" + uniqueSuffix());
        body.put("planDate", LocalDate.now().toString());
        body.put("planType", "MPS/MRP");
        body.put("planHorizon", 30);
        body.put("demandType", "综合");
        body.put("includeSo", 1);
        body.put("includeSafetyStock", 1);
        body.put("includeOpenPo", 1);
        body.put("includeOpenMo", 1);
        body.put("includeOnhandStock", 1);
        body.put("timeFenceDays", 7);
        body.put("lotSizeType", "逐批法");
        body.put("orderType", "采购申请");
        body.put("planStatus", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    /** 构造生产入库单创建请求体 */
    protected Map<String, Object> buildProductionReceiptBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("receiptNo", uniqueReceiptNo());
        body.put("receiptDate", LocalDate.now().toString());
        body.put("warehouseId", 1);
        body.put("productionOrderId", 1);
        body.put("productionOrderNo", "MO-TEST-001");
        body.put("totalQuantity", new BigDecimal("100"));
        body.put("qualifiedQuantity", new BigDecimal("95"));
        body.put("rejectedQuantity", new BigDecimal("5"));
        body.put("status", "草稿");
        body.put("remark", "自动化测试创建");
        return body;
    }

    // ===================== 业务辅助方法：创建并返回 ID =====================

    /** 创建 BOM 并返回 bomId */
    protected Long createBomAndGetId() {
        Response r = authPost("/api/masterdata/bom", buildBomBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.bomId")).longValue();
    }

    /** 创建工作中心并返回 workCenterId
     *  注意：WorkCenterController.create 返回 Result<Void>，不返回实体
     *  需要先创建再查列表获取 ID */
    protected Long createWorkCenterAndGetId() {
        Map<String, Object> body = buildWorkCenterBody();
        String code = (String) body.get("workCenterCode");
        Response r = authPost("/api/production/work-center", body);
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        // 创建后查询列表获取 ID
        Response listResp = authGetWithParams("/api/production/work-center/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        listResp.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        // 找到匹配的记录
        java.util.List<Map<String, Object>> records = listResp.path("data.records");
        if (records != null) {
            for (Map<String, Object> rec : records) {
                if (code.equals(rec.get("workCenterCode"))) {
                    return ((Number) rec.get("workCenterId")).longValue();
                }
            }
        }
        throw new IllegalStateException("创建工作中心后未找到记录, code=" + code);
    }

    /** 创建工艺路线并返回 routingId */
    protected Long createRoutingAndGetId() {
        Response r = authPost("/api/production/routings", buildRoutingBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.routingId")).longValue();
    }

    /** 创建工位并返回 workstationId
     *  注意：WorkstationController.save 返回 Result<Void>，不返回实体 */
    protected Long createWorkstationAndGetId() {
        Map<String, Object> body = buildWorkstationBody();
        String code = (String) body.get("workstationCode");
        Response r = authPost("/api/production/workstation", body);
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        // 创建后查询列表获取 ID
        Response listResp = authGetWithParams("/api/production/workstation/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        listResp.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        java.util.List<Map<String, Object>> records = listResp.path("data.records");
        if (records != null) {
            for (Map<String, Object> rec : records) {
                if (code.equals(rec.get("workstationCode"))) {
                    return ((Number) rec.get("workstationId")).longValue();
                }
            }
        }
        throw new IllegalStateException("创建工位后未找到记录, code=" + code);
    }

    /** 创建生产工单并返回 orderId
     *  注意：ProductionController.create 返回 Result<Void>，不返回实体 */
    protected Long createWorkOrderAndGetId() {
        Map<String, Object> body = buildWorkOrderBody();
        String orderNo = (String) body.get("orderNo");
        Response r = authPost("/api/production/workorders", body);
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        // 创建后查询列表获取 ID
        Response listResp = authGetWithParams("/api/production/workorders",
                Map.of("current", 1, "size", 50, "keyword", orderNo));
        listResp.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        java.util.List<Map<String, Object>> records = listResp.path("data.records");
        if (records != null) {
            for (Map<String, Object> rec : records) {
                if (orderNo.equals(rec.get("orderNo"))) {
                    return ((Number) rec.get("orderId")).longValue();
                }
            }
        }
        throw new IllegalStateException("创建生产工单后未找到记录, orderNo=" + orderNo);
    }

    /** 创建工序汇报单并返回 reportId */
    protected Long createOperationReportAndGetId() {
        Response r = authPost("/api/production/operation-report", buildOperationReportBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.reportId")).longValue();
    }

    /** 创建委外加工单并返回 subcontractId */
    protected Long createSubcontractAndGetId() {
        Response r = authPost("/api/production/subcontract", buildSubcontractBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.subcontractId")).longValue();
    }

    /** 创建 MRP 方案并返回 planId */
    protected Long createMrpPlanAndGetId() {
        Response r = authPost("/api/production/mrp-plan", buildMrpPlanBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.planId")).longValue();
    }

    /** 创建生产入库单并返回 receiptId */
    protected Long createProductionReceiptAndGetId() {
        Response r = authPost("/api/production/receipts", buildProductionReceiptBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.receiptId")).longValue();
    }

    // ===================== 通用断言辅助 =====================

    /** 断言操作成功（code=200） */
    protected void assertSuccess(Response r, String operation) {
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        System.out.println("  [PASS] " + operation);
    }

    /** 断言操作失败（code!=200） */
    protected void assertFailed(Response r, String operation) {
        int code = r.path("code");
        if (code != 200) {
            System.out.println("  [PASS] " + operation + " (预期失败, code=" + code + ")");
        } else {
            throw new AssertionError("预期操作应失败但成功了: " + operation);
        }
    }

    /** 断言状态值 */
    protected void assertStatus(Response r, String expectedStatus) {
        String actualStatus = r.path("data.status");
        if (actualStatus == null) {
            actualStatus = r.path("data.planStatus");
        }
        org.junit.jupiter.api.Assertions.assertEquals(expectedStatus, actualStatus,
                "状态不匹配，期望: " + expectedStatus + "，实际: " + actualStatus);
    }
}
