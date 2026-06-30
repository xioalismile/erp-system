package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库 - 对齐 PG warehouse 表
 */
@Data
@TableName("warehouse")
public class Warehouse {

    @TableId(value = "stock_id", type = IdType.AUTO)
    private Long stockId;

    @TableField("stock_code")
    private String stockCode;

    @TableField("stock_name")
    private String stockName;

    @TableField("stock_type")
    private String stockType;

    @TableField("address")
    private String address;

    @TableField("manager_id")
    private Long managerId;

    @TableField("is_bin_control")
    private Short isBinControl;

    @TableField("is_mrp_available")
    private Short isMrpAvailable;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
