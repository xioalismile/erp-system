package com.erp.finance.cost.calculation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cost_calculation_item")
public class CostCalculationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("calc_id")
    private Long calcId;

    @TableField("cost_type")
    private String costType;

    @TableField("cost_name")
    private String costName;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("unit")
    private String unit;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 含税价（按表头「计税方式」由单价换算落库，口径对齐销售订单试点） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算落库，口径对齐销售订单试点） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("amount")
    private BigDecimal amount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}