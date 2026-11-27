package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.masterdata.entity.CustomerPoolConfig;
import com.erp.masterdata.entity.CustomerWarningConfig;
import com.erp.masterdata.mapper.CustomerPoolConfigMapper;
import com.erp.masterdata.mapper.CustomerWarningConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增/修改CRM客户预警配置（设置中心 → CRM预警），走强制确认。
 * 支持公海池配置(poolConfig)与跟进预警配置(warningConfig)，分别按主键判断新增/修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCrmWarningSaveTool implements AiTool {

    private final CustomerPoolConfigMapper customerPoolConfigMapper;
    private final CustomerWarningConfigMapper customerWarningConfigMapper;
    /** 自建 ObjectMapper 必须注册 JavaTimeModule（历史坑：AI 链路 LocalDate 序列化崩溃） */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String getName() {
        return "setting_crm_warning_save";
    }

    @Override
    public String getDescription() {
        return "新增或修改CRM客户预警配置（执行前会弹确认卡片）。至少提供 poolConfig 或 warningConfig 之一。"
                + "poolConfig(公海池配置，对象)：configId(有值=修改，无=新增)、configName、noFollowDays(未跟进天数)、"
                + "noOrderDays(未开单天数)、warningDays(预警天数)、isEnabled(0/1)、remark。"
                + "warningConfig(跟进预警配置，对象)：warningId(有值=修改，无=新增)、warningType(no_follow_up/no_order)、"
                + "thresholdDays(阈值天数)、isEnabled(0/1)、receiverRole(接收角色)、remark。"
                + "先通过 setting_crm_warning_get 查看现有配置。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> poolProps = new LinkedHashMap<>();
        poolProps.put("type", "object");
        Map<String, Object> poolFields = new LinkedHashMap<>();
        poolFields.put("configId", Map.of("type", "integer", "description", "公海池配置ID，有值=修改，无=新增"));
        poolFields.put("configName", Map.of("type", "string", "description", "配置名称"));
        poolFields.put("noFollowDays", Map.of("type", "integer", "description", "未跟进天数"));
        poolFields.put("noOrderDays", Map.of("type", "integer", "description", "未开单天数"));
        poolFields.put("warningDays", Map.of("type", "integer", "description", "预警天数"));
        poolFields.put("isEnabled", Map.of("type", "integer", "description", "是否启用：1是/0否"));
        poolFields.put("remark", Map.of("type", "string", "description", "备注"));
        poolProps.put("properties", poolFields);

        Map<String, Object> warningProps = new LinkedHashMap<>();
        warningProps.put("type", "object");
        Map<String, Object> warningFields = new LinkedHashMap<>();
        warningFields.put("warningId", Map.of("type", "integer", "description", "预警配置ID，有值=修改，无=新增"));
        warningFields.put("warningType", Map.of("type", "string", "description", "预警类型：no_follow_up/no_order"));
        warningFields.put("thresholdDays", Map.of("type", "integer", "description", "阈值天数"));
        warningFields.put("isEnabled", Map.of("type", "integer", "description", "是否启用：1是/0否"));
        warningFields.put("receiverRole", Map.of("type", "string", "description", "接收角色"));
        warningFields.put("remark", Map.of("type", "string", "description", "备注"));
        warningProps.put("properties", warningFields);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("poolConfig", Map.of("type", "object", "description", "公海池配置对象，可选", "items", poolProps));
        props.put("warningConfig", Map.of("type", "object", "description", "跟进预警配置对象，可选", "items", warningProps));
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
            Object poolObj = p.get("poolConfig");
            Object warningObj = p.get("warningConfig");
            if (poolObj == null && warningObj == null) {
                return Map.of("success", false, "message", "参数缺失：至少提供 poolConfig 或 warningConfig 之一");
            }
            StringBuilder msg = new StringBuilder();

            if (poolObj != null) {
                CustomerPoolConfig cfg = poolObj instanceof CustomerPoolConfig c
                        ? c : mapper.convertValue(poolObj, CustomerPoolConfig.class);
                boolean isNew = cfg.getConfigId() == null;
                if (isNew) {
                    customerPoolConfigMapper.insert(cfg);
                } else {
                    customerPoolConfigMapper.updateById(cfg);
                }
                log.info("[AI设置] 用户{} {}CRM公海池配置 id={} name={}",
                        scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                        cfg.getConfigId(), cfg.getConfigName());
                msg.append(isNew ? "已新增公海池配置" : "已修改公海池配置").append("；");
            }

            if (warningObj != null) {
                CustomerWarningConfig cfg = warningObj instanceof CustomerWarningConfig c
                        ? c : mapper.convertValue(warningObj, CustomerWarningConfig.class);
                boolean isNew = cfg.getWarningId() == null;
                if (isNew) {
                    customerWarningConfigMapper.insert(cfg);
                } else {
                    customerWarningConfigMapper.updateById(cfg);
                }
                log.info("[AI设置] 用户{} {}CRM跟进预警配置 id={} type={}",
                        scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                        cfg.getWarningId(), cfg.getWarningType());
                msg.append(isNew ? "已新增跟进预警配置" : "已修改跟进预警配置").append("；");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", msg.toString());
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 保存CRM预警配置失败", e);
            return Map.of("success", false, "message", "保存CRM预警配置失败: " + e.getMessage());
        }
    }
}
