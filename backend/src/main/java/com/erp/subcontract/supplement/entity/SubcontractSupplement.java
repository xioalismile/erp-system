package com.erp.subcontract.supplement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("subcontract_supplement")
public class SubcontractSupplement {

    @TableId(value = "id", type = IdType.AUTO)
    @ExportField(label = "补料单ID", order = 0, hidden = true)
    private Long id;

    @TableField("supplement_no")
    @ExportField(label = "补料单号", order = 1, required = true)
    private String supplementNo;

    @TableField("order_id")
    @ExportField(label = "委外单ID", order = 2, hidden = true)
    private Long orderId;

    @TableField("order_no")
    @ExportField(label = "委外单号", order = 3)
    private String orderNo;

    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 4, hidden = true)
    private Long supplierId;

    @TableField("supplier_name")
    @ExportField(label = "供应商", order = 5)
    private String supplierName;

    @TableField("supplement_date")
    @ExportField(label = "补料日期", order = 6, dateFormat = "yyyy-MM-dd")
    private LocalDate supplementDate;

    @TableField("status")
    @ExportField(label = "状态", order = 7)
    private Integer status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    @TableField("total_quantity")
    @ExportField(label = "总数量", order = 8)
    private BigDecimal totalQuantity;

    @TableField("reason")
    @ExportField(label = "补料原因", order = 9)
    private String reason;

    @TableField("remark")
    @ExportField(label = "备注", order = 10)
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 100, hidden = true)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 102, hidden = true)
    private LocalDateTime updateTime;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 来源领料单ID（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("pick_id")
    private Long pickId;

    /** 来源领料单号（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("pick_no")
    private String pickNo;

    /** 来源单据类型（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("source_bill_type")
    private String sourceBillType;

    /** 来源单据号（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("close_status")
    private Integer closeStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    @TableField(exist = false)
    private List<SubcontractSupplementItem> items;
}
