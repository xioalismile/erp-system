package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目余额表 - 对齐 PG account_balance 表
 */
@Data
@TableName("account_balance")
public class AccountBalance {

    @TableId(value = "balance_id", type = IdType.AUTO)
    private Long balanceId;

    @TableField("period_year")
    private Integer periodYear;

    @TableField("period_month")
    private Integer periodMonth;

    @TableField("account_id")
    private Long accountId;

    @TableField("account_code")
    private String accountCode;

    @TableField("account_name")
    private String accountName;

    @TableField("account_direction")
    private String accountDirection;

    @TableField("begin_debit_balance")
    private BigDecimal beginDebitBalance;

    @TableField("begin_credit_balance")
    private BigDecimal beginCreditBalance;

    @TableField("current_debit")
    private BigDecimal currentDebit;

    @TableField("current_credit")
    private BigDecimal currentCredit;

    @TableField("end_debit_balance")
    private BigDecimal endDebitBalance;

    @TableField("end_credit_balance")
    private BigDecimal endCreditBalance;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
