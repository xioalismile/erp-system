package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.FollowUp;

public interface FollowUpService extends IService<FollowUp> {

    PageResult<FollowUp> pageQuery(int current, int size, String keyword, String sourceType,
                                    Long sourceId, String followUpType, Integer importantFlag, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    FollowUp getDetail(Long id);

    /** 保存跟进记录: 自动生成编号, 并更新来源对象(客户/线索/商机)的最后跟进日期与跟进次数 */
    void saveFollowUp(FollowUp fu);

    /** 生成跟进编号: FU-yyyyMMdd-0001 */
    String generateFollowUpNo();
}
