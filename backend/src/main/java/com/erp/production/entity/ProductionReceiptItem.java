package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产入库单明细 - 对齐 PG production_receipt_item 表
 */
@Data
@TableName("production_receipt_item")
public class ProductionReceiptItem {

    @TableId(value = "receipt_item_id", type = IdType.AUTO)
    private Long receiptItemId;

    @TableField("receipt_id")
    private Long receiptId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("work_order_line_no")
    private Integer workOrderLineNo;

    @TableField("operation_report_no")
    private String operationReportNo;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
