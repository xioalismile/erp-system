package com.erp.produce.mrp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mrp_plan_item")
public class MrpPlanItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("plan_id")
    private Long planId;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit")
    private String unit;

    @TableField("gross_demand")
    private BigDecimal grossDemand;

    @TableField("available_stock")
    private BigDecimal availableStock;

    @TableField("safety_stock")
    private BigDecimal safetyStock;

    @TableField("net_demand")
    private BigDecimal netDemand;

    @TableField("plan_quantity")
    private BigDecimal planQuantity;

    @TableField("suggest_type")
    private String suggestType;

    @TableField("lead_time")
    private Integer leadTime;

    @TableField("suggest_date")
    private LocalDate suggestDate;

    @TableField("related_bom_id")
    private Long relatedBomId;

    @TableField("level")
    private Integer level;

    @TableField("parent_id")
    private Long parentId;

    @TableField("sale_order_id")
    private Long saleOrderId;

    @TableField("sale_order_item_id")
    private Long saleOrderItemId;

    @TableField("sale_order_no")
    private String saleOrderNo;

    @TableField("remark")
    private String remark;

    /** 运算时使用的 BOM 清单编码（2026-11-06 持久化，重开计划仍在） */
    @TableField("bom_code")
    private String bomCode;

    /** 运算时使用的 BOM 版本 */
    @TableField("bom_version")
    private String bomVersion;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
