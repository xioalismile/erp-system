package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单用料明细 - 对齐 PG production_order_item 表
 */
@Data
@TableName("production_order_item")
public class WorkOrderMaterial {

    @TableId(value = "order_item_id", type = IdType.AUTO)
    private Long orderItemId;

    @TableField("order_id")
    private Long orderId;

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

    @TableField("bom_quantity")
    private BigDecimal bomQuantity;

    @TableField("required_quantity")
    private BigDecimal requiredQuantity;

    @TableField("issued_quantity")
    private BigDecimal issuedQuantity;

    @TableField("returned_quantity")
    private BigDecimal returnedQuantity;

    @TableField("consumed_quantity")
    private BigDecimal consumedQuantity;

    @TableField("scrap_quantity")
    private BigDecimal scrapQuantity;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("total_cost")
    private BigDecimal totalCost;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

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
