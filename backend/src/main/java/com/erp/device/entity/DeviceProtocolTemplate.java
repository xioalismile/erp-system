package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 协议模板（系统预置只读，可复制为租户模板） */
@Data
@TableName("device_protocol_template")
public class DeviceProtocolTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("template_code")
    private String templateCode;

    @TableField("template_name")
    private String templateName;

    /** MODBUS_TCP / MODBUS_RTU / OPC_UA / MQTT / HTTP / TCP / SERIAL */
    @TableField("protocol_type")
    private String protocolType;

    /** 是否系统预置：1 是（只读可复制）/ 0 否 */
    @TableField("is_preset")
    private Integer isPreset;

    /** 连接参数 JSON */
    @TableField("config_json")
    private String configJson;

    /** 报文格式：JSON / FIXED / DELIMITER / REGEX / SCRIPT */
    @TableField("parse_format")
    private String parseFormat;

    /** BIG_ENDIAN / LITTLE_ENDIAN */
    @TableField("byte_order")
    private String byteOrder;

    /** HIGH_FIRST / LOW_FIRST */
    @TableField("word_order")
    private String wordOrder;

    /** CLOUD 云端接收时间 / DEVICE 设备时间 */
    @TableField("timestamp_source")
    private String timestampSource;

    @TableField("status")
    private String status;

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
}
