package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售预警（2026-10-07）
 *
 * <p>黄 / 橙 / 红 三级 + 处理时限 + 闭环状态机（待处理 → 处理中 → 已处理待确认 → 已关闭）。
 * 规则清单见立项登记表第八节（销售 12 条 + 物流 3 条）。</p>
 */
@Data
@TableName("crm_sales_warning")
public class SalesWarning {

    @TableId(value = "warning_id", type = IdType.AUTO)
    private Long warningId;

    @TableField("warning_no")
    private String warningNo;

    @TableField("warning_type")
    private String warningType;

    /** 预警等级：黄(提醒)/橙(警告)/红(严重) */
    @TableField("warning_level")
    private String warningLevel;

    /** 对象类型：个人/客户/单据 */
    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private Long targetId;

    @TableField("target_name")
    private String targetName;

    /** 负责人（用来判断"与谁有关"、推送给谁） */
    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("owner_name")
    private String ownerName;

    @TableField("trigger_value")
    private String triggerValue;

    @TableField("threshold_value")
    private String thresholdValue;

    @TableField("push_to")
    private String pushTo;

    @TableField("deadline")
    private LocalDateTime deadline;

    /** 闭环状态：待处理/处理中/已处理待确认/已关闭 */
    @TableField("status")
    private String status;

    @TableField("handle_remark")
    private String handleRemark;

    @TableField("handle_attachment")
    private String handleAttachment;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("handler_name")
    private String handlerName;

    @TableField("handle_time")
    private LocalDateTime handleTime;

    /** 升级来源（原预警编号），用于追溯升级链 */
    @TableField("escalated_from")
    private String escalatedFrom;

    @TableField("period_start")
    private LocalDate periodStart;

    @TableField("period_end")
    private LocalDate periodEnd;

    @TableField("creator_id")
    private Long creatorId;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    @TableField(value = "modify_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime modifyDate;

    /** 剩余时间（秒，派生不落库；负数=已超时） */
    @TableField(exist = false)
    private Long remainSeconds;
}
