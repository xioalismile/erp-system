package com.erp.report.custom.controller;

import com.erp.common.Result;
import com.erp.report.custom.entity.CustomReport;
import com.erp.report.custom.service.CustomReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 自定义报表控制器
 *
 * 路径 /api/report/custom/**，经 AuthInterceptor 统一登录鉴权。
 * 数据权限（销售/采购只看自己）由 CustomReportSqlBuilder 在 SQL 层强制注入。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/custom")
@RequiredArgsConstructor
public class CustomReportController {

    private final CustomReportService customReportService;

    // ==================== 设计器元数据 ====================

    /** 数据源列表（模块分组，设计器第一步选择） */
    @GetMapping("/datasources")
    public Result<Map<String, Object>> listDataSources() {
        return Result.success(customReportService.listDataSources());
    }

    /** 数据源完整元数据（主表字段 + 可用关联及字段） */
    @GetMapping("/datasources/{key}")
    public Result<Map<String, Object>> describeDataSource(@PathVariable String key) {
        return Result.success(customReportService.describeDataSource(key));
    }

    /** 跨单据关联候选列（双方树中同名的可关联外键列；受限角色跨模块在服务层拦截） */
    @GetMapping("/datasources/{key}/joinables/{targetKey}")
    public Result<Map<String, Object>> joinables(HttpServletRequest req, @PathVariable String key,
                                                 @PathVariable String targetKey) {
        return Result.success(customReportService.joinables(req, key, targetKey));
    }

    // ==================== 报表管理 ====================

    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest req,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(customReportService.listMyReports(req, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(HttpServletRequest req, @PathVariable Long id) {
        return Result.success(customReportService.detail(req, id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(HttpServletRequest req, @RequestBody CustomReport report) {
        return Result.success(customReportService.create(req, report));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(HttpServletRequest req, @PathVariable Long id,
                                              @RequestBody CustomReport report) {
        return Result.success(customReportService.update(req, id, report));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest req, @PathVariable Long id) {
        customReportService.delete(req, id);
        return Result.success();
    }

    @PostMapping("/{id}/copy")
    public Result<Map<String, Object>> copy(HttpServletRequest req, @PathVariable Long id) {
        return Result.success(customReportService.copy(req, id));
    }

    // ==================== 运行与导出 ====================

    @PostMapping("/{id}/run")
    public Result<Map<String, Object>> run(HttpServletRequest req, @PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        RunParams p = RunParams.of(body);
        return Result.success(customReportService.run(req, id, p.startDate, p.endDate,
                p.keyword, p.status, p.page, p.size, p.dateSource, p.dateField, p.conditionsJson));
    }

    @GetMapping("/{id}/export")
    public void export(HttpServletRequest req, HttpServletResponse resp, @PathVariable Long id,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String dateSource,
                       @RequestParam(required = false) String dateField,
                       @RequestParam(required = false) String conditions) {
        customReportService.export(req, resp, id, startDate, endDate, keyword, status,
                dateSource, dateField, conditions);
    }

    /**
     * 运行参数（body 可空）。
     * conditions 为「任意字段过滤条件」JSON 数组，原样序列化后传给服务（服务侧做白名单校验）。
     * dateSource/dateField 为查看页日期筛选兜底字段，报表未配置 dateField 时生效。
     */
    private static class RunParams {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        String startDate; String endDate; String keyword; String status;
        String dateSource; String dateField;
        String conditionsJson;
        int page = 1; int size = 20;

        static RunParams of(Map<String, Object> body) {
            RunParams p = new RunParams();
            if (body == null) return p;
            p.startDate = str(body.get("startDate"));
            p.endDate = str(body.get("endDate"));
            p.keyword = str(body.get("keyword"));
            p.status = str(body.get("status"));
            p.dateSource = str(body.get("dateSource"));
            p.dateField = str(body.get("dateField"));
            Object page = body.get("page");
            Object size = body.get("size");
            if (page instanceof Number n) p.page = n.intValue();
            if (size instanceof Number n) p.size = n.intValue();
            Object conditions = body.get("conditions");
            if (conditions != null) {
                try {
                    p.conditionsJson = MAPPER.writeValueAsString(conditions);
                } catch (Exception e) {
                    throw new IllegalArgumentException("筛选条件格式错误");
                }
            }
            return p;
        }

        static String str(Object v) {
            return v == null ? null : v.toString();
        }
    }
}
