package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工序 - 对齐 PG workstation 表
 */
@Data
@TableName("workstation")
public class Workstation {

    @TableId(value = "workstation_id", type = IdType.AUTO)
    private Long workstationId;

    @TableField("workstation_code")
    private String workstationCode;

    @TableField("workstation_name")
    private String workstationName;

    @TableField("work_center_id")
    private Long workCenterId;

    @TableField("workstation_type")
    private String workstationType;

    @TableField("location")
    private String location;

    @TableField("is_active")
    private Short isActive;

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
