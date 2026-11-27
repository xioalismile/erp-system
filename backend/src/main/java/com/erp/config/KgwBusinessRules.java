package com.erp.config;

import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * KGW 设置中心业务规则统一入口
 * <p>
 * 将系统参数(sys_config)中的所有 kgw_* 配置转换为业务校验方法
 * 用于销售订单、销售出库、采购订单等单据保存时的强制校验
 * </p>
 */
@Component
@RequiredArgsConstructor
public class KgwBusinessRules {

    private final SysConfigService sysConfigService;

    public boolean isCustomerRequired() {
        return sysConfigService.getBooleanValue("kgw_customer_required", true);
    }

    public boolean isMaterialRequired() {
        return sysConfigService.getBooleanValue("kgw_material_required", true);
    }

    public boolean isWarehouseRequired() {
        return sysConfigService.getBooleanValue("kgw_warehouse_required", true);
    }

    public boolean isSerialNoEnabled() {
        return sysConfigService.getBooleanValue("kgw_serial_no_enabled", false);
    }

    public boolean isInventoryWarningEnabled() {
        return sysConfigService.getBooleanValue("kgw_inventory_warning", false);
    }

    /**
     * 销售价格策略是否启用
     * <p>
     * 修复（2026-09-20）：原读取 {@code kgw_sale_price_strategy} —— 该 Key 在 sys_config 中**根本不存在**
     * （设置中心「销售参数」里用户看到并操作的是「价格策略启用」= {@code sale.price.strategy.enabled}），
     * 因此无论如何设置该开关，这里恒为 false，价格策略永远不生效。
     * 现改为读设置中心真正的那一项，默认值与配置项自身默认值（true）一致。
     * </p>
     */
    public boolean isSalePriceStrategyEnabled() {
        return sysConfigService.getBooleanValue("sale.price.strategy.enabled", true);
    }

    /**
     * 采购价格策略是否启用
     * <p>
     * 修复（2026-09-20）：原读取 {@code kgw_purchase_price_strategy}（同样不存在），
     * 现改为读设置中心「采购参数」→「价格策略启用」= {@code purchase.price.strategy.enabled}（见 db/103 脚本初始化）。
     * </p>
     */
    public boolean isPurchasePriceStrategyEnabled() {
        return sysConfigService.getBooleanValue("purchase.price.strategy.enabled", true);
    }

    public boolean isApprovalEnabled() {
        return sysConfigService.getBooleanValue("kgw_approval_enabled", false);
    }

    public boolean isOperateLogEnabled() {
        return sysConfigService.getBooleanValue("kgw_operate_log_enabled", true);
    }

    public boolean isPrintLogEnabled() {
        return sysConfigService.getBooleanValue("kgw_print_log_enabled", false);
    }

    /** 审批触发金额阈值，订单金额 >= 该值时进入待审核状态 */
    public BigDecimal getApprovalAmountLimit() {
        int limit = sysConfigService.getIntValue("kgw_approval_amount_limit", 50000);
        return new BigDecimal(limit);
    }
}
