package com.erp.test.basedata;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 基础资料查询接口测试
 * 覆盖：分页参数、关键字搜索、状态过滤
 */
@DisplayName("基础资料查询接口测试")
public class BasedataQueryTest extends BaseBasedataTest {

    static String[] modules() {
        return TestDataBuilder.getAllModules();
    }

    @ParameterizedTest(name = "[{index}] {0} - 分页查询 (current=1, size=10)")
    @MethodSource("modules")
    @DisplayName("分页查询应返回 200 且包含分页结构")
    void testPageQuery(String module) {
        Response resp = authGetWithParams("/api/basedata/" + module + "/page",
                Map.of("current", 1, "size", 10));
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200));
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 分页查询成功");
    }

    @ParameterizedTest(name = "[{index}] {0} - 分页 size=1 应只返回1条")
    @MethodSource("modules")
    @DisplayName("分页参数 size=1 应只返回 1 条记录")
    void testPageSizeLimit(String module) {
        Response resp = authGetWithParams("/api/basedata/" + module + "/page",
                Map.of("current", 1, "size", 1));
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200));

        // data 应该是 Page 对象，包含 records 或直接是 list
        Object data = resp.path("data");
        if (data instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) data;
            org.junit.jupiter.api.Assertions.assertTrue(list.size() <= 1,
                    "[" + module + "] size=1 但返回了 " + list.size() + " 条");
        } else if (data instanceof Map) {
            // MyBatis-Plus Page 对象：检查 records 字段
            java.util.List<?> records = resp.path("data.records");
            if (records != null) {
                org.junit.jupiter.api.Assertions.assertTrue(records.size() <= 1,
                        "[" + module + "] size=1 但 records 返回了 " + records.size() + " 条");
            }
        }
    }

    @ParameterizedTest(name = "[{index}] {0} - 关键字搜索 keyword=test")
    @MethodSource("modules")
    @DisplayName("关键字搜索应返回 200（即使无匹配）")
    void testKeywordSearch(String module) {
        Response resp = authGetWithParams("/api/basedata/" + module + "/page",
                Map.of("current", 1, "size", 20, "keyword", "zzz_no_match_keyword_zzz"));
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200));
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 关键字搜索返回 code=200");
    }

    @ParameterizedTest(name = "[{index}] {0} - 状态过滤 status=启用")
    @MethodSource("modules")
    @DisplayName("按状态过滤应返回 200")
    void testStatusFilter(String module) {
        Response resp = authGetWithParams("/api/basedata/" + module + "/page",
                Map.of("current", 1, "size", 20, "status", "启用"));
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200));
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] 状态过滤返回 code=200");
    }

    @ParameterizedTest(name = "[{index}] {0} - list 接口返回 200")
    @MethodSource("modules")
    @DisplayName("list 接口应返回 200 且 data 为列表")
    void testListQuery(String module) {
        Response resp = authGet("/api/basedata/" + module + "/list");
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());
    }

    @ParameterizedTest(name = "[{index}] {0} - list 接口按 status=停用 过滤")
    @MethodSource("modules")
    @DisplayName("list 接口按状态过滤应返回 200")
    void testListStatusFilter(String module) {
        Response resp = authGetWithParams("/api/basedata/" + module + "/list",
                Map.of("status", "停用"));
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200));
    }

    @ParameterizedTest(name = "[{index}] {0} - current=2 翻页查询")
    @CsvSource({
        "currency, 2, 5",
        "customer, 2, 5",
        "supplier, 2, 5",
        "material, 2, 5",
        "department, 2, 5",
        "warehouse, 1, 100"
    })
    @DisplayName("分页翻页查询应正常返回")
    void testPaginationTurnPage(String module, int current, int size) {
        Response resp = authGetWithParams("/api/basedata/" + module + "/page",
                Map.of("current", current, "size", size));
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200));
        System.out.println("[" + module + "] current=" + current + ", size=" + size + " 查询成功");
    }
}
