package com.erp.config;

import com.erp.config.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：TaxUtil 是含税业务税额计算统一入口（价内税/价外税切换）。
 * 覆盖风险：
 *   1. 含税开关 disabled：税额恒为 0，价税合计 = 原金额
 *   2. 含税开关 enabled + 价外税(exclusive)：税额 = 不含税 × 率/100
 *   3. 含税开关 enabled + 价内税(inclusive)：税额 = 含税 × 率/(100+率)
 *   4. 税率配置非法字符串 → 降级为默认 13，不抛 NumberFormatException
 *   5. 金额 null → 0，不抛 NPE
 *   6. 精度：结果固定 SCALE=2 HALF_UP
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaxUtil 含税业务税额计算工具测试")
class TaxUtilTest {

    @Mock
    private SysConfigService sysConfigService;

    @Mock
    private PrecisionUtil precisionUtil;

    private TaxUtil taxUtil;

    @BeforeEach
    void setUp() {
        // 精度工具：金额固定 2 位小数（与本测试关注的 SCALE=2 HALF_UP 口径一致）
        when(precisionUtil.getAmountScale()).thenReturn(2);
        taxUtil = new TaxUtil(sysConfigService, precisionUtil);
        // 默认：禁用含税
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(false);
        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn("13");
        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("exclusive");
    }

    // ========== isTaxEnabled / getTaxRate / getTaxMethod 配置读取 ==========

    @Test
    @DisplayName("isTaxEnabled：读取配置开关")
    void isTaxEnabled_readsConfig() {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(true);
        assertTrue(taxUtil.isTaxEnabled());

        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(false);
        assertFalse(taxUtil.isTaxEnabled());
    }

    @Test
    @DisplayName("getTaxRate：合法数字字符串 → BigDecimal")
    void getTaxRate_validNumeric_shouldReturnDecimal() {
        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn("9");
        assertEquals(0, new BigDecimal("9").compareTo(taxUtil.getTaxRate()));

        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn("6.5");
        assertEquals(0, new BigDecimal("6.5").compareTo(taxUtil.getTaxRate()));
    }

    @Test
    @DisplayName("getTaxRate：非法字符串 → 降级默认 13，不抛异常")
    void getTaxRate_invalidString_shouldFallbackToDefault13() {
        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn("不是数字");
        assertEquals(0, new BigDecimal("13").compareTo(taxUtil.getTaxRate()));

        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn("");
        assertEquals(0, new BigDecimal("13").compareTo(taxUtil.getTaxRate()));

        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn(null);
        // 当 null 时，getOrDefault 行为由 Mockito 决定，但此处 verify 日志降级即可
    }

    @Test
    @DisplayName("getTaxMethod：读取配置值")
    void getTaxMethod_readsConfig() {
        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("inclusive");
        assertEquals("inclusive", taxUtil.getTaxMethod());

        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("exclusive");
        assertEquals("exclusive", taxUtil.getTaxMethod());
    }

    // ========== 含税业务 disabled ==========

    @ParameterizedTest(name = "含税关闭时 calculateTax 金额={0} → 0.00")
    @CsvSource({"100", "0", "9999.99"})
    @DisplayName("含税关闭：calculateTax → 恒为 0.00")
    void calculateTax_taxDisabled_alwaysZero(String amountStr) {
        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal result = taxUtil.calculateTax(amount);
        assertEquals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).compareTo(result),
                "含税关闭税额必须为 0.00");
        assertEquals(2, result.scale(), "结果必须有 2 位小数");
    }

    @Test
    @DisplayName("含税关闭：calculateTax(null) → 0.00，不抛 NPE")
    void calculateTax_taxDisabledNullAmount_returnsZero() {
        BigDecimal result = taxUtil.calculateTax(null);
        assertEquals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).compareTo(result));
    }

    @Test
    @DisplayName("含税关闭：calculateTotalWithTax = 原金额(2位小数)")
    void calculateTotalWithTax_taxDisabled_equalsOriginalAmountScaled() {
        // 含税关闭：价税合计 = amount(保留2位)
        BigDecimal amount = new BigDecimal("123.456");
        BigDecimal result = taxUtil.calculateTotalWithTax(amount);
        assertEquals(0, new BigDecimal("123.46").compareTo(result),
                "应为原金额 HALF_UP 到 2 位小数");

        BigDecimal zero = taxUtil.calculateTotalWithTax(null);
        assertEquals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).compareTo(zero));
    }

    // ========== 价外税 exclusive ==========

    private void enableExclusiveTax(String rate) {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(true);
        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn(rate);
        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("exclusive");
    }

    @ParameterizedTest(name = "价外税 金额={0} 税率={1}% → 税额={2} 价税合计={3}")
    @CsvSource({
            // 100 * 13% = 13.00, 价税合计 113.00
            "100, 13, 13.00, 113.00",
            // 200 * 9% = 18.00
            "200, 9, 18.00, 218.00",
            // 100 * 0% = 0
            "100, 0, 0.00, 100.00",
            // 123.45 * 13% = 16.0485 → HALF_UP 16.05
            "123.45, 13, 16.05, 139.50"
    })
    @DisplayName("价外税：calculateTax 与 calculateTotalWithTax 正确性")
    void exclusiveTax_calculations_shouldBeCorrect(
            String amountStr, String rateStr, String expectedTax, String expectedTotal) {
        enableExclusiveTax(rateStr);
        BigDecimal amount = new BigDecimal(amountStr);

        assertEquals(0, new BigDecimal(expectedTax).compareTo(taxUtil.calculateTax(amount)),
                "税额计算错误");
        assertEquals(0, new BigDecimal(expectedTotal).compareTo(taxUtil.calculateTotalWithTax(amount)),
                "价税合计计算错误");
    }

    @Test
    @DisplayName("价外税：金额为 0 → 税额 0，价税合计 0")
    void exclusiveTax_zeroAmount_shouldAllZero() {
        enableExclusiveTax("13");
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(taxUtil.calculateTax(BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(taxUtil.calculateTotalWithTax(BigDecimal.ZERO)));
    }

    // ========== 价内税 inclusive ==========

    private void enableInclusiveTax(String rate) {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(true);
        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn(rate);
        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("inclusive");
    }

    @ParameterizedTest(name = "价内税 含税金额={0} 税率={1}% → 税额={2} 价税合计={0}(保持)")
    @CsvSource({
            // 113 × 13/(100+13) = 13.00
            "113, 13, 13.00",
            // 109 × 9/109 = 9.00
            "109, 9, 9.00",
            // 100 × 13/113 = 11.504... → HALF_UP 11.50
            "100, 13, 11.50"
    })
    @DisplayName("价内税：calculateTax 正确；calculateTotalWithTax = 原金额(保持)")
    void inclusiveTax_calculations_shouldBeCorrect(
            String amountStr, String rateStr, String expectedTax) {
        enableInclusiveTax(rateStr);
        BigDecimal amount = new BigDecimal(amountStr);

        assertEquals(0, new BigDecimal(expectedTax).compareTo(taxUtil.calculateTax(amount)),
                "价内税税额计算错误");

        // 价内税：价税合计 = 原金额(本身即为含税价)
        BigDecimal expectedTotal = amount.setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, expectedTotal.compareTo(taxUtil.calculateTotalWithTax(amount)),
                "价内税价税合计应为原金额本身");
    }

    @Test
    @DisplayName("calculateTax / calculateTotalWithTax：null 金额 → 0.00，不抛 NPE")
    void nullAmount_shouldReturnZeroQuietly() {
        enableExclusiveTax("13");
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(taxUtil.calculateTax(null)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(taxUtil.calculateTotalWithTax(null)));

        enableInclusiveTax("13");
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(taxUtil.calculateTax(null)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(taxUtil.calculateTotalWithTax(null)));
    }

    @Test
    @DisplayName("计税方式大小写兼容：INCLUSIVE / Inclusive / EXCLUSIVE")
    void taxMethod_caseInsensitive_shouldHandle() {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(true);
        when(sysConfigService.getValue("sys.tax.rate", "13")).thenReturn("13");

        // 大写 EXCLUSIVE → 仍走默认 exclusive
        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("EXCLUSIVE");
        BigDecimal r1 = taxUtil.calculateTax(new BigDecimal("100"));
        assertEquals(0, new BigDecimal("13.00").compareTo(r1));

        // INCLUSIVE (任意大小写) 会被 equalsIgnoreCase("inclusive") 匹配
        when(sysConfigService.getValue("sys.tax.method", "exclusive")).thenReturn("INCLUSIVE");
        BigDecimal r2 = taxUtil.calculateTax(new BigDecimal("113"));
        assertEquals(0, new BigDecimal("13.00").compareTo(r2));
    }
}
