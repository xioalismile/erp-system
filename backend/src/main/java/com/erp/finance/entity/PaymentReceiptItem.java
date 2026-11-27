package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单明细 - 对齐 PG cash_receipt_item 表
 * 承载实际收款行（账户/金额/银行账号/备注），解决收款明细无处落库导致回显丢失的问题
 */
@Data
@TableName("cash_receipt_item")
public class PaymentReceiptItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 收款单ID */
    @TableField("receipt_id")
    private Long receiptId;

    @TableField("account_id")
    private Long accountId;

    @TableField("account_name")
    private String accountName;

    @TableField("account_no")
    private String accountNo;

    @TableField("bank_account")
    private String bankAccount;

    /** 金额（原币） */
    @TableField("amount")
    private BigDecimal amount;

    /** 本位币金额 */
    @TableField("base_amount")
    private BigDecimal baseAmount;

    @TableField("remark")
    private String remark;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
