package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询设置中心系统参数（按分组）。
 * 覆盖设置项：基础信息设置、系统参数设置、员工权限补充、菜单名称、主题、消息通知、批次编号、物流看板参数、审批开关。
 * 注意：AI 自身配置（ai.* 前缀：工具开关/数据政策/配额）不在此工具返回范围内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingConfigListTool implements AiTool {

    private final SysConfigService sysConfigService;

    @Override
    public String getName() {
        return "setting_config_list";
    }

    @Override
    public String getDescription() {
        return "查询设置中心的系统参数（按分组）。适用于：查看基础信息/系统参数/主题/消息通知/菜单名称/批次编号/物流看板等配置当前值。"
                + "group 取值：basic(基础信息)、sys_param(系统参数)、purchase(采购参数)、sale(销售参数)、"
                + "inventory(库存参数)、subcontract(委外参数)、employee_perm(员工权限补充)、menu_name(菜单名称)、"
                + "ui(主题)、notification(消息通知)、batch_no(批次编号)、logistics(物流看板)、approval(审批开关)。"
                + "不传 group 时返回全部可查分组。修改参数请使用 setting_config_set。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("group", Map.of("type", "string",
                "description", "分组key，可选值见工具描述；不传则返回全部可查分组"));
        props.put("keyword", Map.of("type", "string",
                "description", "可选。按参数key或名称模糊过滤"));
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
            String group = SettingSupport.str(params == null ? null : params.get("group"));
            String keyword = SettingSupport.str(params == null ? null : params.get("keyword"));
            List<String> groups;
            if (group != null) {
                if (!SettingSupport.CONFIG_GROUPS.contains(group)) {
                    return Map.of("success", false, "message",
                            "不支持的分组: " + group + "。可用分组: " + String.join("、", SettingSupport.CONFIG_GROUPS));
                }
                groups = List.of(group);
            } else {
                groups = new ArrayList<>(SettingSupport.CONFIG_GROUPS);
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (String g : groups) {
                List<SysConfig> list = sysConfigService.listByGroup(g);
                if (list == null) continue;
                for (SysConfig c : list) {
                    String key = c.getConfigKey();
                    // AI 自身配置不返回（工具开关/数据政策/配额）
                    if (SettingSupport.isAiOwnedKey(key)) continue;
                    // 已下线的配置项不返回（界面已不渲染、代码无消费方，避免误报"可以改开关"）
                    if (SettingSupport.isDeprecatedKey(key)) continue;
                    // 密钥类参数（password/secret，如物流授权码/签名密钥/17TRACK密钥）不下发给 AI
                    String cfgType = c.getConfigType() == null ? "" : c.getConfigType();
                    if ("password".equalsIgnoreCase(cfgType) || "secret".equalsIgnoreCase(cfgType)) continue;
                    if (keyword != null) {
                        String name = c.getConfigName() == null ? "" : c.getConfigName();
                        if (!(key != null && key.toLowerCase().contains(keyword.toLowerCase()))
                                && !name.contains(keyword)) {
                            continue;
                        }
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", key);
                    item.put("name", c.getConfigName());
                    item.put("value", c.getConfigValue());
                    item.put("type", c.getConfigType());
                    item.put("options", c.getConfigOptions());
                    item.put("remark", c.getRemark());
                    items.add(item);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", items.size());
            result.put("items", items);
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询系统参数失败", e);
            return Map.of("success", false, "message", "查询系统参数失败: " + e.getMessage());
        }
    }
}
