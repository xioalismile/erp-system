package com.erp.test.inventory;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 库存管理模块状态流转自动化测试
 *
 * 覆盖 5 类库存单据的审批状态机：
 *  通用流转: 草稿 →(submit)→ 待审核 →(approve)→ 已生效 →(close)→ 已关闭
 *           草稿 →(cancel)→ 已取消
 *           已生效 →(reverse-approve)→ 待审核 (重置草稿，回滚库存)
 *
 *  盘点单特有: 草稿 →(start-check)→ 盘点中 →(finish-check)→ 已盘点 →(submit)→ 待审核
 *
 * 校验规则：
 *  - 草稿不可直接 approve (必须先 submit)
 *  - 草稿不可 reverse-approve
 *  - 已生效不可直接修改 (update 应失败)
 *  - 明细 lineNo 从 1 开始递增
 */
@DisplayName("库存管理模块 - 状态流转测试")
public class InventoryStatusFlowTest extends InventoryTestBase {

    // ==================================================================
    // 1. 其他入库单 OtherReceipt 状态流转
    // ==================================================================

    @Test
    @DisplayName("其他入库单 - 草稿 → 待审核 (submit)")
    void testOtherReceiptSubmitForApproval() {
        Long id = createOtherReceiptAndGetId();
        authGet("/api/inventory/other-receipts/{id}", id).then().body("data.status", equalTo("草稿"));
        Response r = authPost("/api/inventory/other-receipts/{id}/submit", null, id);
        assertSuccess(r, "OtherReceipt submit");
        assertStatus(authGet("/api/inventory/other-receipts/{id}", id), "待审核");
    }

    @Test
    @DisplayName("其他入库单 - 草稿不可直接审核（需先提交）")
    void testOtherReceiptApproveFromDraftFails() {
        Long id = createOtherReceiptAndGetId();
        Response r = authPost("/api/inventory/other-receipts/{id}/approve", null, id);
        assertFailed(r, "OtherReceipt approve from draft");
    }

    @Test
    @DisplayName("其他入库单 - 草稿 → 待审核 → 已生效")
    void testOtherReceiptApproveFromPending() {
        Long id = createOtherReceiptAndGetId();
        authPost("/api/inventory/other-receipts/{id}/submit", null, id);
        Response r = authPost("/api/inventory/other-receipts/{id}/approve", null, id);
        assertSuccess(r, "OtherReceipt approve");
        Response get = authGet("/api/inventory/other-receipts/{id}", id);
        assertStatus(get, "已生效");
        get.then().body("data.approvedBy", notNullValue());
        get.then().body("data.approvedAt", notNullValue());
    }

    @Test
    @DisplayName("其他入库单 - 重置草稿: 已生效 → 待审核 (approvedBy/approvedAt 清空)")
    void testOtherReceiptReverseApprove() {
        Long id = createOtherReceiptAndGetId();
        authPost("/api/inventory/other-receipts/{id}/submit", null, id);
        authPost("/api/inventory/other-receipts/{id}/approve", null, id);
        Response r = authPost("/api/inventory/other-receipts/{id}/reverse-approve", null, id);
        assertSuccess(r, "OtherReceipt reverse-approve");
        Response get = authGet("/api/inventory/other-receipts/{id}", id);
        assertStatus(get, "待审核");
        get.then().body("data.approvedBy", nullValue());
        get.then().body("data.approvedAt", nullValue());
    }

    @Test
    @DisplayName("其他入库单 - 草稿不可重置草稿")
    void testOtherReceiptReverseApproveFromDraftFails() {
        Long id = createOtherReceiptAndGetId();
        Response r = authPost("/api/inventory/other-receipts/{id}/reverse-approve", null, id);
        assertFailed(r, "OtherReceipt reverse-approve from draft");
    }

    @Test
    @DisplayName("其他入库单 - 已生效 → 已关闭 (close)")
    void testOtherReceiptClose() {
        Long id = createOtherReceiptAndGetId();
        authPost("/api/inventory/other-receipts/{id}/submit", null, id);
        authPost("/api/inventory/other-receipts/{id}/approve", null, id);
        Response r = authPost("/api/inventory/other-receipts/{id}/close?reason=测试关闭", null, id);
        assertSuccess(r, "OtherReceipt close");
        assertStatus(authGet("/api/inventory/other-receipts/{id}", id), "已关闭");
    }

    @Test
    @DisplayName("其他入库单 - 草稿 → 已取消 (cancel)")
    void testOtherReceiptCancel() {
        Long id = createOtherReceiptAndGetId();
        Response r = authPost("/api/inventory/other-receipts/{id}/cancel?reason=测试取消", null, id);
        assertSuccess(r, "OtherReceipt cancel");
        assertStatus(authGet("/api/inventory/other-receipts/{id}", id), "已取消");
    }

    @Test
    @DisplayName("其他入库单 - 已生效不可修改（update 应失败）")
    void testOtherReceiptModifyApprovedFails() {
        Long id = createOtherReceiptAndGetId();
        authPost("/api/inventory/other-receipts/{id}/submit", null, id);
        authPost("/api/inventory/other-receipts/{id}/approve", null, id);
        Map<String, Object> body = buildOtherReceiptBody();
        body.put("receiptId", id);
        body.put("remark", "尝试修改已生效单据");
        Response r = authPut("/api/inventory/other-receipts/{id}", body, id);
        assertFailed(r, "OtherReceipt modify approved");
    }

    @Test
    @DisplayName("其他入库单 - 明细行号从1开始递增")
    void testOtherReceiptLineNoStartsAt1() {
        Long id = createOtherReceiptAndGetId();
        Response r = authGet("/api/inventory/other-receipts/{id}", id);
        assertSuccess(r, "OtherReceipt lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"));
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"));
    }

    // ==================================================================
    // 2. 其他出库单 OtherIssue 状态流转
    // ==================================================================

    @Test
    @DisplayName("其他出库单 - 草稿 → 待审核 (submit)")
    void testOtherIssueSubmitForApproval() {
        Long id = createOtherIssueAndGetId();
        Response r = authPost("/api/inventory/other-issues/{id}/submit", null, id);
        assertSuccess(r, "OtherIssue submit");
        assertStatus(authGet("/api/inventory/other-issues/{id}", id), "待审核");
    }

    @Test
    @DisplayName("其他出库单 - 草稿不可直接审核")
    void testOtherIssueApproveFromDraftFails() {
        Long id = createOtherIssueAndGetId();
        Response r = authPost("/api/inventory/other-issues/{id}/approve", null, id);
        assertFailed(r, "OtherIssue approve from draft");
    }

    @Test
    @DisplayName("其他出库单 - 草稿 → 待审核 → 已生效")
    void testOtherIssueApproveFromPending() {
        // 出库审核会扣减库存，先预备足量库存（batchNo 与出库明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/other-issues", buildOtherIssueBody(batch));
        Long id = ((Number) createResp.path("data.issueId")).longValue();
        authPost("/api/inventory/other-issues/{id}/submit", null, id);
        Response r = authPost("/api/inventory/other-issues/{id}/approve", null, id);
        assertSuccess(r, "OtherIssue approve");
        assertStatus(authGet("/api/inventory/other-issues/{id}", id), "已生效");
    }

    @Test
    @DisplayName("其他出库单 - 重置草稿: 已生效 → 待审核")
    void testOtherIssueReverseApprove() {
        // 出库审核会扣减库存，先预备足量库存（batchNo 与出库明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/other-issues", buildOtherIssueBody(batch));
        Long id = ((Number) createResp.path("data.issueId")).longValue();
        authPost("/api/inventory/other-issues/{id}/submit", null, id);
        authPost("/api/inventory/other-issues/{id}/approve", null, id);
        Response r = authPost("/api/inventory/other-issues/{id}/reverse-approve", null, id);
        assertSuccess(r, "OtherIssue reverse-approve");
        Response get = authGet("/api/inventory/other-issues/{id}", id);
        assertStatus(get, "待审核");
        get.then().body("data.approvedBy", nullValue());
        get.then().body("data.approvedAt", nullValue());
    }

    @Test
    @DisplayName("其他出库单 - 草稿不可重置草稿")
    void testOtherIssueReverseApproveFromDraftFails() {
        Long id = createOtherIssueAndGetId();
        Response r = authPost("/api/inventory/other-issues/{id}/reverse-approve", null, id);
        assertFailed(r, "OtherIssue reverse-approve from draft");
    }

    @Test
    @DisplayName("其他出库单 - 已生效 → 已关闭")
    void testOtherIssueClose() {
        // 出库审核会扣减库存，先预备足量库存（batchNo 与出库明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/other-issues", buildOtherIssueBody(batch));
        Long id = ((Number) createResp.path("data.issueId")).longValue();
        authPost("/api/inventory/other-issues/{id}/submit", null, id);
        authPost("/api/inventory/other-issues/{id}/approve", null, id);
        Response r = authPost("/api/inventory/other-issues/{id}/close?reason=测试关闭", null, id);
        assertSuccess(r, "OtherIssue close");
        assertStatus(authGet("/api/inventory/other-issues/{id}", id), "已关闭");
    }

    @Test
    @DisplayName("其他出库单 - 草稿 → 已取消")
    void testOtherIssueCancel() {
        Long id = createOtherIssueAndGetId();
        Response r = authPost("/api/inventory/other-issues/{id}/cancel?reason=测试取消", null, id);
        assertSuccess(r, "OtherIssue cancel");
        assertStatus(authGet("/api/inventory/other-issues/{id}", id), "已取消");
    }

    @Test
    @DisplayName("其他出库单 - 已生效不可修改")
    void testOtherIssueModifyApprovedFails() {
        // 出库审核会扣减库存，先预备足量库存（batchNo 与出库明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/other-issues", buildOtherIssueBody(batch));
        Long id = ((Number) createResp.path("data.issueId")).longValue();
        authPost("/api/inventory/other-issues/{id}/submit", null, id);
        authPost("/api/inventory/other-issues/{id}/approve", null, id);
        Map<String, Object> body = buildOtherIssueBody(batch);
        body.put("issueId", id);
        body.put("remark", "尝试修改已生效单据");
        Response r = authPut("/api/inventory/other-issues/{id}", body, id);
        assertFailed(r, "OtherIssue modify approved");
    }

    @Test
    @DisplayName("其他出库单 - 明细行号从1开始递增")
    void testOtherIssueLineNoStartsAt1() {
        Long id = createOtherIssueAndGetId();
        Response r = authGet("/api/inventory/other-issues/{id}", id);
        assertSuccess(r, "OtherIssue lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"));
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"));
    }

    // ==================================================================
    // 3. 生产领料单 MaterialRequisition 状态流转
    // ==================================================================

    @Test
    @DisplayName("生产领料单 - 草稿 → 待审核 (submit)")
    void testMaterialRequisitionSubmitForApproval() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authPost("/api/inventory/material-requisitions/{id}/submit", null, id);
        assertSuccess(r, "MaterialRequisition submit");
        assertStatus(authGet("/api/inventory/material-requisitions/{id}", id), "待审核");
    }

    @Test
    @DisplayName("生产领料单 - 草稿不可直接审核")
    void testMaterialRequisitionApproveFromDraftFails() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authPost("/api/inventory/material-requisitions/{id}/approve", null, id);
        assertFailed(r, "MaterialRequisition approve from draft");
    }

    @Test
    @DisplayName("生产领料单 - 草稿 → 待审核 → 已生效")
    void testMaterialRequisitionApproveFromPending() {
        // 领料审核会扣减库存，先预备足量库存（batchNo 与领料明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/material-requisitions", buildMaterialRequisitionBody(batch));
        Long id = ((Number) createResp.path("data.requisitionId")).longValue();
        authPost("/api/inventory/material-requisitions/{id}/submit", null, id);
        Response r = authPost("/api/inventory/material-requisitions/{id}/approve", null, id);
        assertSuccess(r, "MaterialRequisition approve");
        assertStatus(authGet("/api/inventory/material-requisitions/{id}", id), "已生效");
    }

    @Test
    @DisplayName("生产领料单 - 重置草稿: 已生效 → 待审核")
    void testMaterialRequisitionReverseApprove() {
        // 领料审核会扣减库存，先预备足量库存（batchNo 与领料明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/material-requisitions", buildMaterialRequisitionBody(batch));
        Long id = ((Number) createResp.path("data.requisitionId")).longValue();
        authPost("/api/inventory/material-requisitions/{id}/submit", null, id);
        authPost("/api/inventory/material-requisitions/{id}/approve", null, id);
        Response r = authPost("/api/inventory/material-requisitions/{id}/reverse-approve", null, id);
        assertSuccess(r, "MaterialRequisition reverse-approve");
        Response get = authGet("/api/inventory/material-requisitions/{id}", id);
        assertStatus(get, "待审核");
        get.then().body("data.approvedBy", nullValue());
        get.then().body("data.approvedAt", nullValue());
    }

    @Test
    @DisplayName("生产领料单 - 草稿不可重置草稿")
    void testMaterialRequisitionReverseApproveFromDraftFails() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authPost("/api/inventory/material-requisitions/{id}/reverse-approve", null, id);
        assertFailed(r, "MaterialRequisition reverse-approve from draft");
    }

    @Test
    @DisplayName("生产领料单 - 已生效 → 已关闭")
    void testMaterialRequisitionClose() {
        // 领料审核会扣减库存，先预备足量库存（batchNo 与领料明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/material-requisitions", buildMaterialRequisitionBody(batch));
        Long id = ((Number) createResp.path("data.requisitionId")).longValue();
        authPost("/api/inventory/material-requisitions/{id}/submit", null, id);
        authPost("/api/inventory/material-requisitions/{id}/approve", null, id);
        Response r = authPost("/api/inventory/material-requisitions/{id}/close?reason=测试关闭", null, id);
        assertSuccess(r, "MaterialRequisition close");
        assertStatus(authGet("/api/inventory/material-requisitions/{id}", id), "已关闭");
    }

    @Test
    @DisplayName("生产领料单 - 草稿 → 已取消")
    void testMaterialRequisitionCancel() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authPost("/api/inventory/material-requisitions/{id}/cancel?reason=测试取消", null, id);
        assertSuccess(r, "MaterialRequisition cancel");
        assertStatus(authGet("/api/inventory/material-requisitions/{id}", id), "已取消");
    }

    @Test
    @DisplayName("生产领料单 - 已生效不可修改")
    void testMaterialRequisitionModifyApprovedFails() {
        // 领料审核会扣减库存，先预备足量库存（batchNo 与领料明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/material-requisitions", buildMaterialRequisitionBody(batch));
        Long id = ((Number) createResp.path("data.requisitionId")).longValue();
        authPost("/api/inventory/material-requisitions/{id}/submit", null, id);
        authPost("/api/inventory/material-requisitions/{id}/approve", null, id);
        Map<String, Object> body = buildMaterialRequisitionBody(batch);
        body.put("requisitionId", id);
        body.put("remark", "尝试修改已生效单据");
        Response r = authPut("/api/inventory/material-requisitions/{id}", body, id);
        assertFailed(r, "MaterialRequisition modify approved");
    }

    @Test
    @DisplayName("生产领料单 - 明细行号从1开始递增")
    void testMaterialRequisitionLineNoStartsAt1() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authGet("/api/inventory/material-requisitions/{id}", id);
        assertSuccess(r, "MaterialRequisition lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"));
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"));
    }

    // ==================================================================
    // 4. 调拨单 StockTransfer 状态流转
    // ==================================================================

    @Test
    @DisplayName("调拨单 - 草稿 → 待审核 (submit)")
    void testStockTransferSubmitForApproval() {
        Long id = createStockTransferAndGetId();
        Response r = authPost("/api/inventory/stock-transfers/{id}/submit", null, id);
        assertSuccess(r, "StockTransfer submit");
        assertStatus(authGet("/api/inventory/stock-transfers/{id}", id), "待审核");
    }

    @Test
    @DisplayName("调拨单 - 草稿不可直接审核")
    void testStockTransferApproveFromDraftFails() {
        Long id = createStockTransferAndGetId();
        Response r = authPost("/api/inventory/stock-transfers/{id}/approve", null, id);
        assertFailed(r, "StockTransfer approve from draft");
    }

    @Test
    @DisplayName("调拨单 - 草稿 → 待审核 → 已生效")
    void testStockTransferApproveFromPending() {
        // 调拨审核会从调出仓库扣减库存，先预备足量库存（batchNo 与调拨明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/stock-transfers", buildStockTransferBody(batch));
        Long id = ((Number) createResp.path("data.transferId")).longValue();
        authPost("/api/inventory/stock-transfers/{id}/submit", null, id);
        Response r = authPost("/api/inventory/stock-transfers/{id}/approve", null, id);
        assertSuccess(r, "StockTransfer approve");
        assertStatus(authGet("/api/inventory/stock-transfers/{id}", id), "已生效");
    }

    @Test
    @DisplayName("调拨单 - 重置草稿: 已生效 → 待审核")
    void testStockTransferReverseApprove() {
        // 调拨审核会从调出仓库扣减库存，先预备足量库存（batchNo 与调拨明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/stock-transfers", buildStockTransferBody(batch));
        Long id = ((Number) createResp.path("data.transferId")).longValue();
        authPost("/api/inventory/stock-transfers/{id}/submit", null, id);
        authPost("/api/inventory/stock-transfers/{id}/approve", null, id);
        Response r = authPost("/api/inventory/stock-transfers/{id}/reverse-approve", null, id);
        assertSuccess(r, "StockTransfer reverse-approve");
        Response get = authGet("/api/inventory/stock-transfers/{id}", id);
        assertStatus(get, "待审核");
        get.then().body("data.approvedBy", nullValue());
        get.then().body("data.approvedAt", nullValue());
    }

    @Test
    @DisplayName("调拨单 - 草稿不可重置草稿")
    void testStockTransferReverseApproveFromDraftFails() {
        Long id = createStockTransferAndGetId();
        Response r = authPost("/api/inventory/stock-transfers/{id}/reverse-approve", null, id);
        assertFailed(r, "StockTransfer reverse-approve from draft");
    }

    @Test
    @DisplayName("调拨单 - 已生效 → 已关闭")
    void testStockTransferClose() {
        // 调拨审核会从调出仓库扣减库存，先预备足量库存（batchNo 与调拨明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/stock-transfers", buildStockTransferBody(batch));
        Long id = ((Number) createResp.path("data.transferId")).longValue();
        authPost("/api/inventory/stock-transfers/{id}/submit", null, id);
        authPost("/api/inventory/stock-transfers/{id}/approve", null, id);
        Response r = authPost("/api/inventory/stock-transfers/{id}/close?reason=测试关闭", null, id);
        assertSuccess(r, "StockTransfer close");
        assertStatus(authGet("/api/inventory/stock-transfers/{id}", id), "已关闭");
    }

    @Test
    @DisplayName("调拨单 - 草稿 → 已取消")
    void testStockTransferCancel() {
        Long id = createStockTransferAndGetId();
        Response r = authPost("/api/inventory/stock-transfers/{id}/cancel?reason=测试取消", null, id);
        assertSuccess(r, "StockTransfer cancel");
        assertStatus(authGet("/api/inventory/stock-transfers/{id}", id), "已取消");
    }

    @Test
    @DisplayName("调拨单 - 已生效不可修改")
    void testStockTransferModifyApprovedFails() {
        // 调拨审核会从调出仓库扣减库存，先预备足量库存（batchNo 与调拨明细对齐）
        String batch = uniqueBatchNo();
        prepareStock(batch, new BigDecimal("20"));
        Response createResp = authPost("/api/inventory/stock-transfers", buildStockTransferBody(batch));
        Long id = ((Number) createResp.path("data.transferId")).longValue();
        authPost("/api/inventory/stock-transfers/{id}/submit", null, id);
        authPost("/api/inventory/stock-transfers/{id}/approve", null, id);
        Map<String, Object> body = buildStockTransferBody(batch);
        body.put("transferId", id);
        body.put("remark", "尝试修改已生效单据");
        Response r = authPut("/api/inventory/stock-transfers/{id}", body, id);
        assertFailed(r, "StockTransfer modify approved");
    }

    @Test
    @DisplayName("调拨单 - 明细行号从1开始递增")
    void testStockTransferLineNoStartsAt1() {
        Long id = createStockTransferAndGetId();
        Response r = authGet("/api/inventory/stock-transfers/{id}", id);
        assertSuccess(r, "StockTransfer lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"));
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"));
    }

    // ==================================================================
    // 5. 盘点单 StockCheck 状态流转（含 start-check / finish-check 特有流程）
    // ==================================================================

    @Test
    @DisplayName("盘点单 - 草稿 → 盘点中 (start-check)")
    void testStockCheckStartCheck() {
        Long id = createStockCheckAndGetId();
        Response r = authPost("/api/inventory/stock-checks/{id}/start-check", null, id);
        assertSuccess(r, "StockCheck start-check");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "盘点中");
    }

    @Test
    @DisplayName("盘点单 - 盘点中 → 已盘点 (finish-check)")
    void testStockCheckFinishCheck() {
        Long id = createStockCheckAndGetId();
        authPost("/api/inventory/stock-checks/{id}/start-check", null, id);
        Response r = authPost("/api/inventory/stock-checks/{id}/finish-check", null, id);
        assertSuccess(r, "StockCheck finish-check");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "已盘点");
    }

    @Test
    @DisplayName("盘点单 - 已盘点 → 待审核 (submit from finished)")
    void testStockCheckSubmitFromFinished() {
        Long id = createStockCheckAndGetId();
        authPost("/api/inventory/stock-checks/{id}/start-check", null, id);
        authPost("/api/inventory/stock-checks/{id}/finish-check", null, id);
        Response r = authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        assertSuccess(r, "StockCheck submit from finished");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "待审核");
    }

    @Test
    @DisplayName("盘点单 - 完整流程: 草稿 → 盘点中 → 已盘点 → 待审核 → 已生效")
    void testStockCheckFullCheckFlow() {
        Long id = createStockCheckAndGetId();
        authGet("/api/inventory/stock-checks/{id}", id).then().body("data.status", equalTo("草稿"));

        authPost("/api/inventory/stock-checks/{id}/start-check", null, id);
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "盘点中");

        authPost("/api/inventory/stock-checks/{id}/finish-check", null, id);
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "已盘点");

        authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "待审核");

        authPost("/api/inventory/stock-checks/{id}/approve", null, id);
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "已生效");
    }

    @Test
    @DisplayName("盘点单 - 草稿 → 待审核 (submit 直提)")
    void testStockCheckSubmitForApproval() {
        Long id = createStockCheckAndGetId();
        Response r = authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        assertSuccess(r, "StockCheck submit");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "待审核");
    }

    @Test
    @DisplayName("盘点单 - 草稿不可直接审核")
    void testStockCheckApproveFromDraftFails() {
        Long id = createStockCheckAndGetId();
        Response r = authPost("/api/inventory/stock-checks/{id}/approve", null, id);
        assertFailed(r, "StockCheck approve from draft");
    }

    @Test
    @DisplayName("盘点单 - 草稿 → 待审核 → 已生效")
    void testStockCheckApproveFromPending() {
        Long id = createStockCheckAndGetId();
        authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        Response r = authPost("/api/inventory/stock-checks/{id}/approve", null, id);
        assertSuccess(r, "StockCheck approve");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "已生效");
    }

    @Test
    @DisplayName("盘点单 - 重置草稿: 已生效 → 待审核")
    void testStockCheckReverseApprove() {
        Long id = createStockCheckAndGetId();
        authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        authPost("/api/inventory/stock-checks/{id}/approve", null, id);
        Response r = authPost("/api/inventory/stock-checks/{id}/reverse-approve", null, id);
        assertSuccess(r, "StockCheck reverse-approve");
        Response get = authGet("/api/inventory/stock-checks/{id}", id);
        assertStatus(get, "待审核");
        get.then().body("data.approvedBy", nullValue());
        get.then().body("data.approvedAt", nullValue());
    }

    @Test
    @DisplayName("盘点单 - 草稿不可重置草稿")
    void testStockCheckReverseApproveFromDraftFails() {
        Long id = createStockCheckAndGetId();
        Response r = authPost("/api/inventory/stock-checks/{id}/reverse-approve", null, id);
        assertFailed(r, "StockCheck reverse-approve from draft");
    }

    @Test
    @DisplayName("盘点单 - 已生效 → 已关闭")
    void testStockCheckClose() {
        Long id = createStockCheckAndGetId();
        authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        authPost("/api/inventory/stock-checks/{id}/approve", null, id);
        Response r = authPost("/api/inventory/stock-checks/{id}/close?reason=测试关闭", null, id);
        assertSuccess(r, "StockCheck close");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "已关闭");
    }

    @Test
    @DisplayName("盘点单 - 草稿 → 已取消")
    void testStockCheckCancel() {
        Long id = createStockCheckAndGetId();
        Response r = authPost("/api/inventory/stock-checks/{id}/cancel?reason=测试取消", null, id);
        assertSuccess(r, "StockCheck cancel");
        assertStatus(authGet("/api/inventory/stock-checks/{id}", id), "已取消");
    }

    @Test
    @DisplayName("盘点单 - 已生效不可修改")
    void testStockCheckModifyApprovedFails() {
        Long id = createStockCheckAndGetId();
        authPost("/api/inventory/stock-checks/{id}/submit", null, id);
        authPost("/api/inventory/stock-checks/{id}/approve", null, id);
        Map<String, Object> body = buildStockCheckBody();
        body.put("checkId", id);
        body.put("remark", "尝试修改已生效单据");
        Response r = authPut("/api/inventory/stock-checks/{id}", body, id);
        assertFailed(r, "StockCheck modify approved");
    }

    @Test
    @DisplayName("盘点单 - 明细行号从1开始递增")
    void testStockCheckLineNoStartsAt1() {
        Long id = createStockCheckAndGetId();
        Response r = authGet("/api/inventory/stock-checks/{id}", id);
        assertSuccess(r, "StockCheck lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"));
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"));
    }
}
