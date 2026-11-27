package com.erp.serialno.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_serial_no_config")
public class SysSerialNoConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("doc_type")
    private String docType;

    @TableField("doc_name")
    private String docName;

    @TableField("prefix")
    private String prefix;

    @TableField("date_format")
    private String dateFormat;

    @TableField("serial_length")
    private Integer serialLength;

    @TableField("reset_type")
    private String resetType;

    @TableField("separator")
    private String separator;

    @TableField("current_serial")
    private Integer currentSerial;

    @TableField("\"current_date\"")
    private String currentDate;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("enabled")
    private Integer enabled;

    @TableField("auto_generate")
    private Integer autoGenerate;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
