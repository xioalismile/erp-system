package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sale_contract")
public class SaleContract {

    @TableId(value = "contract_id", type = IdType.AUTO)
    @ExportField(label = "合同ID", order = 0, hidden = true)
    private Long contractId;

    @TableField("contract_no")
    @ExportField(label = "合同号", order = 1, required = true)
    private String contractNo;

    @TableField("contract_date")
    @ExportField(label = "合同日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate contractDate;

    @TableField("contract_type")
    @ExportField(label = "合同类型", order = 3)
    private String contractType;

    @TableField("validity_start")
    @ExportField(label = "有效期起", order = 4, dateFormat = "yyyy-MM-dd")
    private LocalDate validityStart;

    @TableField("validity_end")
    @ExportField(label = "有效期止", order = 5, dateFormat = "yyyy-MM-dd")
    private LocalDate validityEnd;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 6, required = true)
    private Long customerId;

    @TableField("customer_no")
    @ExportField(label = "客户编码", order = 7)
    private String customerNo;

    @TableField("customer_short_name")
    @ExportField(label = "客户简称", order = 8)
    private String customerShortName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 9)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 10)
    private String contactPhone;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 11)
    private Long departmentId;

    @TableField("saler_id")
    @ExportField(label = "销售员ID", order = 12)
    private Long salerId;

    @TableField("price_level_id")
    @ExportField(label = "价格等级ID", order = 13)
    private Long priceLevelId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 14, required = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 15, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "合同金额", order = 16, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 17, numberFormat = "0.00")
    private BigDecimal taxAmount;

    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 18, numberFormat = "0.00")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    @ExportField(label = "折扣金额", order = 19, numberFormat = "0.00")
    private BigDecimal discountAmount;

    @TableField("net_amount")
    @ExportField(label = "净额", order = 20, numberFormat = "0.00")
    private BigDecimal netAmount;

    @TableField("delivery_date")
    @ExportField(label = "交货日期", order = 21, dateFormat = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    @TableField("payment_term_id")
    @ExportField(label = "付款条件ID", order = 22)
    private Long paymentTermId;

    @TableField("payment_method")
    @ExportField(label = "付款方式", order = 23)
    private String paymentMethod;

    @TableField("deposit_amount")
    @ExportField(label = "订金金额", order = 24, numberFormat = "0.00")
    private BigDecimal depositAmount;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 25)
    private Long warehouseId;

    @TableField("receive_address")
    @ExportField(label = "收货地址", order = 26)
    private String receiveAddress;

    @TableField("transport_method")
    @ExportField(label = "运输方式", order = 27)
    private String transportMethod;

    @TableField("is_tax")
    @ExportField(label = "是否含税", order = 28)
    private String isTax;

    @TableField("tax_calc_method")
    @ExportField(label = "计税方式", order = 29)
    private String taxCalcMethod;

    @TableField("is_invoiced")
    @ExportField(label = "是否开票", order = 30)
    private String isInvoiced;

    @TableField("logistics_company")
    @ExportField(label = "物流公司", order = 31)
    private String logisticsCompany;

    @TableField("logistics_no")
    @ExportField(label = "物流单号", order = 32)
    private String logisticsNo;

    @TableField("invoice_type")
    @ExportField(label = "发票类型", order = 33)
    private String invoiceType;

    @TableField("project_no")
    @ExportField(label = "项目号", order = 34)
    private String projectNo;

    @TableField("customer_order_no")
    @ExportField(label = "客户订单号", order = 35)
    private String customerOrderNo;

    /** 是否代发货（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("is_consignment")
    @ExportField(label = "是否代发货", order = 44)
    private String isConsignment;

    /** 拣货人（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("picker_id")
    @ExportField(label = "拣货人", order = 45)
    private Long pickerId;

    @TableField("opportunity_id")
    @ExportField(label = "商机ID", order = 40, hidden = true)
    private Long opportunityId;

    @TableField("opportunity_no")
    @ExportField(label = "商机编号", order = 41)
    private String opportunityNo;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 42)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 43)
    private String sourceBillNo;

    @TableField("remark")
    @ExportField(label = "备注", order = 36)
    private String remark;

    @TableField("attachment_urls")
    @ExportField(label = "附件", order = 37, hidden = true)
    private String attachmentUrls;

    @TableField("status")
    @ExportField(label = "状态", order = 38)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 39)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 40)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 41, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("close_status")
    @ExportField(label = "关闭状态", order = 42)
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

    /** 销售合同明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<SaleContractItem> items;

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
