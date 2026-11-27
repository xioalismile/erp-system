package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 替换发货指示（销售在订单行上预先指定"这一行发多少、用哪个替代品"）。
 * 表 sale_order_substitute_plan，2026-11-27 替换发货需求。
 */
@Data
@TableName("sale_order_substitute_plan")
public class SaleOrderSubstitutePlan {

    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;

    @TableField("order_id")
    private Long orderId;

    @TableField("order_item_id")
    private Long orderItemId;

    @TableField("substitute_material_id")
    private Long substituteMaterialId;

    @TableField("substitute_material_code")
    private String substituteMaterialCode;

    @TableField("substitute_material_name")
    private String substituteMaterialName;

    @TableField("specification")
    private String specification;

    @TableField("base_unit_id")
    private Long baseUnitId;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("quantity")
    private BigDecimal quantity;

    /** 已执行数量：出库单审核 +，反审/作废 - */
    @TableField("turned_delivery_quantity")
    private BigDecimal turnedDeliveryQuantity;

    /** 有效 / 已取消 */
    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @TableField("org_code")
    private String orgCode;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    // ===== 展示字段（非数据库） =====
    @TableField(exist = false)
    private String orderNo;

    @TableField(exist = false)
    private Integer lineNo;

    @TableField(exist = false)
    private Long originMaterialId;

    @TableField(exist = false)
    private String originMaterialCode;

    @TableField(exist = false)
    private String originMaterialName;

    /** 待执行数量 = quantity - turnedDeliveryQuantity */
    @TableField(exist = false)
    private BigDecimal pendingQuantity;
}
