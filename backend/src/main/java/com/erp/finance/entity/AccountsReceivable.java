package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 应收账款 - 销售订单确认时生成
 */
@Data
@TableName("accounts_receivable")
public class AccountsReceivable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应收单号: AR+年月日+4位流水 */
    private String arNo;

    /** 来源单据类型: SALE_ORDER销售订单 */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据编号 */
    private String sourceNo;

    /** 客户ID */
    private Long customerId;
    private String customerCode;
    private String customerName;

    /** 应收金额 */
    private BigDecimal totalAmount;

    /** 已收金额 */
    private BigDecimal receivedAmount;

    /** 应收余额 = totalAmount - receivedAmount */
    private BigDecimal balanceAmount;

    /** 币种 */
    private String currency;

    /** 结算方式 */
    private String paymentMethod;

    /** 预计收款日期 */
    private LocalDate expectedDate;

    /** 实际结清日期 */
    private LocalDate settledDate;

    /** 状态: 待收款/部分收款/已结清/坏账 */
    private String status;

    /** 核销状态: 未核销/部分核销/已核销 */
    private String writeOffStatus;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;

    @TableLogic
    private Integer deleted;
}
