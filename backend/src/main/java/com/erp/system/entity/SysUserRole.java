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

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("created_by")
    private String createdBy;
}
