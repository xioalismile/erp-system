package com.erp.customfield.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自定义字段定义（元数据）
 */
@Data
@TableName("sys_custom_field")
public class CustomField {

    @TableId(value = "field_id", type = IdType.AUTO)
    private Long fieldId;

    @TableField("entity_type")
    private String entityType;

    @TableField("field_key")
    private String fieldKey;

    @TableField("field_label")
    private String fieldLabel;

    @TableField("field_type")
    private String fieldType;

    /** select 类型的选项 JSON：[{"label":"x","value":"y"}] */
    @TableField("field_options")
    private String fieldOptions;

    @TableField("default_value")
    private String defaultValue;

    @TableField("placeholder")
    private String placeholder;

    /** number 类型小数位 */
    @TableField("precision_val")
    private Integer precisionVal;

    @TableField("min_value")
    private BigDecimal minValue;

    @TableField("max_value")
    private BigDecimal maxValue;

    /** 公式表达式：如 {{qty}} * {{price}} * (1 - {{rate}}/100) */
    @TableField("formula_expr")
    private String formulaExpr;

    /** 公式依赖字段key列表 JSON：["qty","price","rate"] */
    @TableField("formula_deps")
    private String formulaDeps;

    @TableField("section_name")
    private String sectionName;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("is_required")
    @com.fasterxml.jackson.annotation.JsonProperty("isRequired")
    private Integer isRequired;

    @TableField("is_list_show")
    private Integer isListShow;

    /** 是否在物料选择下拉中显示：0否 1是（仅 entity_type=material 生效） */
    @TableField("is_drop_show")
    private Integer isDropShow;

    /** 是否同步到 ext_data JSONB 便于 WHERE 过滤 */
    @TableField("is_search")
    private Integer isSearch;

    @TableField("is_enabled")
    private Integer isEnabled;

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
