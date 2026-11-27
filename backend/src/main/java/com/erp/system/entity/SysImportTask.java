package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入任务表 - 对齐 PG sys_import_task 表
 * 供设置中心「导入进度」页面展示异步导入任务；当前无写入方，查询返回空列表为正确行为
 */
@Data
@TableName("sys_import_task")
public class SysImportTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    @TableField("task_name")
    private String taskName;

    /** 导入类型 */
    @TableField("import_type")
    private String importType;

    /** 总条数 */
    @TableField("total")
    private Integer total;

    /** 成功条数 */
    @TableField("success_count")
    private Integer successCount;

    /** 失败条数 */
    @TableField("failed_count")
    private Integer failedCount;

    /** 操作人 */
    @TableField("created_by")
    private String createdBy;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 状态：pending|running|success|failed|partial */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ===== 前端展示字段（非表字段，查询后填充） =====

    /** 任务名（=task_name） */
    @TableField(exist = false)
    private String name;

    /** 导入类型（=import_type） */
    @TableField(exist = false)
    private String type;

    /** 成功条数（=success_count） */
    @TableField(exist = false)
    private Integer success;

    /** 失败条数（=failed_count） */
    @TableField(exist = false)
    private Integer failed;

    /** 操作人（=created_by） */
    @TableField(exist = false)
    private String createBy;
}
