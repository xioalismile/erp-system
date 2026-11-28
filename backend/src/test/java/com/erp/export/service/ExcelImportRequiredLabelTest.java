package com.erp.export.service;

import com.erp.export.registry.BillExcelRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入「单号必填」口径回归测试（纯 JUnit，不依赖 Spring 与数据库）。
 *
 * <p><b>存在意义（2026-11-27 实锤缺陷）</b>：打样单 / 免费样品单补导入导出时，给单号列加了
 * {@code @ExportField(label="打样单号"/"订单号", required=true)}，却没有把它们登记进
 * {@code AUTO_CODE_DOC_TYPE}。后果有两个：①模板把单号标成必填，用户按正常用法把单号留空
 * （保存时服务本来会自动取号 SAM-/FS-）→ 导入逐行报"单号不能为空"，整单导不进来；
 * ②用户按提示手填单号 → 导入把单号原样落库，绕过自动编号，而 sample_no 有唯一索引，
 * 导出后再导入同一份文件必然唯一约束冲突。</p>
 *
 * <p>本测试锁住"单号列被自动编码豁免"这个口径：只要有人再新增一张单据时漏登记，
 * 或删掉这里的登记，这里就是构建期红灯，而不是等用户现场导不进来。</p>
 */
@DisplayName("导入：单号列按自动编码豁免必填")
class ExcelImportRequiredLabelTest {

    @Test
    @DisplayName("打样单 / 免费样品单的单号列必须登记为自动编码（导入不要求手填）")
    void sampleBillsAreRegisteredAsAutoCode() throws Exception {
        Field mapField = ExcelServiceImpl.class.getDeclaredField("AUTO_CODE_DOC_TYPE");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> autoCode = (Map<String, String>) mapField.get(null);

        assertEquals("SAMPLE_ORDER", autoCode.get("sampleOrder"),
                "打样单未登记自动编码：导入会把「打样单号」当必填，单号留空整单失败");
        assertEquals("FREE_SAMPLE_ORDER", autoCode.get("freeSampleOrder"),
                "免费样品单未登记自动编码：导入会把「订单号」当必填，单号留空整单失败");

        // 两张单必须确实是可导入的，否则上面的登记没有意义
        BillExcelRegistry.Bill sample = BillExcelRegistry.get("sampleOrder");
        BillExcelRegistry.Bill freeSample = BillExcelRegistry.get("freeSampleOrder");
        assertTrue(sample != null && sample.importable(), "打样单未登记为可导入");
        assertTrue(freeSample != null && freeSample.importable(), "免费样品单未登记为可导入");
    }

    @Test
    @DisplayName("自动编码开启时单号列不进必填集合；关闭时仍要求手填")
    void numberLabelIsExemptOnlyWhenAutoCodeOn() {
        // 开启（默认）：单号由服务自动取号 → 导入不拦
        assertFalse(ExcelServiceImpl.entityRequiredLabels(
                        com.erp.sample.entity.SampleOrder.class, true).contains("打样单号"),
                "自动编码开启时「打样单号」不应是必填");
        assertFalse(ExcelServiceImpl.entityRequiredLabels(
                        com.erp.freesample.entity.FreeSampleOrder.class, true).contains("订单号"),
                "自动编码开启时「订单号」不应是必填");

        // 关闭（用户在设置中心显式关掉自动编号）：必须要求手填，不能放宽成空白单号
        assertTrue(ExcelServiceImpl.entityRequiredLabels(
                        com.erp.sample.entity.SampleOrder.class, false).contains("打样单号"),
                "自动编码关闭时「打样单号」必须仍是必填");
        assertTrue(ExcelServiceImpl.entityRequiredLabels(
                        com.erp.freesample.entity.FreeSampleOrder.class, false).contains("订单号"),
                "自动编码关闭时「订单号」必须仍是必填");
    }
}
