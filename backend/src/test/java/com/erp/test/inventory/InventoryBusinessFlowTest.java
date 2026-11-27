package com.erp.test.inventory;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

/**
 * 库存管理模块业务规则与库存影响自动化测试
 *
 * 测试内容：
 *  1. 字段校验：明细数量必须 > 0（0 与负数均应失败）
 *  2. 库存增减规则：
 *     - 其他入库审核 → addStock (+qty)
 *     - 其他出库审核 → deductStock (-qty)
 *     - 重置草稿 → 反向操作（入库重置草稿 = 扣减）
 *     - 调拨审核 → 调出扣减 + 调入增加
 *     - 盘点审核 → 盘盈增加 / 盘亏扣减
 *  3. 业务约束：调拨单调出仓库与调入仓库不能相同
 *
 * 库存定位: (stockId, binId, materialId, batchNo) 唯一索引
 *   所以出库/调拨/盘点明细的 batchNo 必须与入库时的 batchNo 对齐才能命中库存记录。
 */
@DisplayName("库存管理模块 - 业务规则与库存影响测试")
public class InventoryBusinessFlowTest extends InventoryTestBase {

    // ==================================================================
    // 字段校验：数量必须 > 0
    // ==================================================================

    @Test
    @DisplayName("校验 - 其他入库单明细数量为0应创建失败")
    void testQtyMustBePositive() {
        Map<String, Object> body = buildOtherReceiptBody();
        // 覆盖明细为数量 0
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherReceiptItem(new BigDecimal("0")));
        body.put("items", items);
        Response r = authPost("/api/inventory/other-receipts", body);
        assertFailed(r, "OtherReceipt qty=0 should fail");
    }

    @Test
    @DisplayName("校验 - 其他入库单明细数量为负应创建失败")
    void testQtyMustBePositiveNegative() {
        Map<String, Object> body = buildOtherReceiptBody();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherReceiptItem(new BigDecimal("-5")));
        body.put("items", items);
        Response r = authPost("/api/inventory/other-receipts", body);
        assertFailed(r, "OtherReceipt qty=-5 should fail");
    }

    @Test
    @DisplayName("校验 - 其他出库单明细数量为0应创建失败")
    void testOtherIssueQtyZeroFails() {
        Map<String, Object> body = buildOtherIssueBody();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherIssueItem(new BigDecimal("0")));
        body.put("items", items);
        Response r = authPost("/api/inventory/other-issues", body);
        assertFailed(r, "OtherIssue qty=0 should fail");
    }

    @Test
    @DisplayName("校验 - 生产领料单明细数量为负应创建失败")
    void testMaterialRequisitionQtyNegativeFails() {
        Map<String, Object> body = buildMaterialRequisitionBody();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildMaterialRequisitionItem(new BigDecimal("-1")));
        body.put("items", items);
        Response r = authPost("/api/inventory/material-requisitions", body);
        assertFailed(r, "MaterialRequisition qty=-1 should fail");
    }

    // ==================================================================
    // 库存影响：其他入库审核 → 库存增加
    // ==================================================================

    @Test
    @DisplayName("库存影响 - 其他入库审核后库存增加（+10）")
    void testStockIncreaseOnReceiptApprove() {
        // 1. 记录审核前库存
        BigDecimal before = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  入库审核前库存: " + before);

        // 2. 创建其他入库单 qty=10，提交+审核
        Map<String, Object> body = buildOtherReceiptBodyWithQty(new BigDecimal("10"));
        Response createResp = authPost("/api/inventory/other-receipts", body);
        assertSuccess(createResp, "OtherReceipt create for stock increase");
        Long id = ((Number) createResp.path("data.receiptId")).longValue();

        authPost("/api/inventory/other-receipts/{id}/submit", null, id)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/other-receipts/{id}/approve", null, id)
                .then().body("code", equalTo(200));

        // 3. 记录审核后库存，校验增量 = +10
        BigDecimal after = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  入库审核后库存: " + after);
        BigDecimal delta = after.subtract(before);
        org.junit.jupiter.api.Assertions.assertEquals(0, delta.compareTo(new BigDecimal("10")),
                "入库审核后库存应增加10，实际增量: " + delta);
    }

    // ==================================================================
    // 库存影响：其他出库审核 → 库存减少
    // ==================================================================

    @Test
    @DisplayName("库存影响 - 其他出库审核后库存减少（-5）")
    void testStockDecreaseOnIssueApprove() {
        // 1. 先通过其他入库补充库存 qty=20（保证后续出库有库存可扣）
        String batch = uniqueBatchNo();
        Map<String, Object> receiptBody = buildReceiptBodyWithBatch(new BigDecimal("20"), batch);
        Response receiptResp = authPost("/api/inventory/other-receipts", receiptBody);
        assertSuccess(receiptResp, "OtherReceipt create for stock prepare");
        Long receiptId = ((Number) receiptResp.path("data.receiptId")).longValue();
        authPost("/api/inventory/other-receipts/{id}/submit", null, receiptId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/other-receipts/{id}/approve", null, receiptId)
                .then().body("code", equalTo(200));

        // 2. 记录出库前库存
        BigDecimal before = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  出库审核前库存: " + before);

        // 3. 创建其他出库单 qty=5（使用相同 batchNo 命中库存记录），提交+审核
        Map<String, Object> issueBody = buildIssueBodyWithBatch(new BigDecimal("5"), batch);
        Response issueResp = authPost("/api/inventory/other-issues", issueBody);
        assertSuccess(issueResp, "OtherIssue create for stock decrease");
        Long issueId = ((Number) issueResp.path("data.issueId")).longValue();
        authPost("/api/inventory/other-issues/{id}/submit", null, issueId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/other-issues/{id}/approve", null, issueId)
                .then().body("code", equalTo(200));

        // 4. 校验库存增量 = -5
        BigDecimal after = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  出库审核后库存: " + after);
        BigDecimal delta = after.subtract(before);
        org.junit.jupiter.api.Assertions.assertEquals(0, delta.compareTo(new BigDecimal("-5")),
                "出库审核后库存应减少5，实际增量: " + delta);
    }

    // ==================================================================
    // 库存影响：重置草稿 → 库存回滚
    // ==================================================================

    @Test
    @DisplayName("库存影响 - 其他入库重置草稿后库存回滚（-20）")
    void testStockReversalOnReverseApprove() {
        // 1. 通过其他入库增加库存 qty=20
        String batch = uniqueBatchNo();
        Map<String, Object> receiptBody = buildReceiptBodyWithBatch(new BigDecimal("20"), batch);
        Response receiptResp = authPost("/api/inventory/other-receipts", receiptBody);
        assertSuccess(receiptResp, "OtherReceipt create for reversal test");
        Long receiptId = ((Number) receiptResp.path("data.receiptId")).longValue();
        authPost("/api/inventory/other-receipts/{id}/submit", null, receiptId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/other-receipts/{id}/approve", null, receiptId)
                .then().body("code", equalTo(200));

        // 2. 记录重置草稿前库存
        BigDecimal before = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  重置草稿前库存: " + before);

        // 3. 重置草稿（入库重置草稿 = 扣减库存）
        Response r = authPost("/api/inventory/other-receipts/{id}/reverse-approve", null, receiptId);
        assertSuccess(r, "OtherReceipt reverse-approve");

        // 4. 校验库存增量 = -20
        BigDecimal after = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  重置草稿后库存: " + after);
        BigDecimal delta = after.subtract(before);
        org.junit.jupiter.api.Assertions.assertEquals(0, delta.compareTo(new BigDecimal("-20")),
                "入库重置草稿后库存应减少20，实际增量: " + delta);
    }

    // ==================================================================
    // 库存影响：调拨审核 → 调出减少 + 调入增加
    // ==================================================================

    @Test
    @DisplayName("库存影响 - 调拨审核后调出仓库-10、调入仓库+10")
    void testTransferStockMovement() {
        // 1. 先给仓库1补充库存 qty=50
        String batch = uniqueBatchNo();
        Map<String, Object> receiptBody = buildReceiptBodyWithBatch(new BigDecimal("50"), batch);
        Response receiptResp = authPost("/api/inventory/other-receipts", receiptBody);
        assertSuccess(receiptResp, "OtherReceipt create for transfer prepare");
        Long receiptId = ((Number) receiptResp.path("data.receiptId")).longValue();
        authPost("/api/inventory/other-receipts/{id}/submit", null, receiptId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/other-receipts/{id}/approve", null, receiptId)
                .then().body("code", equalTo(200));

        // 2. 记录调拨前两个仓库的库存
        BigDecimal beforeFrom = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        BigDecimal beforeTo = getInventoryQuantity(WAREHOUSE_ID_2, MATERIAL_ID);
        System.out.println("  调拨前 仓库1库存: " + beforeFrom + ", 仓库2库存: " + beforeTo);

        // 3. 创建调拨单 仓库1 → 仓库2 qty=10（使用相同 batchNo），提交+审核
        Map<String, Object> transferBody = buildTransferBodyWithBatch(
                new BigDecimal("10"), WAREHOUSE_ID_1, WAREHOUSE_ID_2, batch);
        Response transferResp = authPost("/api/inventory/stock-transfers", transferBody);
        assertSuccess(transferResp, "StockTransfer create for movement");
        Long transferId = ((Number) transferResp.path("data.transferId")).longValue();
        authPost("/api/inventory/stock-transfers/{id}/submit", null, transferId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/stock-transfers/{id}/approve", null, transferId)
                .then().body("code", equalTo(200));

        // 4. 校验：仓库1 -10，仓库2 +10
        BigDecimal afterFrom = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        BigDecimal afterTo = getInventoryQuantity(WAREHOUSE_ID_2, MATERIAL_ID);
        System.out.println("  调拨后 仓库1库存: " + afterFrom + ", 仓库2库存: " + afterTo);

        BigDecimal deltaFrom = afterFrom.subtract(beforeFrom);
        BigDecimal deltaTo = afterTo.subtract(beforeTo);
        org.junit.jupiter.api.Assertions.assertEquals(0, deltaFrom.compareTo(new BigDecimal("-10")),
                "调拨后调出仓库库存应减少10，实际增量: " + deltaFrom);
        org.junit.jupiter.api.Assertions.assertEquals(0, deltaTo.compareTo(new BigDecimal("10")),
                "调拨后调入仓库库存应增加10，实际增量: " + deltaTo);
    }

    // ==================================================================
    // 库存影响：盘点审核 → 盘盈入库
    // ==================================================================

    @Test
    @DisplayName("库存影响 - 盘点审核盘盈入库（+10）")
    void testStockCheckProfitLoss() {
        // 1. 先通过其他入库补充库存 qty=100（作为账面数量基准）
        String batch = uniqueBatchNo();
        Map<String, Object> receiptBody = buildReceiptBodyWithBatch(new BigDecimal("100"), batch);
        Response receiptResp = authPost("/api/inventory/other-receipts", receiptBody);
        assertSuccess(receiptResp, "OtherReceipt create for check baseline");
        Long receiptId = ((Number) receiptResp.path("data.receiptId")).longValue();
        authPost("/api/inventory/other-receipts/{id}/submit", null, receiptId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/other-receipts/{id}/approve", null, receiptId)
                .then().body("code", equalTo(200));

        // 2. 记录盘点审核前库存
        BigDecimal before = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  盘点审核前库存: " + before);

        // 3. 创建盘点单：账面100，实盘110（盘盈10），提交+审核
        Map<String, Object> checkBody = buildCheckBodyWithBatch(
                new BigDecimal("100"), new BigDecimal("110"), batch);
        Response checkResp = authPost("/api/inventory/stock-checks", checkBody);
        assertSuccess(checkResp, "StockCheck create for profit");
        Long checkId = ((Number) checkResp.path("data.checkId")).longValue();
        authPost("/api/inventory/stock-checks/{id}/submit", null, checkId)
                .then().body("code", equalTo(200));
        authPost("/api/inventory/stock-checks/{id}/approve", null, checkId)
                .then().body("code", equalTo(200));

        // 4. 校验库存增量 = +10（盘盈入库）
        BigDecimal after = getInventoryQuantity(WAREHOUSE_ID_1, MATERIAL_ID);
        System.out.println("  盘点审核后库存: " + after);
        BigDecimal delta = after.subtract(before);
        org.junit.jupiter.api.Assertions.assertEquals(0, delta.compareTo(new BigDecimal("10")),
                "盘盈审核后库存应增加10，实际增量: " + delta);
    }

    // ==================================================================
    // 业务约束：调拨单调出与调入仓库不能相同
    // ==================================================================

    @Test
    @DisplayName("业务约束 - 调拨单调出仓库与调入仓库相同应创建失败")
    void testSameWarehouseTransferFails() {
        Map<String, Object> body = new HashMap<>();
        body.put("transferDate", java.time.LocalDate.now().toString());
        body.put("fromStockId", WAREHOUSE_ID_1);
        body.put("toStockId", WAREHOUSE_ID_1); // 相同仓库
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("transferType", "仓库间调拨");
        body.put("remark", "测试相同仓库调拨应失败");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildStockTransferItem(new BigDecimal("10"), WAREHOUSE_ID_1, WAREHOUSE_ID_1));
        body.put("items", items);

        Response r = authPost("/api/inventory/stock-transfers", body);
        assertFailed(r, "StockTransfer same warehouse should fail");
    }

    // ==================================================================
    // 内部辅助：构造带指定 batchNo 的单据请求体
    // ==================================================================

    /** 其他入库单（单条明细，指定数量与批次号） */
    private Map<String, Object> buildReceiptBodyWithBatch(BigDecimal qty, String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiptDate", java.time.LocalDate.now().toString());
        body.put("receiptType", "盘盈入库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "库存影响测试-其他入库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherReceiptItem(qty, batchNo));
        body.put("items", items);
        return body;
    }

    /** 其他出库单（单条明细，指定数量与批次号） */
    private Map<String, Object> buildIssueBodyWithBatch(BigDecimal qty, String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("issueDate", java.time.LocalDate.now().toString());
        body.put("issueType", "盘亏出库");
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("handlerId", HANDLER_ID);
        body.put("departmentId", DEPARTMENT_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "库存影响测试-其他出库");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildOtherIssueItem(qty, batchNo));
        body.put("items", items);
        return body;
    }

    /** 调拨单（单条明细，指定数量、调出/调入仓库与批次号） */
    private Map<String, Object> buildTransferBodyWithBatch(BigDecimal qty, Long fromStockId, Long toStockId, String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("transferDate", java.time.LocalDate.now().toString());
        body.put("fromStockId", fromStockId);
        body.put("toStockId", toStockId);
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("transferType", "仓库间调拨");
        body.put("remark", "库存影响测试-调拨");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildStockTransferItem(qty, fromStockId, toStockId, batchNo));
        body.put("items", items);
        return body;
    }

    /** 盘点单（单条明细，指定账面/实盘数量与批次号） */
    private Map<String, Object> buildCheckBodyWithBatch(BigDecimal bookQty, BigDecimal checkQty, String batchNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("checkDate", java.time.LocalDate.now().toString());
        body.put("warehouseId", WAREHOUSE_ID_1);
        body.put("stockType", "全盘");
        body.put("handlerId", HANDLER_ID);
        body.put("currencyId", CURRENCY_ID);
        body.put("exchangeRate", BigDecimal.ONE);
        body.put("remark", "库存影响测试-盘点");
        body.put("status", "草稿");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildStockCheckItem(bookQty, checkQty, batchNo));
        body.put("items", items);
        return body;
    }
}
