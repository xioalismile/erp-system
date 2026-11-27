package com.erp.ai.tool.impl;

import com.erp.ai.service.AiImageSearchService;
import com.erp.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageSearchTool implements AiTool {

    private final AiImageSearchService aiImageSearchService;

    @Override
    public String getName() {
        return "image_search";
    }

    @Override
    public String getDescription() {
        return "以图识图：根据用户上传的图片，在物料库中进行相似度匹配，找到最相似的物料，并查询相关单据。" +
                "当用户上传图片并询问这是什么物料、相关订单、库存等问题时，首先调用此工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("type", "string");
        imageUrl.put("description", "图片URL，用户上传的图片地址，用于图片识别和相似度匹配");
        props.put("imageUrl", imageUrl);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "用户的查询意图描述，如：找物料、查订单、查库存等");
        props.put("query", query);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("imageUrl"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String imageUrl = (String) params.get("imageUrl");
        String query = (String) params.getOrDefault("query", "");
        return aiImageSearchService.searchByImage(imageUrl, query);
    }
}
