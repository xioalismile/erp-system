package com.erp.finance.wage.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.util.SecurityUtils;
import com.erp.finance.wage.dto.WageSummaryDTO;
import com.erp.finance.wage.entity.WageSettlement;
import com.erp.finance.wage.entity.WageSettlementItem;
import com.erp.finance.wage.service.WageSettlementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工序工资核算控制器
 * 路径前缀: /api/finance/wage-settlements
 * 状态流转: 草稿 →(提交/免审批即生效) 已生效 →(作废) 已作废 →(反作废) 已生效
 */
@RestController
@RequestMapping("/api/finance/wage-settlements")
@RequiredArgsConstructor
public class WageSettlementController {

    private final WageSettlementService wageSettlementService;

    /** 按员工聚合的汇总行（页面主表格数据源） */
    @GetMapping("/summary")
    public Result<List<WageSummaryDTO>> summary(@RequestParam(required = false) String period,
                                                @RequestParam(required = false) Long deptId,
                                                @RequestParam(required = false) Long employeeId) {
        return Result.success(wageSettlementService.summary(period, deptId, employeeId));
    }

    /** 已生成工资核算单分页 */
    @GetMapping("/page")
    public Result<PageResult<WageSettlement>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        int[] p = ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(wageSettlementService.pageQuery(p[0], p[1], period, status, keyword, headerFilters));
    }

    @GetMapping("/{id}")
    public Result<WageSettlement> getDetail(@PathVariable Long id) {
        return Result.success(wageSettlementService.getDetail(id));
    }

    @GetMapping("/{id}/items")
    public Result<List<WageSettlementItem>> getItems(@PathVariable Long id) {
        return Result.success(wageSettlementService.getItems(id));
    }

    /** 按期间生成工资核算单（一张单 = 一个期间，明细行 = 员工），保存为草稿 */
    @PostMapping
    public Result<WageSettlement> generate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String period = body.get("period") == null ? null : body.get("period").toString();
        Long deptId = parseLong(body.get("deptId"));
        Long employeeId = parseLong(body.get("employeeId"));
        String operator = SecurityUtils.getCurrentUsername(request);
        return Result.success(wageSettlementService.generate(period, deptId, employeeId, operator));
    }

    /** 提交：草稿 → 已生效 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest request) {
        wageSettlementService.submit(id, SecurityUtils.getCurrentUsername(request));
        return Result.success();
    }

    /** 作废：已生效 → 已作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, String> body,
                                 HttpServletRequest request) {
        String reason = body == null ? null : body.get("reason");
        wageSettlementService.voidBill(id, SecurityUtils.getCurrentUsername(request), reason);
        return Result.success();
    }

    /** 反作废：已作废 → 已生效 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest request) {
        wageSettlementService.unvoidBill(id, SecurityUtils.getCurrentUsername(request));
        return Result.success();
    }

    private Long parseLong(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
