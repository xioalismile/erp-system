package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺路线「某道工序」自己的自定义字段定义。
 *
 * <p>字段只属于该路线该工序；工单工序实例生成时按 operation_key 取自己的字段，
 * 值仍存通用自定义字段值表（工序实例 ID + 镜像字段 key）。</p>
 */
@Data
@TableName("routing_operation_field")
public class RoutingOperationField {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("routing_id")
    private Long routingId;

    /** 工序稳定标识（routing_operation.operation_key） */
    @TableField("operation_key")
    private String operationKey;

    @TableField("field_label")
    private String fieldLabel;

    /** text / textarea / number / select / date / switch */
    @TableField("field_type")
    private String fieldType;

    /** select 选项 JSON：[{"label":"x","value":"x"}] */
    @TableField("field_options")
    private String fieldOptions;

    @TableField("is_required")
    private Short isRequired;

    @TableField("sort_no")
    private Integer sortNo;

    /** 镜像到 sys_custom_field 的字段ID（值表/打印共用该字段 key） */
    @TableField("custom_field_id")
    private Long customFieldId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 非库字段：镜像到通用自定义字段的 key（前端据此取值/打印） */
    @TableField(exist = false)
    private String customFieldKey;
}
