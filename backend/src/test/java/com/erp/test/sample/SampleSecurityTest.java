package com.erp.test.sample;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 打样管理模块安全性自动化测试
 *
 * 测试内容：
 *  1. 无Token访问应被拒绝（401/403）
 *  2. 无效Token访问应被拒绝
 *  3. 查询不存在的ID应返回错误
 *  4. 对不存在的打样单执行状态流转应返回错误
 *  5. 删除不存在的ID应返回错误
 */
@DisplayName("打样管理模块 - 安全性测试")
public class SampleSecurityTest extends SampleTestBase {

    @Test
    @DisplayName("安全 - 无Token访问打样单列表应被拒绝")
    void testNoTokenList() {
        Response r = noAuthGet("/api/sample/orders/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无Token访问列表 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token创建打样单应被拒绝")
    void testNoTokenCreate() {
        Response r = noAuthPost("/api/sample/orders", buildSampleBody());
        int status = r.statusCode();
        System.out.println("  无Token创建打样单 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token转BOM应被拒绝")
    void testNoTokenConvert() {
        Response r = noAuthPost("/api/sample/orders/1/convert-to-bom", null);
        int status = r.statusCode();
        System.out.println("  无Token转BOM HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 无Token访问仪表盘应被拒绝")
    void testNoTokenDashboard() {
        Response r = noAuthGet("/api/sample/orders/dashboard");
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
                .when()
                .get("/api/sample/orders/page?current=1&size=10");
        int status = r.statusCode();
        System.out.println("  无效Token访问 HTTP状态=" + status + " (预期401/403)");
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "无效Token访问应返回401或403，实际: " + status);
    }

    @Test
    @DisplayName("安全 - 查询不存在的打样单ID应返回错误")
    void testGetNonExistent() {
        Response r = authGet("/api/sample/orders/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  查询不存在打样单 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "查询不存在的ID应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 对不存在的打样单执行转BOM应返回错误")
    void testConvertNonExistent() {
        Response r = authPost("/api/sample/orders/{id}/convert-to-bom", null, 99999999L);
        int code = r.path("code");
        System.out.println("  不存在打样单转BOM code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "对不存在的ID执行转BOM应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 对不存在的打样单执行提交审核应返回错误")
    void testSubmitNonExistent() {
        Response r = authPost("/api/sample/orders/{id}/submit", null, 99999999L);
        int code = r.path("code");
        System.out.println("  不存在打样单提交 code=" + code + " (预期非200)");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, code,
                "对不存在的ID执行状态流转应返回非200，实际: " + code);
    }

    @Test
    @DisplayName("安全 - 删除不存在的打样单应返回错误或幂等成功")
    void testDeleteNonExistent() {
        Response r = authDelete("/api/sample/orders/{id}", 99999999L);
        int code = r.path("code");
        System.out.println("  删除不存在打样单 code=" + code + " (预期非200或幂等200)");
        org.junit.jupiter.api.Assertions.assertTrue(code == 200 || code == 500 || code == 404,
                "删除不存在的ID应返回200/404/500，实际: " + code);
    }
}
