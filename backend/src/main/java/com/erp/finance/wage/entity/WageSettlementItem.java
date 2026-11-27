package com.erp.finance.wage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序工资核算单明细 - 对齐 PG wage_settlement_item 表（每条已生效工序汇报单一行）
 */
@Data
@TableName("wage_settlement_item")
public class WageSettlementItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("settlement_id")
    private Long settlementId;

    @TableField("line_no")
    private Integer lineNo;

    /** 生产工单号（operation_report.production_order_no） */
    @TableField("order_no")
    private String orderNo;

    /** 工序编码（work_order_operation / routing_operation） */
    @TableField("operation_code")
    private String operationCode;

    @TableField("operation_name")
    private String operationName;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("employee_name")
    private String employeeName;

    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    /** 计件单价（元/件） */
    @TableField("piece_rate")
    private BigDecimal pieceRate;

    @TableField("piece_amount")
    private BigDecimal pieceAmount;

    /** 工时（含加班工时，用于计时工资核算） */
    @TableField("work_hours")
    private BigDecimal workHours;

    /** 工时费率（元/小时） */
    @TableField("hourly_rate")
    private BigDecimal hourlyRate;

    @TableField("time_amount")
    private BigDecimal timeAmount;

    /** 小计 = 计件工资 + 计时工资 */
    @TableField("amount")
    private BigDecimal amount;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
