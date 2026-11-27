// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文本切片服务：按段落+固定长度切片，带重叠窗口
 */
@Slf4j
@Service
public class TextChunkerService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // 先按段落拆分
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder buffer = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (buffer.length() + para.length() + 2 <= CHUNK_SIZE) {
                if (buffer.length() > 0) buffer.append("\n\n");
                buffer.append(para);
            } else {
                // 当前段落太长，单独切片
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString().trim());
                    // 重叠：保留末尾的 CHUNK_OVERLAP 字符
                    String tail = buffer.toString();
                    buffer = new StringBuilder(tail.length() > CHUNK_OVERLAP
                            ? tail.substring(tail.length() - CHUNK_OVERLAP) : tail);
                }
                if (para.length() <= CHUNK_SIZE) {
                    buffer.append(para);
                } else {
                    // 超长段落按字符硬切
                    for (int i = 0; i < para.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                        int end = Math.min(i + CHUNK_SIZE, para.length());
                        chunks.add(para.substring(i, end));
                    }
                }
            }
        }
        if (buffer.length() > 0) chunks.add(buffer.toString().trim());
        return chunks;
    }

    /**
     * 从切片文本中提取关键词（简单策略：取高频中文词）
     */
    public String extractKeywords(String text) {
        if (text == null || text.length() < 10) return "";
        // 简单按标点和空格分词，取长度>=2的词
        String[] tokens = text.split("[\\s,，。、；;:：!?！？()（）\\[\\]【】\"'`]+");
        return Arrays.stream(tokens)
                .filter(t -> t.length() >= 2 && t.length() <= 8)
                .distinct()
                .limit(10)
                .collect(Collectors.joining(","));
    }
}
