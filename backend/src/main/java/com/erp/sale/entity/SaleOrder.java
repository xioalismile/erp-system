package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sale_order")
public class SaleOrder {

    @TableId(value = "order_id", type = IdType.AUTO)
    @ExportField(label = "订单ID", order = 0, hidden = true)
    private Long orderId;

    @TableField("order_no")
    @ExportField(label = "订单号", order = 1, required = true)
    private String orderNo;

    @TableField("order_date")
    @ExportField(label = "订单日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate orderDate;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 3, required = true)
    private Long customerId;

    @TableField("customer_no")
    @ExportField(label = "客户编码", order = 4)
    private String customerNo;

    @TableField("customer_short_name")
    @ExportField(label = "客户简称", order = 5)
    private String customerShortName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 6)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 7)
    private String contactPhone;

    @TableField("saler_id")
    @ExportField(label = "销售员ID", order = 8)
    private Long salerId;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 9)
    private Long departmentId;

    @TableField("price_level_id")
    @ExportField(label = "价格等级ID", order = 10)
    private Long priceLevelId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 11, required = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 12, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "订单金额", order = 13, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 14, numberFormat = "0.00")
    private BigDecimal taxAmount;

    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 15, numberFormat = "0.00")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    @ExportField(label = "折扣金额", order = 16, numberFormat = "0.00")
    private BigDecimal discountAmount;

    @TableField("net_amount")
    @ExportField(label = "净额", order = 17, numberFormat = "0.00")
    private BigDecimal netAmount;

    @TableField("delivery_date")
    @ExportField(label = "交货日期", order = 18, dateFormat = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    /** 超量发货比例(%)：10=10%；最高发货=订单数量×(1+比例/100)；0=不允许超量 */
    @TableField("overship_ratio")
    @ExportField(label = "超量发货比例(%)", order = 93, numberFormat = "0.00")
    private BigDecimal overshipRatio;

    @TableField("payment_term_id")
    @ExportField(label = "付款条件ID", order = 19)
    private Long paymentTermId;

    @TableField("payment_method")
    @ExportField(label = "付款方式", order = 20)
    private String paymentMethod;

    @TableField("deposit_amount")
    @ExportField(label = "订金金额", order = 21, numberFormat = "0.00")
    private BigDecimal depositAmount;

    /** 定金收款账户（基础资料-银行账户），订单审核自动生成销售预收款单时带入 */
    @TableField("account_id")
    private Long accountId;

    @TableField("account_name")
    private String accountName;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 22)
    private Long warehouseId;

    @TableField("receive_address")
    @ExportField(label = "收货地址", order = 23)
    private String receiveAddress;

    @TableField("transport_method")
    @ExportField(label = "运输方式", order = 24)
    private String transportMethod;

    @TableField("is_tax")
    @ExportField(label = "是否含税", order = 25)
    private String isTax;

    @TableField("tax_calc_method")
    @ExportField(label = "计税方式", order = 26)
    private String taxCalcMethod;

    @TableField("is_invoiced")
    @ExportField(label = "是否开票", order = 27)
    private String isInvoiced;

    @TableField("is_sample")
    @ExportField(label = "是否样品", order = 28)
    private String isSample;

    @TableField("is_consignment")
    @ExportField(label = "是否托售", order = 29)
    private String isConsignment;

    @TableField("logistics_company")
    @ExportField(label = "物流公司", order = 30)
    private String logisticsCompany;

    @TableField("logistics_no")
    @ExportField(label = "物流单号", order = 31)
    private String logisticsNo;

    @TableField("picker_id")
    @ExportField(label = "拣货员ID", order = 32)
    private Long pickerId;

    @TableField("invoice_type")
    @ExportField(label = "发票类型", order = 33)
    private String invoiceType;

    @TableField("project_no")
    @ExportField(label = "项目号", order = 34)
    private String projectNo;

    @TableField("customer_order_no")
    @ExportField(label = "客户订单号", order = 35)
    private String customerOrderNo;

    @TableField("business_type")
    @ExportField(label = "业务类型", order = 36)
    private String businessType;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 37)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 38)
    private String sourceBillNo;

    @TableField("opportunity_id")
    @ExportField(label = "商机ID", order = 39, hidden = true)
    private Long opportunityId;

    @TableField("opportunity_no")
    @ExportField(label = "商机编号", order = 40)
    private String opportunityNo;

    @TableField("quote_no")
    @ExportField(label = "报价单号", order = 39)
    private String quoteNo;

    @TableField("remark")
    @ExportField(label = "备注", order = 40)
    private String remark;

    @TableField("attachment_urls")
    @ExportField(label = "附件", order = 41, hidden = true)
    private String attachmentUrls;

    @TableField("status")
    @ExportField(label = "状态", order = 42)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 43)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 44)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 45, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("close_status")
    @ExportField(label = "关闭状态", order = 46)
    private Integer closeStatus;

    @TableField("closed_by")
    @ExportField(label = "关闭人", order = 47, hidden = true)
    private String closedBy;

    @TableField("closed_at")
    @ExportField(label = "关闭时间", order = 48, hidden = true)
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    @ExportField(label = "关闭原因", order = 49)
    private String closedReason;

    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField("base_total_amount")
    private BigDecimal baseTotalAmount;

    @TableField("base_tax_amount")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private BigDecimal baseNetAmount;

    /** 已收款金额（预收款+正常收款累计） */
    @TableField("received_amount")
    @ExportField(label = "已收款", order = 50)
    private BigDecimal receivedAmount;

    /** 发货收款管控模式（源头订单选定）：none/quota/full/advance；空=按设置中心默认（等价脚本 deploy/sql/168_delivery_control_on_bill.sql） */
    @TableField("payment_control")
    private String paymentControl;

    /** 超期账款管控开关：true=本单提交时客户有超期未收款则阻断；空=按设置中心默认 */
    @TableField("overdue_block")
    private String overdueBlock;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;

    /** 销售订单明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<SaleOrderItem> items;

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

    // ===== 替换货（2026-11-17）：销售订单页「销售替代出库」按钮的显隐依据 =====
    /** 是否存在已生效的【普通】销售出库单（排除换货生成的出库单） */
    @TableField(exist = false)
    private Boolean hasEffectiveDelivery;

    /** 是否被人工「手动结案」（自动结案不影响换货入口） */
    @TableField(exist = false)
    private Boolean manualClosed;

    /** 是否还有替换发货待客户回库（P > 0） */
    @TableField(exist = false)
    private Boolean hasPendingReturn;

    /** 剩余可替换数量合计（=0 表示替换发货已完成，「销售替代出库」入口不再出现） */
    @TableField(exist = false)
    private java.math.BigDecimal replaceableTotalQuantity;
}
