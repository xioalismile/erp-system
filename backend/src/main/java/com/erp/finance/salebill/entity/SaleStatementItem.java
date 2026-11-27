package com.erp.finance.salebill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("sale_statement_item")
public class SaleStatementItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("statement_id")
    private Long statementId;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_no")
    private String sourceNo;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_date")
    private LocalDate sourceDate;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("received_amount")
    private BigDecimal receivedAmount;

    @TableField("unpaid_amount")
    private BigDecimal unpaidAmount;

    @TableField("remark")
    private String remark;
}
