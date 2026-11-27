package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产入库单明细 - 对齐 PG production_receipt_item 表
 */
@Data
@TableName("production_receipt_item")
public class ProductionReceiptItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "receipt_item_id", type = IdType.AUTO)
    private Long receiptItemId;

    @TableField("receipt_id")
    private Long receiptId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("amount")
    private BigDecimal amount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_with_tax")
    private BigDecimal priceWithTax;

    /** 不含税价（按表头「计税方式」由单价换算得出，落库） */
    @TableField("price_without_tax")
    private BigDecimal priceWithoutTax;

    @TableField("avg_cost")
    private BigDecimal avgCost;

    @TableField("stock_id")
    private Long stockId;

    @TableField("bin_id")
    private Long binId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("work_order_line_no")
    private Integer workOrderLineNo;

    @TableField("operation_report_no")
    private String operationReportNo;

    /** 来源工序行（work_order_operation.id）：该行由哪道入库类型工序产生，用于按行汇总已入库量 */
    @TableField("source_operation_id")
    private Long sourceOperationId;

    @TableField("remark")
    private String remark;

    /** 不合格处理方式：RELEASE=放行 / RETURN=自动退货（生产不入库，工单保留可入库量）/ SCRAP=进报废仓 */
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
