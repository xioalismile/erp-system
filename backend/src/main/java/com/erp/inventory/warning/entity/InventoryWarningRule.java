package com.erp.inventory.warning.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_warning_rule")
public class InventoryWarningRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("rule_name")
    private String ruleName;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("warehouse_name")
    private String warehouseName;

    @TableField("warning_type")
    private String warningType;

    @TableField("min_quantity")
    private BigDecimal minQuantity;

    @TableField("max_quantity")
    private BigDecimal maxQuantity;

    @TableField("notify_type")
    private Integer notifyType;

    @TableField("notify_users")
    private String notifyUsers;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    @TableField("create_by")
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
