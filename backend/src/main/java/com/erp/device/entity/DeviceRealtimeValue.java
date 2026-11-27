package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 点位实时值（一台设备一个点位一行，覆盖更新） */
@Data
@TableName("device_realtime_value")
public class DeviceRealtimeValue {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    @TableField("point_id")
    private Long pointId;

    @TableField("point_key")
    private String pointKey;

    /** 原始值文本 */
    @TableField("value_text")
    private String valueText;

    /** 换算后的数值（文本点位为空） */
    @TableField("value_num")
    private BigDecimal valueNum;

    /** 数据时间（设备时间或云端接收时间，取自协议模板口径） */
    @TableField("value_time")
    private LocalDateTime valueTime;

    /** GOOD / STALE / BAD */
    @TableField("quality")
    private String quality;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---------------- 非数据库字段 ----------------

    @TableField(exist = false)
    private String pointName;

    @TableField(exist = false)
    private String unit;

    @TableField(exist = false)
    private String purpose;
}
