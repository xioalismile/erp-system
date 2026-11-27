package com.erp.crm.controller;

import com.erp.common.Result;
import com.erp.crm.service.SalesTargetBreakdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 销售目标 · 五级分解（年/季/月/周/日）（2026-10-07）
 */
@RestController
@RequestMapping("/api/crm/sales-target-breakdown")
@RequiredArgsConstructor
public class SalesTargetBreakdownController {

    private final SalesTargetBreakdownService service;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/breakdown")
    public Result<Map<String, Object>> breakdown(@RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Long employeeId,
                                                 jakarta.servlet.http.HttpServletRequest request) {
        // 不传 employeeId 时自动取当前登录人对应的员工（我的驾驶舱零参数调用）
        if (employeeId == null) {
            try {
                employeeId = dataScopeResolver.resolve(request).getEmployeeId();
            } catch (Exception ignored) { }
        }
        return Result.success(service.breakdown(year, employeeId));
    }

    /** 手工覆盖某月目标（周/日目标随之重算） */
    @PostMapping("/month")
    public Result<Void> saveMonth(@RequestBody MonthRequest req) {
        service.saveMonthOverride(req.getYear(), req.getEmployeeId(), req.getMonthNo(), req.getAmount());
        return Result.success();
    }

    /** 取消某月手工覆盖（回到 季 ÷ 3） */
    @DeleteMapping("/month")
    public Result<Void> clearMonth(@RequestParam Integer year, @RequestParam Long employeeId,
                                   @RequestParam Integer monthNo) {
        service.clearMonthOverride(year, employeeId, monthNo);
        return Result.success();
    }

    public static class MonthRequest {
        private Integer year;
        private Long employeeId;
        private Integer monthNo;
        private BigDecimal amount;

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Integer getMonthNo() { return monthNo; }
        public void setMonthNo(Integer monthNo) { this.monthNo = monthNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
