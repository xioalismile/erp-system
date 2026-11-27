package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购退货单(对应 purchase_return 表)
 * 文档3.4 表头字段(24个)
 * 单据状态流转: 草稿 → 待审核 → 已生效
 */
@Data
@TableName("purchase_return")
public class PurchaseReturn {

    @TableId(value = "return_id", type = IdType.AUTO)
    @ExportField(label = "退货单ID", order = 0, hidden = true)
    private Long returnId;

    @TableField("return_no")
    @ExportField(label = "退货单号", order = 1, required = true)
    private String returnNo;

    @TableField("return_date")
    @ExportField(label = "退货日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate returnDate;

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

    @TableField("stock_name")
    @ExportField(label = "仓库名称", order = 10)
    private String stockName;

    @TableField("source_bill_type")
    @ExportField(label = "源单类型", order = 11)
    private String sourceType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 12)
    private String sourceBillNo;

    @TableField("total_amount")
    @ExportField(label = "退货总额", order = 13, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("return_reason")
    @ExportField(label = "退货原因", order = 14)
    private String returnReason;

    @TableField("return_type")
    @ExportField(label = "退货类型", order = 15)
    private String returnType;

    @TableField("status")
    @ExportField(label = "状态", order = 16)
    private String status;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 17)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核日期", order = 18, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
    private String remark;

    @TableField("is_closed")
    private Integer isClosed;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("is_lot_control")
    @ExportField(label = "是否批次管理", order = 20)
    private Integer isLotControl;

    /** 库存影响：1=正常退货扣减库存；0=质检自动退货（不合格品从未入库，只做单据与财务冲减） */
    @TableField("stock_effect")
    private Integer stockEffect;

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

    /** 折扣金额（表头汇总，落库） */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

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

    /** 收货地址（退货收货地址 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("delivery_address")
    private String deliveryAddress;

    /** 联系人（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("contact_person")
    @ExportField(label = "联系人", order = 21)
    private String contactPerson;

    /** 联系电话（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 22)
    private String contactPhone;

    /** 币种ID（2026-09-28 补列：前端可选但此前实体与数据库都没有该字段） */
    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 23)
    private Long currencyId;

    /** 折扣率（2026-09-28 补列：前端可填但此前实体与数据库都没有该字段） */
    @TableField("discount_rate")
    @ExportField(label = "折扣率", order = 24, numberFormat = "0.00")
    private BigDecimal discountRate;

    /** 汇率（数据库已存在该列，此前实体未映射 → 前端填了不落库） */
    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 25, numberFormat = "0.0000")
    private BigDecimal exchangeRate;

    @TableField(exist = false)
    private List<PurchaseReturnItem> items;
}