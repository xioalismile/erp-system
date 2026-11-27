package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("unit_of_measure")
public class UnitOfMeasure {

    @TableId(value = "unit_id", type = IdType.AUTO)
    @ExportField(label = "计量单位ID", order = 0, hidden = true)
    private Long unitId;

    @TableField("unit_code")
    @ExportField(label = "计量单位编码", order = 1, required = true)
    private String unitCode;

    @TableField("unit_name")
    @ExportField(label = "计量单位名称", order = 2, required = true)
    private String unitName;

    @TableField("unit_group")
    @ExportField(label = "计量单位组", order = 3)
    private String unitGroup;

    @TableField("is_base_unit")
    @ExportField(label = "是否基本单位", order = 4)
    private Short isBaseUnit;

    @TableField("conversion_rate")
    @ExportField(label = "换算率", order = 5)
    private java.math.BigDecimal conversionRate;

    @TableField("status")
    @ExportField(label = "状态", order = 6)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 7)
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