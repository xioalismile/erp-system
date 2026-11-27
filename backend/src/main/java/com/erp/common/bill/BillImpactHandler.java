package com.erp.common.bill;

/**
 * 单据业务影响处理器（审核/反审/作废/反作废/结案/反结案 的双向影响释放钩子）。
 *
 * <p>影响类型举例：
 * <ul>
 *   <li>库存：销售出库审核扣减库存、反审/作废补回；采购入库审核增加库存</li>
 *   <li>应收/应付：销售开票审核生成应收、作废红冲应收</li>
 *   <li>MRP占用：销售订单审核锁定毛需求、作废/结案释放毛需求</li>
 *   <li>信用额度：销售订单审核占用客户信用额度、作废/结案释放</li>
 *   <li>往来核销：收款/付款审核核销应收/应付、反审反核销</li>
 * </ul>
 * </p>
 *
 * <p>约定：所有方法均在事务内调用，抛 RuntimeException 即可回滚。</p>
 *
 * @param <B> 单据主实体类型
 */
public interface BillImpactHandler<B> {

    /** 审核通过后：释放正向影响（例：扣库存/生成应收/占信用/占MRP） */
    default void onApproved(B bill) {}

    /** 重置草稿后：回收正向影响（onApproved 的反向动作） */
    default void onReverseApproved(B bill) {}

    /** 结案时：释放结案影响（例：冻结后续下推/释放MRP未执行需求） */
    default void onClosed(B bill) {}

    /** 反结案时：回收结案影响（onClosed 的反向） */
    default void onReopened(B bill) {}

    /** 作废时：影响回收（通常等价于 onReverseApproved + onClosed 的合并回收） */
    default void onVoided(B bill) {}

    /** 反作废时：影响恢复（恢复成作废前的生效状态，通常等价于 onApproved + 若已结案需 onClosed 恢复） */
    default void onUnvoided(B bill) {}
}
