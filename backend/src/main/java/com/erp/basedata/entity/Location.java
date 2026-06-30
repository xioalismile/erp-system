package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 货位 - 对齐 PG location 表
 */
@Data
@TableName("location")
public class Location {

    @TableId(value = "bin_id", type = IdType.AUTO)
    private Long binId;

    @TableField("bin_code")
    private String binCode;

    @TableField("bin_name")
    private String binName;

    @TableField("stock_id")
    private Long stockId;

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
