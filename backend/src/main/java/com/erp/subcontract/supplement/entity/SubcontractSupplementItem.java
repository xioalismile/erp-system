package com.erp.subcontract.supplement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("subcontract_supplement_item")
public class SubcontractSupplementItem extends com.erp.common.bill.BillItemUnitFields {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("supplement_id")
    private Long supplementId;

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

    /** 实际出库单位成本（出库时冻结，供委外成品材料成本归集） */
    @TableField("avg_cost")
    private BigDecimal avgCost;

    /** 补料批次号（批次管理物料必填） */
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
