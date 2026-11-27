package com.erp.common.bill;

import java.util.List;

/**
 * 下游单据校验器（严格型作废/重置草稿/删除的前置规则钩子）。
 *
 * <p>参考金蝶/用友：作废前必须保证"没有下游已生效单据"，
 * 否则提示"已被[XXX单据:单号]引用，无法作废"，需先作废下游再作废上游。</p>
 *
 * <p>由各ServiceImpl实现，返回的 List 用于拼接错误明细。</p>
 *
 * @param <B> 单据主实体类型
 */
public interface DownstreamBillValidator<B> {

    /**
     * 查询下游"已生效/已生效"关联单据列表。
     *
     * @return 每张下游单据的可读描述，例：["销售出库单:SO20260801001","销售发票:INV20260801002"]；
     *         若无下游已生效，返回空列表。
     */
    List<String> listApprovedDownstreamBills(B bill);
}
