package com.erp.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电商平台订单明细
 *
 * 口径：
 * - 一行一个平台 SKU，行号按平台返回顺序；
 * - mapped_* 记录拉单当时的映射快照，后续改映射不影响已拉订单的展示。
 */
@Data
@TableName("ecom_platform_order_item")
public class EcomPlatformOrderItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    /** 平台明细行ID（亚马逊 OrderItemId） */
    @TableField("platform_item_id")
    private String platformItemId;

    /** 明细行号（1 起） */
    @TableField("line_no")
    private Integer lineNo;

    @TableField("seller_sku")
    private String sellerSku;

    @TableField("asin")
    private String asin;

    /** 平台商品名称 */
    @TableField("product_name")
    private String productName;

    @TableField("quantity")
    private BigDecimal quantity;

    /** 平台行单价 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 平台行金额 */
    @TableField("item_total")
    private BigDecimal itemTotal;

    /** 拉单时映射到的系统物料快照 */
    @TableField("mapped_material_id")
    private Long mappedMaterialId;

    @TableField("mapped_material_code")
    private String mappedMaterialCode;

    @TableField("mapped_material_name")
    private String mappedMaterialName;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
