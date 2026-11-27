package com.erp.subcontract.inbound.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("subcontract_inbound_item")
public class SubcontractInboundItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("inbound_id")
    private Long inboundId;

    @TableField("order_item_id")
    private Long orderItemId;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit")
    private String unit;

    /** 单位ID（2026-11-10 多单位换算接入；历史数据可能为空，后端按单位名称兜底） */
    @com.baomidou.mybatisplus.annotation.TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

    @TableField("processing_fee")
    private BigDecimal processingFee;

    /** 成品入库单位成本（材料成本 + 加工费，审核时计算并冻结） */
    @TableField("unit_cost")
    private BigDecimal unitCost;

    /** 本行归集的材料成本合计（审核时计算，供追溯与对账） */
    @TableField("material_cost")
    private BigDecimal materialCost;

    /** 成品批次号（批次管理物料必填） */
    @TableField("batch_no")
    private String batchNo;

    /** 质检合格数量（质检开关打开后由质检回写） */
    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    /** 不合格处理方式：RELEASE=放行 / RETURN=自动退货 / SCRAP=进报废仓 */
    @TableField("handling_type")
    private String handlingType;

    @TableField("scrap_warehouse_id")
    private Long scrapWarehouseId;

    @TableField("scrap_warehouse_name")
    private String scrapWarehouseName;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
