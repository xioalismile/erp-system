package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.Warehouse;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.WarehouseService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * AI库存查询服务 - 对齐 inventory_current 表结构
 */
@Slf4j
@Service("aiInventoryService")
@RequiredArgsConstructor
public class AiInventoryService {

    private final InventoryService inventoryService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;

    public Map<String, Object> queryInventory(Map<String, Object> params) {
        String materialCode = (String) params.get("materialCode");
        String materialName = (String) params.get("materialName");

        // 先按 materialCode 查 Material, 拿 materialId
        Long materialId = null;
        if (materialCode != null && !materialCode.isBlank()) {
            Material m = materialService.getOne(
                    new QueryWrapper<Material>().eq("material_code", materialCode));
            materialId = m != null ? m.getMaterialId() : null;
        }

        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (materialId != null) wrapper.eq("material_id", materialId);
        wrapper.last("LIMIT 20");

        List<Inventory> list = inventoryService.list(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Inventory inv : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            // 通过 materialId 查 Material 拿编码/名称/安全库存
            Material mat = inv.getMaterialId() != null
                    ? materialService.getById(inv.getMaterialId()) : null;
            if (mat != null) {
                m.put("materialCode", mat.getMaterialCode());
                m.put("materialName", mat.getMaterialName());
                m.put("safetyStock", mat.getMinStock() != null ? mat.getMinStock() : BigDecimal.ZERO);
            } else {
                m.put("materialCode", "");
                m.put("materialName", "");
                m.put("safetyStock", BigDecimal.ZERO);
            }
            // 通过 stockId 查 Warehouse 拿仓库名称
            Warehouse wh = inv.getStockId() != null
                    ? warehouseService.getById(inv.getStockId()) : null;
            m.put("warehouse", wh != null ? wh.getStockName() : "");
            m.put("quantity", inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO);
            BigDecimal qty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
            BigDecimal safety = (BigDecimal) m.get("safetyStock");
            m.put("warning", qty.compareTo(safety) < 0 ? "库存不足" : "正常");
            result.add(m);
        }
        return Map.of("inventory", result, "total", result.size());
    }
}
