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
 * 写工具：修改自动编号配置（设置中心 → 自动编号设置），走强制确认。
 * 只覆盖本次传入的字段，未传字段保持原值；docType 不允许修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingSerialNoUpdateTool implements AiTool {

    private final SysSerialNoConfigService sysSerialNoConfigService;

    @Override
    public String getName() {
        return "setting_serial_no_update";
    }

    @Override
    public String getDescription() {
        return "修改某条自动编号配置（执行前会弹确认卡片）。只改传入的字段，未传的字段保持原值；单据类型docType不可修改。"
                + "必填参数：id(配置ID)。可选参数：prefix(前缀)、dateFormat(日期格式，如 yyyyMMdd)、"
                + "serialLength(流水位数)、separator(分隔符)、resetType(重置类型)、enabled(是否启用)、"
                + "autoGenerate(是否自动生成编号)、remark(备注)。改前建议先用 setting_serial_no_list 核对。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "配置ID，必填（来自 setting_serial_no_list）"));
        props.put("prefix", Map.of("type", "string", "description", "编号前缀，如 SO-"));
        props.put("dateFormat", Map.of("type", "string", "description", "日期格式，如 yyyyMMdd，可空"));
        props.put("serialLength", Map.of("type", "integer", "description", "流水号位数，如 4"));
        props.put("separator", Map.of("type", "string", "description", "分隔符，如 -"));
        props.put("resetType", Map.of("type", "string", "description", "重置类型，如 none/daily/monthly/yearly"));
        props.put("enabled", Map.of("type", "boolean", "description", "是否启用该编号规则"));
        props.put("autoGenerate", Map.of("type", "boolean", "description", "是否自动生成编号（false=手工录入）"));
        props.put("remark", Map.of("type", "string", "description", "备注，可空"));
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

            if (params.containsKey("prefix")) cfg.setPrefix(SettingSupport.str(params.get("prefix")));
            if (params.containsKey("dateFormat")) cfg.setDateFormat(SettingSupport.str(params.get("dateFormat")));
            if (params.containsKey("separator")) cfg.setSeparator(SettingSupport.str(params.get("separator")));
            if (params.containsKey("resetType")) cfg.setResetType(SettingSupport.str(params.get("resetType")));
            if (params.containsKey("remark")) cfg.setRemark(SettingSupport.str(params.get("remark")));
            if (params.containsKey("serialLength")) {
                Integer serialLength = SettingSupport.integer(params.get("serialLength"));
                if (serialLength != null) cfg.setSerialLength(serialLength);
            }
            if (params.containsKey("enabled")) {
                cfg.setEnabled(SettingSupport.bool(params.get("enabled"), true) ? 1 : 0);
            }
            if (params.containsKey("autoGenerate")) {
                cfg.setAutoGenerate(SettingSupport.bool(params.get("autoGenerate"), true) ? 1 : 0);
            }

            boolean ok = sysSerialNoConfigService.updateConfig(cfg);
            if (!ok) {
                return Map.of("success", false, "message", "保存自动编号配置失败: " + id);
            }
            log.info("[AI设置] 用户{}修改自动编号配置 id={} docType={}",
                    scope != null ? scope.getUsername() : "?", id, cfg.getDocType());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已更新【" + (cfg.getDocName() != null ? cfg.getDocName() : cfg.getDocType())
                    + "】的自动编号配置。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 修改自动编号配置失败", e);
            return Map.of("success", false, "message", "修改自动编号配置失败: " + e.getMessage());
        }
    }
}
