package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产工单 - 对齐 PG production_order 表
 */
@Data
@TableName("production_order")
public class WorkOrder {

    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("order_date")
    private LocalDate orderDate;

    @TableField("bom_id")
    private Long bomId;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("completed_quantity")
    private BigDecimal completedQuantity;

    @TableField("rejected_quantity")
    private BigDecimal rejectedQuantity;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("manager_id")
    private Long managerId;

    @TableField("routing_id")
    private Long routingId;

    @TableField("priority")
    private String priority;

    @TableField("plan_start_date")
    private LocalDate planStartDate;

    @TableField("plan_end_date")
    private LocalDate planEndDate;

    @TableField("actual_start_date")
    private LocalDate actualStartDate;

    @TableField("actual_end_date")
    private LocalDate actualEndDate;

    @TableField("lead_time_days")
    private Integer leadTimeDays;

    @TableField("is_urgent")
    private Short isUrgent;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_no")
    private String sourceNo;

    @TableField("sales_order_no")
    private String salesOrderNo;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

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

    @TableField("org_code")
    private String orgCode;

    /** 工单用料明细(非数据库字段) */
    @TableField(exist = false)
    private List<WorkOrderMaterial> materials;
}
