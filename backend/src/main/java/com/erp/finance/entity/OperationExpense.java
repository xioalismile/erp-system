package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 运营费用实体
 * 对应表: operation_expense
 * 来源: 2026-07-01 客户录音访谈需求
 *
 * 单号格式: OE-yyyyMMdd-0001
 * 状态流转: 草稿 → 待审核 → 已生效
 * 费用类型: 出差 / 宴请 / 快递 / 其他
 */
@Data
@TableName(value = "operation_expense", autoResultMap = true)
public class OperationExpense {

    /** 费用ID */
    @TableId(value = "expense_id", type = IdType.AUTO)
    @ExportField(label = "费用ID", order = 1, hidden = true)
    private Long expenseId;

    /** 费用单号: OE-yyyyMMdd-0001 */
    @TableField("expense_no")
    @ExportField(label = "费用单号", order = 2, required = true)
    private String expenseNo;

    /** 费用类型: 出差/宴请/快递/其他 */
    @TableField("expense_type")
    @ExportField(label = "费用类型", order = 3, required = true)
    private String expenseType;

    /** 费用日期 */
    @TableField("expense_date")
    @ExportField(label = "费用日期", order = 4, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate expenseDate;

    /** 金额 */
    @TableField("amount")
    @ExportField(label = "金额", order = 5, numberFormat = "0.00", required = true)
    private BigDecimal amount;

    /** 关联单据类型: 销售订单/采购订单/无 */
    @TableField("related_order_type")
    @ExportField(label = "关联单据类型", order = 6)
    private String relatedOrderType;

    /** 关联单据ID */
    @TableField("related_order_id")
    @ExportField(label = "关联单据ID", order = 7, hidden = true)
    private Long relatedOrderId;

    /** 关联单据编号 */
    @TableField("related_order_no")
    @ExportField(label = "关联单据编号", order = 8)
    private String relatedOrderNo;

    /** 申请人ID */
    @TableField("applicant_id")
    @ExportField(label = "申请人ID", order = 9, hidden = true)
    private Long applicantId;

    /** 申请人姓名 */
    @TableField("applicant_name")
    @ExportField(label = "申请人", order = 10)
    private String applicantName;

    /** 部门ID */
    @TableField("department_id")
    @ExportField(label = "部门ID", order = 11, hidden = true)
    private Long departmentId;

    /** 部门名称 */
    @TableField("department_name")
    @ExportField(label = "部门", order = 12)
    private String departmentName;

    /** 分摊方式: 平均分摊/按数量分摊/按金额分摊/自定义 */
    @TableField("allocation_method")
    @ExportField(label = "分摊方式", order = 13)
    private String allocationMethod;

    /** 状态: 草稿/待审核/已生效 */
    @TableField("status")
    @ExportField(label = "状态", order = 14, hidden = true)
    private String status;

    /** 审批状态文本(与全系统口径一致: 草稿/待审核/已生效/已作废/驳回) */
    @TableField("approval_status")
    private String approvalStatus;

    /** 审核人 */
    @TableField("approved_by")
    @ExportField(label = "审核人", order = 15, hidden = true)
    private String approvedBy;

    /** 审核时间 */
    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 16, hidden = true)
    private LocalDateTime approvedAt;

    /** 结案状态：0=未结案 1=已结案（结案后 status 保持已生效，前端按此字段渲染已结案） */
    @TableField("close_status")
    @ExportField(label = "结案状态", order = 17, hidden = true)
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，供反作废恢复；统一状态机要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;
    @TableField("voided_by")
    private String voidedBy;
    @TableField("voided_at")
    private LocalDateTime voidedAt;
    @TableField("void_reason")
    private String voidReason;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 18)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 费用明细行JSON(持久化, 存储多行费用类型+金额+备注) */
    @TableField(value = "expense_lines_json", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> expenseLinesJson;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 费用分摊明细列表(非数据库字段, 审核后自动汇总到成本核算单) */
    @TableField(exist = false)
    private List<OperationExpenseAllocation> allocations;

    /** 费用明细行(非数据库字段, 供前端使用, 与expenseLinesJson互转) */
    @TableField(exist = false)
    private List<Map<String, Object>> expenseLines;
}
