package com.erp.finance.credit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("credit_limit")
public class CreditLimit {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("credit_limit")
    private BigDecimal creditLimit;

    @TableField("used_amount")
    private BigDecimal usedAmount;

    @TableField("available_amount")
    private BigDecimal availableAmount;

    @TableField("warning_ratio")
    private BigDecimal warningRatio;

    @TableField("status")
    private Integer status;

    @TableField("effective_date")
    private LocalDate effectiveDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("base_credit_limit")
    private BigDecimal baseCreditLimit;

    @TableField("base_used_amount")
    private BigDecimal baseUsedAmount;

    @TableField("base_available_amount")
    private BigDecimal baseAvailableAmount;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
