package com.erp.inventory.batch.service;

import java.util.Map;

/**
 * 批次追溯服务（化妆品批记录）。
 *
 * <p>追溯树由现有单据实时推导（不建追溯关系表），支持正查与反查：</p>
 * <ul>
 *   <li>up（上游）：本批所用原料批次 → 供应商批号 / 供应商</li>
 *   <li>self（本批）：工单 + 配方/工艺快照 + 检验 + 放行</li>
 *   <li>down（下游）：本批卖给了哪些客户；若本批是原料批，则给出「哪些成品批用了它」（召回反查）</li>
 * </ul>
 */
public interface BatchTraceService {

    /**
     * 追溯查询。
     *
     * @param batchId 批次档案ID
     * @param direction up / down / both（缺省 both）
     */
    Map<String, Object> trace(Long batchId, String direction);
}
