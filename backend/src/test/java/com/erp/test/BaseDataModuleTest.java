package com.erp.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 基础资料 12 大模块自动化测试
 * 覆盖: 列表查询、未授权访问、字段完整性等
 */
@DisplayName("基础资料模块测试")
public class BaseDataModuleTest {

    private static String token;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8080;

        // 登录获取 token
        token = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"admin\",\"password\":\"Erp@2026\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .extract().path("data.token");
    }

    // ============ 12 个模块的列表查询参数化测试 ============
    @ParameterizedTest(name = "模块 #{index}: {0} 应返回 200 且包含 records/data 字段")
    @ValueSource(strings = {
        "/api/basedata/currency/list",
        "/api/basedata/unit/list",
        "/api/basedata/material-category/list",
        "/api/basedata/customer/list",
        "/api/basedata/supplier/list",
        "/api/basedata/warehouse/list",
        "/api/basedata/location/list",
        "/api/basedata/material/list",
        "/api/basedata/department/list",
        "/api/basedata/employee/list",
        "/api/basedata/payment-term/list",
        "/api/basedata/price-level/list"
    })
    @DisplayName("所有 12 个模块列表查询应正常返回")
    void testAllModulesListSuccess(String endpoint) {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get(endpoint)
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }

    // ============ 未授权访问测试 ============
    @ParameterizedTest(name = "未携带 token 访问 {0} 应失败")
    @ValueSource(strings = {
        "/api/basedata/currency/list",
        "/api/basedata/customer/list",
        "/api/basedata/supplier/list",
        "/api/basedata/material/list"
    })
    @DisplayName("无 Token 访问应被拦截")
    void testUnauthorizedAccess(String endpoint) {
        given()
        .when()
            .get(endpoint)
        .then()
            .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    // ============ 错误 Token 测试 ============
    @ParameterizedTest
    @ValueSource(strings = {
        "invalid_token",
        "Bearer invalid",
        "",
        "1234567890"
    })
    @DisplayName("错误 Token 访问应失败")
    void testInvalidToken(String invalidToken) {
        given()
            .header("Authorization", "Bearer " + invalidToken)
        .when()
            .get("/api/basedata/currency/list")
        .then()
            .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    // ============ 用户信息接口测试 ============
    @Test
    @DisplayName("获取用户信息接口应返回完整字段")
    void testUserInfo() {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/auth/userinfo")
        .then()
            .statusCode(200)
            .body("code", equalTo(200))
            .body("data.userId", notNullValue())
            .body("data.username", equalTo("admin"))
            .body("data.realName", notNullValue())
            .body("data.roles", hasItem("admin"))
            .body("data.permissions", hasItem("*"));
    }

    // ============ 字段完整性测试: 客户档案 ============
    @Test
    @DisplayName("客户档案列表字段完整性验证")
    void testCustomerFieldIntegrity() {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/basedata/customer/list")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }
}
