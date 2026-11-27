package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 未关联数据：设备在跑但认不到工单工序时的暂存 */
@Data
@TableName("device_unmatched_data")
public class DeviceUnmatchedData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    @TableField("period_start")
    private LocalDateTime periodStart;

    @TableField("period_end")
    private LocalDateTime periodEnd;

    @TableField("qualified_count")
    private BigDecimal qualifiedCount;

    @TableField("rejected_count")
    private BigDecimal rejectedCount;

    @TableField("raw_message_id")
    private Long rawMessageId;

    /** 未能关联的原因（可直接展示） */
    @TableField("reason")
    private String reason;

    /** 未关联 / 已认领 / 已过期 */
    @TableField("status")
    private String status;

    @TableField("claimed_operation_row_id")
    private Long claimedOperationRowId;

    @TableField("claimed_pending_id")
    private Long claimedPendingId;

    @TableField("claimed_by")
    private String claimedBy;

    @TableField("claimed_time")
    private LocalDateTime claimedTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ---------------- 非数据库字段 ----------------

    @TableField(exist = false)
    private String deviceCode;

    @TableField(exist = false)
    private String deviceName;

    @TableField(exist = false)
    private String claimedOperationName;
}
