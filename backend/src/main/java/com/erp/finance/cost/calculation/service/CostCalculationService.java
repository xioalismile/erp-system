package com.erp.finance.cost.calculation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.entity.CostCalculationItem;
import com.erp.finance.cost.calculation.entity.CostReportItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CostCalculationService extends IService<CostCalculation> {

    /**
     * 分页查询成本核算单
     * @param filterCreatedBy 数据权限过滤值(创建人用户名)，为 null 时不过滤
     */
    PageResult<CostCalculation> pageQuery(int current, int size, String keyword, String status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    CostCalculation getDetail(Long id);

    void saveCostCalculation(CostCalculation calc, List<CostCalculationItem> items);

    void calculateCost(Long id);

    void confirm(Long id);

    /** 反确认：已确认 -> 已核算（解锁，可重新核算） */
    void reverseConfirm(Long id);

    void delete(Long id);

    BigDecimal getUnitCost(Long materialId);

    /** 当期核算 - 为指定期间的所有物料自动归集成本 */
    Map<String, Object> periodCalculate(String period);

    /** 成本报表 - 获取指定期间的物料成本汇总 */
    List<CostReportItem> getCostReport(String period, String keyword);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效 → close_status=1（status 保持已生效） */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);
}