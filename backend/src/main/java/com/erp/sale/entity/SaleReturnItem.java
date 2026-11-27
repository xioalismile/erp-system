package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sale_return_item")
public class SaleReturnItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "return_item_id", type = IdType.AUTO)
    private Long returnItemId;

    @TableField("return_id")
    private Long returnId;

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

    @TableField("tax_type")
    private Integer taxType;

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

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("batch_no")
    private String batchNo;

    /** 行级 BOM（2026-11-06）：按物料主 BOM 自动带出 */
    @TableField("bom_id")
    private Long bomId;

    @TableField("bom_code")
    private String bomCode;

    @TableField("bom_version")
    private String bomVersion;

    @TableField("delivery_line_no")
    private Integer deliveryLineNo;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("source_type")
    private String sourceType;

    /** 已开票数量（被红字销售发票引用的累计数量） */
    @TableField("turned_invoice_quantity")
    private BigDecimal turnedInvoiceQuantity;

    @TableField("return_reason")
    private String returnReason;

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
