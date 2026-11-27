package com.erp.report.custom.controller;

import com.erp.common.Result;
import com.erp.report.custom.entity.CustomDashboard;
import com.erp.report.custom.service.CustomDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 自定义仪表盘控制器
 *
 * 路径 /api/report/custom/dashboard/**（字面量路径优先于 /{id} 通配匹配），
 * 经 AuthInterceptor 统一登录鉴权；卡片数据权限由报表可见性双重校验保证。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/custom/dashboard")
@RequiredArgsConstructor
public class CustomDashboardController {

    private final CustomDashboardService customDashboardService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest req,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(customDashboardService.listMyDashboards(req, keyword, page, size));
    }

    /** 详情（含卡片解析：报表名/模式/图表类型/是否可访问） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(HttpServletRequest req, @PathVariable Long id) {
        return Result.success(customDashboardService.detail(req, id));
    }

    /** 查看数据（逐卡片执行报表，异常卡片单独提示） */
    @GetMapping("/{id}/data")
    public Result<Map<String, Object>> data(HttpServletRequest req, @PathVariable Long id) {
        return Result.success(customDashboardService.data(req, id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(HttpServletRequest req, @RequestBody CustomDashboard dashboard) {
        return Result.success(customDashboardService.create(req, dashboard));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(HttpServletRequest req, @PathVariable Long id,
                                              @RequestBody CustomDashboard dashboard) {
        return Result.success(customDashboardService.update(req, id, dashboard));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest req, @PathVariable Long id) {
        customDashboardService.delete(req, id);
        return Result.success();
    }
}
