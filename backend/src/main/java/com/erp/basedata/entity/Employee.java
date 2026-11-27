package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("employee")
public class Employee {

    @TableId(value = "employee_id", type = IdType.AUTO)
    @ExportField(label = "员工ID", order = 0, hidden = true)
    private Long employeeId;

    @TableField("employee_code")
    @ExportField(label = "员工编码", order = 1, required = true)
    private String employeeCode;

    @TableField("employee_name")
    @ExportField(label = "员工姓名", order = 2, required = true)
    private String employeeName;

    @TableField("dept_id")
    @ExportField(label = "部门ID", order = 3)
    private Long deptId;

    /** 部门名称（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private String deptName;

    @TableField("position")
    @ExportField(label = "职位", order = 4)
    private String position;

    /** 工时费率（元/小时）：工序工资核算的计时工资取数来源；空/0 = 不计算计时工资（2026-09-27 第5期） */
    @TableField("hourly_rate")
    private java.math.BigDecimal hourlyRate;

    @TableField("gender")
    @ExportField(label = "性别", order = 5)
    private String gender;

    @TableField("mobile_phone")
    @ExportField(label = "手机号", order = 6)
    private String mobilePhone;

    @TableField("email")
    @ExportField(label = "邮箱", order = 7)
    private String email;

    @TableField("entry_date")
    @ExportField(label = "入职日期", order = 8, dateFormat = "yyyy-MM-dd")
    private LocalDate entryDate;

    @TableField("user_id")
    @ExportField(label = "用户ID", order = 9)
    private Long userId;

    @TableField("status")
    @ExportField(label = "状态", order = 10)
    private String status;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String password;

    @TableField(exist = false)
    private Long roleId;

    @TableField("leave_date")
    @ExportField(label = "离职日期", order = 11, dateFormat = "yyyy-MM-dd")
    private LocalDate leaveDate;

    @TableField("education")
    @ExportField(label = "学历", order = 12)
    private String education;

    @TableField("id_card")
    @ExportField(label = "身份证号", order = 13)
    private String idCard;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 14)
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 102, hidden = true)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新人", order = 103, hidden = true)
    private String updatedBy;
}