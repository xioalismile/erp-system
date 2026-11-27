package com.erp.test.production;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生产模块字段完整性与基础资料关联自动化测试
 *
 * 测试目标（参照标准ERP验收规范）：
 *  1. 模拟用户在前端页面填写所有业务字段 + 通过下拉选择关联基础资料后保存，验证保存是否正常
 *  2. 保存后查询返回，验证所有字段值与输入一致（前端显示正常的先决条件）
 *  3. 重点验证下拉关联字段（物料/部门/员工/仓库/供应商/工作中心等）是否正确存储和回显
 *  4. 验证主从表结构（BOM明细/工艺路线工序/入库明细/委外发料明细）的保存和查询
 *
 * 覆盖生产模块 9 类单据：
 *  - BOM 物料清单（含明细）
 *  - 工作中心（含部门/负责人/委外供应商）
 *  - 工位（关联工作中心）
 *  - 工艺路线（含工序明细）
 *  - 生产工单（关联BOM/物料/仓库/部门）
 *  - 生产入库单（含明细）
 *  - 工序汇报单
 *  - 委外加工单（含发料明细）
 *  - MRP 运算方案
 *
 * 前置条件：基础资料模块已有数据（物料/部门/员工/仓库/供应商/单位/币种）
 */
@DisplayName("生产模块字段完整性与基础资料关联测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductionFieldIntegrationTest extends ProductionFieldTestBase {

    // ===================== 1. BOM 物料清单 =====================

    @Test
    @Order(1)
    @DisplayName("1.1 BOM完整字段保存+查询验证（含2行明细）")
    void testBomFullFieldsSaveAndQuery() {
        System.out.println("\n========== 1.1 BOM完整字段保存+查询 ==========");
        Map<String, Object> body = buildFullBomBody();
        String bomCode = (String) body.get("bomCode");

        // 保存
        Response createResp = authPost("/api/masterdata/bom", body);
        assertSuccess(createResp, "BOM保存成功");
        Long bomId = toLong(createResp.path("data.bomId"));
        assertNotNull(bomId, "bomId不应为null");
        System.out.println("  BOM保存成功, bomId=" + bomId + ", bomCode=" + bomCode);

        // 查询
        Response detailResp = authGet("/api/masterdata/bom/{id}", bomId);
        assertSuccess(detailResp, "BOM查询成功");
        Map<String, Object> data = getData(detailResp);

        // 验证表头字段
        assertFieldEquals("bomCode", bomCode, data.get("bomCode"));
        assertFieldEquals("bomName", body.get("bomName"), data.get("bomName"));
        assertFieldEquals("bomVersion", "V1.0", data.get("bomVersion"));
        assertFieldEquals("materialId", refMaterialId, data.get("materialId"));
        assertFieldEquals("materialCode", refMaterialCode, data.get("materialCode"));
        assertFieldEquals("materialName", refMaterialName, data.get("materialName"));
        assertFieldEquals("unitId", refUnitId, data.get("unitId"));
        assertFieldEquals("quantity", new BigDecimal("1"), data.get("quantity"));
        assertFieldEquals("bomType", "标准BOM", data.get("bomType"));
        assertFieldEquals("isPrimary", 1, data.get("isPrimary"));
        assertFieldEquals("scrapRate", new BigDecimal("2.5"), data.get("scrapRate"));
        assertFieldEquals("status", "草稿", data.get("status"));
        logPass("BOM表头字段验证通过");

        // 验证明细
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertNotNull(items, "BOM明细不应为null");
        assertEquals(2, items.size(), "BOM明细应有2行");
        assertFieldEquals("明细行1.lineNo", 10, items.get(0).get("lineNo"));
        assertFieldEquals("明细行1.materialId", refMaterialId2, items.get(0).get("materialId"));
        assertFieldEquals("明细行1.materialCode", refMaterialCode2, items.get(0).get("materialCode"));
        assertFieldEquals("明细行1.quantity", new BigDecimal("2"), items.get(0).get("quantity"));
        assertFieldEquals("明细行1.isKeyPart", 1, items.get(0).get("isKeyPart"));
        assertFieldEquals("明细行1.supplyType", "领料", items.get(0).get("supplyType"));
        logPass("BOM明细字段验证通过 (2行)");
    }

    // ===================== 2. 工作中心 =====================

    @Test
    @Order(2)
    @DisplayName("2.1 工作中心完整字段保存+查询验证（含部门/负责人下拉）")
    void testWorkCenterFullFieldsSaveAndQuery() {
        System.out.println("\n========== 2.1 工作中心完整字段保存+查询 ==========");
        Map<String, Object> body = buildFullWorkCenterBody();
        String code = (String) body.get("workCenterCode");

        Response createResp = authPost("/api/production/work-center", body);
        assertSuccess(createResp, "工作中心保存成功");

        // 查列表获取 ID
        Response listResp = authGetWithParams("/api/production/work-center/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        assertSuccess(listResp, "工作中心列表查询成功");
        List<Map<String, Object>> records = listResp.path("data.records");
        Long wcId = null;
        for (Map<String, Object> rec : records) {
            if (code.equals(rec.get("workCenterCode"))) {
                wcId = toLong(rec.get("workCenterId"));
                break;
            }
        }
        assertNotNull(wcId, "未找到刚创建的工作中心");
        System.out.println("  工作中心保存成功, wcId=" + wcId);

        // 查详情
        Response detailResp = authGet("/api/production/work-center/{id}", wcId);
        assertSuccess(detailResp, "工作中心详情查询成功");
        Map<String, Object> data = getData(detailResp);

        // 验证字段
        assertFieldEquals("workCenterCode", code, data.get("workCenterCode"));
        assertFieldEquals("workCenterName", body.get("workCenterName"), data.get("workCenterName"));
        assertFieldEquals("workCenterType", "生产线", data.get("workCenterType"));
        assertFieldEquals("departmentId", refDepartmentId, data.get("departmentId"));
        assertFieldEquals("managerId", refEmployeeId, data.get("managerId"));
        assertFieldEquals("location", "A区1号厂房", data.get("location"));
        assertFieldEquals("capacityPerDay", new BigDecimal("500"), data.get("capacityPerDay"));
        assertFieldEquals("capacityUnit", "件", data.get("capacityUnit"));
        assertFieldEquals("efficiency", new BigDecimal("95"), data.get("efficiency"));
        assertFieldEquals("laborRate", new BigDecimal("80"), data.get("laborRate"));
        assertFieldEquals("overheadRate", new BigDecimal("40"), data.get("overheadRate"));
        assertFieldEquals("maxWorkers", 10, data.get("maxWorkers"));
        assertFieldEquals("isOutsourced", 0, data.get("isOutsourced"));
        assertFieldEquals("status", "启用", data.get("status"));
        logPass("工作中心字段验证通过（部门ID=" + refDepartmentId + ", 员工ID=" + refEmployeeId + " 正确存储）");
    }

    @Test
    @Order(3)
    @DisplayName("2.2 委外工作中心供应商下拉关联验证")
    void testOutsourcedWorkCenterWithSupplier() {
        System.out.println("\n========== 2.2 委外工作中心供应商关联 ==========");
        Map<String, Object> body = buildFullOutsourcedWorkCenterBody();
        String code = (String) body.get("workCenterCode");

        Response createResp = authPost("/api/production/work-center", body);
        assertSuccess(createResp, "委外工作中心保存成功");

        Response listResp = authGetWithParams("/api/production/work-center/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        List<Map<String, Object>> records = listResp.path("data.records");
        Long wcId = null;
        for (Map<String, Object> rec : records) {
            if (code.equals(rec.get("workCenterCode"))) {
                wcId = toLong(rec.get("workCenterId"));
                break;
            }
        }
        assertNotNull(wcId, "未找到委外工作中心");

        Response detailResp = authGet("/api/production/work-center/{id}", wcId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("isOutsourced", 1, data.get("isOutsourced"));
        assertFieldEquals("supplierId", refSupplierId, data.get("supplierId"));
        logPass("委外工作中心供应商关联验证通过 (supplierId=" + refSupplierId + ")");
    }

    // ===================== 3. 工位 =====================

    @Test
    @Order(4)
    @DisplayName("3.1 工位完整字段保存+查询验证（关联工作中心下拉）")
    void testWorkstationFullFieldsSaveAndQuery() {
        System.out.println("\n========== 3.1 工位完整字段保存+查询 ==========");
        Long wcId = createWorkCenterAndGetId();
        Map<String, Object> body = buildFullWorkstationBody(wcId);
        String code = (String) body.get("workstationCode");

        Response createResp = authPost("/api/production/workstation", body);
        assertSuccess(createResp, "工位保存成功");

        Response listResp = authGetWithParams("/api/production/workstation/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        List<Map<String, Object>> records = listResp.path("data.records");
        Long wsId = null;
        for (Map<String, Object> rec : records) {
            if (code.equals(rec.get("workstationCode"))) {
                wsId = toLong(rec.get("workstationId"));
                break;
            }
        }
        assertNotNull(wsId, "未找到工位");
        System.out.println("  工位保存成功, wsId=" + wsId);

        Response detailResp = authGet("/api/production/workstation/{id}", wsId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("workstationCode", code, data.get("workstationCode"));
        assertFieldEquals("workstationName", body.get("workstationName"), data.get("workstationName"));
        assertFieldEquals("workCenterId", wcId, data.get("workCenterId"));
        assertFieldEquals("workstationType", "生产工位", data.get("workstationType"));
        assertFieldEquals("location", "A区1号工位", data.get("location"));
        assertFieldEquals("status", "启用", data.get("status"));
        logPass("工位字段验证通过（关联工作中心ID=" + wcId + " 正确存储）");
    }

    // ===================== 4. 工艺路线 =====================

    @Test
    @Order(5)
    @DisplayName("4.1 工艺路线完整字段保存+查询验证（含2道工序明细）")
    void testRoutingFullFieldsSaveAndQuery() {
        System.out.println("\n========== 4.1 工艺路线完整字段保存+查询 ==========");
        Long wcId = createWorkCenterAndGetId();
        Long wsId = createWorkstationAndGetIdViaCode(wcId);

        Map<String, Object> body = buildFullRoutingBody(wcId, wsId);
        String code = (String) body.get("routingCode");

        Response createResp = authPost("/api/production/routings", body);
        assertSuccess(createResp, "工艺路线保存成功");
        Long routingId = toLong(createResp.path("data.routingId"));
        assertNotNull(routingId, "routingId不应为null");
        System.out.println("  工艺路线保存成功, routingId=" + routingId);

        Response detailResp = authGet("/api/production/routings/{id}", routingId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("routingCode", code, data.get("routingCode"));
        assertFieldEquals("routingName", body.get("routingName"), data.get("routingName"));
        assertFieldEquals("routingVersion", "V1.0", data.get("routingVersion"));
        assertFieldEquals("materialId", refMaterialId, data.get("materialId"));
        assertFieldEquals("materialCode", refMaterialCode, data.get("materialCode"));
        assertFieldEquals("materialName", refMaterialName, data.get("materialName"));
        assertFieldEquals("isPrimary", 1, data.get("isPrimary"));
        assertFieldEquals("status", "草稿", data.get("status"));
        logPass("工艺路线表头字段验证通过");

        // 验证工序明细
        List<Map<String, Object>> ops = (List<Map<String, Object>>) data.get("operations");
        if (ops == null) ops = (List<Map<String, Object>>) data.get("steps");
        assertNotNull(ops, "工序明细不应为null");
        assertTrue(ops.size() >= 2, "工序明细应有至少2行，实际: " + ops.size());
        assertFieldEquals("工序1.lineNo", 10, ops.get(0).get("lineNo"));
        assertFieldEquals("工序1.operationName", "下料", ops.get(0).get("operationName"));
        assertFieldEquals("工序1.workCenterId", wcId, ops.get(0).get("workCenterId"));
        assertFieldEquals("工序1.processHours", new BigDecimal("2.0"), ops.get(0).get("processHours"));
        assertFieldEquals("工序2.lineNo", 20, ops.get(1).get("lineNo"));
        assertFieldEquals("工序2.operationName", "组装", ops.get(1).get("operationName"));
        logPass("工艺路线工序明细验证通过 (2道工序)");
    }

    // ===================== 5. 生产工单 =====================

    @Test
    @Order(6)
    @DisplayName("5.1 生产工单完整字段保存+查询验证（关联BOM/物料/仓库/部门下拉）")
    void testWorkOrderFullFieldsSaveAndQuery() {
        System.out.println("\n========== 5.1 生产工单完整字段保存+查询 ==========");
        Long bomId = createBomAndGetId();
        Map<String, Object> body = buildFullWorkOrderBody(bomId);
        String orderNo = (String) body.get("orderNo");

        Response createResp = authPost("/api/production/workorders", body);
        assertSuccess(createResp, "生产工单保存成功");

        // 查列表获取 ID
        Response listResp = authGetWithParams("/api/production/workorders",
                Map.of("current", 1, "size", 50, "keyword", orderNo));
        List<Map<String, Object>> records = listResp.path("data.records");
        Long orderId = null;
        for (Map<String, Object> rec : records) {
            if (orderNo.equals(rec.get("orderNo"))) {
                orderId = toLong(rec.get("orderId"));
                break;
            }
        }
        assertNotNull(orderId, "未找到工单");
        System.out.println("  工单保存成功, orderId=" + orderId);

        Response detailResp = authGet("/api/production/workorders/{id}", orderId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("orderNo", orderNo, data.get("orderNo"));
        assertFieldEquals("bomId", bomId, data.get("bomId"));
        assertFieldEquals("materialId", refMaterialId, data.get("materialId"));
        assertFieldEquals("materialCode", refMaterialCode, data.get("materialCode"));
        assertFieldEquals("materialName", refMaterialName, data.get("materialName"));
        assertFieldEquals("unitId", refUnitId, data.get("unitId"));
        assertFieldEquals("quantity", new BigDecimal("100"), data.get("quantity"));
        assertFieldEquals("warehouseId", refWarehouseId, data.get("warehouseId"));
        assertFieldEquals("departmentId", refDepartmentId, data.get("departmentId"));
        assertFieldEquals("managerId", refEmployeeId, data.get("managerId"));
        assertFieldEquals("priority", "高", data.get("priority"));
        assertFieldEquals("leadTimeDays", 7, data.get("leadTimeDays"));
        assertFieldEquals("sourceType", "手工", data.get("sourceType"));
        assertFieldEquals("status", "草稿", data.get("status"));
        logPass("生产工单字段验证通过（BOM/物料/仓库/部门/员工下拉关联正确）");
    }

    // ===================== 6. 生产入库单 =====================

    @Test
    @Order(7)
    @DisplayName("6.1 生产入库单完整字段保存+查询验证（含明细）")
    void testProductionReceiptFullFieldsSaveAndQuery() {
        System.out.println("\n========== 6.1 生产入库单完整字段保存+查询 ==========");
        Long bomId = createBomAndGetId();
        Long orderId = createWorkOrderAndGetIdViaNo(bomId);
        // 查工单号
        Response woDetail = authGet("/api/production/workorders/{id}", orderId);
        String orderNo = woDetail.path("data.orderNo");

        Map<String, Object> body = buildFullReceiptBody(orderId, orderNo);
        String receiptNo = (String) body.get("receiptNo");

        Response createResp = authPost("/api/production/receipts", body);
        assertSuccess(createResp, "生产入库单保存成功");
        Long receiptId = toLong(createResp.path("data.receiptId"));
        assertNotNull(receiptId, "receiptId不应为null");
        System.out.println("  入库单保存成功, receiptId=" + receiptId);

        Response detailResp = authGet("/api/production/receipts/{id}", receiptId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("receiptNo", receiptNo, data.get("receiptNo"));
        assertFieldEquals("warehouseId", refWarehouseId, data.get("warehouseId"));
        assertFieldEquals("productionOrderId", orderId, data.get("productionOrderId"));
        assertFieldEquals("productionOrderNo", orderNo, data.get("productionOrderNo"));
        assertFieldEquals("departmentId", refDepartmentId, data.get("departmentId"));
        assertFieldEquals("handlerId", refEmployeeId, data.get("handlerId"));
        assertFieldEquals("currencyId", refCurrencyId, data.get("currencyId"));
        assertFieldEquals("totalQuantity", new BigDecimal("100"), data.get("totalQuantity"));
        assertFieldEquals("qualifiedQuantity", new BigDecimal("98"), data.get("qualifiedQuantity"));
        assertFieldEquals("rejectedQuantity", new BigDecimal("2"), data.get("rejectedQuantity"));
        assertFieldEquals("status", "草稿", data.get("status"));
        logPass("入库单表头字段验证通过");

        // 验证明细
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertNotNull(items, "入库明细不应为null");
        assertTrue(items.size() >= 1, "入库明细应有至少1行");
        assertFieldEquals("明细1.materialId", refMaterialId, items.get(0).get("materialId"));
        assertFieldEquals("明细1.materialCode", refMaterialCode, items.get(0).get("materialCode"));
        assertFieldEquals("明细1.quantity", new BigDecimal("100"), items.get(0).get("quantity"));
        assertFieldEquals("明细1.unitPrice", new BigDecimal("10"), items.get(0).get("unitPrice"));
        logPass("入库单明细字段验证通过");
    }

    // ===================== 8. 工序汇报单 =====================

    @Test
    @Order(9)
    @DisplayName("8.1 工序汇报单完整字段保存+查询验证")
    void testOperationReportFullFieldsSaveAndQuery() {
        System.out.println("\n========== 8.1 工序汇报单完整字段保存+查询 ==========");
        Long bomId = createBomAndGetId();
        Long orderId = createWorkOrderAndGetIdViaNo(bomId);
        Response woDetail = authGet("/api/production/workorders/{id}", orderId);
        String orderNo = woDetail.path("data.orderNo");

        Long wcId = createWorkCenterAndGetId();
        Long wsId = createWorkstationAndGetIdViaCode(wcId);
        Map<String, Object> body = buildFullOperationReportBody(wcId, wsId, orderNo);
        String reportNo = (String) body.get("reportNo");

        Response createResp = authPost("/api/production/operation-report", body);
        assertSuccess(createResp, "工序汇报单保存成功");
        Long reportId = toLong(createResp.path("data.reportId"));
        assertNotNull(reportId, "reportId不应为null");
        System.out.println("  汇报单保存成功, reportId=" + reportId);

        Response detailResp = authGet("/api/production/operation-report/{id}", reportId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("reportNo", reportNo, data.get("reportNo"));
        assertFieldEquals("productionOrderNo", orderNo, data.get("productionOrderNo"));
        assertFieldEquals("processNo", "10", data.get("processNo"));
        assertFieldEquals("workCenterId", wcId, data.get("workCenterId"));
        assertFieldEquals("workstationId", wsId, data.get("workstationId"));
        assertFieldEquals("operationName", "下料工序", data.get("operationName"));
        assertFieldEquals("employeeId", refEmployeeId, data.get("employeeId"));
        assertFieldEquals("employeeName", refEmployeeName, data.get("employeeName"));
        assertFieldEquals("totalQuantity", new BigDecimal("100"), data.get("totalQuantity"));
        assertFieldEquals("qualifiedQuantity", new BigDecimal("95"), data.get("qualifiedQuantity"));
        assertFieldEquals("rejectedQuantity", new BigDecimal("5"), data.get("rejectedQuantity"));
        assertFieldEquals("workHours", new BigDecimal("8"), data.get("workHours"));
        assertFieldEquals("overtimeHours", new BigDecimal("2"), data.get("overtimeHours"));
        assertFieldEquals("shiftType", "白班", data.get("shiftType"));
        assertFieldEquals("status", "草稿", data.get("status"));
        logPass("工序汇报单字段验证通过（工作中心/工位/员工下拉关联正确）");
    }

    // ===================== 9. 委外加工单 =====================

    @Test
    @Order(10)
    @DisplayName("9.1 委外加工单完整字段保存+查询验证（含发料明细）")
    void testSubcontractFullFieldsSaveAndQuery() {
        System.out.println("\n========== 9.1 委外加工单完整字段保存+查询 ==========");
        Map<String, Object> body = buildFullSubcontractBody();
        String billNo = (String) body.get("billNo");

        Response createResp = authPost("/api/production/subcontract", body);
        assertSuccess(createResp, "委外加工单保存成功");
        Long subId = toLong(createResp.path("data.subcontractId"));
        assertNotNull(subId, "subcontractId不应为null");
        System.out.println("  委外单保存成功, subcontractId=" + subId);

        Response detailResp = authGet("/api/production/subcontract/{id}", subId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("billNo", billNo, data.get("billNo"));
        assertFieldEquals("materialId", refMaterialId, data.get("materialId"));
        assertFieldEquals("materialCode", refMaterialCode, data.get("materialCode"));
        assertFieldEquals("materialName", refMaterialName, data.get("materialName"));
        assertFieldEquals("unitId", refUnitId, data.get("unitId"));
        assertFieldEquals("qty", new BigDecimal("100"), data.get("qty"));
        assertFieldEquals("supplierId", refSupplierId, data.get("supplierId"));
        assertFieldEquals("supplierName", refSupplierName, data.get("supplierName"));
        assertFieldEquals("processNo", "30", data.get("processNo"));
        assertFieldEquals("unitPrice", new BigDecimal("15"), data.get("unitPrice"));
        assertFieldEquals("totalPrice", new BigDecimal("1500"), data.get("totalPrice"));
        assertFieldEquals("status", "草稿", data.get("status"));
        logPass("委外单表头字段验证通过（物料/供应商下拉关联正确）");

        // 验证发料明细
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items != null && !items.isEmpty()) {
            assertFieldEquals("发料1.materialId", refMaterialId2, items.get(0).get("materialId"));
            assertFieldEquals("发料1.materialName", refMaterialName2, items.get(0).get("materialName"));
            assertFieldEquals("发料1.baseQty", new BigDecimal("200"), items.get(0).get("baseQty"));
            assertFieldEquals("发料1.supplyType", "我方供料", items.get(0).get("supplyType"));
            logPass("委外发料明细字段验证通过");
        }
    }

    // ===================== 10. MRP 运算方案 =====================

    @Test
    @Order(11)
    @DisplayName("10.1 MRP方案完整字段保存+查询验证")
    void testMrpPlanFullFieldsSaveAndQuery() {
        System.out.println("\n========== 10.1 MRP方案完整字段保存+查询 ==========");
        Map<String, Object> body = buildFullMrpPlanBody();
        String planCode = (String) body.get("planCode");

        Response createResp = authPost("/api/production/mrp-plan", body);
        assertSuccess(createResp, "MRP方案保存成功");
        Long planId = toLong(createResp.path("data.planId"));
        assertNotNull(planId, "planId不应为null");
        System.out.println("  MRP方案保存成功, planId=" + planId);

        Response detailResp = authGet("/api/production/mrp-plan/{id}", planId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("planCode", planCode, data.get("planCode"));
        assertFieldEquals("planName", body.get("planName"), data.get("planName"));
        assertFieldEquals("planType", "MPS/MRP", data.get("planType"));
        assertFieldEquals("planHorizon", 90, data.get("planHorizon"));
        assertFieldEquals("demandSource", "销售订单+预测", data.get("demandSource"));
        assertFieldEquals("includeSo", 1, data.get("includeSo"));
        assertFieldEquals("includeForecast", 1, data.get("includeForecast"));
        assertFieldEquals("includeSafetyStock", 1, data.get("includeSafetyStock"));
        assertFieldEquals("includeOpenPo", 1, data.get("includeOpenPo"));
        assertFieldEquals("includeOpenMo", 1, data.get("includeOpenMo"));
        assertFieldEquals("includeOnhandStock", 1, data.get("includeOnhandStock"));
        assertFieldEquals("timeFenceDays", 7, data.get("timeFenceDays"));
        assertFieldEquals("lotSizeType", "逐批法", data.get("lotSizeType"));
        assertFieldEquals("leadTimeDays", 14, data.get("leadTimeDays"));
        assertFieldEquals("planStatus", "草稿", data.get("planStatus"));
        logPass("MRP方案字段验证通过（所有开关参数正确存储）");
    }

    // ===================== 11. 下拉关联专项验证 =====================

    @Test
    @Order(12)
    @DisplayName("11.1 物料下拉选择后冗余字段存储验证")
    void testMaterialDropdownAutoFill() {
        System.out.println("\n========== 11.1 物料下拉冗余字段存储验证 ==========");
        // 场景：用户选物料后，前端自动带出 materialCode/materialName，后端需正确存储这些冗余字段
        Map<String, Object> body = buildFullBomBody();
        // 显式设置与基础资料一致的冗余字段
        body.put("materialId", refMaterialId);
        body.put("materialCode", refMaterialCode);
        body.put("materialName", refMaterialName);

        Response createResp = authPost("/api/masterdata/bom", body);
        assertSuccess(createResp, "BOM保存成功");
        Long bomId = toLong(createResp.path("data.bomId"));

        Response detailResp = authGet("/api/masterdata/bom/{id}", bomId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("materialId", refMaterialId, data.get("materialId"));
        assertFieldEquals("materialCode", refMaterialCode, data.get("materialCode"));
        assertFieldEquals("materialName", refMaterialName, data.get("materialName"));
        logPass("物料下拉冗余字段存储验证通过 (前端选择物料后 code/name 正确保存和回显)");
    }

    @Test
    @Order(13)
    @DisplayName("11.2 供应商下拉选择后名称存储验证")
    void testSupplierDropdownAutoFill() {
        System.out.println("\n========== 11.2 供应商下拉名称存储验证 ==========");
        Map<String, Object> body = buildFullSubcontractBody();
        body.put("supplierId", refSupplierId);
        body.put("supplierName", refSupplierName);

        Response createResp = authPost("/api/production/subcontract", body);
        assertSuccess(createResp, "委外单保存成功");
        Long subId = toLong(createResp.path("data.subcontractId"));

        Response detailResp = authGet("/api/production/subcontract/{id}", subId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("supplierId", refSupplierId, data.get("supplierId"));
        assertFieldEquals("supplierName", refSupplierName, data.get("supplierName"));
        logPass("供应商下拉名称存储验证通过 (前端选择供应商后 name 正确保存和回显)");
    }

    @Test
    @Order(14)
    @DisplayName("11.3 部门下拉选择后ID存储验证（工作中心关联部门）")
    void testDepartmentDropdownStorage() {
        System.out.println("\n========== 11.3 部门下拉ID存储验证 ==========");
        Map<String, Object> body = buildFullWorkCenterBody();
        body.put("departmentId", refDepartmentId);

        Response createResp = authPost("/api/production/work-center", body);
        assertSuccess(createResp, "工作中心保存成功");
        String code = (String) body.get("workCenterCode");
        Response listResp = authGetWithParams("/api/production/work-center/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        List<Map<String, Object>> records = listResp.path("data.records");
        Long wcId = null;
        for (Map<String, Object> rec : records) {
            if (code.equals(rec.get("workCenterCode"))) {
                wcId = toLong(rec.get("workCenterId"));
                break;
            }
        }
        assertNotNull(wcId, "未找到工作中心");

        Response detailResp = authGet("/api/production/work-center/{id}", wcId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("departmentId", refDepartmentId, data.get("departmentId"));
        logPass("部门下拉ID存储验证通过 (departmentId=" + refDepartmentId + " 正确存储)");
    }

    @Test
    @Order(15)
    @DisplayName("11.4 仓库下拉选择后ID存储验证（生产工单关联仓库）")
    void testWarehouseDropdownStorage() {
        System.out.println("\n========== 11.4 仓库下拉ID存储验证 ==========");
        Long bomId = createBomAndGetId();
        Map<String, Object> body = buildFullWorkOrderBody(bomId);
        body.put("warehouseId", refWarehouseId);

        Response createResp = authPost("/api/production/workorders", body);
        assertSuccess(createResp, "工单保存成功");
        String orderNo = (String) body.get("orderNo");
        Response listResp = authGetWithParams("/api/production/workorders",
                Map.of("current", 1, "size", 50, "keyword", orderNo));
        List<Map<String, Object>> records = listResp.path("data.records");
        Long orderId = null;
        for (Map<String, Object> rec : records) {
            if (orderNo.equals(rec.get("orderNo"))) {
                orderId = toLong(rec.get("orderId"));
                break;
            }
        }
        assertNotNull(orderId, "未找到工单");

        Response detailResp = authGet("/api/production/workorders/{id}", orderId);
        Map<String, Object> data = getData(detailResp);
        assertFieldEquals("warehouseId", refWarehouseId, data.get("warehouseId"));
        logPass("仓库下拉ID存储验证通过 (warehouseId=" + refWarehouseId + " 正确存储)");
    }

    // ===================== 辅助方法 =====================

    /** 创建工位并通过列表查询获取 ID（基于工作中心） */
    private Long createWorkstationAndGetIdViaCode(Long workCenterId) {
        Map<String, Object> body = buildFullWorkstationBody(workCenterId);
        String code = (String) body.get("workstationCode");
        Response r = authPost("/api/production/workstation", body);
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        Response listResp = authGetWithParams("/api/production/workstation/page",
                Map.of("current", 1, "size", 50, "keyword", code));
        List<Map<String, Object>> records = listResp.path("data.records");
        for (Map<String, Object> rec : records) {
            if (code.equals(rec.get("workstationCode"))) {
                return toLong(rec.get("workstationId"));
            }
        }
        throw new IllegalStateException("创建工位后未找到记录, code=" + code);
    }

    /** 创建工单并通过列表查询获取 ID（基于 BOM） */
    private Long createWorkOrderAndGetIdViaNo(Long bomId) {
        Map<String, Object> body = buildFullWorkOrderBody(bomId);
        String orderNo = (String) body.get("orderNo");
        Response r = authPost("/api/production/workorders", body);
        r.then().statusCode(200).body("code", org.hamcrest.Matchers.equalTo(200));
        Response listResp = authGetWithParams("/api/production/workorders",
                Map.of("current", 1, "size", 50, "keyword", orderNo));
        List<Map<String, Object>> records = listResp.path("data.records");
        for (Map<String, Object> rec : records) {
            if (orderNo.equals(rec.get("orderNo"))) {
                return toLong(rec.get("orderId"));
            }
        }
        throw new IllegalStateException("创建工单后未找到记录, orderNo=" + orderNo);
    }
}
