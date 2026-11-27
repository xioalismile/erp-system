package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工作中心 - 对齐 PG work_center 表
 */
@Data
@TableName("work_center")
public class WorkCenter {

    @TableId(value = "work_center_id", type = IdType.AUTO)
    @ExportField(label = "工作中心ID", order = 0, hidden = true)
    private Long workCenterId;

    @TableField("work_center_code")
    @ExportField(label = "工作中心编码", order = 1, required = true)
    private String workCenterCode;

    @TableField("work_center_name")
    @ExportField(label = "工作中心名称", order = 2, required = true)
    private String workCenterName;

    /** 类型：设备/设备组/生产线/车间/工作单元 */
    @TableField("work_center_type")
    @ExportField(label = "类型", order = 3)
    private String workCenterType;

    @TableField("department_id")
    @ExportField(label = "部门ID", order = 4, hidden = true)
    private Long departmentId;

    @TableField("manager_id")
    @ExportField(label = "负责人ID", order = 5, hidden = true)
    private Long managerId;

    @TableField("location")
    @ExportField(label = "位置", order = 6)
    private String location;

    @TableField("capacity_type")
    @ExportField(label = "产能类型", order = 7)
    private String capacityType;

    @TableField("capacity_per_day")
    @ExportField(label = "日产能", order = 8)
    private BigDecimal capacityPerDay;

    /** 产能单位：小时/件数 */
    @TableField("capacity_unit")
    @ExportField(label = "产能单位", order = 9)
    private String capacityUnit;

    @TableField("efficiency")
    @ExportField(label = "效率", order = 10)
    private BigDecimal efficiency;

    /** 人工费率 */
    @TableField("labor_rate")
    @ExportField(label = "人工费率", order = 11)
    private BigDecimal laborRate;

    /** 制造费用率 */
    @TableField("overhead_rate")
    @ExportField(label = "制造费用率", order = 12)
    private BigDecimal overheadRate;

    /** 最大操作人数 */
    @TableField("max_workers")
    @ExportField(label = "最大操作人数", order = 13)
    private Integer maxWorkers;

    /** 是否委外 */
    @TableField("is_outsourced")
    @ExportField(label = "是否委外", order = 14)
    private Short isOutsourced;

    /** 委外供应商 */
    @TableField("supplier_id")
    @ExportField(label = "供应商ID", order = 15, hidden = true)
    private Long supplierId;

    @TableField("is_active")
    @ExportField(label = "是否启用", order = 16)
    private Short isActive;

    @TableField("status")
    @ExportField(label = "状态", order = 17)
    private String status;

    @TableField("remark")
    @ExportField(label = "备注", order = 18)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 102, hidden = true)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新人", order = 103, hidden = true)
    private String updatedBy;
}
