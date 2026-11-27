package com.erp.inventory.warning.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_warning_log")
public class InventoryWarningLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_name")
    private String materialName;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

    @TableField("current_qty")
    private BigDecimal currentQty;

    @TableField("warning_type")
    private String warningType;

    @TableField("warning_level")
    private String warningLevel;

    @TableField("is_handled")
    private Integer isHandled;

    @TableField("handled_by")
    private String handledBy;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("handle_remark")
    private String handleRemark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
