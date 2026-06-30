package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.Workstation;
import com.erp.production.service.WorkstationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long workCenterId) {
        return Result.success(workstationService.pageQuery(current, size, keyword, status, workCenterId));
    }

    @GetMapping("/list")
    public Result<List<Workstation>> list(@RequestParam(required = false) String status) {
        QueryWrapper<Workstation> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc("workstation_code");
        return Result.success(workstationService.list(qw));
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
