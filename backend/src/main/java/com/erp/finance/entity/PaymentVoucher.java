package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款单 - 对齐 PG cash_payment 表
 */
@Data
@TableName("cash_payment")
public class PaymentVoucher {

    @TableId(value = "payment_id", type = IdType.AUTO)
    private Long paymentId;

    @TableField("payment_no")
    private String paymentNo;

    @TableField("payment_date")
    private LocalDate paymentDate;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("settle_mode_id")
    private Long settleModeId;

    @TableField("bank_account")
    private String bankAccount;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("paid_amount")
    private BigDecimal paidAmount;

    @TableField("document_no")
    private String documentNo;

    @TableField("cashier_id")
    private Long cashierId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

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
