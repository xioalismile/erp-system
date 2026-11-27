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

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    // ========== 关联展示字段（非数据库字段，仅用于前端展示）==========

    /** 物料编码（真实列 inventory_current.material_code；物料被删时靠它兜底显示，2026-09-19 起生效） */
    @TableField("material_code")
    private String materialCode;

    /** 物料名称（真实列 inventory_current.material_name；物料被删时靠它兜底显示） */
    @TableField("material_name")
    private String materialName;

    /** 规格型号（真实列 inventory_current.spec；物料被删时靠它兜底显示） */
    @TableField("spec")
    private String model;

    /** 物料主图URL（关联material表） */
    @TableField(exist = false)
    private String mainImage;

    /** 单位名称（关联material表对应的单位） */
    @TableField(exist = false)
    private String unitName;

    /** 安全/最小库存（关联material表，用于预警展示） */
    @TableField(exist = false)
    private BigDecimal safetyStock;

    /** 仓库名称（关联warehouse表） */
    @TableField(exist = false)
    private String stockName;

    /** 仓库编码（关联warehouse表） */
    @TableField(exist = false)
    private String stockCode;

    /** 最新核算成本（取自cost_calculation表最新已确认记录的unit_cost，非数据库字段，仅用于前端展示） */
    @TableField(exist = false)
    private BigDecimal latestCalcCost;

    /** 订单占用量（生效销售订单待发货数量合计，按物料全局统计，非数据库字段） */
    @TableField(exist = false)
    private BigDecimal reservedQuantity;

    /** 预计可用量（历史名"虚拟可用"）= 本行实际可用库存 - 物料订单占用量（非数据库字段，负数表示缺口） */
    @TableField(exist = false)
    private BigDecimal virtualAvailable;
}
