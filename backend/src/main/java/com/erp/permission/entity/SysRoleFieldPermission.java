package com.erp.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_field_permission")
public class SysRoleFieldPermission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("role_id")
    private Long roleId;

    @TableField("page_key")
    private String pageKey;

    @TableField("field_key")
    private String fieldKey;

    @TableField("visible")
    private Integer visible;

    @TableField("editable")
    private Integer editable;

    @TableField("required")
    private Integer required;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
