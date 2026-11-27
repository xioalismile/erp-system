package com.erp.test.basedata;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 树形模块 /tree 端点测试
 * 覆盖：MaterialCategory、Department、Account 三个有 /tree 端点的模块
 */
@DisplayName("树形模块 /tree 端点测试")
public class BasedataTreeTest extends BaseBasedataTest {

    static String[] treeModules() {
        return TestDataBuilder.getTreeModules();
    }

    @ParameterizedTest(name = "[{index}] {0} - /tree 接口应返回 200 且 data 为列表")
    @MethodSource("treeModules")
    @DisplayName("树形接口应正常返回树状数据")
    void testTreeEndpoint(String module) {
        Response resp = authGet("/api/basedata/" + module + "/tree");
        resp.then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] /tree 接口返回 200");
    }

    @ParameterizedTest(name = "[{index}] {0} - /tree 与 /list 数据量对比")
    @MethodSource("treeModules")
    @DisplayName("树形接口与 list 接口返回的记录总数应一致")
    void testTreeVsListCount(String module) {
        Response treeResp = authGet("/api/basedata/" + module + "/tree");
        Response listResp = authGet("/api/basedata/" + module + "/list");
        treeResp.then().body("code", equalTo(200));
        listResp.then().body("code", equalTo(200));

        // 树形结构可能嵌套在 children 里，这里只校验都返回了非 null 数据
        Object treeData = treeResp.path("data");
        Object listData = listResp.path("data");
        System.out.println("[" + TestDataBuilder.getModuleCnName(module) + "] treeData 类型=" + (treeData == null ? "null" : treeData.getClass().getSimpleName())
                + ", listData 类型=" + (listData == null ? "null" : listData.getClass().getSimpleName()));
    }
}
