package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 价格等级 - 对齐 PG price_level 表
 */
@Data
@TableName("price_level")
public class PriceLevel {

    @TableId(value = "price_level_id", type = IdType.AUTO)
    private Long priceLevelId;

    @TableField("price_level_code")
    private String priceLevelCode;

    @TableField("price_level_name")
    private String priceLevelName;

    @TableField("price_adjust_rate")
    private java.math.BigDecimal priceAdjustRate;

    @TableField("sort_order")
    private Integer sortOrder;

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
