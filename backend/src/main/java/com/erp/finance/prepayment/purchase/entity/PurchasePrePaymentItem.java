package com.erp.finance.prepayment.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_pre_payment_item")
public class PurchasePrePaymentItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("pre_payment_id")
    private Long prePaymentId;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_no")
    private String sourceNo;

    @TableField("source_type")
    private String sourceType;

    @TableField("account_id")
    private Long accountId;

    @TableField("account_name")
    private String accountName;

    @TableField("account_no")
    private String accountNo;

    @TableField("amount")
    private BigDecimal amount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 退款金额（原币，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("return_amount")
    private BigDecimal returnAmount;

    /** 本位币金额（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("base_amount")
    private BigDecimal baseAmount;

    /** 已核销金额（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("used_amount")
    private BigDecimal usedAmount;

    /** 已核销本位币金额（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("used_base_amount")
    private BigDecimal usedBaseAmount;

    /** 退款本位币金额（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("return_base_amount")
    private BigDecimal returnBaseAmount;

    /** 备注（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("remark")
    private String remark;
}
