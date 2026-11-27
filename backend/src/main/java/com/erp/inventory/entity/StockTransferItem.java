package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存调拨明细(对应 stock_transfer_item 表)
 */
@Data
@TableName("stock_transfer_item")
public class StockTransferItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "transfer_item_id", type = IdType.AUTO)
    @ExportField(label = "明细ID", order = 1, hidden = true)
    private Long transferItemId;

    @TableField("transfer_id")
    @ExportField(label = "调拨单ID", order = 2, hidden = true)
    private Long transferId;

    @TableField("line_no")
    @ExportField(label = "行号", order = 3)
    private Integer lineNo;

    @TableField("material_id")
    @ExportField(label = "物料ID", order = 4, hidden = true)
    private Long materialId;

    @TableField("material_code")
    @ExportField(label = "物料编码", order = 5, required = true)
    private String materialCode;

    @TableField("material_name")
    @ExportField(label = "物料名称", order = 6, required = true)
    private String materialName;

    @TableField("specification")
    @ExportField(label = "规格型号", order = 7)
    private String specification;

    @TableField("unit_id")
    @ExportField(label = "单位ID", order = 8, hidden = true)
    private Long unitId;

    @TableField("quantity")
    @ExportField(label = "数量", order = 9, numberFormat = "0.00")
    private BigDecimal quantity;

    @TableField("unit_price")
    @ExportField(label = "单价", order = 10, numberFormat = "0.00")
    private BigDecimal unitPrice;

    @TableField("amount")
    @ExportField(label = "金额", order = 11, numberFormat = "0.00")
    private BigDecimal amount;

    @TableField("avg_cost")
    @ExportField(label = "平均成本", order = 12, numberFormat = "0.00")
    private BigDecimal avgCost;

    @TableField("from_stock_id")
    @ExportField(label = "调出库存ID", order = 13, hidden = true)
    private Long fromStockId;

    @TableField("from_bin_id")
    @ExportField(label = "调出库位ID", order = 14, hidden = true)
    private Long fromBinId;

    @TableField("to_stock_id")
    @ExportField(label = "调入库存ID", order = 15, hidden = true)
    private Long toStockId;

    @TableField("to_bin_id")
    @ExportField(label = "调入库位ID", order = 16, hidden = true)
    private Long toBinId;

    @TableField("batch_no")
    @ExportField(label = "批号", order = 17)
    private String batchNo;

    @TableField("production_date")
    @ExportField(label = "生产日期", order = 18, dateFormat = "yyyy-MM-dd")
    private LocalDate productionDate;

    @TableField("expiry_date")
    @ExportField(label = "有效期至", order = 19, dateFormat = "yyyy-MM-dd")
    private LocalDate expiryDate;

    @TableField("source_bill_type")
    @ExportField(label = "源单类型", order = 20)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 21)
    private String sourceBillNo;

    @TableField("source_doc_id")
    @ExportField(label = "源单ID", order = 22, hidden = true)
    private Long sourceDocId;

    @TableField("remark")
    @ExportField(label = "备注", order = 23)
    private String remark;

    /** 税率(%)（明细税率 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("tax_rate")
    private BigDecimal taxRate;

    /** 折扣率(%)（明细折扣率 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 折扣金额（明细折扣金额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /** 税额（明细税额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 净额（明细净额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
