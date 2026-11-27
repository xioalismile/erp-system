package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@TableName("sale_quote")
public class SaleQuote {

    @TableId(value = "quote_id", type = IdType.AUTO)
    @ExportField(label = "报价单ID", order = 0, hidden = true)
    private Long quoteId;

    @TableField("quote_no")
    @ExportField(label = "报价单号", order = 1, required = true)
    private String quoteNo;

    @TableField("quote_date")
    @ExportField(label = "报价日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate quoteDate;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 3, required = true)
    private Long customerId;

    /** 客户编号（2026-11-08 补列：前端一直有该输入框，但实体与数据库都没有该字段，保存即丢） */
    @TableField("customer_no")
    @ExportField(label = "客户编号", order = 41)
    private String customerNo;

    /** 客户简称（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("customer_short_name")
    @ExportField(label = "客户简称", order = 42)
    private String customerShortName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 4)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 5)
    private String contactPhone;

    @TableField("saler_id")
    @ExportField(label = "销售员ID", order = 6)
    private Long salerId;

    @TableField("price_level_id")
    @ExportField(label = "价格等级ID", order = 7)
    private Long priceLevelId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 8, required = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 9, numberFormat = "0.0000")
    private java.math.BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "报价金额", order = 10, numberFormat = "0.00")
    private java.math.BigDecimal totalAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 11, numberFormat = "0.00")
    private java.math.BigDecimal taxAmount;

    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 12, numberFormat = "0.00")
    private java.math.BigDecimal discountRate;

    @TableField("discount_amount")
    @ExportField(label = "折扣金额", order = 13, numberFormat = "0.00")
    private java.math.BigDecimal discountAmount;

    @TableField("net_amount")
    @ExportField(label = "净额", order = 14, numberFormat = "0.00")
    private java.math.BigDecimal netAmount;

    /** 表头「计税方式」（含税价/不含税价/不计税）：映射数据库列 tax_calc_method，随单据保存/回读并驱动明细换算 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    @TableField("valid_from")
    @ExportField(label = "有效期起", order = 15, dateFormat = "yyyy-MM-dd")
    private LocalDate validFrom;

    @TableField("valid_to")
    @ExportField(label = "有效期止", order = 16, dateFormat = "yyyy-MM-dd")
    private LocalDate validTo;

    @TableField("delivery_date")
    @ExportField(label = "交货日期", order = 17, dateFormat = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    @TableField("payment_term_id")
    @ExportField(label = "付款条件ID", order = 18)
    private Long paymentTermId;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
    private String remark;

    @TableField("attachment_urls")
    @ExportField(label = "附件", order = 20, hidden = true)
    private String attachmentUrls;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 21)
    private String sourceBillNo;

    @TableField("opportunity_id")
    @ExportField(label = "商机ID", order = 40, hidden = true)
    private Long opportunityId;

    @TableField("opportunity_no")
    @ExportField(label = "商机编号", order = 41)
    private String opportunityNo;

    /** 结算方式ID（基础资料-结算方式）：本表原先无此列，前端下拉选了不落库（2026-09-20 补列） */
    @TableField("settle_mode_id")
    private Long settleModeId;

    @TableField("status")
    @ExportField(label = "状态", order = 22)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 23)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 24)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 25, dateFormat = "yyyy-MM-dd HH:mm:ss")
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

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

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

    /** 收货地址（报价单送货地址 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("delivery_address")
    private String deliveryAddress;

    /** 预付款比例(%)（报价单约定预付比例 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_rate")
    private java.math.BigDecimal prepaidRate;

    /** 预付款金额（按报价金额×预付比例折算 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_amount")
    private java.math.BigDecimal prepaidAmount;

    /** 销售报价单明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<SaleQuoteItem> items;

    /** 客户名称(非数据库字段) */
    @TableField(exist = false)
    private String customerName;

    /** 销售员名称(非数据库字段) */
    @TableField(exist = false)
    private String salerName;

    /** 币种代码(非数据库字段) */
    @TableField(exist = false)
    private String currencyCode;

    /** 付款条件名称(非数据库字段) */
    @TableField(exist = false)
    private String paymentTermName;
}
