package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预算单主表 - 对齐 PG budget 表
 */
@Data
@TableName("budget")
public class Budget {

    @TableId(value = "budget_id", type = IdType.AUTO)
    private Long budgetId;

    @TableField("budget_code")
    private String budgetCode;

    @TableField("budget_name")
    private String budgetName;

    @TableField("budget_type")
    private String budgetType;

    @TableField("period_year")
    private Integer periodYear;

    @TableField("period_month")
    private Integer periodMonth;

    @TableField("department_id")
    private Long departmentId;

    @TableField("project_code")
    private String projectCode;

    @TableField("budget_amount")
    private BigDecimal budgetAmount;

    @TableField("used_amount")
    private BigDecimal usedAmount;

    @TableField("available_amount")
    private BigDecimal availableAmount;

    @TableField("control_level")
    private String controlLevel;

    @TableField("is_enabled")
    private Short isEnabled;

    @TableField("effective_date")
    private LocalDate effectiveDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("remark")
    private String remark;

    @TableField("budget_period")
    private String budgetPeriod;

    @TableField("warn_rate")
    private BigDecimal warnRate;

    @TableField("executed_rate")
    private BigDecimal executedRate;

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

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField("org_code")
    private String orgCode;
}
