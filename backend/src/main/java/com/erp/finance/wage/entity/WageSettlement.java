package com.erp.finance.wage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序工资核算单 - 对齐 PG wage_settlement 表（一张单 = 一个期间，明细行 = 员工）
 */
@Data
@TableName("wage_settlement")
public class WageSettlement {

    @TableId(value = "settlement_id", type = IdType.AUTO)
    private Long settlementId;

    /** 单据号 WG-yyyyMMdd-0001 */
    @TableField("settlement_no")
    private String settlementNo;

    /** 核算期间 yyyy-MM */
    @TableField("period")
    private String period;

    @TableField("dept_id")
    private Long deptId;

    @TableField("dept_name")
    private String deptName;

    /** 生成时的员工筛选（为空 = 全部员工） */
    @TableField("employee_id")
    private Long employeeId;

    @TableField("employee_name")
    private String employeeName;

    @TableField("qualified_quantity")
    private BigDecimal qualifiedQuantity;

    @TableField("piece_amount")
    private BigDecimal pieceAmount;

    @TableField("time_amount")
    private BigDecimal timeAmount;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 草稿 / 已生效 / 已作废 */
    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField(value = "approved_by", updateStrategy = FieldStrategy.IGNORED)
    private String approvedBy;

    @TableField(value = "approved_at", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime approvedAt;

    /** 结案状态：0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField(value = "void_reason", updateStrategy = FieldStrategy.IGNORED)
    private String voidReason;

    @TableField(value = "voided_by", updateStrategy = FieldStrategy.IGNORED)
    private String voidedBy;

    @TableField(value = "voided_at", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime voidedAt;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 明细行（非数据库字段） */
    @TableField(exist = false)
    private List<WageSettlementItem> items;
}
