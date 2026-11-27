package com.erp.acquisition.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集结果实体类
 * 对应表 acquisition_result
 */
@Data
@TableName("acquisition_result")
public class AcquisitionResult {

    @TableId(value = "result_id", type = IdType.AUTO)
    private Long resultId;

    /** 关联任务ID */
    @TableField("task_id")
    private Long taskId;

    @TableField("company_name")
    private String companyName;

    /** 统一社会信用代码 */
    @TableField("credit_code")
    private String creditCode;

    /** 法人代表 */
    @TableField("legal_person")
    private String legalPerson;

    /** 注册资本 */
    @TableField("registered_capital")
    private String registeredCapital;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_email")
    private String contactEmail;

    @TableField("registered_address")
    private String registeredAddress;

    /** 通信地址（用户要求新增字段） */
    @TableField("contact_address")
    private String contactAddress;

    /** 所属地区（省份/城市） */
    @TableField("area")
    private String area;

    /** 经营范围 */
    @TableField("business_scope")
    private String businessScope;

    @TableField("industry")
    private String industry;

    /** 成立日期字符串 */
    @TableField("establish_date")
    private String establishDate;

    /** 企业状态 */
    @TableField("company_status")
    private String companyStatus;

    /** JSON数组多张截图路径 */
    @TableField("screenshot_path")
    private String screenshotPath;

    @TableField("source_url")
    private String sourceUrl;

    /** AI识别原始结果JSON */
    @TableField("ai_raw_result")
    private String aiRawResult;

    /** 审核状态 pending/approved/rejected/imported */
    @TableField("review_status")
    private String reviewStatus;

    @TableField("reviewed_by")
    private Long reviewedBy;

    @TableField("reviewed_date")
    private LocalDateTime reviewedDate;

    @TableField("reject_reason")
    private String rejectReason;

    /** 已导入的CRM线索ID */
    @TableField("imported_lead_id")
    private Long importedLeadId;

    /** 备注 */
    @TableField("remark")
    private String remark;

    @TableField("create_date")
    private LocalDateTime createDate;

    /** 创建人（由 AuditMetaObjectHandler 自动填充当前登录用户名） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /** 修改人（由 AuditMetaObjectHandler 自动填充当前登录用户名） */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 逻辑删除，0未删除 */
    @TableField("deleted")
    private Integer deleted;
}
