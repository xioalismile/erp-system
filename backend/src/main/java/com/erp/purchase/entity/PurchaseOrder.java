package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(value = "order_id", type = IdType.AUTO)
    @ExportField(label = "订单ID", order = 0, hidden = true)
    private Long orderId;

    @TableField("order_no")
    @ExportField(label = "订单号", order = 1, required = true)
    private String orderNo;

    @TableField("order_date")
    @ExportField(label = "订单日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate orderDate;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 3, required = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商名称", order = 4)
    private String supplierName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 5)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 6)
    private String contactPhone;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 7)
    private Long departmentId;

    @TableField("dept_name")
    @ExportField(label = "部门名称", order = 8)
    private String deptName;

    @TableField("buyer_id")
    @ExportField(label = "采购员ID", order = 9)
    private Long buyerId;

    @TableField("buyer_name")
    @ExportField(label = "采购员", order = 10)
    private String buyerName;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 11, required = true)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 12, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField("tax_rate")
    @ExportField(label = "税率", order = 13, numberFormat = "0.00")
    private BigDecimal taxRate;

    @TableField("total_amount")
    @ExportField(label = "订单金额", order = 14, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("tax_amount")
    @ExportField(label = "税额", order = 15, numberFormat = "0.00")
    private BigDecimal taxAmount;

    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 16, numberFormat = "0.00")
    private BigDecimal discountRate;

    @TableField("discount_amount")
    @ExportField(label = "折扣金额", order = 17, numberFormat = "0.00")
    private BigDecimal discountAmount;

    @TableField("net_amount")
    @ExportField(label = "净额", order = 18, numberFormat = "0.00")
    private BigDecimal netAmount;

    @TableField("prepayment_amount")
    @ExportField(label = "预付金额", order = 19, numberFormat = "0.00")
    private BigDecimal prepaymentAmount;

    /** 预付款账户（基础资料-银行账户），采购订单审核自动生成采购预付款单时带入 */
    @TableField("account_id")
    private Long accountId;

    @TableField("account_name")
    private String accountName;

    @TableField("delivery_date")
    @ExportField(label = "交货日期", order = 20, dateFormat = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    @TableField("delivery_place")
    @ExportField(label = "交货地点", order = 21)
    private String deliveryPlace;

    @TableField("payment_term_id")
    @ExportField(label = "付款条件ID", order = 22)
    private Long paymentTermId;

    @TableField("source_bill_type")
    @ExportField(label = "源单据类型", order = 23)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单据号", order = 24)
    private String sourceBillNo;

    @TableField("request_no")
    @ExportField(label = "申请单号", order = 25)
    private String requestNo;

    @TableField("remark")
    @ExportField(label = "备注", order = 26)
    private String remark;

    @TableField("attachment_urls")
    @ExportField(label = "附件", order = 27, hidden = true)
    private String attachmentUrls;

    /** 结算方式ID（基础资料-结算方式）：本表原先无此列，前端下拉选了不落库（2026-09-20 补列） */
    @TableField("settle_mode_id")
    private Long settleModeId;

    @TableField("status")
    @ExportField(label = "状态", order = 28)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 29)
    private String approvalStatus;

    @TableField("is_closed")
    @ExportField(label = "是否关闭", order = 30)
    private Integer isClosed;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 31)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 32, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("close_status")
    @ExportField(label = "关闭状态", order = 33)
    private Integer closeStatus;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

    @TableField("closed_by")
    @ExportField(label = "关闭人", order = 34, hidden = true)
    private String closedBy;

    @TableField("closed_at")
    @ExportField(label = "关闭时间", order = 35, hidden = true)
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    @ExportField(label = "关闭原因", order = 36)
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

    /** 表头「计税方式」（含税价/不含税价/不计税）：行级口径，表头值仅作新增行默认值；映射数据库列 tax_calc_method */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

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

    /** 预付款比例(%)（订单约定预付比例，金额复用已有 prepayment_amount + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("prepaid_rate")
    private BigDecimal prepaidRate;

    /** 采购订单明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<PurchaseOrderItem> items;

    /** 供应商编码(非数据库字段) */
    @TableField(exist = false)
    private String supplierCode;

    /** 币种代码(非数据库字段) */
    @TableField(exist = false)
    private String currencyCode;

    /** 付款条件名称(非数据库字段) */
    @TableField(exist = false)
    private String paymentTermName;

    /**
     * 入库状态(非数据库字段，仅列表展示用)：未入库 / 部分入库 / 已入库。
     * 由采购订单分页查询按明细已入库数量(received_quantity)与订单数量(quantity)批量聚合回填，
     * 口径与 {@link PurchaseOrderService#queryItemExecutionStatus(Long)} 的"执行完毕"判定一致。
     */
    @TableField(exist = false)
    private String inboundStatus;
}
