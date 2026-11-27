package com.erp.print.template.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("print_template_field")
public class PrintTemplateField {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("template_id")
    private Long templateId;

    @TableField("field_key")
    private String fieldKey;

    @TableField("field_name")
    private String fieldName;

    @TableField("field_type")
    private String fieldType;

    @TableField("display_format")
    private String displayFormat;

    @TableField("position_x")
    private Integer positionX;

    @TableField("position_y")
    private Integer positionY;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("font_size")
    private Integer fontSize;

    @TableField("font_weight")
    private String fontWeight;

    @TableField("font_color")
    private String fontColor;

    @TableField("alignment")
    private String alignment;

    @TableField("visible")
    private Integer visible;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("remark")
    private String remark;
}
