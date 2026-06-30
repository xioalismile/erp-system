package com.erp.production.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.WorkOrder;
import com.erp.production.service.MrpCalculationService;
import com.erp.production.service.WorkOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workOrderNo) {
        return Result.success(workOrderService.pageQuery(current, size, productCode, status, workOrderNo));
    }

    @GetMapping("/workorders/{id}")
    public Result<WorkOrder> detail(@PathVariable Long id) {
        return Result.success(workOrderService.getDetail(id));
    }

    @GetMapping("/workorders/{id}/progress")
    public Result<Map<String, Object>> progress(@PathVariable Long id) {
        return Result.success(workOrderService.getProgress(id));
    }

    @PostMapping("/workorders")
    public Result<WorkOrder> create(@RequestBody WorkOrder wo) {
        return Result.success(workOrderService.createWorkOrder(wo));
    }

    @PutMapping("/workorders/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WorkOrder wo) {
        wo.setId(id);
        workOrderService.updateWorkOrder(wo);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/issue")
    public Result<Void> issue(@PathVariable Long id, @RequestBody IssueRequest body) {
        workOrderService.issueMaterial(id, body.getMaterialId(), body.getQuantity());
        return Result.success();
    }

    @PostMapping("/workorders/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        workOrderService.startWork(id);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/report")
    public Result<Void> report(@PathVariable Long id, @RequestBody ReportRequest body) {
        workOrderService.reportCompletion(id, body.getCompletedQty(), body.getScrapQty());
        return Result.success();
    }

    @PostMapping("/workorders/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        workOrderService.finishWork(id);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        workOrderService.closeWork(id);
        return Result.success();
    }

    @PostMapping("/workorders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        workOrderService.cancelWork(id, body.get("reason"));
        return Result.success();
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

    @PostMapping("/mrp/generate-workorders")
    public Result<List<WorkOrder>> generateWorkOrders(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
        LocalDate demandDate = body.get("demandDate") != null
                ? LocalDate.parse(body.get("demandDate").toString()) : LocalDate.now().plusDays(30);
        return Result.success(workOrderService.generateFromMrp(suggestions, demandDate));
    }

    @PostMapping("/workorders/from-sale-order")
    public Result<List<WorkOrder>> generateFromSaleOrder(@RequestBody Map<String, Object> body) {
        Long saleOrderId = Long.valueOf(body.get("saleOrderId").toString());
        @SuppressWarnings("unchecked")
        List<Long> itemIds = body.get("itemIds") != null
                ? (List<Long>) body.get("itemIds") : null;
        return Result.success(workOrderService.generateFromSaleOrder(saleOrderId, itemIds));
    }

    @Data
    public static class IssueRequest {
        private Long materialId;
        private BigDecimal quantity;
    }

    @Data
    public static class ReportRequest {
        private BigDecimal completedQty;
        private BigDecimal scrapQty;
    }
}