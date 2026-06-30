package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 应付账款 - 采购订单确认时生成
 */
@Data
@TableName("accounts_payable")
public class AccountsPayable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应付单号: AP+年月日+4位流水 */
    private String apNo;

    /** 来源单据类型: PURCHASE_ORDER采购订单 */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据编号 */
    private String sourceNo;

    /** 供应商ID */
    private Long supplierId;
    private String supplierCode;
    private String supplierName;

    /** 应付金额 */
    private BigDecimal totalAmount;

    /** 已付金额 */
    private BigDecimal paidAmount;

    /** 应付余额 = totalAmount - paidAmount */
    private BigDecimal balanceAmount;

    /** 币种 */
    private String currency;

    /** 结算方式 */
    private String paymentMethod;

    /** 预计付款日期 */
    private LocalDate expectedDate;

    /** 实际结清日期 */
    private LocalDate settledDate;

    /** 状态: 待付款/部分付款/已结清 */
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
