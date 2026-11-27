package com.erp.finance.controller;

import com.erp.common.Result;
import com.erp.finance.entity.CostAccountingConfig;
import com.erp.finance.service.CostAccountingConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成本核算配置控制器
 * 路径前缀: /api/finance/cost-config
 *
 * defaultMethod 取值: moving_avg / weighted_avg / fixed
 */
@Slf4j
@RestController
@RequestMapping("/api/finance/cost-config")
@RequiredArgsConstructor
public class CostAccountingConfigController {

    private final CostAccountingConfigService costAccountingConfigService;

    /** 查询全部配置 */
    @GetMapping("/list")
    public Result<List<CostAccountingConfig>> list() {
        return Result.success(costAccountingConfigService.listAll());
    }

    /** 保存配置（新增或编辑） */
    @PostMapping
    public Result<CostAccountingConfig> save(@RequestBody CostAccountingConfig config) {
        return Result.success(costAccountingConfigService.saveEntity(config));
    }

    /** 删除配置 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        costAccountingConfigService.delete(id);
        return Result.success();
    }

    /** 获取当前启用的配置 */
    @GetMapping("/active")
    public Result<CostAccountingConfig> getActive() {
        return Result.success(costAccountingConfigService.getActiveConfig());
    }
}
