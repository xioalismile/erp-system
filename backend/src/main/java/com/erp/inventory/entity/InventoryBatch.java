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

    /** 物料编码（列表展示用，来自 material 表，不落库） */
    @TableField(exist = false)
    private String materialCode;

    /** 物料名称（列表展示用，来自 material 表，不落库） */
    @TableField(exist = false)
    private String materialName;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("manufacturing_no")
    private String manufacturingNo;

    @TableField("supplier_id")
    private Long supplierId;

    /** 供应商名称（列表展示用，来自 supplier 表，不落库） */
    @TableField(exist = false)
    private String supplierName;

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

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
