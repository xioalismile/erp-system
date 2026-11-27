package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工序 - 对齐 PG workstation 表
 */
@Data
@TableName("workstation")
public class Workstation {

    @TableId(value = "workstation_id", type = IdType.AUTO)
    @ExportField(label = "工序ID", order = 0, hidden = true)
    private Long workstationId;

    @TableField("workstation_code")
    @ExportField(label = "工序编码", order = 1, required = true)
    private String workstationCode;

    @TableField("workstation_name")
    @ExportField(label = "工序名称", order = 2, required = true)
    private String workstationName;

    @TableField("work_center_id")
    @ExportField(label = "工作中心ID", order = 3, hidden = true)
    private Long workCenterId;

    @TableField("workstation_type")
    @ExportField(label = "类型", order = 4)
    private String workstationType;

    @TableField("location")
    @ExportField(label = "位置", order = 5)
    private String location;

    @TableField("is_active")
    @ExportField(label = "是否启用", order = 6)
    private Short isActive;

    @TableField("status")
    @ExportField(label = "状态", order = 7)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 8)
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
