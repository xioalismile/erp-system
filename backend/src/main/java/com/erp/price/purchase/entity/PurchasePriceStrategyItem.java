package com.erp.price.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_price_strategy_item")
public class PurchasePriceStrategyItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("strategy_id")
    private Long strategyId;

    /** 对象类型：material 按物料 / category 按商品分类 / supplier 按供应商（与主表 target_type 一致） */
    @TableField("target_type")
    private String targetType;

    /** 对象ID：物料ID / 商品分类ID / 供应商ID */
    @TableField("target_id")
    private Long targetId;

    /** 商品ID（按物料时 = targetId，保留用于旧数据与引用检查） */
    @TableField("material_id")
    private Long materialId;

    /** 价格类型: fixed 固定价 / discount 折扣率 / markup 加价率 */
    @TableField("price_type")
    private String priceType;

    @TableField("price_value")
    private BigDecimal priceValue;

    @TableField("cost_price")
    private BigDecimal costPrice;

    @TableField("gross_profit_rate")
    private BigDecimal grossProfitRate;

    /** 该对象的数量下限（为空=不限） */
    @TableField("min_quantity")
    private BigDecimal minQuantity;

    /** 该对象的数量上限（为空=不限） */
    @TableField("max_quantity")
    private BigDecimal maxQuantity;

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
