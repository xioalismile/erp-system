package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工作中心 - 对齐 PG work_center 表
 */
@Data
@TableName("work_center")
public class WorkCenter {

    @TableId(value = "work_center_id", type = IdType.AUTO)
    private Long workCenterId;

    @TableField("work_center_code")
    private String workCenterCode;

    @TableField("work_center_name")
    private String workCenterName;

    @TableField("department_id")
    private Long departmentId;

    @TableField("manager_id")
    private Long managerId;

    @TableField("location")
    private String location;

    @TableField("capacity_type")
    private String capacityType;

    @TableField("capacity_per_day")
    private BigDecimal capacityPerDay;

    @TableField("efficiency")
    private BigDecimal efficiency;

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
