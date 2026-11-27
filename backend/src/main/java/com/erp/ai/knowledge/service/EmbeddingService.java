// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.service;

import com.erp.ai.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 向量化服务：调用DashScope text-embedding-v3生成1024维向量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // yml兜底：数据库未配置QWEN Key时使用（application.yml: ai.qwen.api-key=${QWEN_API_KEY:}）
    @Value("${ai.qwen.api-key:}")
    private String qwenApiKeyFallback;
    @Value("${ai.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qwenBaseUrlFallback;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String EMBEDDING_MODEL = "text-embedding-v3";

    /**
     * 批量生成向量
     */
    public List<double[]> embed(List<String> texts) throws Exception {
        String apiKey = aiConfigService.getChannelApiKey("QWEN");
        // 数据库未配置QWEN Key时，回退到application.yml的QWEN_API_KEY环境变量
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = qwenApiKeyFallback;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("通义千问通道未配置 API Key，无法生成向量。请在【小助手右上角 ⚙️ 设置 → 内置服务商「通义千问」→ 配置】中填入阿里云百炼（sk- 开头）的 Key");
        }
        String baseUrl = aiConfigService.getChannelConfig("QWEN", "base-url", qwenBaseUrlFallback);

        List<double[]> result = new ArrayList<>();
        // DashScope embedding 支持批量，每次最多25条
        int batchSize = 25;
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            result.addAll(embedBatch(batch, apiKey, baseUrl));
        }
        return result;
    }

    private List<double[]> embedBatch(List<String> texts, String apiKey, String baseUrl) throws Exception {
        String bodyJson = objectMapper.writeValueAsString(java.util.Map.of(
                "model", EMBEDDING_MODEL,
                "input", texts
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Embedding调用失败(" + response.code() + "): " + respBody);
            }
            JsonNode root = objectMapper.readTree(respBody);
            List<double[]> batchResult = new ArrayList<>();
            for (JsonNode item : root.get("data")) {
                JsonNode emb = item.get("embedding");
                double[] vec = new double[emb.size()];
                for (int j = 0; j < emb.size(); j++) vec[j] = emb.get(j).asDouble();
                batchResult.add(vec);
            }
            return batchResult;
        }
    }

    /**
     * 余弦相似度
     */
    public double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
