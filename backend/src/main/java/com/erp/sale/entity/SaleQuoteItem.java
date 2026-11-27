package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sale_quote_item")
public class SaleQuoteItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "quote_item_id", type = IdType.AUTO)
    private Long quoteItemId;

    @TableField("quote_id")
    private Long quoteId;

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

    /** 单位名称(非数据库字段,仅用于前端展示) */
    @TableField(exist = false)
    private String unit;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 折扣率(%) */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 折扣额 */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("tax_rate")
    private BigDecimal taxRate = new BigDecimal("13");

    /** 是否赠品 0-否 1-是 (数据库 smallint，勿用 Boolean) */
    @TableField("is_free")
    private Integer isFree;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    /** 已转订单数量 */
    @TableField("turned_order_quantity")
    private BigDecimal turnedOrderQuantity = BigDecimal.ZERO;

    @TableField("delivery_date")
    private LocalDate deliveryDate;

    @TableField("delivery_days")
    private Integer deliveryDays;

    @TableField("remark")
    private String remark;

    /** 行级 BOM（2026-11-06）：按物料主 BOM 自动带出 */
    @TableField("bom_id")
    private Long bomId;

    @TableField("bom_code")
    private String bomCode;

    @TableField("bom_version")
    private String bomVersion;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
