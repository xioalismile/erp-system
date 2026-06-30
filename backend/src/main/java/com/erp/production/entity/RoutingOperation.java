package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工艺路线工序明细 - 对齐 PG routing_operation 表
 */
@Data
@TableName("routing_operation")
public class RoutingOperation {

    @TableId(value = "operation_id", type = IdType.AUTO)
    private Long operationId;

    @TableField("routing_id")
    private Long routingId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("operation_code")
    private String operationCode;

    @TableField("operation_name")
    private String operationName;

    @TableField("workstation_type")
    private String workstationType;

    @TableField("workstation_id")
    private Long workstationId;

    @TableField("work_center_id")
    private Long workCenterId;

    @TableField("setup_hours")
    private BigDecimal setupHours;

    @TableField("process_hours")
    private BigDecimal processHours;

    @TableField("wait_hours")
    private BigDecimal waitHours;

    @TableField("move_hours")
    private BigDecimal moveHours;

    @TableField("total_hours")
    private BigDecimal totalHours;

    @TableField("batch_size")
    private Integer batchSize;

    @TableField("workers_required")
    private Integer workersRequired;

    @TableField("is_qc_required")
    private Short isQcRequired;

    @TableField("is_critical")
    private Short isCritical;

    @TableField("next_operation_line_no")
    private Integer nextOperationLineNo;

    @TableField("previous_operation_line_no")
    private Integer previousOperationLineNo;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
