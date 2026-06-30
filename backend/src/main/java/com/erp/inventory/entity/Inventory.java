package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_current")
public class Inventory {

    @TableId(value = "inventory_id", type = IdType.AUTO)
    private Long inventoryId;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("material_id")
    private Long materialId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("available_quantity")
    private BigDecimal availableQuantity;

    @TableField("frozen_quantity")
    private BigDecimal frozenQuantity;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("total_cost")
    private BigDecimal totalCost;

    @TableField("last_in_date")
    private LocalDate lastInDate;

    @TableField("last_out_date")
    private LocalDate lastOutDate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
