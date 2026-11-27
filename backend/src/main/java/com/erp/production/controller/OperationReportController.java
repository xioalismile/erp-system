package com.erp.production.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.service.OperationReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工序汇报单 Controller
 * 路由: /api/production/operation-report
 */
@RestController
@RequestMapping("/api/production/operation-report")
@RequiredArgsConstructor
public class OperationReportController {

    private final OperationReportService operationReportService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤列表与卡片 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<OperationReport>> page(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 自定义字段搜索（设置中心勾选「可搜索」的字段）：JSON {fieldKey: 值}
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(operationReportService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, headerFilters));
    }

    @GetMapping("/page")
    public Result<PageResult<OperationReport>> pageAlias(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 自定义字段搜索（设置中心勾选「可搜索」的字段）：JSON {fieldKey: 值}
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(operationReportService.pageQuery(p[0], p[1], keyword, status, startDate, endDate, employeeId, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 列表页顶部统计卡片数据（数据权限与列表同口径：按角色 data_scope（统一数据权限），统一用列表的 created_by 列过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("totalCount", countByScope(null, null, null));
        data.put("monthCount", countByScope(null, monthStart(), monthEnd()));
        data.put("draftCount", countByScope("草稿", null, null));
        data.put("pendingCount", countByScope("待审核", null, null));
        data.put("approvedCount", countByScope("已生效", null, null));
        return Result.success(data);
    }

    /**
     * 统计：状态（可空）+ 报工日期区间（可空）内的单据数。
     * 数据权限与列表逐字相同：同样按角色 data_scope（统一数据权限）过滤 created_by 列，
     * 避免出现「卡片数字 ≠ 列表条数」。
     */
    private long countByScope(String status, String startDate, String endDate) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OperationReport> w =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        com.erp.common.query.ListQuerySupport.applyDateRange(w, "report_date", startDate, endDate);
        dataScopeResolver.applyIfLimited(w, null, "created_by");
        return operationReportService.count(w);
    }

    /** 本月起始：当月 1 号 */
    private String monthStart() {
        return java.time.LocalDate.now().withDayOfMonth(1).toString();
    }

    /** 本月结束：当月最后一天（"1 号 ~ 最后一天"，与卡片点击套用的区间一致） */
    private String monthEnd() {
        return java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString();
    }

    @GetMapping("/list")
    public Result<List<OperationReport>> list() {
        return Result.success(operationReportService.list());
    }

    @GetMapping("/{id}")
    public Result<OperationReport> detail(@PathVariable Long id) {
        return Result.success(operationReportService.getDetail(id));
    }

    @PostMapping
    public Result<OperationReport> create(@RequestBody OperationReport report) {
        operationReportService.saveReport(report);
        return Result.success(report);
    }

    @PutMapping("/{id}")
    public Result<OperationReport> update(@PathVariable Long id, @RequestBody OperationReport report) {
        report.setReportId(id);
        operationReportService.saveReport(report);
        return Result.success(report);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 整单删除统一走状态机：草稿校验 + 下游校验 + 成本核算联动校验
        operationReportService.deleteBill(id);
        return Result.success();
    }

    /** 提交审核: 草稿 → 待审核 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestHeader(value = "X-Operator", required = false) String operator) {
        operationReportService.submit(id, operator);
        return Result.success();
    }

    /** 审核: 待审核 → 已生效 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        operationReportService.approve(id, operator);
        return Result.success();
    }

    @GetMapping("/generate-no")
    public Result<Map<String, String>> generateNo() {
        return Result.success(Map.of("reportNo", operationReportService.generateReportNo()));
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        operationReportService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        operationReportService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        operationReportService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        operationReportService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 结案: 已生效 → close_status=1（status 保持已生效，前端按 closeStatus 渲染已结案） */
    @PostMapping("/{id}/close-order")
    public Result<Void> closeOrder(@PathVariable Long id, HttpServletRequest req) {
        operationReportService.closeOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 反结案: close_status=0 */
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, HttpServletRequest req) {
        operationReportService.reopenOrder(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
