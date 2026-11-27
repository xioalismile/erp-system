// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.ai.knowledge.entity.KnowledgeChunk;
import com.erp.ai.knowledge.mapper.KnowledgeChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识检索服务：向量相似度 + 关键词匹配混合检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int TOP_K = 3;
    private static final int MAX_TOTAL_LENGTH = 2000;

    /**
     * 根据用户问题检索相关知识片段
     */
    public String retrieve(String query) {
        if (query == null || query.isBlank()) return "";
        try {
            List<KnowledgeChunk> allChunks = chunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunk>().select(
                            KnowledgeChunk::getId, KnowledgeChunk::getContent,
                            KnowledgeChunk::getKeywords, KnowledgeChunk::getEmbedding
                    ));
            if (allChunks.isEmpty()) return "";

            double[] queryVec = embeddingService.embed(List.of(query)).get(0);
            List<ScoredChunk> scored = new ArrayList<>();
            for (KnowledgeChunk c : allChunks) {
                double[] vec = objectMapper.readValue(c.getEmbedding(), double[].class);
                double sim = embeddingService.cosineSimilarity(queryVec, vec);
                // 关键词命中加权
                if (c.getKeywords() != null) {
                    for (String kw : c.getKeywords().split(",")) {
                        if (!kw.isBlank() && query.contains(kw)) sim += 0.1;
                    }
                }
                scored.add(new ScoredChunk(c, sim));
            }
            scored.sort((a, b) -> Double.compare(b.score, a.score));

            StringBuilder sb = new StringBuilder();
            int totalLen = 0;
            for (int i = 0; i < Math.min(TOP_K, scored.size()); i++) {
                String content = scored.get(i).chunk.getContent();
                if (totalLen + content.length() > MAX_TOTAL_LENGTH) break;
                sb.append("【知识片段").append(i + 1).append("】\n").append(content).append("\n\n");
                totalLen += content.length();
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("[AI知识库] 检索失败: {}", e.getMessage());
            return "";
        }
    }

    private static class ScoredChunk {
        KnowledgeChunk chunk;
        double score;
        ScoredChunk(KnowledgeChunk chunk, double score) { this.chunk = chunk; this.score = score; }
    }
}
