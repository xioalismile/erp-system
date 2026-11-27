package com.erp.kai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小楷独立版 · 外部系统连接（FEAT-KAI-STANDALONE-001）。
 * 凭据 AES-GCM 加密落库（前缀 ENC:），列表只回显脱敏标记，编辑留空=保持原值。
 */
@Data
@TableName("kai_ext_connection")
public class KaiExtConnection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kaiUserId;

    /** 连接名称（客户自取，如"演示系统""金蝶星空-华东"） */
    private String name;

    /** 连接类型：DEMO / GENERIC_REST / KINGDEE / YONYOU */
    private String type;

    /** 服务地址 */
    private String baseUrl;

    /** 鉴权方式：API_KEY / BEARER_TOKEN / BASIC / LOGIN_TOKEN */
    private String authType;

    /** 加密后的凭据 JSON（前缀 ENC:） */
    private String credentialEnc;

    /** 附加配置 JSON（通用 REST 的端点映射、字段映射等） */
    private String extraConfig;

    /** 1=启用 0=停用 */
    private Integer enabled;

    private Integer timeoutSeconds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
