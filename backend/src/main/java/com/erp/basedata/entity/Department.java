package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("department")
public class Department {

    @TableId(value = "dept_id", type = IdType.AUTO)
    @ExportField(label = "部门ID", order = 0, hidden = true)
    private Long deptId;

    @TableField("dept_code")
    @ExportField(label = "部门编码", order = 1, required = true)
    private String deptCode;

    @TableField("dept_name")
    @ExportField(label = "部门名称", order = 2, required = true)
    private String deptName;

    @TableField("parent_dept_id")
    @ExportField(label = "上级部门ID", order = 3)
    private Long parentDeptId;

    @TableField("dept_type")
    @ExportField(label = "部门类型", order = 4)
    private String deptType;

    @TableField("manager_id")
    @ExportField(label = "部门经理ID", order = 5)
    private Long managerId;

    @TableField("status")
    @ExportField(label = "状态", order = 6)
    private String status;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 7)
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