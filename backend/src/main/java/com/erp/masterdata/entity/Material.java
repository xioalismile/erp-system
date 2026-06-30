package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material")
public class Material {

    @TableId(value = "material_id", type = IdType.AUTO)
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("full_name")
    private String fullName;

    @TableField("model")
    private String model;

    @TableField("material_type")
    private String materialType;

    @TableField("category_id")
    private Long categoryId;

    @TableField("base_unit_id")
    private Long baseUnitId;

    @TableField("purchase_unit_id")
    private Long purchaseUnitId;

    @TableField("sale_unit_id")
    private Long saleUnitId;

    @TableField("stock_unit_id")
    private Long stockUnitId;

    @TableField("production_unit_id")
    private Long productionUnitId;

    @TableField("unit_conversion")
    private BigDecimal unitConversion;

    @TableField("is_lot_control")
    private Short isLotControl;

    @TableField("is_sn_control")
    private Short isSnControl;

    @TableField("is_bin_control")
    private Short isBinControl;

    @TableField("is_qc_required")
    private Short isQcRequired;

    @TableField("is_expiry_control")
    private Short isExpiryControl;

    @TableField("expiry_days")
    private Integer expiryDays;

    @TableField("sale_price")
    private BigDecimal salePrice;

    @TableField("purchase_price")
    private BigDecimal purchasePrice;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("min_stock")
    private BigDecimal minStock;

    @TableField("max_stock")
    private BigDecimal maxStock;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
