package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sale_contract_item")
public class SaleContractItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("contract_id")
    private Long contractId;

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

    @TableField("unit")
    private String unit;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("discount_rate")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("avg_cost")
    private BigDecimal avgCost;

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

    @TableField("delivery_date")
    private String deliveryDate;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("remark")
    private String remark;

    @TableField("sort_no")
    private Integer sortNo;

    /** 行级 BOM（2026-11-06）：按物料主 BOM 自动带出 */
    @TableField("bom_id")
    private Long bomId;

    @TableField("bom_code")
    private String bomCode;

    @TableField("bom_version")
    private String bomVersion;
}
