package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备接入凭证（一设备一凭证；密钥仅创建/轮换时明文返回一次） */
@Data
@TableName("device_access_credential")
public class DeviceAccessCredential {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    /** 凭证标识：DEVC-{租户ID}-{随机串} */
    @TableField("credential_no")
    private String credentialNo;

    /** 密钥摘要（SHA-256，用于完整性核对） */
    @TableField("secret_hash")
    private String secretHash;

    /** 密钥密文（AES-GCM，验签时解密使用；接口永不返回） */
    @TableField("secret_enc")
    private String secretEnc;

    @TableField("secret_salt")
    private String secretSalt;

    /** 启用 / 已吊销 */
    @TableField("status")
    private String status;

    @TableField("last_access_time")
    private LocalDateTime lastAccessTime;

    @TableField("last_access_ip")
    private String lastAccessIp;

    /** 连续鉴权失败次数（成功一次即清零） */
    @TableField("fail_count")
    private Integer failCount;

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

    // ---------------- 非数据库字段 ----------------

    @TableField(exist = false)
    private String deviceCode;

    @TableField(exist = false)
    private String deviceName;

    /** 密钥尾号（列表展示，不返回明文/密文） */
    @TableField(exist = false)
    private String secretMasked;

    /** 凭证明文密钥：仅生成/轮换接口返回一次，不落库回读 */
    @TableField(exist = false)
    private String secretPlain;
}
