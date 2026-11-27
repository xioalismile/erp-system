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
 * 缺口覆盖：PrecisionUtil 统一处理数量/单价/金额/汇率/百分比的精度与舍入。
 * 覆盖风险：
 *   1. 各精度 scale getter 能正确读取配置，带默认值兜底
 *   2. getRoundingMode：合法配置名返回对应枚举；非法字符串 → HALF_UP
 *   3. scaleQuantity / scalePrice / scaleAmount / scaleRate：
 *      - null 输入 → BigDecimal.ZERO（不抛 NPE）
 *      - 按对应 scale 和舍入模式处理
 *   4. 统一舍入模式切换（HALF_UP vs DOWN）能影响结果
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PrecisionUtil 精度与舍入工具测试")
class PrecisionUtilTest {

    @Mock
    private SysConfigService sysConfigService;

    private PrecisionUtil precisionUtil;

    @BeforeEach
    void setUp() {
        precisionUtil = new PrecisionUtil(sysConfigService);
        // 默认配置
        when(sysConfigService.getIntValue("precision.quantity", 2)).thenReturn(2);
        when(sysConfigService.getIntValue("precision.price", 4)).thenReturn(4);
        when(sysConfigService.getIntValue("precision.amount", 2)).thenReturn(2);
        when(sysConfigService.getIntValue("precision.rate", 6)).thenReturn(4);
        when(sysConfigService.getIntValue("precision.percent", 2)).thenReturn(2);
        when(sysConfigService.getValue("precision.rounding.mode", "HALF_UP")).thenReturn("HALF_UP");
    }

    // ========== 精度 getter ==========

    @ParameterizedTest(name = "精度配置读取: key={0} 默认={1} 配置={2} → 返回={2}")
    @CsvSource({
            "precision.quantity, 2, 3, 3",
            "precision.price, 4, 6, 6",
            "precision.amount, 2, 4, 4",
            // 汇率默认值是 6（PrecisionUtil.getRateScale 的兜底值），这里必须与生产代码一致，
            // 否则 Mockito 按“默认值 4”打桩匹配不到实际调用（默认 6）→ 返回 0 而误报失败（2026-11-11 修复）。
            "precision.rate, 6, 6, 6",
            "precision.percent, 2, 3, 3"
    })
    @DisplayName("各精度 getter：读取配置值，不使用默认")
    void scaleGetters_readsConfigValue(String key, int defVal, int configured, int expected) {
        // 覆盖对应配置
        lenient().when(sysConfigService.getIntValue(key, defVal)).thenReturn(configured);

        switch (key) {
            case "precision.quantity" -> assertEquals(expected, precisionUtil.getQuantityScale());
            case "precision.price" -> assertEquals(expected, precisionUtil.getPriceScale());
            case "precision.amount" -> assertEquals(expected, precisionUtil.getAmountScale());
            case "precision.rate" -> assertEquals(expected, precisionUtil.getRateScale());
            case "precision.percent" -> assertEquals(expected, precisionUtil.getPercentScale());
        }
    }

    // ========== getRoundingMode 舍入模式 ==========

    @ParameterizedTest(name = "合法舍入模式名: {0} → {1}")
    @CsvSource({
            "HALF_UP, HALF_UP",
            "DOWN, DOWN",
            "UP, UP",
            "HALF_DOWN, HALF_DOWN",
            "HALF_EVEN, HALF_EVEN",
            "FLOOR, FLOOR",
            "CEILING, CEILING"
    })
    @DisplayName("getRoundingMode：合法模式名 → 正确枚举")
    void getRoundingMode_validName_shouldReturnEnum(String modeStr, RoundingMode expected) {
        when(sysConfigService.getValue("precision.rounding.mode", "HALF_UP")).thenReturn(modeStr);
        assertEquals(expected, precisionUtil.getRoundingMode());
    }

    @ParameterizedTest(name = "非法舍入模式名降级: {0} → HALF_UP")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "INVALID", "半", "ROUND_HALF_UP"})
    @DisplayName("getRoundingMode：非法字符串 → 默认 HALF_UP，不抛异常")
    void getRoundingMode_invalidName_shouldFallbackToHalfUp(String invalidName) {
        when(sysConfigService.getValue("precision.rounding.mode", "HALF_UP")).thenReturn(invalidName);
        assertEquals(RoundingMode.HALF_UP, precisionUtil.getRoundingMode());
    }

    // ========== scale 方法：默认 HALF_UP + 精度 ==========

    @Test
    @DisplayName("scaleQuantity：默认 scale=2 HALF_UP")
    void scaleQuantity_defaultSettings_shouldScale2HalfUp() {
        // 1.005 → 1.01 (HALF_UP)
        assertEquals(0, new BigDecimal("1.01").compareTo(
                precisionUtil.scaleQuantity(new BigDecimal("1.005"))));
        // 1.0049 → 1.00
        assertEquals(0, new BigDecimal("1.00").compareTo(
                precisionUtil.scaleQuantity(new BigDecimal("1.0049"))));
    }

    @Test
    @DisplayName("scalePrice：默认 scale=4 HALF_UP")
    void scalePrice_defaultSettings_shouldScale4HalfUp() {
        // 1.00005 → 1.0001
        assertEquals(0, new BigDecimal("1.0001").compareTo(
                precisionUtil.scalePrice(new BigDecimal("1.00005"))));
    }

    @Test
    @DisplayName("scaleAmount：默认 scale=2 HALF_UP")
    void scaleAmount_defaultSettings_shouldScale2HalfUp() {
        assertEquals(0, new BigDecimal("99.99").compareTo(
                precisionUtil.scaleAmount(new BigDecimal("99.994"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                precisionUtil.scaleAmount(new BigDecimal("99.995"))));
    }

    @Test
    @DisplayName("scaleRate：默认 scale=4 HALF_UP")
    void scaleRate_defaultSettings_shouldScale4HalfUp() {
        assertEquals(0, new BigDecimal("7.1235").compareTo(
                precisionUtil.scaleRate(new BigDecimal("7.12345"))));
    }

    // ========== scale 方法：null → 0，不抛 NPE ==========

    @Test
    @DisplayName("所有 scaleXxx(null) → BigDecimal.ZERO")
    void allScaleMethods_nullInput_shouldReturnZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(precisionUtil.scaleQuantity(null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(precisionUtil.scalePrice(null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(precisionUtil.scaleAmount(null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(precisionUtil.scaleRate(null)));
    }

    // ========== 舍入模式切换影响结果 ==========

    @Test
    @DisplayName("切换舍入模式为 DOWN：金额 99.999 → 99.99（而非 HALF_UP 的 100.00）")
    void scaleAmount_roundingDown_shouldTruncateNotRoundUp() {
        when(sysConfigService.getValue("precision.rounding.mode", "HALF_UP")).thenReturn("DOWN");

        BigDecimal result = precisionUtil.scaleAmount(new BigDecimal("99.999"));
        assertEquals(0, new BigDecimal("99.99").compareTo(result),
                "DOWN 模式应截去尾部, 实际: " + result);
    }

    @Test
    @DisplayName("自定义精度：数量 scale=3，99.9995 → 100.000")
    void scaleQuantity_customScale3_shouldUseIt() {
        when(sysConfigService.getIntValue("precision.quantity", 2)).thenReturn(3);

        BigDecimal result = precisionUtil.scaleQuantity(new BigDecimal("99.9995"));
        assertEquals(3, result.scale(), "scale 应为 3, 实际: " + result.scale());
        assertEquals(0, new BigDecimal("100.000").compareTo(result));
    }
}
