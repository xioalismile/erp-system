package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 销售换货单：用替换货品代替原货品发出，数据回写销售订单原货品行。
 * 来源 = 销售订单，由销售订单「销售替代出库」按钮生成。
 */
@Data
@TableName("sale_exchange")
public class SaleExchange {

    @TableId(value = "exchange_id", type = IdType.AUTO)
    private Long exchangeId;

    @TableField("exchange_no")
    @ExportField(label = "换货单号", order = 1, required = true)
    private String exchangeNo;

    @TableField("exchange_date")
    @ExportField(label = "换货日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate exchangeDate;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 3, required = true)
    private Long customerId;

    @TableField("customer_name")
    @ExportField(label = "客户", order = 4)
    private String customerName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 5)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 6)
    private String contactPhone;

    @TableField("saler_id")
    @ExportField(label = "销售员ID", order = 7)
    private Long salerId;

    @TableField("saler_name")
    @ExportField(label = "销售员", order = 8)
    private String salerName;

    @TableField("dept_id")
    private Long deptId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 9)
    private Long currencyId;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 10, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField("tax_calc_method")
    private String taxCalcMethod;

    @TableField("source_bill_type")
    @ExportField(label = "来源单据类型", order = 11)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "来源订单号", order = 12)
    private String sourceBillNo;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("delivery_address")
    private String deliveryAddress;

    @TableField("remark")
    @ExportField(label = "备注", order = 20)
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField("total_quantity")
    @ExportField(label = "数量合计", order = 13, numberFormat = "0.####")
    private BigDecimal totalQuantity;

    @TableField("total_amount")
    @ExportField(label = "金额", order = 14, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 15, numberFormat = "0.00")
    private BigDecimal taxAmount;

    @TableField("discount_amount")
    @ExportField(label = "折扣额", order = 16, numberFormat = "0.00")
    private BigDecimal discountAmount;

    @TableField("net_amount")
    @ExportField(label = "价税合计", order = 17, numberFormat = "0.00")
    private BigDecimal netAmount;

    @TableField("generated_delivery_id")
    private Long generatedDeliveryId;

    @TableField("generated_delivery_no")
    @ExportField(label = "已生成出库单", order = 18)
    private String generatedDeliveryNo;

    @TableField("status")
    @ExportField(label = "状态", order = 21)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 22)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 23)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 24, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField("org_code")
    private String orgCode;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 明细（非数据库字段） */
    @TableField(exist = false)
    private List<SaleExchangeItem> items;

    /**
     * 表头自定义字段值（非数据库字段）：键=字段标识，值=字段值。
     * 与物料档案同一承载方式，保存时由销售换货单服务写入自定义字段值表，详情时回填。
     */
    @TableField(exist = false)
    private Map<String, Object> customFieldValues;
}
