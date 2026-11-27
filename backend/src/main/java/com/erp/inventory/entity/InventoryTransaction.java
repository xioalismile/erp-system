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

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    // ===== 物料冗余展示列（真实列，2026-09-19 起随台账一起落库）=====
    // 台账是历史账：物料主档被删除/换 id 后实时 join 补不回编码，必须自带一份
    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    // ===== 关联展示字段（非数据库字段，由 Controller 填充） =====
    @TableField(exist = false)
    private String unitName;

    @TableField(exist = false)
    private String stockName;

    @TableField(exist = false)
    private String stockCode;

    // ===== 单据跳转字段（非数据库字段，由 Controller 按 trans_type + trans_no 反查单据主键填充） =====
    // 台账只存单据号文本、不存单据主键；用户在库存流水里点「单据号」需跳到来源单据，故在查询时反查一次主键。
    /** 单据类型编码（单据关联注册表的 docType，如 PURCHASE_INBOUND）；为空表示该行无法跳转 */
    @TableField(exist = false)
    private String billDocType;

    /** 对应单据主键（反查不到为 null，前端据此决定是否可点） */
    @TableField(exist = false)
    private Long billId;
}
