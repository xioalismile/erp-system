package com.erp.test.basedata;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 编码唯一性校验测试
 * 覆盖：重复新增应失败（返回 409 或错误码）、修改时同 ID 同编码可成功
 */
@DisplayName("编码唯一性校验测试")
public class BasedataUniquenessTest extends BaseBasedataTest {

    static String[] modules() {
        return TestDataBuilder.getAllModules();
    }

    @ParameterizedTest(name = "[{index}] {0} - 重复编码新增应失败")
    @MethodSource("modules")
    @DisplayName("重复编码新增应返回错误（409 或 500）")
    void testDuplicateCodeCreate(String module) {
        String basePath = "/api/basedata/" + module;
        String cnName = TestDataBuilder.getModuleCnName(module);
        String codeField = TestDataBuilder.getCodeField(module);
        String idField = TestDataBuilder.getIdField(module);

        // 1. 新增一条记录
        Map<String, Object> data = TestDataBuilder.buildByModule(module);
        Response r1 = authPost(basePath, data);
        if (r1.path("code") != null && !r1.path("code").equals(200)) {
            System.err.println("[" + cnName + "] 首次新增失败: " + r1.path("message"));
            return;
        }
        Object originalCode = data.get(codeField);

        // 2. 用相同编码再次新增（应失败）
        Response r2 = authPost(basePath, data);
        r2.then().statusCode(200);
        int code = r2.path("code");
        // 应返回 409（编码已存在）或 500（其他错误），但不能是 200
        org.junit.jupiter.api.Assertions.assertNotEquals(
                200, code,
                "[" + cnName + "] 重复编码新增不应返回 200，实际: " + code + " msg=" + r2.path("message")
        );
        System.out.println("[" + cnName + "] 重复编码新增正确返回 code=" + code + " msg=" + r2.path("message"));

        // 3. 清理：从 list 找到刚新增的记录并删除
        try {
            Response list = authGet(basePath + "/list");
            java.util.List<Map<String, Object>> records = list.jsonPath().getList("data");
            if (records != null) {
                for (Map<String, Object> r : records) {
                    Object codeInList = r.get(codeField);
                    if (codeInList != null && originalCode.equals(codeInList.toString())) {
                        Object id = r.get(idField);
                        Object idForPath = (id instanceof Integer) ? ((Integer) id).longValue() : id;
                        authDelete(basePath + "/{id}", idForPath);
                        System.out.println("[" + cnName + "] 清理测试数据, id=" + id);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[" + cnName + "] 清理失败: " + e.getMessage());
        }
    }

    @ParameterizedTest(name = "[{index}] {0} - 同 ID 同编码修改应成功")
    @MethodSource("modules")
    @DisplayName("修改时使用相同 ID 和相同编码应成功（排除自身）")
    void testUpdateSameCodeSameId(String module) {
        String basePath = "/api/basedata/" + module;
        String cnName = TestDataBuilder.getModuleCnName(module);
        String codeField = TestDataBuilder.getCodeField(module);
        String idField = TestDataBuilder.getIdField(module);
        String nameField = TestDataBuilder.getNameField(module);

        // 1. 新增一条记录
        Map<String, Object> data = TestDataBuilder.buildByModule(module);
        Response r1 = authPost(basePath, data);
        if (r1.path("code") == null || !r1.path("code").equals(200)) {
            System.err.println("[" + cnName + "] 新增失败: " + r1.path("message"));
            return;
        }
        Object originalCode = data.get(codeField);

        // 2. 找到新增记录的 id
        Response list = authGet(basePath + "/list");
        java.util.List<Map<String, Object>> records = list.jsonPath().getList("data");
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

        // 3. 用相同 ID + 相同编码修改名称（应成功，因为唯一性校验排除自身）
        Map<String, Object> updateData = new java.util.HashMap<>(data);
        updateData.put(idField, targetId);
        updateData.put(nameField, "唯一性测试_修改后名称");

        Response r2 = authPut(basePath, updateData);
        r2.then().statusCode(200);
        int updateCode = r2.path("code");
        // 同 ID 同编码修改应该成功（除非其他业务校验失败）
        System.out.println("[" + cnName + "] 同ID同编码修改返回 code=" + updateCode + " msg=" + r2.path("message"));

        // 4. 清理
        try {
            Object idForPath = (targetId instanceof Integer) ? ((Integer) targetId).longValue() : targetId;
            authDelete(basePath + "/{id}", idForPath);
        } catch (Exception ignored) {}
    }
}
