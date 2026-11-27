package com.erp.qc.service;

import com.erp.qc.entity.QcRecord;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 批次检验服务（化妆品批记录）。
 *
 * <p>与现有「工序质检」（WorkOrderOperationServiceImpl 写 qc_record，qcType=首检/抽检/全检）并存：
 * 本服务按批次建检（qc_stage=来料检/成品检），检验提交后回写批次质量状态。</p>
 *
 * <p>口径（设计文档 §3.4）：采购入库保存（启用批次管理的原料）→ 生成来料待检；
 * 生产入库保存（成品）→ 生成成品待检；检验提交需电子签名；复核再签一次。</p>
 */
public interface QcBatchService {

    /**
     * 按批次建检：匹配模板生成检项明细，批次质量状态置「待检」，返回检验记录ID。
     *
     * @param qcStage 检验环节：来料检 / 成品检
     * @param batchId 批次档案ID
     * @throws com.erp.config.BusinessException 批次不存在 / 已有进行中的检验 / 模板未配置
     */
    Long createFromBatch(String qcStage, Long batchId, HttpServletRequest request);

    /**
     * 提交检验（含手写签名）：保存实测值与结论，回写批次 qc_status / qc_result。
     * 结论仅允许：合格 / 不合格 / 让步接收。
     *
     * @param signImage 手写签名图（PNG DataURL）；为空时按历史密码签名兜底
     */
    void submitWithSign(Long recordId, QcRecord payload, HttpServletRequest request, String password, String signImage);

    /** 检验复核（含手写签名）：落复核人；须先提交过检验 */
    void reviewWithSign(Long recordId, HttpServletRequest request, String password, String signImage);
}
