package com.erp.price.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sale_price_strategy")
public class SalePriceStrategy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("strategy_name")
    private String strategyName;

    @TableField("strategy_code")
    private String strategyCode;

    /** 适用范围（互斥单选）：material 按物料 / category 按商品分类 / customer 按客户 */
    @TableField("target_type")
    private String targetType;

    @TableField("priority")
    private Integer priority;

    @TableField("customer_level")
    private String customerLevel;

    @TableField("material_category_id")
    private Long materialCategoryId;

    @TableField("brand_id")
    private Long brandId;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    @TableField("min_quantity")
    private BigDecimal minQuantity;

    @TableField("max_quantity")
    private BigDecimal maxQuantity;

    @TableField("price_type")
    private String priceType;

    @TableField("price_value")
    private BigDecimal priceValue;

    @TableField("status")
    private Integer status;

    // ========== 统一单据状态机审计字段（2026-11-09 接入） ==========

    /** 审核状态（与主状态同源同步） */
    @TableField("approval_status")
    private String approvalStatus;

    /** 审核人 */
    @TableField("approved_by")
    private String approvedBy;

    /** 审核时间 */
    @TableField("approved_at")
    private LocalDateTime approvedAt;

    /** 结案状态：0 未结案 / 1 已结案 */
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

    @TableField(exist = false)
    private List<SalePriceStrategyItem> items;
}
