package com.erp.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.WorkCenter;
import com.erp.production.service.WorkCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(workCenterService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<WorkCenter>> list(@RequestParam(required = false) String status) {
        QueryWrapper<WorkCenter> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc("work_center_code");
        return Result.success(workCenterService.list(qw));
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
        workCenterService.updateById(entity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workCenterService.removeById(id);
        return Result.success();
    }
}
