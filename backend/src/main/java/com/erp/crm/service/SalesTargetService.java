package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.SalesTargetCompany;
import com.erp.crm.entity.SalesTargetEmployee;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface SalesTargetService extends IService<SalesTargetCompany> {

    /** 公司年度目标分页查询（支持列头排序） */
    PageResult<SalesTargetCompany> pageQueryCompany(int current, int size, Integer year, String status,
                                                    String sortBy, String sortOrder);

    /** 销售人员目标分页查询（quarter 非 null 时填充季度口径指标；支持列头排序） */
    PageResult<SalesTargetEmployee> pageQueryEmployee(int current, int size, Integer year, Long employeeId,
                                                       Long deptId, String allocationStatus, Integer quarter,
                                                       String sortBy, String sortOrder);

    /** 创建公司年度目标 */
    SalesTargetCompany createCompanyTarget(SalesTargetCompany target);

    /** 更新公司年度目标 */
    void updateCompanyTarget(Long id, SalesTargetCompany target);

    /** 删除公司年度目标 */
    void deleteCompanyTarget(Long id);

    /** 自动生成下年度目标（按增长率递增） */
    SalesTargetCompany generateNextYearTarget(Long currentYearId);

    /** 分配销售人员年度目标 */
    SalesTargetEmployee allocateEmployeeTarget(SalesTargetEmployee target);

    /** 批量分配销售人员目标 */
    int batchAllocate(List<SalesTargetEmployee> targets);

    /** 更新销售员目标 */
    void updateEmployeeTarget(Long id, SalesTargetEmployee target);

    /** 删除销售员目标 */
    void deleteEmployeeTarget(Long id);

    /** 重新汇总某销售员某年度的实际完成金额（来自销售订单+客户数据） */
    BigDecimal refreshActualAmount(Integer year, Long employeeId);

    /** 重新汇总实际完成指标：金额/订单数/新客户数；quarter 非 null 时仅按季度口径计算（不落库），否则按年度口径并写回 */
    Map<String, Object> refreshActualMetrics(Integer year, Long employeeId, Integer quarter);

    /** 获取某年度目标分配看板：公司目标/已分配/未分配/缺口人数 */
    Map<String, Object> getAllocationDashboard(Integer year);

    /** 获取某年度销售目标完成情况列表（含完成率排名） */
    List<Map<String, Object>> getCompletionRanking(Integer year);

    /** 获取公司年度目标详情（含分配情况） */
    SalesTargetCompany getCompanyTargetDetail(Long id);
}
