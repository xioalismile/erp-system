package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
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
    @ExportField(label = "出库单ID", order = 1, hidden = true)
    private Long issueId;

    @TableField("issue_no")
    @ExportField(label = "出库单号", order = 2, required = true)
    private String issueNo;

    @TableField("issue_date")
    @ExportField(label = "出库日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate issueDate;

    @TableField("issue_type")
    @ExportField(label = "出库类型", order = 4, required = true)
    private String issueType;

    @TableField("warehouse_id")
    @ExportField(label = "仓库ID", order = 5, required = true)
    private Long warehouseId;

    @TableField("handler_id")
    @ExportField(label = "经办人ID", order = 6)
    private Long handlerId;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 7)
    private Long departmentId;

    @TableField("currency_id")
    @ExportField(label = "币种ID", order = 8)
    private Long currencyId;

    @TableField("exchange_rate")
    @ExportField(label = "汇率", order = 9, numberFormat = "0.00")
    private BigDecimal exchangeRate;

    @TableField("total_amount")
    @ExportField(label = "出库总额", order = 10, numberFormat = "0.00")
    private BigDecimal totalAmount;

    @TableField("source_bill_type")
    @ExportField(label = "源单类型", order = 11)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "源单编号", order = 12)
    private String sourceBillNo;

    @TableField("remark")
    @ExportField(label = "备注", order = 13)
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

    /** 作废前状态（数字code，供反作废恢复；统一状态机抽象方法要求） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 结案状态：0=未结案 1=已结案（前端反结案按钮按此字段判断；本模块 /close 同时将 status 置为"已关闭"） */
    @TableField("close_status")
    private Integer closeStatus;
    @TableField("closed_by")
    private String closedBy;
    @TableField("closed_at")
    private java.time.LocalDateTime closedAt;
    @TableField("closed_reason")
    private String closedReason;

    /** 业务执行态（bus_status）。与 status(审批态)、close_status(完结标志) 三字段解耦；迁移期双写，详见 BusStatusEnum 注释 */
    @TableField("bus_status")
    private String busStatus;

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

    /** 表头折扣金额（其他出库单分摊折扣 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /** 表头税额（其他出库单税额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 表头净额（扣除折扣与税额后的净额 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 表头「计税方式」（含税价 / 不含税价 / 不计税）：映射数据库列 tax_calc_method，随单据保存/回读；明细据此换算 price_with_tax / price_without_tax */
    @TableField("tax_calc_method")
    private String taxCalcMethod;

    /** 其他出库明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<OtherIssueItem> items;
}
