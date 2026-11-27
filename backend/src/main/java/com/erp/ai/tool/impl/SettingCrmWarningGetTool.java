package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import com.erp.masterdata.entity.CustomerPoolConfig;
import com.erp.masterdata.entity.CustomerWarningConfig;
import com.erp.masterdata.mapper.CustomerPoolConfigMapper;
import com.erp.masterdata.mapper.CustomerWarningConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询CRM客户预警全部配置（设置中心 → CRM预警）。
 * 含公海池配置 + 跟进预警配置 + crm.* 系统参数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCrmWarningGetTool implements AiTool {

    private final CustomerPoolConfigMapper customerPoolConfigMapper;
    private final CustomerWarningConfigMapper customerWarningConfigMapper;
    private final SysConfigService sysConfigService;

    @Override
    public String getName() {
        return "setting_crm_warning_get";
    }

    @Override
    public String getDescription() {
        return "查询CRM客户预警的全部配置（只读）。适用于：查看客户公海池规则、未跟进/未开单预警阈值、crm.* 开关与阈值参数。"
                + "返回 poolConfigs(公海池配置) + warningConfigs(跟进预警配置) + crmConfigs(系统参数)。"
                + "修改请用 setting_crm_warning_save，删除请用 setting_crm_warning_delete。无必填参数。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
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
            List<Map<String, Object>> poolConfigs = new ArrayList<>();
            List<CustomerPoolConfig> poolList = customerPoolConfigMapper.selectList(null);
            if (poolList != null) {
                for (CustomerPoolConfig c : poolList) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("configId", c.getConfigId());
                    m.put("configName", c.getConfigName());
                    m.put("noFollowDays", c.getNoFollowDays());
                    m.put("noOrderDays", c.getNoOrderDays());
                    m.put("warningDays", c.getWarningDays());
                    m.put("isEnabled", c.getIsEnabled());
                    m.put("remark", c.getRemark());
                    poolConfigs.add(m);
                }
            }

            List<Map<String, Object>> warningConfigs = new ArrayList<>();
            List<CustomerWarningConfig> warningList = customerWarningConfigMapper.selectList(null);
            if (warningList != null) {
                for (CustomerWarningConfig c : warningList) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("warningId", c.getWarningId());
                    m.put("warningType", c.getWarningType());
                    m.put("thresholdDays", c.getThresholdDays());
                    m.put("isEnabled", c.getIsEnabled());
                    m.put("receiverRole", c.getReceiverRole());
                    m.put("remark", c.getRemark());
                    warningConfigs.add(m);
                }
            }

            List<Map<String, Object>> crmConfigs = new ArrayList<>();
            List<SysConfig> configList = sysConfigService.listByGroup("crm");
            if (configList != null) {
                for (SysConfig c : configList) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", c.getConfigKey());
                    m.put("name", c.getConfigName());
                    m.put("value", c.getConfigValue());
                    m.put("type", c.getConfigType());
                    m.put("remark", c.getRemark());
                    crmConfigs.add(m);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("poolConfigs", poolConfigs);
            result.put("warningConfigs", warningConfigs);
            result.put("crmConfigs", crmConfigs);
            result.put("message", "CRM预警配置：公海池 " + poolConfigs.size() + " 条、跟进预警 "
                    + warningConfigs.size() + " 条、系统参数 " + crmConfigs.size() + " 项。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询CRM预警配置失败", e);
            return Map.of("success", false, "message", "查询CRM预警配置失败: " + e.getMessage());
        }
    }
}
