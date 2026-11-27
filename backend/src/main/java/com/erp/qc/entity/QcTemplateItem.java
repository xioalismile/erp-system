package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质检模板检验项：项目名 / 检验方法 / 标准值 / 上限 / 下限 / 单位 / 是否必填。
 */
@Data
@TableName("qc_template_item")
public class QcTemplateItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 所属模板ID */
    @TableField("template_id")
    private Long templateId;

    /** 行号 */
    @TableField("line_no")
    private Integer lineNo;

    /** 检验项目名称 */
    @TableField("item_name")
    private String itemName;

    /** 检验方法 */
    @TableField("inspect_method")
    private String inspectMethod;

    /** 标准值（文本，用于描述性标准） */
    @TableField("standard_value")
    private String standardValue;

    /** 上限：实测值 ≤ 上限才判合格（为空 = 不卡上限） */
    @TableField("upper_limit")
    private BigDecimal upperLimit;

    /** 下限：实测值 ≥ 下限才判合格（为空 = 不卡下限） */
    @TableField("lower_limit")
    private BigDecimal lowerLimit;

    /** 单位 */
    @TableField("unit")
    private String unit;

    /** 是否必填：1=必填（不填判不合格） 0=选填 */
    @TableField("is_required")
    private Integer isRequired;

    /** 行备注（明细行的备注） */
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
