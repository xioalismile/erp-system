package com.erp.version.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据版本记录 - 记录业务表数据变更的前/后快照
 */
@Data
@TableName("data_version_record")
public class DataVersionRecord {

    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    @TableField("table_name")
    private String tableName;

    @TableField("record_id")
    private Long recordId;

    @TableField("record_code")
    private String recordCode;

    @TableField("business_module")
    private String businessModule;

    @TableField("operation_type")
    private String operationType;

    @TableField("before_snapshot")
    private String beforeSnapshot;

    @TableField("after_snapshot")
    private String afterSnapshot;

    @TableField("version_number")
    private Long versionNumber;

    @TableField("operator")
    private String operator;

    @TableField("operate_time")
    private LocalDateTime operateTime;

    @TableField("rolled_back")
    private Short rolledBack;

    @TableField("rollback_enabled")
    private Short rollbackEnabled;

    @TableField("remark")
    private String remark;
}
