package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.entity.MaterialSubstitute;
import com.erp.masterdata.mapper.MaterialSubstituteMapper;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 货品替代清单维护（货品档案「替代货品」）。
 * 换货弹窗默认只列本清单；同时允许手动选择其它货品。
 */
@RestController
@RequestMapping("/api/material-substitutes")
@RequiredArgsConstructor
public class MaterialSubstituteController {

    private final MaterialSubstituteMapper mapper;
    private final MaterialService materialService;

    /** 清单（含货品展示信息），供货品档案与换货弹窗使用 */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam Long materialId) {
        List<MaterialSubstitute> rows = mapper.selectList(new QueryWrapper<MaterialSubstitute>()
                .eq("material_id", materialId).orderByAsc("sort_order").orderByAsc("id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (MaterialSubstitute r : rows) {
            Material m = materialService.getById(r.getSubstituteMaterialId());
            if (m == null) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", r.getId());
            c.put("materialId", r.getMaterialId());
            c.put("substituteMaterialId", r.getSubstituteMaterialId());
            c.put("materialCode", m.getMaterialCode());
            c.put("materialName", m.getMaterialName());
            c.put("specification", m.getModel());
            c.put("baseUnitId", m.getBaseUnitId());
            c.put("mainImage", m.getMainImage());
            c.put("sortOrder", r.getSortOrder());
            out.add(c);
        }
        return Result.success(out);
    }

    /** 整单保存：全删全插，排除自身与重复 */
    @PostMapping("/batch-save")
    @Transactional
    public Result<Void> batchSave(@RequestBody Map<String, Object> body) {
        Long materialId = body.get("materialId") == null ? null : Long.valueOf(String.valueOf(body.get("materialId")));
        if (materialId == null) throw new BusinessException("缺少货品ID");
        Object raw = body.get("substituteMaterialIds");
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                Long id = Long.valueOf(String.valueOf(o));
                if (id.equals(materialId)) throw new BusinessException("替代货品不能是货品自身");
                if (!ids.contains(id)) ids.add(id);
            }
        }
        // 基本单位一致性：不一致直接拒绝（换货无法反写原订单行）
        Material origin = materialService.getById(materialId);
        if (origin == null) throw new BusinessException("货品不存在");
        for (Long id : ids) {
            Material sub = materialService.getById(id);
            if (sub == null) throw new BusinessException("替代货品不存在: " + id);
            if (origin.getBaseUnitId() != null && sub.getBaseUnitId() != null
                    && !origin.getBaseUnitId().equals(sub.getBaseUnitId())) {
                throw new BusinessException("替代货品「" + sub.getMaterialName() + "」与原货品基本单位不一致");
            }
        }
        mapper.delete(new QueryWrapper<MaterialSubstitute>().eq("material_id", materialId));
        int sort = 0;
        for (Long id : ids) {
            MaterialSubstitute r = new MaterialSubstitute();
            r.setMaterialId(materialId);
            r.setSubstituteMaterialId(id);
            r.setSortOrder(sort++);
            mapper.insert(r);
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.success();
    }
}
