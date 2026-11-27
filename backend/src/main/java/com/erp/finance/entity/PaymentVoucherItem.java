package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付款单明细 - 对齐 PG cash_payment_item 表
 * 承载实际付款行（账户/金额/银行账号/备注），解决付款明细无处落库导致回显丢失的问题
 */
@Data
@TableName("cash_payment_item")
public class PaymentVoucherItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 付款单ID */
    @TableField("payment_id")
    private Long paymentId;

    @TableField("account_id")
    private Long accountId;

    @TableField("account_name")
    private String accountName;

    @TableField("account_no")
    private String accountNo;

    @TableField("bank_account")
    private String bankAccount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("base_amount")
    private BigDecimal baseAmount;

    @TableField("remark")
    private String remark;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
