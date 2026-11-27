package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.inventory.entity.Inventory;
import com.erp.basedata.entity.Warehouse;
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

    /**
     * 查库存。
     * <p>
     * 2026-09-29 修复三处（用户实测："问两条物料的库存，却返回全部 28 条且顺序看不懂"）：
     * ① 原实现**完全忽略 materialName**（只按编码过滤），传了名称等于没传 → 返回全量；
     * ② 无任何过滤且无排序 → 结果毫无业务顺序；
     * ③ 编码查不到时静默返回全量（"查不到"与"没有库存"混为一谈）。
     * 现在：编码与名称都支持**多值**（逗号/顿号/空格分隔，OR 关系），编码查不到会如实回报；
     * 排序固定为「物料编码 → 库存主键」；无任何条件时明确提示"未指定物料"。
     */
    public Map<String, Object> queryInventory(Map<String, Object> params) {
        List<String> codes = splitTokens(str(params.get("materialCode")));
        List<String> names = splitTokens(str(params.get("materialName")));

        // 物料编码 → 物料主键（精确匹配；保密物料视为查不到，不暴露其存在）
        List<Long> materialIds = new ArrayList<>();
        List<String> unknownCodes = new ArrayList<>();
        for (String code : codes) {
            Material m = materialService.getOne(new QueryWrapper<Material>()
                    .eq("material_code", code)
                    .ne("is_confidential", 1));
            if (m != null && m.getMaterialId() != null) {
                materialIds.add(m.getMaterialId());
            } else {
                unknownCodes.add(code);
            }
        }

        // 传了编码但一条都没匹配上，且没有名称条件 → 如实回报，绝不能退化成"返回全量库存"
        if (!codes.isEmpty() && materialIds.isEmpty() && names.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", false);
            out.put("message", "未找到物料编码：" + String.join("、", unknownCodes) + "（可能不存在或属于受保护数据）");
            out.put("inventory", Collections.emptyList());
            out.put("total", 0);
            return out;
        }

        boolean hasFilter = !materialIds.isEmpty() || !names.isEmpty();
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (hasFilter) {
            // 多值 OR 必须整组包在 and(...) 内（MyBatis-Plus 平铺 or() 不给分组补括号，会吃掉后续条件）
            wrapper.and(w -> {
                boolean first = true;
                if (!materialIds.isEmpty()) {
                    w.in("material_id", materialIds);
                    first = false;
                }
                for (String n : names) {
                    if (first) {
                        w.like("material_name", n);
                        first = false;
                    } else {
                        w.or().like("material_name", n);
                    }
                }
            });
        }
        // 稳定且可读的顺序：物料编码 → 库存主键（stock_name 只是实体上的非数据库字段，不能用于排序）
        wrapper.orderByAsc("material_code").orderByAsc("inventory_id");
        wrapper.last("LIMIT 50");

        List<Inventory> list = inventoryService.list(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Inventory inv : list) {
            Material mat = inv.getMaterialId() != null
                    ? materialService.getById(inv.getMaterialId()) : null;
            if (mat != null && mat.getIsConfidential() != null && mat.getIsConfidential() == 1) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inventoryId", inv.getInventoryId());
            if (mat != null) {
                m.put("materialCode", mat.getMaterialCode());
                m.put("materialName", mat.getMaterialName());
                m.put("safetyStock", mat.getMinStock() != null ? mat.getMinStock() : BigDecimal.ZERO);
            } else {
                m.put("materialCode", inv.getMaterialCode() != null ? inv.getMaterialCode() : "");
                m.put("materialName", inv.getMaterialName() != null ? inv.getMaterialName() : "");
                m.put("safetyStock", BigDecimal.ZERO);
            }
            Warehouse wh = inv.getStockId() != null
                    ? warehouseService.getById(inv.getStockId()) : null;
            m.put("stockName", wh != null ? wh.getStockName() : "");
            BigDecimal qty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
            m.put("quantity", qty);
            BigDecimal safety = (BigDecimal) m.get("safetyStock");
            m.put("warning", qty.compareTo(safety) < 0 ? "库存不足" : "正常");
            result.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("inventory", result);
        out.put("total", result.size());
        if (!unknownCodes.isEmpty()) {
            out.put("unknownCodes", unknownCodes);
            out.put("unknownNote", "以下编码未匹配到物料（可能不存在或属于受保护数据）：" + String.join("、", unknownCodes));
        }
        if (!hasFilter) {
            out.put("hint", "未指定物料编码或名称，以下为按物料编码排序的前 50 条；要精确结果请提供物料编码或名称");
        } else if (result.isEmpty()) {
            out.put("message", "这些物料在库存表里没有任何库存记录（不是查询失败）");
        }
        return out;
    }

    /** 多值拆分：支持中英文逗号、顿号、分号、斜杠、空格分隔，去空去重 */
    private List<String> splitTokens(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null || raw.isBlank()) return tokens;
        for (String part : raw.split("[,\\uFF0C\\u3001;\\uFF1B/\\s]+")) {
            String t = part.trim();
            if (!t.isEmpty() && !tokens.contains(t)) tokens.add(t);
        }
        return tokens;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
