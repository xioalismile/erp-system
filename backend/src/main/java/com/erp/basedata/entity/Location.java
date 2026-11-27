package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 货位 - 对齐 PG location 表
 */
@Data
@TableName("location")
public class Location {

    @TableId(value = "bin_id", type = IdType.AUTO)
    @ExportField(label = "货位ID", order = 0, hidden = true)
    private Long binId;

    @TableField("bin_code")
    @ExportField(label = "货位编码", order = 1, required = true)
    private String binCode;

    @TableField("bin_name")
    @ExportField(label = "货位名称", order = 2, required = true)
    private String binName;

    @TableField("stock_id")
    @ExportField(label = "所属仓库ID", order = 3, required = true)
    private Long stockId;

    @TableField("status")
    @ExportField(label = "状态", order = 4)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 5)
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
