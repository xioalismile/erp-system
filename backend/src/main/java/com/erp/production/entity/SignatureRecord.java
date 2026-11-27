package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电子签名记录 - 对齐 PG signature_record 表
 * 化妆品批记录：检验提交/放行签发/记录更正三处二次密码签名，永久留痕只增不改。
 * 服务层不提供 update/delete（服务只增口径在 Mapper 调用侧约束）。
 */
@Data
@TableName("signature_record")
public class SignatureRecord {

    @TableId(value = "signature_id", type = IdType.AUTO)
    private Long signatureId;

    @TableField("action_type")
    private String actionType;

    @TableField("target_table")
    private String targetTable;

    @TableField("target_id")
    private Long targetId;

    @TableField("target_desc")
    private String targetDesc;

    @TableField("signer_name")
    private String signerName;

    @TableField("sign_time")
    private LocalDateTime signTime;

    @TableField("verify_result")
    private String verifyResult;

    /** 手写签名图（PNG DataURL，只增不改；历史密码签名记录为空） */
    @TableField("sign_image")
    private String signImage;

    /** 签名方式：手写 / 密码 */
    @TableField("sign_method")
    private String signMethod;
}
