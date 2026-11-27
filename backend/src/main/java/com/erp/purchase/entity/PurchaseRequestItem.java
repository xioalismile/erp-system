package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购申请单明细(对应 purchase_request_item 表)
 * 文档3.1 表体字段(15个)
 */
@Data
@TableName("purchase_request_item")
public class PurchaseRequestItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "request_item_id", type = IdType.AUTO)
    private Long requestItemId;

    @TableField("request_id")
    private Long requestId;

    /** 行号 FRowNo, 从1开始, 步长1 */
    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    /** 规格型号 FModel */
    @TableField("specification")
    private String model;

    @TableField("unit_id")
    private Long unitId;

    /** 申请数量 FApplyQty, 必须大于0 */
    @TableField("quantity")
    private BigDecimal applyQty;

    /** 审批数量 FApprovedQty, 不可超过申请数量 */
    @TableField("approved_qty")
    private BigDecimal approvedQty;

    /** 建议供应商 FSuggestedSupplierID */
    @TableField("suggested_supplier_id")
    private Long suggestedSupplierId;

    /** 建议供应商名称 FSuggestedSupplierName */
    @TableField("suggested_supplier_name")
    private String suggestedSupplierName;

    /** 源单类型 FSourceType */
    @TableField("source_type")
    private String sourceType;

    /** 源单编号 FSourceBillNo */
    @TableField("source_bill_no")
    private String sourceBillNo;

    /** 源单类型（标准命名） */
    @TableField("source_bill_type")
    private String sourceBillType;

    /** 源单主表ID */
    @TableField("source_doc_id")
    private Long sourceDocId;

    /** 源单行号 FSourceRowNo */
    @TableField("source_row_no")
    private Integer sourceRowNo;

    /** 需求日期 FDemandDate */
    @TableField("demand_date")
    private LocalDate demandDate;

    /** 行备注 FRowRemark */
    @TableField("remark")
    private String rowRemark;

    /** 批次号（明细批次管理必填 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("batch_no")
    private String batchNo;

    /** 生产日期（批次物料生产日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("production_date")
    private LocalDate productionDate;

    /** 有效期至（批次物料失效日期 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
