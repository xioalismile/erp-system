package com.erp.inventory.batch.service;

/**
 * 批次放行守卫（化妆品批记录）。
 *
 * <p>设置中心参数 {@code batch.release.outbound.control} 三档：</p>
 * <ul>
 *   <li>允许 —— 不拦截，与旧行为完全一致</li>
 *   <li>预警提示 —— 返回预警文案，前端弹窗确认后可继续；后端不阻断</li>
 *   <li>不允许 —— 直接拦截，抛业务异常</li>
 * </ul>
 *
 * <p>判定口径：仅对「启用批次管理」的物料生效；qc_status=已放行 视为可出库；
 * 历史批次（qc_status=合格 且无放行单）不拦；待检 / 不合格 / 已冻结 在「不允许」档拦截。</p>
 */
public interface BatchReleaseGuardService {

    /** 当前控制档位：允许 / 预警提示 / 不允许 */
    String currentMode();

    /**
     * 出库校验（保存/审核链路调用）。
     * mode=不允许 且存在待检/不合格/已冻结批次 → 抛 BusinessException；
     * 其余档位只记录日志，不阻断。
     *
     * @param materialId 物料ID
     * @param batchNo 批号
     * @param lineLabel 明细行描述（用于错误文案，如「第2行[SKU001]」）
     */
    void enforceOutbound(Long materialId, String batchNo, String lineLabel);

    /** 单条预警文案；可正常出库时返回 null */
    String warningOf(Long materialId, String batchNo, String lineLabel);
}
