package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_survey")
public class Survey {

    @TableId(value = "survey_id", type = IdType.AUTO)
    @ExportField(label = "调研ID", order = 0, hidden = true)
    private Long surveyId;

    @TableField("survey_no")
    @ExportField(label = "调研编号", order = 1, required = true)
    private String surveyNo;

    @TableField("survey_name")
    @ExportField(label = "调研名称", order = 2, required = true)
    private String surveyName;

    /** 调查类型: 交易后/定期回访/新品反馈/服务评价/其他 */
    @TableField("survey_type")
    @ExportField(label = "调研类型", order = 3)
    private String surveyType;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 4, required = true)
    private Long customerId;

    @TableField("customer_name")
    @ExportField(label = "客户名称", order = 5)
    private String customerName;

    @TableField("contact_person")
    @ExportField(label = "联系人", order = 6)
    private String contactPerson;

    @TableField("survey_date")
    @ExportField(label = "调研日期", order = 7, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate surveyDate;

    /** 调查方式: 电话/问卷/在线评价/现场面谈/其他 */
    @TableField("survey_method")
    @ExportField(label = "调研方式", order = 8)
    private String surveyMethod;

    /** 总体评分: 1-5, 默认0 */
    @TableField("overall_score")
    @ExportField(label = "总体评分", order = 9)
    private Integer overallScore;

    @TableField("product_score")
    @ExportField(label = "产品评分", order = 10)
    private Integer productScore;

    @TableField("service_score")
    @ExportField(label = "服务评分", order = 11)
    private Integer serviceScore;

    @TableField("price_score")
    @ExportField(label = "价格评分", order = 12)
    private Integer priceScore;

    @TableField("delivery_score")
    @ExportField(label = "交付评分", order = 13)
    private Integer deliveryScore;

    @TableField("after_sales_score")
    @ExportField(label = "售后评分", order = 14)
    private Integer afterSalesScore;

    /** 表扬内容: TEXT */
    @TableField("praise_content")
    @ExportField(label = "表扬内容", order = 15)
    private String praiseContent;

    /** 批评内容: TEXT */
    @TableField("criticism_content")
    @ExportField(label = "批评内容", order = 16)
    private String criticismContent;

    /** 改进计划: TEXT */
    @TableField("improvement_plan")
    @ExportField(label = "改进计划", order = 17)
    private String improvementPlan;

    /** 状态: 草稿/进行中/已完成/已分析, 默认'草稿' */
    @TableField("status")
    @ExportField(label = "状态", order = 18)
    private String status;

    @TableField("analyze_result")
    @ExportField(label = "分析结果", order = 19)
    private String analyzeResult;

    @TableField("remark")
    @ExportField(label = "备注", order = 20)
    private String remark;

    @TableField("handler_id")
    @ExportField(label = "处理人ID", order = 21)
    private Long handlerId;

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