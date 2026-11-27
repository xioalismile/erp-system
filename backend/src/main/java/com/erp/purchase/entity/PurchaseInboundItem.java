package com.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购入库单明细(对应 purchase_receipt_item 表)
 * 文档3.3 表体字段(18个)
 */
@Data
@TableName("purchase_receipt_item")
public class PurchaseInboundItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "receipt_item_id", type = IdType.AUTO)
    private Long receiptItemId;

    @TableField("receipt_id")
    private Long receiptId;

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

    /** 数量 FQty, 必须>0且≤可入库数量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 单价 FPrice, 不含税, 从源单(采购订单)带出 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 金额 FAmount, 自动计算: 数量 × 单价 */
    @TableField("amount")
    private BigDecimal amount;

    @TableField("avg_cost")
    private BigDecimal avgCost;

    /** 已退货数量(由采购退货单回写, 不可编辑, 非数据库字段) */
    @TableField(exist = false)
    private BigDecimal returnedQuantity;

    /** 批次号 FLotNo, 批次管理物料必填 (映射现有 batch_no 列) */
    @TableField("batch_no")
    private String batchNo;

    /** 序列号 FSerialNo, 序列号管理物料必填 */
    @TableField("serial_no")
    private String serialNo;

    /** 生产日期 FProductionDate */
    @TableField("production_date")
    private LocalDate productionDate;

    /** 有效期至 FExpiryDate */
    @TableField("expiry_date")
    private LocalDate expiryDate;

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

    @TableField("base_unit_price")
    private BigDecimal baseUnitPrice;

    @TableField("base_amount")
    private BigDecimal baseAmount;

    @TableField("base_tax_amount")
    private BigDecimal baseTaxAmount;

    @TableField("base_net_amount")
    private BigDecimal baseNetAmount;

    /** 税率(%)（按表头「计税方式」参与含税价/不含税价换算） */
    @TableField("tax_rate")
    private BigDecimal taxRate;

    /** 税额（自动计算：折后金额 × 税率 / 100；不计税时为 0） */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 价税合计（自动计算：金额 + 税额） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    /** 行备注 FRowRemark */
    @TableField("remark")
    private String remark;

    /** 行仓库（2026-11-07：表头仓库改为统一选择器，明细仓库为入库依据） */
    @TableField("stock_id")
    private Long stockId;

    @TableField("stock_name")
    private String stockName;

    /** 质检合格数量（质检开关打开后由质检回写） */
    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    /** 质检不合格数量 */
    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 不合格处理方式：RELEASE=放行 / RETURN=自动退货 / SCRAP=进报废仓 */
    @TableField("handling_type")
    private String handlingType;

    @TableField("scrap_warehouse_id")
    private Long scrapWarehouseId;

    @TableField("scrap_warehouse_name")
    private String scrapWarehouseName;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
