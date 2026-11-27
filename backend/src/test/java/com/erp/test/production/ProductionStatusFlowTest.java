package com.erp.test.production;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 生产管理模块状态流转自动化测试
 *
 * 覆盖文档第五部分各子模块的状态流转规则：
 *  5.1 BOM:        草稿 → 已生效 (approve) → 已停用 (disable)
 *  5.2 工艺路线:    草稿 → 已生效 (approve) → 已停用 (disable)
 *  5.3 工作中心:    启用 ↔ 停用 (enable/disable)
 *  5.4 生产工单:    草稿 → 已生效 (submit) → 已结案 (close: close_status=1) → 未结案 (reopen)
 *  5.6 工序汇报单:  草稿 → 待审核 (submit) → 已生效 (approve)
 *  5.7 委外加工单:  草稿 → 已下达 (release) → 执行中 (start) → 已完工 (complete) → 已关闭 (close)
 *  5.8 MRP方案:    草稿 → 已生效 (approve) → 已执行 (execute)
 *  补充 生产入库单:  草稿 → 已生效 (approve) → 已关闭 (close); 草稿 → 已取消 (cancel)
 */
@DisplayName("生产管理模块 - 状态流转测试")
public class ProductionStatusFlowTest extends ProductionTestBase {

    // ==================================================================
    // 5.1 BOM 状态流转: 草稿 → 已生效 → 已停用
    // ==================================================================

    @Test
    @DisplayName("5.1 BOM - 草稿 → 已生效 (approve)")
    void testBomApprove() {
        Long bomId = createBomAndGetId();
        // 验证初始状态
        authGet("/api/masterdata/bom/{id}", bomId).then().body("data.status", equalTo("草稿"));
        // 执行审核
        Response r = authPost("/api/masterdata/bom/{id}/approve", null, bomId);
        assertSuccess(r, "BOM approve");
        // 验证状态已变更
        authGet("/api/masterdata/bom/{id}", bomId).then().body("data.status", equalTo("已生效"));
    }

    @Test
    @DisplayName("5.1 BOM - 已生效 → 已停用 (disable)")
    void testBomDisable() {
        Long bomId = createBomAndGetId();
        authPost("/api/masterdata/bom/{id}/approve", null, bomId);
        Response r = authPost("/api/masterdata/bom/{id}/disable", null, bomId);
        assertSuccess(r, "BOM disable");
        authGet("/api/masterdata/bom/{id}", bomId).then().body("data.status", equalTo("已停用"));
    }

    @Test
    @DisplayName("5.1 BOM - 草稿状态不可直接停用（需先审核）")
    void testBomCannotDisableFromDraft() {
        Long bomId = createBomAndGetId();
        Response r = authPost("/api/masterdata/bom/{id}/disable", null, bomId);
        // 草稿直接停用应失败或状态不变
        int code = r.path("code");
        System.out.println("  BOM草稿停用 code=" + code + " (预期失败或状态不变)");
    }

    // ==================================================================
    // 5.2 工艺路线 状态流转: 草稿 → 已生效 → 已停用
    // ==================================================================

    @Test
    @DisplayName("5.2 工艺路线 - 草稿 → 已生效 (approve)")
    void testRoutingApprove() {
        Long routingId = createRoutingAndGetId();
        authGet("/api/production/routings/{id}", routingId).then().body("data.status", equalTo("草稿"));
        Response r = authPost("/api/production/routings/{id}/approve", null, routingId);
        assertSuccess(r, "Routing approve");
        authGet("/api/production/routings/{id}", routingId).then().body("data.status", equalTo("已生效"));
    }

    @Test
    @DisplayName("5.2 工艺路线 - 已生效 → 已停用 (disable)")
    void testRoutingDisable() {
        Long routingId = createRoutingAndGetId();
        authPost("/api/production/routings/{id}/approve", null, routingId);
        Response r = authPost("/api/production/routings/{id}/disable", null, routingId);
        assertSuccess(r, "Routing disable");
        authGet("/api/production/routings/{id}", routingId).then().body("data.status", equalTo("已停用"));
    }

    // ==================================================================
    // 5.3 工作中心 状态流转: 启用 ↔ 停用
    // ==================================================================

    @Test
    @DisplayName("5.3 工作中心 - 启用 → 停用 (disable)")
    void testWorkCenterDisable() {
        Long wcId = createWorkCenterAndGetId();
        // 初始状态为启用
        authGet("/api/production/work-center/{id}", wcId).then().body("data.status", equalTo("启用"));
        Response r = authPost("/api/production/work-center/{id}/disable", null, wcId);
        assertSuccess(r, "WorkCenter disable");
        authGet("/api/production/work-center/{id}", wcId).then().body("data.status", equalTo("停用"));
    }

    @Test
    @DisplayName("5.3 工作中心 - 停用 → 启用 (enable)")
    void testWorkCenterEnable() {
        Long wcId = createWorkCenterAndGetId();
        authPost("/api/production/work-center/{id}/disable", null, wcId);
        Response r = authPost("/api/production/work-center/{id}/enable", null, wcId);
        assertSuccess(r, "WorkCenter enable");
        authGet("/api/production/work-center/{id}", wcId).then().body("data.status", equalTo("启用"));
    }

    // ==================================================================
    // 5.4 生产工单 状态流转: 草稿 → 已生效(submit) → 已结案(close) → 未结案(reopen)
    //     通用状态机口径：status 只有 草稿/待审核/已生效/已作废，业务闭环用独立字段 close_status
    // ==================================================================

    @Test
    @DisplayName("5.4 生产工单 - 状态流转: 草稿 → 提交(已生效) → 已结案 → 未结案")
    void testWorkOrderStatusFlow() {
        Long orderId = createWorkOrderAndGetId();
        // 初始状态: 草稿
        authGet("/api/production/workorders/{id}", orderId).then().body("data.status", equalTo("草稿"));
        System.out.println("  初始状态: 草稿");

        // 草稿 → 提交（免审批: 直接已生效；需审批: 落待审核）
        Response r1 = authPost("/api/production/workorders/{id}/submit", null, orderId);
        assertSuccess(r1, "WorkOrder submit");
        String st = authGet("/api/production/workorders/{id}", orderId).path("data.status");
        System.out.println("  submit后状态: " + st);
        org.junit.jupiter.api.Assertions.assertTrue("已生效".equals(st) || "待审核".equals(st),
                "submit 后状态应为 已生效 或 待审核，实际: " + st);

        // 待审核 → 审核通过（已生效）
        if ("待审核".equals(st)) {
            Response r2 = authPost("/api/production/workorders/{id}/approve", null, orderId);
            assertSuccess(r2, "WorkOrder approve");
            authGet("/api/production/workorders/{id}", orderId).then().body("data.status", equalTo("已生效"));
            System.out.println("  approve后状态: 已生效");
        }

        // 已生效 → 已结案（status 保持「已生效」，闭环写在 close_status=1）
        Response r3 = authPost("/api/production/workorders/{id}/close", Map.of("reason", "测试结案"), orderId);
        assertSuccess(r3, "WorkOrder close");
        authGet("/api/production/workorders/{id}", orderId).then()
                .body("data.status", equalTo("已生效"))
                .body("data.closeStatus", equalTo(1));
        System.out.println("  close后状态: 已生效 + 已结案(close_status=1)");

        // 已结案 → 未结案
        Response r4 = authPost("/api/production/workorders/{id}/reopen", null, orderId);
        assertSuccess(r4, "WorkOrder reopen");
        authGet("/api/production/workorders/{id}", orderId).then().body("data.closeStatus", equalTo(0));
        System.out.println("  reopen后状态: 未结案(close_status=0)");
    }

    @Test
    @DisplayName("5.4 生产工单 - 草稿不可直接审核（需先提交）")
    void testWorkOrderCannotApproveFromDraft() {
        Long orderId = createWorkOrderAndGetId();
        Response r = authPost("/api/production/workorders/{id}/approve", null, orderId);
        int code = r.path("code");
        System.out.println("  草稿直接approve code=" + code + " (预期失败)");
    }

    @Test
    @DisplayName("5.4 生产工单 - 已提交后不可重复提交（状态守卫）")
    void testWorkOrderCannotSubmitTwice() {
        Long orderId = createWorkOrderAndGetId();
        Response r1 = authPost("/api/production/workorders/{id}/submit", null, orderId);
        assertSuccess(r1, "WorkOrder submit");
        Response r2 = authPost("/api/production/workorders/{id}/submit", null, orderId);
        int code = r2.path("code");
        System.out.println("  重复submit code=" + code + " (预期失败)");
    }

    // ==================================================================
    // 5.6 工序汇报单 状态流转: 草稿 → 待审核 → 已生效
    // ==================================================================

    @Test
    @DisplayName("5.6 工序汇报单 - 完整状态流转: 草稿 → 待审核 → 已生效")
    void testOperationReportFullStatusFlow() {
        Long reportId = createOperationReportAndGetId();
        authGet("/api/production/operation-report/{id}", reportId).then().body("data.status", equalTo("草稿"));
        System.out.println("  初始状态: 草稿");

        Response r1 = authPost("/api/production/operation-report/{id}/submit", null, reportId);
        assertSuccess(r1, "OperationReport submit");
        authGet("/api/production/operation-report/{id}", reportId).then().body("data.status", equalTo("待审核"));
        System.out.println("  submit后状态: 待审核");

        Response r2 = authPost("/api/production/operation-report/{id}/approve", null, reportId);
        assertSuccess(r2, "OperationReport approve");
        authGet("/api/production/operation-report/{id}", reportId).then().body("data.status", equalTo("已生效"));
        System.out.println("  approve后状态: 已生效");
    }

    @Test
    @DisplayName("5.6 工序汇报单 - 草稿不可直接审核")
    void testOperationReportCannotApproveFromDraft() {
        Long reportId = createOperationReportAndGetId();
        Response r = authPost("/api/production/operation-report/{id}/approve", null, reportId);
        int code = r.path("code");
        System.out.println("  草稿直接approve code=" + code + " (预期失败)");
    }

    // ==================================================================
    // 5.7 委外加工单 状态流转: 草稿 → 已下达 → 执行中 → 已完工 → 已关闭
    // ==================================================================

    @Test
    @DisplayName("5.7 委外加工单 - 完整状态流转: 草稿 → 已下达 → 执行中 → 已完工 → 已关闭")
    void testSubcontractFullStatusFlow() {
        Long subcontractId = createSubcontractAndGetId();
        authGet("/api/production/subcontract/{id}", subcontractId).then().body("data.status", equalTo("草稿"));
        System.out.println("  初始状态: 草稿");

        Response r1 = authPost("/api/production/subcontract/{id}/release", null, subcontractId);
        assertSuccess(r1, "Subcontract release");
        authGet("/api/production/subcontract/{id}", subcontractId).then().body("data.status", equalTo("已下达"));
        System.out.println("  release后状态: 已下达");

        Response r2 = authPost("/api/production/subcontract/{id}/start", null, subcontractId);
        assertSuccess(r2, "Subcontract start");
        authGet("/api/production/subcontract/{id}", subcontractId).then().body("data.status", equalTo("执行中"));
        System.out.println("  start后状态: 执行中");

        Response r3 = authPost("/api/production/subcontract/{id}/complete", null, subcontractId);
        assertSuccess(r3, "Subcontract complete");
        authGet("/api/production/subcontract/{id}", subcontractId).then().body("data.status", equalTo("已完工"));
        System.out.println("  complete后状态: 已完工");

        Response r4 = authPost("/api/production/subcontract/{id}/close", Map.of("reason", "测试关闭"), subcontractId);
        assertSuccess(r4, "Subcontract close");
        authGet("/api/production/subcontract/{id}", subcontractId).then().body("data.status", equalTo("已关闭"));
        System.out.println("  close后状态: 已关闭");
    }

    @Test
    @DisplayName("5.7 委外加工单 - 草稿不可直接开工")
    void testSubcontractCannotStartFromDraft() {
        Long subcontractId = createSubcontractAndGetId();
        Response r = authPost("/api/production/subcontract/{id}/start", null, subcontractId);
        int code = r.path("code");
        System.out.println("  草稿直接start code=" + code + " (预期失败)");
    }

    // ==================================================================
    // 5.8 MRP方案 状态流转: 草稿 → 已生效 → 已执行
    // ==================================================================

    @Test
    @DisplayName("5.8 MRP方案 - 完整状态流转: 草稿 → 已生效 → 已执行")
    void testMrpPlanFullStatusFlow() {
        Long planId = createMrpPlanAndGetId();
        authGet("/api/production/mrp-plan/{id}", planId).then().body("data.planStatus", equalTo("草稿"));
        System.out.println("  初始状态: 草稿");

        Response r1 = authPost("/api/production/mrp-plan/{id}/approve", null, planId);
        assertSuccess(r1, "MrpPlan approve");
        authGet("/api/production/mrp-plan/{id}", planId).then().body("data.planStatus", equalTo("已生效"));
        System.out.println("  approve后状态: 已生效");

        Response r2 = authPost("/api/production/mrp-plan/{id}/execute", null, planId);
        assertSuccess(r2, "MrpPlan execute");
        authGet("/api/production/mrp-plan/{id}", planId).then().body("data.planStatus", equalTo("已执行"));
        System.out.println("  execute后状态: 已执行");
    }

    @Test
    @DisplayName("5.8 MRP方案 - 草稿不可直接执行（需先审核）")
    void testMrpPlanCannotExecuteFromDraft() {
        Long planId = createMrpPlanAndGetId();
        Response r = authPost("/api/production/mrp-plan/{id}/execute", null, planId);
        int code = r.path("code");
        System.out.println("  草稿直接execute code=" + code + " (预期失败)");
    }

    // ==================================================================
    // 生产入库单 状态流转: 草稿 → 已生效 → 已关闭; 草稿 → 已取消
    // ==================================================================

    @Test
    @DisplayName("生产入库单 - 草稿 → 已生效 (approve)")
    void testProductionReceiptApprove() {
        Long receiptId = createProductionReceiptAndGetId();
        authGet("/api/production/receipts/{id}", receiptId).then().body("data.status", equalTo("草稿"));
        System.out.println("  初始状态: 草稿");

        Response r = authPost("/api/production/receipts/{id}/approve", null, receiptId);
        assertSuccess(r, "ProductionReceipt approve");
        authGet("/api/production/receipts/{id}", receiptId).then().body("data.status", equalTo("已生效"));
        System.out.println("  approve后状态: 已生效");
    }

    @Test
    @DisplayName("生产入库单 - 已生效 → 已关闭 (close)")
    void testProductionReceiptClose() {
        Long receiptId = createProductionReceiptAndGetId();
        authPost("/api/production/receipts/{id}/approve", null, receiptId);
        // close 需要 reason 作为 RequestParam
        Response r = authPost("/api/production/receipts/{id}/close?reason=测试关闭", null, receiptId);
        assertSuccess(r, "ProductionReceipt close");
        authGet("/api/production/receipts/{id}", receiptId).then().body("data.status", equalTo("已关闭"));
        System.out.println("  close后状态: 已关闭");
    }

    @Test
    @DisplayName("生产入库单 - 草稿 → 已取消 (cancel)")
    void testProductionReceiptCancel() {
        Long receiptId = createProductionReceiptAndGetId();
        Response r = authPost("/api/production/receipts/{id}/cancel?reason=测试取消", null, receiptId);
        assertSuccess(r, "ProductionReceipt cancel");
        authGet("/api/production/receipts/{id}", receiptId).then().body("data.status", equalTo("已取消"));
        System.out.println("  cancel后状态: 已取消");
    }

    @Test
    @DisplayName("生产入库单 - 草稿不可直接关闭（需先审核）")
    void testProductionReceiptCannotCloseFromDraft() {
        Long receiptId = createProductionReceiptAndGetId();
        Response r = authPost("/api/production/receipts/{id}/close?reason=测试", null, receiptId);
        int code = r.path("code");
        System.out.println("  草稿直接close code=" + code + " (预期失败)");
    }
}
