package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产领料明细(对应 material_requisition_item 表)
 */
@Data
@TableName("material_requisition_item")
public class MaterialRequisitionItem {

    @TableId(value = "requisition_item_id", type = IdType.AUTO)
    private Long requisitionItemId;

    @TableField("requisition_id")
    private Long requisitionId;

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

    @TableField("issued_quantity")
    private BigDecimal issuedQuantity;

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

    @TableField("work_order_line_no")
    private Integer workOrderLineNo;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

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
