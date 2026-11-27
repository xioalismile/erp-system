package com.erp.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.Survey;
import com.erp.crm.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crm/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/page")
    public Result<PageResult<Survey>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String surveyType,
            @RequestParam(required = false) Long customerId,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(surveyService.pageQuery(current, size, keyword, status, surveyType, customerId, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<Survey> detail(@PathVariable Long id) {
        return Result.success(surveyService.getDetail(id));
    }

    @PostMapping
    public Result<Survey> create(@RequestBody Survey survey) {
        surveyService.saveSurvey(survey);
        return Result.success(survey);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Survey survey) {
        survey.setSurveyId(id);
        surveyService.updateById(survey);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        surveyService.removeById(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        surveyService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        surveyService.complete(id);
        return Result.success();
    }

    @PutMapping("/{id}/analyze")
    public Result<Void> analyze(@PathVariable Long id, @RequestBody Map<String, String> body) {
        surveyService.analyze(id, body.get("analyzeResult"));
        return Result.success();
    }

    /** 仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);

        QueryWrapper<Survey> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, null);
        long totalSurveys = surveyService.count(totalW);

        QueryWrapper<Survey> draftW = new QueryWrapper<Survey>().eq("status", "草稿");
        scopeCtx.applyTo(draftW, null);
        long draftCount = surveyService.count(draftW);

        QueryWrapper<Survey> doingW = new QueryWrapper<Survey>().eq("status", "进行中");
        scopeCtx.applyTo(doingW, null);
        long doingCount = surveyService.count(doingW);

        QueryWrapper<Survey> completedW = new QueryWrapper<Survey>().eq("status", "已完成");
        scopeCtx.applyTo(completedW, null);
        long completedCount = surveyService.count(completedW);

        QueryWrapper<Survey> analyzedW = new QueryWrapper<Survey>().eq("status", "已分析");
        scopeCtx.applyTo(analyzedW, null);
        long analyzedCount = surveyService.count(analyzedW);

        Map<String, Long> statusDist = new HashMap<>();
        statusDist.put("草稿", draftCount);
        statusDist.put("进行中", doingCount);
        statusDist.put("已完成", completedCount);
        statusDist.put("已分析", analyzedCount);

        // 类型分布: 按调查类型分组统计
        QueryWrapper<Survey> typeWrapper = new QueryWrapper<>();
        typeWrapper.select("survey_type", "count(*) as cnt").groupBy("survey_type");
        scopeCtx.applyTo(typeWrapper, null);
        List<Map<String, Object>> typeMaps = surveyService.getBaseMapper().selectMaps(typeWrapper);
        Map<String, Long> typeDist = new HashMap<>();
        if (typeMaps != null) {
            for (Map<String, Object> m : typeMaps) {
                Object type = m.get("survey_type");
                Object cnt = m.get("cnt");
                typeDist.put(type == null ? "未分类" : type.toString(),
                        cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }

        // 低分数量: overall_score <= 3 且状态为已完成/已分析
        QueryWrapper<Survey> lowScoreW = new QueryWrapper<Survey>()
                .le("overall_score", 3).in("status", "已完成", "已分析");
        scopeCtx.applyTo(lowScoreW, null);
        long lowScoreCount = surveyService.count(lowScoreW);

        Map<String, Object> data = new HashMap<>();
        data.put("totalSurveys", totalSurveys);
        data.put("statusDist", statusDist);
        data.put("typeDist", typeDist);
        data.put("lowScoreCount", lowScoreCount);
        data.put("avgOverall", avgScore("overall_score", scopeCtx));
        data.put("avgProductScore", avgScore("product_score", scopeCtx));
        data.put("avgServiceScore", avgScore("service_score", scopeCtx));
        data.put("avgPriceScore", avgScore("price_score", scopeCtx));
        data.put("avgDeliveryScore", avgScore("delivery_score", scopeCtx));
        data.put("avgAfterSalesScore", avgScore("after_sales_score", scopeCtx));
        return Result.success(data);
    }

    /** 计算指定评分列的平均分(状态为已完成/已分析，按数据权限过滤) */
    private double avgScore(String column, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<Survey> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(" + column + ") as avgScore")
                .in("status", "已完成", "已分析");
        scopeCtx.applyTo(wrapper, null);
        List<Object> objs = surveyService.getBaseMapper().selectObjs(wrapper);
        if (objs == null || objs.isEmpty() || objs.get(0) == null) {
            return 0.0;
        }
        return ((Number) objs.get(0)).doubleValue();
    }
}
