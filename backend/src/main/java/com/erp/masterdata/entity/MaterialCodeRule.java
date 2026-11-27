package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物料编码规则实体
 * 对应表: material_code_rule
 * 来源: 2026-07-01 客户录音访谈需求
 *
 * 规则按物料分类配置，category_id 为 NULL 表示通用规则（兜底）。
 * 编码生成格式: 前缀 + yyyyMMdd + 6位流水号（按 last_seq+1 递增并回写）。
 */
@Data
@TableName("material_code_rule")
public class MaterialCodeRule {

    /** 规则ID */
    @TableId(value = "rule_id", type = IdType.AUTO)
    @ExportField(label = "规则ID", order = 0, hidden = true)
    private Long ruleId;

    /** 物料分类ID（为 NULL 表示通用规则） */
    @TableField("category_id")
    @ExportField(label = "物料分类ID", order = 1, hidden = true)
    private Long categoryId;

    /** 物料分类名称（冗余字段，便于展示） */
    @TableField("category_name")
    @ExportField(label = "物料分类名称", order = 2)
    private String categoryName;

    /** 编码前缀 */
    @TableField("prefix")
    @ExportField(label = "编码前缀", order = 3, required = true)
    private String prefix;

    /** 流水号位数（默认 6 位） */
    @TableField("seq_length")
    @ExportField(label = "流水号位数", order = 4)
    private Integer seqLength;

    /** 是否启用: 0-禁用, 1-启用 */
    @TableField("is_enabled")
    @ExportField(label = "是否启用", order = 5)
    private Integer isEnabled;

    /** 是否拼接分类编码: 0-不拼接, 1-拼接(从根分类到当前分类的编码依次拼接) */
    @TableField("include_category_code")
    @ExportField(label = "拼接分类编码", order = 6)
    private Integer includeCategoryCode;

    /** 是否包含日期: 0-不包含, 1-包含yyyyMMdd */
    @TableField("include_date")
    @ExportField(label = "包含日期", order = 7)
    private Integer includeDate;

    /** 最后已使用流水号 */
    @TableField("last_seq")
    @ExportField(label = "最后流水号", order = 8, hidden = true)
    private Long lastSeq;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;
}
