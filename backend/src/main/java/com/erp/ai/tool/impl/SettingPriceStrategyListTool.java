package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.common.PageResult;
import com.erp.price.purchase.entity.PurchasePriceStrategy;
import com.erp.price.purchase.service.PurchasePriceStrategyService;
import com.erp.price.sale.entity.SalePriceStrategy;
import com.erp.price.sale.service.SalePriceStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询销售/采购价格策略列表（设置中心 → 价格策略）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPriceStrategyListTool implements AiTool {

    private final SalePriceStrategyService salePriceStrategyService;
    private final PurchasePriceStrategyService purchasePriceStrategyService;

    @Override
    public String getName() {
        return "setting_price_strategy_list";
    }

    @Override
    public String getDescription() {
        return "查询价格策略列表（分页，只读）。适用于：查看销售价格策略或采购价格策略。"
                + "必填参数：type('sale'销售 / 'purchase'采购)。可选：keyword(按策略名称或编码过滤)、"
                + "page(默认1)、size(默认20)。修改请用 setting_price_strategy_save。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", Map.of("type", "string", "description", "价格策略类型：sale(销售) / purchase(采购)，必填"));
        props.put("keyword", Map.of("type", "string", "description", "可选，按策略名称或编码模糊过滤"));
        props.put("page", Map.of("type", "integer", "description", "页码，默认1"));
        props.put("size", Map.of("type", "integer", "description", "每页条数，默认20"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("type"));
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
            String keyword = SettingSupport.str(p.get("keyword"));
            Integer page = SettingSupport.integer(p.get("page"));
            Integer size = SettingSupport.integer(p.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;

            List<Map<String, Object>> items = new ArrayList<>();
            long total;
            if ("sale".equalsIgnoreCase(type)) {
                PageResult<SalePriceStrategy> pr = salePriceStrategyService.listPage(current, pageSize, keyword);
                total = pr == null ? 0 : pr.getTotal();
                if (pr != null && pr.getRecords() != null) {
                    for (SalePriceStrategy s : pr.getRecords()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", s.getId());
                        item.put("strategyName", s.getStrategyName());
                        item.put("strategyCode", s.getStrategyCode());
                        item.put("priority", s.getPriority());
                        item.put("customerLevel", s.getCustomerLevel());
                        item.put("materialCategoryId", s.getMaterialCategoryId());
                        item.put("brandId", s.getBrandId());
                        item.put("startDate", s.getStartDate());
                        item.put("endDate", s.getEndDate());
                        item.put("minQuantity", s.getMinQuantity());
                        item.put("maxQuantity", s.getMaxQuantity());
                        item.put("priceType", s.getPriceType());
                        item.put("priceValue", s.getPriceValue());
                        item.put("status", s.getStatus());
                        item.put("remark", s.getRemark());
                        items.add(item);
                    }
                }
            } else if ("purchase".equalsIgnoreCase(type)) {
                PageResult<PurchasePriceStrategy> pr = purchasePriceStrategyService.listPage(current, pageSize, keyword);
                total = pr == null ? 0 : pr.getTotal();
                if (pr != null && pr.getRecords() != null) {
                    for (PurchasePriceStrategy s : pr.getRecords()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", s.getId());
                        item.put("strategyName", s.getStrategyName());
                        item.put("strategyCode", s.getStrategyCode());
                        item.put("priority", s.getPriority());
                        item.put("supplierLevel", s.getSupplierLevel());
                        item.put("materialCategoryId", s.getMaterialCategoryId());
                        item.put("brandId", s.getBrandId());
                        item.put("startDate", s.getStartDate());
                        item.put("endDate", s.getEndDate());
                        item.put("minQuantity", s.getMinQuantity());
                        item.put("maxQuantity", s.getMaxQuantity());
                        item.put("priceType", s.getPriceType());
                        item.put("priceValue", s.getPriceValue());
                        item.put("status", s.getStatus());
                        item.put("remark", s.getRemark());
                        items.add(item);
                    }
                }
            } else {
                return Map.of("success", false, "message", "type 必须是 sale 或 purchase");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", total);
            result.put("items", items);
            result.put("message", "共查到 " + total + " 条" + ("sale".equalsIgnoreCase(type) ? "销售" : "采购")
                    + "价格策略（第 " + current + " 页）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询价格策略失败", e);
            return Map.of("success", false, "message", "查询价格策略失败: " + e.getMessage());
        }
    }
}
