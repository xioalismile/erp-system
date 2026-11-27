package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BOM 子项（明细）实体
 *
 * 字段对齐UI截图（商品明细表格）：
 * - No（行号）
 * - 图片
 * - 子件编码/子件名称
 * - 子件型号
 * - 规格属性
 * - 用量/损耗率/供应类型/工序/关键件/位号
 */
@Data
@TableName("bom_item")
public class BomItem {

    @TableId(value = "bom_item_id", type = IdType.AUTO)
    private Long bomItemId;

    @TableField("bom_id")
    private Long bomId;

    /** 行号（从1开始步长1） */
    @TableField("line_no")
    private Integer lineNo;

    // ========== 子件基础信息 ==========

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    /** 规格型号 */
    @TableField("model_no")
    private String modelNo;

    /** 规格属性 */
    @TableField("spec_property")
    private String specProperty;

    /** 规格 */
    @TableField("specification")
    private String specification;

    /** 子件图片URL */
    @TableField("image_url")
    private String imageUrl;

    // ========== 用量与计量 ==========

    @TableField("unit_id")
    private Long unitId;

    /** 单位名称（冗余） */
    @TableField(exist = false)
    private String unitName;

    /** 基本用量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 用量类型：固定/变动 */
    @TableField("qty_type")
    private String qtyType;

    /** 损耗率(%) */
    @TableField("scrap_rate")
    private BigDecimal scrapRate;

    // ========== 时间与工序 ==========

    /** 生效日期 */
    @TableField("effective_date")
    private LocalDate effectiveDate;

    /** 失效日期 */
    @TableField("expiry_date")
    private LocalDate expiryDate;

    /** 工序号 */
    @TableField("process_no")
    private String processNo;

    /** 工序名称（冗余） */
    @TableField(exist = false)
    private String processName;

    // ========== 供应与标记 ==========

    /** 是否关键件 */
    @TableField("is_key_part")
    private Short isKeyPart;

    /** 供应类型：领料/倒冲/虚拟件/委外 */
    @TableField("supply_type")
    private String supplyType;

    /** 位号 */
    @TableField("position_no")
    private String positionNo;

    /** 工位ID */
    @TableField("workstation_id")
    private Long workstationId;

    /** 是否可选件 */
    @TableField("is_optional")
    private Short isOptional;

    // ========== 多级BOM关系 ==========

    /** 层级（1级=直接子件，2级=孙件，以此类推） */
    @TableField(exist = false)
    private Integer level;

    /** 父件编码（导出/导入用，非数据库字段） */
    @TableField(exist = false)
    private String parentMaterialCode;

    /** 父件名称（导出/导入用，非数据库字段） */
    @TableField(exist = false)
    private String parentMaterialName;

    /** 父行号（用于多级BOM，子项中的子项） */
    @TableField("parent_line_no")
    private Integer parentLineNo;

    /** 替代料行号 */
    @TableField("substitute_line_no")
    private Integer substituteLineNo;

    // ========== 备注 ==========

    @TableField("remark")
    private String remark;

    // ========== 审计字段 ==========

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
