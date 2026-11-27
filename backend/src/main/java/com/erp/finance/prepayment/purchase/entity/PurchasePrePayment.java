package com.erp.finance.prepayment.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("purchase_pre_payment")
public class PurchasePrePayment {

    @TableId(value = "id", type = IdType.AUTO)
    @ExportField(label = "预付款ID", order = 1, hidden = true)
    private Long id;

    @TableField("payment_no")
    @ExportField(label = "预付款单号", order = 2, required = true)
    private String paymentNo;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 3, hidden = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商名称", order = 4, required = true)
    private String supplierName;

    /** 付款人（员工档案ID，业务口径「付款人」），用于列表按付款人筛选 */
    @TableField("cashier_id")
    private Long cashierId;

    @TableField("cashier_name")
    private String cashierName;

    @TableField("order_id")
    @ExportField(label = "订单ID", order = 5, hidden = true)
    private Long orderId;

    @TableField("order_no")
    @ExportField(label = "订单编号", order = 6)
    private String orderNo;

    @TableField("payment_date")
    @ExportField(label = "付款日期", order = 7, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate paymentDate;

    @TableField("amount")
    @ExportField(label = "预付金额", order = 8, numberFormat = "0.00", required = true)
    private BigDecimal amount;

    @TableField("base_amount")
    @ExportField(label = "本位币金额", order = 9, numberFormat = "0.00")
    private BigDecimal baseAmount;

    @TableField("currency_code")
    @ExportField(label = "币种编码", order = 10)
    private String currencyCode;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 11, numberFormat = "0.00")
    private BigDecimal exchangeRate;

    @TableField("payment_method")
    @ExportField(label = "付款方式", order = 12)
    private String paymentMethod;

    @TableField("account_id")
    @ExportField(label = "账户ID", order = 13, hidden = true)
    private Long accountId;

    @TableField("account_name")
    @ExportField(label = "账户名称", order = 14)
    private String accountName;

    @TableField("status")
    @ExportField(label = "状态", order = 15, hidden = true)
    private Integer status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    /** 审核人（统一状态机审核通过时写入） */
    @TableField("approved_by")
    private String approvedBy;

    /** 审核时间 */
    @TableField("approved_at")
    private LocalDateTime approvedAt;

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("used_amount")
    @ExportField(label = "已用金额", order = 16, numberFormat = "0.00")
    private BigDecimal usedAmount;

    @TableField("remain_amount")
    @ExportField(label = "剩余金额", order = 17, numberFormat = "0.00")
    private BigDecimal remainAmount;

    /** 结案状态：0=未结案 1=已结案（结案后 status 保持已生效，前端按此字段渲染已结案） */
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    @TableField("voided_by")
    private String voidedBy;
    @TableField("voided_at")
    private LocalDateTime voidedAt;
    @TableField("void_reason")
    private String voidReason;

    /** 来源标记：PURCHASE_ORDER_PREPAY=采购订单预付自动生成（人工录入为空），用于反审回收与追踪 */
    @TableField("source_type")
    private String sourceType;

    /** 来源单据ID（如付款单多付自动转入时存付款单ID），用于幂等与回滚 */
    @TableField("source_bill_id")
    private Long sourceBillId;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField(exist = false)
    private List<PurchasePrePaymentItem> items;
}
