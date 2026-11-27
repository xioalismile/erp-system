package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.sale.entity.SaleOrderSubstitutePlan;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 替换发货指示（销售在订单行上预先指定替代品）。
 * 2026-11-27 替换发货需求。
 */
public interface SaleOrderSubstitutePlanService extends IService<SaleOrderSubstitutePlan> {

    /** 订单详情/弹窗：按订单查有效与已取消指示（含展示字段与待执行数量） */
    List<SaleOrderSubstitutePlan> listByOrder(Long orderId);

    SaleOrderSubstitutePlan savePlan(SaleOrderSubstitutePlan plan, String operator);

    SaleOrderSubstitutePlan updatePlan(Long id, SaleOrderSubstitutePlan plan, String operator);

    /** 取消指示：已执行部分保留，剩余不再执行 */
    void cancelPlan(Long id, String operator);

    /** 订单反审核/作废时级联取消（返回取消条数） */
    int cancelByOrder(Long orderId, String reason, String operator);

    /** 订单删除时清理（物理删除仅用于草稿订单） */
    void deleteByOrder(Long orderId);

    /** 该订单行是否存在待执行（有效且未执行完）的销售指示 */
    boolean hasActivePendingPlan(Long orderItemId);

    /** 批量判断，出库单详情回显用 */
    Set<Long> activePendingItemIds(Collection<Long> orderItemIds);

    /** 出库审核 + / 反审作废 -；delta 为负时不低于 0 */
    void addTurned(Long planId, BigDecimal delta);

    /** 该订单行其它有效指示的待执行合计（excludePlanId 可为 null） */
    BigDecimal sumPendingByOrderItem(Long orderItemId, Long excludePlanId);
}
