package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.Complaint;

public interface ComplaintService extends IService<Complaint> {

    PageResult<Complaint> pageQuery(int current, int size, String keyword, String handleStatus,
                                     String complaintType, String severityLevel, Long customerId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    Complaint getDetail(Long id);

    void saveComplaint(Complaint complaint);

    /** 分配处理人: 新建/任意 → 处理中 */
    void assignHandler(Long id, Long handlerId);

    /** 解决: 处理中 → 已解决 */
    void resolve(Long id, String handleResult);

    /** 关闭: 已解决 → 已关闭 */
    void close(Long id, Integer satisfactionScore);

    String generateComplaintNo();
}
