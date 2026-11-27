package com.erp.subcontract.supplement.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.subcontract.supplement.entity.SubcontractSupplement;
import com.erp.subcontract.supplement.service.SubcontractSupplementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subcontract/supplements")
@RequiredArgsConstructor
public class SubcontractSupplementController {

    private final SubcontractSupplementService subcontractSupplementService;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @org.springframework.beans.factory.annotation.Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    @GetMapping("/page")
    public Result<PageResult<SubcontractSupplement>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            // 自定义字段搜索（设置中心勾选「可搜索」的字段）：JSON {fieldKey: 值}
            @RequestParam(required = false) String customSearch,
            // 表头字段过滤（2026-10-14 筛选条件自定义）：接收全部查询参数，只挑 f_ 前缀交给白名单翻译
            @RequestParam java.util.Map<String, String> allParams) {
        // 表头字段过滤（键剥掉 f_ 前缀；白名单外/空白值在 BillHeaderFilterSupport 内忽略）
        java.util.Map<String, String> headerFilters = com.erp.common.query.BillHeaderFilterSupport.extractFilters(allParams);
        return Result.success(subcontractSupplementService.pageQuery(current, size, keyword, status, sortBy, sortOrder, customSearch, headerFilters));
    }

    /** 统一数据权限解析件（按角色 sys_role.data_scope 过滤，禁止各页面自拼） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /**
     * 仪表盘统计（2026-09-20 新增）：与列表同数据权限口径（按 create_by 过滤）；
     * 「本月」按 supplement_date 取区间。状态：0 草稿 / 1 待审核 / 2 已生效
     */
    @GetMapping("/dashboard")
    public Result<java.util.Map<String, Object>> dashboard(HttpServletRequest request) {
        java.time.LocalDate monthStart = java.time.LocalDate.now().withDayOfMonth(1);
        java.time.LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        QueryWrapper<SubcontractSupplement> monthW = new QueryWrapper<>();
        com.erp.common.query.ListQuerySupport.applyDateRange(monthW, "supplement_date", monthStart.toString(), monthEnd.toString());
        dataScopeResolver.applyIfLimited(monthW, null, "create_by");
        long monthSupplements = subcontractSupplementService.count(monthW);

        QueryWrapper<SubcontractSupplement> draftW = new QueryWrapper<SubcontractSupplement>().eq("status", 0);
        dataScopeResolver.applyIfLimited(draftW, null, "create_by");
        long draftSupplements = subcontractSupplementService.count(draftW);

        QueryWrapper<SubcontractSupplement> pendingW = new QueryWrapper<SubcontractSupplement>().eq("status", 1);
        dataScopeResolver.applyIfLimited(pendingW, null, "create_by");
        long pendingSupplements = subcontractSupplementService.count(pendingW);

        QueryWrapper<SubcontractSupplement> effectiveW = new QueryWrapper<SubcontractSupplement>().eq("status", 2);
        dataScopeResolver.applyIfLimited(effectiveW, null, "create_by");
        long effectiveSupplements = subcontractSupplementService.count(effectiveW);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("monthSupplements", monthSupplements);
        data.put("draftSupplements", draftSupplements);
        data.put("pendingSupplements", pendingSupplements);
        data.put("effectiveSupplements", effectiveSupplements);
        return Result.success(data);
    }

    @GetMapping("/list")
    public Result<List<SubcontractSupplement>> list() {
        return Result.success(subcontractSupplementService.list(new QueryWrapper<SubcontractSupplement>().orderByDesc("create_time")));
    }

    @GetMapping("/{id}")
    public Result<SubcontractSupplement> detail(@PathVariable Long id) {
        return Result.success(subcontractSupplementService.getDetail(id));
    }

    @PostMapping
    public Result<SubcontractSupplement> create(@RequestBody SubcontractSupplement supplement) {
        subcontractSupplementService.saveSupplementWithItems(supplement, supplement.getItems());
        return Result.success(supplement);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SubcontractSupplement supplement) {
        supplement.setId(id);
        subcontractSupplementService.saveSupplementWithItems(supplement, supplement.getItems());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 2026-11-16：删除统一走单据状态机（草稿直接删；已生效先对冲库存再删；级联清明细+按编号清库存流水）
        subcontractSupplementService.deleteBill(id);
        return Result.success();
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        subcontractSupplementService.submitForApproval(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam(required = false) String approver,
                                HttpServletRequest req) {
        String operator = (approver != null && !approver.isBlank())
                ? approver
                : (String) req.getAttribute("currentUsername");
        subcontractSupplementService.approve(id, operator);
        return Result.success();
    }

    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        subcontractSupplementService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        subcontractSupplementService.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        subcontractSupplementService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        subcontractSupplementService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
