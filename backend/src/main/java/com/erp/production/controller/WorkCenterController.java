package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.config.BusinessException;
import com.erp.production.entity.WorkCenter;
import com.erp.production.service.WorkCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作中心 Controller
 * 路由: /api/production/work-center
 */
@RestController
@RequestMapping("/api/production/work-center")
@RequiredArgsConstructor
public class WorkCenterController {

    private final WorkCenterService workCenterService;

    @GetMapping("/page")
    public Result<PageResult<WorkCenter>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierId,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        return Result.success(workCenterService.pageQuery(p[0], p[1], keyword, status, supplierId, employeeId, sortBy, sortOrder));
    }

    /** 仪表盘统计（基础资料类：与 /page 一致不做数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", workCenterService.count(new QueryWrapper<WorkCenter>()));
        stats.put("enabledCount", workCenterService.count(new QueryWrapper<WorkCenter>().eq("status", "启用")));
        stats.put("disabledCount", workCenterService.count(new QueryWrapper<WorkCenter>().eq("status", "停用")));
        // 「委外类型」卡：委外工作中心计数（is_outsourced = 1）
        stats.put("outsourcedCount", workCenterService.count(new QueryWrapper<WorkCenter>().eq("is_outsourced", 1)));
        return Result.success(stats);
    }

    @GetMapping("/list")
    public Result<List<WorkCenter>> list(@RequestParam(required = false) String status) {
        QueryWrapper<WorkCenter> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc("work_center_code");
        return Result.success(workCenterService.list(qw));
    }

    /**
     * 【即输即存】工作中心按名称"找不到就建、找到就复用"。
     *
     * <p>用户口径（2026-09-23）：工序明细里的「工作中心」要能直接输入回车保存，下次还能选到，
     * 不必先跳到「工作中心」菜单建档案。按<b>名称</b>去重：同名视为同一条，编码自动生成。
     */
    @PostMapping("/quick-save")
    public Result<WorkCenter> quickSave(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) return Result.error(400, "工作中心名称不能为空");
        WorkCenter exist = workCenterService.getOne(
                new QueryWrapper<WorkCenter>().eq("work_center_name", name).last("LIMIT 1"));
        if (exist != null) return Result.success(exist);

        WorkCenter wc = new WorkCenter();
        wc.setWorkCenterName(name);
        wc.setWorkCenterCode(nextWorkCenterCode());
        wc.setWorkCenterType("工作单元");
        wc.setStatus("启用");
        wc.setIsActive((short) 1);
        wc.setIsOutsourced((short) 0);
        workCenterService.save(wc);
        return Result.success(wc);
    }

    /** 自动生成工作中心编码：WC-yyyyMMdd-#### */
    private String nextWorkCenterCode() {
        String prefix = "WC-" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        List<WorkCenter> rows = workCenterService.list(
                new QueryWrapper<WorkCenter>().select("work_center_code").likeRight("work_center_code", prefix));
        int seq = 0;
        for (WorkCenter w : rows) {
            String code = w.getWorkCenterCode();
            if (code == null || code.length() <= prefix.length()) continue;
            try {
                seq = Math.max(seq, Integer.parseInt(code.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // 异形编码不参与流水判定
            }
        }
        return prefix + String.format("%04d", seq + 1);
    }

    @GetMapping("/{id}")
    public Result<WorkCenter> get(@PathVariable Long id) {
        return Result.success(workCenterService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody WorkCenter entity) {
        if (entity.getWorkCenterCode() != null && workCenterService.existsByCode(entity.getWorkCenterCode())) {
            return Result.error(409, "编码已存在");
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) entity.setStatus("启用");
        if (entity.getIsActive() == null) entity.setIsActive((short) 1);
        if (entity.getIsOutsourced() == null) entity.setIsOutsourced((short) 0);
        try {
            workCenterService.validateOutsourced(entity);
        } catch (BusinessException e) {
            return Result.error(400, e.getMessage());
        }
        workCenterService.save(entity);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WorkCenter entity) {
        entity.setWorkCenterId(id);
        if (entity.getWorkCenterCode() != null
                && workCenterService.existsByCodeExcludeId(entity.getWorkCenterCode(), id)) {
            return Result.error(409, "编码已存在");
        }
        try {
            workCenterService.validateOutsourced(entity);
        } catch (BusinessException e) {
            return Result.error(400, e.getMessage());
        }
        workCenterService.updateById(entity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workCenterService.removeById(id);
        return Result.success();
    }

    /** 启用: 停用 → 启用 */
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id,
                               @RequestHeader(value = "X-Operator", required = false) String operator) {
        workCenterService.enable(id, operator);
        return Result.success();
    }

    /** 停用: 启用 → 停用 */
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        workCenterService.disable(id, operator);
        return Result.success();
    }
}
