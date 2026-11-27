package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.service.AiOrderService;
import com.erp.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快速下单工具 - 从用户粘贴/输入的文本中提取客户、物料、数量、单价，创建销售订单草稿（走审批）。
 * 信息不全时AI应先反问补齐，不要调用本工具瞎编参数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateSaleOrderDraftTool implements AiTool {

    private final AiOrderService aiOrderService;

    @Override
    public String getName() {
        return "create_sale_order_draft";
    }

    @Override
    public String getDescription() {
        return "创建销售订单草稿（保存后进入审批流程，状态为待审核）。"
                + "适用于用户粘贴客户聊天记录、微信消息、或口述下单内容的场景。"
                + "必须先从用户输入中提取：客户名称(customerName)、物料明细(items，含物料编码或名称、数量、单价)。"
                + "若客户名称、物料、数量任一信息缺失，必须先反问用户补齐，禁止编造或使用默认值。"
                + "交货日期(deliveryDate)可选，用户未提则不传。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("materialCode", Map.of("type", "string", "description", "物料编码（与materialName至少提供一个）"));
        itemProps.put("materialName", Map.of("type", "string", "description", "物料名称（与materialCode至少提供一个）"));
        itemProps.put("quantity", Map.of("type", "number", "description", "数量，必须大于0"));
        itemProps.put("unitPrice", Map.of("type", "number", "description", "单价，可选，用户提到价格时必填"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "array");
        itemSchema.put("description", "订单物料明细列表");
        itemSchema.put("items", Map.of("type", "object", "properties", itemProps));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("customerName", Map.of("type", "string", "description", "客户名称（必填）"));
        props.put("deliveryDate", Map.of("type", "string", "description", "交货日期，格式yyyy-MM-dd（可选）"));
        props.put("items", itemSchema);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("customerName", "items"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        try {
            return aiOrderService.createDraftOrder(params, userId);
        } catch (Exception e) {
            log.error("[AI下单] 创建订单草稿失败: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "创建订单失败: " + e.getMessage());
        }
    }
}
