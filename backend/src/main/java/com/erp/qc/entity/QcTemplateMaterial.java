package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质检模板 ↔ 适用物料（多选）：一个模板可绑定多个物料。
 *
 * <p>绑定口径：模板的「适用物料」为多选；匹配工单工序时「物料＋工序」组合优先，
 * 同名工序靠物料区分，避免不同物料的同名工序互相串用。</p>
 */
@Data
@TableName("qc_template_material")
public class QcTemplateMaterial {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属质检模板ID */
    @TableField("template_id")
    private Long templateId;

    /** 适用物料ID（物料档案） */
    @TableField("material_id")
    private Long materialId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
