package com.erp.price.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.price.sale.entity.SalePriceStrategy;

import java.math.BigDecimal;

public interface SalePriceStrategyService extends IService<SalePriceStrategy> {

    PageResult<SalePriceStrategy> listPage(int current, int size, String keyword);

    SalePriceStrategy getById(Long id);

    void save(SalePriceStrategy strategy, java.util.List<com.erp.price.sale.entity.SalePriceStrategyItem> items);

    void update(SalePriceStrategy strategy, java.util.List<com.erp.price.sale.entity.SalePriceStrategyItem> items);

    void delete(Long id);

    void toggleStatus(Long id);

    /** 下一个可用优先级（现有最大优先级 + 1，空表返回 1）：新增页默认值，保证不与现有策略重复 */
    int nextPriority();

    BigDecimal calculatePrice(Long customerId, Long materialId, BigDecimal quantity, String taxType);

    /**
     * 仅"命中价格策略"时返回价格，未命中返回 null。
     * <p>供取价优先级（策略 → 价格等级 → 物料默认价）逐档回落使用：
     * calculatePrice 的兜底是"物料售价"，会把"未命中"和"命中价恰好等于物料售价"混在一起，
     * 无法判断该档是否命中，故单列此方法（2026-09-20 价格策略修复）。</p>
     */
    BigDecimal tryMatchPrice(Long customerId, Long materialId, BigDecimal quantity, String taxType);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);
}
