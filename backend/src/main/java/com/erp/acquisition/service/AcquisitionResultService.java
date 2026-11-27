package com.erp.acquisition.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.acquisition.entity.AcquisitionResult;
import com.erp.common.PageResult;

import java.util.List;

public interface AcquisitionResultService extends IService<AcquisitionResult> {

    PageResult<AcquisitionResult> pageQuery(int current, int size, Long taskId, String keyword, String reviewStatus, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    /** 待审核数量（与列表同数据权限口径） */
    long countPending(Long taskId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx);

    AcquisitionResult getDetail(Long id);

    /** 批量保存采集结果 */
    List<AcquisitionResult> batchSave(List<AcquisitionResult> results);

    /** 审核 */
    void review(Long id, String reviewStatus, Long reviewerId, String rejectReason);

    /** 批量导入CRM线索，调用LeadService.saveLead，返回成功导入的数量 */
    int batchImportToLeads(List<Long> resultIds, Long assignedSalesId);

    /** 单个删除（逻辑删除） */
    void deleteById(Long id);

    /** 批量删除（逻辑删除） */
    int batchDelete(List<Long> ids);

    /** 按任务删除所有结果（逻辑删除），用于重新采集前清空旧数据 */
    int deleteByTaskId(Long taskId);
}
