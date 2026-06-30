package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 付款记录 - 采购付款
 */
@Data
@TableName("payment_voucher")
public class PaymentVoucher {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 付款单号: PAY+年月日+4位流水 */
    private String voucherNo;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 关联应付单ID */
    private Long apId;
    private String apNo;

    /** 供应商ID */
    private Long supplierId;
    private String supplierCode;
    private String supplierName;

    /** 采购订单ID */
    private Long purchaseOrderId;
    private String purchaseOrderNo;

    /** 付款金额 */
    private BigDecimal amount;

    /** 付款方式: 银行转账/现金/支票/汇票 */
    private String paymentMethod;

    /** 银行账号 */
    private String bankAccount;

    /** 交易流水号 */
    private String transactionNo;

    /** 付款人 */
    private String paidBy;

    /** 确认人 */
    private String confirmedBy;

    /** 确认日期 */
    private LocalDate confirmedDate;

    /** 状态: 待确认/已确认/已作废 */
    private String status;

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
