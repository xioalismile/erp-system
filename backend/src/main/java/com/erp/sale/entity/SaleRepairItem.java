package com.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 售后维修明细（客户产品行） */
@Data
@TableName("sale_repair_item")
public class SaleRepairItem {

    @TableId(value = "repair_item_id", type = IdType.AUTO)
    private Long repairItemId;

    @TableField("repair_id")
    private Long repairId;

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

    @TableField("serial_no")
    private String serialNo;

    @TableField("batch_no")
    private String batchNo;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("fault_description")
    private String faultDescription;

    @TableField("repair_plan")
    private String repairPlan;

    @TableField("warranty_status")
    private String warrantyStatus;

    @TableField("labor_hours")
    private BigDecimal laborHours;

    @TableField("received_quantity")
    private BigDecimal receivedQuantity;

    @TableField("returned_quantity")
    private BigDecimal returnedQuantity;

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

    /** 维修明细行自定义字段值（非数据库字段），随明细行 id 存取 */
    @TableField(exist = false)
    private Map<String, Object> customFieldValues;
}
