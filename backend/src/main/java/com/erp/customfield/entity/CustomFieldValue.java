package com.erp.customfield.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义字段值（EAV 模式）
 */
@Data
@TableName("sys_custom_field_value")
public class CustomFieldValue {

    @TableId(value = "value_id", type = IdType.AUTO)
    private Long valueId;

    @TableField("entity_type")
    private String entityType;

    @TableField("entity_id")
    private Long entityId;

    @TableField("field_key")
    private String fieldKey;

    /** 统一文本存储，读取时按 field_type 转换 */
    @TableField("field_value")
    private String fieldValue;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
