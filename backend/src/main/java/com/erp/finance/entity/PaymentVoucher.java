package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
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
    @ExportField(label = "付款单ID", order = 1, hidden = true)
    private Long paymentId;

    @TableField("payment_no")
    @ExportField(label = "付款单号", order = 2, required = true)
    private String paymentNo;

    @TableField("payment_date")
    @ExportField(label = "付款日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate paymentDate;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 4, hidden = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商名称", order = 5, required = true)
    private String supplierName;

    @TableField("settle_mode_id")
    @ExportField(label = "结算方式ID", order = 6, hidden = true)
    private Long settleModeId;

    @TableField("bank_account")
    @ExportField(label = "银行账号", order = 7)
    private String bankAccount;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 8, hidden = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 9, numberFormat = "0.00")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "总金额", order = 10, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("amount")
    @ExportField(label = "付款金额", order = 11, numberFormat = "0.00", required = true)
    private BigDecimal amount;

    @TableField("discount_amount")
    @ExportField(label = "折扣金额", order = 12, numberFormat = "0.00")
    private BigDecimal discountAmount;

    @TableField("paid_amount")
    @ExportField(label = "已付金额", order = 13, numberFormat = "0.00")
    private BigDecimal paidAmount;

    @TableField("document_no")
    @ExportField(label = "凭证号", order = 14)
    private String documentNo;

    @TableField("cashier_id")
    @ExportField(label = "出纳ID", order = 15, hidden = true)
    private Long cashierId;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 16, hidden = true)
    private Long departmentId;

    @TableField("source_bill_type")
    @ExportField(label = "源单类型", order = 17)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 18)
    private String sourceBillNo;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
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

    /** 结案状态 0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;

    /** 作废前状态码(0草稿/1待审核/2已确认)，反作废恢复用 */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

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

    /** 核销来源明细（传输字段，不落主表；保存草稿时落库 bill_writeoff_detail，确认时核销） */
    @TableField(exist = false)
    private java.util.List<BillWriteoffDetail> writeOffItems;

    /** 付款明细（传输字段，不落主表；保存草稿时落库 cash_payment_item，详情接口组装回填） */
    @TableField(exist = false)
    private java.util.List<PaymentVoucherItem> items;
}
