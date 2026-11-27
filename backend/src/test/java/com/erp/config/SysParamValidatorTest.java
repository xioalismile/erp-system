package com.erp.config;

import com.erp.config.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：SysParamValidator 集中封装设置中心业务规则校验。
 * 覆盖风险：
 *   1. 来源单号校验（配置开关+空值抛错）
 *   2. 客户/货品/供应商/单据号查重（开关+duplicateExists组合）
 *   3. 仓库必填校验（开关+warehouseId==null）
 *   4. 允许零单价校验（开关关 + 单价为0 → 抛错，附行号信息）
 *   5. KGW审批触发（开关开+净金额≥阈值+草稿态 → 升级为待审核，返回true）
 *   6. 锁单据日期（开关开+已生效+日期变 → 抛错；草稿变日期放行）
 *   7. 超量校验（策略"不允许"抛错；"警告提示"记录日志但放行）
 *   8. adjustTaxIfDisabled：含税关闭 → 税额置0，净额=折后金额
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SysParamValidator 设置中心业务规则校验器测试")
class SysParamValidatorTest {

    @Mock
    private SysConfigService sysConfigService;

    private SysParamValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SysParamValidator(sysConfigService);
    }

    // ==================== 1. 来源单号校验 ====================

    @Test
    @DisplayName("来源单号：开关关时允许为空（通过）")
    void validateRequireSource_disabled_shouldPassEvenBlank() {
        when(sysConfigService.getBooleanValue("sale.order.require.source", false)).thenReturn(false);
        assertDoesNotThrow(() ->
                validator.validateRequireSource("sale.order.require.source", "销售订单", ""));
        assertDoesNotThrow(() ->
                validator.validateRequireSource("sale.order.require.source", "销售订单", null));
    }

    @Test
    @DisplayName("来源单号：开关开+为空 → BusinessException（附单据类型中文名）")
    void validateRequireSource_enabledAndBlank_shouldThrow() {
        when(sysConfigService.getBooleanValue("sale.order.require.source", false)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateRequireSource("sale.order.require.source", "销售订单", ""));
        assertTrue(ex.getMessage().contains("销售订单"),
                "错误信息应包含单据类型, 实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("来源单号"),
                "错误信息应包含校验主题");
    }

    @Test
    @DisplayName("来源单号：开关开+有值 → 通过")
    void validateRequireSource_enabledAndHasValue_shouldPass() {
        when(sysConfigService.getBooleanValue("sale.order.require.source", false)).thenReturn(true);
        assertDoesNotThrow(() ->
                validator.validateRequireSource("sale.order.require.source", "销售订单", "SO-001"));
    }

    // ==================== 2. 客户/货品/供应商查重 ====================

    @ParameterizedTest(name = "{0} 查重：开关开+重复存在 → 抛错")
    @CsvSource({
            "customer, 张三",
            "material, 螺丝M4",
            "supplier, 优质供应商A"
    })
    @DisplayName("名称查重：开关开 + duplicateExists=true → BusinessException")
    void validateNameDuplicate_enabledAndDuplicate_shouldThrow(String type, String name) {
        String configKey = switch (type) {
            case "customer" -> "sys.customer.duplicate.check";
            case "material" -> "sys.material.duplicate.check";
            case "supplier" -> "sys.supplier.duplicate.check";
            default -> throw new IllegalArgumentException();
        };
        when(sysConfigService.getBooleanValue(configKey, true)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            switch (type) {
                case "customer" -> validator.validateCustomerNameDuplicate(name, true);
                case "material" -> validator.validateMaterialNameDuplicate(name, true);
                case "supplier" -> validator.validateSupplierNameDuplicate(name, true);
            }
        });
        assertTrue(ex.getMessage().contains(name), "错误信息应包含名称: " + ex.getMessage());
    }

    @Test
    @DisplayName("客户查重：开关关 + 重复 → 放行（不抛错）")
    void validateCustomerNameDuplicate_disabledEvenIfDuplicate_shouldPass() {
        when(sysConfigService.getBooleanValue("sys.customer.duplicate.check", true)).thenReturn(false);
        assertDoesNotThrow(() ->
                validator.validateCustomerNameDuplicate("任何名称", true));
    }

    @Test
    @DisplayName("客户查重：开关开 + 无重复 → 放行")
    void validateCustomerNameDuplicate_enabledNoDuplicate_shouldPass() {
        when(sysConfigService.getBooleanValue("sys.customer.duplicate.check", true)).thenReturn(true);
        assertDoesNotThrow(() ->
                validator.validateCustomerNameDuplicate("新客户", false));
    }

    // ==================== 3. 仓库必填 ====================

    @Test
    @DisplayName("仓库必填：开关开 + warehouseId=null → 抛错")
    void validateWarehouseRequired_enabledAndNull_shouldThrow() {
        when(sysConfigService.getBooleanValue("kgw_warehouse_required", true)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateWarehouseRequired("销售出库单", null));
        assertTrue(ex.getMessage().contains("销售出库单"));
        assertTrue(ex.getMessage().contains("仓库"));
    }

    @Test
    @DisplayName("仓库必填：开关开 + 有值 / 开关关 + null → 均通过")
    void validateWarehouseRequired_passCases() {
        // 开 + 有仓库
        when(sysConfigService.getBooleanValue("kgw_warehouse_required", true)).thenReturn(true);
        assertDoesNotThrow(() -> validator.validateWarehouseRequired("销售出库单", 1L));
        // 关 + 无仓库
        when(sysConfigService.getBooleanValue("kgw_warehouse_required", true)).thenReturn(false);
        assertDoesNotThrow(() -> validator.validateWarehouseRequired("销售出库单", null));
    }

    // ==================== 4. 单据号查重 ====================

    @Test
    @DisplayName("单据号查重：开关开 + 重复 → 抛错（带单据类型+单号）")
    void validateBillNoDuplicate_enabledAndDuplicate_shouldThrow() {
        when(sysConfigService.getBooleanValue("sys.bill.no.duplicate.check", false)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateBillNoDuplicate("SO-001", "销售订单", true));
        assertTrue(ex.getMessage().contains("SO-001"));
        assertTrue(ex.getMessage().contains("销售订单"));
    }

    // ==================== 5. 允许单价为0 ====================

    @Test
    @DisplayName("零单价校验：开关关（不允许为0）+ 单价0 → 抛错（含行号）")
    void validateAllowZeroPrice_disallowedAndZero_shouldThrowWithLineNo() {
        when(sysConfigService.getBooleanValue("sale.order.allow.zero.price", true)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateAllowZeroPrice("sale.order.allow.zero.price", "销售订单", BigDecimal.ZERO, 3));
        assertTrue(ex.getMessage().contains("第3行"), "应含行号信息: " + ex.getMessage());
    }

    @ParameterizedTest(name = "零单价放行场景: 允许={0}, 单价={1}")
    @CsvSource({
            "true, 0",       // 允许为0
            "false, 0.01",   // 不允许但不为0
            "true, 100"      // 允许且不为0
    })
    @DisplayName("零单价校验：允许开关开 OR 单价非0 → 均通过")
    void validateAllowZeroPrice_passCases(boolean allowZero, String priceStr) {
        when(sysConfigService.getBooleanValue("x.y.z", true)).thenReturn(allowZero);
        BigDecimal price = new BigDecimal(priceStr);
        assertDoesNotThrow(() ->
                validator.validateAllowZeroPrice("x.y.z", "销售订单", price, 1));
    }

    @Test
    @DisplayName("零单价校验：单价 null → 直接通过（不判等 null == 0）")
    void validateAllowZeroPrice_nullPrice_shouldPass() {
        when(sysConfigService.getBooleanValue("x.y.z", true)).thenReturn(false);
        assertDoesNotThrow(() ->
                validator.validateAllowZeroPrice("x.y.z", "测试单", null, 1));
    }

    // ==================== 6. KGW审批校验 ====================

    @Test
    @DisplayName("KGW审批：开关关 → 返回 false，不修改状态")
    void applyKgwApprovalForPurchase_disabled_shouldReturnFalse() {
        when(sysConfigService.getBooleanValue("kgw_approval_enabled", false)).thenReturn(false);

        AtomicReference<String> status = new AtomicReference<>("草稿");
        AtomicReference<String> approvalStatus = new AtomicReference<>(null);
        boolean triggered = validator.applyKgwApprovalForPurchase(
                "采购订单", status::set, approvalStatus::set, status.get(), new BigDecimal("100000"));

        assertFalse(triggered);
        assertEquals("草稿", status.get(), "状态不应被修改");
    }

    @Test
    @DisplayName("KGW审批：开关开 + 金额≥阈值 + 草稿态 → 升级为待审核，返回 true")
    void applyKgwApprovalForPurchase_enabledOverLimitAndDraft_shouldUpgradeToPending() {
        when(sysConfigService.getBooleanValue("kgw_approval_enabled", false)).thenReturn(true);
        when(sysConfigService.getIntValue("kgw_approval_amount_limit", 50000)).thenReturn(50000);

        AtomicReference<String> status = new AtomicReference<>("草稿");
        AtomicReference<String> approvalStatus = new AtomicReference<>(null);
        boolean triggered = validator.applyKgwApprovalForPurchase(
                "采购订单", status::set, approvalStatus::set, status.get(), new BigDecimal("60000"));

        assertTrue(triggered);
        assertEquals("待审核", status.get());
        assertEquals("待审核", approvalStatus.get());
    }

    @Test
    @DisplayName("KGW审批：金额<阈值 / 非草稿 / null金额 → 返回 false")
    void applyKgwApprovalForPurchase_nonTriggerCases_shouldReturnFalse() {
        when(sysConfigService.getBooleanValue("kgw_approval_enabled", false)).thenReturn(true);
        when(sysConfigService.getIntValue("kgw_approval_amount_limit", 50000)).thenReturn(50000);

        // 金额 < 阈值
        AtomicReference<String> s1 = new AtomicReference<>("草稿");
        boolean t1 = validator.applyKgwApprovalForPurchase("采购订单", s1::set, x -> {}, s1.get(), new BigDecimal("49999"));
        assertFalse(t1);
        assertEquals("草稿", s1.get());

        // 非草稿态（即使超金额也不触发升级）
        AtomicReference<String> s2 = new AtomicReference<>("已生效");
        boolean t2 = validator.applyKgwApprovalForPurchase("采购订单", s2::set, x -> {}, s2.get(), new BigDecimal("100000"));
        assertFalse(t2);

        // null 金额
        AtomicReference<String> s3 = new AtomicReference<>("草稿");
        boolean t3 = validator.applyKgwApprovalForPurchase("采购订单", s3::set, x -> {}, s3.get(), null);
        assertFalse(t3);
    }

    // ==================== 7. 锁单据日期 ====================

    @Test
    @DisplayName("锁单据日期：开关开 + 已生效 + 日期改了 → 抛错")
    void validateLockBillDate_enabledEffectiveAndChanged_shouldThrow() {
        when(sysConfigService.getBooleanValue("sys.lock.bill.date", false)).thenReturn(true);
        LocalDate oldD = LocalDate.of(2026, 8, 1);
        LocalDate newD = LocalDate.of(2026, 8, 2);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateLockBillDate("销售订单", oldD, newD, "已生效"));
        assertTrue(ex.getMessage().contains("销售订单"));
        assertTrue(ex.getMessage().contains("单据日期"));
    }

    @ParameterizedTest(name = "锁日期放行场景: 开={0} 状态={1} 日期变={2}")
    @CsvSource({
            "false, 已生效, true",   // 开关关：即使改日期也放行
            "true, 草稿, true",     // 开关开但草稿态：允许改
            "true, 已生效, false",  // 日期未变
    })
    @DisplayName("锁单据日期：开关关/草稿态/日期未变 → 均通过")
    void validateLockBillDate_passCases(boolean enabled, String status, boolean changed) {
        when(sysConfigService.getBooleanValue("sys.lock.bill.date", false)).thenReturn(enabled);
        LocalDate base = LocalDate.of(2026, 8, 1);
        LocalDate another = LocalDate.of(2026, 8, 2);
        assertDoesNotThrow(() ->
                validator.validateLockBillDate("X单",
                        base, changed ? another : base, status));
    }

    // ==================== 8. 超量校验 ====================

    @Test
    @DisplayName("超量校验：策略='不允许' + 超量 → BusinessException（含行号、数量）")
    void validateOverQty_disallowPolicy_shouldThrow() {
        when(sysConfigService.getValue("sale.order.over.qty", "预警提示")).thenReturn("不允许");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateOverQty("sale.order.over.qty", "销售订单", 2,
                        new BigDecimal("120"), new BigDecimal("100")));
        assertTrue(ex.getMessage().contains("第2行"), "含行号: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("120"), "含本单数量");
        assertTrue(ex.getMessage().contains("100"), "含可转数量");
    }

    @Test
    @DisplayName("超量校验：策略='预警提示' + 超量 → 仅日志/可见预警，不抛错（放行）")
    void validateOverQty_warnPolicy_shouldPass() {
        when(sysConfigService.getValue("sale.order.over.qty", "预警提示")).thenReturn("预警提示");
        assertDoesNotThrow(() ->
                validator.validateOverQty("sale.order.over.qty", "销售订单", 1,
                        new BigDecimal("200"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("超量校验：策略='不控制' + 超量 → 直接放行，不抛错")
    void validateOverQty_noControlPolicy_shouldPass() {
        when(sysConfigService.getValue("sale.order.over.qty", "预警提示")).thenReturn("不控制");
        assertDoesNotThrow(() ->
                validator.validateOverQty("sale.order.over.qty", "销售订单", 1,
                        new BigDecimal("200"), new BigDecimal("100")));
    }

    @Test
    @DisplayName("超量策略归一化：历史值 警告提示→预警提示、不检测→不控制")
    void getOverQtyPolicy_legacyValues_shouldNormalize() {
        when(sysConfigService.getValue("sale.order.over.qty", "预警提示")).thenReturn("警告提示");
        assertEquals("预警提示", validator.getOverQtyPolicy("sale.order.over.qty"));
        when(sysConfigService.getValue("sale.order.over.qty", "预警提示")).thenReturn("不检测");
        assertEquals("不控制", validator.getOverQtyPolicy("sale.order.over.qty"));
    }

    @ParameterizedTest(name = "超量校验放行：订={0} ≤ 可转={1}")
    @CsvSource({"100, 100", "50, 100"})
    @DisplayName("超量校验：数量 ≤ 可转 → 不论策略均通过")
    void validateOverQty_withinLimit_shouldAlwaysPass(int ordered, int available) {
        assertDoesNotThrow(() ->
                validator.validateOverQty("x", "单", 1,
                        new BigDecimal(ordered), new BigDecimal(available)));
    }

    @ParameterizedTest(name = "超量校验null安全: orderQty={0}, availableQty={1}")
    @CsvSource({", 100", "100, ", ", "})
    @DisplayName("超量校验：任一数量为null → 直接通过，不抛NPE")
    void validateOverQty_nullSafe_shouldPass(String order, String avail) {
        BigDecimal o = order == null ? null : new BigDecimal(order);
        BigDecimal a = avail == null ? null : new BigDecimal(avail);
        assertDoesNotThrow(() ->
                validator.validateOverQty("x", "单", 1, o, a));
    }

    // ==================== 9. adjustTaxIfDisabled 含税业务关闭调整 ====================

    @Test
    @DisplayName("adjustTaxIfDisabled：含税开关启 → 不修改（原样保留）")
    void adjustTaxIfDisabled_taxEnabled_shouldNotModify() {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(true);

        AtomicReference<BigDecimal> tax = new AtomicReference<>(new BigDecimal("13"));
        AtomicReference<BigDecimal> net = new AtomicReference<>(new BigDecimal("113"));
        BigDecimal discounted = new BigDecimal("100");
        BigDecimal origTax = new BigDecimal("13");

        validator.adjustTaxIfDisabled("销售订单", tax::set, net::set, discounted, origTax);

        assertEquals(0, new BigDecimal("13").compareTo(tax.get()), "启用含税不修改税");
        assertEquals(0, new BigDecimal("113").compareTo(net.get()), "启用含税不修改净额");
    }

    @Test
    @DisplayName("adjustTaxIfDisabled：含税关 → 税额置0，净额=折后金额(2位小数)")
    void adjustTaxIfDisabled_taxDisabled_shouldZeroTaxAndResetNet() {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(false);

        AtomicReference<BigDecimal> tax = new AtomicReference<>(new BigDecimal("13"));
        AtomicReference<BigDecimal> net = new AtomicReference<>(new BigDecimal("113"));
        BigDecimal discounted = new BigDecimal("99.995"); // HALF_UP → 100.00

        validator.adjustTaxIfDisabled("销售订单", tax::set, net::set, discounted, new BigDecimal("13"));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(tax.get()), "税额应为 0.00");
        assertEquals(2, tax.get().scale());
        assertEquals(0, new BigDecimal("100.00").compareTo(net.get()),
                "净额应为折后金额(保留2位 HALF_UP), 实际: " + net.get());
    }

    @Test
    @DisplayName("adjustTaxIfDisabled：含税关 + 折后金额null → 净额置0.00，不抛NPE")
    void adjustTaxIfDisabled_taxDisabledNullDiscounted_shouldSetNetZero() {
        when(sysConfigService.getBooleanValue("sys.tax.enabled", false)).thenReturn(false);

        AtomicReference<BigDecimal> tax = new AtomicReference<>(new BigDecimal("99"));
        AtomicReference<BigDecimal> net = new AtomicReference<>(new BigDecimal("99"));

        validator.adjustTaxIfDisabled("采购订单", tax::set, net::set, null, new BigDecimal("10"));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(tax.get()));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(net.get()));
    }

    // ==================== 开关 getter 覆盖 ====================

    @ParameterizedTest(name = "开关读取: {0} = {1}")
    @CsvSource({
            "sys.tax.enabled, true",
            "sys.tax.enabled, false",
            "sys.lock.bill.date, true"
    })
    @DisplayName("简单开关 getter：正确读取配置")
    void simpleSwitches_readsConfig(String key, boolean value) {
        switch (key) {
            case "sys.tax.enabled" ->
                    when(sysConfigService.getBooleanValue(key, false)).thenReturn(value);
            case "sys.lock.bill.date" ->
                    when(sysConfigService.getBooleanValue(key, false)).thenReturn(value);
        }
        switch (key) {
            case "sys.tax.enabled" -> assertEquals(value, validator.isTaxEnabled());
            case "sys.lock.bill.date" -> assertEquals(value, validator.isLockBillDate());
        }
    }

    @Test
    @DisplayName("isAllowAddMaterial：读取配置，默认 true")
    void isAllowAddMaterial_readsConfig() {
        when(sysConfigService.getBooleanValue("sale.order.allow.add.material", true)).thenReturn(false);
        assertFalse(validator.isAllowAddMaterial("sale.order.allow.add.material"));

        when(sysConfigService.getBooleanValue("x", true)).thenReturn(true);
        assertTrue(validator.isAllowAddMaterial("x"));
    }
}
