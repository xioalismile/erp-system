package com.erp.config;

import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 含税业务工具类（优先级2.6）
 * <p>
 * 从设置中心读取含税配置(sys.tax.enabled, sys.tax.rate, sys.tax.method)，
 * 统一处理税额计算。
 * </p>
 * <ul>
 *   <li>sys.tax.enabled: 是否启用含税业务，默认 false</li>
 *   <li>sys.tax.rate: 默认税率(百分比)，默认 13</li>
 *   <li>sys.tax.method: 计税方式，exclusive=价外税(默认), inclusive=价内税</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxUtil {

    private final SysConfigService sysConfigService;
    /** 金额小数位改由设置中心统一控制（设置中心 → 系统参数 → 小数位数设置 → 金额小数位数） */
    private final PrecisionUtil precisionUtil;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 是否启用含税业务 */
    public boolean isTaxEnabled() {
        return sysConfigService.getBooleanValue("sys.tax.enabled", false);
    }

    /** 获取税率(百分比)，默认 13 */
    public BigDecimal getTaxRate() {
        String rate = sysConfigService.getValue("sys.tax.rate", "13");
        try {
            return new BigDecimal(rate);
        } catch (NumberFormatException e) {
            log.warn("无效的税率配置: {}，使用默认 13", rate);
            return new BigDecimal("13");
        }
    }

    /** 获取计税方式: exclusive=价外税, inclusive=价内税 */
    public String getTaxMethod() {
        return sysConfigService.getValue("sys.tax.method", "exclusive");
    }

    /**
     * 计算税额
     * <p>
     * 当 sys.tax.enabled 为 false 时，税额返回 0。
     * 当 sys.tax.enabled 为 true 时：
     * <ul>
     *   <li>价外税(exclusive): 税额 = 不含税金额 × 税率 / 100</li>
     *   <li>价内税(inclusive): 税额 = 含税金额 - 不含税金额 = 含税金额 × 税率 / (100 + 税率)</li>
     * </ul>
     * </p>
     *
     * @param amount 金额（价外税时为不含税金额，价内税时为含税金额）
     * @return 税额
     */
    public BigDecimal calculateTax(BigDecimal amount) {
        if (!isTaxEnabled()) {
            return BigDecimal.ZERO.setScale(precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
        }
        if (amount == null) {
            return BigDecimal.ZERO.setScale(precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
        }
        BigDecimal rate = getTaxRate();
        String method = getTaxMethod();
        if ("inclusive".equalsIgnoreCase(method)) {
            // 价内税: 税额 = 含税金额 × 税率 / (100 + 税率)
            return amount.multiply(rate)
                    .divide(HUNDRED.add(rate), precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
        }
        // 价外税(默认): 税额 = 不含税金额 × 税率 / 100
        return amount.multiply(rate)
                .divide(HUNDRED, precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
    }

    /**
     * 计算价税合计
     * <p>
     * 价外税: 价税合计 = 不含税金额 + 税额
     * 价内税: 价税合计 = 含税金额(传入的amount)
     * 未启用含税: 价税合计 = amount
     * </p>
     *
     * @param amount 金额
     * @return 价税合计
     */
    public BigDecimal calculateTotalWithTax(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
        }
        if (!isTaxEnabled()) {
            return amount.setScale(precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
        }
        String method = getTaxMethod();
        if ("inclusive".equalsIgnoreCase(method)) {
            // 价内税: amount 即为价税合计
            return amount.setScale(precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
        }
        // 价外税: 价税合计 = amount + 税额
        return amount.add(calculateTax(amount)).setScale(precisionUtil.getAmountScale(), RoundingMode.HALF_UP);
    }
}
