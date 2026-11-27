package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.serialno.entity.SysSerialNoConfig;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：删除自动编号配置（设置中心 → 自动编号设置），走强制确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingSerialNoDeleteTool implements AiTool {

    private final SysSerialNoConfigService sysSerialNoConfigService;

    @Override
    public String getName() {
        return "setting_serial_no_delete";
    }

    @Override
    public String getDescription() {
        return "删除某条自动编号配置（执行前会弹确认卡片）。删除后该单据类型将不再按此规则生成编号，请谨慎操作。"
                + "必填参数：id(配置ID，先通过 setting_serial_no_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "配置ID，必填"));
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
            SysSerialNoConfig cfg = sysSerialNoConfigService.getById(id);
            if (cfg == null) {
                return Map.of("success", false, "message", "自动编号配置不存在: " + id);
            }
            boolean ok = sysSerialNoConfigService.deleteConfig(id);
            if (!ok) {
                return Map.of("success", false, "message", "删除自动编号配置失败: " + id);
            }
            log.info("[AI设置] 用户{}删除自动编号配置 id={} docType={}",
                    scope != null ? scope.getUsername() : "?", id, cfg.getDocType());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已删除【" + (cfg.getDocName() != null ? cfg.getDocName() : cfg.getDocType())
                    + "】的自动编号配置。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 删除自动编号配置失败", e);
            return Map.of("success", false, "message", "删除自动编号配置失败: " + e.getMessage());
        }
    }
}
