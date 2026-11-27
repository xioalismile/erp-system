package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备接入通道（HTTP / MQTT / TCP / WebSocket / 云端轮询） */
@Data
@TableName("device_channel")
public class DeviceChannel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    /** HTTP / MQTT / TCP / WEBSOCKET / POLL */
    @TableField("channel_type")
    private String channelType;

    @TableField("channel_name")
    private String channelName;

    /** 连接参数 JSON（按通道类型不同字段） */
    @TableField("config_json")
    private String configJson;

    /** 心跳间隔（秒） */
    @TableField("heartbeat_interval")
    private Integer heartbeatInterval;

    /** 离线判定：连续多少个心跳周期未收到数据即离线 */
    @TableField("offline_threshold")
    private Integer offlineThreshold;

    /** 是否启用：1 是 / 0 否（默认关闭） */
    @TableField("enabled")
    private Integer enabled;

    @TableField("last_connected_time")
    private LocalDateTime lastConnectedTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(exist = false)
    private String deviceCode;

    @TableField(exist = false)
    private String deviceName;
}
