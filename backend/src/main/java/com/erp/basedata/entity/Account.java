package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会计科目 - 对齐 PG account 表
 */
@Data
@TableName("account")
public class Account {

    @TableId(value = "account_id", type = IdType.AUTO)
    private Long accountId;

    @TableField("account_code")
    private String accountCode;

    @TableField("account_name")
    private String accountName;

    @TableField("account_full_name")
    private String accountFullName;

    @TableField("parent_account_id")
    private Long parentAccountId;

    @TableField("account_type")
    private String accountType;

    @TableField("balance_direction")
    private String balanceDirection;

    @TableField("account_level")
    private Integer accountLevel;

    @TableField("is_leaf")
    private Short isLeaf;

    @TableField("is_cash_account")
    private Short isCashAccount;

    @TableField("is_qc_required")
    private String isQcRequired;

    @TableField("is_qc_required_quantity")
    private Short isQcRequiredQuantity;

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
