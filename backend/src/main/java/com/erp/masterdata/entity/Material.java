package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material")
public class Material {

    @TableId(value = "material_id", type = IdType.AUTO)
    @ExportField(label = "物料ID", order = 0, hidden = true)
    private Long materialId;

    @TableField("material_code")
    @ExportField(label = "物料编码", order = 1, required = true)
    private String materialCode;

    @TableField("material_name")
    @ExportField(label = "物料名称", order = 2, required = true)
    private String materialName;

    @TableField("full_name")
    @ExportField(label = "全称", order = 3)
    private String fullName;

    @TableField("model")
    @ExportField(label = "规格型号", order = 4)
    private String model;

    @TableField("material_type")
    @ExportField(label = "物料类型", order = 5)
    private String materialType;

    @TableField("supply_type")
    @ExportField(label = "供应类型", order = 5)
    private String supplyType;

    @TableField("default_supplier_id")
    private Long defaultSupplierId;

    @TableField("default_supplier_name")
    @ExportField(label = "默认供应商", order = 5)
    private String defaultSupplierName;

    @TableField("category_id")
    @ExportField(label = "物料分类ID", order = 6, hidden = true)
    private Long categoryId;

    /** 分类编码（导入用，非持久化字段） */
    @TableField(exist = false)
    @ExportField(label = "物料分类编码", order = 6)
    private String categoryCode;

    /** 分类名称（只读响应载体：按 category_id 关联 material_category 实时解析；不落库、不进导入导出） */
    @TableField(exist = false)
    private String categoryName;

    /** 单位名称（导入用，非持久化字段；导入时按名称/编码解析为基本单位ID，模板必填列） */
    @TableField(exist = false)
    @ExportField(label = "单位", order = 7, required = true)
    private String unitName;

    @TableField("base_unit_id")
    @ExportField(label = "基本单位ID", order = 7, hidden = true)
    private Long baseUnitId;

    /** 保修期（月）：售后维修保修判定用；空按默认 12 个月 */
    @TableField("warranty_months")
    private Integer warrantyMonths;

    /** 基本单位名称（只读响应载体：按 base_unit_id 关联 unit_of_measure 实时解析；不落库、不进导入导出）
     *  注意：与导入专用的 unitName 是两个字段，勿混用（前端有断言测试禁止依赖 Material.unitName） */
    @TableField(exist = false)
    private String baseUnitName;

    @TableField("purchase_unit_id")
    @ExportField(label = "采购单位ID", order = 8, hidden = true)
    private Long purchaseUnitId;

    @TableField("sale_unit_id")
    @ExportField(label = "销售单位ID", order = 9, hidden = true)
    private Long saleUnitId;

    @TableField("stock_unit_id")
    @ExportField(label = "库存单位ID", order = 10, hidden = true)
    private Long stockUnitId;

    @TableField("production_unit_id")
    @ExportField(label = "生产单位ID", order = 11, hidden = true)
    private Long productionUnitId;

    @TableField("unit_conversion")
    @ExportField(label = "单位换算率", order = 12)
    private BigDecimal unitConversion;

    // ==================== 多单位换算（2026-11-09） ====================

    /** 是否启用辅计量：关闭时单据只按基本单位，行为与旧版完全一致 */
    @TableField("multi_unit_enabled")
    @ExportField(label = "辅计量管理", order = 96)
    private Boolean multiUnitEnabled;

    /** 辅计量单位（如重量 kg） */
    @TableField("aux_unit_id")
    @ExportField(label = "辅计量单位ID", order = 97, hidden = true)
    private Long auxUnitId;

    /** 换算方式：固定 / 浮动 */
    @TableField("aux_rate_mode")
    @ExportField(label = "换算方式", order = 98)
    private String auxRateMode;

    /** 辅换算率：1 个基本单位 = 多少个辅计量单位（如 1 瓶 = 0.05032 kg） */
    @TableField("aux_conversion_rate")
    @ExportField(label = "辅换算率", order = 99)
    private BigDecimal auxConversionRate;

    /**
     * 辅计量换算明细（2026-11-10 多单位换算重构，1 对多，来源表 material_unit_conversion）。
     * 仅作响应载体 + 保存入参：不落库到 material 表；保存时随物料一起提交，事务内整表删插。
     */
    @TableField(exist = false)
    private java.util.List<com.erp.basedata.entity.MaterialUnitConversion> unitConversions;

    @TableField("is_lot_control")
    @ExportField(label = "是否批次管理", order = 13)
    private Short isLotControl;

    @TableField("is_sn_control")
    @ExportField(label = "是否序列号管理", order = 14)
    private Short isSnControl;

    @TableField("is_bin_control")
    @ExportField(label = "是否库位管理", order = 15)
    private Short isBinControl;

    @TableField("is_qc_required")
    @ExportField(label = "是否质检", order = 16)
    private Short isQcRequired;

    @TableField("is_expiry_control")
    @ExportField(label = "是否有效期管理", order = 17)
    private Short isExpiryControl;

    @TableField("expiry_days")
    @ExportField(label = "有效期天数", order = 18)
    private Integer expiryDays;

    @TableField("sale_price")
    @ExportField(label = "销售价格", order = 19)
    private BigDecimal salePrice;

    @TableField("purchase_price")
    @ExportField(label = "采购价格", order = 20)
    private BigDecimal purchasePrice;

    @TableField("tax_rate")
    @ExportField(label = "税率(%)", order = 21)
    private BigDecimal taxRate;

    @TableField("min_stock")
    @ExportField(label = "最小库存", order = 22)
    private BigDecimal minStock;

    @TableField("max_stock")
    @ExportField(label = "最大库存", order = 23)
    private BigDecimal maxStock;

    /**
     * 【2026-11-25】采购批量：最小订单递增量（包装量），与金蝶「采购批量」同口径。
     * 用途：MRP 采购建议量、采购订单数量按它的整数倍向上取整；0/空 = 不启用（旧行为不变）。
     */
    @TableField("purchase_batch_qty")
    @ExportField(label = "采购批量", order = 31, numberFormat = "0.0000")
    private BigDecimal purchaseBatchQty;

    /**
     * 【2026-11-25】最小订货量：每次采购数量下限，与金蝶「最小订单数」同口径。
     * 用途：MRP 采购建议量先抬到该值，再按采购批量取整；0/空 = 不启用。
     */
    @TableField("min_order_qty")
    @ExportField(label = "最小订货量", order = 32, numberFormat = "0.0000")
    private BigDecimal minOrderQty;

    @TableField("status")
    @ExportField(label = "状态", order = 24)
    private String status;

    /** 图片URL列表(多图,逗号分隔,Excel导入内嵌图片自动上传后回填) */
    @TableField("image_urls")
    @ExportField(label = "物料图片", order = 25)
    private String imageUrls;

    @TableField("is_confidential")
    @ExportField(label = "是否保密物料", order = 27)
    private Short isConfidential;

    /** 主图URL */
    @TableField("main_image")
    @ExportField(label = "主图URL", order = 26, hidden = true)
    private String mainImage;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 28)
    private String remark;

    /**
     * 【工时体系 2026-10-07】单件标准加工工时（小时）。
     * 用途：① 工艺路线工序的「单件加工工时」默认带出它；② 工时误差分析里作为"标准"基数。
     */
    @TableField("standard_hours")
    @ExportField(label = "单件标准工时(h)", order = 29, numberFormat = "0.0000")
    private BigDecimal standardHours;

    /**
     * 【2026-11-07】物料日产能（件/天）。
     * 用途：APS 排程的占用天数 = max(工序工时 ÷ 工作中心日产能×效率, 工序计划数量 ÷ 物料日产能)；
     * 0 或空 = 不限制，排程结果与升级前完全一致。
     */
    @TableField("daily_capacity")
    @ExportField(label = "日产能(件/天)", order = 30, numberFormat = "0.0000")
    private BigDecimal dailyCapacity;

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

    /** 默认仓库ID（基础资料-仓库，采购/入库默认带出 + 2026-09-20 补列：消除"前端可选但不落库"） */
    @TableField("default_warehouse_id")
    private Long defaultWarehouseId;

    /** 最新核算成本（取自cost_calculation表最新已确认记录的unit_cost，非数据库字段） */
    @TableField(exist = false)
    private java.math.BigDecimal avgCost;

    /**
     * 保存/编辑时随主表一并传入的自定义字段值（fieldKey -> value）。
     * 仅作请求入参：不落库（exist=false）、不回传（WRITE_ONLY），
     * 供「货品查重字段」组合唯一(AND)校验把自定义字段一并纳入组合比对。
     */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private java.util.Map<String, Object> customFieldValues;
}
