package com.erp.crm.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.SalesWarning;
import com.erp.crm.service.SalesWarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 销售预警（2026-10-07）
 *
 * <p>列表 / 汇总 / 处理 / 关闭 / 手动生成 / 手动超时升级。规则与分档口径见 SalesWarningService。</p>
 */
@RestController
@RequestMapping("/api/crm/sales-warning")
@RequiredArgsConstructor
public class SalesWarningController {

    private final SalesWarningService salesWarningService;

    @GetMapping("/page")
    public Result<PageResult<SalesWarning>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String warningType,
            @RequestParam(required = false) String keyword) {
        return Result.success(salesWarningService.pageQuery(current, size, level, status, warningType, keyword));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(salesWarningService.summary());
    }

    /** 手动生成指定日期的预警（页面「立即结算」按钮 / 排障用） */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestParam(required = false) String date) {
        LocalDate day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return Result.success(salesWarningService.generate(day));
    }

    /** 手动触发超时升级 */
    @PostMapping("/escalate")
    public Result<Map<String, Object>> escalate() {
        int n = salesWarningService.escalate();
        return Result.success(Map.of("escalated", n));
    }

    /** 认领：待处理 → 处理中（闭环状态机第二态） */
    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        salesWarningService.claim(id, currentUserId(request), currentUsername(request));
        return Result.success();
    }

    /** 处理（填处理说明 + 可传凭证）→ 已处理待确认 */
    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleRequest req,
                               jakarta.servlet.http.HttpServletRequest request) {
        salesWarningService.handle(id, req.getRemark(), req.getAttachment(),
                currentUserId(request), currentUsername(request));
        return Result.success();
    }

    /** 关闭 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody HandleRequest req,
                              jakarta.servlet.http.HttpServletRequest request) {
        salesWarningService.close(id, req.getRemark(), currentUserId(request), currentUsername(request));
        return Result.success();
    }

    private Long currentUserId(jakarta.servlet.http.HttpServletRequest request) {
        Object v = request.getAttribute("currentUserId");
        return v == null ? null : Long.valueOf(v.toString());
    }

    private String currentUsername(jakarta.servlet.http.HttpServletRequest request) {
        Object v = request.getAttribute("currentUsername");
        return v == null ? null : v.toString();
    }

    public static class HandleRequest {
        private String remark;
        private String attachment;

        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getAttachment() { return attachment; }
        public void setAttachment(String attachment) { this.attachment = attachment; }
    }
}
