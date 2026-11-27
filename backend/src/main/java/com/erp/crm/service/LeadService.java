package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.Lead;

public interface LeadService extends IService<Lead> {

    PageResult<Lead> pageQuery(int current, int size, String keyword, String leadStatus,
                                String intentionLevel, Long assignedSalesId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    Lead getDetail(Long id);

    void saveLead(Lead lead);

    /** 更新线索状态: 新线索/跟进中/已转化/已放弃 */
    void updateLeadStatus(Long id, String status);

    /** 分配销售员 */
    void assignSales(Long id, Long salesId);

    /** 放弃线索 */
    void abandon(Long id, String reason);

    /** 转化为客户，返回新客户ID（默认转化为正式客户） */
    Long convertToCustomer(Long id);

    /** 转化为客户，返回新客户ID；toPool=true 时直接进入公海池 */
    Long convertToCustomer(Long id, boolean toPool);

    /** 生成线索编号: L-yyyyMMdd-0001 */
    String generateLeadNo();
}
