package com.erp.test.basedata;

import com.erp.test.TestEnv;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;

/**
 * 基础资料模块测试基类
 * 提供登录 token 获取、通用请求封装、通用断言等能力
 *
 * 多租户口径（2026-09-20）：目标环境与账号从系统属性/环境变量读（见 TestEnv），
 * 登录后必须完成租户选择，否则请求会落主库 erp_db。
 */
public abstract class BaseBasedataTest {

    protected static String token;
    /** 目标环境地址（系统属性 erp.test.base-url / 环境变量 ERP_TEST_BASE_URL） */
    protected static final String BASE_URL = TestEnv.baseUrl();

    @BeforeAll
    static void loginAndGetToken() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.urlEncodingEnabled = true;

        // 登录 → 选租户（三步闸门）；禁止平台超管 admin（其 token 无 tenantId，业务请求会落主库 erp_db）
        token = TestEnv.loginAndSelectTenant(TestEnv.tenantAdminUser(), TestEnv.tenantAdminPassword());

        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("登录失败，无法获取 token");
        }
        System.out.println("[BaseBasedataTest] 登录成功, target=" + BASE_URL
                + ", token=" + token.substring(0, Math.min(20, token.length())) + "...");
    }

    /** 带 token 的 GET 请求 */
    protected Response authGet(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(path, pathParams);
    }

    /** 带 token 的 GET 请求（含 query 参数） */
    protected Response authGetWithParams(String path, Map<String, ?> params) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .queryParams(params)
                .when()
                .get(path);
    }

    /** 带 token 的 POST 请求 */
    protected Response authPost(String path, Object body, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(path, pathParams);
    }

    /** 带 token 的 PUT 请求 */
    protected Response authPut(String path, Object body) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put(path);
    }

    /** 带 token 的 DELETE 请求 */
    protected Response authDelete(String path, Object... pathParams) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(path, pathParams);
    }
}
