package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产入库单 - 对齐 PG production_receipt 表
 */
@Data
@TableName("production_receipt")
public class ProductionReceipt {

    @TableId(value = "receipt_id", type = IdType.AUTO)
    private Long receiptId;

    @TableField("receipt_no")
    private String receiptNo;

    @TableField("receipt_date")
    private LocalDate receiptDate;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("production_order_no")
    private String productionOrderNo;

    @TableField("department_id")
    private Long departmentId;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("total_quantity")
    private BigDecimal totalQuantity;

    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

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

    /** 明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<ProductionReceiptItem> items;
}
