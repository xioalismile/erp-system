package com.erp.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_tenant")
public class SysUserTenant {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tenantId;
    private Short isAdminInTenant;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
