package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收付款核销明细 - 对齐 PG bill_writeoff_detail 表
 * bill_type: 收款单 / 付款单
 * source_type: 预收款单 / 销售订单 / 应收单 / 预付款单 / 应付单
 */
@Data
@TableName("bill_writeoff_detail")
public class BillWriteoffDetail {

    @TableId(value = "writeoff_id", type = IdType.AUTO)
    private Long writeoffId;

    /** 方向：收款单 / 付款单 */
    @TableField("bill_type")
    private String billType;

    /** 收款单 receipt_id / 付款单 payment_id */
    @TableField("bill_id")
    private Long billId;

    @TableField("bill_no")
    private String billNo;

    /** 来源单类型：预收款单 / 销售订单 / 应收单 / 预付款单 / 应付单 */
    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_no")
    private String sourceNo;

    /** 来源明细行ID（销售订单明细行；空=汇总收款） */
    @TableField("source_item_id")
    private Long sourceItemId;

    /** 来源明细行货品名（明细收款展示用） */
    @TableField("source_item_name")
    private String sourceItemName;

    @TableField("source_amount")
    private BigDecimal sourceAmount;

    @TableField("used_amount")
    private BigDecimal usedAmount;

    @TableField("writeoff_amount")
    private BigDecimal writeoffAmount;

    @TableField("writeoff_date")
    private LocalDate writeoffDate;

    /** 冲销状态：未冲销 / 已冲销（已冲销的行在确认收款/付款时跳过，不重复核销） */
    @TableField("settled_qty")
    private String settledQty;

    /** 独立冲销时间 */
    @TableField("settled_at")
    private LocalDateTime settledAt;

    /** 余数不收：1=本行来源单剩余金额不再收/付款（结案） */
    @TableField("waive_remainder")
    private Integer waiveRemainder;

    /** 行备注（明细行的备注） */
    @TableField("remark")
    private String remark;

    /** 销售员ID（选来源单时带出来源销售订单的销售员；应收核销沿来源单回溯；可改） */
    @TableField("saler_id")
    private Long salerId;

    /** 销售员姓名（冗余列，展示用） */
    @TableField("saler_name")
    private String salerName;

    /** 行不含税收款 = 本行本次核销金额 ×(订单不含税金额÷订单价税合计)，订单价税合计≤0或缺 → 不换算 */
    @TableField("tax_excluded_amount")
    private BigDecimal taxExcludedAmount;

    /** 行提成比例%（行级可改，空则回落单头/设置中心默认） */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 行特殊服务费（行级手填扣减项，随行落库——保证每行提成可归属到该行销售员） */
    @TableField("special_service_fee")
    private BigDecimal specialServiceFee;

    /** 行销售提成 = (行不含税收款 − 行特殊服务费) × 行提成比例% */
    @TableField("commission")
    private BigDecimal commission;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;
}
