package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购发票明细(对应 purchase_invoice_item 表)
 * 文档3.5 表体字段(15个)
 */
@Data
@TableName("purchase_invoice_item")
public class PurchaseInvoiceItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "invoice_item_id", type = IdType.AUTO)
    private Long invoiceItemId;

    @TableField("invoice_id")
    private Long invoiceId;

    /** 行号 FRowNo, 从1开始, 步长1 */
    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    /** 规格型号 FModel */
    @TableField("specification")
    private String model;

    @TableField("unit_id")
    private Long unitId;

    /** 数量 FQty, 必须>0且≤可开票数量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 单价 FPrice, 不含税, 从源单带出 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 金额 FAmount, 自动计算: 数量 × 单价 */
    @TableField("amount")
    private BigDecimal amount;

    /** 税率(%) FTaxRate */
    @TableField("tax_rate")
    private BigDecimal taxRate;

    /** 税额 FTaxAmount, 自动计算: 金额 × 税率 / 100 */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 价税合计（自动计算：金额 + 税额） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 税收分类编码 FTaxCategory */
    @TableField("tax_category")
    private String taxCategory;

    /** 含税价（按行级「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按行级「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    /** 行级计税方式（价内税/价外税/不计税）：行级口径，传输字段不落库，保存时用于换算两列 */
    @TableField(exist = false)
    private String taxCalcMethod;

    /**
     * 折扣率(%)：前端明细有折扣列，但 purchase_invoice_item 表无折扣列，
     * 用传输字段接收后并入金额计算（折扣直接体现在 amount 中，不单独落库）。
     */
    @TableField(exist = false)
    private BigDecimal discountRate;

    /** 折扣额（传输字段，不落库；仅用于接收前端值并参与金额计算） */
    @TableField(exist = false)
    private BigDecimal discountAmount;

    /** 源单类型 FSourceType */
    @TableField("source_type")
    private String sourceType;

    /** 源单编号 FSourceBillNo */
    @TableField("source_bill_no")
    private String sourceBillNo;

    /** 源单类型（标准命名） */
    @TableField("source_bill_type")
    private String sourceBillType;

    /** 源单主表ID */
    @TableField("source_doc_id")
    private Long sourceDocId;

    /** 源单行号 FSourceRowNo */
    @TableField("source_row_no")
    private Integer sourceRowNo;

    /** 批次号（明细批次管理必填 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("batch_no")
    private String batchNo;

    /** 生产日期（批次物料生产日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("production_date")
    private LocalDate productionDate;

    /** 有效期至（批次物料失效日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("expiry_date")
    private LocalDate expiryDate;

    /** 行备注（明细行的备注，下推时随行带入） */
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
