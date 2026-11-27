package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质检模板 ↔ 适用分类（多选）：挂上某物料分类后，该分类**及其下级分类**下的物料自动适用。
 *
 * <p>与「适用物料」并存：匹配时精确到物料的优先，分类作为兜底。</p>
 */
@Data
@TableName("qc_template_category")
public class QcTemplateCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属质检模板ID */
    @TableField("template_id")
    private Long templateId;

    /** 适用物料分类ID（物料分类主档） */
    @TableField("category_id")
    private Long categoryId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
