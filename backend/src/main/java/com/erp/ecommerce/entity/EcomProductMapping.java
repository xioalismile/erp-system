package com.erp.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电商商品映射（平台货品 ↔ 系统物料）
 *
 * 口径：
 * - 一个平台 SKU 一条映射，对应店铺唯一（同一 SKU 不同店铺可各自映射）；
 * - 转销售订单时按映射把平台行换成系统物料，未映射的 SKU 会拦截并提示先去补映射。
 */
@Data
@TableName("ecom_product_mapping")
public class EcomProductMapping {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    /** 平台卖家SKU */
    @TableField("seller_sku")
    private String sellerSku;

    /** 平台商品编号 ASIN */
    @TableField("asin")
    private String asin;

    /** 平台商品名称 */
    @TableField("product_name")
    private String productName;

    /** 对应系统物料 */
    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
}
