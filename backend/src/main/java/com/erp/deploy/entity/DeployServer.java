package com.erp.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署目标客户服务器
 */
@Data
@TableName("deploy_server")
public class DeployServer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUser;
    private String sshPassword;
    private String remoteWorkDir;
    /** 服务器上后端 jar 绝对路径 */
    private String jarTarget;
    /** 前端目录列表，分号分隔，存在即全拷 */
    private String frontendTargets;
    private String stopCommand;
    private String startCommand;
    private String healthUrl;
    /** 业务库名/账号/密码：三项齐全则部署时 agent 执行幂等补列（schema sync），防老库缺列卡登录 */
    private String dbName;
    private String dbUser;
    private String dbPassword;
    private String remark;
    private Integer enabled;
    /** 当前运行版本号（部署成功自动回写） */
    private String currentVersion;
    /** 当前版本部署时间 */
    private LocalDateTime versionDeployedAt;
    /** 包模式 full/customer（防止客户包/全量包错发） */
    private String buildMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
