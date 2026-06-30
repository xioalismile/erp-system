package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据字典类型表 - 对齐 PG sys_dict_type 表
 */
@Data
@TableName("sys_dict_type")
public class SysDictType {

    @TableId(value = "dict_type_id", type = IdType.AUTO)
    private Long dictTypeId;

    @TableField("dict_type_code")
    private String dictTypeCode;

    @TableField("dict_type_name")
    private String dictTypeName;

    @TableField("dict_category")
    private String dictCategory;

    @TableField("is_system")
    private Short isSystem;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("remark")
    private String remark;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    /** 字典数据列表(非数据库字段) */
    @TableField(exist = false)
    private List<SysDictData> dictDataList;
}
