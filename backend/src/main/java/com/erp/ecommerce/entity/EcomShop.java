package com.erp.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电商店铺授权档案（第一期：亚马逊北美站）
 *
 * 口径：
 * - 店铺 = 平台 + 站点区域，先北美站，后续可扩欧洲/日本站；
 * - 卖家平台凭据（客户端编号/密钥/刷新令牌）加密落库，接口永不回明文；
 * - 店铺关联客户：转销售订单时作为单据客户来源（客户从客户管理选）。
 */
@Data
@TableName("ecom_shop")
public class EcomShop {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 店铺名称 */
    @TableField("shop_name")
    private String shopName;

    /** 平台：AMAZON（第一期写死） */
    @TableField("platform")
    private String platform;

    /** 站点区域：NA 北美站 / EU 欧洲站 / FE 日本站 */
    @TableField("region")
    private String region;

    /** 市场ID（北美站默认 ATVPDKIKX0DER 美国） */
    @TableField("marketplace_id")
    private String marketplaceId;

    /** 卖家编号（卖家平台可见） */
    @TableField("seller_id")
    private String sellerId;

    /** 卖家平台应用客户端编号（加密） */
    @TableField("client_id")
    private String clientId;

    /** 卖家平台应用客户端密钥（加密） */
    @TableField("client_secret")
    private String clientSecret;

    /** 卖家平台刷新令牌（加密） */
    @TableField("refresh_token")
    private String refreshToken;

    /** 访问令牌（拼多多等平台直接落库，加密；亚马逊每次现换不落库） */
    @TableField("access_token")
    private String accessToken;

    /** 访问令牌到期时间（拼多多） */
    @TableField("token_expire_at")
    private LocalDateTime tokenExpireAt;

    /** 授权状态：未授权 / 已授权 / 已失效 */
    @TableField("auth_status")
    private String authStatus;

    /** 最近一次授权成功时间 */
    @TableField("last_auth_time")
    private LocalDateTime lastAuthTime;

    /** 关联客户（转销售订单时用，来自客户管理） */
    @TableField("associate_customer_id")
    private Long associateCustomerId;

    @TableField("associate_customer_code")
    private String associateCustomerCode;

    @TableField("associate_customer_name")
    private String associateCustomerName;

    /** 状态：1 启用 / 0 停用 */
    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /** 客户端密钥脱敏（非数据库字段，接口返回用） */
    @TableField(exist = false)
    private String clientSecretMasked;

    /** 刷新令牌是否已配置（非数据库字段，接口返回用） */
    @TableField(exist = false)
    private Boolean refreshTokenConfigured;

    /** 访问令牌是否已配置（非数据库字段，接口返回用） */
    @TableField(exist = false)
    private Boolean accessTokenConfigured;
}
