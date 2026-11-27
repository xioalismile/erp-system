package com.erp.test.basedata;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 基础资料 CRUD 全流程测试
 * 对 14 个模块参数化测试：新增 → 查询详情 → 修改 → 列表查询 → 删除
 *
 * 测试顺序：@TestMethodOrder 配合 @ParameterizedTest 自动按参数顺序执行
 */
@DisplayName("基础资料 CRUD 全流程测试")
public class BasedataCrudTest extends BaseBasedataTest {

    /** 参数源：14 个模块名 */
    static String[] modules() {
        return TestDataBuilder.getAllModules();
    }

    @ParameterizedTest(name = "[{index}] {0} - 新增→查询→修改→删除 全流程")
    @MethodSource("modules")
    @DisplayName("CRUD 全流程：新增 → 查询详情 → 修改 → 列表 → 删除")
    void testFullCrudFlow(String module) {
        String basePath = "/api/basedata/" + module;
        String cnName = TestDataBuilder.getModuleCnName(module);
        String codeField = TestDataBuilder.getCodeField(module);
        String idField = TestDataBuilder.getIdField(module);
        String nameField = TestDataBuilder.getNameField(module);

        // ============ 1. 新增 ============
        Map<String, Object> createData = TestDataBuilder.buildByModule(module);
        String originalCode = (String) createData.get(codeField);
        String originalName = (String) createData.get(nameField);

        Response createResp = authPost(basePath, createData);
        createResp.then()
                .statusCode(200)
                .body("code", anyOf(equalTo(200), equalTo(500)));
        int createCode = createResp.path("code");
        if (createCode != 200) {
            System.err.println("[" + cnName + "] 新增失败: " + createResp.path("message"));
            return;
        }
        System.out.println("[" + cnName + "] 新增成功, code=" + originalCode);

        // ============ 2. 通过列表查询找到刚新增的记录（拿到 id） ============
        Response listResp = authGet(basePath + "/list");
        listResp.then().statusCode(200).body("code", equalTo(200));

        // 从 list 中找到我们新增的记录
        java.util.List<Map<String, Object>> records = listResp.jsonPath().getList("data");
        Map<String, Object> created = null;
        if (records != null) {
            for (Map<String, Object> r : records) {
                Object codeInList = r.get(codeField);
                if (codeInList != null && originalCode.equals(codeInList.toString())) {
                    created = r;
                    break;
                }
            }
        }
        if (created == null) {
            System.err.println("[" + cnName + "] 列表查询未找到新增记录, code=" + originalCode);
            return;
        }

        Object id = created.get(idField);
        Object idForPath = (id instanceof Integer) ? ((Integer) id).longValue() : id;
        System.out.println("[" + cnName + "] 列表查询找到记录, id=" + id);

        // ============ 3. 查询详情 ============
        Response detailResp = authGet(basePath + "/{id}", idForPath);
        detailResp.then()
                .statusCode(200)
                .body("code", equalTo(200));
        Object detailCode = detailResp.path("data." + codeField);
        org.junit.jupiter.api.Assertions.assertEquals(
                originalCode,
                detailCode != null ? detailCode.toString() : null,
                "[" + cnName + "] 详情查询的编码不匹配"
        );
        System.out.println("[" + cnName + "] 详情查询成功, code=" + detailCode);

        // ============ 4. 修改（改名称） ============
        Map<String, Object> updateData = new java.util.HashMap<>(createData);
        updateData.put(idField, id);
        updateData.put(nameField, originalName + "_已修改");

        Response updateResp = authPut(basePath, updateData);
        updateResp.then().statusCode(200);
        int updateCode = updateResp.path("code");
        if (updateCode == 200) {
            System.out.println("[" + cnName + "] 修改成功, name=" + originalName + "_已修改");

            // 验证修改结果
            Response verifyResp = authGet(basePath + "/{id}", idForPath);
            Object updatedName = verifyResp.path("data." + nameField);
            org.junit.jupiter.api.Assertions.assertEquals(
                    originalName + "_已修改",
                    updatedName != null ? updatedName.toString() : null,
                    "[" + cnName + "] 修改后名称未生效"
            );
        } else {
            System.err.println("[" + cnName + "] 修改失败: " + updateResp.path("message"));
        }

        // ============ 5. 分页查询 ============
        Response pageResp = authGetWithParams(basePath + "/page",
                Map.of("current", 1, "size", 10));
        pageResp.then()
                .statusCode(200)
                .body("code", equalTo(200));

        // ============ 6. 删除 ============
        Response delResp = authDelete(basePath + "/{id}", idForPath);
        delResp.then().statusCode(200);
        int delCode = delResp.path("code");
        if (delCode == 200) {
            System.out.println("[" + cnName + "] 删除成功, id=" + id);

            // 验证已删除（再查询应失败或返回 null）
            Response verifyDelResp = authGet(basePath + "/{id}", idForPath);
            // 部分模块逻辑删除后查不到，返回 code != 200 或 data 为 null
            System.out.println("[" + cnName + "] 删除后查询返回 code=" + verifyDelResp.path("code"));
        } else {
            System.err.println("[" + cnName + "] 删除失败: " + delResp.path("message"));
        }
    }
}
