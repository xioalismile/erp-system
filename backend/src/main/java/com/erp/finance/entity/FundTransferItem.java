package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资金调拨明细表 - 对齐 PG fund_transfer_item 表
 */
@Data
@TableName("fund_transfer_item")
public class FundTransferItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("transfer_id")
    private Long transferId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("fee_item")
    private String feeItem;

    @TableField("fee_amount")
    private BigDecimal feeAmount;

    @TableField("fee_account_code")
    private String feeAccountCode;

    @TableField("settle_status")
    private String settleStatus;

    @TableField("settle_date")
    private LocalDate settleDate;

    @TableField("bank_ref_no")
    private String bankRefNo;

    @TableField("remark")
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
