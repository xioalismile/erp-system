package com.erp.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打样单实体（对应 sample_order 表）
 * 文档5.9 表头字段（28字段）
 * 单据状态流转: 草稿 → 待审核 → 已生效 → 打样中 → 已完成 → 已确认 → 已转BOM；已确认前可取消
 */
@Data
@TableName("sample_order")
public class SampleOrder {

    @TableId(value = "sample_id", type = IdType.AUTO)
    private Long sampleId;

    /** 打样单号 SAM-yyyyMMdd-0001 */
    @TableField("sample_no")
    private String sampleNo;

    @TableField("sample_name")
    private String sampleName;

    /** 样品类型: 新产品/改进产品/来料打样/内部研发 */
    @TableField("sample_type")
    private String sampleType;

    /** 打样阶段: 原样/初样/确认样/大货样 */
    @TableField("sample_stage")
    private String sampleStage;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("contact_person")
    private String contactPerson;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    @TableField("sample_qty")
    private BigDecimal sampleQty;

    @TableField("demand_date")
    private LocalDate demandDate;

    @TableField("plan_finish_date")
    private LocalDate planFinishDate;

    @TableField("actual_finish_date")
    private LocalDate actualFinishDate;

    @TableField("department_id")
    private Long departmentId;

    @TableField("engineer_id")
    private Long engineerId;

    /** 优先级: 紧急/高/普通/低 */
    @TableField("priority")
    private String priority;

    @TableField("cost_amount")
    private BigDecimal costAmount;

    /** 价税合计（2026-11-23 补齐，口径见 deploy/sql/196） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 状态: 草稿/待审核/已生效/打样中/已完成/已确认/已转BOM/已取消 */
    @TableField("status")
    private String status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    /** 审核人（免审批自动生效时留空） */
    @TableField("approved_by")
    private String approvedBy;

    /** 审核时间 */
    @TableField("approved_at")
    private LocalDateTime approvedAt;

    /** 作废前状态（数字code，供反作废恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 打样进度: 自定义文本，如"图纸设计/物料准备/加工中/组装调试/待验收" */
    @TableField("sample_progress")
    private String progress;

    /** 是否已转BOM: 0-未转, 1-已转 */
    @TableField("is_bom_generated")
    private Short isBomGenerated;

    @TableField("generated_bom_id")
    private Long generatedBomId;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("org_code")
    private String orgCode;

    /** 结案状态: 0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：打通打样单编辑页的附件上传） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 表头「计税方式」（含税价/不含税价/不计税）：映射数据库列 tax_calc_method，随单据保存/回读并驱动明细换算 */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    /** 打样子件清单（非数据库字段） */
    @TableField(exist = false)
    private List<SampleOrderItem> items;
}
