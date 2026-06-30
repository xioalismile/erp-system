package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工 - 对齐 PG employee 表
 */
@Data
@TableName("employee")
public class Employee {

    @TableId(value = "employee_id", type = IdType.AUTO)
    private Long employeeId;

    @TableField("employee_code")
    private String employeeCode;

    @TableField("employee_name")
    private String employeeName;

    @TableField("dept_id")
    private Long deptId;

    @TableField("position")
    private String position;

    @TableField("gender")
    private String gender;

    @TableField("mobile_phone")
    private String mobilePhone;

    @TableField("email")
    private String email;

    @TableField("entry_date")
    private LocalDate entryDate;

    @TableField("user_id")
    private Long userId;

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
}
