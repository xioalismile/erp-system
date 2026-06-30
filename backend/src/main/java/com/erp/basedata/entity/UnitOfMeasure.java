package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计量单位 - 对齐 PG unit_of_measure 表
 */
@Data
@TableName("unit_of_measure")
public class UnitOfMeasure {

    @TableId(value = "unit_id", type = IdType.AUTO)
    private Long unitId;

    @TableField("unit_code")
    private String unitCode;

    @TableField("unit_name")
    private String unitName;

    @TableField("unit_group")
    private String unitGroup;

    @TableField("is_base_unit")
    private Short isBaseUnit;

    @TableField("conversion_rate")
    private java.math.BigDecimal conversionRate;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
