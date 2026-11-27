package com.erp.test.production;

import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产模块字段完整性测试基类
 *
 * 在 ProductionTestBase 基础上扩展：
 *  1. 启动时动态查询基础资料（物料/部门/员工/仓库/供应商/单位/币种），获取真实 ID
 *  2. 提供"完整字段"构造工厂（覆盖实体所有业务字段，而非最小必填字段）
 *  3. 提供字段逐一断言辅助方法（验证保存后查询返回的字段值与输入一致，即前端显示正常的先决条件）
 *
 * 设计目标：模拟用户在前端页面通过下拉选择基础资料 + 输入所有字段后保存的场景，
 *          验证保存是否正常、关联字段是否正确存储、查询返回是否完整。
 */
public abstract class ProductionFieldTestBase extends ProductionTestBase {

    /** 基础资料引用（@BeforeAll 时动态加载，避免硬编码 ID） */
    protected static Long refMaterialId;
    protected static String refMaterialCode;
    protected static String refMaterialName;
    protected static String refMaterialSpec;
    protected static Long refMaterialId2;        // 第二个物料（用于 BOM 子件/委外发料）
    protected static String refMaterialCode2;
    protected static String refMaterialName2;

    protected static Long refUnitId;
    protected static String refUnitName;
    protected static Long refDepartmentId;
    protected static String refDepartmentName;
    protected static Long refEmployeeId;
    protected static String refEmployeeName;
    protected static Long refWarehouseId;
    protected static String refWarehouseName;
    protected static Long refSupplierId;
    protected static String refSupplierName;
    protected static Long refCurrencyId;
    protected static String refCurrencyCode;

    @org.junit.jupiter.api.BeforeAll
    static void loadReferenceData() {
        // 本方法名与父类 loginAndGetToken 不同，父类 @BeforeAll 已先执行登录；此处保持原有行为再登录一次，
        // 确保 token 一定对应当前配置的目标环境与租户（禁止平台超管 admin：其 token 无 tenantId 会写主库）
        io.restassured.RestAssured.baseURI = BASE_URL;
        io.restassured.RestAssured.urlEncodingEnabled = true;
        token = com.erp.test.TestEnv.loginAndSelectTenant(
                com.erp.test.TestEnv.tenantAdminUser(), com.erp.test.TestEnv.tenantAdminPassword());
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("登录失败，无法获取 token，请确认后端已启动 (" + BASE_URL + ")");
        }
        System.out.println("[ProductionFieldTestBase] 登录成功, token=" + token.substring(0, Math.min(20, token.length())) + "...");

        System.out.println("[ProductionFieldTestBase] 开始加载基础资料...");

        io.restassured.specification.RequestSpecification authReq = io.restassured.RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .header("X-Operator", com.erp.test.TestEnv.operatorName());

        // 物料 - 取前 2 条
        List<Map<String, Object>> materials = authReq.when().get("/api/basedata/material/list")
                .then().statusCode(200).extract().path("data");
        if (materials == null || materials.size() < 2) {
            throw new IllegalStateException("基础资料-物料数据不足，至少需要 2 条，实际: " + (materials == null ? 0 : materials.size()));
        }
        Map<String, Object> m1 = materials.get(0);
        Map<String, Object> m2 = materials.get(1);
        refMaterialId = toLong(m1.get("materialId"));
        refMaterialCode = (String) m1.get("materialCode");
        refMaterialName = (String) m1.get("materialName");
        refMaterialSpec = (String) m1.getOrDefault("specification", "");
        refMaterialId2 = toLong(m2.get("materialId"));
        refMaterialCode2 = (String) m2.get("materialCode");
        refMaterialName2 = (String) m2.get("materialName");
        System.out.println("  物料1: id=" + refMaterialId + ", code=" + refMaterialCode + ", name=" + refMaterialName);
        System.out.println("  物料2: id=" + refMaterialId2 + ", code=" + refMaterialCode2 + ", name=" + refMaterialName2);

        // 单位
        List<Map<String, Object>> units = authReq.when().get("/api/basedata/unit/list")
                .then().statusCode(200).extract().path("data");
        refUnitId = toLong(units.get(0).get("unitId"));
        refUnitName = (String) units.get(0).get("unitName");
        System.out.println("  单位: id=" + refUnitId + ", name=" + refUnitName);

        // 部门（注意：API 返回字段名为 deptId/deptName，而非 departmentId/departmentName）
        List<Map<String, Object>> depts = authReq.when().get("/api/basedata/department/list")
                .then().statusCode(200).extract().path("data");
        refDepartmentId = toLong(depts.get(0).get("deptId"));
        refDepartmentName = (String) depts.get(0).get("deptName");
        System.out.println("  部门: id=" + refDepartmentId + ", name=" + refDepartmentName);

        // 员工
        List<Map<String, Object>> emps = authReq.when().get("/api/basedata/employee/list")
                .then().statusCode(200).extract().path("data");
        refEmployeeId = toLong(emps.get(0).get("employeeId"));
        refEmployeeName = (String) emps.get(0).get("employeeName");
        System.out.println("  员工: id=" + refEmployeeId + ", name=" + refEmployeeName);

        // 仓库（注意：API 返回字段名为 stockId/stockName，而非 warehouseId/warehouseName）
        List<Map<String, Object>> whs = authReq.when().get("/api/basedata/warehouse/list")
                .then().statusCode(200).extract().path("data");
        refWarehouseId = toLong(whs.get(0).get("stockId"));
        refWarehouseName = (String) whs.get(0).get("stockName");
        System.out.println("  仓库: id=" + refWarehouseId + ", name=" + refWarehouseName);

        // 供应商
        List<Map<String, Object>> sups = authReq.when().get("/api/basedata/supplier/list")
                .then().statusCode(200).extract().path("data");
        refSupplierId = toLong(sups.get(0).get("supplierId"));
        refSupplierName = (String) sups.get(0).get("supplierName");
        System.out.println("  供应商: id=" + refSupplierId + ", name=" + refSupplierName);

        // 币种
        List<Map<String, Object>> curs = authReq.when().get("/api/basedata/currency/list")
                .then().statusCode(200).extract().path("data");
        refCurrencyId = toLong(curs.get(0).get("currencyId"));
        refCurrencyCode = (String) curs.get(0).get("currencyCode");
        System.out.println("  币种: id=" + refCurrencyId + ", code=" + refCurrencyCode);

        System.out.println("[ProductionFieldTestBase] 基础资料加载完成");
    }

    /** 安全转换 Long（兼容 Integer/Long 返回） */
    protected static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }

    /** 重写 createBomAndGetId，使用完整字段（含 unitId，避免 NOT NULL 约束失败） */
    @Override
    protected Long createBomAndGetId() {
        io.restassured.response.Response r = authPost("/api/masterdata/bom", buildFullBomBody());
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        return ((Number) r.path("data.bomId")).longValue();
    }

    // ===================== 完整字段构造工厂 =====================
    // 覆盖实体所有业务字段（而非最小必填字段），模拟用户完整填写表单

    /** 构造 BOM 完整字段请求体（含 2 行明细） */
    protected Map<String, Object> buildFullBomBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("bomCode", uniqueBomCode());
        body.put("bomName", "完整字段BOM-" + uniqueSuffix());
        body.put("bomVersion", "V1.0");
        body.put("materialId", refMaterialId);
        body.put("materialCode", refMaterialCode);
        body.put("materialName", refMaterialName);
        body.put("unitId", refUnitId);
        body.put("quantity", new BigDecimal("1"));
        body.put("bomType", "标准BOM");
        body.put("isPrimary", 1);
        body.put("isEnabled", 1);
        body.put("effectiveDate", LocalDate.now().toString());
        body.put("expiryDate", LocalDate.now().plusYears(1).toString());
        body.put("substituteBomCode", "");
        body.put("scrapRate", new BigDecimal("2.5"));
        body.put("remark", "完整字段测试-自动化");
        body.put("attachmentUrls", "");
        body.put("status", "草稿");

        // 2 行 BOM 明细
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        items.add(buildBomItemLine(1, refMaterialId2, refMaterialCode2, refMaterialName2, refUnitId, new BigDecimal("2")));
        items.add(buildBomItemLine(2, refMaterialId, refMaterialCode, refMaterialName, refUnitId, new BigDecimal("0.5")));
        body.put("items", items);
        return body;
    }

    private Map<String, Object> buildBomItemLine(int lineNo, Long matId, String matCode, String matName, Long unitId, BigDecimal qty) {
        Map<String, Object> item = new HashMap<>();
        item.put("lineNo", lineNo);
        item.put("materialId", matId);
        item.put("materialCode", matCode);
        item.put("materialName", matName);
        item.put("specification", "");
        item.put("unitId", unitId);
        item.put("quantity", qty);
        item.put("qtyType", "固定");
        item.put("scrapRate", new BigDecimal("1.0"));
        item.put("processNo", "10");
        item.put("isKeyPart", lineNo == 1 ? 1 : 0);
        item.put("supplyType", "领料");
        item.put("positionNo", "");
        item.put("isOptional", 0);
        item.put("remark", "明细行" + lineNo);
        return item;
    }

    /** 构造工作中心完整字段请求体（含部门/负责人/委外供应商） */
    protected Map<String, Object> buildFullWorkCenterBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("workCenterCode", uniqueWorkCenterCode());
        body.put("workCenterName", "完整字段工作中心-" + uniqueSuffix());
        body.put("workCenterType", "生产线");
        body.put("departmentId", refDepartmentId);          // 关联部门
        body.put("managerId", refEmployeeId);                // 关联负责人
        body.put("location", "A区1号厂房");
        body.put("capacityType", "按产量");
        body.put("capacityPerDay", new BigDecimal("500"));
        body.put("capacityUnit", "件");
        body.put("efficiency", new BigDecimal("95"));
        body.put("laborRate", new BigDecimal("80"));
        body.put("overheadRate", new BigDecimal("40"));
        body.put("maxWorkers", 10);
        body.put("isOutsourced", 0);
        body.put("isActive", 1);
        body.put("status", "启用");
        body.put("remark", "完整字段测试-自动化");
        return body;
    }

    /** 构造委外工作中心（关联供应商） */
    protected Map<String, Object> buildFullOutsourcedWorkCenterBody() {
        Map<String, Object> body = buildFullWorkCenterBody();
        body.put("workCenterCode", uniqueWorkCenterCode());
        body.put("workCenterName", "委外工作中心-" + uniqueSuffix());
        body.put("workCenterType", "车间");
        body.put("isOutsourced", 1);
        body.put("supplierId", refSupplierId);               // 关联供应商
        return body;
    }

    /** 构造工位完整字段请求体 */
    protected Map<String, Object> buildFullWorkstationBody(Long workCenterId) {
        Map<String, Object> body = new HashMap<>();
        body.put("workstationCode", uniqueWorkstationCode());
        body.put("workstationName", "完整字段工位-" + uniqueSuffix());
        body.put("workCenterId", workCenterId);
        body.put("workstationType", "生产工位");
        body.put("location", "A区1号工位");
        body.put("isActive", 1);
        body.put("status", "启用");
        body.put("remark", "完整字段测试-自动化");
        return body;
    }

    /** 构造工艺路线完整字段请求体（含 2 道工序） */
    protected Map<String, Object> buildFullRoutingBody(Long workCenterId, Long workstationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("routingCode", uniqueRoutingCode());
        body.put("routingName", "完整字段工艺路线-" + uniqueSuffix());
        body.put("routingVersion", "V1.0");
        body.put("materialId", refMaterialId);
        body.put("materialCode", refMaterialCode);
        body.put("materialName", refMaterialName);
        body.put("isPrimary", 1);
        body.put("isEnabled", 1);
        body.put("effectiveDate", LocalDate.now().toString());
        body.put("expiryDate", LocalDate.now().plusYears(1).toString());
        body.put("remark", "完整字段测试-自动化");
        body.put("status", "草稿");

        // 2 道工序
        List<Map<String, Object>> ops = new java.util.ArrayList<>();
        ops.add(buildRoutingOperation(1, "10", "下料", workCenterId, workstationId));
        ops.add(buildRoutingOperation(2, "20", "组装", workCenterId, workstationId));
        body.put("operations", ops);
        return body;
    }

    private Map<String, Object> buildRoutingOperation(int lineNo, String opCode, String opName, Long wcId, Long wsId) {
        Map<String, Object> op = new HashMap<>();
        op.put("lineNo", lineNo);
        op.put("operationCode", opCode);
        op.put("operationName", opName);
        op.put("workstationType", "生产工位");
        op.put("workstationId", wsId);
        op.put("workCenterId", wcId);
        op.put("workCenterName", "测试工作中心");
        op.put("isOutsourced", 0);
        op.put("setupHours", new BigDecimal("0.5"));
        op.put("processHours", new BigDecimal("2.0"));
        op.put("waitHours", new BigDecimal("0.5"));
        op.put("moveHours", new BigDecimal("0.2"));
        op.put("batchSize", 100);
        op.put("workersRequired", 2);
        op.put("isQcRequired", 1);
        op.put("isCritical", lineNo == 1 ? 1 : 0);
        op.put("remark", "工序" + opName);
        return op;
    }

    /** 构造生产工单完整字段请求体 */
    protected Map<String, Object> buildFullWorkOrderBody(Long bomId) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", uniqueWorkOrderNo());
        body.put("orderDate", LocalDate.now().toString());
        body.put("bomId", bomId);
        body.put("materialId", refMaterialId);
        body.put("materialCode", refMaterialCode);
        body.put("materialName", refMaterialName);
        body.put("model", refMaterialSpec);
        body.put("unitId", refUnitId);
        body.put("quantity", new BigDecimal("100"));
        body.put("warehouseId", refWarehouseId);
        body.put("departmentId", refDepartmentId);
        body.put("managerId", refEmployeeId);
        body.put("priority", "高");
        body.put("planStartDate", LocalDate.now().toString());
        body.put("planEndDate", LocalDate.now().plusDays(7).toString());
        body.put("leadTimeDays", 7);
        body.put("isUrgent", 0);
        body.put("isClosed", 0);
        body.put("sourceType", "手工");
        body.put("sourceNo", "");
        body.put("salesOrderNo", "");
        body.put("remark", "完整字段测试-自动化");
        body.put("status", "草稿");
        return body;
    }

    /** 构造生产入库单完整字段请求体（含明细） */
    protected Map<String, Object> buildFullReceiptBody(Long workOrderId, String workOrderNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiptNo", uniqueReceiptNo());
        body.put("receiptDate", LocalDate.now().toString());
        body.put("warehouseId", refWarehouseId);
        body.put("productionOrderId", workOrderId);
        body.put("productionOrderNo", workOrderNo);
        body.put("departmentId", refDepartmentId);
        body.put("handlerId", refEmployeeId);
        body.put("currencyId", refCurrencyId);
        body.put("exchangeRate", new BigDecimal("1"));
        body.put("totalQuantity", new BigDecimal("100"));
        body.put("qualifiedQuantity", new BigDecimal("98"));
        body.put("rejectedQuantity", new BigDecimal("2"));
        body.put("sourceBillType", "生产工单");
        body.put("sourceBillNo", workOrderNo);
        body.put("remark", "完整字段测试-自动化");
        body.put("status", "草稿");

        // 1 行明细
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("lineNo", 1);
        item.put("materialId", refMaterialId);
        item.put("materialCode", refMaterialCode);
        item.put("materialName", refMaterialName);
        item.put("specification", refMaterialSpec);
        item.put("unitId", refUnitId);
        item.put("stockId", refWarehouseId);
        item.put("quantity", new BigDecimal("100"));
        item.put("qualifiedQuantity", new BigDecimal("98"));
        item.put("rejectedQuantity", new BigDecimal("2"));
        item.put("unitPrice", new BigDecimal("10"));
        item.put("amount", new BigDecimal("980"));
        item.put("batchNo", "BATCH-" + uniqueSuffix());
        item.put("productionDate", LocalDate.now().toString());
        item.put("remark", "入库明细行1");
        items.add(item);
        body.put("items", items);
        return body;
    }

    /** 构造工序汇报单完整字段请求体 */
    protected Map<String, Object> buildFullOperationReportBody(Long workCenterId, Long workstationId, String workOrderNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("reportNo", uniqueReportNo());
        body.put("reportDate", LocalDate.now().toString());
        body.put("productionOrderNo", workOrderNo);
        body.put("processNo", "10");
        body.put("workCenterId", workCenterId);
        body.put("workstationId", workstationId);
        body.put("operationName", "下料工序");
        body.put("employeeId", refEmployeeId);
        body.put("employeeName", refEmployeeName);
        body.put("reportType", "工序汇报");
        body.put("totalQuantity", new BigDecimal("100"));
        body.put("qualifiedQuantity", new BigDecimal("95"));
        body.put("rejectedQuantity", new BigDecimal("5"));
        body.put("workHours", new BigDecimal("8"));
        body.put("overtimeHours", new BigDecimal("2"));
        body.put("shiftType", "白班");
        body.put("remark", "完整字段测试-自动化");
        body.put("status", "草稿");
        return body;
    }

    /** 构造委外加工单完整字段请求体（含发料明细） */
    protected Map<String, Object> buildFullSubcontractBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("billNo", uniqueSubcontractNo());
        body.put("orderDate", LocalDate.now().toString());
        body.put("materialId", refMaterialId);
        body.put("materialCode", refMaterialCode);
        body.put("materialName", refMaterialName);
        body.put("unitId", refUnitId);
        body.put("qty", new BigDecimal("100"));
        body.put("supplierId", refSupplierId);
        body.put("supplierName", refSupplierName);
        body.put("processNo", "30");
        body.put("unitPrice", new BigDecimal("15"));
        body.put("totalPrice", new BigDecimal("1500"));
        body.put("planStartDate", LocalDate.now().toString());
        body.put("planFinishDate", LocalDate.now().plusDays(10).toString());
        body.put("remark", "完整字段测试-自动化");
        body.put("status", "草稿");

        // 1 行发料明细
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("lineNo", 1);
        item.put("materialId", refMaterialId2);
        item.put("materialName", refMaterialName2);
        item.put("unitId", refUnitId);
        item.put("baseQty", new BigDecimal("200"));
        item.put("actualQty", new BigDecimal("200"));
        item.put("supplyType", "我方供料");
        item.put("remark", "委外发料行1");
        items.add(item);
        body.put("items", items);
        return body;
    }

    /** 构造 MRP 方案完整字段请求体 */
    protected Map<String, Object> buildFullMrpPlanBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("planCode", uniqueMrpPlanCode());
        body.put("planName", "完整字段MRP方案-" + uniqueSuffix());
        body.put("planDate", LocalDate.now().toString());
        body.put("planType", "MPS/MRP");
        body.put("planHorizon", 90);
        body.put("demandSource", "销售订单+预测");
        body.put("demandType", "综合");
        body.put("includeSo", 1);
        body.put("includeForecast", 1);
        body.put("includeSafetyStock", 1);
        body.put("includeOpenPo", 1);
        body.put("includeOpenMo", 1);
        body.put("includeOnhandStock", 1);
        body.put("timeFenceDays", 7);
        body.put("lotSizeType", "逐批法");
        body.put("lotSize", new BigDecimal("1"));
        body.put("minOrderQty", new BigDecimal("1"));
        body.put("safetyStockDays", 7);
        body.put("leadTimeDays", 14);
        body.put("mrpHorizontalDays", 7);
        body.put("orderType", "采购申请");
        body.put("isAutoCreateOrder", 0);
        body.put("startDate", LocalDate.now().toString());
        body.put("endDate", LocalDate.now().plusDays(90).toString());
        body.put("remark", "完整字段测试-自动化");
        body.put("planStatus", "草稿");
        return body;
    }

    // ===================== 字段断言辅助 =====================

    /** 断言两个值相等（支持 null） */
    protected static void assertFieldEquals(String fieldName, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null) {
            org.junit.jupiter.api.Assertions.fail("字段[" + fieldName + "]期望 null，实际: " + actual);
        }
        // 数字比较（兼容 Integer/Long/BigDecimal）
        if (expected instanceof Number && actual instanceof Number) {
            org.junit.jupiter.api.Assertions.assertEquals(
                    new java.math.BigDecimal(expected.toString()).doubleValue(),
                    new java.math.BigDecimal(actual.toString()).doubleValue(),
                    0.001,
                    "字段[" + fieldName + "]值不匹配");
            return;
        }
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual,
                "字段[" + fieldName + "]值不匹配");
    }

    /** 断言字段非 null */
    protected static void assertFieldNotNull(String fieldName, Object actual) {
        org.junit.jupiter.api.Assertions.assertNotNull(actual, "字段[" + fieldName + "]不应为 null");
    }

    /** 打印字段验证通过日志 */
    protected static void logPass(String msg) {
        System.out.println("  [PASS] " + msg);
    }

    /** 从创建响应或查询响应中提取 data */
    protected static Map<String, Object> getData(Response r) {
        return r.path("data");
    }
}
