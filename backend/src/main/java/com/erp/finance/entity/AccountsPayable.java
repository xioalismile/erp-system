package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应付账款 - 对齐 PG account_payable 表
 */
@Data
@TableName("account_payable")
public class AccountsPayable {

    @ExportField(label = "应付账款ID", order = 1, required = true)
    @TableId(value = "ap_id", type = IdType.AUTO)
    private Long apId;

    @ExportField(label = "应付单号", order = 2, required = true)
    @TableField("ap_no")
    private String apNo;

    @ExportField(label = "应付日期", order = 3, required = true)
    @TableField("ap_date")
    private LocalDate apDate;

    @ExportField(label = "供应商ID", order = 4, required = true)
    @TableField("supplier_id")
    private Long supplierId;

    @ExportField(label = "供应商名称", order = 5, required = true)
    @TableField("supplier_name")
    private String supplierName;

    @ExportField(label = "币种ID", order = 6, required = true)
    @TableField("currency_id")
    private Long currencyId;

    @ExportField(label = "汇率", order = 7, required = false)
    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @ExportField(label = "总金额", order = 8, required = true)
    @TableField("total_amount")
    private BigDecimal totalAmount;

    @ExportField(label = "税额", order = 9, required = false)
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @ExportField(label = "净额", order = 10, required = false)
    @TableField("net_amount")
    private BigDecimal netAmount;

    @ExportField(label = "已付金额", order = 11, required = false)
    @TableField("paid_amount")
    private BigDecimal paidAmount;

    @ExportField(label = "余额", order = 12, required = true)
    @TableField("balance_amount")
    private BigDecimal balanceAmount;

    @ExportField(label = "到期日", order = 13, required = false)
    @TableField("due_date")
    private LocalDate dueDate;

    @ExportField(label = "付款条款ID", order = 14, required = false)
    @TableField("payment_term_id")
    private Long paymentTermId;

    @ExportField(label = "来源单据类型", order = 15, required = false)
    @TableField("source_bill_type")
    private String sourceBillType;

    @ExportField(label = "来源单据号", order = 16, required = false)
    @TableField("source_bill_no")
    private String sourceBillNo;

    @ExportField(label = "发票号", order = 17, required = false)
    @TableField("invoice_no")
    private String invoiceNo;

    @ExportField(label = "采购员ID", order = 18, required = false)
    @TableField("buyer_id")
    private Long buyerId;

    @ExportField(label = "部门ID", order = 19, required = false)
    @TableField("department_id")
    private Long departmentId;

    @ExportField(label = "是否自动生成", order = 20, required = false)
    @TableField("is_auto_gen")
    private Short isAutoGen;

    @ExportField(label = "是否已结清", order = 21, required = false)
    @TableField("is_closed")
    private Short isClosed;

    @ExportField(label = "结清人", order = 22, required = false)
    @TableField("closed_by")
    private String closedBy;

    @ExportField(label = "结清时间", order = 23, required = false)
    @TableField("closed_at")
    private LocalDateTime closedAt;

    @ExportField(label = "结清原因", order = 24, required = false)
    @TableField("closed_reason")
    private String closedReason;

    @ExportField(label = "备注", order = 25, required = false)
    @TableField("remark")
    private String remark;

    @ExportField(label = "附件URL", order = 26, required = false)
    @TableField("attachment_urls")
    private String attachmentUrls;

    @ExportField(label = "状态", order = 27, required = false)
    @TableField("status")
    private String status;

    @ExportField(label = "审批状态", order = 28, required = false)
    @TableField("approval_status")
    private String approvalStatus;

    @ExportField(label = "审批人", order = 29, required = false)
    @TableField("approved_by")
    private String approvedBy;

    @ExportField(label = "审批时间", order = 30, required = false)
    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @ExportField(label = "创建时间", order = 31, required = false)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @ExportField(label = "更新时间", order = 32, required = false)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @ExportField(label = "创建人", order = 33, required = false)
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @ExportField(label = "更新人", order = 34, required = false)
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @ExportField(label = "组织编码", order = 35, required = false)
    @TableField("org_code")
    private String orgCode;
}
