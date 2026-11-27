package com.erp.basedata.controller;

import com.erp.basedata.entity.MaterialUnitConversion;
import com.erp.basedata.service.MaterialUnitConversionService;
import com.erp.common.Result;
import com.erp.config.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物料辅计量换算 Controller（2026-11-10 多单位换算重构第一阶段）。
 *
 * <p>路由挂在物料档案路径之下（/api/basedata/material/...），
 * 鉴权口径与物料编辑完全同源，沿用物料编辑的权限码，不新增独立菜单权限。</p>
 */
@RestController
@RequestMapping("/api/basedata/material")
@RequiredArgsConstructor
public class MaterialUnitConversionController {

    private final MaterialUnitConversionService materialUnitConversionService;

    /** 按物料查换算明细列表（物料档案编辑页「辅计量换算」页签回显） */
    @GetMapping("/{materialId}/unit-conversions")
    public Result<List<MaterialUnitConversion>> listByMaterial(@PathVariable Long materialId) {
        return Result.success(materialUnitConversionService.listByMaterialId(materialId));
    }

    /**
     * 按多个物料批量查换算明细（单据编辑页整单一次性取回，避免逐物料请求）。
     *
     * <p>ids 逗号分隔的物料ID；返回按物料ID分组的行列表（无配置的物料也返回空列表条目）。
     * 鉴权与物料编辑同源（挂在物料档案路径下）。</p>
     */
    @GetMapping("/unit-conversions-batch")
    public Result<Map<Long, List<MaterialUnitConversion>>> listBatch(@RequestParam("ids") String ids) {
        Set<Long> materialIds = new LinkedHashSet<>();
        if (ids != null && !ids.isBlank()) {
            for (String part : ids.split(",")) {
                String s = part.trim();
                if (s.isEmpty()) continue;
                try {
                    materialIds.add(Long.parseLong(s));
                } catch (NumberFormatException e) {
                    throw new BusinessException("物料ID格式不正确：" + s);
                }
            }
        }
        if (materialIds.isEmpty()) {
            return Result.success(Map.of());
        }
        return Result.success(materialUnitConversionService.listByMaterialIds(new ArrayList<>(materialIds)));
    }

    /** 按物料整表保存换算明细（事务内先删后插；行单位去重、换算率>0 校验） */
    @PostMapping("/{materialId}/unit-conversions")
    public Result<Void> saveByMaterial(@PathVariable Long materialId,
                                       @RequestBody List<MaterialUnitConversion> rows) {
        materialUnitConversionService.replaceForMaterial(materialId, rows);
        return Result.success();
    }
}
