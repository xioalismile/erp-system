package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购发票(对应 purchase_invoice 表)
 * 文档3.5 表头字段(23个)
 * 单据状态流转: 草稿 → 待审核 → 已生效
 */
@Data
@TableName("purchase_invoice")
public class PurchaseInvoice {

    @TableId(value = "invoice_id", type = IdType.AUTO)
    @ExportField(label = "发票ID", order = 0, hidden = true)
    private Long invoiceId;

    @TableField("invoice_no")
    @ExportField(label = "发票号", order = 1, required = true)
    private String invoiceNo;

    @TableField("invoice_type")
    @ExportField(label = "发票类型", order = 2)
    private String invoiceType;

    @TableField("invoice_date")
    @ExportField(label = "发票日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate invoiceDate;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 4, required = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商名称", order = 5)
    private String supplierName;

    @TableField("tax_no")
    @ExportField(label = "供应商税号", order = 6)
    private String supplierTaxNo;

    @TableField("source_bill_type")
    @ExportField(label = "源单类型", order = 7)
    private String sourceType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 8)
    private String sourceBillNo;

    @TableField("total_amount")
    @ExportField(label = "金额", order = 9, numberFormat = "0.00")
    private BigDecimal amount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 10, numberFormat = "0.00")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    @ExportField(label = "价税合计", order = 11, numberFormat = "0.00")
    private BigDecimal taxTotalAmount;

    @TableField("invoice_code")
    @ExportField(label = "发票代码", order = 12)
    private String invoiceCode;

    @TableField("invoice_number")
    @ExportField(label = "发票号码", order = 13)
    private String invoiceNumber;

    @TableField("status")
    @ExportField(label = "状态", order = 14)
    private String status;

    @TableField("is_red_invoice")
    @ExportField(label = "是否红字发票", order = 15)
    private Integer isRedInvoice;

    @TableField("is_match")
    @ExportField(label = "是否勾稽", order = 16)
    private Integer isMatch;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 17)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核日期", order = 18, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
    private String remark;

    @TableField("is_closed")
    private Integer isClosed;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 审批状态标签（与 status 同源同步，统一状态机抽象方法要求） */
    @TableField("approval_status")
    private String approvalStatus;

    /** 作废前状态（数字code，供反作废恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField("base_total_amount")
    private BigDecimal baseTotalAmount;

    @TableField("base_tax_amount")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private BigDecimal baseNetAmount;

    /** 表头「计税方式」（含税价/不含税价/不计税）：行级口径，表头值仅作新增行默认值；映射数据库列 tax_calc_method */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

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

    /** 收货地址（发票对应收货地址 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("delivery_address")
    private String deliveryAddress;

    /** 联系人（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("contact_person")
    @ExportField(label = "联系人", order = 20)
    private String contactPerson;

    /** 联系电话（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 21)
    private String contactPhone;

    /** 币种ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 22)
    private Long currencyId;

    /** 折扣率（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 23, numberFormat = "0.00")
    private BigDecimal discountRate;

    /** 汇率（数据库已存在该列，此前实体未映射 → 前端填了不落库） */
    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 24, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField(exist = false)
    private List<PurchaseInvoiceItem> items;

    @TableField(exist = false)
    private String supplierCode;

    @TableField(exist = false)
    private String buyerName;
}