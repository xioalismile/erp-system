package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 其他出库单(对应 other_issue 表)
 */
@Data
@TableName("other_issue")
public class OtherIssue {

    @TableId(value = "issue_id", type = IdType.AUTO)
    private Long issueId;

    @TableField("issue_no")
    private String issueNo;

    @TableField("issue_date")
    private LocalDate issueDate;

    @TableField("issue_type")
    private String issueType;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("source_bill_type")
    private String sourceBillType;

    @TableField("source_bill_no")
    private String sourceBillNo;

    @TableField("remark")
    private String remark;

    @TableField("attachment_urls")
    private String attachmentUrls;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;

    /** 其他出库明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<OtherIssueItem> items;
}
