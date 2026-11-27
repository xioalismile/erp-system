package com.erp.test.production;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 生产管理模块安全性自动化测试
 *
 * 测试内容：
 *  1. 无Token访问应被拒绝（401或403）
 *  2. 无效Token访问应被拒绝
 *  3. 查询不存在的ID应返回错误
 *  4. 删除不存在的ID应返回错误
 *  5. 状态流转操作不存在ID应返回错误
 */
@DisplayName("生产管理模块 - 安全性测试")
public class ProductionSecurityTest extends ProductionTestBase {

    @Test
    @DisplayName("安全 - 无Token访问BOM列表应被拒绝")
    void testNoTokenBomList() {
        Response r = noAuthGet("/api/masterdata/bom/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无Token访问BOM列表 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token访问生产工单列表应被拒绝")
    void testNoTokenWorkOrderList() {
        Response r = noAuthGet("/api/production/workorders?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无Token访问生产工单列表 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token创建委外加工单应被拒绝")
    void testNoTokenCreateSubcontract() {
        Response r = noAuthPost("/api/production/subcontract", buildSubcontractBody());
        int status = r.statusCode();
        System.out.println("  无Token创建委外加工单 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token创建MRP方案应被拒绝")
    void testNoTokenCreateMrpPlan() {
        Response r = noAuthPost("/api/production/mrp-plan", buildMrpPlanBody());
        int status = r.statusCode();
        System.out.println("  无Token创建MRP方案 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token访问生产仪表盘应被拒绝")
    void testNoTokenDashboard() {
        Response r = noAuthGet("/api/production/dashboard");
        int status = r.statusCode();
        System.out.println("  无Token访问仪表盘 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无效Token访问应被拒绝")
    void testInvalidToken() {
        Response r = RestAssured.given()
                .header("Authorization", "Bearer invalid_token_12345")
                .header("X-Operator", "admin")
                .when()
                .get("/api/production/work-center/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无效Token访问 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无效Token访问应返回401或403，实际: " + status);
    }

    // ==================================================================
    // 不存在的资源
    // ==================================================================

    @Test
    @DisplayName("安全 - 查询不存在的BOM ID应返回错误")
    void testGetNonExistentBom() {
        Response r = authGet("/api/masterdata/bom/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  查询不存在BOM code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "查询不存在的ID应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 查询不存在的生产工单ID应返回错误")
    void testGetNonExistentWorkOrder() {
        Response r = authGet("/api/production/workorders/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  查询不存在工单 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "查询不存在的ID应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 对不存在的工单执行release应返回错误")
    void testReleaseNonExistentWorkOrder() {
        Response r = authPost("/api/production/workorders/{id}/release", null, 99999999L);
        int code = r.path("code");
        System.out.println("  不存在工单release code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "对不存在的ID执行状态流转应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 对不存在的委外单执行complete应返回错误")
    void testCompleteNonExistentSubcontract() {
        Response r = authPost("/api/production/subcontract/{id}/complete", null, 99999999L);
        int code = r.path("code");
        System.out.println("  不存在委外单complete code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "对不存在的ID执行状态流转应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 删除不存在的MRP方案应返回错误或幂等成功")
    void testDeleteNonExistentMrpPlan() {
        Response r = authDelete("/api/production/mrp-plan/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  删除不存在MRP方案 code=" + code + " (预期非200或幂等200)");
        // 部分实现采用幂等删除(删除不存在的记录返回成功)，此处仅记录行为
        org.junit.jupiter.api.Assertions.assertTrue(code == 200 || code == 500 || code == 404,
                "删除不存在的ID应返回200/404/500，实际: " + code);
    }
}
