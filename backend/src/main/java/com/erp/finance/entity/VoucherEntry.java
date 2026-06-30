package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会计凭证分录表 - 对齐 PG voucher_entry 表
 */
@Data
@TableName("voucher_entry")
public class VoucherEntry {

    @TableId(value = "entry_id", type = IdType.AUTO)
    private Long entryId;

    @TableField("voucher_id")
    private Long voucherId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("account_id")
    private Long accountId;

    @TableField("account_code")
    private String accountCode;

    @TableField("account_name")
    private String accountName;

    @TableField("account_direction")
    private String accountDirection;

    @TableField("debit_amount")
    private BigDecimal debitAmount;

    @TableField("credit_amount")
    private BigDecimal creditAmount;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("foreign_amount")
    private BigDecimal foreignAmount;

    @TableField("auxiliary_type")
    private String auxiliaryType;

    @TableField("auxiliary_id")
    private Long auxiliaryId;

    @TableField("auxiliary_name")
    private String auxiliaryName;

    @TableField("settlement_no")
    private String settlementNo;

    @TableField("person_name")
    private String personName;

    @TableField("department_name")
    private String departmentName;

    @TableField("project_name")
    private String projectName;

    @TableField("customer_name")
    private String customerName;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("summary")
    private String summary;

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
