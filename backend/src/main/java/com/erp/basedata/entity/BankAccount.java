package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 银行账户（财务账户）实体
 * 对应表: bank_account（06_finance.sql 定义）
 */
@Data
@TableName("bank_account")
public class BankAccount {

    @TableId(value = "account_id", type = IdType.AUTO)
    @ExportField(label = "账户ID", order = 0, hidden = true)
    private Long accountId;

    @TableField("account_no")
    @ExportField(label = "账户编码", order = 1, required = true)
    private String accountNo;

    @TableField("account_name")
    @ExportField(label = "账户名称", order = 2, required = true)
    private String accountName;

    @TableField("bank_name")
    @ExportField(label = "开户银行", order = 3, required = true)
    private String bankName;

    @TableField("account_type")
    @ExportField(label = "账户类型", order = 4)
    private String accountType;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 5, hidden = true)
    private Long currencyId;

    @TableField("opening_balance")
    @ExportField(label = "期初余额", order = 6)
    private BigDecimal openingBalance;

    @TableField("current_balance")
    @ExportField(label = "当前余额", order = 7)
    private BigDecimal currentBalance;

    @TableField("is_default")
    @ExportField(label = "默认账户", order = 8)
    private Short isDefault;

    @TableField("is_active")
    @ExportField(label = "是否活跃", order = 9)
    private Short isActive;

    @TableField("status")
    @ExportField(label = "状态", order = 10)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 11)
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
