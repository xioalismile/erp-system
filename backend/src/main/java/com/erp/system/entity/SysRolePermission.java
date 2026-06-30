package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限表 - 对齐 PG sys_role_permission 表
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(value = "permission_id", type = IdType.AUTO)
    private Long permissionId;

    @TableField("role_id")
    private Long roleId;

    @TableField("menu_id")
    private Long menuId;

    @TableField("permission_type")
    private String permissionType;

    @TableField("permission_value")
    private Integer permissionValue;

    @TableField("is_inherited")
    private Short isInherited;

    @TableField("parent_role_id")
    private Long parentRoleId;

    @TableField("data_scope_type")
    private String dataScopeType;

    @TableField("data_scope_value")
    private String dataScopeValue;

    @TableField("org_scope_type")
    private String orgScopeType;

    @TableField("org_scope_value")
    private String orgScopeValue;

    @TableField("field_permissions")
    private String fieldPermissions;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private String updatedBy;
}
