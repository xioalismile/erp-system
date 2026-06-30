package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存盘点明细(对应 stock_check_item 表)
 */
@Data
@TableName("stock_check_item")
public class StockCheckItem {

    @TableId(value = "check_item_id", type = IdType.AUTO)
    private Long checkItemId;

    @TableField("check_id")
    private Long checkId;

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

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("book_quantity")
    private BigDecimal bookQuantity;

    @TableField("book_amount")
    private BigDecimal bookAmount;

    @TableField("check_quantity")
    private BigDecimal checkQuantity;

    @TableField("check_amount")
    private BigDecimal checkAmount;

    @TableField("profit_quantity")
    private BigDecimal profitQuantity;

    @TableField("profit_amount")
    private BigDecimal profitAmount;

    @TableField("loss_quantity")
    private BigDecimal lossQuantity;

    @TableField("loss_amount")
    private BigDecimal lossAmount;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("check_result")
    private String checkResult;

    @TableField("reason")
    private String reason;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
