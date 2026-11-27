package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收款单 - 对齐 PG cash_receipt 表
 */
@Data
@TableName("cash_receipt")
public class PaymentReceipt {

    @ExportField(label = "收款单ID", order = 1, hidden = true)
    @TableId(value = "receipt_id", type = IdType.AUTO)
    private Long receiptId;

    @ExportField(label = "收款单号", order = 2, required = true)
    @TableField("receipt_no")
    private String receiptNo;

    @ExportField(label = "收款日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    @TableField("receipt_date")
    private LocalDate receiptDate;

    @ExportField(label = "客户ID", order = 4, hidden = true)
    @TableField("customer_id")
    private Long customerId;

    @ExportField(label = "客户名称", order = 5, required = true)
    @TableField("customer_name")
    private String customerName;

    @ExportField(label = "结算方式ID", order = 6, hidden = true)
    @TableField("settle_mode_id")
    private Long settleModeId;

    @ExportField(label = "银行账户", order = 7)
    @TableField("bank_account")
    private String bankAccount;

    @ExportField(label = "币种ID", order = 8, hidden = true)
    @TableField("currency_id")
    private Long currencyId;

    @ExportField(label = "汇率", order = 9, numberFormat = "0.00")
    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @ExportField(label = "总金额", order = 10, numberFormat = "0.00")
    @TableField("total_amount")
    private BigDecimal totalAmount;

    @ExportField(label = "收款金额", order = 11, numberFormat = "0.00", required = true)
    @TableField("amount")
    private BigDecimal amount;

    @ExportField(label = "折扣金额", order = 12, numberFormat = "0.00")
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @ExportField(label = "实收金额", order = 13, numberFormat = "0.00")
    @TableField("received_amount")
    private BigDecimal receivedAmount;

    /** 提成比例%（新开单默认取设置中心参数 finance.commission.default.rate，单内可改） */
    @ExportField(label = "提成比例%", order = 20, numberFormat = "0.00")
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 特殊服务费（提成基数扣减项，手填） */
    @ExportField(label = "特殊服务费", order = 21, numberFormat = "0.00")
    @TableField("special_service_fee")
    private BigDecimal specialServiceFee;

    /** 不含税收款金额（=核销明细各行不含税收款合计，保存时后端兜底重算） */
    @ExportField(label = "不含税收款金额", order = 22, numberFormat = "0.00")
    @TableField("tax_excluded_received_amount")
    private BigDecimal taxExcludedReceivedAmount;

    /** 销售提成 =（不含税收款金额 − 特殊服务费）× 提成比例%，保存时后端兜底重算 */
    @ExportField(label = "销售提成", order = 23, numberFormat = "0.00")
    @TableField("sales_commission")
    private BigDecimal salesCommission;

    @ExportField(label = "票据号", order = 14)
    @TableField("document_no")
    private String documentNo;

    @ExportField(label = "出纳员ID", order = 15, hidden = true)
    @TableField("cashier_id")
    private Long cashierId;

    @ExportField(label = "部门ID", order = 16, hidden = true)
    @TableField("department_id")
    private Long departmentId;

    @ExportField(label = "来源", order = 24)
    @TableField("source_type")
    private String sourceType;

    @ExportField(label = "来源单据类型", order = 17)
    @TableField("source_bill_type")
    private String sourceBillType;

    @ExportField(label = "来源单据号", order = 18)
    @TableField("source_bill_no")
    private String sourceBillNo;

    @ExportField(label = "备注", order = 19)
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

    /** 收款明细（传输字段，不落主表；保存草稿时落库 cash_receipt_item，详情接口组装回填） */
    @TableField(exist = false)
    private java.util.List<PaymentReceiptItem> items;
}
