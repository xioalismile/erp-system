package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存台账 - 记录所有库存出入库流水
 */
@Data
@TableName("inventory_ledger")
public class InventoryTransaction {

    @TableId(value = "ledger_id", type = IdType.AUTO)
    private Long ledgerId;

    @TableField("trans_date")
    private LocalDate transDate;

    @TableField("trans_type")
    private String transType;

    @TableField("trans_no")
    private String transNo;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("material_id")
    private Long materialId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("in_quantity")
    private BigDecimal inQuantity;

    @TableField("in_amount")
    private BigDecimal inAmount;

    @TableField("out_quantity")
    private BigDecimal outQuantity;

    @TableField("out_amount")
    private BigDecimal outAmount;

    @TableField("balance_quantity")
    private BigDecimal balanceQuantity;

    @TableField("balance_amount")
    private BigDecimal balanceAmount;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_line_no")
    private Integer sourceLineNo;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private String createdBy;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
