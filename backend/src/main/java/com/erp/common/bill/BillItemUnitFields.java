package com.erp.common.bill;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单据明细「多单位换算」的隐藏折算字段（2026-11-09，2026-11-10 修订）。
 *
 * <p>换算对用户不可见，单据上只展示业务列（单位 / 数量 / 副单位 / 浮动单位等）。
 * <ul>
 *   <li>{@code conversionRate}：本行「所选单位 → 基本单位」的换算率快照（仅追溯展示用，不参与浮动折算）；</li>
 *   <li>{@code baseQuantity}：折算后的基本单位数量，<b>库存与成本只认它</b>；
 *       落库前由单位换算服务统一算好——有浮动单位与浮动数量时按浮动数量折算，否则按「数量 × 换算率」。</li>
 * </ul>
 *
 * <p>【2026-11-10 浮动单位】新增两列：
 * <ul>
 *   <li>{@code floatUnitId}：本行浮动数量的计量单位；</li>
 *   <li>{@code floatQuantity}：本行浮动数量（全局开关「启用浮动单位」开启且换算方式=浮动时，单据行允许手填）。</li>
 * </ul>
 * 库存记账统一读 {@link #effectiveBaseQuantity()}，返回值就是落库时算好的 {@code baseQuantity}。</p>
 */
@Getter
@Setter
public class BillItemUnitFields {

    /** 换算率快照：1 个所选单位 = 多少个基本单位 */
    @TableField("conversion_rate")
    private BigDecimal conversionRate;

    /** 基本计量单位数量：库存记账与成本计算的唯一口径 */
    @TableField("base_quantity")
    private BigDecimal baseQuantity;

    /** 副单位：本行副单位数量以该单位计（2026-11-10 支持单据行手选，空 = 按档案第一个固定换算行） */
    @TableField("sub_unit_id")
    private Long subUnitId;

    /** 浮动单位：本行浮动数量以该单位计（仅换算方式=浮动且全局开关开启时使用） */
    @TableField("float_unit_id")
    private Long floatUnitId;

    /** 浮动数量（按浮动单位计）：如实际过磅重量 */
    @TableField("float_quantity")
    private BigDecimal floatQuantity;

    /**
     * 浮动换算率（2026-11-10，标准浮动换算口径）：1 个浮动单位 = 多少个基本单位。
     * 默认取物料档案换算行的率，单据行可改（钢材每根长度不同 → 本次每根折吨数不同）。
     */
    @TableField("float_conversion_rate")
    private BigDecimal floatConversionRate;

    /**
     * 库存记账取数入口（2026-11-10 修订）。
     *
     * <p>统一返回落库前算好的 {@code baseQuantity}：浮动单位与浮动数量都在保存时由
     * 单位换算服务折算进这个字段，库存记账不再二次折算（避免"快照率被覆盖后又算一遍"的口径漂移）。
     * 未开启多单位 / 未填浮动的行，baseQuantity = 数量 × 换算率，与旧版完全一致。</p>
     */
    public BigDecimal effectiveBaseQuantity() {
        return baseQuantity;
    }
}
