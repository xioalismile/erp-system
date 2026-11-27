package com.erp.test.inventory;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 库存管理模块安全性自动化测试
 *
 * 测试内容：
 *  1. 无 Token 访问库存接口应被拒绝（401 或 403）
 *  2. 无效 Token 访问应被拒绝
 *  3. 无 Token 创建单据应被拒绝
 *  4. 无 Token 执行审核操作应被拒绝
 *  5. 查询/操作不存在的资源应返回错误
 */
@DisplayName("库存管理模块 - 安全性测试")
public class InventorySecurityTest extends InventoryTestBase {

    // ==================================================================
    // 无 Token 访问
    // ==================================================================

    @Test
    @DisplayName("安全 - 无Token访问其他入库单列表应被拒绝")
    void testNoTokenFailsOtherReceiptList() {
        Response r = noAuthGet("/api/inventory/other-receipts/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无Token访问入库单列表 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token访问即时库存列表应被拒绝")
    void testNoTokenFailsInventoryList() {
        Response r = noAuthGet("/api/inventory/list?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无Token访问即时库存 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token访问盘点单列表应被拒绝")
    void testNoTokenFailsStockCheckList() {
        Response r = noAuthGet("/api/inventory/stock-checks/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无Token访问盘点单列表 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无效Token访问应被拒绝")
    void testInvalidTokenFails() {
        Response r = RestAssured.given()
                .header("Authorization", "Bearer invalid_token_12345")
                .header("X-Operator", "admin")
                .when()
                .get("/api/inventory/other-receipts/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无效Token访问 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无效Token访问应返回401或403，实际: " + status);
    }

    // ==================================================================
    // 无 Token 创建/操作
    // ==================================================================

    @Test
    @DisplayName("安全 - 无Token创建其他入库单应被拒绝")
    void testNoTokenCreateFails() {
        Response r = noAuthPost("/api/inventory/other-receipts", buildOtherReceiptBody());
        int status = r.statusCode();
        System.out.println("  无Token创建入库单 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token创建应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token创建调拨单应被拒绝")
    void testNoTokenCreateTransferFails() {
        Response r = noAuthPost("/api/inventory/stock-transfers", buildStockTransferBody());
        int status = r.statusCode();
        System.out.println("  无Token创建调拨单 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token创建应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token执行审核操作应被拒绝")
    void testNoTokenApproveFails() {
        // 先带 Token 创建一张入库单
        Long id = createOtherReceiptAndGetId();
        // 无 Token 尝试审核
        Response r = noAuthPost("/api/inventory/other-receipts/" + id + "/approve", null);
        int status = r.statusCode();
        System.out.println("  无Token审核 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token审核应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token执行提交操作应被拒绝")
    void testNoTokenSubmitFails() {
        Long id = createOtherIssueAndGetId();
        Response r = noAuthPost("/api/inventory/other-issues/" + id + "/submit", null);
        int status = r.statusCode();
        System.out.println("  无Token提交 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token提交应返回401或403，实际: " + status);
    }

    // ==================================================================
    // 不存在的资源
    // ==================================================================

    @Test
    @DisplayName("安全 - 查询不存在的其他入库单ID应返回错误")
    void testGetNonExistentOtherReceipt() {
        Response r = authGet("/api/inventory/other-receipts/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  查询不存在入库单 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "查询不存在的ID应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 查询不存在的调拨单ID应返回错误")
    void testGetNonExistentStockTransfer() {
        Response r = authGet("/api/inventory/stock-transfers/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  查询不存在调拨单 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "查询不存在的ID应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 对不存在的入库单执行审核应返回错误")
    void testApproveNonExistentOtherReceipt() {
        Response r = authPost("/api/inventory/other-receipts/{id}/approve", null, 99999999L);
        int code = r.path("code");
        System.out.println("  不存在入库单approve code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "对不存在的ID执行审核应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 对不存在的盘点单执行开始盘点应返回错误")
    void testStartCheckNonExistentStockCheck() {
        Response r = authPost("/api/inventory/stock-checks/{id}/start-check", null, 99999999L);
        int code = r.path("code");
        System.out.println("  不存在盘点单start-check code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "对不存在的ID执行状态流转应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 删除不存在的生产领料单应返回错误或幂等成功")
    void testDeleteNonExistentMaterialRequisition() {
        Response r = authDelete("/api/inventory/material-requisitions/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  删除不存在领料单 code=" + code + " (预期非200或幂等200)");
        org.junit.jupiter.api.Assertions.assertTrue(code == 200 || code == 500 || code == 404,
                "删除不存在的ID应返回200/404/500，实际: " + code);
    }
}
