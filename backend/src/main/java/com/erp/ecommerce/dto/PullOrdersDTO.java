package com.erp.ecommerce.dto;

import lombok.Data;

/** 拉取平台订单入参 */
@Data
public class PullOrdersDTO {
    /** 店铺ID（空 = 全部启用店铺） */
    private Long shopId;
    /** 往前看多少天（1~90，默认 7） */
    private Integer days;
}
