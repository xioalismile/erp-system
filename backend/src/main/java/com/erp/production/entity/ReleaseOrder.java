package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 放行单 - 对齐 PG release_order 表
 * 化妆品批记录：成品批次检验合格后由质量负责人签发放行，接入统一状态机（AbstractBillService）。
 * 生效时回写批次质量状态为「已放行」，并自动生成留样任务。
 */
@Data
@TableName("release_order")
public class ReleaseOrder {

    @TableId(value = "release_id", type = IdType.AUTO)
    private Long releaseId;

    @TableField("release_no")
    private String releaseNo;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("work_order_id")
    private Long workOrderId;

    @TableField("work_order_no")
    private String workOrderNo;

    @TableField("qc_record_id")
    private Long qcRecordId;

    @TableField("release_result")
    private String releaseResult;

    @TableField("released_by")
    private String releasedBy;

    @TableField("released_at")
    private LocalDateTime releasedAt;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

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
}
