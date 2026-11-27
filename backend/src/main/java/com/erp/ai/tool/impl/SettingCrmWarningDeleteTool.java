package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.masterdata.mapper.CustomerPoolConfigMapper;
import com.erp.masterdata.mapper.CustomerWarningConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：删除CRM客户预警配置（设置中心 → CRM预警），走强制确认。
 * poolConfigId 与 warningConfigId 二选一。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCrmWarningDeleteTool implements AiTool {

    private final CustomerPoolConfigMapper customerPoolConfigMapper;
    private final CustomerWarningConfigMapper customerWarningConfigMapper;

    @Override
    public String getName() {
        return "setting_crm_warning_delete";
    }

    @Override
    public String getDescription() {
        return "删除CRM客户预警配置（执行前会弹确认卡片）。参数 poolConfigId 或 warningConfigId 二选一，分别删除公海池配置或跟进预警配置。"
                + "删除后不可恢复。先通过 setting_crm_warning_get 查看现有配置及其ID。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("poolConfigId", Map.of("type", "integer", "description", "公海池配置ID，可选（与 warningConfigId 二选一）"));
        props.put("warningConfigId", Map.of("type", "integer", "description", "跟进预警配置ID，可选（与 poolConfigId 二选一）"));
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
            Long poolConfigId = SettingSupport.longVal(p.get("poolConfigId"));
            Long warningConfigId = SettingSupport.longVal(p.get("warningConfigId"));
            if (poolConfigId == null && warningConfigId == null) {
                return Map.of("success", false, "message", "参数缺失：需要 poolConfigId 或 warningConfigId 之一");
            }
            if (poolConfigId != null && warningConfigId != null) {
                return Map.of("success", false, "message", "poolConfigId 与 warningConfigId 只能二选一");
            }

            if (poolConfigId != null) {
                customerPoolConfigMapper.deleteById(poolConfigId);
                log.info("[AI设置] 用户{}删除CRM公海池配置 id={}",
                        scope != null ? scope.getUsername() : "?", poolConfigId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", "已删除公海池配置（ID " + poolConfigId + "）。");
                return result;
            }

            customerWarningConfigMapper.deleteById(warningConfigId);
            log.info("[AI设置] 用户{}删除CRM跟进预警配置 id={}",
                    scope != null ? scope.getUsername() : "?", warningConfigId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已删除跟进预警配置（ID " + warningConfigId + "）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 删除CRM预警配置失败", e);
            return Map.of("success", false, "message", "删除CRM预警配置失败: " + e.getMessage());
        }
    }
}
