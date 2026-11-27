package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_lead")
public class Lead {

    @TableId(value = "lead_id", type = IdType.AUTO)
    @ExportField(label = "线索ID", order = 0, hidden = true)
    private Long leadId;

    @TableField("lead_no")
    @ExportField(label = "线索编号", order = 1, required = true)
    private String leadNo;

    @TableField("lead_name")
    @ExportField(label = "线索名称", order = 2, required = true)
    private String leadName;

    @TableField("company_name")
    @ExportField(label = "公司名称", order = 3)
    private String companyName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 4)
    private String contactPerson;

    @TableField("contact_phone")
    @ExportField(label = "联系电话", order = 5)
    private String contactPhone;

    @TableField("contact_email")
    @ExportField(label = "联系邮箱", order = 6)
    private String contactEmail;

    @TableField("contact_position")
    @ExportField(label = "联系人职位", order = 7)
    private String contactPosition;

    @TableField("source_type")
    @ExportField(label = "来源类型", order = 8)
    private String sourceType;

    @TableField("source_detail")
    @ExportField(label = "来源详情", order = 9)
    private String sourceDetail;

    @TableField("lead_type")
    @ExportField(label = "线索类型", order = 10)
    private String leadType;

    @TableField("industry")
    @ExportField(label = "所属行业", order = 11)
    private String industry;

    @TableField("company_scale")
    @ExportField(label = "公司规模", order = 12)
    private String companyScale;

    @TableField("demand_desc")
    @ExportField(label = "需求描述", order = 13)
    private String demandDesc;

    /** 统一社会信用代码（来自采集结果） */
    @TableField("credit_code")
    @ExportField(label = "统一社会信用代码", order = 14)
    private String creditCode;

    /** 注册资本（来自采集结果） */
    @TableField("registered_capital")
    @ExportField(label = "注册资本", order = 15)
    private String registeredCapital;

    /** 注册地址（来自采集结果） */
    @TableField("registered_address")
    @ExportField(label = "注册地址", order = 16)
    private String registeredAddress;

    /** 通信地址（来自采集结果） */
    @TableField("contact_address")
    @ExportField(label = "通信地址", order = 17)
    private String contactAddress;

    /** 所属地区（来自采集结果） */
    @TableField("area")
    @ExportField(label = "所属地区", order = 18)
    private String area;

    /** 成立日期字符串（来自采集结果） */
    @TableField("establish_date")
    @ExportField(label = "成立日期", order = 19)
    private String establishDate;

    /** 企业状态（来自采集结果） */
    @TableField("company_status")
    @ExportField(label = "企业状态", order = 20)
    private String companyStatus;

    /** 来源URL（来自采集结果） */
    @TableField("source_url")
    @ExportField(label = "来源URL", order = 21)
    private String sourceUrl;

    @TableField("estimated_amount")
    @ExportField(label = "预估金额", order = 22)
    private BigDecimal estimatedAmount;

    @TableField("intention_level")
    @ExportField(label = "意向等级", order = 23)
    private String intentionLevel;

    @TableField("lead_status")
    @ExportField(label = "线索状态", order = 24, required = true)
    private String leadStatus;

    @TableField("assigned_sales_id")
    @ExportField(label = "分配销售员ID", order = 25)
    private Long assignedSalesId;

    @TableField("assigned_date")
    @ExportField(label = "分配日期", order = 26, dateFormat = "yyyy-MM-dd")
    private LocalDate assignedDate;

    @TableField("follow_up_count")
    @ExportField(label = "跟进次数", order = 27)
    private Integer followUpCount;

    @TableField("last_follow_up_date")
    @ExportField(label = "最后跟进日期", order = 28, dateFormat = "yyyy-MM-dd")
    private LocalDate lastFollowUpDate;

    @TableField("next_follow_up_date")
    @ExportField(label = "下次跟进日期", order = 29, dateFormat = "yyyy-MM-dd")
    private LocalDate nextFollowUpDate;

    @TableField("converted_customer_id")
    @ExportField(label = "转化客户ID", order = 30)
    private Long convertedCustomerId;

    @TableField("converted_date")
    @ExportField(label = "转化日期", order = 31, dateFormat = "yyyy-MM-dd")
    private LocalDate convertedDate;

    @TableField("abandon_reason")
    @ExportField(label = "放弃原因", order = 32)
    private String abandonReason;

    @TableField("remark")
    @ExportField(label = "备注", order = 33)
    private String remark;

    /** 图片URL（多个逗号分隔，Excel导入内嵌图片自动上传后回填） */
    @TableField("image_url")
    @ExportField(label = "线索图片", order = 34)
    private String imageUrl;

    @TableField("creator_id")
    @ExportField(label = "创建人ID", order = 100, hidden = true)
    private Long creatorId;

    @TableField("create_date")
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createDate;

    /** 创建人（数据权限过滤维度，AuditMetaObjectHandler 自动填充） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField("modifier_id")
    @ExportField(label = "修改人ID", order = 102, hidden = true)
    private Long modifierId;

    @TableField("modify_date")
    @ExportField(label = "修改时间", order = 103, hidden = true)
    private LocalDateTime modifyDate;
}