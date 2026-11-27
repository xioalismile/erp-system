package com.erp.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 登录接口自动化测试用例
 * 覆盖: 正常登录、密码错误、用户不存在、空值、SQL注入等场景
 */
@DisplayName("登录接口测试")
public class AuthLoginTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8080;
        RestAssured.basePath = "/api/auth";
    }

    @Test
    @DisplayName("正确账号密码登录成功")
    void testLoginSuccess() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"admin\",\"password\":\"Erp@2026\"}")
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body("code", equalTo(200))
            .body("data.token", notNullValue())
            .body("data.username", equalTo("admin"))
            .body("data.realName", notNullValue());
    }

    @ParameterizedTest(name = "密码错误场景: pwd={1}")
    @CsvSource({
        "admin, wrongpassword",
        "admin, ADMIN123",
        "admin, 123456",
        "admin, ''",
        "admin, admin1234"
    })
    @DisplayName("错误密码登录应失败")
    void testLoginWrongPassword(String username, String password) {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password))
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body("code", equalTo(500))
            .body("message", containsString("用户名或密码错误"));
    }

    @ParameterizedTest(name = "用户不存在场景: user={0}")
    @CsvSource({
        "nonexistent",
        "root",
        "administrator",
        "admin' OR '1'='1",
        "admin--"
    })
    @DisplayName("不存在用户登录应失败")
    void testLoginUserNotFound(String username) {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"username\":\"%s\",\"password\":\"Erp@2026\"}", username))
        .when()
            .post("/login")
        .then()
            .statusCode(200)
            .body("code", equalTo(500));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("用户名为空应返回错误")
    void testLoginEmptyUsername(String username) {
        String body = (username == null)
            ? "{\"password\":\"Erp@2026\"}"
            : String.format("{\"username\":\"%s\",\"password\":\"Erp@2026\"}", username);
        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/login")
        .then()
            .statusCode(200);
    }

    @Test
    @DisplayName("SQL注入攻击防御测试")
    void testSqlInjectionDefense() {
        String[] payloads = {
            "' OR '1'='1",
            "'; DROP TABLE sys_user; --",
            "' UNION SELECT * FROM sys_user --",
            "admin'/*",
            "admin' #"
        };
        for (String payload : payloads) {
            given()
                .contentType(ContentType.JSON)
                .body(String.format("{\"username\":\"%s\",\"password\":\"anything\"}", payload))
            .when()
                .post("/login")
            .then()
                .statusCode(200)
                .body("code", equalTo(500));
        }
    }

    @Test
    @DisplayName("登出接口测试")
    void testLogout() {
        given()
        .when()
            .post("/logout")
        .then()
            .statusCode(200)
            .body("code", equalTo(200));
    }
}
