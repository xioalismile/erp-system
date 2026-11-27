package com.erp.print.template.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("print_template")
public class PrintTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("template_code")
    private String templateCode;

    @TableField("template_name")
    private String templateName;

    @TableField("doc_type")
    private String docType;

    @TableField("template_type")
    private String templateType;

    @TableField("template_content")
    private String templateContent;

    @TableField("template_style")
    private String templateStyle;

    @TableField("is_default")
    private Integer isDefault;

    @TableField("page_size")
    private String pageSize;

    @TableField("page_orientation")
    private String pageOrientation;

    @TableField("margin_top")
    private Integer marginTop;

    @TableField("margin_bottom")
    private Integer marginBottom;

    @TableField("margin_left")
    private Integer marginLeft;

    @TableField("margin_right")
    private Integer marginRight;

    @TableField("header_content")
    private String headerContent;

    @TableField("footer_content")
    private String footerContent;

    @TableField("watermark")
    private String watermark;

    @TableField("watermark_enabled")
    private Integer watermarkEnabled;

    @TableField("status")
    private Integer status;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
