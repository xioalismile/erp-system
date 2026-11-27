package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.Routing;
import com.erp.production.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/routings")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @GetMapping("/page")
    public Result<PageResult<Routing>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        return Result.success(routingService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, sortBy, sortOrder));
    }

    /** 仪表盘统计（基础资料类：与 /page 一致不做数据权限过滤）——工艺路线已降为"主档"，只有 启用/停用 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", routingService.count(new QueryWrapper<Routing>()));
        stats.put("enabledCount", routingService.count(new QueryWrapper<Routing>().eq("status", "启用")));
        stats.put("disabledCount", routingService.count(new QueryWrapper<Routing>().eq("status", "停用")));
        // 工序类型取值清单：唯一事实来源在后端，前端下拉直接取（避免前后端各写一份）
        stats.put("operationTypes", com.erp.production.service.RoutingServiceImpl.OPERATION_TYPES);
        return Result.success(stats);
    }

    @GetMapping("/list")
    public Result<List<Routing>> list() {
        return Result.success(routingService.list());
    }

    /** 【2026-11-26】按物料取适用的工艺路线（工单成品行选路线时过滤） */
    @GetMapping("/applicable")
    public Result<List<Routing>> applicable(@RequestParam(required = false) Long materialId) {
        return Result.success(routingService.listApplicableByMaterial(materialId));
    }

    /** 工艺路线里配置过的工序名（去重）—— 质检模板「适用工序」下拉等取候选用 */
    @GetMapping("/operation-names")
    public Result<List<String>> operationNames() {
        return Result.success(routingService.listAllOperationNames());
    }

    @GetMapping("/{id}")
    public Result<Routing> detail(@PathVariable Long id) {
        return Result.success(routingService.getDetail(id));
    }

    @PostMapping
    public Result<Routing> create(@RequestBody Routing routing) {
        routingService.saveRoutingWithOperations(routing, routing.getOperations());
        return Result.success(routing);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Routing routing) {
        routing.setRoutingId(id);
        routingService.saveRoutingWithOperations(routing, routing.getOperations());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        routingService.deleteRouting(id);
        return Result.success();
    }

    /**
     * 【兼容保留】审核端点：工艺路线已从"单据"降为"主档"，不再走审核。
     * 保留端点是为了老前端/老书签调用时能拿到明确提示，而不是 404。
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        routingService.approve(id, operator);
        return Result.success();
    }

    /** 启用：停用 → 启用 */
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id,
                               @RequestHeader(value = "X-Operator", required = false) String operator) {
        routingService.enable(id, operator);
        return Result.success();
    }

    /** 停用：启用 → 停用 */
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        routingService.disable(id, operator);
        return Result.success();
    }
}
