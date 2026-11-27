package com.erp.test.production;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 生产管理模块 CRUD 自动化测试
 *
 * 覆盖文档第五部分 5.1-5.8 全部 8 个子模块 + 生产入库单
 * 测试内容：
 *  1. 列表分页查询（LIST/PAGE）
 *  2. 创建单据（CREATE）
 *  3. 按ID查询详情（GET BY ID）
 *  4. 更新单据（UPDATE）
 *  5. 删除单据（DELETE）
 *
 * 模块清单：
 *  5.1 BOM物料清单       - /api/masterdata/bom
 *  5.2 工艺路线          - /api/production/routings
 *  5.3 工作中心/工位      - /api/production/work-center, /api/production/workstation
 *  5.4 生产工单          - /api/production/workorders
 *  5.6 工序汇报单        - /api/production/operation-report
 *  5.7 委外加工单        - /api/production/subcontract
 *  5.8 MRP运算方案       - /api/production/mrp-plan
 *  补充  生产入库单       - /api/production/receipts
 */
@DisplayName("生产管理模块 - CRUD测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductionCrudTest extends ProductionTestBase {

    // ==================================================================
    // 5.1 BOM物料清单
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("5.1 BOM - 列表分页查询")
    void testBomListPage() {
        Response r = authGetWithParams("/api/masterdata/bom/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "BOM LIST/PAGE");
        r.then().body("data", notNullValue());
        System.out.println("  BOM列表查询成功, total=" + r.path("data.total"));
    }

    @Test
    @Order(2)
    @DisplayName("5.1 BOM - 创建BOM")
    void testBomCreate() {
        Response r = authPost("/api/masterdata/bom", buildBomBody());
        assertSuccess(r, "BOM CREATE");
        r.then().body("data.bomId", notNullValue());
        r.then().body("data.bomCode", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        System.out.println("  BOM创建成功, bomId=" + r.path("data.bomId") + ", bomCode=" + r.path("data.bomCode"));
    }

    @Test
    @Order(3)
    @DisplayName("5.1 BOM - 按ID查询详情")
    void testBomGetById() {
        Long bomId = createBomAndGetId();
        Response r = authGet("/api/masterdata/bom/{id}", bomId);
        assertSuccess(r, "BOM GET BY ID");
        r.then().body("data.bomId", equalTo(bomId.intValue()));
    }

    @Test
    @Order(4)
    @DisplayName("5.1 BOM - 更新BOM")
    void testBomUpdate() {
        Long bomId = createBomAndGetId();
        Map<String, Object> body = buildBomBody();
        body.put("bomId", bomId);
        body.put("bomName", "更新后BOM名称");
        Response r = authPut("/api/masterdata/bom/{id}", body, bomId);
        assertSuccess(r, "BOM UPDATE");
        // 验证更新结果
        Response getResp = authGet("/api/masterdata/bom/{id}", bomId);
        getResp.then().body("data.bomName", equalTo("更新后BOM名称"));
    }

    @Test
    @Order(5)
    @DisplayName("5.1 BOM - 删除BOM")
    void testBomDelete() {
        Long bomId = createBomAndGetId();
        Response r = authDelete("/api/masterdata/bom/{id}", bomId);
        assertSuccess(r, "BOM DELETE");
    }

    // ==================================================================
    // 5.3 工作中心
    // ==================================================================

    @Test
    @Order(10)
    @DisplayName("5.3 工作中心 - 列表分页查询")
    void testWorkCenterListPage() {
        Response r = authGetWithParams("/api/production/work-center/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "WorkCenter LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(11)
    @DisplayName("5.3 工作中心 - 创建工作中心")
    void testWorkCenterCreate() {
        Response r = authPost("/api/production/work-center", buildWorkCenterBody());
        assertSuccess(r, "WorkCenter CREATE");
    }

    @Test
    @Order(12)
    @DisplayName("5.3 工作中心 - 按ID查询详情")
    void testWorkCenterGetById() {
        Long wcId = createWorkCenterAndGetId();
        Response r = authGet("/api/production/work-center/{id}", wcId);
        assertSuccess(r, "WorkCenter GET BY ID");
        r.then().body("data.workCenterId", equalTo(wcId.intValue()));
    }

    @Test
    @Order(13)
    @DisplayName("5.3 工作中心 - 更新工作中心")
    void testWorkCenterUpdate() {
        Long wcId = createWorkCenterAndGetId();
        Map<String, Object> body = buildWorkCenterBody();
        body.put("workCenterId", wcId);
        body.put("workCenterName", "更新后工作中心名称");
        Response r = authPut("/api/production/work-center/{id}", body, wcId);
        assertSuccess(r, "WorkCenter UPDATE");
        // 验证
        Response getResp = authGet("/api/production/work-center/{id}", wcId);
        getResp.then().body("data.workCenterName", equalTo("更新后工作中心名称"));
    }

    @Test
    @Order(14)
    @DisplayName("5.3 工作中心 - 删除工作中心")
    void testWorkCenterDelete() {
        Long wcId = createWorkCenterAndGetId();
        Response r = authDelete("/api/production/work-center/{id}", wcId);
        assertSuccess(r, "WorkCenter DELETE");
    }

    // ==================================================================
    // 5.2 工艺路线
    // ==================================================================

    @Test
    @Order(20)
    @DisplayName("5.2 工艺路线 - 列表分页查询")
    void testRoutingListPage() {
        Response r = authGetWithParams("/api/production/routings/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "Routing LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(21)
    @DisplayName("5.2 工艺路线 - 创建工艺路线")
    void testRoutingCreate() {
        Response r = authPost("/api/production/routings", buildRoutingBody());
        assertSuccess(r, "Routing CREATE");
        r.then().body("data.routingId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
    }

    @Test
    @Order(22)
    @DisplayName("5.2 工艺路线 - 按ID查询详情")
    void testRoutingGetById() {
        Long routingId = createRoutingAndGetId();
        Response r = authGet("/api/production/routings/{id}", routingId);
        assertSuccess(r, "Routing GET BY ID");
        r.then().body("data.routingId", equalTo(routingId.intValue()));
    }

    @Test
    @Order(23)
    @DisplayName("5.2 工艺路线 - 更新工艺路线")
    void testRoutingUpdate() {
        Long routingId = createRoutingAndGetId();
        Map<String, Object> body = buildRoutingBody();
        body.put("routingId", routingId);
        body.put("routingName", "更新后工艺路线名称");
        Response r = authPut("/api/production/routings/{id}", body, routingId);
        assertSuccess(r, "Routing UPDATE");
    }

    @Test
    @Order(24)
    @DisplayName("5.2 工艺路线 - 删除工艺路线")
    void testRoutingDelete() {
        Long routingId = createRoutingAndGetId();
        Response r = authDelete("/api/production/routings/{id}", routingId);
        assertSuccess(r, "Routing DELETE");
    }

    // ==================================================================
    // 5.3 工位
    // ==================================================================

    @Test
    @Order(30)
    @DisplayName("5.3 工位 - 列表分页查询")
    void testWorkstationListPage() {
        Response r = authGetWithParams("/api/production/workstation/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "Workstation LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(31)
    @DisplayName("5.3 工位 - 创建工位")
    void testWorkstationCreate() {
        Response r = authPost("/api/production/workstation", buildWorkstationBody());
        assertSuccess(r, "Workstation CREATE");
    }

    @Test
    @Order(32)
    @DisplayName("5.3 工位 - 按ID查询详情")
    void testWorkstationGetById() {
        Long wsId = createWorkstationAndGetId();
        Response r = authGet("/api/production/workstation/{id}", wsId);
        assertSuccess(r, "Workstation GET BY ID");
        r.then().body("data.workstationId", equalTo(wsId.intValue()));
    }

    @Test
    @Order(33)
    @DisplayName("5.3 工位 - 更新工位")
    void testWorkstationUpdate() {
        Long wsId = createWorkstationAndGetId();
        Map<String, Object> body = buildWorkstationBody();
        body.put("workstationId", wsId);
        body.put("workstationName", "更新后工位名称");
        Response r = authPut("/api/production/workstation/{id}", body, wsId);
        assertSuccess(r, "Workstation UPDATE");
    }

    @Test
    @Order(34)
    @DisplayName("5.3 工位 - 删除工位")
    void testWorkstationDelete() {
        Long wsId = createWorkstationAndGetId();
        Response r = authDelete("/api/production/workstation/{id}", wsId);
        assertSuccess(r, "Workstation DELETE");
    }

    // ==================================================================
    // 5.4 生产工单
    // ==================================================================

    @Test
    @Order(40)
    @DisplayName("5.4 生产工单 - 列表分页查询")
    void testWorkOrderListPage() {
        Response r = authGetWithParams("/api/production/workorders",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "WorkOrder LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(41)
    @DisplayName("5.4 生产工单 - 创建生产工单")
    void testWorkOrderCreate() {
        Response r = authPost("/api/production/workorders", buildWorkOrderBody());
        assertSuccess(r, "WorkOrder CREATE");
    }

    @Test
    @Order(42)
    @DisplayName("5.4 生产工单 - 按ID查询详情")
    void testWorkOrderGetById() {
        Long orderId = createWorkOrderAndGetId();
        Response r = authGet("/api/production/workorders/{id}", orderId);
        assertSuccess(r, "WorkOrder GET BY ID");
        r.then().body("data.orderId", equalTo(orderId.intValue()));
    }

    @Test
    @Order(43)
    @DisplayName("5.4 生产工单 - 更新生产工单")
    void testWorkOrderUpdate() {
        Long orderId = createWorkOrderAndGetId();
        Map<String, Object> body = buildWorkOrderBody();
        body.put("orderId", orderId);
        body.put("remark", "更新后备注");
        Response r = authPut("/api/production/workorders/{id}", body, orderId);
        assertSuccess(r, "WorkOrder UPDATE");
    }

    // ==================================================================
    // 5.6 工序汇报单
    // ==================================================================

    @Test
    @Order(60)
    @DisplayName("5.6 工序汇报单 - 列表分页查询")
    void testOperationReportListPage() {
        Response r = authGetWithParams("/api/production/operation-report/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "OperationReport LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(61)
    @DisplayName("5.6 工序汇报单 - 创建工序汇报单")
    void testOperationReportCreate() {
        Response r = authPost("/api/production/operation-report", buildOperationReportBody());
        assertSuccess(r, "OperationReport CREATE");
        r.then().body("data.reportId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
    }

    @Test
    @Order(62)
    @DisplayName("5.6 工序汇报单 - 按ID查询详情")
    void testOperationReportGetById() {
        Long reportId = createOperationReportAndGetId();
        Response r = authGet("/api/production/operation-report/{id}", reportId);
        assertSuccess(r, "OperationReport GET BY ID");
        r.then().body("data.reportId", equalTo(reportId.intValue()));
    }

    @Test
    @Order(63)
    @DisplayName("5.6 工序汇报单 - 更新工序汇报单")
    void testOperationReportUpdate() {
        Long reportId = createOperationReportAndGetId();
        Map<String, Object> body = buildOperationReportBody();
        body.put("reportId", reportId);
        body.put("remark", "更新后备注");
        Response r = authPut("/api/production/operation-report/{id}", body, reportId);
        assertSuccess(r, "OperationReport UPDATE");
    }

    @Test
    @Order(64)
    @DisplayName("5.6 工序汇报单 - 删除工序汇报单")
    void testOperationReportDelete() {
        Long reportId = createOperationReportAndGetId();
        Response r = authDelete("/api/production/operation-report/{id}", reportId);
        assertSuccess(r, "OperationReport DELETE");
    }

    // ==================================================================
    // 5.7 委外加工单
    // ==================================================================

    @Test
    @Order(70)
    @DisplayName("5.7 委外加工单 - 列表分页查询")
    void testSubcontractListPage() {
        Response r = authGetWithParams("/api/production/subcontract/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "SubcontractOrder LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(71)
    @DisplayName("5.7 委外加工单 - 创建委外加工单")
    void testSubcontractCreate() {
        Response r = authPost("/api/production/subcontract", buildSubcontractBody());
        assertSuccess(r, "SubcontractOrder CREATE");
        r.then().body("data.subcontractId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        // 验证金额计算: totalPrice = qty × unitPrice
        r.then().body("data.totalPrice", comparesEqualTo(1000.0f));
    }

    @Test
    @Order(72)
    @DisplayName("5.7 委外加工单 - 按ID查询详情")
    void testSubcontractGetById() {
        Long subcontractId = createSubcontractAndGetId();
        Response r = authGet("/api/production/subcontract/{id}", subcontractId);
        assertSuccess(r, "SubcontractOrder GET BY ID");
        r.then().body("data.subcontractId", equalTo(subcontractId.intValue()));
    }

    @Test
    @Order(73)
    @DisplayName("5.7 委外加工单 - 更新委外加工单")
    void testSubcontractUpdate() {
        Long subcontractId = createSubcontractAndGetId();
        Map<String, Object> body = buildSubcontractBody();
        body.put("subcontractId", subcontractId);
        body.put("remark", "更新后备注");
        Response r = authPut("/api/production/subcontract/{id}", body, subcontractId);
        assertSuccess(r, "SubcontractOrder UPDATE");
    }

    @Test
    @Order(74)
    @DisplayName("5.7 委外加工单 - 删除委外加工单")
    void testSubcontractDelete() {
        Long subcontractId = createSubcontractAndGetId();
        Response r = authDelete("/api/production/subcontract/{id}", subcontractId);
        assertSuccess(r, "SubcontractOrder DELETE");
    }

    // ==================================================================
    // 5.8 MRP运算方案
    // ==================================================================

    @Test
    @Order(80)
    @DisplayName("5.8 MRP方案 - 列表分页查询")
    void testMrpPlanListPage() {
        Response r = authGetWithParams("/api/production/mrp-plan/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "MrpPlan LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(81)
    @DisplayName("5.8 MRP方案 - 创建MRP方案")
    void testMrpPlanCreate() {
        Response r = authPost("/api/production/mrp-plan", buildMrpPlanBody());
        assertSuccess(r, "MrpPlan CREATE");
        r.then().body("data.planId", notNullValue());
        r.then().body("data.planStatus", equalTo("草稿"));
    }

    @Test
    @Order(82)
    @DisplayName("5.8 MRP方案 - 按ID查询详情")
    void testMrpPlanGetById() {
        Long planId = createMrpPlanAndGetId();
        Response r = authGet("/api/production/mrp-plan/{id}", planId);
        assertSuccess(r, "MrpPlan GET BY ID");
        r.then().body("data.planId", equalTo(planId.intValue()));
    }

    @Test
    @Order(83)
    @DisplayName("5.8 MRP方案 - 更新MRP方案")
    void testMrpPlanUpdate() {
        Long planId = createMrpPlanAndGetId();
        Map<String, Object> body = buildMrpPlanBody();
        body.put("planId", planId);
        body.put("planName", "更新后MRP方案名称");
        Response r = authPut("/api/production/mrp-plan/{id}", body, planId);
        assertSuccess(r, "MrpPlan UPDATE");
    }

    @Test
    @Order(84)
    @DisplayName("5.8 MRP方案 - 删除MRP方案")
    void testMrpPlanDelete() {
        Long planId = createMrpPlanAndGetId();
        Response r = authDelete("/api/production/mrp-plan/{id}", planId);
        assertSuccess(r, "MrpPlan DELETE");
    }

    // ==================================================================
    // 生产入库单
    // ==================================================================

    @Test
    @Order(90)
    @DisplayName("生产入库单 - 列表分页查询")
    void testProductionReceiptListPage() {
        Response r = authGetWithParams("/api/production/receipts/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "ProductionReceipt LIST/PAGE");
        r.then().body("data", notNullValue());
    }

    @Test
    @Order(91)
    @DisplayName("生产入库单 - 创建生产入库单")
    void testProductionReceiptCreate() {
        Response r = authPost("/api/production/receipts", buildProductionReceiptBody());
        assertSuccess(r, "ProductionReceipt CREATE");
        r.then().body("data.receiptId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
    }

    @Test
    @Order(92)
    @DisplayName("生产入库单 - 按ID查询详情")
    void testProductionReceiptGetById() {
        Long receiptId = createProductionReceiptAndGetId();
        Response r = authGet("/api/production/receipts/{id}", receiptId);
        assertSuccess(r, "ProductionReceipt GET BY ID");
        r.then().body("data.receiptId", equalTo(receiptId.intValue()));
    }

    @Test
    @Order(93)
    @DisplayName("生产入库单 - 更新生产入库单")
    void testProductionReceiptUpdate() {
        Long receiptId = createProductionReceiptAndGetId();
        Map<String, Object> body = buildProductionReceiptBody();
        body.put("receiptId", receiptId);
        body.put("remark", "更新后备注");
        Response r = authPut("/api/production/receipts/{id}", body, receiptId);
        assertSuccess(r, "ProductionReceipt UPDATE");
    }

    @Test
    @Order(94)
    @DisplayName("生产入库单 - 删除生产入库单")
    void testProductionReceiptDelete() {
        Long receiptId = createProductionReceiptAndGetId();
        Response r = authDelete("/api/production/receipts/{id}", receiptId);
        assertSuccess(r, "ProductionReceipt DELETE");
    }

    // ==================================================================
    // 仪表盘统计接口
    // ==================================================================

    @Test
    @Order(100)
    @DisplayName("生产仪表盘 - 统计接口")
    void testProductionDashboard() {
        Response r = authGet("/api/production/dashboard");
        assertSuccess(r, "Dashboard");
        r.then().body("data", notNullValue());
        System.out.println("  仪表盘数据: " + r.path("data"));
    }
}
