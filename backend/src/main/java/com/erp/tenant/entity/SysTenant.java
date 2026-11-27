package com.erp.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(value = "tenant_id", type = IdType.AUTO)
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private String dbName;
    private String dbHost;
    private Integer dbPort;
    private String dbUsername;
    private String dbPassword;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private Integer maxUsers;
    private String status;
    private LocalDate expireDate;
    private String remark;
    private String version;
    private String features;
    private Integer trialDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
