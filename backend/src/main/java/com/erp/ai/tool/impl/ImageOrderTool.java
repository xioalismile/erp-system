package com.erp.ai.tool.impl;

import com.erp.ai.service.AiOrderService;
import com.erp.ai.service.AiVisionService;
import com.erp.ai.tool.AiTool;
import com.erp.masterdata.entity.Material;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 图片下单工具
 * 流程：识别图片中的商品 → 匹配物料库 → 调用 AiOrderService.createDraftOrder() 创建销售订单草稿
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageOrderTool implements AiTool {

    private final AiVisionService aiVisionService;
    private final AiOrderService aiOrderService;

    @Override
    public String getName() {
        return "image_create_order";
    }

    @Override
    public String getDescription() {
        return "图片下单：识别用户上传的商品图片，匹配物料库后自动创建销售订单草稿。" +
                "适用场景：用户上传商品图片并希望下单（如'帮我下单'、'这个产品订100个'）。" +
                "调用此工具后系统会自动识别商品、匹配物料、生成订单草稿。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("type", "string");
        imageUrl.put("description", "用户上传的商品图片URL");
        props.put("imageUrl", imageUrl);

        Map<String, Object> userQuery = new LinkedHashMap<>();
        userQuery.put("type", "string");
        userQuery.put("description", "用户的原始查询文本，可能包含数量、客户名等信息（如：这个产品订100个，客户是XX公司）");
        props.put("userQuery", userQuery);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("imageUrl"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String imageUrl = (String) params.get("imageUrl");
        String userQuery = (String) params.getOrDefault("userQuery", "");

        try {
            log.info("[图片下单] 开始处理 imageUrl={}, query={}", imageUrl, userQuery);

            // 1. 识别图片
            String recognitionResult = aiVisionService.recognizeImage(imageUrl, buildRecognitionPrompt());
            if (recognitionResult == null || recognitionResult.isBlank()) {
                return Map.of("success", false, "message", "图片识别失败，请检查图片是否清晰");
            }
            log.info("[图片下单] 识别结果: {}", recognitionResult);

            // 2. 匹配物料
            List<Material> matched = aiVisionService.matchMaterials(recognitionResult, 3);
            if (matched.isEmpty()) {
                return Map.of(
                        "success", false,
                        "message", "未在物料库中找到匹配的商品，请先在物料档案中添加该商品",
                        "recognitionResult", recognitionResult
                );
            }

            Material material = matched.get(0);
            log.info("[图片下单] 匹配到物料: {} ({})", material.getMaterialName(), material.getMaterialCode());

            // 3. 从用户查询中提取数量和客户
            int quantity = extractQuantity(userQuery);
            String customerName = extractCustomerName(userQuery);

            // 4. 构建下单参数
            Map<String, Object> orderParams = new HashMap<>();
            if (customerName != null) {
                orderParams.put("customerName", customerName);
            } else {
                // 没有客户名时，使用第一个客户（由AiOrderService处理）
                orderParams.put("customerName", "");
            }

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> item = new HashMap<>();
            item.put("materialCode", material.getMaterialCode());
            item.put("materialName", material.getMaterialName());
            item.put("quantity", new BigDecimal(quantity));
            item.put("unitPrice", material.getSalePrice() != null ? material.getSalePrice() : BigDecimal.TEN);
            items.add(item);
            orderParams.put("items", items);

            // 5. 创建订单
            Map<String, Object> result = aiOrderService.createDraftOrder(orderParams, userId);

            // 6. 补充识别信息
            Map<String, Object> enrichedResult = new LinkedHashMap<>(result);
            enrichedResult.put("recognitionResult", recognitionResult);
            enrichedResult.put("matchedMaterial", Map.of(
                    "materialCode", material.getMaterialCode(),
                    "materialName", material.getMaterialName(),
                    "model", material.getModel() != null ? material.getModel() : ""
            ));
            return enrichedResult;

        } catch (Exception e) {
            log.error("[图片下单] 执行失败", e);
            return Map.of("success", false, "message", "图片下单失败: " + e.getMessage());
        }
    }

    private String buildRecognitionPrompt() {
        return "请仔细识别这张图片中的商品，提取以下关键信息：\n" +
                "1. 商品名称：这是什么商品\n" +
                "2. 规格型号：尺寸、规格、型号\n" +
                "3. 颜色特征\n" +
                "4. 品牌标识\n" +
                "5. 数量（如果图片中有数量信息）\n" +
                "请用中文清晰描述。";
    }

    private int extractQuantity(String query) {
        if (query == null || query.isBlank()) return 1;
        // 匹配中文数字和阿拉伯数字
        String lower = query.toLowerCase();
        // 先找阿拉伯数字
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(个|件|台|套|kg|千克|只|pcs|个|盒|箱)").matcher(lower);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        m = java.util.regex.Pattern.compile("(\\d+)").matcher(lower);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        // 中文数字
        String[] cn = {"一","二","三","四","五","六","七","八","九","十"};
        for (int i = 0; i < cn.length; i++) {
            if (query.contains(cn[i])) return i + 1;
        }
        return 1;
    }

    private String extractCustomerName(String query) {
        if (query == null || query.isBlank()) return null;
        // 匹配 "客户是XXX" 或 "送给XXX" 等
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("客户[是为]([^,，。.\\s]+)").matcher(query);
        if (m.find()) return m.group(1).trim();
        m = java.util.regex.Pattern.compile("送给([^,，。.\\s]+)").matcher(query);
        if (m.find()) return m.group(1).trim();
        return null;
    }
}
