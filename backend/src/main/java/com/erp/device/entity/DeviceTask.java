package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 设备任务：设备与某道工单工序的有效关联 */
@Data
@TableName("device_task")
public class DeviceTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("device_id")
    private Long deviceId;

    @TableField("order_id")
    private Long orderId;

    @TableField("order_no")
    private String orderNo;

    /** work_order_operation.id */
    @TableField("operation_row_id")
    private Long operationRowId;

    @TableField("operation_name")
    private String operationName;

    /** MSG 报文显式 / AUTO 自动关联 / MANUAL 人工认领 / DISPATCH 任务下发 / SCAN 扫码认领 */
    @TableField("source_type")
    private String sourceType;

    /** 建立关联时的计数基线 */
    @TableField("count_base")
    private BigDecimal countBase;

    /** 待下发 / 已下发 / 执行中 / 已完成 / 已取消 / 下发失败 */
    @TableField("task_status")
    private String taskStatus;

    /** 解除原因：完工/取消/停机超时/人工/吊销 */
    @TableField("release_reason")
    private String releaseReason;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    // ---------------- 非数据库字段 ----------------

    @TableField(exist = false)
    private String deviceCode;

    @TableField(exist = false)
    private String deviceName;

    /** 工序号（工单工序 line_no） */
    @TableField(exist = false)
    private Integer operationLineNo;

    /** 工单物料名称（列表辅助展示） */
    @TableField(exist = false)
    private String materialName;

    /** 工序当前状态（列表辅助展示） */
    @TableField(exist = false)
    private String operationStatus;
}
