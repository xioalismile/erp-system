package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 售后维修配件（领用行） */
@Data
@TableName("sale_repair_part")
public class SaleRepairPart {

    @TableId(value = "part_id", type = IdType.AUTO)
    private Long partId;

    @TableField("repair_id")
    private Long repairId;

    @TableField("repair_item_id")
    private Long repairItemId;

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

    @TableField("unit")
    private String unit;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("avg_cost")
    private BigDecimal avgCost;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("issued_quantity")
    private BigDecimal issuedQuantity;

    @TableField("issue_id")
    private Long issueId;

    @TableField("issue_no")
    private String issueNo;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 配件行自定义字段值（非数据库字段），随配件行 id 存取 */
    @TableField(exist = false)
    private Map<String, Object> customFieldValues;
}
