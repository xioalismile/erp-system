package com.erp.qc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 质检模板头：一套可维护的检验标准（模板头 + 检验项列表）。
 *
 * <p>绑定口径（用户确认的唯一事实来源）：模板可按「物料」或「工序」绑定；**默认不绑定**。
 * 工序的「需质检」开关默认关闭；只有工序开启了「需质检」才需要绑模板、才走质检、才卡下道数量。
 * 未开启质检的工序完全不受影响（与改造前一致）。</p>
 */
@Data
@TableName("qc_template")
public class QcTemplate {

    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    /** 模板编码（自动生成） */
    @TableField("template_code")
    private String templateCode;

    /** 模板名称 */
    @TableField("template_name")
    private String templateName;

    /** 模板类型：MATERIAL=物料质检模板（按物料/分类/工序绑定）；BILL=单据质检模板（按单据类型绑定） */
    @TableField("template_type")
    private String templateType;

    /** 单据级模板绑定的单据类型，逗号分隔，可绑定多个（PURCHASE_INBOUND,PRODUCTION_RECEIPT,SUBCONTRACT_INBOUND） */
    @TableField("bill_types")
    private String billTypes;

    /** 是否默认模板：1=是 0=否 */
    @TableField("is_default")
    private Integer isDefault;

    /** 适用物料ID（按物料绑定时使用；为空 = 不按物料绑定） */
    @TableField("material_id")
    private Long materialId;

    /** 适用工序名称（按工序绑定时使用；为空 = 不按工序绑定） */
    @TableField("operation_name")
    private String operationName;

    /** 质检类型：首检 / 抽检 / 全检 */
    @TableField("qc_type")
    private String qcType;

    /** 抽样比例(%)：仅抽检使用；抽样数 = 报工数量 × 抽样比例 / 100 */
    @TableField("sample_ratio")
    private BigDecimal sampleRatio;

    /** 允许不良率(%)：仅抽检使用，默认 0（不允许不良） */
    @TableField("allowed_defect_ratio")
    private BigDecimal allowedDefectRatio;

    /** 是否启用：1=启用 0=停用 */
    @TableField("is_enabled")
    private Integer isEnabled;

    /** 备注 */
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

    /** 检验项明细（非库字段：提交/查询模板时一并携带） */
    @TableField(exist = false)
    private List<QcTemplateItem> items;

    /** 列表展示用：适用物料名称（非库字段，后端 join 物料档案回填） */
    @TableField(exist = false)
    private String materialName;

    /** 列表展示用：适用物料编码（非库字段） */
    @TableField(exist = false)
    private String materialCode;

    /** 列表展示用：检验项数量（非库字段，后端按 qc_template_item 统计回填） */
    @TableField(exist = false)
    private Integer itemCount;

    // ================= 2026-10-07 绑定模型改造：多选物料 + 多选分类（均非库字段） =================

    /** 适用物料ID集合（多选；提交与查询一并携带；落 qc_template_material 关联表） */
    @TableField(exist = false)
    private List<Long> materialIds;

    /** 适用分类ID集合（多选；落 qc_template_category 关联表；含下级分类） */
    @TableField(exist = false)
    private List<Long> categoryIds;

    /** 列表展示用：适用物料名称列表（后端按关联表 join 物料档案回填，如「芯片A (M001)」） */
    @TableField(exist = false)
    private List<String> materialNames;

    /** 列表展示用：适用分类名称列表（后端按关联表回填） */
    @TableField(exist = false)
    private List<String> categoryNames;
}
