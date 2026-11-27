package com.erp.finance.cost.calculation.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.entity.CostCalculationItem;
import com.erp.finance.cost.calculation.entity.CostReportItem;
import com.erp.finance.cost.calculation.service.CostCalculationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cost-calculation")
@RequiredArgsConstructor
public class CostCalculationController {

    private final CostCalculationService costCalculationService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<CostCalculation>> pageQuery(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围（替代 getCreatedByFilter 硬编码）
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(costCalculationService.pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<CostCalculation> getDetail(@PathVariable Long id) {
        return Result.success(costCalculationService.getDetail(id));
    }

    @PostMapping
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> data) {
        CostCalculation calc = new CostCalculation();
        // 支持更新已有草稿：前端二次保存（含提交前静默保存）携带id时走更新，避免重复插单
        if (data.get("id") != null && !data.get("id").toString().isBlank()) {
            calc.setId(Long.valueOf(data.get("id").toString()));
            CostCalculation existing = costCalculationService.getById(calc.getId());
            if (existing == null) throw new com.erp.config.BusinessException("成本核算单不存在");
            if (!"草稿".equals(existing.getStatus())) {
                throw new com.erp.config.BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
            }
        }
        calc.setCalcName((String) data.get("calcName"));
        calc.setMaterialId(data.get("materialId") != null ? Long.valueOf(data.get("materialId").toString()) : null);
        calc.setMaterialCode((String) data.get("materialCode"));
        calc.setMaterialName((String) data.get("materialName"));
        calc.setQuantity(data.get("quantity") != null ? new java.math.BigDecimal(data.get("quantity").toString()) : null);
        calc.setCalcMethod((String) data.get("calcMethod"));
        // 表头「计税方式」（传输字段，驱动明细含税价/不含税价换算；库表无此列，不落库）
        calc.setTaxCalcMethod((String) data.get("taxCalcMethod"));
        calc.setRemark((String) data.get("remark"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) data.get("items");
        List<CostCalculationItem> items = itemList.stream().map(item -> {
            CostCalculationItem ci = new CostCalculationItem();
            ci.setCostType((String) item.get("costType"));
            ci.setCostName((String) item.get("costName"));
            ci.setMaterialId(item.get("materialId") != null ? Long.valueOf(item.get("materialId").toString()) : null);
            ci.setMaterialCode((String) item.get("materialCode"));
            ci.setMaterialName((String) item.get("materialName"));
            ci.setUnit((String) item.get("unit"));
            ci.setQuantity(item.get("quantity") != null ? new java.math.BigDecimal(item.get("quantity").toString()) : null);
            ci.setUnitPrice(item.get("unitPrice") != null ? new java.math.BigDecimal(item.get("unitPrice").toString()) : null);
            ci.setAmount(item.get("amount") != null ? new java.math.BigDecimal(item.get("amount").toString()) : null);
            return ci;
        }).toList();

        costCalculationService.saveCostCalculation(calc, items);
        return Result.success(Map.of("id", calc.getId()));
    }

    @PutMapping("/{id}/calculate")
    public Result<Void> calculate(@PathVariable Long id) {
        costCalculationService.calculateCost(id);
        return Result.success();
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        costCalculationService.confirm(id);
        return Result.success();
    }

    /** 反确认：已确认 -> 已核算（解锁，可重新核算） */
    @PutMapping("/{id}/reverse-confirm")
    public Result<Void> reverseConfirm(@PathVariable Long id) {
        costCalculationService.reverseConfirm(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        costCalculationService.delete(id);
        return Result.success();
    }

    @GetMapping("/unit-cost/{materialId}")
    public Result<java.math.BigDecimal> getUnitCost(@PathVariable Long materialId) {
        return Result.success(costCalculationService.getUnitCost(materialId));
    }

    /** 当期核算 - 为指定期间的所有物料自动归集成本 */
    @PostMapping("/period-calculate")
    public Result<Map<String, Object>> periodCalculate(@RequestBody Map<String, String> params) {
        String period = params.get("period");
        if (period == null || period.isBlank()) {
            return Result.error("核算期间不能为空");
        }
        return Result.success(costCalculationService.periodCalculate(period));
    }

    /** 成本报表 - 获取指定期间的物料成本汇总 */
    @GetMapping("/report")
    public Result<List<CostReportItem>> getCostReport(
            @RequestParam String period,
            @RequestParam(defaultValue = "") String keyword) {
        return Result.success(costCalculationService.getCostReport(period, keyword));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        costCalculationService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        costCalculationService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        costCalculationService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        costCalculationService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        costCalculationService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        costCalculationService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}