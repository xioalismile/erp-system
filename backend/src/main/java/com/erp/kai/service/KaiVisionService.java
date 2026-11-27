package com.erp.kai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 图片识别（2026-10-19 新增）。
 * 口径：用客户当前所选模型看图（自带模型优先，未配置回退平台默认）——与 2026-09-28
 * 「图片识别用你选的那个模型」口径一致；识别按真实 tokens 计入用量并扣积分（流水备注「图片识别」），
 * 自带模型免费规则照常生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiVisionService {

    private final KaiModelService modelService;
    private final KaiCreditService creditService;
    private final KaiUsageService usageService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    public record VisionResult(String text, long promptTokens, long completionTokens,
                               long totalTokens, boolean ownModel, String modelName, BigDecimal charged) {}

    /** 识别一张图片：返回文字描述；失败抛中文异常（不扣积分）。 */
    public VisionResult recognize(Long kaiUserId, Long modelProviderId, String fileName, byte[] bytes) {
        KaiModelService.VisionTarget t = modelService.resolveVisionTarget(kaiUserId, modelProviderId);
        if (t == null || isBlank(t.baseUrl()) || isBlank(t.model())) {
            throw new IllegalArgumentException("暂时没有可用的模型来识别图片——请先在「设置 → 我的模型」配置一个模型，或联系管理员开通内置模型");
        }
        String dataUrl = "data:" + mimeOf(fileName) + ";base64," + Base64.getEncoder().encodeToString(bytes);
        try {
            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", dataUrl);
            Map<String, Object> imgPart = new LinkedHashMap<>();
            imgPart.put("type", "image_url");
            imgPart.put("image_url", imageUrl);
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "请完整识别这张图片的内容：文字原样抄录、表格转成文本、图形用一句话说明。直接输出识别结果，不要解释。");
            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", List.of(textPart, imgPart));
            Map<String, Object> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是图片识别助手，把图片内容转成文字交给后续业务处理。");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", t.model());
            body.put("messages", List.of(sysMsg, userMsg));
            body.put("temperature", 0.2);
            // 图片识别输出拉满（2026-10-21）：原 2000 会被模型的思考内容吃掉导致正文截断
            body.put("max_tokens", 8192);

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(t.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json");
            if (!isBlank(t.apiKey())) rb.header("Authorization", "Bearer " + t.apiKey());
            rb.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                    .build().send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalArgumentException(upstreamError(resp.body(), resp.statusCode()));
            }
            JsonNode root = mapper.readTree(resp.body());
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new IllegalArgumentException("模型没有返回图片识别结果，请换个说法重试或更换模型");
            }
            JsonNode usage = root.path("usage");
            long p = usage.path("prompt_tokens").asLong(0);
            long c = usage.path("completion_tokens").asLong(0);
            long total = usage.path("total_tokens").asLong(p + c);
            usageService.collect(kaiUserId, p, c, total);
            BigDecimal charged = creditService.consume(kaiUserId, p, c, total, t.model(), t.ownModel(), false, "图片识别", t.builtinRate());
            return new VisionResult(text, p, c, total, t.ownModel(), t.model(), charged);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[小楷独立版] 图片识别失败: {}", e.getMessage());
            throw new IllegalArgumentException("图片识别失败：" + e.getMessage());
        }
    }

    /** 上游报错转用户可读中文（含"不支持图片/视觉"的换模型指引）。 */
    private String upstreamError(String body, int status) {
        String detail = "";
        try {
            detail = mapper.readTree(body).path("error").path("message").asText("");
        } catch (Exception ignore) { }
        if (detail.isBlank()) detail = body == null ? "" : body;
        String lower = detail.toLowerCase();
        if (lower.contains("image") || lower.contains("vision") || lower.contains("multimodal")
                || lower.contains("not support") || lower.contains("unsupported")) {
            return "当前模型不支持看图——请到「设置 → 我的模型」换一个支持视觉的模型后重试（上游返回：" + snippet(detail) + "）";
        }
        return "识别服务返回错误（" + status + "）：" + snippet(detail);
    }

    private String snippet(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }

    private String mimeOf(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
