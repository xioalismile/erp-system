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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：KgwBusinessRules 是库管王设置中心业务规则的统一入口。
 * 覆盖风险：
 *   1. 各 boolean 开关正确读取 sys_config（kgw_customer_required / kgw_material_required /
 *      kgw_warehouse_required / kgw_serial_no_enabled /
 *      kgw_inventory_warning / kgw_sale_price_strategy / kgw_purchase_price_strategy /
 *      kgw_approval_enabled / kgw_operate_log_enabled / kgw_print_log_enabled）
 *   2. getApprovalAmountLimit：int 配置 → BigDecimal，默认 50000
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KgwBusinessRules 库管王设置中心规则入口测试")
class KgwBusinessRulesTest {

    @Mock
    private SysConfigService sysConfigService;

    private KgwBusinessRules rules;

    @BeforeEach
    void setUp() {
        rules = new KgwBusinessRules(sysConfigService);
    }

    @ParameterizedTest(name = "boolean 开关: configKey={0} 方法={1} 值={2}")
    @CsvSource({
            "kgw_customer_required, customerRequired, true",
            "kgw_customer_required, customerRequired, false",
            "kgw_material_required, materialRequired, true",
            "kgw_material_required, materialRequired, false",
            "kgw_warehouse_required, warehouseRequired, true",
            "kgw_warehouse_required, warehouseRequired, false",
            "kgw_serial_no_enabled, serialNoEnabled, true",
            "kgw_inventory_warning, inventoryWarningEnabled, true",
            "sale.price.strategy.enabled, salePriceStrategyEnabled, true",
            "purchase.price.strategy.enabled, purchasePriceStrategyEnabled, true",
            "kgw_approval_enabled, approvalEnabled, true",
            "kgw_approval_enabled, approvalEnabled, false",
            "kgw_operate_log_enabled, operateLogEnabled, true",
            "kgw_operate_log_enabled, operateLogEnabled, false",
            "kgw_print_log_enabled, printLogEnabled, true"
    })
    @DisplayName("各 boolean 开关：正确映射配置值到对应 getter")
    void booleanSwitches_shouldReadConfig(String configKey, String methodName, boolean value) {
        boolean defaultVal = switch (configKey) {
            case "kgw_customer_required", "kgw_material_required",
                 "kgw_warehouse_required", "kgw_operate_log_enabled",
                 "sale.price.strategy.enabled", "purchase.price.strategy.enabled" -> true;
            default -> false;
        };
        when(sysConfigService.getBooleanValue(configKey, defaultVal)).thenReturn(value);

        boolean actual = switch (methodName) {
            case "customerRequired" -> rules.isCustomerRequired();
            case "materialRequired" -> rules.isMaterialRequired();
            case "warehouseRequired" -> rules.isWarehouseRequired();
            case "serialNoEnabled" -> rules.isSerialNoEnabled();
            case "inventoryWarningEnabled" -> rules.isInventoryWarningEnabled();
            case "salePriceStrategyEnabled" -> rules.isSalePriceStrategyEnabled();
            case "purchasePriceStrategyEnabled" -> rules.isPurchasePriceStrategyEnabled();
            case "approvalEnabled" -> rules.isApprovalEnabled();
            case "operateLogEnabled" -> rules.isOperateLogEnabled();
            case "printLogEnabled" -> rules.isPrintLogEnabled();
            default -> throw new IllegalArgumentException("未知方法: " + methodName);
        };
        assertEquals(value, actual, "方法 " + methodName + " 应返回 " + value);
    }

    @Test
    @DisplayName("getApprovalAmountLimit：默认 50000 → BigDecimal")
    void getApprovalAmountLimit_default_50000() {
        when(sysConfigService.getIntValue("kgw_approval_amount_limit", 50000)).thenReturn(50000);
        assertEquals(0, new BigDecimal("50000").compareTo(rules.getApprovalAmountLimit()));
    }

    @ParameterizedTest(name = "审批金额阈值配置: limit={0} → BigDecimal({0})")
    @ValueSource(ints = {1000, 30000, 50000, 100000, 0})
    @DisplayName("getApprovalAmountLimit：各配置值 → 正确的 BigDecimal")
    void getApprovalAmountLimit_variousConfigValues(int limit) {
        when(sysConfigService.getIntValue("kgw_approval_amount_limit", 50000)).thenReturn(limit);
        assertEquals(0, new BigDecimal(limit).compareTo(rules.getApprovalAmountLimit()));
    }
}
