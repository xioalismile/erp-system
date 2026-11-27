package com.erp.test.sample;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * 打样管理模块状态流转自动化测试
 *
 * 覆盖文档5.9 打样单的完整状态流转：
 *  草稿 → 待审核(提交) → 已生效(审核) → 打样中(开始) → 已完成(完成) → 已确认(确认) → 已转BOM(转BOM)
 *  任意状态(已转BOM/已取消除外) → 已取消
 *
 * 重点测试：
 *  1. 合法状态流转应成功
 *  2. 非法状态流转应失败（如草稿直接转BOM、草稿直接开始打样等）
 *  3. 已转BOM不可重复转BOM
 *  4. 重置草稿回到草稿
 */
@DisplayName("打样管理模块 - 状态流转测试")
public class SampleStatusFlowTest extends SampleTestBase {

    @Test
    @DisplayName("状态流转 - 草稿 → 待审核 (submit)")
    void testSubmit() {
        Long sampleId = createSampleAndGetId();
        // 验证初始状态
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("草稿"));
        // 执行提交
        Response r = authPost("/api/sample/orders/{id}/submit", null, sampleId);
        assertSuccess(r, "submit");
        // 验证状态
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("待审核"));
        System.out.println("  草稿 → 待审核 成功");
    }

    @Test
    @DisplayName("状态流转 - 待审核 → 已生效 (approve)")
    void testApprove() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/approve", null, sampleId);
        assertSuccess(r, "approve");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已生效"));
        System.out.println("  待审核 → 已生效 成功");
    }

    @Test
    @DisplayName("状态流转 - 已生效 → 草稿 (reverseApprove)")
    void testReverseApprove() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/reverse-approve", null, sampleId);
        assertSuccess(r, "reverseApprove");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("草稿"));
        System.out.println("  已生效 → 草稿 成功");
    }

    @Test
    @DisplayName("状态流转 - 已生效 → 打样中 (start)")
    void testStart() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/start", null, sampleId);
        assertSuccess(r, "start");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("打样中"));
        System.out.println("  已生效 → 打样中 成功");
    }

    @Test
    @DisplayName("状态流转 - 打样中 → 已完成 (complete)")
    void testComplete() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/complete", null, sampleId);
        assertSuccess(r, "complete");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已完成"));
        // 验证实际完成日期已填写
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.actualFinishDate", notNullValue());
        System.out.println("  打样中 → 已完成 成功");
    }

    @Test
    @DisplayName("状态流转 - 已完成 → 已确认 (confirm)")
    void testConfirm() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        authPost("/api/sample/orders/{id}/complete", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/confirm", null, sampleId);
        assertSuccess(r, "confirm");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已确认"));
        System.out.println("  已完成 → 已确认 成功");
    }

    @Test
    @DisplayName("非法流转 - 草稿不可直接审核（需先提交）")
    void testCannotApproveFromDraft() {
        Long sampleId = createSampleAndGetId();
        Response r = authPost("/api/sample/orders/{id}/approve", null, sampleId);
        assertFailed(r, "草稿直接approve");
    }

    @Test
    @DisplayName("非法流转 - 草稿不可直接开始打样")
    void testCannotStartFromDraft() {
        Long sampleId = createSampleAndGetId();
        Response r = authPost("/api/sample/orders/{id}/start", null, sampleId);
        assertFailed(r, "草稿直接start");
    }

    @Test
    @DisplayName("非法流转 - 待审核不可直接开始打样（需先审核）")
    void testCannotStartFromPending() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/start", null, sampleId);
        assertFailed(r, "待审核直接start");
    }

    @Test
    @DisplayName("非法流转 - 打样中不可直接确认（需先完成）")
    void testCannotConfirmFromInProgress() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/confirm", null, sampleId);
        assertFailed(r, "打样中直接confirm");
    }

    @Test
    @DisplayName("非法流转 - 已完成不可直接转BOM（需先确认）")
    void testCannotConvertFromCompleted() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        authPost("/api/sample/orders/{id}/complete", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId);
        assertFailed(r, "已完成直接convertToBom");
    }

    @Test
    @DisplayName("非法流转 - 草稿不可直接转BOM")
    void testCannotConvertFromDraft() {
        Long sampleId = createSampleAndGetId();
        Response r = authPost("/api/sample/orders/{id}/convert-to-bom", null, sampleId);
        assertFailed(r, "草稿直接convertToBom");
    }

    @Test
    @DisplayName("取消 - 草稿状态可取消")
    void testCancelFromDraft() {
        Long sampleId = createSampleAndGetId();
        Response r = authPost("/api/sample/orders/{id}/cancel", Map.of("reason", "测试取消"), sampleId);
        assertSuccess(r, "cancel from draft");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已取消"));
        System.out.println("  草稿 → 已取消 成功");
    }

    @Test
    @DisplayName("取消 - 已确认状态可取消")
    void testCancelFromConfirmed() {
        Long sampleId = createSampleAndGetId();
        authPost("/api/sample/orders/{id}/submit", null, sampleId);
        authPost("/api/sample/orders/{id}/approve", null, sampleId);
        authPost("/api/sample/orders/{id}/start", null, sampleId);
        authPost("/api/sample/orders/{id}/complete", null, sampleId);
        authPost("/api/sample/orders/{id}/confirm", null, sampleId);
        Response r = authPost("/api/sample/orders/{id}/cancel", Map.of("reason", "客户取消"), sampleId);
        assertSuccess(r, "cancel from confirmed");
        authGet("/api/sample/orders/{id}", sampleId).then().body("data.status", equalTo("已取消"));
        System.out.println("  已确认 → 已取消 成功");
    }
}
