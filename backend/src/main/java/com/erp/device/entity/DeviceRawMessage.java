package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 原始报文（解析失败也留存，可重放） */
@Data
@TableName("device_raw_message")
public class DeviceRawMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    @TableField("channel_type")
    private String channelType;

    /** IN 上报 / OUT 下发 */
    @TableField("direction")
    private String direction;

    /** 设备消息序号或幂等键 */
    @TableField("message_key")
    private String messageKey;

    @TableField("raw_content")
    private String rawContent;

    /** 解析结果 JSON（点位键 → 值） */
    @TableField("parsed_json")
    private String parsedJson;

    /** 成功 / 失败 / 部分成功 */
    @TableField("parse_status")
    private String parseStatus;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    /** 是否模拟上报：1 是（不写实时值、不参与生产联动） */
    @TableField("simulated")
    private Integer simulated;

    // ---------------- 非数据库字段 ----------------

    @TableField(exist = false)
    private String deviceCode;

    @TableField(exist = false)
    private String deviceName;
}
