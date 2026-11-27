package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.inventory.warning.entity.InventoryWarningRule;
import com.erp.inventory.warning.service.InventoryWarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增/修改库存预警规则（设置中心 → 库存预警），走强制确认。
 * id 为空走 saveRule（新增），否则走 updateRule（只覆盖传入字段）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingInvWarningSaveTool implements AiTool {

    private final InventoryWarningService inventoryWarningService;

    @Override
    public String getName() {
        return "setting_inv_warning_save";
    }

    @Override
    public String getDescription() {
        return "新增或修改库存预警规则（执行前会弹确认卡片）。不传 id 为新增，传 id 为修改（只改传入字段）。"
                + "新增必填：ruleName(规则名称)。可选：id、materialCode(物料编码)、materialName(物料名称)、"
                + "warehouseName(仓库名称)、warningType(预警类型，如 low_stock/high_stock)、"
                + "minQuantity(下限数量)、maxQuantity(上限数量)、notifyType(通知方式，整数)、"
                + "status(1启用/0停用)、remark。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "规则ID，修改时必传；新增不传"));
        props.put("ruleName", Map.of("type", "string", "description", "规则名称，新增必填"));
        props.put("materialCode", Map.of("type", "string", "description", "物料编码"));
        props.put("materialName", Map.of("type", "string", "description", "物料名称"));
        props.put("warehouseName", Map.of("type", "string", "description", "仓库名称"));
        props.put("warningType", Map.of("type", "string", "description", "预警类型，如 low_stock/high_stock"));
        props.put("minQuantity", Map.of("type", "number", "description", "下限数量"));
        props.put("maxQuantity", Map.of("type", "number", "description", "上限数量"));
        props.put("notifyType", Map.of("type", "integer", "description", "通知方式"));
        props.put("status", Map.of("type", "integer", "description", "状态：1启用/0停用"));
        props.put("remark", Map.of("type", "string", "description", "备注"));
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
            Long id = SettingSupport.longVal(p.get("id"));
            String ruleName = SettingSupport.str(p.get("ruleName"));
            if (id == null && ruleName == null) {
                return Map.of("success", false, "message", "新增库存预警规则需要 ruleName");
            }

            InventoryWarningRule rule = new InventoryWarningRule();
            rule.setId(id);
            if (p.containsKey("ruleName")) rule.setRuleName(ruleName);
            if (p.containsKey("materialCode")) rule.setMaterialCode(SettingSupport.str(p.get("materialCode")));
            if (p.containsKey("materialName")) rule.setMaterialName(SettingSupport.str(p.get("materialName")));
            if (p.containsKey("warehouseName")) rule.setWarehouseName(SettingSupport.str(p.get("warehouseName")));
            if (p.containsKey("warningType")) rule.setWarningType(SettingSupport.str(p.get("warningType")));
            if (p.containsKey("minQuantity")) rule.setMinQuantity(SettingSupport.decimal(p.get("minQuantity")));
            if (p.containsKey("maxQuantity")) rule.setMaxQuantity(SettingSupport.decimal(p.get("maxQuantity")));
            if (p.containsKey("notifyType")) rule.setNotifyType(SettingSupport.integer(p.get("notifyType")));
            if (p.containsKey("status")) rule.setStatus(SettingSupport.integer(p.get("status")));
            if (p.containsKey("remark")) rule.setRemark(SettingSupport.str(p.get("remark")));

            boolean isNew = id == null;
            if (isNew) {
                inventoryWarningService.saveRule(rule);
            } else {
                inventoryWarningService.updateRule(rule);
            }
            log.info("[AI设置] 用户{} {}库存预警规则 name={} id={}",
                    scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                    rule.getRuleName(), rule.getId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", (isNew ? "已新增" : "已修改") + "库存预警规则「"
                    + (rule.getRuleName() != null ? rule.getRuleName() : rule.getId()) + "」。");
            if (rule.getId() != null) result.put("id", rule.getId());
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 保存库存预警规则失败", e);
            return Map.of("success", false, "message", "保存库存预警规则失败: " + e.getMessage());
        }
    }
}
