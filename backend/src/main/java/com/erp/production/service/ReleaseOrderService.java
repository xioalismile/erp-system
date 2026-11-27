package com.erp.production.service;

import com.erp.production.entity.ReleaseOrder;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 放行单服务（化妆品批记录）。
 *
 * <p>状态机动作（保存草稿/提交/审核/驳回/重置草稿/作废/反作废）由统一状态机基类提供；
 * 本接口只补批记录特有的便利入口。</p>
 *
 * <p>生效影响（onApproved）：回写批次「已放行」+ 放行人/时间，并自动生成留样任务；
 * 重置草稿/作废（onReverseApproved/onVoided）：批次回退「合格」（保留检验结论）。</p>
 */
public interface ReleaseOrderService {

    /** 从批次一键生成放行单草稿：带出批次/物料/工单/检验记录信息并生成单号，返回放行单ID */
    ReleaseOrder createDraftFromBatch(Long batchId, HttpServletRequest request);
}
