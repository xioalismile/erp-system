package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("account")
public class Account {

    @TableId(value = "account_id", type = IdType.AUTO)
    @ExportField(label = "科目ID", order = 0, hidden = true)
    private Long accountId;

    @TableField("account_code")
    @ExportField(label = "科目编码", order = 1, required = true)
    private String accountCode;

    @TableField("account_name")
    @ExportField(label = "科目名称", order = 2, required = true)
    private String accountName;

    @TableField("account_full_name")
    @ExportField(label = "科目全称", order = 3)
    private String accountFullName;

    @TableField("parent_account_id")
    @ExportField(label = "上级科目ID", order = 4)
    private Long parentAccountId;

    @TableField("account_type")
    @ExportField(label = "科目类型", order = 5)
    private String accountType;

    @TableField("balance_direction")
    @ExportField(label = "余额方向", order = 6)
    private String balanceDirection;

    @TableField("account_level")
    @ExportField(label = "科目级别", order = 7)
    private Integer accountLevel;

    @TableField("is_leaf")
    @ExportField(label = "是否末级", order = 8)
    private Short isLeaf;

    @TableField("is_cash_account")
    @ExportField(label = "是否现金科目", order = 9)
    private Short isCashAccount;

    @TableField("is_qc_required")
    @ExportField(label = "是否需要质检", order = 10)
    private String isQcRequired;

    @TableField("is_qc_required_quantity")
    @ExportField(label = "是否质检数量", order = 11)
    private Short isQcRequiredQuantity;

    @TableField("status")
    @ExportField(label = "状态", order = 12)
    private String status;

    @TableField("cash_flow_item")
    @ExportField(label = "现金流量项目", order = 13)
    private String cashFlowItem;

    @TableField("auxiliary_accounting")
    @ExportField(label = "辅助核算", order = 14)
    private String auxiliaryAccounting;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 15)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 102, hidden = true)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新人", order = 103, hidden = true)
    private String updatedBy;
}