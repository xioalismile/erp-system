package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_complaint")
public class Complaint {

    @TableId(value = "complaint_id", type = IdType.AUTO)
    @ExportField(label = "投诉ID", order = 0, hidden = true)
    private Long complaintId;

    @TableField("complaint_no")
    @ExportField(label = "投诉编号", order = 1, required = true)
    private String complaintNo;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 2, required = true)
    private Long customerId;

    @TableField("customer_name")
    @ExportField(label = "客户名称", order = 3)
    private String customerName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 4)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 5)
    private String contactPhone;

    @TableField("complaint_date")
    @ExportField(label = "投诉日期", order = 6, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate complaintDate;

    /** 投诉类型: 产品质量/发货延迟/服务态度/售后问题/价格问题/其他 */
    @TableField("complaint_type")
    @ExportField(label = "投诉类型", order = 7)
    private String complaintType;

    /** 投诉渠道: 电话/邮件/在线客服/现场/其他 */
    @TableField("complaint_channel")
    @ExportField(label = "投诉渠道", order = 8)
    private String complaintChannel;

    @TableField("complaint_content")
    @ExportField(label = "投诉内容", order = 9)
    private String complaintContent;

    /** 严重级别: 高/中/低, 默认'中' */
    @TableField("severity_level")
    @ExportField(label = "严重级别", order = 10)
    private String severityLevel;

    @TableField("assigned_handler_id")
    @ExportField(label = "处理人ID", order = 11)
    private Long assignedHandlerId;

    @TableField("assigned_date")
    @ExportField(label = "分配日期", order = 12, dateFormat = "yyyy-MM-dd")
    private LocalDate assignedDate;

    /** 处理状态: 新建/处理中/已解决/已关闭, 默认'新建' */
    @TableField("handle_status")
    @ExportField(label = "处理状态", order = 13)
    private String handleStatus;

    @TableField("handle_result")
    @ExportField(label = "处理结果", order = 14)
    private String handleResult;

    @TableField("handle_date")
    @ExportField(label = "处理日期", order = 15, dateFormat = "yyyy-MM-dd")
    private LocalDate handleDate;

    @TableField("compensation_amount")
    @ExportField(label = "赔偿金额", order = 16)
    private BigDecimal compensationAmount;

    @TableField("satisfaction_score")
    @ExportField(label = "满意度评分", order = 17)
    private Integer satisfactionScore;

    @TableField("close_date")
    @ExportField(label = "关闭日期", order = 18, dateFormat = "yyyy-MM-dd")
    private LocalDate closeDate;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
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

    @TableField("modifier_id")
    @ExportField(label = "修改人ID", order = 102, hidden = true)
    private Long modifierId;

    @TableField(value = "modify_date", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "修改时间", order = 103, hidden = true)
    private LocalDateTime modifyDate;
}