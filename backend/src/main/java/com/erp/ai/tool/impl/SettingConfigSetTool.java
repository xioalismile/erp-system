package com.erp.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：修改设置中心系统参数（单键写入，走强制确认）。
 *
 * 安全约束（硬编码，不可配置）：
 * 1. 拒绝 ai.* 前缀的 key（AI 自身工具开关/数据政策/配额），防 AI 自提权或关闭脱敏；
 * 2. 只允许已存在的参数（不新建 key）；
 * 3. 只做单键写入（不提供整组覆盖，避免历史值被空值冲掉）；
 * 4. group 必须命中白名单，且必须与该参数实际所属分组一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingConfigSetTool implements AiTool {

    private final SysConfigService sysConfigService;

    @Override
    public String getName() {
        return "setting_config_set";
    }

    @Override
    public String getDescription() {
        return "修改设置中心的系统参数（单键写入，执行前会弹确认卡片）。"
                + "适用于：改基础信息名称、改主题主色、开关消息通知、改菜单显示名、改批次编号规则、改物流看板参数、开关审批等。"
                + "注意：必须先通过 setting_config_list 查到确切的 key 再改；不支持一次改整组参数；"
                + "AI 自身的开关/政策/配额（ai. 前缀）不允许修改；密钥/授权码类请到界面设置。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("group", Map.of("type", "string",
                "description", "该参数所属分组key（与 setting_config_list 返回一致）"));
        props.put("key", Map.of("type", "string",
                "description", "参数key，必须是已存在的参数（先调 setting_config_list 确认）"));
        props.put("value", Map.of("type", "string",
                "description", "新值。布尔型传 true/false；数字型传数字字符串；下拉型传选项value"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("group", "key", "value"));
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
            String key = SettingSupport.str(params == null ? null : params.get("key"));
            Object rawValue = params == null ? null : params.get("value");

            if (group == null || key == null) {
                return Map.of("success", false, "message", "参数缺失：需要 group 和 key");
            }
            if (!SettingSupport.CONFIG_GROUPS.contains(group)) {
                return Map.of("success", false, "message",
                        "不支持的分组: " + group + "。可用分组: " + String.join("、", SettingSupport.CONFIG_GROUPS));
            }
            // 硬黑名单：AI 自身配置（工具开关/数据政策/配额）
            if (SettingSupport.isAiOwnedKey(key)) {
                return Map.of("success", false, "message",
                        "AI 不得修改 AI 自身的配置（" + key + "）。如需调整请由管理员前往 设置中心 → AI数据使用政策/AI用量管理 操作");
            }
            // 已下线的配置项：界面已不渲染、代码无消费方，改也无效果，直接拒绝并指路专用页
            if (SettingSupport.isDeprecatedKey(key)) {
                return Map.of("success", false, "message",
                        "参数 " + key + " 已下线（设置中心系统参数里已不再提供）。客户/供应商编号请到 设置中心 → 自动编号设置 的「客户档案 / 供应商档案」里配置");
            }

            SysConfig cfg = sysConfigService.getOne(
                    new QueryWrapper<SysConfig>().eq("config_key", key).last("LIMIT 1"));
            if (cfg == null) {
                return Map.of("success", false, "message",
                        "参数不存在: " + key + "。请先调用 setting_config_list 确认准确 key，不会新建参数");
            }
            if (cfg.getConfigGroup() != null && !cfg.getConfigGroup().equals(group)) {
                return Map.of("success", false, "message",
                        "参数 " + key + " 属于分组 " + cfg.getConfigGroup() + "，与传入的 group=" + group + " 不一致");
            }

            String value = rawValue == null ? "" : String.valueOf(rawValue).trim();
            // 按参数类型做基础校验
            String type = cfg.getConfigType() == null ? "" : cfg.getConfigType().toLowerCase();
            if ("boolean".equals(type)) {
                String v = value.toLowerCase();
                if (!"true".equals(v) && !"false".equals(v) && !"1".equals(v) && !"0".equals(v)) {
                    return Map.of("success", false, "message",
                            "参数 " + key + " 是开关类型，值必须为 true/false");
                }
                value = ("1".equals(v) || "true".equals(v)) ? "true" : "false";
            } else if ("number".equals(type) || "int".equals(type) || "integer".equals(type)) {
                try {
                    new java.math.BigDecimal(value);
                } catch (Exception e) {
                    return Map.of("success", false, "message",
                            "参数 " + key + " 是数字类型，值必须为数字");
                }
            }

            String oldValue = cfg.getConfigValue();
            boolean ok = sysConfigService.updateValue(key, value);
            if (!ok) {
                return Map.of("success", false, "message", "保存失败: " + key);
            }
            log.info("[AI设置] 用户{}修改系统参数 {}: {} -> {}",
                    scope != null ? scope.getUsername() : "?", key, oldValue, value);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已把「" + (cfg.getConfigName() != null ? cfg.getConfigName() : key)
                    + "」由 " + (oldValue == null || oldValue.isBlank() ? "(空)" : oldValue) + " 改为 " + value);
            result.put("key", key);
            result.put("oldValue", oldValue);
            result.put("newValue", value);
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 修改系统参数失败", e);
            return Map.of("success", false, "message", "修改系统参数失败: " + e.getMessage());
        }
    }
}
