package com.erp.crm.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.SalesTargetCompany;
import com.erp.crm.entity.SalesTargetEmployee;
import com.erp.crm.service.SalesTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售目标管理 Controller
 *
 * 提供两大类接口：
 *  1. 公司年度目标管理（CRUD + 自动生成下年度目标）
 *  2. 销售人员目标分配（CRUD + 批量分配 + 完成情况刷新）
 *  3. 看板：分配看板 + 完成率排名
 */
@RestController
@RequestMapping("/api/crm/sales-target")
@RequiredArgsConstructor
public class SalesTargetController {

    private final SalesTargetService salesTargetService;

    // ==================== 公司年度目标 ====================

    /** 公司目标分页查询（支持列头排序） */
    @GetMapping("/company/page")
    public Result<PageResult<SalesTargetCompany>> pageCompany(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(salesTargetService.pageQueryCompany(current, size, year, status, sortBy, sortOrder));
    }

    /** 公司目标详情 */
    @GetMapping("/company/{id}")
    public Result<SalesTargetCompany> getCompany(@PathVariable Long id) {
        return Result.success(salesTargetService.getCompanyTargetDetail(id));
    }

    /** 创建公司年度目标 */
    @PostMapping("/company")
    public Result<SalesTargetCompany> createCompany(@RequestBody SalesTargetCompany target) {
        return Result.success(salesTargetService.createCompanyTarget(target));
    }

    /** 更新公司年度目标 */
    @PutMapping("/company/{id}")
    public Result<Void> updateCompany(@PathVariable Long id, @RequestBody SalesTargetCompany target) {
        salesTargetService.updateCompanyTarget(id, target);
        return Result.success();
    }

    /** 删除公司年度目标 */
    @DeleteMapping("/company/{id}")
    public Result<Void> deleteCompany(@PathVariable Long id) {
        salesTargetService.deleteCompanyTarget(id);
        return Result.success();
    }

    /**
     * 自动生成下年度目标（按增长率递增）
     * 例如今年2000万，增长率30%，则自动生成明年2600万目标
     */
    @PostMapping("/company/{id}/generate-next-year")
    public Result<SalesTargetCompany> generateNextYear(@PathVariable Long id) {
        return Result.success(salesTargetService.generateNextYearTarget(id));
    }

    // ==================== 销售人员目标分配 ====================

    /** 销售员目标分页查询（quarter=1~4 时返回季度口径指标；支持列头排序） */
    @GetMapping("/employee/page")
    public Result<PageResult<SalesTargetEmployee>> pageEmployee(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String allocationStatus,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(salesTargetService.pageQueryEmployee(current, size, year, employeeId, deptId, allocationStatus, quarter, sortBy, sortOrder));
    }

    /** 分配销售员年度目标 */
    @PostMapping("/employee")
    public Result<SalesTargetEmployee> allocateEmployee(@RequestBody SalesTargetEmployee target) {
        return Result.success(salesTargetService.allocateEmployeeTarget(target));
    }

    /** 批量分配销售员目标 */
    @PostMapping("/employee/batch")
    public Result<Integer> batchAllocate(@RequestBody List<SalesTargetEmployee> targets) {
        return Result.success(salesTargetService.batchAllocate(targets));
    }

    /** 更新销售员目标 */
    @PutMapping("/employee/{id}")
    public Result<Void> updateEmployee(@PathVariable Long id, @RequestBody SalesTargetEmployee target) {
        salesTargetService.updateEmployeeTarget(id, target);
        return Result.success();
    }

    /** 删除销售员目标 */
    @DeleteMapping("/employee/{id}")
    public Result<Void> deleteEmployee(@PathVariable Long id) {
        salesTargetService.deleteEmployeeTarget(id);
        return Result.success();
    }

    /** 刷新某销售员实际完成指标（quarter=1~4 时按季度口径计算，不落库；否则按年度口径并写回） */
    @PostMapping("/employee/refresh")
    public Result<Map<String, Object>> refreshActual(
            @RequestParam Integer year,
            @RequestParam Long employeeId,
            @RequestParam(required = false) Integer quarter) {
        Map<String, Object> data = new HashMap<>(salesTargetService.refreshActualMetrics(year, employeeId, quarter));
        data.put("year", year);
        data.put("employeeId", employeeId);
        return Result.success(data);
    }

    // ==================== 看板 ====================

    /**
     * 获取某年度目标分配看板
     * 返回：公司目标/已分配/未分配/已分配人数/缺口人数/是否需要招聘
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) Integer year) {
        return Result.success(salesTargetService.getAllocationDashboard(year));
    }

    /**
     * 获取某年度销售目标完成情况排名
     */
    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> ranking(@RequestParam(required = false) Integer year) {
        return Result.success(salesTargetService.getCompletionRanking(year));
    }
}
