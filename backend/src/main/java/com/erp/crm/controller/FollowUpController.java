package com.erp.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.FollowUp;
import com.erp.crm.service.FollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crm/follow-up")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpService followUpService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 分页查询跟进记录 */
    @GetMapping("/page")
    public Result<PageResult<FollowUp>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String followUpType,
            @RequestParam(required = false) Integer importantFlag,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(followUpService.pageQuery(current, size, keyword, sourceType,
                sourceId, followUpType, importantFlag, scopeCtx, sortBy, sortOrder));
    }

    /** 跟进记录详情 */
    @GetMapping("/{id}")
    public Result<FollowUp> detail(@PathVariable Long id) {
        return Result.success(followUpService.getDetail(id));
    }

    /** 新增跟进记录 */
    @PostMapping
    public Result<FollowUp> create(@RequestBody FollowUp fu) {
        followUpService.saveFollowUp(fu);
        return Result.success(fu);
    }

    /** 修改跟进记录 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody FollowUp fu) {
        fu.setFollowUpId(id);
        followUpService.updateById(fu);
        return Result.success();
    }

    /** 删除跟进记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        followUpService.removeById(id);
        return Result.success();
    }

    /** 按来源查询跟进历史 (客户/线索/商机的跟进记录列表) */
    @GetMapping("/by-source")
    public Result<List<FollowUp>> bySource(@RequestParam String sourceType,
                                            @RequestParam Long sourceId,
                                            jakarta.servlet.http.HttpServletRequest request) {
        QueryWrapper<FollowUp> wrapper = new QueryWrapper<>();
        wrapper.eq("source_type", sourceType).eq("source_id", sourceId).orderByDesc("follow_up_date");
        // 数据权限（2026-10-09）：与 /page 同口径，按 created_by 收敛可见范围
        dataScopeResolver.resolve(request).applyTo(wrapper, null);
        return Result.success(followUpService.list(wrapper));
    }

    /** 今日待跟进任务 (next_follow_up_date = 今天) */
    @GetMapping("/today")
    public Result<List<FollowUp>> today(jakarta.servlet.http.HttpServletRequest request) {
        QueryWrapper<FollowUp> wrapper = new QueryWrapper<>();
        wrapper.eq("next_follow_up_date", LocalDate.now()).orderByDesc("important_flag");
        // 数据权限（2026-10-09）：与 /page 同口径，按 created_by 收敛可见范围
        dataScopeResolver.resolve(request).applyTo(wrapper, null);
        return Result.success(followUpService.list(wrapper));
    }

    /** 跟进记录仪表盘统计（与列表一致，按 created_by 数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);

        QueryWrapper<FollowUp> totalW = new QueryWrapper<>();
        scopeCtx.applyTo(totalW, null);
        long totalFollowUps = followUpService.count(totalW);

        QueryWrapper<FollowUp> todayW = new QueryWrapper<FollowUp>().eq("next_follow_up_date", LocalDate.now());
        scopeCtx.applyTo(todayW, null);
        long todayTasks = followUpService.count(todayW);

        QueryWrapper<FollowUp> importantW = new QueryWrapper<FollowUp>().eq("important_flag", 1);
        scopeCtx.applyTo(importantW, null);
        long importantCount = followUpService.count(importantW);

        // 按跟进方式统计
        QueryWrapper<FollowUp> typeWrapper = new QueryWrapper<>();
        typeWrapper.select("follow_up_type", "count(*) as cnt").groupBy("follow_up_type");
        scopeCtx.applyTo(typeWrapper, null);
        List<Map<String, Object>> typeRows = followUpService.listMaps(typeWrapper);
        Map<String, Long> byType = new HashMap<>();
        for (Map<String, Object> row : typeRows) {
            String type = row.get("follow_up_type") != null ? row.get("follow_up_type").toString() : "未知";
            long cnt = row.get("cnt") != null ? Long.parseLong(row.get("cnt").toString()) : 0L;
            byType.put(type, cnt);
        }

        // 按来源类型统计
        QueryWrapper<FollowUp> sourceWrapper = new QueryWrapper<>();
        sourceWrapper.select("source_type", "count(*) as cnt").groupBy("source_type");
        scopeCtx.applyTo(sourceWrapper, null);
        List<Map<String, Object>> sourceRows = followUpService.listMaps(sourceWrapper);
        Map<String, Long> bySourceType = new HashMap<>();
        for (Map<String, Object> row : sourceRows) {
            String type = row.get("source_type") != null ? row.get("source_type").toString() : "未知";
            long cnt = row.get("cnt") != null ? Long.parseLong(row.get("cnt").toString()) : 0L;
            bySourceType.put(type, cnt);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalFollowUps", totalFollowUps);
        data.put("todayTasks", todayTasks);
        data.put("importantCount", importantCount);
        data.put("byType", byType);
        data.put("bySourceType", bySourceType);
        return Result.success(data);
    }
}
