package com.erp.qc.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.service.QcTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 质检模板控制器（基础资料 → 质检模板）。
 * 路由: /api/qc/templates
 *
 * <p>模板 = 模板头（名称/适用物料/适用工序/质检类型）+ 检验项列表；按物料或工序绑定，默认不绑定。
 * 本控制器只做模板主档 CRUD；工序检验提交走 {@code /api/production/work-order-operation/{id}/qc}。</p>
 */
@RestController
@RequestMapping("/api/qc/templates")
@RequiredArgsConstructor
public class QcTemplateController {

    private final QcTemplateService qcTemplateService;

    /** 分页查询（keyword 匹配编码/名称/适用工序；qcType 质检类型；isEnabled 启停） */
    @GetMapping
    public Result<PageResult<QcTemplate>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String qcType,
                                               @RequestParam(required = false) Integer isEnabled) {
        return Result.success(qcTemplateService.pageQuery(current, size, keyword, qcType, isEnabled));
    }

    /** 启用中的模板全量列表（工单编辑页「质检模板」下拉、工序面板选择用） */
    @GetMapping("/list")
    public Result<List<QcTemplate>> list() {
        return Result.success(qcTemplateService.listEnabled());
    }

    /** 详情（含检验项明细 items） */
    @GetMapping("/{id}")
    public Result<QcTemplate> detail(@PathVariable Long id) {
        return Result.success(qcTemplateService.getDetail(id));
    }

    /** 新增模板 */
    @PostMapping
    public Result<QcTemplate> create(@RequestBody QcTemplate template) {
        Long id = qcTemplateService.createTemplate(template);
        return Result.success(qcTemplateService.getDetail(id));
    }

    /** 编辑模板（检验项整单覆盖） */
    @PutMapping("/{id}")
    public Result<QcTemplate> update(@PathVariable Long id, @RequestBody QcTemplate template) {
        qcTemplateService.updateTemplate(id, template);
        return Result.success(qcTemplateService.getDetail(id));
    }

    /** 删除模板（级联删除检验项明细） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qcTemplateService.deleteTemplate(id);
        return Result.success();
    }
}
