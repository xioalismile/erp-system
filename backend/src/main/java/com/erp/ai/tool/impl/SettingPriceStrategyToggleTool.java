package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.price.purchase.service.PurchasePriceStrategyService;
import com.erp.price.sale.service.SalePriceStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：启用/停用销售或采购价格策略（设置中心 → 价格策略），走强制确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPriceStrategyToggleTool implements AiTool {

    private final SalePriceStrategyService salePriceStrategyService;
    private final PurchasePriceStrategyService purchasePriceStrategyService;

    @Override
    public String getName() {
        return "setting_price_strategy_toggle";
    }

    @Override
    public String getDescription() {
        return "切换销售或采购价格策略的启用/停用状态（执行前会弹确认卡片）。"
                + "必填参数：type('sale'销售 / 'purchase'采购)、id(策略ID，先通过 setting_price_strategy_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", Map.of("type", "string", "description", "价格策略类型：sale / purchase，必填"));
        props.put("id", Map.of("type", "integer", "description", "策略ID，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("type", "id"));
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
            String type = SettingSupport.str(p.get("type"));
            Long id = SettingSupport.longVal(p.get("id"));
            if (type == null || id == null) {
                return Map.of("success", false, "message", "参数缺失：需要 type 和 id");
            }
            if ("sale".equalsIgnoreCase(type)) {
                salePriceStrategyService.toggleStatus(id);
            } else if ("purchase".equalsIgnoreCase(type)) {
                purchasePriceStrategyService.toggleStatus(id);
            } else {
                return Map.of("success", false, "message", "type 必须是 sale 或 purchase");
            }
            log.info("[AI设置] 用户{}切换{}价格策略状态 id={}",
                    scope != null ? scope.getUsername() : "?",
                    "sale".equalsIgnoreCase(type) ? "销售" : "采购", id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已切换" + ("sale".equalsIgnoreCase(type) ? "销售" : "采购")
                    + "价格策略（ID " + id + "）的启用状态。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 切换价格策略状态失败", e);
            return Map.of("success", false, "message", "切换价格策略状态失败: " + e.getMessage());
        }
    }
}
