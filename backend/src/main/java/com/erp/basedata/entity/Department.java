package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门 - 对齐 PG department 表
 */
@Data
@TableName("department")
public class Department {

    @TableId(value = "dept_id", type = IdType.AUTO)
    private Long deptId;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("parent_dept_id")
    private Long parentDeptId;

    @TableField("dept_type")
    private String deptType;

    @TableField("manager_id")
    private Long managerId;

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
