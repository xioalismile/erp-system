package com.erp.acquisition.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.acquisition.entity.AcquisitionTask;
import com.erp.common.PageResult;

public interface AcquisitionTaskService extends IService<AcquisitionTask> {

    PageResult<AcquisitionTask> pageQuery(int current, int size, String keyword, String platform, String status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    AcquisitionTask getDetail(Long id);

    /** 自动生成taskNo */
    AcquisitionTask createTask(AcquisitionTask task);

    void updateTask(AcquisitionTask task);

    void deleteTask(Long id);

    /** 触发采集，调用采集服务 */
    void startTask(Long id);

    void pauseTask(Long id);

    /**
     * 更新任务状态（供采集服务回调使用）
     * 采集服务执行采集时会通过 PUT /api/acquisition/task/{id}/status 接口更新任务状态
     * 支持的状态值: running/completed/failed/paused
     *
     * @param taskId 任务ID
     * @param status 新状态
     * @param capturedCount 已采集数量
     * @param errorMessage 错误信息（失败时填充）
     */
    void updateStatus(Long taskId, String status, Integer capturedCount, String errorMessage);

    /** 保存企查查登录态 */
    void saveLoginCookie(Long taskId, String cookie);

    /** 生成任务编号: ACQ-yyyyMMdd-0001 */
    String generateTaskNo();
}
