package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("batch")
public class InventoryBatch {

    @TableId(value = "batch_id", type = IdType.AUTO)
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("manufacturing_no")
    private String manufacturingNo;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("incoming_date")
    private LocalDate incomingDate;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("available_quantity")
    private BigDecimal availableQuantity;

    @TableField("frozen_quantity")
    private BigDecimal frozenQuantity;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
