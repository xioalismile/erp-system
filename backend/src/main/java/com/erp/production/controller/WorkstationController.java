package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.Workstation;
import com.erp.production.service.WorkstationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工序 Controller
 * 路由: /api/production/workstation
 */
@RestController
@RequestMapping("/api/production/workstation")
@RequiredArgsConstructor
public class WorkstationController {

    private final WorkstationService workstationService;

    @GetMapping("/page")
    public Result<PageResult<Workstation>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long workCenterId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 分页参数统一解析（current 优先、旧参数名 page 兜底；size 上限 500）—— ListQuerySupport 统一件
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        return Result.success(workstationService.pageQuery(p[0], p[1], keyword, status, workCenterId, sortBy, sortOrder));
    }

    /** 仪表盘统计（基础资料类：与 /page 一致不做数据权限过滤） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", workstationService.count(new QueryWrapper<Workstation>()));
        stats.put("enabledCount", workstationService.count(new QueryWrapper<Workstation>().eq("status", "启用")));
        stats.put("disabledCount", workstationService.count(new QueryWrapper<Workstation>().eq("status", "停用")));
        // 「工序类型」卡：本表实际出现的工序类型去重数量（原页面写死 5，语义不明）
        List<Workstation> typeRows = workstationService.list(
                new QueryWrapper<Workstation>().select("DISTINCT workstation_type"));
        long typeCount = typeRows.stream()
                // MyBatis 对「所选列全为 NULL」的行会映射成 null 元素（实测 workstation_type 为空时 NPE）
                .filter(java.util.Objects::nonNull)
                .map(Workstation::getWorkstationType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .count();
        stats.put("typeCount", typeCount);
        return Result.success(stats);
    }

    @GetMapping("/list")
    public Result<List<Workstation>> list(@RequestParam(required = false) String status) {
        QueryWrapper<Workstation> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc("workstation_code");
        return Result.success(workstationService.list(qw));
    }

    /**
     * 【即输即存】工序（工位）按名称"找不到就建、找到就复用"。
     *
     * <p>用户口径（2026-09-23）：工序明细里的「工位」要能直接输入回车保存，下次还能选到，
     * 不必先跳到配置页建档案。所以这里按<b>名称</b>去重：同名视为同一条，编码自动生成。
     *
     * <p>幂等：并发下先查后插，靠名称唯一性收敛（重复提交只会复用同一条）。
     */
    @PostMapping("/quick-save")
    public Result<Workstation> quickSave(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) return Result.error(400, "工序名称不能为空");
        Workstation exist = workstationService.getOne(
                new QueryWrapper<Workstation>().eq("workstation_name", name).last("LIMIT 1"));
        if (exist != null) return Result.success(exist);

        Workstation ws = new Workstation();
        ws.setWorkstationName(name);
        ws.setWorkstationCode(nextWorkstationCode());
        if (body.get("workCenterId") != null) {
            ws.setWorkCenterId(Long.valueOf(String.valueOf(body.get("workCenterId"))));
        }
        if (body.get("workstationType") != null) {
            ws.setWorkstationType(String.valueOf(body.get("workstationType")));
        }
        ws.setStatus("启用");
        ws.setIsActive((short) 1);
        workstationService.save(ws);
        return Result.success(ws);
    }

    /** 自动生成工序（工位）编码：WS-yyyyMMdd-#### */
    private String nextWorkstationCode() {
        String prefix = "WS-" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        List<Workstation> rows = workstationService.list(
                new QueryWrapper<Workstation>().select("workstation_code").likeRight("workstation_code", prefix));
        int seq = 0;
        for (Workstation w : rows) {
            String code = w.getWorkstationCode();
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
    public Result<Workstation> get(@PathVariable Long id) {
        return Result.success(workstationService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Workstation entity) {
        if (entity.getWorkstationCode() != null && workstationService.existsByCode(entity.getWorkstationCode())) {
            return Result.error(409, "编码已存在");
        }
        workstationService.save(entity);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Workstation entity) {
        entity.setWorkstationId(id);
        if (entity.getWorkstationCode() != null
                && workstationService.existsByCodeExcludeId(entity.getWorkstationCode(), id)) {
            return Result.error(409, "编码已存在");
        }
        workstationService.updateById(entity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workstationService.removeById(id);
        return Result.success();
    }
}
