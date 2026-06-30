package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购入库单(对应 purchase_receipt 表)
 */
@Data
@TableName("purchase_receipt")
public class PurchaseInbound {

    @TableId(value = "receipt_id", type = IdType.AUTO)
    private Long receiptId;

    @TableField("receipt_no")
    private String receiptNo;

    @TableField("receipt_date")
    private LocalDate receiptDate;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("contact_person")
    private String contactPerson;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("buyer_id")
    private Long buyerId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("discount_rate")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("invoice_no")
    private String invoiceNo;

    @TableField("order_no")
    private String orderNo;

    @TableField("delivery_no")
    private String deliveryNo;

    @TableField("is_return")
    private String isReturn;

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

    /** 采购入库明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<PurchaseInboundItem> items;
}
