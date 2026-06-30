package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户主表 - 对齐 PG sys_user 表
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    @TableField("user_code")
    private String userCode;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("real_name")
    private String realName;

    @TableField("email")
    private String email;

    @TableField("mobile_phone")
    private String mobilePhone;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("dept_id")
    private Long deptId;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("user_type")
    private String userType;

    @TableField("auth_type")
    private String authType;

    @TableField("is_admin")
    private Short isAdmin;

    @TableField("is_enabled")
    private Short isEnabled;

    @TableField("is_locked")
    private Short isLocked;

    @TableField("lock_time")
    private LocalDateTime lockTime;

    @TableField("password_expire_time")
    private LocalDateTime passwordExpireTime;

    @TableField("password_change_time")
    private LocalDateTime passwordChangeTime;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("login_attempts")
    private Integer loginAttempts;

    @TableField("status")
    private String status;

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

    /** 角色ID列表(非数据库字段, 用于分配角色) */
    @TableField(exist = false)
    private List<Long> roleIds;
}
