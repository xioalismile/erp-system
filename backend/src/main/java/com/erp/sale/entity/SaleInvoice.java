package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@TableName("sale_invoice")
public class SaleInvoice {

    @TableId(value = "invoice_id", type = IdType.AUTO)
    @ExportField(label = "发票ID", order = 0, hidden = true)
    private Long invoiceId;

    @TableField("invoice_no")
    @ExportField(label = "发票号", order = 1, required = true)
    private String invoiceNo;

    @TableField("invoice_date")
    @ExportField(label = "开票日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate invoiceDate;

    @TableField("invoice_type")
    @ExportField(label = "发票类型", order = 3)
    private String invoiceType;

    /** 税票号码（2026-10-13 新增）：税控开票软件开出的真实发票号码，与系统单号 invoiceNo 相互独立；
     *  复制单不继承（复制=开新票），下推不携带（每张税票号唯一） */
    @TableField("tax_invoice_no")
    @ExportField(label = "税票号码", order = 42)
    private String taxInvoiceNo;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 4, required = true)
    private Long customerId;

    @TableField("tax_no")
    @ExportField(label = "税号", order = 5)
    private String taxNo;

    @TableField("address")
    @ExportField(label = "地址", order = 6)
    private String address;

    @TableField("bank_name")
    @ExportField(label = "开户银行", order = 7)
    private String bankName;

    @TableField("bank_account")
    @ExportField(label = "银行账号", order = 8)
    private String bankAccount;

    @TableField("saler_id")
    @ExportField(label = "销售员ID", order = 9)
    private Long salerId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 10, required = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 11, numberFormat = "0.0000")
    private java.math.BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "总金额", order = 12, numberFormat = "0.00")
    private java.math.BigDecimal totalAmount;

    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 13, numberFormat = "0.00")
    private java.math.BigDecimal discountRate;

    @TableField("discount_amount")
    @ExportField(label = "折扣金额", order = 14, numberFormat = "0.00")
    private java.math.BigDecimal discountAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 15, numberFormat = "0.00")
    private java.math.BigDecimal taxAmount;

    @TableField("net_amount")
    @ExportField(label = "净额", order = 16, numberFormat = "0.00")
    private java.math.BigDecimal netAmount;

    /** 表头「计税方式」（含税价/不含税价/不计税）：映射数据库列 tax_calc_method，随单据保存/回读并驱动明细换算 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    /** 付款条件ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("payment_term_id")
    @ExportField(label = "付款条件", order = 28)
    private Long paymentTermId;

    /** 价格等级ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("price_level_id")
    @ExportField(label = "价格等级", order = 29)
    private Long priceLevelId;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 17)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 18)
    private String sourceBillNo;

    @TableField("opportunity_id")
    @ExportField(label = "商机ID", order = 40, hidden = true)
    private Long opportunityId;

    @TableField("opportunity_no")
    @ExportField(label = "商机编号", order = 41)
    private String opportunityNo;

    @TableField("delivery_no")
    @ExportField(label = "出库单号", order = 19)
    private String deliveryNo;

    @TableField("tax_rate_avg")
    @ExportField(label = "平均税率", order = 20, numberFormat = "0.00")
    private java.math.BigDecimal taxRateAvg;

    @TableField("remark")
    @ExportField(label = "备注", order = 21)
    private String remark;

    @TableField("attachment_urls")
    @ExportField(label = "附件", order = 22, hidden = true)
    private String attachmentUrls;

    /** 结算方式ID（基础资料-结算方式）：本表原先无此列，前端下拉选了不落库（2026-09-20 补列） */
    @TableField("settle_mode_id")
    private Long settleModeId;

    @TableField("status")
    @ExportField(label = "状态", order = 23)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 24)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 25)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 26, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime approvedAt;

    /** 结案状态：0=未结案 1=已结案（结案后 status 保持已生效，前端按此字段渲染已结案） */
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private java.time.LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField("ar_no")
    @ExportField(label = "应收单号", order = 27)
    private String arNo;

    @TableField("generate_ar")
    private Short generateAr;

    /** 是否红字发票: 0-蓝字(正常), 1-红字(退货冲减) */
    @TableField("is_red_invoice")
    private Short isRedInvoice;

    /** 原蓝字发票ID（红字发票专用） */
    @TableField("original_invoice_id")
    private Long originalInvoiceId;

    /** 原蓝字发票号（红字发票专用） */
    @TableField("original_invoice_no")
    private String originalInvoiceNo;

    /** 销售发票明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<SaleInvoiceItem> items;

    /** 客户名称(非数据库字段) */
    @TableField(exist = false)
    private String customerName;

    /** 销售员名称(非数据库字段) */
    @TableField(exist = false)
    private String salerName;

    /** 币种代码(非数据库字段) */
    @TableField(exist = false)
    private String currencyCode;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private java.time.LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private java.time.LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;

    /** 预付款比例(%)（开票约定预付比例 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_rate")
    private java.math.BigDecimal prepaidRate;

    /** 预付款金额（按发票金额×预付比例折算 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_amount")
    private java.math.BigDecimal prepaidAmount;
}
