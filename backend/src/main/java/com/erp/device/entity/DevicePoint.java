package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 设备数据点（点位） */
@Data
@TableName("device_point")
public class DevicePoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    /** 协议模板（可空） */
    @TableField("template_id")
    private Long templateId;

    /** 点位标识（设备内唯一） */
    @TableField("point_key")
    private String pointKey;

    @TableField("point_name")
    private String pointName;

    /**
     * 业务用途：
     * START_SIGNAL 开工信号 / STOP_SIGNAL 停机信号 / QUALIFIED_COUNT 合格计数 /
     * REJECTED_COUNT 报废计数 / ALARM_CODE 报警码 / FAULT_BIT 故障位 /
     * PARAM 工艺参数 / WRITABLE_PARAM 可写参数 / ORDER_NO 工单号 / OPERATION_NO 工序号 /
     * MONITOR 普通监控
     */
    @TableField("purpose")
    private String purpose;

    /** 设备地址（Modbus 寄存器 / OPC UA 节点） */
    @TableField("address")
    private String address;

    /** 报文键（JSON 路径 / 定长偏移） */
    @TableField("parse_key")
    private String parseKey;

    /** BOOLEAN / INTEGER / DECIMAL / TEXT */
    @TableField("data_type")
    private String dataType;

    @TableField("unit")
    private String unit;

    /** 换算系数：入库值 = 原始值 × 系数 + 偏移 */
    @TableField("scale_factor")
    private BigDecimal scaleFactor;

    @TableField("scale_offset")
    private BigDecimal scaleOffset;

    /** 采集频率（秒） */
    @TableField("sample_interval")
    private Integer sampleInterval;

    /** 死区：变化未超过该值不上报 */
    @TableField("dead_zone")
    private BigDecimal deadZone;

    /** 是否可写：1 是 / 0 否 */
    @TableField("writable")
    private Integer writable;

    @TableField("alarm_upper")
    private BigDecimal alarmUpper;

    @TableField("alarm_lower")
    private BigDecimal alarmLower;

    @TableField("alarm_level")
    private String alarmLevel;

    @TableField("enabled")
    private Integer enabled;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    // ---------------- 非数据库字段：最近值回显 ----------------

    @TableField(exist = false)
    private String lastValue;

    @TableField(exist = false)
    private LocalDateTime lastValueTime;
}
