package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工序汇报单 - 对齐 PG operation_report 表
 */
@Data
@TableName("operation_report")
public class OperationReport {

    @TableId(value = "report_id", type = IdType.AUTO)
    @ExportField(label = "汇报单ID", order = 0, hidden = true)
    private Long reportId;

    @TableField("report_no")
    @ExportField(label = "汇报单号", order = 1, required = true)
    private String reportNo;

    @TableField("report_date")
    @ExportField(label = "汇报日期", order = 2, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate reportDate;

    @TableField("production_order_id")
    @ExportField(label = "生产工单ID", order = 3, hidden = true)
    private Long productionOrderId;

    @TableField("production_order_no")
    @ExportField(label = "生产工单号", order = 4)
    private String productionOrderNo;

    @TableField("process_no")
    @ExportField(label = "工序号", order = 5)
    private String processNo;

    @TableField("work_center_id")
    @ExportField(label = "工作中心ID", order = 6, hidden = true)
    private Long workCenterId;

    @TableField("workstation_id")
    @ExportField(label = "工作站ID", order = 7, hidden = true)
    private Long workstationId;

    @TableField("operation_id")
    @ExportField(label = "工序ID", order = 8, hidden = true)
    private Long operationId;

    @TableField("operation_name")
    @ExportField(label = "工序名称", order = 9)
    private String operationName;

    @TableField("employee_id")
    @ExportField(label = "员工ID", order = 10, hidden = true)
    private Long employeeId;

    @TableField("employee_name")
    @ExportField(label = "员工姓名", order = 11)
    private String employeeName;

    @TableField("report_type")
    @ExportField(label = "汇报类型", order = 12)
    private String reportType;

    @TableField("total_quantity")
    @ExportField(label = "总数量", order = 13, numberFormat = "0.00")
    private BigDecimal totalQuantity;

    @TableField("qualified_quantity")
    @ExportField(label = "合格数量", order = 14, numberFormat = "0.00")
    private BigDecimal qualifiedQuantity;

    @TableField("rejected_quantity")
    @ExportField(label = "报废数量", order = 15, numberFormat = "0.00")
    private BigDecimal rejectedQuantity;

    @TableField("work_hours")
    @ExportField(label = "工时", order = 16, numberFormat = "0.00")
    private BigDecimal workHours;

    @TableField("overtime_hours")
    @ExportField(label = "加班工时", order = 17, numberFormat = "0.00")
    private BigDecimal overtimeHours;

    @TableField("shift_type")
    @ExportField(label = "班次", order = 18)
    private String shiftType;

    @TableField("remark")
    @ExportField(label = "备注", order = 19)
    private String remark;

    @TableField("source_bill_type")
    @ExportField(label = "来源单据类型", order = 21)
    private String sourceBillType;

    @TableField("source_bill_no")
    @ExportField(label = "来源单据号", order = 22)
    private String sourceBillNo;

    @TableField("status")
    @ExportField(label = "状态", order = 23)
    private String status;

    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 24)
    private String approvalStatus;

    @TableField("approved_by")
    @ExportField(label = "审核人", order = 25)
    private String approvedBy;

    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 26, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 结案状态: 0=未结案 1=已结案 */
    @TableField("close_status")
    private Integer closeStatus;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字code，反作废时恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    @TableField("voided_by")
    private String voidedBy;

    @TableField("voided_at")
    private LocalDateTime voidedAt;

    @TableField("void_reason")
    private String voidReason;

    /** 附件URL列表（多附件以逗号分隔，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("attachment_urls")
    private String attachmentUrls;

    /** 使用设备（前端为自由文本输入，2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("equipment_id")
    private String equipmentId;

    /** 工作中心名称（2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("work_center_name")
    private String workCenterName;
}
