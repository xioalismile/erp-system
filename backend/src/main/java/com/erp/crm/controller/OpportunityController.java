package com.erp.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crm/opportunity")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    private static final List<String> STAGES = Arrays.asList(
            "初步接触", "需求确认", "方案报价", "商务谈判", "成交", "已关闭");

    @GetMapping("/page")
    public Result<PageResult<Opportunity>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String salesStage,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long assignedSalesId,
            @RequestParam(required = false) Integer isClosed,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(opportunityService.pageQuery(current, size, keyword, salesStage,
                customerId, assignedSalesId, isClosed, scopeCtx, sortBy, sortOrder, customSearch));
    }

    @GetMapping("/{id}")
    public Result<Opportunity> detail(@PathVariable Long id) {
        return Result.success(opportunityService.getDetail(id));
    }

    @PostMapping
    public Result<Opportunity> create(@RequestBody Opportunity opportunity) {
        opportunityService.saveOpportunity(opportunity);
        return Result.success(opportunity);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Opportunity opportunity) {
        opportunity.setOpportunityId(id);
        opportunityService.updateById(opportunity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        opportunityService.removeById(id);
        return Result.success();
    }

    /** 更新销售阶段 */
    @PutMapping("/{id}/stage")
    public Result<Void> updateStage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        opportunityService.updateSalesStage(id, body.get("stage"), body.get("winReason"), body.get("loseReason"));
        return Result.success();
    }

    /** 关闭商机 */
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Boolean won = body.get("won") != null ? Boolean.valueOf(body.get("won").toString()) : false;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        opportunityService.closeOpportunity(id, won, reason);
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);

        QueryWrapper<Opportunity> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, null);
        long totalOpportunities = opportunityService.count(totalW);

        QueryWrapper<Opportunity> closedW = new QueryWrapper<Opportunity>().eq("is_closed", 1);
        scopeCtx.applyTo(closedW, null);
        long closedCount = opportunityService.count(closedW);

        QueryWrapper<Opportunity> wonW = new QueryWrapper<Opportunity>()
                .eq("is_closed", 1)
                .eq("sales_stage", "已关闭")
                .isNotNull("win_reason")
                .ne("win_reason", "");
        scopeCtx.applyTo(wonW, null);
        long wonCount = opportunityService.count(wonW);

        // 阶段分布
        Map<String, Object> stageDist = new HashMap<>();
        for (String stage : STAGES) {
            QueryWrapper<Opportunity> stageW = new QueryWrapper<Opportunity>().eq("sales_stage", stage);
            scopeCtx.applyTo(stageW, null);
            stageDist.put(stage, opportunityService.count(stageW));
        }

        // 进行中商机的预估金额合计
        BigDecimal totalEstimatedAmount = BigDecimal.ZERO;
        QueryWrapper<Opportunity> openW = new QueryWrapper<Opportunity>().eq("is_closed", 0);
        scopeCtx.applyTo(openW, null);
        List<Opportunity> openOpps = opportunityService.list(openW);
        for (Opportunity opp : openOpps) {
            if (opp.getEstimatedAmount() != null) {
                totalEstimatedAmount = totalEstimatedAmount.add(opp.getEstimatedAmount());
            }
        }

        // 逾期跟进：下次跟进日期早于今天且未关闭
        QueryWrapper<Opportunity> overdueW = new QueryWrapper<Opportunity>()
                .lt("next_follow_up_date", LocalDate.now())
                .eq("is_closed", 0);
        scopeCtx.applyTo(overdueW, null);
        long overdueFollowUps = opportunityService.count(overdueW);

        Map<String, Object> data = new HashMap<>();
        data.put("stageDist", stageDist);
        data.put("totalOpportunities", totalOpportunities);
        data.put("closedCount", closedCount);
        data.put("wonCount", wonCount);
        data.put("totalEstimatedAmount", totalEstimatedAmount);
        data.put("overdueFollowUps", overdueFollowUps);
        return Result.success(data);
    }
}
