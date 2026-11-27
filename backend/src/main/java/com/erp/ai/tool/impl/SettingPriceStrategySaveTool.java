package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.price.purchase.entity.PurchasePriceStrategy;
import com.erp.price.purchase.entity.PurchasePriceStrategyItem;
import com.erp.price.purchase.service.PurchasePriceStrategyService;
import com.erp.price.sale.entity.SalePriceStrategy;
import com.erp.price.sale.entity.SalePriceStrategyItem;
import com.erp.price.sale.service.SalePriceStrategyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增/修改销售或采购价格策略（设置中心 → 价格策略），走强制确认。
 * id 为空走 save，否则走 update；修改未传 items 时自动沿用原明细，避免明细被清空。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPriceStrategySaveTool implements AiTool {

    private final SalePriceStrategyService salePriceStrategyService;
    private final PurchasePriceStrategyService purchasePriceStrategyService;
    /** 自建 ObjectMapper 必须注册 JavaTimeModule（历史坑：AI 链路 LocalDate 序列化崩溃） */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String getName() {
        return "setting_price_strategy_save";
    }

    @Override
    public String getDescription() {
        return "新增或修改价格策略（执行前会弹确认卡片）。不传 id 为新增，传 id 为修改。"
                + "必填参数：type('sale'销售 / 'purchase'采购)。"
                + "公共字段：id、targetType(适用范围：material按物料/category按商品分类/customer按客户，采购侧第三档为supplier按供应商)、"
                + "priority(优先级，默认1且不能与其它策略重复)、materialCategoryId、"
                + "startDate(yyyy-MM-dd)、endDate、minQuantity、maxQuantity、status(1启用/0停用)、remark。"
                + "策略编码 strategyCode 由系统自动生成，不需要传；策略名称已取消。"
                + "items(明细数组，必填，至少一条)：每行含 {targetType, targetId(对象ID：物料/商品分类/客户或供应商), "
                + "minQuantity/maxQuantity(该对象的数量区间，可空=不限)，"
                + "priceType(fixed固定价/discount折扣率/markup加价率/gross_profit毛利率), priceValue, costPrice, grossProfitRate, remark}。"
                + "顶层的 minQuantity/maxQuantity 已废弃（数量区间改为按明细行设置），可不传。"
                + "修改时不传 items 会沿用原明细。先通过 setting_price_strategy_list 确认 id。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", Map.of("type", "string", "description", "价格策略类型：sale / purchase，必填"));
        props.put("id", Map.of("type", "integer", "description", "策略ID，修改时必传；新增不传"));
        props.put("targetType", Map.of("type", "string", "description", "适用范围：material按物料 / category按商品分类 / customer(supplier)按客户(供应商)"));
        props.put("strategyName", Map.of("type", "string", "description", "策略名称（已取消，可不传）"));
        props.put("strategyCode", Map.of("type", "string", "description", "策略编码（系统自动生成，可不传）"));
        props.put("priority", Map.of("type", "integer", "description", "优先级"));
        props.put("customerLevel", Map.of("type", "string", "description", "客户等级（仅销售）"));
        props.put("supplierLevel", Map.of("type", "string", "description", "供应商等级（仅采购）"));
        props.put("materialCategoryId", Map.of("type", "integer", "description", "物料分类ID"));
        props.put("brandId", Map.of("type", "integer", "description", "品牌ID"));
        props.put("startDate", Map.of("type", "string", "description", "生效日期 yyyy-MM-dd"));
        props.put("endDate", Map.of("type", "string", "description", "失效日期 yyyy-MM-dd"));
        props.put("minQuantity", Map.of("type", "number", "description", "最小数量"));
        props.put("maxQuantity", Map.of("type", "number", "description", "最大数量"));
        props.put("priceType", Map.of("type", "string", "description", "价格类型"));
        props.put("priceValue", Map.of("type", "number", "description", "价格值"));
        props.put("status", Map.of("type", "integer", "description", "状态：1启用/0停用"));
        props.put("remark", Map.of("type", "string", "description", "备注"));
        props.put("items", Map.of("type", "array", "description", "明细数组，可选",
                "items", Map.of("type", "object")));
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
            Long id = SettingSupport.longVal(p.get("id"));
            Object itemsObj = p.get("items");

            if ("sale".equalsIgnoreCase(type)) {
                SalePriceStrategy strategy = new SalePriceStrategy();
                strategy.setId(id);
                if (p.containsKey("strategyName")) strategy.setStrategyName(SettingSupport.str(p.get("strategyName")));
                if (p.containsKey("strategyCode")) strategy.setStrategyCode(SettingSupport.str(p.get("strategyCode")));
                if (p.containsKey("targetType")) strategy.setTargetType(SettingSupport.str(p.get("targetType")));
                if (p.containsKey("priority")) strategy.setPriority(SettingSupport.integer(p.get("priority")));
                if (p.containsKey("customerLevel")) strategy.setCustomerLevel(SettingSupport.str(p.get("customerLevel")));
                if (p.containsKey("materialCategoryId")) strategy.setMaterialCategoryId(SettingSupport.longVal(p.get("materialCategoryId")));
                if (p.containsKey("brandId")) strategy.setBrandId(SettingSupport.longVal(p.get("brandId")));
                if (p.containsKey("startDate")) strategy.setStartDate(parseDate(SettingSupport.str(p.get("startDate"))));
                if (p.containsKey("endDate")) strategy.setEndDate(parseDate(SettingSupport.str(p.get("endDate"))));
                if (p.containsKey("minQuantity")) strategy.setMinQuantity(SettingSupport.decimal(p.get("minQuantity")));
                if (p.containsKey("maxQuantity")) strategy.setMaxQuantity(SettingSupport.decimal(p.get("maxQuantity")));
                if (p.containsKey("priceType")) strategy.setPriceType(SettingSupport.str(p.get("priceType")));
                if (p.containsKey("priceValue")) strategy.setPriceValue(SettingSupport.decimal(p.get("priceValue")));
                if (p.containsKey("status")) strategy.setStatus(SettingSupport.integer(p.get("status")));
                if (p.containsKey("remark")) strategy.setRemark(SettingSupport.str(p.get("remark")));

                List<SalePriceStrategyItem> items = new ArrayList<>();
                if (itemsObj instanceof List<?> rawItems && !rawItems.isEmpty()) {
                    for (Object o : rawItems) {
                        items.add(o instanceof SalePriceStrategyItem si ? si
                                : mapper.convertValue(o, SalePriceStrategyItem.class));
                    }
                } else if (id != null) {
                    SalePriceStrategy existing = salePriceStrategyService.getById(id);
                    if (existing.getItems() != null) items = existing.getItems();
                }

                boolean isNew = id == null;
                if (isNew) {
                    salePriceStrategyService.save(strategy, items);
                } else {
                    salePriceStrategyService.update(strategy, items);
                }
                log.info("[AI设置] 用户{} {}销售价格策略 name={} id={}",
                        scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                        strategy.getStrategyName(), strategy.getId());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", (isNew ? "已新增" : "已修改") + "销售价格策略「"
                        + (strategy.getStrategyName() != null ? strategy.getStrategyName() : strategy.getId()) + "」。");
                if (strategy.getId() != null) result.put("id", strategy.getId());
                return result;
            }

            if ("purchase".equalsIgnoreCase(type)) {
                PurchasePriceStrategy strategy = new PurchasePriceStrategy();
                strategy.setId(id);
                if (p.containsKey("strategyName")) strategy.setStrategyName(SettingSupport.str(p.get("strategyName")));
                if (p.containsKey("strategyCode")) strategy.setStrategyCode(SettingSupport.str(p.get("strategyCode")));
                if (p.containsKey("targetType")) strategy.setTargetType(SettingSupport.str(p.get("targetType")));
                if (p.containsKey("priority")) strategy.setPriority(SettingSupport.integer(p.get("priority")));
                if (p.containsKey("supplierLevel")) strategy.setSupplierLevel(SettingSupport.str(p.get("supplierLevel")));
                if (p.containsKey("materialCategoryId")) strategy.setMaterialCategoryId(SettingSupport.longVal(p.get("materialCategoryId")));
                if (p.containsKey("brandId")) strategy.setBrandId(SettingSupport.longVal(p.get("brandId")));
                if (p.containsKey("startDate")) strategy.setStartDate(parseDate(SettingSupport.str(p.get("startDate"))));
                if (p.containsKey("endDate")) strategy.setEndDate(parseDate(SettingSupport.str(p.get("endDate"))));
                if (p.containsKey("minQuantity")) strategy.setMinQuantity(SettingSupport.decimal(p.get("minQuantity")));
                if (p.containsKey("maxQuantity")) strategy.setMaxQuantity(SettingSupport.decimal(p.get("maxQuantity")));
                if (p.containsKey("priceType")) strategy.setPriceType(SettingSupport.str(p.get("priceType")));
                if (p.containsKey("priceValue")) strategy.setPriceValue(SettingSupport.decimal(p.get("priceValue")));
                if (p.containsKey("status")) strategy.setStatus(SettingSupport.integer(p.get("status")));
                if (p.containsKey("remark")) strategy.setRemark(SettingSupport.str(p.get("remark")));

                List<PurchasePriceStrategyItem> items = new ArrayList<>();
                if (itemsObj instanceof List<?> rawItems && !rawItems.isEmpty()) {
                    for (Object o : rawItems) {
                        items.add(o instanceof PurchasePriceStrategyItem pi ? pi
                                : mapper.convertValue(o, PurchasePriceStrategyItem.class));
                    }
                } else if (id != null) {
                    PurchasePriceStrategy existing = purchasePriceStrategyService.getById(id);
                    if (existing.getItems() != null) items = existing.getItems();
                }

                boolean isNew = id == null;
                if (isNew) {
                    purchasePriceStrategyService.save(strategy, items);
                } else {
                    purchasePriceStrategyService.update(strategy, items);
                }
                log.info("[AI设置] 用户{} {}采购价格策略 name={} id={}",
                        scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                        strategy.getStrategyName(), strategy.getId());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", (isNew ? "已新增" : "已修改") + "采购价格策略「"
                        + (strategy.getStrategyName() != null ? strategy.getStrategyName() : strategy.getId()) + "」。");
                if (strategy.getId() != null) result.put("id", strategy.getId());
                return result;
            }

            return Map.of("success", false, "message", "type 必须是 sale 或 purchase");
        } catch (Exception e) {
            log.error("[AI设置] 保存价格策略失败", e);
            return Map.of("success", false, "message", "保存价格策略失败: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String s) {
        return s == null ? null : LocalDate.parse(s);
    }
}
