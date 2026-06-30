package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 币种 - 对齐 PG currency 表
 */
@Data
@TableName("currency")
public class Currency {

    @TableId(value = "currency_id", type = IdType.AUTO)
    private Long currencyId;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("currency_name")
    private String currencyName;

    @TableField("currency_symbol")
    private String currencySymbol;

    @TableField("is_base_currency")
    private Short isBaseCurrency;

    @TableField("exchange_rate")
    private java.math.BigDecimal exchangeRate;

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
