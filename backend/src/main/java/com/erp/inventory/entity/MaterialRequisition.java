package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产领料单(对应 material_requisition 表)
 */
@Data
@TableName("material_requisition")
public class MaterialRequisition {

    @TableId(value = "requisition_id", type = IdType.AUTO)
    private Long requisitionId;

    @TableField("requisition_no")
    private String requisitionNo;

    @TableField("requisition_date")
    private LocalDate requisitionDate;

    @TableField("work_order_no")
    private String workOrderNo;

    @TableField("department_id")
    private Long departmentId;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("warehouse_id")
    private Long warehouseId;

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

    /** 生产领料明细列表(非数据库字段) */
    @TableField(exist = false)
    private List<MaterialRequisitionItem> items;
}
