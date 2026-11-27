package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联表 - 对齐 PG sys_user_role 表
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(value = "user_role_id", type = IdType.AUTO)
    private Long userRoleId;

    @TableField("user_id")
    private Long userId;

    @TableField("role_id")
    private Long roleId;

    @TableField(value = "effective_date", exist = false)
    private java.time.LocalDate effectiveDate;

    @TableField(value = "expiry_date", exist = false)
    private java.time.LocalDate expiryDate;

    @TableField("is_primary_role")
    private Short isPrimaryRole;

    @TableField("assign_date")
    private java.time.LocalDate assignDate;

    @TableField("assign_by")
    private String assignBy;

    @TableField("data_scope_type")
    private String dataScopeType;

    @TableField("data_scope_value")
    private String dataScopeValue;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;
}
