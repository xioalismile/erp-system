package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资金调拨单主表 - 对齐 PG fund_transfer 表
 */
@Data
@TableName("fund_transfer")
public class FundTransfer {

    @TableId(value = "transfer_id", type = IdType.AUTO)
    private Long transferId;

    @TableField("transfer_no")
    private String transferNo;

    @TableField("transfer_date")
    private LocalDate transferDate;

    @TableField("from_bank_account")
    private String fromBankAccount;

    @TableField("from_account_name")
    private String fromAccountName;

    @TableField("to_bank_account")
    private String toBankAccount;

    @TableField("to_account_name")
    private String toAccountName;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("transfer_fee")
    private BigDecimal transferFee;

    @TableField("reason")
    private String reason;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField("from_bank_name")
    private String fromBankName;

    @TableField("from_bank_branch")
    private String fromBankBranch;

    @TableField("from_account_balance")
    private BigDecimal fromAccountBalance;

    @TableField("to_bank_name")
    private String toBankName;

    @TableField("to_bank_branch")
    private String toBankBranch;

    @TableField("transfer_type")
    private String transferType;

    @TableField("expected_fee")
    private BigDecimal expectedFee;

    @TableField("actual_fee")
    private BigDecimal actualFee;

    @TableField("completion_date")
    private LocalDate completionDate;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;
}
