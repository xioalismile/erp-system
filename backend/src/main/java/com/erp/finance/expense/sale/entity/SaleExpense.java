package com.erp.finance.expense.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sale_expense")
public class SaleExpense {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("expense_no")
    private String expenseNo;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("expense_type_id")
    private Long expenseTypeId;

    @TableField("expense_type_name")
    private String expenseTypeName;

    @TableField("expense_date")
    private LocalDate expenseDate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("base_amount")
    private BigDecimal baseAmount;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("related_doc_id")
    private Long relatedDocId;

    @TableField("related_doc_no")
    private String relatedDocNo;

    @TableField("related_doc_type")
    private String relatedDocType;

    @TableField("cost_sharing_method")
    private String costSharingMethod;

    @TableField("status")
    private Integer status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;
    @TableField("approved_at")
    private LocalDateTime approvedAt;
    /** 作废前状态（数字code，供反作废恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;
    @TableField("voided_by")
    private String voidedBy;
    @TableField("voided_at")
    private LocalDateTime voidedAt;
    @TableField("void_reason")
    private String voidReason;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<SaleExpenseItem> items;
}
