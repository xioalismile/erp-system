package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产领料明细(对应 material_requisition_item 表)
 */
@Data
@TableName("material_requisition_item")
public class MaterialRequisitionItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "requisition_item_id", type = IdType.AUTO)
    @ExportField(label = "明细ID", order = 1, hidden = true)
    private Long requisitionItemId;

    @TableField("requisition_id")
    @ExportField(label = "领料单ID", order = 2, hidden = true)
    private Long requisitionId;

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

    @TableField("issued_quantity")
    @ExportField(label = "已发数量", order = 10, numberFormat = "0.00")
    private BigDecimal issuedQuantity;

    @TableField("unit_price")
    @ExportField(label = "单价", order = 11, numberFormat = "0.00")
    private BigDecimal unitPrice;

    @TableField("amount")
    @ExportField(label = "金额", order = 12, numberFormat = "0.00")
    private BigDecimal amount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("avg_cost")
    @ExportField(label = "平均成本", order = 13, numberFormat = "0.00")
    private BigDecimal avgCost;

    @TableField("stock_id")
    @ExportField(label = "库存ID", order = 14, hidden = true)
    private Long stockId;

    @TableField("bin_id")
    @ExportField(label = "库位ID", order = 15, hidden = true)
    private Long binId;

    @TableField("batch_no")
    @ExportField(label = "批号", order = 16)
    private String batchNo;

    @TableField("work_order_line_no")
    @ExportField(label = "工单行号", order = 17)
    private Integer workOrderLineNo;

    @TableField("source_bill_type")
    @ExportField(label = "源单类型", order = 18)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 19)
    private String sourceBillNo;

    @TableField("source_line_no")
    @ExportField(label = "源单行号", order = 20)
    private Integer sourceLineNo;

    @TableField("source_doc_id")
    @ExportField(label = "源单ID", order = 21, hidden = true)
    private Long sourceDocId;

    @TableField("remark")
    @ExportField(label = "备注", order = 22)
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
