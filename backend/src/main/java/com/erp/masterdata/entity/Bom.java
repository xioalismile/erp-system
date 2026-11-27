package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BOM 物料清单主表
 *
 * 字段对齐UI截图（生产管理 → BOM）：
 * - 查询区：单据编号/单据日期/BOM分类/母件编号/母件名称/母件规格/规格属性/色号/产地/材质/状态/单据状态
 * - 详情区：单据编号/单据日期/母件名称/母件编号/母件规格/色号/产地/材质/规格属性/版本号/基本单位/负责人/默认BOM/状态/BOM分类/默认仓库/数量
 *
 * 状态枚举：草稿/已生效/已停用
 * 启用状态：启用/禁用
 */
@Data
@TableName("bom")
public class Bom {

    @TableId(value = "bom_id", type = IdType.AUTO)
    @ExportField(label = "BOM ID", order = 0, hidden = true)
    private Long bomId;

    /** 单据编号（系统自动生成，BOM-yyyyMMdd-XXXX） */
    @TableField("bom_code")
    @ExportField(label = "单据编号", order = 1, required = true)
    private String bomCode;

    /** BOM名称（兼容旧引用，=母件名称+版本） */
    @TableField("bom_name")
    @ExportField(label = "BOM名称", order = 2)
    private String bomName;

    /** 单据日期 */
    @TableField("bom_date")
    @ExportField(label = "单据日期", order = 3, dateFormat = "yyyy-MM-dd", required = true)
    private LocalDate bomDate;

    /** BOM分类（如：标准/工程/生产/成本/委外） */
    @TableField("bom_category")
    @ExportField(label = "BOM分类", order = 4)
    private String bomCategory;

    // ========== 母件（父项）信息 ==========

    /** 母件名称（冗余存储，便于列表显示） */
    @TableField("material_name")
    @ExportField(label = "母件名称", order = 5, required = true)
    private String materialName;

    /** 母件编号 */
    @TableField("material_id")
    @ExportField(label = "母件ID", order = 6, hidden = true)
    private Long materialId;

    @TableField("material_code")
    @ExportField(label = "母件编号", order = 7, required = true)
    private String materialCode;

    /** 母件规格 */
    @TableField("specification")
    @ExportField(label = "母件规格", order = 8)
    private String specification;

    /** 规格属性（颜色/尺码等多规格属性） */
    @TableField("spec_property")
    @ExportField(label = "规格属性", order = 9)
    private String specProperty;

    /** 色号 */
    @TableField("color_no")
    @ExportField(label = "色号", order = 10)
    private String colorNo;

    /** 产地 */
    @TableField("origin")
    @ExportField(label = "产地", order = 11)
    private String origin;

    /** 材质 */
    @TableField("material_quality")
    @ExportField(label = "材质", order = 12)
    private String materialQuality;

    // ========== BOM基础信息 ==========

    /** 版本号（如V1.0） */
    @TableField("bom_version")
    @ExportField(label = "版本号", order = 13)
    private String bomVersion;

    /** 基本单位ID */
    @TableField("unit_id")
    @ExportField(label = "基本单位ID", order = 14, hidden = true)
    private Long unitId;

    /** 基本单位名称（冗余） */
    @TableField(exist = false)
    private String unitName;

    /** 数量（BOM展开基本生产数量） */
    @TableField("quantity")
    @ExportField(label = "数量", order = 15, required = true)
    private BigDecimal quantity;

    /** BOM类型：标准BOM/工程BOM/生产BOM/成本BOM */
    @TableField("bom_type")
    @ExportField(label = "BOM类型", order = 16)
    private String bomType;

    /** 是否默认BOM（同一物料下唯一启用） */
    @TableField("is_default")
    @ExportField(label = "是否默认BOM", order = 17)
    private Short isDefault;

    /** 是否主BOM */
    @TableField("is_primary")
    @ExportField(label = "是否主BOM", order = 18)
    private Short isPrimary;

    /** 状态：启用/禁用 */
    @TableField("is_enabled")
    @ExportField(label = "启用状态", order = 19)
    private Short isEnabled;

    /** 单据状态：草稿/已生效/已停用 */
    @TableField("status")
    @ExportField(label = "单据状态", order = 20)
    private String status;

    /** 审核状态 */
    @TableField("approval_status")
    @ExportField(label = "审核状态", order = 21)
    private String approvalStatus;

    /** 审核人 */
    @TableField("approved_by")
    @ExportField(label = "审核人", order = 22)
    private String approvedBy;

    /** 审核时间 */
    @TableField("approved_at")
    @ExportField(label = "审核时间", order = 23)
    private LocalDateTime approvedAt;

    // ========== 结案 / 作废审计（统一单据状态机，2026-11-09 接入） ==========

    /** 结案状态：0 未结案 / 1 已结案（与主状态独立） */
    @TableField("close_status")
    private Integer closeStatus;

    /** 结案人 */
    @TableField("closed_by")
    private String closedBy;

    /** 结案时间 */
    @TableField("closed_at")
    private LocalDateTime closedAt;

    /** 结案原因 */
    @TableField("closed_reason")
    private String closedReason;

    /** 作废前状态（数字编码，供反作废恢复） */
    @TableField("bill_before_status")
    private Integer billBeforeStatus;

    /** 作废人 */
    @TableField("voided_by")
    private String voidedBy;

    /** 作废时间 */
    @TableField("voided_at")
    private LocalDateTime voidedAt;

    /** 作废原因 */
    @TableField("void_reason")
    private String voidReason;

    // ========== 负责人与仓库 ==========

    /** 负责人ID */
    @TableField("manager_id")
    @ExportField(label = "负责人ID", order = 24, hidden = true)
    private Long managerId;

    /** 负责人姓名（冗余） */
    @TableField("manager_name")
    @ExportField(label = "负责人", order = 25)
    private String managerName;

    /** 默认仓库ID */
    @TableField("default_warehouse_id")
    @ExportField(label = "默认仓库ID", order = 26, hidden = true)
    private Long defaultWarehouseId;

    /** 默认仓库名称（冗余） */
    @TableField("default_warehouse_name")
    @ExportField(label = "默认仓库", order = 27)
    private String defaultWarehouseName;

    // ========== 扩展字段 ==========

    /** 生效日期 */
    @TableField("effective_date")
    @ExportField(label = "生效日期", order = 28, dateFormat = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    /** 失效日期 */
    @TableField("expiry_date")
    @ExportField(label = "失效日期", order = 29, dateFormat = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 替代BOM编号 */
    @TableField("substitute_bom_code")
    @ExportField(label = "替代BOM编号", order = 30)
    private String substituteBomCode;

    /** 损耗率(%) - 表头级 */
    @TableField("scrap_rate")
    @ExportField(label = "损耗率(%)", order = 31)
    private BigDecimal scrapRate;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 32)
    private String remark;

    /** 附件URL */
    @TableField("attachment_urls")
    @ExportField(label = "附件URL", order = 33, hidden = true)
    private String attachmentUrls;

    // ========== 审计字段 ==========

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @ExportField(label = "创建人", order = 102, hidden = true)
    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新人", order = 103, hidden = true)
    private String updatedBy;

    // ========== 关联字段（非数据库） ==========

    /** BOM 明细列表 */
    @TableField(exist = false)
    private List<BomItem> items;

    /** 层级号（多级BOM使用，1为顶层） */
    @TableField(exist = false)
    @ExportField(label = "层级号", order = 50)
    private Integer level;
}
