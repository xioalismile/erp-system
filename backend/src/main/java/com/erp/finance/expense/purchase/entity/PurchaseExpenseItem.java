package com.erp.finance.expense.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_expense_item")
public class PurchaseExpenseItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("expense_id")
    private Long expenseId;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_no")
    private String sourceNo;

    @TableField("source_type")
    private String sourceType;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_name")
    private String materialName;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("sharing_amount")
    private BigDecimal sharingAmount;

    @TableField("sharing_ratio")
    private BigDecimal sharingRatio;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
