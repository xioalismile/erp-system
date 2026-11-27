package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购退货单明细(对应 purchase_return_item 表)
 * 文档3.4 表体字段(16个)
 */
@Data
@TableName("purchase_return_item")
public class PurchaseReturnItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "return_item_id", type = IdType.AUTO)
    private Long returnItemId;

    @TableField("return_id")
    private Long returnId;

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

    /** 数量 FQty, 必须>0且≤可退数量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 退货出库实际成本（审核时冻结，反审按原成本加回库存） */
    @TableField("avg_cost")
    private BigDecimal avgCost;

    /** 单价 FPrice, 不含税, 从源单(采购入库单)带出 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 金额 FAmount, 自动计算: 数量 × 单价 */
    @TableField("amount")
    private BigDecimal amount;

    /** 税率(%)（行级「计税方式」参与含税价/不含税价换算） */
    @TableField("tax_rate")
    private BigDecimal taxRate;

    /** 税额（自动计算：折后金额 × 税率 / 100；不计税时为 0） */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 价税合计（自动计算：金额 + 税额） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 折扣率(%)（行级折扣，落库） */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 折扣额（自动计算：折前金额 × 折扣率 / 100，落库） */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /** 含税价（按行级「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按行级「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    /** 行级计税方式（价内税/价外税/不计税）：行级口径，传输字段不落库，保存时用于换算两列 */
    @TableField(exist = false)
    private String taxCalcMethod;

    /** 批次号 FLotNo, 批次管理物料必填 */
    @TableField("batch_no")
    private String batchNo;

    /** 货位 FBinID */
    @TableField("bin_id")
    private Long binId;

    /** 货位名称 FBinName */
    @TableField("bin_name")
    private String binName;

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

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_item_id")
    private Long sourceItemId;

    /** 行备注 FRowRemark */
    @TableField("remark")
    private String remark;

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
