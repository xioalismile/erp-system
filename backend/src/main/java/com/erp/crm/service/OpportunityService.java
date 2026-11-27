package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.Opportunity;

import java.math.BigDecimal;

public interface OpportunityService extends IService<Opportunity> {

    PageResult<Opportunity> pageQuery(int current, int size, String keyword, String salesStage,
                                       Long customerId, Long assignedSalesId, Integer isClosed, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch);

    Opportunity getDetail(Long id);

    void saveOpportunity(Opportunity opp);

    /** 更新销售阶段，并根据阶段映射自动设置概率 */
    void updateSalesStage(Long id, String stage, String winReason, String loseReason);

    /** 关闭商机：won=true 赢单，won=false 输单 */
    void closeOpportunity(Long id, boolean won, String reason);

    /**
     * 销售订单审核时回写商机成交（记录成交订单 + 推进阶段 + 升级客户）
     * 幂等设计：已成交则跳过阶段推进，但仍更新订单关联
     */
    void markWonBySaleOrder(Long opportunityId, Long saleOrderId, String saleOrderNo, BigDecimal actualAmount);

    String generateOpportunityNo();
}
