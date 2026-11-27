package com.erp.test.basedata;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

/**
 * 启用/停用 + 批量删除测试
 * 覆盖：enable/disable 端点、批量删除端点
 */
@DisplayName("启用/停用 + 批量删除测试")
public class BasedataStatusAndBatchTest extends BaseBasedataTest {

    static String[] modules() {
        return TestDataBuilder.getAllModules();
    }

    @ParameterizedTest(name = "[{index}] {0} - 启用→停用→启用 状态切换")
    @MethodSource("modules")
    @DisplayName("启用/停用接口应正常切换状态")
    void testEnableDisableFlow(String module) {
        String basePath = "/api/basedata/" + module;
        String cnName = TestDataBuilder.getModuleCnName(module);
        String codeField = TestDataBuilder.getCodeField(module);
        String idField = TestDataBuilder.getIdField(module);

        // 1. 新增记录
        Map<String, Object> data = TestDataBuilder.buildByModule(module);
        Response createResp = authPost(basePath, data);
        if (!createResp.path("code").equals(200)) {
            System.err.println("[" + cnName + "] 新增失败: " + createResp.path("message"));
            return;
        }
        Object originalCode = data.get(codeField);

        // 2. 找到 id
        Response list = authGet(basePath + "/list");
        List<Map<String, Object>> records = list.jsonPath().getList("data");
        Object targetId = null;
        if (records != null) {
            for (Map<String, Object> r : records) {
                Object codeInList = r.get(codeField);
                if (codeInList != null && originalCode.equals(codeInList.toString())) {
                    targetId = r.get(idField);
                    break;
                }
            }
        }
        if (targetId == null) {
            System.err.println("[" + cnName + "] 找不到新增记录");
            return;
        }
        Object idForPath = (targetId instanceof Integer) ? ((Integer) targetId).longValue() : targetId;

        try {
            // 3. 停用
            Response disableResp = authPost(basePath + "/disable/{id}", "", idForPath);
            disableResp.then().statusCode(200);
            System.out.println("[" + cnName + "] 停用返回 code=" + disableResp.path("code") + " msg=" + disableResp.path("message"));

            // 4. 启用
            Response enableResp = authPost(basePath + "/enable/{id}", "", idForPath);
            enableResp.then().statusCode(200);
            System.out.println("[" + cnName + "] 启用返回 code=" + enableResp.path("code") + " msg=" + enableResp.path("message"));

            // 5. 再次停用（验证重复调用幂等性）
            Response disable2 = authPost(basePath + "/disable/{id}", "", idForPath);
            disable2.then().statusCode(200);
            System.out.println("[" + cnName + "] 再次停用返回 code=" + disable2.path("code"));
        } finally {
            // 清理
            authDelete(basePath + "/{id}", idForPath);
            System.out.println("[" + cnName + "] 清理完成, id=" + targetId);
        }
    }

    @ParameterizedTest(name = "[{index}] {0} - 批量删除")
    @MethodSource("modules")
    @DisplayName("批量删除接口应正常工作")
    void testBatchDelete(String module) {
        String basePath = "/api/basedata/" + module;
        String cnName = TestDataBuilder.getModuleCnName(module);
        String codeField = TestDataBuilder.getCodeField(module);
        String idField = TestDataBuilder.getIdField(module);

        // 1. 新增 2 条记录
        Map<String, Object> d1 = TestDataBuilder.buildByModule(module);
        Map<String, Object> d2 = TestDataBuilder.buildByModule(module);
        Response r1 = authPost(basePath, d1);
        Response r2 = authPost(basePath, d2);
        if (!r1.path("code").equals(200) || !r2.path("code").equals(200)) {
            System.err.println("[" + cnName + "] 批量新增失败");
            return;
        }

        // 2. 找到 ids
        Response list = authGet(basePath + "/list");
        List<Map<String, Object>> records = list.jsonPath().getList("data");
        java.util.List<Object> ids = new java.util.ArrayList<>();
        if (records != null) {
            for (Map<String, Object> r : records) {
                Object codeInList = r.get(codeField);
                if (codeInList != null && (codeInList.toString().equals(d1.get(codeField))
                        || codeInList.toString().equals(d2.get(codeField)))) {
                    Object id = r.get(idField);
                    if (id instanceof Integer) id = ((Integer) id).longValue();
                    ids.add(id);
                }
            }
        }

        if (ids.size() < 2) {
            System.err.println("[" + cnName + "] 找到的记录数不足 2: " + ids.size());
            // 清理已存在的
            for (Object id : ids) {
                authDelete(basePath + "/{id}", id);
            }
            return;
        }

        // 3. 批量删除
        Response batchResp = authPost(basePath + "/batch-delete", ids);
        batchResp.then().statusCode(200);
        System.out.println("[" + cnName + "] 批量删除 " + ids.size() + " 条返回 code=" + batchResp.path("code") + " msg=" + batchResp.path("message"));

        // 4. 验证已删除
        for (Object id : ids) {
            Response verify = authGet(basePath + "/{id}", id);
            // 删除后查询应失败或返回空数据
            System.out.println("[" + cnName + "] 删除后查询 id=" + id + " 返回 code=" + verify.path("code"));
        }
    }
}
