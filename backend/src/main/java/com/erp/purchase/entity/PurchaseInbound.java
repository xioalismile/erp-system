package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购入库单(对应 purchase_receipt 表)
 * 文档3.3 表头字段(24个)
 * 单据状态流转: 草稿 → 待审核 → 已生效
 */
@Data
@TableName("purchase_receipt")
public class PurchaseInbound {

    @TableId(value = "receipt_id", type = IdType.AUTO)
    @ExportField(label = "入库单ID", order = 0, hidden = true)
    private Long receiptId;

    @TableField("receipt_no")
    @ExportField(label = "入库单号", order = 1, required = true)
    private String receiptNo;

    @TableField("receipt_date")
    @ExportField(label = "入库日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate inDate;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 3, required = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商名称", order = 4)
    private String supplierName;

    @TableField("buyer_id")
    @ExportField(label = "采购员ID", order = 5)
    private Long buyerId;

    @TableField("buyer_name")
    @ExportField(label = "采购员", order = 6)
    private String buyerName;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 7)
    private Long deptId;

    @TableField("dept_name")
    @ExportField(label = "部门名称", order = 8)
    private String deptName;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 9)
    private Long stockId;

    /** 质检开关（2026-11-07）：开启后必须先质检，按合格/不合格处理结果入库 */
    @TableField("qc_enabled")
    private Integer qcEnabled;

    @TableField("qc_template_id")
    private Long qcTemplateId;

    @TableField("qc_status")
    private String qcStatus;

    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 不合格金额（财务应付调整依据） */
    @TableField("rejected_amount")
    private BigDecimal rejectedAmount;

    /** 应付金额（退货处理时扣减不合格金额） */
    @TableField("payable_amount")
    private BigDecimal payableAmount;

    @TableField("stock_name")
    @ExportField(label = "仓库名称", order = 10)
    private String stockName;

    @TableField("source_type")
    @ExportField(label = "源单类型", order = 11)
    private String sourceType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 12)
    private String sourceBillNo;

    @TableField("total_amount")
    @ExportField(label = "入库总额", order = 13, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("status")
    @ExportField(label = "状态", order = 14)
    private String status;

    @TableField("delivery_no")
    @ExportField(label = "送货单号", order = 15)
    private String deliveryNo;

    @TableField("inspection_no")
    @ExportField(label = "检验单号", order = 16)
    private String inspectionNo;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 17)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核日期", order = 18, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
    private String remark;

    @TableField("is_lot_control")
    private Integer isLotControl;

    @TableField("is_closed")
    private Integer isClosed;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 审批状态标签（与 status 同源同步，统一状态机抽象方法要求） */
    @TableField("approval_status")
    private String approvalStatus;

    /** 作废前状态（数字code，供反作废恢复） */
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

    /** 税额（表头汇总，落库） */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 价税合计（表头汇总，落库） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 表头「计税方式」（含税价/不含税价/不计税）：映射数据库列 tax_calc_method，随单据保存/回读 */
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

    /** 收货地址（入库单收货地址 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("delivery_address")
    private String deliveryAddress;

    /** 期望交货日期（入库单约定到货日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** 联系人（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("contact_person")
    @ExportField(label = "联系人", order = 20)
    private String contactPerson;

    /** 联系电话（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 21)
    private String contactPhone;

    /** 付款方式（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("payment_method")
    @ExportField(label = "付款方式", order = 22)
    private String paymentMethod;

    /** 币种ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 23)
    private Long currencyId;

    /** 汇率（数据库已存在该列，此前实体未映射 → 前端填了不落库） */
    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 24, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField(exist = false)
    private List<PurchaseInboundItem> items;
}
