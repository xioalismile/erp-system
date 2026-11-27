package com.erp.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署版本库：本地一键打包产生的版本（低代码平台式版本管理）
 */
@Data
@TableName("deploy_version")
public class DeployVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 版本号，如 20260827.01 */
    private String versionNo;
    /** full=全量包 / customer=客户包 */
    private String buildMode;
    private String note;
    /** 版本包本地存储路径 */
    private String zipPath;
    private Long zipSize;
    private String zipMd5;
    private String operator;
    private LocalDateTime createdAt;
}
