package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存盘点明细(对应 stock_check_item 表)
 */
@Data
@TableName("stock_check_item")
public class StockCheckItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "check_item_id", type = IdType.AUTO)
    @ExportField(label = "明细ID", order = 1, hidden = true)
    private Long checkItemId;

    @TableField("check_id")
    @ExportField(label = "盘点单ID", order = 2, hidden = true)
    private Long checkId;

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

    @TableField("stock_id")
    @ExportField(label = "库存ID", order = 9, hidden = true)
    private Long stockId;

    @TableField("bin_id")
    @ExportField(label = "库位ID", order = 10, hidden = true)
    private Long binId;

    @TableField("batch_no")
    @ExportField(label = "批号", order = 11)
    private String batchNo;

    @TableField("book_quantity")
    @ExportField(label = "账面数量", order = 12, numberFormat = "0.00")
    private BigDecimal bookQuantity;

    @TableField("book_amount")
    @ExportField(label = "账面金额", order = 13, numberFormat = "0.00")
    private BigDecimal bookAmount;

    @TableField("check_quantity")
    @ExportField(label = "盘点数量", order = 14, numberFormat = "0.00")
    private BigDecimal checkQuantity;

    @TableField("check_amount")
    @ExportField(label = "盘点金额", order = 15, numberFormat = "0.00")
    private BigDecimal checkAmount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("profit_quantity")
    @ExportField(label = "盘盈数量", order = 16, numberFormat = "0.00")
    private BigDecimal profitQuantity;

    @TableField("profit_amount")
    @ExportField(label = "盘盈金额", order = 17, numberFormat = "0.00")
    private BigDecimal profitAmount;

    @TableField("loss_quantity")
    @ExportField(label = "盘亏数量", order = 18, numberFormat = "0.00")
    private BigDecimal lossQuantity;

    @TableField("loss_amount")
    @ExportField(label = "盘亏金额", order = 19, numberFormat = "0.00")
    private BigDecimal lossAmount;

    @TableField("unit_price")
    @ExportField(label = "单价", order = 20, numberFormat = "0.00")
    private BigDecimal unitPrice;

    @TableField("avg_cost")
    @ExportField(label = "平均成本", order = 21, numberFormat = "0.00")
    private BigDecimal avgCost;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("check_result")
    @ExportField(label = "盘点结果", order = 22)
    private String checkResult;

    @TableField("reason")
    @ExportField(label = "原因", order = 23)
    private String reason;

    @TableField("remark")
    @ExportField(label = "备注", order = 24)
    private String remark;

    @TableField("profit_loss_reason")
    @ExportField(label = "盘盈盘亏原因", order = 25)
    private String profitLossReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
