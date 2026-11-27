package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志变更明细表 - 对齐 PG sys_operate_log_detail 表
 */
@Data
@TableName("sys_operate_log_detail")
public class SysOperateLogDetail {

    @TableId(value = "detail_id", type = IdType.AUTO)
    private Long detailId;

    @TableField("log_id")
    private Long logId;

    @TableField("field_name")
    private String fieldName;

    @TableField("field_desc")
    private String fieldDesc;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    @TableField("data_type")
    private String dataType;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
