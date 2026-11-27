package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.common.PageResult;
import com.erp.inventory.warning.entity.InventoryWarningRule;
import com.erp.inventory.warning.service.InventoryWarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询库存预警规则列表（设置中心 → 库存预警）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingInvWarningListTool implements AiTool {

    private final InventoryWarningService inventoryWarningService;

    @Override
    public String getName() {
        return "setting_inv_warning_list";
    }

    @Override
    public String getDescription() {
        return "查询库存预警规则列表（分页，只读）。适用于：查看某物料/仓库的上下限预警配置、启用状态。"
                + "参数：keyword(可选，按规则名/物料编码/物料名称过滤)、status(可选，1启用/0停用)、"
                + "page(默认1)、size(默认20)。修改请用 setting_inv_warning_save。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "可选，按规则名/物料编码/物料名称模糊过滤"));
        props.put("status", Map.of("type", "integer", "description", "可选，状态：1启用/0停用"));
        props.put("page", Map.of("type", "integer", "description", "页码，默认1"));
        props.put("size", Map.of("type", "integer", "description", "每页条数，默认20"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Map<String, Object> p = params == null ? Map.of() : params;
            String keyword = SettingSupport.str(p.get("keyword"));
            Integer status = SettingSupport.integer(p.get("status"));
            Integer page = SettingSupport.integer(p.get("page"));
            Integer size = SettingSupport.integer(p.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;

            PageResult<InventoryWarningRule> pr = inventoryWarningService.pageQuery(
                    current, pageSize, keyword, status, null, null);
            List<Map<String, Object>> items = new ArrayList<>();
            if (pr != null && pr.getRecords() != null) {
                for (InventoryWarningRule r : pr.getRecords()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.getId());
                    item.put("ruleName", r.getRuleName());
                    item.put("materialId", r.getMaterialId());
                    item.put("materialCode", r.getMaterialCode());
                    item.put("materialName", r.getMaterialName());
                    item.put("warehouseId", r.getWarehouseId());
                    item.put("warehouseName", r.getWarehouseName());
                    item.put("warningType", r.getWarningType());
                    item.put("minQuantity", r.getMinQuantity());
                    item.put("maxQuantity", r.getMaxQuantity());
                    item.put("notifyType", r.getNotifyType());
                    item.put("notifyUsers", r.getNotifyUsers());
                    item.put("status", r.getStatus());
                    item.put("remark", r.getRemark());
                    items.add(item);
                }
            }
            long total = pr == null ? 0 : pr.getTotal();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", total);
            result.put("items", items);
            result.put("message", "共查到 " + total + " 条库存预警规则（第 " + current + " 页）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询库存预警规则失败", e);
            return Map.of("success", false, "message", "查询库存预警规则失败: " + e.getMessage());
        }
    }
}
