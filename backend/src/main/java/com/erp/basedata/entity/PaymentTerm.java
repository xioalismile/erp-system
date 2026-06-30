package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 付款条件 - 对齐 PG payment_term 表
 */
@Data
@TableName("payment_term")
public class PaymentTerm {

    @TableId(value = "payment_term_id", type = IdType.AUTO)
    private Long paymentTermId;

    @TableField("payment_term_code")
    private String paymentTermCode;

    @TableField("payment_term_name")
    private String paymentTermName;

    @TableField("payment_days")
    private Integer paymentDays;

    @TableField("discount_days")
    private Integer discountDays;

    @TableField("discount_rate")
    private java.math.BigDecimal discountRate;

    @TableField("advance_rate")
    private java.math.BigDecimal advanceRate;

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
