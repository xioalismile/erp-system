package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material_category")
public class MaterialCategory {

    @TableId(value = "category_id", type = IdType.AUTO)
    @ExportField(label = "分类ID", order = 0, hidden = true)
    private Long categoryId;

    @TableField("category_code")
    @ExportField(label = "分类编码", order = 1, required = true)
    private String categoryCode;

    @TableField("category_name")
    @ExportField(label = "分类名称", order = 2, required = true)
    private String categoryName;

    @TableField("parent_id")
    @ExportField(label = "上级分类ID", order = 3)
    private Long parentId;

    /** 上级分类编码（导入用，非持久化字段） */
    @TableField(exist = false)
    @ExportField(label = "上级分类编码", order = 3)
    private String parentCode;

    @TableField("category_level")
    @ExportField(label = "分类级别", order = 4)
    private Integer categoryLevel;

    @TableField("sort_order")
    @ExportField(label = "排序号", order = 5)
    private Integer sortOrder;

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