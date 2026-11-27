package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.MaterialUnitConversion;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.mapper.MaterialUnitConversionMapper;
import com.erp.config.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaterialUnitConversionServiceImpl
        extends ServiceImpl<MaterialUnitConversionMapper, MaterialUnitConversion>
        implements MaterialUnitConversionService {

    private final UnitOfMeasureService unitOfMeasureService;

    @Override
    public List<MaterialUnitConversion> listByMaterialId(Long materialId) {
        if (materialId == null) return new ArrayList<>();
        return list(new QueryWrapper<MaterialUnitConversion>()
                .eq("material_id", materialId)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    @Override
    public Map<Long, List<MaterialUnitConversion>> listByMaterialIds(List<Long> materialIds) {
        Map<Long, List<MaterialUnitConversion>> grouped = new LinkedHashMap<>();
        if (materialIds == null || materialIds.isEmpty()) return grouped;
        List<MaterialUnitConversion> rows = list(new QueryWrapper<MaterialUnitConversion>()
                .in("material_id", materialIds)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        for (MaterialUnitConversion row : rows) {
            grouped.computeIfAbsent(row.getMaterialId(), k -> new ArrayList<>()).add(row);
        }
        // 无配置的物料也返回空列表条目：前端按"响应里有没有该物料"判缓存命中，缺条目会反复重复请求
        for (Long id : materialIds) {
            grouped.computeIfAbsent(id, k -> new ArrayList<>());
        }
        return grouped;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceForMaterial(Long materialId, List<MaterialUnitConversion> rows) {
        if (materialId == null || rows == null) return;

        // === 保存前校验（中文报错）：单位必选 + 同一物料单位去重 + 换算率必须大于 0 ===
        Set<Long> seenUnits = new HashSet<>();
        int index = 0;
        for (MaterialUnitConversion row : rows) {
            index++;
            if (row.getUnitId() == null) {
                throw new BusinessException("辅计量换算第" + index + "行：请选择辅计量单位");
            }
            if (!seenUnits.add(row.getUnitId())) {
                UnitOfMeasure unit = unitOfMeasureService.getById(row.getUnitId());
                String unitName = unit != null && unit.getUnitName() != null ? unit.getUnitName() : String.valueOf(row.getUnitId());
                throw new BusinessException("辅计量换算的辅计量单位重复：" + unitName);
            }
            if (row.getConversionRate() != null && row.getConversionRate().signum() < 0) {
                throw new BusinessException("辅计量换算第" + index + "行：换算率不能小于0");
            }
            String mode = row.getRateMode();
            row.setRateMode("浮动".equals(mode) ? "浮动" : "固定");
            // 浮动行的率本来就不固定（2026-11-11 用户口径：允许留空或 0）；固定行仍必须大于 0
            boolean floating = "浮动".equals(row.getRateMode());
            if (!floating && (row.getConversionRate() == null || row.getConversionRate().signum() <= 0)) {
                throw new BusinessException("辅计量换算第" + index + "行：固定换算方式的换算率必须大于0");
            }
        }

        // === 事务内整表删插 ===
        remove(new QueryWrapper<MaterialUnitConversion>().eq("material_id", materialId));
        int sort = 0;
        for (MaterialUnitConversion row : rows) {
            row.setId(null);
            row.setMaterialId(materialId);
            row.setSortOrder(++sort);
        }
        if (!rows.isEmpty()) {
            saveBatch(rows);
        }
    }
}
