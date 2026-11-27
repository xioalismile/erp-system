package com.erp.masterdata.controller;

import com.erp.common.Result;
import com.erp.masterdata.entity.MaterialCodeRule;
import com.erp.masterdata.service.MaterialCodeRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料编码规则控制器
 * 路径前缀: /api/basedata/material-code-rule
 *
 * 提供编码规则的 CRUD 与编码自动生成接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/basedata/material-code-rule")
@RequiredArgsConstructor
public class MaterialCodeRuleController {

    private final MaterialCodeRuleService materialCodeRuleService;

    /** 查询全部规则 */
    @GetMapping("/list")
    public Result<List<MaterialCodeRule>> list() {
        return Result.success(materialCodeRuleService.listAll());
    }

    /** 保存规则（新增或编辑） */
    @PostMapping
    public Result<MaterialCodeRule> save(@RequestBody MaterialCodeRule rule) {
        return Result.success(materialCodeRuleService.saveEntity(rule));
    }

    /** 删除规则 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialCodeRuleService.delete(id);
        return Result.success();
    }

    /**
     * 根据物料分类生成物料编码
     * 优先使用分类专属规则，没有则使用通用规则
     */
    @GetMapping("/generate-code")
    public Result<String> generateCode(@RequestParam(required = false) Long categoryId) {
        return Result.success(materialCodeRuleService.generateCode(categoryId));
    }
}
