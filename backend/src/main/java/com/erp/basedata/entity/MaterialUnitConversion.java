package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料辅计量换算关系（2026-11-10 多单位换算重构第一阶段）。
 *
 * <p>一个物料可配置多行换算明细（1 对多），每行：
 * 辅计量单位 + 换算方式（固定/浮动）+ 换算率。
 * 换算率语义：1 个基本单位 = 多少个该辅单位（如 1盒=10件 → 换算率=10）。</p>
 *
 * <p>对应脚本：{@code deploy/sql/191_material_unit_conversion.sql}；
 * 启动自检：{@code SchemaUpgradeEnsurer#ensureMultiUnitSupportOn}。</p>
 */
@Data
@TableName("material_unit_conversion")
public class MaterialUnitConversion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 物料ID（material.material_id） */
    @TableField("material_id")
    private Long materialId;

    /** 辅计量单位ID（unit_of_measure.unit_id） */
    @TableField("unit_id")
    private Long unitId;

    /** 换算方式：固定 / 浮动 */
    @TableField("rate_mode")
    private String rateMode;

    /** 换算率：1 个基本单位 = 多少个该辅单位 */
    @TableField("conversion_rate")
    private BigDecimal conversionRate;

    /** 排序号（物料档案页签内明细行顺序） */
    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
