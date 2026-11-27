package com.erp.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打样单表体（打样子件清单/样品BOM）
 * 对应 sample_order_item 表，文档5.9 表体字段（14字段）
 */
@Data
@TableName("sample_order_item")
public class SampleOrderItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("sample_id")
    private Long sampleId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("child_material_id")
    private Long childMaterialId;

    @TableField("child_material_code")
    private String childMaterialCode;

    @TableField("child_material_name")
    private String childMaterialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    /** 基本用量：单个样品所需子件数量 */
    @TableField("base_qty")
    private BigDecimal baseQty;

    /** 用量类型：固定/变动 */
    @TableField("qty_type")
    private String qtyType;

    @TableField("scrap_rate")
    private BigDecimal scrapRate;

    /** 供应类型：领料/倒冲/虚拟件/委外 */
    @TableField("supply_type")
    private String supplyType;

    @TableField("is_key_part")
    private Short isKeyPart;

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

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
