package com.erp.subcontract.returnn.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("subcontract_return_item")
public class SubcontractReturnItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("return_id")
    private Long returnId;

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

    /** 退回单位成本（入库时冻结，供委外成品净材料成本冲减） */
    @TableField("avg_cost")
    private BigDecimal avgCost;

    /** 退料批次号（批次管理物料必填） */
    @TableField("batch_no")
    private String batchNo;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

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
