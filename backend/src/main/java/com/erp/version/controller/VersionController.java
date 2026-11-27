package com.erp.version.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.Result;
import com.erp.version.entity.DataVersionRecord;
import com.erp.version.service.DataVersionRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据版本【只读】Controller
 * 路由: /api/version
 *
 * 【2026-09-15 安全收口】原「回滚 / 禁用回滚 / 启用回滚」3 个写端点已删除：
 * 它们无权限码（任何登录用户可调），且 rollback 在 UPDATE 影响 0 行时静默失效却仍标记"已回滚"。
 * 业务兜底改由「反向流程（重置草稿/反作废/反结案）+ 红字冲销 + 账套备份」承担（对齐金蝶/用友口径）。
 * 原实现要点与缺口清单见 docs/操作日志-功能登记.md「回滚能力现状」。
 *
 * 现仅提供只读能力:
 *   1. 查询某条记录的全部版本历史   GET /api/version/list?tableName=xxx&recordId=xxx
 *   2. 分页查询所有版本记录         GET /api/version/page?module=xxx&current=1&size=20
 *   3. 查看版本详情                 GET /api/version/{versionId}
 *   4. 按模块统计版本数             GET /api/version/stats
 */
@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final DataVersionRecordService versionService;

    public VersionController(DataVersionRecordService versionService) {
        this.versionService = versionService;
    }

    /** 查询某条记录的全部版本历史 */
    @GetMapping("/list")
    public Result<List<DataVersionRecord>> list(
            @RequestParam String tableName,
            @RequestParam Long recordId) {
        return Result.success(versionService.listVersions(tableName, recordId));
    }

    /** 分页查询版本记录 */
    @GetMapping("/page")
    public Result<Page<DataVersionRecord>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String operationType) {
        Page<DataVersionRecord> page = new Page<>(current, size);
        QueryWrapper<DataVersionRecord> qw = new QueryWrapper<>();
        if (module != null && !module.isBlank()) qw.eq("business_module", module);
        if (tableName != null && !tableName.isBlank()) qw.eq("table_name", tableName);
        if (operationType != null && !operationType.isBlank()) qw.eq("operation_type", operationType);
        qw.orderByDesc("version_id");
        return Result.success(versionService.page(page, qw));
    }

    /** 版本详情 */
    @GetMapping("/{versionId}")
    public Result<DataVersionRecord> detail(@PathVariable Long versionId) {
        return Result.success(versionService.getById(versionId));
    }

    /** 按模块统计版本数 */
    @GetMapping("/stats")
    public Result<List<ModuleStat>> stats() {
        List<DataVersionRecord> all = versionService.list();
        java.util.Map<String, long[]> agg = new java.util.LinkedHashMap<>();
        for (DataVersionRecord v : all) {
            String m = v.getBusinessModule();
            long[] arr = agg.computeIfAbsent(m, k -> new long[3]);
            arr[0]++;
            if ("UPDATE".equals(v.getOperationType())) arr[1]++;
            if (v.getRolledBack() != null && v.getRolledBack() == 1) arr[2]++;
        }
        List<ModuleStat> result = new java.util.ArrayList<>();
        agg.forEach((m, arr) -> result.add(new ModuleStat(m, arr[0], arr[1], arr[2])));
        return Result.success(result);
    }

    public static class ModuleStat {
        public String module;
        public long totalVersions;
        public long updateCount;
        public long rollbackCount;
        public ModuleStat() {}
        public ModuleStat(String module, long t, long u, long r) {
            this.module = module; this.totalVersions = t; this.updateCount = u; this.rollbackCount = r;
        }
    }

}
