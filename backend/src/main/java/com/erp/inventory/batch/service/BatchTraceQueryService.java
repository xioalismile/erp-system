package com.erp.inventory.batch.service;

import com.erp.inventory.batch.mapper.BatchTraceQueryMapper;

import java.util.List;
import java.util.Map;

/**
 * 批次溯源查询（2026-11-06）：以批次号+物料为入口，返回相关单据、物料信息与多层组成批次。
 */
public interface BatchTraceQueryService {

    /**
     * @param batchNo    批次号
     * @param materialId 物料主键
     * @param depth      组成物料递归层数（默认 5，最大 8）
     */
    Map<String, Object> trace(String batchNo, Long materialId, Integer depth);

    /** 供单测/其它服务复用的单据头解析（返回 docTypeName/docNo/docDate/status/billId 等） */
    Map<String, Object> resolveDoc(String billType, String billNo);

    /** 某批次在库存流水里的出入库单据（in/out 两个列表） */
    Map<String, List<Map<String, Object>>> ledgerDocs(String batchNo, Long materialId);

    BatchTraceQueryMapper mapper();
}
