package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@TableName("sale_delivery")
public class SaleDelivery {

    @TableId(value = "delivery_id", type = IdType.AUTO)
    @ExportField(label = "出库单ID", order = 0, hidden = true)
    private Long deliveryId;

    @TableField("delivery_no")
    @ExportField(label = "出库单号", order = 1, required = true)
    private String deliveryNo;

    @TableField("delivery_date")
    @ExportField(label = "出库日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate deliveryDate;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 3, required = true)
    private Long customerId;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 4)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 5)
    private String contactPhone;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 6)
    private Long warehouseId;

    @TableField("saler_id")
    @ExportField(label = "销售员ID", order = 7)
    private Long salerId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 8, required = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 9, numberFormat = "0.0000")
    private java.math.BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "总金额", order = 10, numberFormat = "0.00")
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

    /** 付款条件ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("payment_term_id")
    @ExportField(label = "付款条件", order = 42)
    private Long paymentTermId;

    /** 价格等级ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("price_level_id")
    @ExportField(label = "价格等级", order = 43)
    private Long priceLevelId;

    /** 运费（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("freight")
    @ExportField(label = "运费", order = 44, numberFormat = "0.00")
    private BigDecimal freight;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 15)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 16)
    private String sourceBillNo;

    @TableField("opportunity_id")
    @ExportField(label = "商机ID", order = 40, hidden = true)
    private Long opportunityId;

    @TableField("opportunity_no")
    @ExportField(label = "商机编号", order = 41)
    private String opportunityNo;

    @TableField("order_no")
    @ExportField(label = "订单号", order = 17)
    private String orderNo;

    @TableField("invoice_no")
    @ExportField(label = "发票号", order = 18)
    private String invoiceNo;

    @TableField("express_company")
    @ExportField(label = "快递公司", order = 19)
    private String expressCompany;

    @TableField("express_no")
    @ExportField(label = "快递单号", order = 20)
    private String expressNo;

    @TableField("contact_address")
    @ExportField(label = "联系地址", order = 21)
    private String contactAddress;

    @TableField("remark")
    @ExportField(label = "备注", order = 22)
    private String remark;

    @TableField("attachment_urls")
    @ExportField(label = "附件", order = 23, hidden = true)
    private String attachmentUrls;

    /** 结算方式ID（基础资料-结算方式）：本表原先无此列，前端下拉选了不落库（2026-09-20 补列） */
    @TableField("settle_mode_id")
    private Long settleModeId;

    @TableField("status")
    @ExportField(label = "状态", order = 24)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 25)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 26)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 27, dateFormat = "yyyy-MM-dd HH:mm:ss")
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

    @TableField("carrier")
    @ExportField(label = "承运人", order = 28)
    private String carrier;

    @TableField("tracking_no")
    @ExportField(label = "跟踪号", order = 29)
    private String trackingNo;

    @TableField("base_total_amount")
    private BigDecimal baseTotalAmount;

    @TableField("base_tax_amount")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private BigDecimal baseNetAmount;

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

    /** 预付款比例(%)（出库约定预付比例 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_rate")
    private BigDecimal prepaidRate;

    /** 预付款金额（按出库金额×预付比例折算 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_amount")
    private BigDecimal prepaidAmount;

    /** 发货收款管控模式（随来源销售订单带出，单据上只读）：none/quota/full/advance（等价脚本 deploy/sql/168_delivery_control_on_bill.sql） */
    @TableField("payment_control")
    private String paymentControl;

    /** 销售出库单明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<SaleDeliveryItem> items;

    /** 客户名称(非数据库字段) */
    @TableField(exist = false)
    private String customerName;

    /** 销售员名称(非数据库字段) */
    @TableField(exist = false)
    private String salerName;

    /** 币种代码(非数据库字段) */
    @TableField(exist = false)
    private String currencyCode;

    /** 仓库名称(非数据库字段) */
    @TableField(exist = false)
    private String warehouseName;
}
