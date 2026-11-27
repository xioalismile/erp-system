package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.system.entity.CommissionRate;
import com.erp.system.service.CommissionRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询当前生效的提成率与历史版本（设置中心 → 提成率设置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCommissionRateCurrentTool implements AiTool {

    private final CommissionRateService commissionRateService;

    @Override
    public String getName() {
        return "setting_commission_rate_current";
    }

    @Override
    public String getDescription() {
        return "查询当前生效的提成率配置及历史版本（只读）。适用于：查看当前基础提成率、超额奖励规则、历史版本。"
                + "参数：page(默认1)、size(默认20)。新增提成率版本请用 setting_commission_rate_create。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("page", Map.of("type", "integer", "description", "历史版本页码，默认1"));
        props.put("size", Map.of("type", "integer", "description", "历史版本每页条数，默认20"));
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
            Integer page = SettingSupport.integer(params == null ? null : params.get("page"));
            Integer size = SettingSupport.integer(params == null ? null : params.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;

            CommissionRate currentRate = commissionRateService.getCurrent();
            List<CommissionRate> history = commissionRateService.listHistory(current, pageSize);

            Map<String, Object> currentMap = null;
            if (currentRate != null) {
                currentMap = toMap(currentRate);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            if (history != null) {
                for (CommissionRate r : history) {
                    items.add(toMap(r));
                }
            }
            int total = commissionRateService.countAll();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("current", currentMap);
            result.put("total", total);
            result.put("items", items);
            result.put("message", currentMap == null
                    ? "当前没有生效的提成率配置。"
                    : "当前生效提成率：基础 " + currentRate.getBaseRate() + "，共 " + total + " 个历史版本。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询提成率失败", e);
            return Map.of("success", false, "message", "查询提成率失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(CommissionRate r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("versionNo", r.getVersionNo());
        m.put("basis", r.getBasis());
        m.put("baseRate", r.getBaseRate());
        m.put("overAchievementMultiplier", r.getOverAchievementMultiplier());
        m.put("overAchievementBonus", r.getOverAchievementBonus());
        m.put("effectiveDate", r.getEffectiveDate());
        m.put("expiryDate", r.getExpiryDate());
        m.put("isCurrent", r.getIsCurrent());
        m.put("remark", r.getRemark());
        m.put("createdBy", r.getCreatedBy());
        return m;
    }
}
