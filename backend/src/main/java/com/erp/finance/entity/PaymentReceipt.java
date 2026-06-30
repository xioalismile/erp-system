package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 收款记录 - 销售收款
 */
@Data
@TableName("payment_receipt")
public class PaymentReceipt {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收款单号: REC+年月日+4位流水 */
    private String receiptNo;

    /** 收款日期 */
    private LocalDate receiptDate;

    /** 关联应收单ID */
    private Long arId;
    private String arNo;

    /** 客户ID */
    private Long customerId;
    private String customerCode;
    private String customerName;

    /** 销售订单ID */
    private Long saleOrderId;
    private String saleOrderNo;

    /** 收款金额 */
    private BigDecimal amount;

    /** 收款方式: 银行转账/现金/支票/汇票 */
    private String paymentMethod;

    /** 银行账号 */
    private String bankAccount;

    /** 交易流水号 */
    private String transactionNo;

    /** 收款人 */
    private String receivedBy;

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
