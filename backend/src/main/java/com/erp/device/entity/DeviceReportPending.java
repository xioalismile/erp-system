package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 待确认报工单：设备产量进入生产前的人工闸门 */
@Data
@TableName("device_report_pending")
public class DeviceReportPending {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("pending_no")
    private String pendingNo;

    @TableField("device_id")
    private Long deviceId;

    @TableField("device_code")
    private String deviceCode;

    @TableField("device_name")
    private String deviceName;

    @TableField("order_id")
    private Long orderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("operation_row_id")
    private Long operationRowId;

    @TableField("operation_name")
    private String operationName;

    @TableField("period_start")
    private LocalDateTime periodStart;

    @TableField("period_end")
    private LocalDateTime periodEnd;

    @TableField("qualified_count")
    private BigDecimal qualifiedCount;

    @TableField("rejected_count")
    private BigDecimal rejectedCount;

    @TableField("run_hours")
    private BigDecimal runHours;

    @TableField("count_start")
    private BigDecimal countStart;

    @TableField("count_end")
    private BigDecimal countEnd;

    @TableField("source_type")
    private String sourceType;

    /** 待确认 / 已确认 / 已驳回 */
    @TableField("status")
    private String status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("confirm_by")
    private String confirmBy;

    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    @TableField("operation_report_id")
    private Long operationReportId;

    @TableField("operation_report_no")
    private String operationReportNo;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---------------- 非数据库字段 ----------------

    @TableField(exist = false)
    private Integer operationLineNo;

    @TableField(exist = false)
    private String operationStatus;

    @TableField(exist = false)
    private String materialName;
}
