package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应收账款 - 对齐 PG account_receivable 表
 */
@Data
@TableName("account_receivable")
public class AccountsReceivable {

    @TableId(value = "ar_id", type = IdType.AUTO)
    private Long arId;

    @TableField("ar_no")
    private String arNo;

    @TableField("ar_date")
    private LocalDate arDate;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("paid_amount")
    private BigDecimal paidAmount;

    @TableField("balance_amount")
    private BigDecimal balanceAmount;

    @TableField("due_date")
    private LocalDate dueDate;

    @TableField("payment_term_id")
    private Long paymentTermId;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("invoice_no")
    private String invoiceNo;

    @TableField("saler_id")
    private Long salerId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("is_auto_gen")
    private Short isAutoGen;

    @TableField("is_closed")
    private Short isClosed;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

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

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;
}
