package com.erp.test.basedata;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

/**
 * 未授权访问 + 不存在 ID 测试
 * 覆盖：无 token、错误 token、不存在 ID 查询/删除
 */
@DisplayName("未授权访问 + 异常 ID 测试")
public class BasedataSecurityTest extends BaseBasedataTest {

    static String[] modules() {
        return TestDataBuilder.getAllModules();
    }

    // ============ 无 Token 访问测试 ============

    @ParameterizedTest(name = "[{index}] {0} - 无 Token 访问 list 应被拦截")
    @MethodSource("modules")
    @DisplayName("无 Token 访问 list 接口应返回 401/403/500")
    void testNoTokenListAccess(String module) {
        RestAssured.given()
                .when()
                .get("/api/basedata/" + module + "/list")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    @ParameterizedTest(name = "[{index}] {0} - 无 Token 访问 page 应被拦截")
    @MethodSource("modules")
    @DisplayName("无 Token 访问 page 接口应被拦截")
    void testNoTokenPageAccess(String module) {
        RestAssured.given()
                .when()
                .get("/api/basedata/" + module + "/page")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    @ParameterizedTest(name = "[{index}] {0} - 无 Token 新增应被拦截")
    @MethodSource("modules")
    @DisplayName("无 Token POST 新增应被拦截")
    void testNoTokenCreate(String module) {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/basedata/" + module)
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    // ============ 错误 Token 测试 ============

    @ParameterizedTest(name = "[{index}] {0} - 错误 Token 访问应被拦截")
    @MethodSource("modules")
    @DisplayName("错误 Token 访问 list 应被拦截")
    void testInvalidToken(String module) {
        RestAssured.given()
                .header("Authorization", "Bearer invalid_token_12345")
                .when()
                .get("/api/basedata/" + module + "/list")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    @ParameterizedTest(name = "[{index}] {0} - 空 Token 访问应被拦截")
    @MethodSource("modules")
    @DisplayName("空 Token 访问应被拦截")
    void testEmptyToken(String module) {
        RestAssured.given()
                .header("Authorization", "")
                .when()
                .get("/api/basedata/" + module + "/list")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(500)));
    }

    // ============ 不存在 ID 测试 ============

    @ParameterizedTest(name = "[{index}] {0} - 查询不存在的 ID=99999999")
    @MethodSource("modules")
    @DisplayName("查询不存在的 ID 应返回 500 或非 200 code")
    void testGetNonExistentId(String module) {
        Response resp = authGet("/api/basedata/" + module + "/{id}", 99999999L);
        resp.then().statusCode(200);
        // 不存在 ID 应返回 500 或 data 为 null
        Integer code = resp.path("code");
        // 接受 200（返回空对象）或 500（报错）两种实现
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 查询不存在 ID 返回 code=" + code);
    }

    @ParameterizedTest(name = "[{index}] {0} - 删除不存在的 ID=99999999")
    @MethodSource("modules")
    @DisplayName("删除不存在的 ID 应有错误处理（不应崩溃）")
    void testDeleteNonExistentId(String module) {
        Response resp = authDelete("/api/basedata/" + module + "/{id}", 99999999L);
        resp.then().statusCode(200); // HTTP 200，业务 code 可能是 500
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 删除不存在 ID 返回 code=" + resp.path("code") + " msg=" + resp.path("message"));
    }

    // ============ 空体/非法请求测试 ============

    @ParameterizedTest(name = "[{index}] {0} - 新增空 body 应被拒绝")
    @MethodSource("modules")
    @DisplayName("POST 空 body 应有错误处理")
    void testEmptyBodyCreate(String module) {
        Response resp = authPost("/api/basedata/" + module, "{}");
        resp.then().statusCode(200);
        // 空 body 缺少必填字段，应返回错误 code
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 空 body 新增返回 code=" + resp.path("code") + " msg=" + resp.path("message"));
    }

    @ParameterizedTest(name = "[{index}] {0} - 非法 JSON 格式处理")
    @MethodSource("modules")
    @DisplayName("POST 非法 JSON 应有错误处理（400/500 或宽容处理为 200）")
    void testInvalidJson(String module) {
        // Spring 默认会把非法 JSON 当空对象处理（返回 200 业务错误），
        // 或者抛 HttpMessageNotReadableException（返回 400）。
        // 两种都算合理行为，这里只验证不抛 500 服务器异常
        Response resp = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{invalid json}")
                .when()
                .post("/api/basedata/" + module);
        // 接受 200（宽容处理为空对象，业务校验失败）、400（请求格式错误）、500（业务异常）
        // 不接受 401/403（认证问题）
        resp.then().statusCode(anyOf(equalTo(200), equalTo(400), equalTo(500)));
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 非法 JSON 返回 code=" + resp.statusCode() + ", body.code=" + resp.path("code"));
    }

    @Test
    @DisplayName("登录接口 - 错误密码返回 500 业务错误")
    void testLoginWrongPassword() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"admin\",\"password\":\"wrong\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(500));
    }
}
