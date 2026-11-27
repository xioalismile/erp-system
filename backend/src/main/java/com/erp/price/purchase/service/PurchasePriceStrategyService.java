package com.erp.price.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.price.purchase.entity.PurchasePriceStrategy;

import java.math.BigDecimal;
import java.util.List;

public interface PurchasePriceStrategyService extends IService<PurchasePriceStrategy> {

    PageResult<PurchasePriceStrategy> listPage(int current, int size, String keyword);

    PurchasePriceStrategy getById(Long id);

    void save(PurchasePriceStrategy strategy, List<com.erp.price.purchase.entity.PurchasePriceStrategyItem> items);

    void update(PurchasePriceStrategy strategy, List<com.erp.price.purchase.entity.PurchasePriceStrategyItem> items);

    void delete(Long id);

    void toggleStatus(Long id);

    /** 下一个可用优先级（现有最大优先级 + 1，空表返回 1）：新增页默认值，保证不与现有策略重复 */
    int nextPriority();

    BigDecimal calculatePrice(Long supplierId, Long materialId, BigDecimal quantity, String taxType);

    /**
     * 仅"命中价格策略"时返回价格，未命中返回 null。
     * <p>供取价优先级（策略 → 物料采购价）逐档回落使用 —— 理由同销售侧（2026-09-20 价格策略修复）。</p>
     */
    BigDecimal tryMatchPrice(Long supplierId, Long materialId, BigDecimal quantity, String taxType);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);
}
