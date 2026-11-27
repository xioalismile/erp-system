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
 * 写工具：启用/停用库存预警规则（设置中心 → 库存预警），走强制确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingInvWarningToggleTool implements AiTool {

    private final InventoryWarningService inventoryWarningService;

    @Override
    public String getName() {
        return "setting_inv_warning_toggle";
    }

    @Override
    public String getDescription() {
        return "切换某条库存预警规则的启用/停用状态（执行前会弹确认卡片）。"
                + "必填参数：id(规则ID，先通过 setting_inv_warning_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "规则ID，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("id"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Long id = SettingSupport.longVal(params == null ? null : params.get("id"));
            if (id == null) {
                return Map.of("success", false, "message", "参数缺失：需要 id");
            }
            InventoryWarningRule rule = inventoryWarningService.getDetail(id);
            if (rule == null) {
                return Map.of("success", false, "message", "库存预警规则不存在: " + id);
            }
            inventoryWarningService.toggleStatus(id);
            log.info("[AI设置] 用户{}切换库存预警规则状态 id={} name={}",
                    scope != null ? scope.getUsername() : "?", id, rule.getRuleName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已切换库存预警规则「" + rule.getRuleName() + "」的启用状态。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 切换库存预警规则状态失败", e);
            return Map.of("success", false, "message", "切换库存预警规则状态失败: " + e.getMessage());
        }
    }
}
