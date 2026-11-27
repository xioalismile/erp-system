package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem extends com.erp.common.bill.BillItemUnitFields {

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

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("received_quantity")
    private BigDecimal receivedQuantity;

    /** 已开票数量(关联发票回写, 不可编辑), 文档字段 FInvoiceQty */
    @TableField("invoiced_quantity")
    private BigDecimal invoicedQuantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("tax_type")
    private Integer taxType;

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

    /** 建议入库仓库, 文档字段 FStockID */
    @TableField("stock_id")
    private Long stockId;

    @TableField("delivery_date")
    private LocalDate deliveryDate;

    @TableField("delivery_place")
    private String deliveryPlace;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("request_line_no")
    private Integer requestLineNo;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("source_type")
    private String sourceType;

    @TableField("detail_close_status")
    private Integer detailCloseStatus;

    @TableField("turned_input_quantity")
    private BigDecimal turnedInputQuantity;

    @TableField("turned_return_quantity")
    private BigDecimal turnedReturnQuantity;

    @TableField("base_unit_price")
    private BigDecimal baseUnitPrice;

    @TableField("base_amount")
    private BigDecimal baseAmount;

    @TableField("base_tax_amount")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private BigDecimal baseNetAmount;

    /** 含税价（按行级/表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按行级/表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("remark")
    private String remark;

    /** 批次号（明细批次管理必填 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("batch_no")
    private String batchNo;

    /** 生产日期（批次物料生产日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("production_date")
    private LocalDate productionDate;

    /** 有效期至（批次物料失效日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
