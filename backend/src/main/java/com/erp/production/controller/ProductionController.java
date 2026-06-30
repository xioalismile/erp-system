package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.WorkOrder;
import com.erp.production.service.MrpCalculationService;
import com.erp.production.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final WorkOrderService workOrderService;
    private final MrpCalculationService mrpCalculationService;

    @GetMapping("/workorders")
    public Result<PageResult<WorkOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(workOrderService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/workorders/{id}")
    public Result<WorkOrder> detail(@PathVariable Long id) {
        return Result.success(workOrderService.getDetail(id));
    }

    @PostMapping("/workorders")
    public Result<Void> create(@RequestBody WorkOrder wo) {
        workOrderService.saveOrderWithItems(wo, wo.getMaterials());
        return Result.success();
    }

    @PutMapping("/workorders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WorkOrder wo) {
        wo.setOrderId(id);
        workOrderService.saveOrderWithItems(wo, wo.getMaterials());
        return Result.success();
    }

    @PostMapping("/workorders/{id}/release")
    public Result<Void> release(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        workOrderService.release(id, operator);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/complete")
    public Result<Void> complete(@PathVariable Long id,
                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
        workOrderService.complete(id, operator);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                             @RequestHeader(value = "X-Operator", required = false) String operator) {
        String reason = body != null ? body.get("reason") : null;
        workOrderService.close(id, operator, reason);
        return Result.success();
    }

    @GetMapping("/workorders/generate-no")
    public Result<Map<String, String>> generateOrderNo() {
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", workOrderService.generateOrderNo());
        return Result.success(map);
    }

    /** 仪表盘统计: 工单总数 + 各状态数量 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        long total = workOrderService.count();
        stats.put("totalWorkOrders", total);
        stats.put("draft", workOrderService.count(new QueryWrapper<WorkOrder>().eq("status", "草稿")));
        stats.put("released", workOrderService.count(new QueryWrapper<WorkOrder>().eq("status", "已下达")));
        stats.put("completed", workOrderService.count(new QueryWrapper<WorkOrder>().eq("status", "已完工")));
        stats.put("closed", workOrderService.count(new QueryWrapper<WorkOrder>().eq("status", "已关闭")));
        return Result.success(stats);
    }

    @PostMapping("/mrp/calculate")
    public Result<MrpCalculationService.MrpResult> calculateMrp(@RequestBody Map<String, Object> body) {
        String materialCode = (String) body.get("materialCode");
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        LocalDate demandDate = body.get("demandDate") != null
                ? LocalDate.parse(body.get("demandDate").toString()) : LocalDate.now().plusDays(30);
        return Result.success(mrpCalculationService.calculate(materialCode, quantity, demandDate));
    }

    @PostMapping("/mrp/calculate-batch")
    public Result<List<MrpCalculationService.MrpResult>> calculateMrpBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> demands = (List<Map<String, Object>>) body.get("demands");
        List<MrpCalculationService.MrpDemand> mrpDemands = new ArrayList<>();
        for (Map<String, Object> d : demands) {
            String materialCode = (String) d.get("materialCode");
            BigDecimal quantity = new BigDecimal(d.get("quantity").toString());
            LocalDate demandDate = d.get("demandDate") != null
                    ? LocalDate.parse(d.get("demandDate").toString()) : LocalDate.now().plusDays(30);
            mrpDemands.add(new MrpCalculationService.MrpDemand(materialCode, quantity, demandDate));
        }
        return Result.success(mrpCalculationService.calculateBatch(mrpDemands));
    }

    @PostMapping("/mrp/bom-expand")
    public Result<Map<String, Object>> expandBom(@RequestBody Map<String, Object> body) {
        String materialCode = (String) body.get("materialCode");
        BigDecimal quantity = body.get("quantity") != null
                ? new BigDecimal(body.get("quantity").toString()) : BigDecimal.ONE;
        return Result.success(mrpCalculationService.expandBom(materialCode, quantity));
    }
}
