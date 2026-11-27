package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.knowledge.service.KnowledgeService;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：删除AI知识库文档（设置中心 → AI知识库），走强制确认。
 * 会同时删除文档文件与其全部向量切片，不可恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingKnowledgeDocDeleteTool implements AiTool {

    private final KnowledgeService knowledgeService;

    @Override
    public String getName() {
        return "setting_knowledge_doc_delete";
    }

    @Override
    public String getDescription() {
        return "删除一个AI知识库文档（执行前会弹确认卡片）。会同时删除文档文件及其全部向量切片，不可恢复，请谨慎操作。"
                + "必填参数：id(文档ID，先通过 setting_knowledge_doc_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "知识库文档ID，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("id"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Long id = SettingSupport.longVal(params == null ? null : params.get("id"));
            if (id == null) {
                return Map.of("success", false, "message", "参数缺失：需要 id");
            }
            knowledgeService.delete(id);
            log.info("[AI设置] 用户{}删除知识库文档 id={}",
                    scope != null ? scope.getUsername() : "?", id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已删除知识库文档（ID " + id + "）及其向量切片。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 删除知识库文档失败", e);
            return Map.of("success", false, "message", "删除知识库文档失败: " + e.getMessage());
        }
    }
}
