package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_follow_up")
public class FollowUp {

    @TableId(value = "follow_up_id", type = IdType.AUTO)
    @ExportField(label = "跟进ID", order = 0, hidden = true)
    private Long followUpId;

    @TableField("follow_up_no")
    @ExportField(label = "跟进编号", order = 1, required = true)
    private String followUpNo;

    /** 来源类型: 客户/线索/商机 */
    @TableField("source_type")
    @ExportField(label = "来源类型", order = 2)
    private String sourceType;

    @TableField("source_id")
    @ExportField(label = "来源ID", order = 3)
    private Long sourceId;

    @TableField("source_name")
    @ExportField(label = "来源名称", order = 4)
    private String sourceName;

    @TableField("follow_up_date")
    @ExportField(label = "跟进日期", order = 5, dateFormat = "yyyy-MM-dd")
    private LocalDate followUpDate;

    /** 跟进方式: 电话/邮件/上门拜访/微信/视频会议/其他 */
    @TableField("follow_up_type")
    @ExportField(label = "跟进方式", order = 6)
    private String followUpType;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 7)
    private String contactPerson;

    /** 跟进内容: NTEXT/CLOB */
    @TableField("content")
    @ExportField(label = "跟进内容", order = 8)
    private String content;

    @TableField("summary")
    @ExportField(label = "跟进摘要", order = 9)
    private String summary;

    @TableField("result")
    @ExportField(label = "跟进结果", order = 10)
    private String result;

    @TableField("next_plan")
    @ExportField(label = "下次计划", order = 11)
    private String nextPlan;

    @TableField("next_follow_up_date")
    @ExportField(label = "下次跟进日期", order = 12, dateFormat = "yyyy-MM-dd")
    private LocalDate nextFollowUpDate;

    /** 重要标记: 0-否，1-是 */
    @TableField("important_flag")
    @ExportField(label = "是否重要", order = 13)
    private Integer importantFlag;

    @TableField("attachment")
    @ExportField(label = "附件", order = 14)
    private String attachment;

    @TableField("follow_up_staff_id")
    @ExportField(label = "跟进人ID", order = 15)
    private Long followUpStaffId;

    // ===== 销售过程动作量化（2026-10-07 起）：回访 / 深入交流两条日任务的判据 =====

    /** 通话时长（秒）；空 = 未填写，不计入回访达标（阈值见设置中心 sales.process.daily_visit） */
    @TableField("call_duration_seconds")
    @ExportField(label = "通话时长（秒）", order = 17)
    private Integer callDurationSeconds;

    /** 微信交流条数；空 = 未填写，不计入深入交流判定 */
    @TableField("chat_message_count")
    @ExportField(label = "微信交流条数", order = 18)
    private Integer chatMessageCount;

    /** 是否深入交流：1=是 0=否；由「通话 ≥300 秒 或 交流 ≥10 句」自动预判回填，人可手改 */
    @TableField("is_deep_communication")
    @ExportField(label = "是否深入交流", order = 19)
    private Integer isDeepCommunication;

    @TableField("remark")
    @ExportField(label = "备注", order = 16)
    private String remark;

    @TableField("creator_id")
    @ExportField(label = "创建人ID", order = 100, hidden = true)
    private Long creatorId;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createDate;

    /** 创建人（数据权限过滤维度，AuditMetaObjectHandler 自动填充） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;
}