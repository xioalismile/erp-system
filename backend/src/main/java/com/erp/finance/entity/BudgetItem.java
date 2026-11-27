package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算明细表 - 对齐 PG budget_item 表
 */
@Data
@TableName("budget_item")
public class BudgetItem {

    @TableId(value = "budget_item_id", type = IdType.AUTO)
    private Long budgetItemId;

    @TableField("budget_id")
    private Long budgetId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("account_id")
    private Long accountId;

    @TableField("account_code")
    private String accountCode;

    @TableField("account_name")
    private String accountName;

    @TableField("budget_amount")
    private BigDecimal budgetAmount;

    @TableField("used_amount")
    private BigDecimal usedAmount;

    @TableField("available_amount")
    private BigDecimal availableAmount;

    @TableField("period_detail")
    private String periodDetail;

    @TableField("budget_item_name")
    private String budgetItemName;

    @TableField("budget_basis")
    private String budgetBasis;

    @TableField("adjustment_amount")
    private BigDecimal adjustmentAmount;

    @TableField("adjustment_reason")
    private String adjustmentReason;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
