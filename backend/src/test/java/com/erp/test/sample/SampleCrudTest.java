package com.erp.test.sample;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 打样管理模块 CRUD 自动化测试
 *
 * 覆盖文档5.9 打样单的：
 *  1. 列表分页查询（PAGE）
 *  2. 创建打样单（CREATE）
 *  3. 按ID查询详情（GET BY ID）
 *  4. 更新打样单（UPDATE）
 *  5. 删除打样单（DELETE）
 */
@DisplayName("打样管理模块 - CRUD测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SampleCrudTest extends SampleTestBase {

    @Test
    @Order(1)
    @DisplayName("打样单 - 列表分页查询")
    void testSampleListPage() {
        Response r = authGetWithParams("/api/sample/orders/page",
                Map.of("current", 1, "size", 10));
        assertSuccess(r, "Sample LIST/PAGE");
        r.then().body("data", notNullValue());
        System.out.println("  打样单列表查询成功, total=" + r.path("data.total"));
    }

    @Test
    @Order(2)
    @DisplayName("打样单 - 创建打样单(含子件清单)")
    void testSampleCreate() {
        Response r = authPost("/api/sample/orders", buildSampleBody());
        assertSuccess(r, "Sample CREATE");
        r.then().body("data.sampleId", notNullValue());
        r.then().body("data.sampleNo", notNullValue());
        r.then().body("data.status", equalTo("草稿"));
        r.then().body("data.sampleNo", startsWith("SAM-"));
        System.out.println("  打样单创建成功, sampleId=" + r.path("data.sampleId") + ", sampleNo=" + r.path("data.sampleNo"));
    }

    @Test
    @Order(3)
    @DisplayName("打样单 - 按ID查询详情(含子件清单)")
    void testSampleGetById() {
        Long sampleId = createSampleAndGetId();
        Response r = authGet("/api/sample/orders/{id}", sampleId);
        assertSuccess(r, "Sample GET BY ID");
        r.then().body("data.sampleId", equalTo(sampleId.intValue()));
        r.then().body("data.items", notNullValue());
        System.out.println("  打样单详情查询成功, items数量=" + ((java.util.List<?>) r.path("data.items")).size());
    }

    @Test
    @Order(4)
    @DisplayName("打样单 - 更新打样单")
    void testSampleUpdate() {
        Long sampleId = createSampleAndGetId();
        Map<String, Object> body = buildSampleBody();
        body.put("sampleId", sampleId);
        body.put("sampleName", "更新后样品名称");
        Response r = authPut("/api/sample/orders/{id}", body, sampleId);
        assertSuccess(r, "Sample UPDATE");
        // 验证更新结果
        Response getResp = authGet("/api/sample/orders/{id}", sampleId);
        getResp.then().body("data.sampleName", equalTo("更新后样品名称"));
    }

    @Test
    @Order(5)
    @DisplayName("打样单 - 删除打样单")
    void testSampleDelete() {
        Long sampleId = createSampleAndGetId();
        Response r = authDelete("/api/sample/orders/{id}", sampleId);
        assertSuccess(r, "Sample DELETE");
    }

    @Test
    @Order(6)
    @DisplayName("打样单 - 生成打样单号")
    void testGenerateSampleNo() {
        Response r = authGet("/api/sample/orders/generate-no");
        assertSuccess(r, "Generate Sample No");
        String sampleNo = r.path("data.sampleNo");
        System.out.println("  生成的打样单号: " + sampleNo);
        org.junit.jupiter.api.Assertions.assertNotNull(sampleNo);
        org.junit.jupiter.api.Assertions.assertTrue(sampleNo.startsWith("SAM-"));
    }

    @Test
    @Order(7)
    @DisplayName("打样单 - 仪表盘统计")
    void testSampleDashboard() {
        Response r = authGet("/api/sample/orders/dashboard");
        assertSuccess(r, "Sample Dashboard");
        r.then().body("data", notNullValue());
        System.out.println("  仪表盘数据: " + r.path("data"));
    }

    @Test
    @Order(8)
    @DisplayName("打样单 - 关键字搜索")
    void testSampleKeywordSearch() {
        // 先创建一条
        Long sampleId = createSampleAndGetId();
        // 获取样品名称
        Response getResp = authGet("/api/sample/orders/{id}", sampleId);
        String sampleName = getResp.path("data.sampleName");

        Response r = authGetWithParams("/api/sample/orders/page",
                Map.of("current", 1, "size", 50, "keyword", sampleName));
        assertSuccess(r, "Sample Keyword Search");
        r.then().body("data.records", not(empty()));
        System.out.println("  关键字搜索成功, 关键字=" + sampleName);
    }

    @Test
    @Order(9)
    @DisplayName("打样单 - 状态筛选")
    void testSampleStatusFilter() {
        Response r = authGetWithParams("/api/sample/orders/page",
                Map.of("current", 1, "size", 50, "status", "草稿"));
        assertSuccess(r, "Sample Status Filter");
        // 所有返回记录状态都应为"草稿"
        java.util.List<Map<String, Object>> records = r.path("data.records");
        if (records != null && !records.isEmpty()) {
            for (Map<String, Object> rec : records) {
                org.junit.jupiter.api.Assertions.assertEquals("草稿", rec.get("status"),
                        "状态筛选结果应全为草稿状态");
            }
        }
        System.out.println("  状态筛选成功, 草稿数量=" + (records != null ? records.size() : 0));
    }
}
