package com.erp.test.inventory;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;

/**
 * 库存管理模块 CRUD 自动化测试
 *
 * 覆盖 5 类库存业务单据的增删改查：
 *  1. 其他入库单 OtherReceipt     /api/inventory/other-receipts    前缀 OIN-yyyyMMdd-0001
 *  2. 其他出库单 OtherIssue       /api/inventory/other-issues      前缀 OOUT-yyyyMMdd-0001
 *  3. 生产领料单 MaterialRequisition /api/inventory/material-requisitions 前缀 MOPICK-yyyyMMdd-0001
 *  4. 调拨单 StockTransfer        /api/inventory/stock-transfers   前缀 TR-yyyyMMdd-0001
 *  5. 盘点单 StockCheck           /api/inventory/stock-checks      前缀 INV-yyyyMMdd-0001
 *
 * 每类单据覆盖：testCreate / testGetDetail / testUpdate / testDelete / testPageQuery / testList
 * 创建后校验单据编号格式（前缀-yyyyMMdd-NNNN）
 */
@DisplayName("库存管理模块 - CRUD测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InventoryCrudTest extends InventoryTestBase {

    private static final Pattern OIN_PATTERN = Pattern.compile("OIN-\\d{8}-\\d{4}");
    private static final Pattern OOUT_PATTERN = Pattern.compile("OOUT-\\d{8}-\\d{4}");
    private static final Pattern MOPICK_PATTERN = Pattern.compile("MOPICK-\\d{8}-\\d{4}");
    private static final Pattern TR_PATTERN = Pattern.compile("TR-\\d{8}-\\d{4}");
    private static final Pattern INV_PATTERN = Pattern.compile("INV-\\d{8}-\\d{4}");

    // ==================================================================
    // 1. 其他入库单 OtherReceipt
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("其他入库单 - 创建（校验编号格式 OIN-yyyyMMdd-NNNN）")
    void testOtherReceiptCreate() {
        Response r = authPost("/api/inventory/other-receipts", buildOtherReceiptBody());
        assertSuccess(r, "OtherReceipt CREATE");
        r.then().body("data.receiptId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        String receiptNo = r.path("data.receiptNo");
        System.out.println("  生成的入库单号: " + receiptNo);
        org.junit.jupiter.api.Assertions.assertNotNull(receiptNo, "入库单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(OIN_PATTERN.matcher(receiptNo).matches(),
                "入库单号格式应为 OIN-yyyyMMdd-NNNN，实际: " + receiptNo);
    }

    @Test
    @Order(2)
    @DisplayName("其他入库单 - 按ID查询详情（含明细）")
    void testOtherReceiptGetDetail() {
        Long id = createOtherReceiptAndGetId();
        Response r = authGet("/api/inventory/other-receipts/{id}", id);
        assertSuccess(r, "OtherReceipt GET BY ID");
        r.then().body("data.receiptId", equalTo(id.intValue()));
        r.then().body("data.items", notNullValue());
        r.then().body("data.items.size()", equalTo(2));
    }

    @Test
    @Order(3)
    @DisplayName("其他入库单 - 更新（修改备注）")
    void testOtherReceiptUpdate() {
        Long id = createOtherReceiptAndGetId();
        Map<String, Object> body = buildOtherReceiptBody();
        body.put("receiptId", id);
        body.put("remark", "更新后备注-其他入库");
        Response r = authPut("/api/inventory/other-receipts/{id}", body, id);
        assertSuccess(r, "OtherReceipt UPDATE");
        Response getResp = authGet("/api/inventory/other-receipts/{id}", id);
        getResp.then().body("data.remark", equalTo("更新后备注-其他入库"));
    }

    @Test
    @Order(4)
    @DisplayName("其他入库单 - 删除")
    void testOtherReceiptDelete() {
        Long id = createOtherReceiptAndGetId();
        Response r = authDelete("/api/inventory/other-receipts/{id}", id);
        assertSuccess(r, "OtherReceipt DELETE");
        // 验证删除后查询应失败
        Response getResp = authGet("/api/inventory/other-receipts/{id}", id);
        int code = getResp.path("code");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code, "删除后查询应返回非200");
    }

    @Test
    @Order(5)
    @DisplayName("其他入库单 - 分页查询")
    void testOtherReceiptPageQuery() {
        createOtherReceiptAndGetId();
        Response r = authGetWithParams("/api/inventory/other-receipts/page",
                Map.of("current", 1, "size", 20));
        assertSuccess(r, "OtherReceipt PAGE");
        r.then().body("data.records", notNullValue());
        r.then().body("data.total", notNullValue());
        System.out.println("  入库单分页 total=" + r.path("data.total"));
    }

    @Test
    @Order(6)
    @DisplayName("其他入库单 - 全量列表查询")
    void testOtherReceiptList() {
        createOtherReceiptAndGetId();
        Response r = authGet("/api/inventory/other-receipts/list");
        assertSuccess(r, "OtherReceipt LIST");
        r.then().body("data", notNullValue());
        List<?> list = r.path("data");
        System.out.println("  入库单列表 size=" + list.size());
    }

    // ==================================================================
    // 2. 其他出库单 OtherIssue
    // ==================================================================

    @Test
    @Order(10)
    @DisplayName("其他出库单 - 创建（校验编号格式 OOUT-yyyyMMdd-NNNN）")
    void testOtherIssueCreate() {
        Response r = authPost("/api/inventory/other-issues", buildOtherIssueBody());
        assertSuccess(r, "OtherIssue CREATE");
        r.then().body("data.issueId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        String issueNo = r.path("data.issueNo");
        System.out.println("  生成的出库单号: " + issueNo);
        org.junit.jupiter.api.Assertions.assertNotNull(issueNo, "出库单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(OOUT_PATTERN.matcher(issueNo).matches(),
                "出库单号格式应为 OOUT-yyyyMMdd-NNNN，实际: " + issueNo);
    }

    @Test
    @Order(11)
    @DisplayName("其他出库单 - 按ID查询详情（含明细）")
    void testOtherIssueGetDetail() {
        Long id = createOtherIssueAndGetId();
        Response r = authGet("/api/inventory/other-issues/{id}", id);
        assertSuccess(r, "OtherIssue GET BY ID");
        r.then().body("data.issueId", equalTo(id.intValue()));
        r.then().body("data.items", notNullValue());
        r.then().body("data.items.size()", equalTo(2));
    }

    @Test
    @Order(12)
    @DisplayName("其他出库单 - 更新（修改备注）")
    void testOtherIssueUpdate() {
        Long id = createOtherIssueAndGetId();
        Map<String, Object> body = buildOtherIssueBody();
        body.put("issueId", id);
        body.put("remark", "更新后备注-其他出库");
        Response r = authPut("/api/inventory/other-issues/{id}", body, id);
        assertSuccess(r, "OtherIssue UPDATE");
        Response getResp = authGet("/api/inventory/other-issues/{id}", id);
        getResp.then().body("data.remark", equalTo("更新后备注-其他出库"));
    }

    @Test
    @Order(13)
    @DisplayName("其他出库单 - 删除")
    void testOtherIssueDelete() {
        Long id = createOtherIssueAndGetId();
        Response r = authDelete("/api/inventory/other-issues/{id}", id);
        assertSuccess(r, "OtherIssue DELETE");
        Response getResp = authGet("/api/inventory/other-issues/{id}", id);
        int code = getResp.path("code");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code, "删除后查询应返回非200");
    }

    @Test
    @Order(14)
    @DisplayName("其他出库单 - 分页查询")
    void testOtherIssuePageQuery() {
        createOtherIssueAndGetId();
        Response r = authGetWithParams("/api/inventory/other-issues/page",
                Map.of("current", 1, "size", 20));
        assertSuccess(r, "OtherIssue PAGE");
        r.then().body("data.records", notNullValue());
        r.then().body("data.total", notNullValue());
        System.out.println("  出库单分页 total=" + r.path("data.total"));
    }

    @Test
    @Order(15)
    @DisplayName("其他出库单 - 全量列表查询")
    void testOtherIssueList() {
        createOtherIssueAndGetId();
        Response r = authGet("/api/inventory/other-issues/list");
        assertSuccess(r, "OtherIssue LIST");
        r.then().body("data", notNullValue());
        List<?> list = r.path("data");
        System.out.println("  出库单列表 size=" + list.size());
    }

    // ==================================================================
    // 3. 生产领料单 MaterialRequisition
    // ==================================================================

    @Test
    @Order(20)
    @DisplayName("生产领料单 - 创建（校验编号格式 MOPICK-yyyyMMdd-NNNN）")
    void testMaterialRequisitionCreate() {
        Response r = authPost("/api/inventory/material-requisitions", buildMaterialRequisitionBody());
        assertSuccess(r, "MaterialRequisition CREATE");
        r.then().body("data.requisitionId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        String no = r.path("data.requisitionNo");
        System.out.println("  生成的领料单号: " + no);
        org.junit.jupiter.api.Assertions.assertNotNull(no, "领料单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(MOPICK_PATTERN.matcher(no).matches(),
                "领料单号格式应为 MOPICK-yyyyMMdd-NNNN，实际: " + no);
    }

    @Test
    @Order(21)
    @DisplayName("生产领料单 - 按ID查询详情（含明细）")
    void testMaterialRequisitionGetDetail() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authGet("/api/inventory/material-requisitions/{id}", id);
        assertSuccess(r, "MaterialRequisition GET BY ID");
        r.then().body("data.requisitionId", equalTo(id.intValue()));
        r.then().body("data.items", notNullValue());
        r.then().body("data.items.size()", equalTo(2));
    }

    @Test
    @Order(22)
    @DisplayName("生产领料单 - 更新（修改备注）")
    void testMaterialRequisitionUpdate() {
        Long id = createMaterialRequisitionAndGetId();
        Map<String, Object> body = buildMaterialRequisitionBody();
        body.put("requisitionId", id);
        body.put("remark", "更新后备注-生产领料");
        Response r = authPut("/api/inventory/material-requisitions/{id}", body, id);
        assertSuccess(r, "MaterialRequisition UPDATE");
        Response getResp = authGet("/api/inventory/material-requisitions/{id}", id);
        getResp.then().body("data.remark", equalTo("更新后备注-生产领料"));
    }

    @Test
    @Order(23)
    @DisplayName("生产领料单 - 删除")
    void testMaterialRequisitionDelete() {
        Long id = createMaterialRequisitionAndGetId();
        Response r = authDelete("/api/inventory/material-requisitions/{id}", id);
        assertSuccess(r, "MaterialRequisition DELETE");
        Response getResp = authGet("/api/inventory/material-requisitions/{id}", id);
        int code = getResp.path("code");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code, "删除后查询应返回非200");
    }

    @Test
    @Order(24)
    @DisplayName("生产领料单 - 分页查询")
    void testMaterialRequisitionPageQuery() {
        createMaterialRequisitionAndGetId();
        Response r = authGetWithParams("/api/inventory/material-requisitions/page",
                Map.of("current", 1, "size", 20));
        assertSuccess(r, "MaterialRequisition PAGE");
        r.then().body("data.records", notNullValue());
        r.then().body("data.total", notNullValue());
        System.out.println("  领料单分页 total=" + r.path("data.total"));
    }

    @Test
    @Order(25)
    @DisplayName("生产领料单 - 全量列表查询")
    void testMaterialRequisitionList() {
        createMaterialRequisitionAndGetId();
        Response r = authGet("/api/inventory/material-requisitions/list");
        assertSuccess(r, "MaterialRequisition LIST");
        r.then().body("data", notNullValue());
        List<?> list = r.path("data");
        System.out.println("  领料单列表 size=" + list.size());
    }

    // ==================================================================
    // 4. 调拨单 StockTransfer
    // ==================================================================

    @Test
    @Order(30)
    @DisplayName("调拨单 - 创建（校验编号格式 TR-yyyyMMdd-NNNN）")
    void testStockTransferCreate() {
        Response r = authPost("/api/inventory/stock-transfers", buildStockTransferBody());
        assertSuccess(r, "StockTransfer CREATE");
        r.then().body("data.transferId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        String no = r.path("data.transferNo");
        System.out.println("  生成的调拨单号: " + no);
        org.junit.jupiter.api.Assertions.assertNotNull(no, "调拨单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(TR_PATTERN.matcher(no).matches(),
                "调拨单号格式应为 TR-yyyyMMdd-NNNN，实际: " + no);
    }

    @Test
    @Order(31)
    @DisplayName("调拨单 - 按ID查询详情（含明细）")
    void testStockTransferGetDetail() {
        Long id = createStockTransferAndGetId();
        Response r = authGet("/api/inventory/stock-transfers/{id}", id);
        assertSuccess(r, "StockTransfer GET BY ID");
        r.then().body("data.transferId", equalTo(id.intValue()));
        r.then().body("data.items", notNullValue());
        r.then().body("data.items.size()", equalTo(2));
    }

    @Test
    @Order(32)
    @DisplayName("调拨单 - 更新（修改备注）")
    void testStockTransferUpdate() {
        Long id = createStockTransferAndGetId();
        Map<String, Object> body = buildStockTransferBody();
        body.put("transferId", id);
        body.put("remark", "更新后备注-调拨");
        Response r = authPut("/api/inventory/stock-transfers/{id}", body, id);
        assertSuccess(r, "StockTransfer UPDATE");
        Response getResp = authGet("/api/inventory/stock-transfers/{id}", id);
        getResp.then().body("data.remark", equalTo("更新后备注-调拨"));
    }

    @Test
    @Order(33)
    @DisplayName("调拨单 - 删除")
    void testStockTransferDelete() {
        Long id = createStockTransferAndGetId();
        Response r = authDelete("/api/inventory/stock-transfers/{id}", id);
        assertSuccess(r, "StockTransfer DELETE");
        Response getResp = authGet("/api/inventory/stock-transfers/{id}", id);
        int code = getResp.path("code");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code, "删除后查询应返回非200");
    }

    @Test
    @Order(34)
    @DisplayName("调拨单 - 分页查询")
    void testStockTransferPageQuery() {
        createStockTransferAndGetId();
        Response r = authGetWithParams("/api/inventory/stock-transfers/page",
                Map.of("current", 1, "size", 20));
        assertSuccess(r, "StockTransfer PAGE");
        r.then().body("data.records", notNullValue());
        r.then().body("data.total", notNullValue());
        System.out.println("  调拨单分页 total=" + r.path("data.total"));
    }

    @Test
    @Order(35)
    @DisplayName("调拨单 - 全量列表查询")
    void testStockTransferList() {
        createStockTransferAndGetId();
        Response r = authGet("/api/inventory/stock-transfers/list");
        assertSuccess(r, "StockTransfer LIST");
        r.then().body("data", notNullValue());
        List<?> list = r.path("data");
        System.out.println("  调拨单列表 size=" + list.size());
    }

    // ==================================================================
    // 5. 盘点单 StockCheck
    // ==================================================================

    @Test
    @Order(40)
    @DisplayName("盘点单 - 创建（校验编号格式 INV-yyyyMMdd-NNNN）")
    void testStockCheckCreate() {
        Response r = authPost("/api/inventory/stock-checks", buildStockCheckBody());
        assertSuccess(r, "StockCheck CREATE");
        r.then().body("data.checkId", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        String no = r.path("data.checkNo");
        System.out.println("  生成的盘点单号: " + no);
        org.junit.jupiter.api.Assertions.assertNotNull(no, "盘点单号不应为空");
        org.junit.jupiter.api.Assertions.assertTrue(INV_PATTERN.matcher(no).matches(),
                "盘点单号格式应为 INV-yyyyMMdd-NNNN，实际: " + no);
    }

    @Test
    @Order(41)
    @DisplayName("盘点单 - 按ID查询详情（含明细）")
    void testStockCheckGetDetail() {
        Long id = createStockCheckAndGetId();
        Response r = authGet("/api/inventory/stock-checks/{id}", id);
        assertSuccess(r, "StockCheck GET BY ID");
        r.then().body("data.checkId", equalTo(id.intValue()));
        r.then().body("data.items", notNullValue());
        r.then().body("data.items.size()", equalTo(2));
    }

    @Test
    @Order(42)
    @DisplayName("盘点单 - 更新（修改备注）")
    void testStockCheckUpdate() {
        Long id = createStockCheckAndGetId();
        Map<String, Object> body = buildStockCheckBody();
        body.put("checkId", id);
        body.put("remark", "更新后备注-盘点");
        Response r = authPut("/api/inventory/stock-checks/{id}", body, id);
        assertSuccess(r, "StockCheck UPDATE");
        Response getResp = authGet("/api/inventory/stock-checks/{id}", id);
        getResp.then().body("data.remark", equalTo("更新后备注-盘点"));
    }

    @Test
    @Order(43)
    @DisplayName("盘点单 - 删除")
    void testStockCheckDelete() {
        Long id = createStockCheckAndGetId();
        Response r = authDelete("/api/inventory/stock-checks/{id}", id);
        assertSuccess(r, "StockCheck DELETE");
        Response getResp = authGet("/api/inventory/stock-checks/{id}", id);
        int code = getResp.path("code");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code, "删除后查询应返回非200");
    }

    @Test
    @Order(44)
    @DisplayName("盘点单 - 分页查询")
    void testStockCheckPageQuery() {
        createStockCheckAndGetId();
        Response r = authGetWithParams("/api/inventory/stock-checks/page",
                Map.of("current", 1, "size", 20));
        assertSuccess(r, "StockCheck PAGE");
        r.then().body("data.records", notNullValue());
        r.then().body("data.total", notNullValue());
        System.out.println("  盘点单分页 total=" + r.path("data.total"));
    }

    @Test
    @Order(45)
    @DisplayName("盘点单 - 全量列表查询")
    void testStockCheckList() {
        createStockCheckAndGetId();
        Response r = authGet("/api/inventory/stock-checks/list");
        assertSuccess(r, "StockCheck LIST");
        r.then().body("data", notNullValue());
        List<?> list = r.path("data");
        System.out.println("  盘点单列表 size=" + list.size());
    }

    // ==================================================================
    // 通用：行号从 1 开始递增
    // ==================================================================

    @Test
    @Order(50)
    @DisplayName("行号校验 - 其他入库单明细行号从1递增")
    void testOtherReceiptLineNoStartsAt1() {
        Long id = createOtherReceiptAndGetId();
        Response r = authGet("/api/inventory/other-receipts/{id}", id);
        assertSuccess(r, "OtherReceipt lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(2, items.size(), "应有2条明细");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"), "第1条明细lineNo应为1");
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"), "第2条明细lineNo应为2");
    }

    @Test
    @Order(51)
    @DisplayName("行号校验 - 盘点单明细行号从1递增")
    void testStockCheckLineNoStartsAt1() {
        Long id = createStockCheckAndGetId();
        Response r = authGet("/api/inventory/stock-checks/{id}", id);
        assertSuccess(r, "StockCheck lineNo check");
        List<Map<String, Object>> items = r.path("data.items");
        org.junit.jupiter.api.Assertions.assertEquals(2, items.size(), "应有2条明细");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.get(0).get("lineNo"), "第1条明细lineNo应为1");
        org.junit.jupiter.api.Assertions.assertEquals(2, items.get(1).get("lineNo"), "第2条明细lineNo应为2");
    }
}
