package com.erp.acquisition.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集任务实体类
 * 对应表 acquisition_task
 */
@Data
@TableName("acquisition_task")
public class AcquisitionTask {

    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    /** 任务编号 ACQ-yyyyMMdd-0001 */
    @TableField("task_no")
    private String taskNo;

    /** 任务名称 */
    @TableField("task_name")
    private String taskName;

    /** 采集平台 qichacha/tianyancha/gsxt */
    @TableField("platform")
    private String platform;

    /** JSON数组字符串，如 ["制造业","深圳","ERP软件"] */
    @TableField("keywords")
    private String keywords;

    /** 行业筛选 */
    @TableField("industry")
    private String industry;

    /** 地区筛选 */
    @TableField("area")
    private String area;

    /** 最大采集页数，默认5 */
    @TableField("max_pages")
    private Integer maxPages;

    /** 最大采集企业数，默认50 */
    @TableField("max_companies")
    private Integer maxCompanies;

    /** 任务状态 pending/running/completed/failed/paused */
    @TableField("status")
    private String status;

    /** JSON格式的登录Cookie（仅服务端采集链路内部使用，禁止下发前端） */
    @JsonIgnore
    @TableField("login_cookie")
    private String loginCookie;

    @TableField("last_run_time")
    private LocalDateTime lastRunTime;

    @TableField("next_run_time")
    private LocalDateTime nextRunTime;

    @TableField("cron_expression")
    private String cronExpression;

    /** 已采集数 */
    @TableField("captured_count")
    private Integer capturedCount;

    /** 已导入数 */
    @TableField("imported_count")
    private Integer importedCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("creator_id")
    private Long creatorId;

    /** 创建人（数据权限过滤维度，由 AuditMetaObjectHandler 自动填充当前登录用户名） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField("create_date")
    private LocalDateTime createDate;

    @TableField("modifier_id")
    private Long modifierId;

    /** 修改人（由 AuditMetaObjectHandler 自动填充当前登录用户名） */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField("modify_date")
    private LocalDateTime modifyDate;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 逻辑删除，0未删除 */
    @TableField("deleted")
    private Integer deleted;
}
