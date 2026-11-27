package com.erp.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.Lead;
import com.erp.crm.service.LeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/crm/lead")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/page")
    public Result<PageResult<Lead>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String leadStatus,
            @RequestParam(required = false) String intentionLevel,
            @RequestParam(required = false) Long assignedSalesId,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(leadService.pageQuery(current, size, keyword, leadStatus, intentionLevel, assignedSalesId, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<Lead> detail(@PathVariable Long id) {
        return Result.success(leadService.getDetail(id));
    }

    @PostMapping
    public Result<Lead> create(@RequestBody Lead lead, jakarta.servlet.http.HttpServletRequest request) {
        // === 归属隔离：新建线索默认归属当前登录用户，确保列表按 assigned_sales_id 过滤可见 ===
        Long currentUserId = (Long) request.getAttribute("currentUserId");
        if (currentUserId != null && lead.getAssignedSalesId() == null) {
            lead.setAssignedSalesId(currentUserId);
        }
        leadService.saveLead(lead);
        return Result.success(lead);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Lead lead) {
        lead.setLeadId(id);
        leadService.updateById(lead);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Lead lead = leadService.getById(id);
        if (lead == null) {
            throw new com.erp.config.BusinessException("线索不存在");
        }
        leadService.removeById(id);
        return Result.success();
    }

    /** 更新线索状态: 新线索/跟进中/已转化/已放弃 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        leadService.updateLeadStatus(id, body.get("status"));
        return Result.success();
    }

    /** 分配销售员 */
    @PutMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        leadService.assignSales(id, body.get("assignedSalesId"));
        return Result.success();
    }

    /** 放弃线索 */
    @PutMapping("/{id}/abandon")
    public Result<Void> abandon(@PathVariable Long id, @RequestBody Map<String, String> body) {
        leadService.abandon(id, body.get("reason"));
        return Result.success();
    }

    /** 转化为客户，返回新客户ID */
    @PostMapping("/{id}/convert")
    public Result<Long> convert(@PathVariable Long id) {
        return Result.success(leadService.convertToCustomer(id));
    }

    /** 仪表盘统计（与列表一致，按 assigned_sales_id 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);

        QueryWrapper<Lead> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, "assigned_sales_id");
        long totalLeads = leadService.count(totalW);

        QueryWrapper<Lead> newW = new QueryWrapper<Lead>().eq("lead_status", "新线索");
        scopeCtx.applyTo(newW, "assigned_sales_id");
        long newLeads = leadService.count(newW);

        QueryWrapper<Lead> followingW = new QueryWrapper<Lead>().eq("lead_status", "跟进中");
        scopeCtx.applyTo(followingW, "assigned_sales_id");
        long followingLeads = leadService.count(followingW);

        QueryWrapper<Lead> convertedW = new QueryWrapper<Lead>().eq("lead_status", "已转化");
        scopeCtx.applyTo(convertedW, "assigned_sales_id");
        long convertedLeads = leadService.count(convertedW);

        QueryWrapper<Lead> abandonedW = new QueryWrapper<Lead>().eq("lead_status", "已放弃");
        scopeCtx.applyTo(abandonedW, "assigned_sales_id");
        long abandonedLeads = leadService.count(abandonedW);

        QueryWrapper<Lead> todayW = new QueryWrapper<Lead>().eq("next_follow_up_date", LocalDate.now());
        scopeCtx.applyTo(todayW, "assigned_sales_id");
        long todayFollowUps = leadService.count(todayW);

        Map<String, Object> statusDistribution = new HashMap<>();
        statusDistribution.put("新线索", newLeads);
        statusDistribution.put("跟进中", followingLeads);
        statusDistribution.put("已转化", convertedLeads);
        statusDistribution.put("已放弃", abandonedLeads);

        Map<String, Object> data = new HashMap<>();
        data.put("totalLeads", totalLeads);
        data.put("todayFollowUps", todayFollowUps);
        data.put("statusDistribution", statusDistribution);
        return Result.success(data);
    }
}
