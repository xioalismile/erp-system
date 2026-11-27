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

    @TableField(exist = false)
    private String parentRoleCode;

    @TableField(exist = false)
    private Integer userCount;

    @TableField(exist = false)
    private java.time.LocalDate effectiveDate;

    @TableField(exist = false)
    private java.time.LocalDate expiryDate;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 菜单ID列表(非数据库字段, 用于分配权限, 存储的是菜单path如 /sale/orders) */
    @TableField(exist = false)
    private List<String> menuIds;
}
