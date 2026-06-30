package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色主表 - 对齐 PG sys_role 表
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    @TableField("role_code")
    private String roleCode;

    @TableField("role_name")
    private String roleName;

    @TableField("role_type")
    private String roleType;

    @TableField("data_scope")
    private String dataScope;

    @TableField("data_scope_value")
    private String dataScopeValue;

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

    /** 菜单ID列表(非数据库字段, 用于分配权限) */
    @TableField(exist = false)
    private List<Long> menuIds;
}
