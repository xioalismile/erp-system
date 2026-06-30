package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工艺路线主表 - 对齐 PG routing 表
 */
@Data
@TableName("routing")
public class Routing {

    @TableId(value = "routing_id", type = IdType.AUTO)
    private Long routingId;

    @TableField("routing_code")
    private String routingCode;

    @TableField("routing_name")
    private String routingName;

    @TableField("routing_version")
    private String routingVersion;

    @TableField("material_id")
    private Long materialId;

    @TableField("bom_id")
    private Long bomId;

    @TableField("is_primary")
    private Short isPrimary;

    @TableField("is_enabled")
    private Short isEnabled;

    @TableField("effective_date")
    private LocalDate effectiveDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("total_work_hours")
    private BigDecimal totalWorkHours;

    @TableField("remark")
    private String remark;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    /** 工序明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<RoutingOperation> operations;
}
