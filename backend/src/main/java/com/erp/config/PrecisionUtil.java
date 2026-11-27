package com.erp.config;

import com.erp.config.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 精度工具类
 * 从设置中心读取精度配置，统一处理数量、单价、金额的精度和舍入方式
 * 配置项：precision.quantity, precision.price, precision.amount, precision.rate, precision.percent, precision.rounding.mode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrecisionUtil {

    private final SysConfigService sysConfigService;

    /**
     * 首次启动自检（守则 2.7.1-3）：把设置中心读到的小数位与舍入方式打印到启动日志，
     * 便于确认"设置到底生效没有"；读取失败一律回落默认值，不影响启动。
     */
    @PostConstruct
    public void logStartupCheck() {
        try {
            log.info("[数值精度] 设置中心读取：数量={}位 单价={}位 金额={}位 汇率={}位 百分比={}位 舍入={}"
                            + "（来源：设置中心 → 系统参数 → 小数位数设置；缺失/非法自动回落默认）",
                    getQuantityScale(), getPriceScale(), getAmountScale(), getRateScale(), getPercentScale(), getRoundingMode());
        } catch (Exception e) {
            log.warn("[数值精度] 启动自检读取失败，将使用默认位数：{}", e.getMessage());
        }
    }

    /**
     * 获取数量小数位数
     */
    public int getQuantityScale() {
        return sysConfigService.getIntValue("precision.quantity", 2);
    }

    /**
     * 获取单价小数位数
     */
    public int getPriceScale() {
        return sysConfigService.getIntValue("precision.price", 4);
    }

    /**
     * 获取金额小数位数
     */
    public int getAmountScale() {
        return sysConfigService.getIntValue("precision.amount", 2);
    }

    /**
     * 获取汇率小数位数
     */
    public int getRateScale() {
        return sysConfigService.getIntValue("precision.rate", 6);
    }

    /**
     * 获取百分比小数位数
     */
    public int getPercentScale() {
        return sysConfigService.getIntValue("precision.percent", 2);
    }

    /**
     * 获取舍入方式
     */
    public RoundingMode getRoundingMode() {
        String mode = sysConfigService.getValue("precision.rounding.mode", "HALF_UP");
        try {
            return RoundingMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            log.warn("无效的舍入方式配置: {}，使用默认 HALF_UP", mode);
            return RoundingMode.HALF_UP;
        }
    }

    /**
     * 对数量进行精度处理
     */
    public BigDecimal scaleQuantity(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(getQuantityScale(), getRoundingMode());
    }

    /**
     * 对单价进行精度处理
     */
    public BigDecimal scalePrice(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(getPriceScale(), getRoundingMode());
    }

    /**
     * 对金额进行精度处理
     */
    public BigDecimal scaleAmount(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(getAmountScale(), getRoundingMode());
    }

    /**
     * 对汇率进行精度处理
     */
    public BigDecimal scaleRate(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(getRateScale(), getRoundingMode());
    }
}
