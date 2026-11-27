package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单成品明细 - 支持一个工单多个成品
 */
@Data
@TableName("work_order_product")
public class WorkOrderProduct extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

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

    @TableField("model")
    private String model;

    @TableField("unit_id")
    private Long unitId;

    @TableField("unit_name")
    private String unitName;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("completed_quantity")
    private BigDecimal completedQuantity;

    @TableField("reported_quantity")
    private BigDecimal reportedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 来源单类型：销售订单/MRP计划 */
    @TableField("source_type")
    private String sourceType;

    /** 来源单号 */
    @TableField("source_no")
    private String sourceNo;

    /** 来源单明细ID */
    @TableField("source_item_id")
    private Long sourceItemId;

    /** 来源单类型（标准命名） */
    @TableField("source_bill_type")
    private String sourceBillType;

    /** 来源单号（标准命名） */
    @TableField("source_bill_no")
    private String sourceBillNo;

    /** 来源单主表ID */
    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("warehouse_id")
    private Long warehouseId;

    /** 该成品明细行使用的工艺路线（第10期：一张工单多个商品时，每个成品各自一套工序） */
    @TableField("routing_id")
    private Long routingId;

    /** 工艺路线版本快照 */
    @TableField("routing_version")
    private String routingVersion;

    @TableField("remark")
    private String remark;

    /** 行级 BOM 清单（2026-11-06）：该成品行使用的 BOM；整单 BOM 仍保留作新建默认值 */
    @TableField("bom_id")
    private Long bomId;

    /** 行级 BOM 清单编码 */
    @TableField("bom_code")
    private String bomCode;

    /** 行级 BOM 版本 */
    @TableField("bom_version")
    private String bomVersion;

    /**
     * 【2026-11-07】下推占用数量（非库字段）：该成品行已下推的生产入库数量，
     * 口径 = 下游草稿 + 待审核 + 已生效 − 已作废。用于选单弹窗「已下推/剩余」，
     * 与「已入库（completed_quantity，仅生效后累加）」分开，不混用。
     */
    @TableField(exist = false)
    private BigDecimal pushedQuantity;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
