package com.erp.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 电商平台订单（第一期：亚马逊）
 *
 * 口径：
 * - 平台单号唯一，拉单按平台单号先查后插去重（重复拉取不会产生重复订单）；
 * - 平台原始报文存 raw_json 供排查，界面不展示；
 * - converted = 是否已转销售订单（一张平台单只允许转一次）。
 */
@Data
@TableName("ecom_platform_order")
public class EcomPlatformOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属平台：AMAZON 亚马逊 / PDD 拼多多（按店铺冗余，便于列表筛选） */
    @TableField("platform")
    private String platform;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    /** 平台订单号（亚马逊 AmazonOrderId） */
    @TableField("platform_order_id")
    private String platformOrderId;

    /** 平台下单时间 */
    @TableField("purchase_date")
    private LocalDateTime purchaseDate;

    /** 平台订单状态（未发货/部分发货/已发货/已取消 等，原样记录） */
    @TableField("order_status")
    private String orderStatus;

    /** 买家付款方式描述（原样记录） */
    @TableField("payment_method")
    private String paymentMethod;

    /** 结算币种代码（USD 等） */
    @TableField("currency_code")
    private String currencyCode;

    /** 平台订单总金额 */
    @TableField("order_total")
    private BigDecimal orderTotal;

    /** 明细行数 */
    @TableField("item_count")
    private Integer itemCount;

    /** 收件人 */
    @TableField("ship_name")
    private String shipName;

    @TableField("ship_city")
    private String shipCity;

    @TableField("ship_state")
    private String shipState;

    @TableField("ship_country")
    private String shipCountry;

    /** 收货地址（拼接后的完整地址） */
    @TableField("ship_address")
    private String shipAddress;

    /** 是否已转销售订单：0 否 / 1 是 */
    @TableField("converted")
    private Integer converted;

    /** 转出的销售订单ID */
    @TableField("converted_sale_order_id")
    private Long convertedSaleOrderId;

    /** 转出的销售订单号 */
    @TableField("converted_sale_order_no")
    private String convertedSaleOrderNo;

    /** 最近拉取时间 */
    @TableField("pull_time")
    private LocalDateTime pullTime;

    /** 平台原始报文（排查用，界面不展示） */
    @TableField("raw_json")
    private String rawJson;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 平台订单明细（非数据库字段） */
    @TableField(exist = false)
    private List<EcomPlatformOrderItem> items;
}
