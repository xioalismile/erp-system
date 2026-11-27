package com.erp.system.onboarding;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.config.service.SysConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开账引导接口（2026-10-11）。
 *
 * <p>新客户初始管理员第一次登录 → 前端查本控制器状态 → 弹小楷向导；
 * 支持跳过（skipped，永不再弹）、按行业预览与执行初始化。</p>
 *
 * <p>权限：状态查询所有登录人可查（决定要不要弹向导）；
 * 跳过 / 预览 / 执行仅管理员（与"仅租户管理员可执行初始化"口径一致）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/system/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final IndustryInitService industryInitService;
    private final IndustryCatalog industryCatalog;
    private final SysConfigService sysConfigService;
    private final OnboardingNlParser nlParser;

    /** 当前引导状态：pending=未引导（弹向导）/ done=已完成 / skipped=已跳过；无记录按 done（存量客户绝不被弹） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        String status = sysConfigService.getValue(IndustryInitService.CFG_STATUS, "done");
        if (status == null || status.isBlank()) status = IndustryInitService.STATUS_DONE;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("industry", sysConfigService.getValue(IndustryInitService.CFG_INDUSTRY));
        data.put("time", sysConfigService.getValue(IndustryInitService.CFG_TIME));
        data.put("isAdmin", SecurityUtils.isAdmin(request));
        return Result.success(data);
    }

    /** 跳过引导（管理员；状态置 skipped，永不再自动弹，之后仍可对小楷说/手动执行初始化） */
    @PostMapping("/skip")
    public Result<Void> skip(HttpServletRequest request) {
        String deny = denyIfNotAdmin(request);
        if (deny != null) return Result.error(deny);
        sysConfigService.updateValue(IndustryInitService.CFG_STATUS, IndustryInitService.STATUS_SKIPPED);
        return Result.success();
    }

    /** 行业清单（向导下拉/快捷选项） */
    @GetMapping("/industries")
    public Result<List<Map<String, Object>>> industries() {
        return Result.success(industryInitService.listIndustries());
    }

    /** 自然语言解析：用户一句话描述公司 → 识别行业 + 人数 + 摘要（不写库） */
    @PostMapping("/parse")
    public Result<Map<String, Object>> parse(@RequestBody Map<String, Object> body) {
        String text = str(body.get("text"));
        return Result.success(nlParser.parse(text));
    }

    /** 预览（不写库）：确认卡数据源 */
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String deny = denyIfNotAdmin(request);
        if (deny != null) return Result.error(deny);
        String industryCode = str(body.get("industryCode"));
        Integer employeeCount = intOrNull(body.get("employeeCount"));
        return Result.success(industryInitService.preview(industryCode, employeeCount));
    }

    /** 执行初始化（管理员；幂等可重试；失败整体回滚，状态保持未完成） */
    @PostMapping("/initialize")
    public Result<Map<String, Object>> initialize(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String deny = denyIfNotAdmin(request);
        if (deny != null) return Result.error(deny);
        String industryCode = str(body.get("industryCode"));
        Integer employeeCount = intOrNull(body.get("employeeCount"));
        String operator = SecurityUtils.getCurrentRealName(request);
        try {
            Map<String, Object> result = industryInitService.initialize(industryCode, employeeCount, operator);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[开账引导] 初始化失败: industry={}, err={}", industryCode, e.getMessage(), e);
            return Result.error("初始化失败（已整体回滚，可重试）: " + e.getMessage());
        }
    }

    private static String denyIfNotAdmin(HttpServletRequest request) {
        return SecurityUtils.isAdmin(request) ? null : "仅管理员可执行开账引导操作";
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intOrNull(Object o) {
        if (o == null) return null;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
