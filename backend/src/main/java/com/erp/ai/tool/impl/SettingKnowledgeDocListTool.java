package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.knowledge.entity.KnowledgeDoc;
import com.erp.ai.knowledge.service.KnowledgeService;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询AI知识库文档列表（设置中心 → AI知识库）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingKnowledgeDocListTool implements AiTool {

    private final KnowledgeService knowledgeService;

    @Override
    public String getName() {
        return "setting_knowledge_doc_list";
    }

    @Override
    public String getDescription() {
        return "查询AI知识库文档列表（只读）。适用于：查看已上传的知识库文档、解析状态、切片数量。"
                + "参数：module(可选，知识库分组模块，如 general)。"
                + "删除文档请用 setting_knowledge_doc_delete。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("module", Map.of("type", "string", "description", "可选，知识库分组模块，如 general"));
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
            String module = SettingSupport.str(params == null ? null : params.get("module"));
            List<KnowledgeDoc> docs = knowledgeService.list(module);
            List<Map<String, Object>> items = new ArrayList<>();
            if (docs != null) {
                for (KnowledgeDoc d : docs) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", d.getId());
                    item.put("title", d.getTitle());
                    item.put("module", d.getModule());
                    item.put("filePath", d.getFilePath());
                    item.put("fileType", d.getFileType());
                    item.put("fileSize", d.getFileSize());
                    item.put("status", d.getStatus());
                    item.put("chunkCount", d.getChunkCount());
                    item.put("errorMsg", d.getErrorMsg());
                    item.put("createdBy", d.getCreatedBy());
                    item.put("createdAt", d.getCreatedAt());
                    items.add(item);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", items.size());
            result.put("items", items);
            result.put("message", "共查到 " + items.size() + " 个知识库文档。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询知识库文档失败", e);
            return Map.of("success", false, "message", "查询知识库文档失败: " + e.getMessage());
        }
    }
}
