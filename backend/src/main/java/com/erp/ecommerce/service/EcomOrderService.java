package com.erp.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.ecommerce.entity.EcomPlatformOrder;

import java.util.Map;

public interface EcomOrderService extends IService<EcomPlatformOrder> {

    PageResult<EcomPlatformOrder> pageQuery(int current, int size, Long shopId, String keyword, Integer converted, String platform);

    /** 详情（含明细行） */
    EcomPlatformOrder getDetail(Long id);

    /**
     * 拉取平台订单：days = 往前看多少天（按平台"最近更新时间"过滤）。
     * 返回 {fetched=拉到, added=新入库, skipped=已存在跳过}。
     */
    Map<String, Object> pullOrders(Long shopId, Integer days);

    /** 转销售订单：生成草稿态销售订单并回写转换标记，返回 {saleOrderId, saleOrderNo} */
    Map<String, Object> convertToSaleOrder(Long id);
}
