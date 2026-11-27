package com.erp.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署/回滚执行日志
 */
@Data
@TableName("deploy_log")
public class DeployLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serverId;
    private String serverName;
    /** DEPLOY / ROLLBACK */
    private String action;
    /** RUNNING / SUCCESS / FAILED / ROLLED_BACK */
    private String status;
    private String versionNote;
    /** 部署的版本号（版本库联动） */
    private String versionNo;
    private String logText;
    private String operator;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
