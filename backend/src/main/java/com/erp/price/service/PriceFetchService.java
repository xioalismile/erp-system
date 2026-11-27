package com.erp.price.service;

import com.erp.price.vo.PriceFetchResult;

import java.math.BigDecimal;

/**
 * 单据取价服务（2026-09-20 价格策略修复）。
 * <p>
 * 按设置中心「取价优先级」逐档回落取价：
 * <ul>
 *   <li>销售：strategy（价格策略）→ level（价格等级）→ material（物料默认售价），由 sale.price.fetch.priority 决定顺序</li>
 *   <li>采购：strategy（价格策略）→ material（物料采购价），由 purchase.price.fetch.priority 决定顺序
 *       —— 采购侧没有「价格等级」档案，等级档不适用</li>
 * </ul>
 * 各档的启用开关：销售 sale.price.strategy.enabled / 采购 purchase.price.strategy.enabled；关闭即跳过策略档。
 * </p>
 */
public interface PriceFetchService {

    /**
     * 取价
     *
     * @param side      sale=销售（默认）/ purchase=采购
     * @param partnerId 销售=客户ID；采购=供应商ID（策略按客户等级/供应商等级匹配，可为空）
     * @param materialId 物料ID（必填）
     * @param quantity  数量（策略的数量区间按此匹配，可为空=1）
     * @param taxType   计税方式（exclusive=价外税，其它或空=不换算）
     */
    PriceFetchResult fetch(String side, Long partnerId, Long materialId, BigDecimal quantity, String taxType);
}
