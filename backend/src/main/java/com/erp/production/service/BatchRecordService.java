package com.erp.production.service;

import com.erp.production.entity.BatchProductionRecord;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 批生产记录服务（化妆品批记录）。
 *
 * <p>设计口径：工单生效时预生成记录头并**固化配方（BOM）与工艺路线快照**；
 * 投料 / 工序 / 检验 / 更正 / 签名等明细**实时聚合**，不在记录表冗余存储，
 * 保证「单据怎么变、记录就怎么显示」，同时快照段永不随后续改动而变。</p>
 */
public interface BatchRecordService {

    /**
     * 工单生效时确保批生产记录头存在（幂等：已有则不重复创建，也不覆盖已固化的快照）。
     *
     * @return 记录头（失败返回已存在的或 null，不阻断工单生效主流程）
     */
    BatchProductionRecord ensureRecord(Long workOrderId);

    /**
     * 批生产记录全量聚合（工单详情「批生产记录」页签数据源）。
     * 返回结构：header / materials（投料，含原料批次）/ operations（工序操作日志）/
     *           qcRecords（检验记录及检项）/ corrections（更正链）/ signatures（签名记录）
     */
    Map<String, Object> getFullRecord(Long workOrderId);

    /** 环境记录（温湿度）可选填写；留空不拦 */
    void saveEnv(Long workOrderId, BigDecimal temperature, BigDecimal humidity, HttpServletRequest request);
}
