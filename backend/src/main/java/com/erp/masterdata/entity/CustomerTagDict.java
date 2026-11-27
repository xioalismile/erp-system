package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户标签字典 */
@Data
@TableName("customer_tag_dict")
public class CustomerTagDict {

    @TableId(value = "tag_id", type = IdType.AUTO)
    private Long tagId;

    @TableField("tag_name")
    private String tagName;

    @TableField("tag_color")
    private String tagColor;

    @TableField("tag_category")
    private String tagCategory;

    @TableField("is_enabled")
    private Integer isEnabled;

    /** 备注 */
    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
