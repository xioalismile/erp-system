package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI库存查询服务
 */
@Slf4j
@Service("aiInventoryService")
@RequiredArgsConstructor
public class AiInventoryService {

    private final InventoryService inventoryService;

    public Map<String, Object> queryInventory(Map<String, Object> params) {
        String materialCode = (String) params.get("materialCode");
        String materialName = (String) params.get("materialName");

        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (materialCode != null) wrapper.eq("material_code", materialCode);
        if (materialName != null) wrapper.like("material_name", materialName);
        wrapper.last("LIMIT 20");

        List<Inventory> list = inventoryService.list(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Inventory inv : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialCode", inv.getMaterialCode());
            m.put("materialName", inv.getMaterialName());
            m.put("warehouse", inv.getWarehouse());
            m.put("quantity", inv.getQuantity());
            m.put("safetyStock", inv.getSafetyStock());
            m.put("warning", inv.getQuantity().compareTo(inv.getSafetyStock()) < 0 ? "库存不足" : "正常");
            result.add(m);
        }
        return Map.of("inventory", result, "total", result.size());
    }
}
