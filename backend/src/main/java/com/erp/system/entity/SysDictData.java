package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据字典明细表 - 对齐 PG sys_dict_data 表
 */
@Data
@TableName("sys_dict_data")
public class SysDictData {

    @TableId(value = "dict_data_id", type = IdType.AUTO)
    private Long dictDataId;

    @TableField("dict_type_id")
    private Long dictTypeId;

    @TableField("dict_label")
    private String dictLabel;

    @TableField("dict_value")
    private String dictValue;

    @TableField("dict_sort")
    private Integer dictSort;

    @TableField("is_default")
    private Short isDefault;

    @TableField("is_enabled")
    private Short isEnabled;

    @TableField("css_class")
    private String cssClass;

    @TableField("list_class")
    private String listClass;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
