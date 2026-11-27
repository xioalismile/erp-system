package com.erp.ai.service;

import com.erp.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSpeechService {

    /**
     * 语音识别固定使用通义千问（阿里云百炼）渠道：
     * ASR 只有百炼提供接口（DeepSeek / 本地 Ollama 均无对应能力），
     * 与"对话用哪个模型"无关——识别结果仍是文字，由前端按普通文本走对话链路。
     */
    private static final String ASR_CHANNEL = "QWEN";
    /** 官方限制：Base64 编码后的音频不能超过 10MB（编码会膨胀约 4/3，源文件按 7MB 预拦） */
    private static final long MAX_SOURCE_BYTES = 7L * 1024 * 1024;

    /**
     * 识别提供方（2026-09-17 新增，sys_config 可切）：
     * - {@link #PROVIDER_QWEN}（默认，= 升级前行为）：走阿里云百炼，需要 Key；
     * - {@link #PROVIDER_LOCAL}：走**服务器自建识别服务**（sherpa-onnx 常驻服务，只监听 127.0.0.1，
     *   **不需要任何 Key、不依赖外网**；用户端零安装）。
     * 配置未填/填错一律按 qwen 处理（fail-safe，保证升级前后行为一致）。
     */
    private static final String PROVIDER_QWEN = "qwen";
    private static final String PROVIDER_LOCAL = "local";
    /** 自建识别服务默认地址（只监听回环，不对外开端口） */
    private static final String DEFAULT_LOCAL_URL = "http://127.0.0.1:6006/asr";
    /** 自建识别超时（秒）：默认 30，允许 5~60 */
    private static final int DEFAULT_LOCAL_TIMEOUT = 30;
    private static final int MIN_LOCAL_TIMEOUT = 5;
    private static final int MAX_LOCAL_TIMEOUT = 60;

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public Result<String> recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("音频文件不能为空");
        }
        if (file.getSize() > MAX_SOURCE_BYTES) {
            return Result.error("录音过长（超过约7MB），请缩短后重试");
        }

        // 识别提供方：local = 服务器自建识别服务（不需要任何 Key）；未配置/其它 = qwen（升级前行为）
        String provider = aiConfigService.getChannelConfig("asr", "provider", PROVIDER_QWEN);
        if (PROVIDER_LOCAL.equalsIgnoreCase(provider == null ? "" : provider.trim())) {
            return recognizeByLocal(file);
        }

        String apiKey = aiConfigService.getChannelApiKey(ASR_CHANNEL);
        if (apiKey == null || apiKey.isBlank()) {
            return Result.error("请先在设置中心配置通义千问 API Key（语音识别依赖通义千问）");
        }

        String baseUrl = aiConfigService.getChannelConfig(ASR_CHANNEL, "base-url",
                "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String model = aiConfigService.getChannelConfig(ASR_CHANNEL, "asr-model", "qwen3-asr-flash");

        try {
            byte[] audio = file.getBytes();
            String mimeType = resolveMimeType(file.getOriginalFilename(), file.getContentType());
            String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audio);

            String result = callQwenAsr(baseUrl, apiKey, model, dataUrl);
            if (result != null && !result.isBlank()) {
                return Result.success(result);
            }
            return Result.error("未识别到有效语音内容");
        } catch (Exception e) {
            log.error("[语音识别] 失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 调用百炼 OpenAI 兼容接口完成语音转文字。
     * 注意：百炼兼容模式没有 /audio/transcriptions 端点（旧实现即因此 404），
     * 正确做法是 POST {baseUrl}/chat/completions，音频以 Base64 Data URL 放在 input_audio 中。
     */
    private String callQwenAsr(String baseUrl, String apiKey, String model, String dataUrl) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        ObjectNode audioPart = content.addObject();
        audioPart.put("type", "input_audio");
        audioPart.putObject("input_audio").put("data", dataUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 只记录状态码与响应正文，绝不打印请求体（内含录音 Base64，会撑爆日志）
        log.info("[语音识别] 渠道={}, 模型={}, API响应状态: {}, body: {}",
                ASR_CHANNEL, model, response.statusCode(),
                response.body() != null && response.body().length() > 200
                        ? response.body().substring(0, 200) + "..." : response.body());

        if (response.statusCode() == 200) {
            return extractTextFromResponse(response.body());
        }
        throw new RuntimeException("API返回错误: " + response.statusCode() + " - " + response.body());
    }

    /** 解析 OpenAI 兼容返回：非流式取 choices[0].message.content */
    private String extractTextFromResponse(String json) {
        try {
            JsonNode content = objectMapper.readTree(json)
                    .path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText("").trim();
            }
            // 兼容多模态形态：content 为 [{text: "..."}] 数组
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    sb.append(part.path("text").asText(""));
                }
                return sb.toString().trim();
            }
            return "";
        } catch (Exception e) {
            log.warn("解析语音识别响应失败", e);
            return "";
        }
    }

    /**
     * 走「服务器自建识别服务」（sherpa-onnx 常驻服务，只监听 127.0.0.1）：
     * 直接把 WAV 字节 POST 给本机服务并取回文本 —— **不需要任何 Key、不依赖外网**。
     * 任何失败都返回**明确中文错误**（不静默、不白屏），且不影响打字与小助手其它功能。
     */
    private Result<String> recognizeByLocal(MultipartFile file) {
        String url = aiConfigService.getChannelConfig("asr", "local.url", DEFAULT_LOCAL_URL);
        if (url == null || url.isBlank()) {
            url = DEFAULT_LOCAL_URL;
        }
        int timeoutSeconds = parseLocalTimeout(
                aiConfigService.getChannelConfig("asr", "local.timeout", String.valueOf(DEFAULT_LOCAL_TIMEOUT)));
        try {
            byte[] audio = file.getBytes();
            // 自建服务只吃标准 WAV（前端已按 16k 单声道 WAV 上传）；不满足时给明确指引，绝不静默
            if (audio.length < 44 || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F') {
                return Result.error("本机语音识别只接受 WAV 音频（当前不是 WAV）。请升级前端或改用通义千问识别");
            }
            long t0 = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "audio/wav")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audio))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long cost = System.currentTimeMillis() - t0;
            String body = response.body() == null ? "" : response.body();
            // 只记状态码/耗时/正文前 200 字（正文不含音频，安全）
            log.info("[语音识别] 本机服务 url={}, 状态={}, 耗时={}ms, body={}",
                    url, response.statusCode(), cost, trimBody(body));
            if (response.statusCode() != 200) {
                return Result.error("本机语音识别服务返回错误(" + response.statusCode() + ")：" + trimBody(body));
            }
            String text = parseLocalText(body);
            if (text == null || text.isBlank()) {
                return Result.error("未识别到有效语音内容");
            }
            return Result.success(text);
        } catch (java.net.ConnectException e) {
            log.warn("[语音识别] 本机服务不可达: {}", url);
            return Result.error("本机语音识别服务未启动或不可达（" + url + "），请联系管理员");
        } catch (java.net.http.HttpTimeoutException e) {
            return Result.error("本机语音识别超时（" + timeoutSeconds + "秒），请缩短录音后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("本机语音识别被中断，请重试");
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Connection refused") || msg.contains("ConnectException")) {
                log.warn("[语音识别] 本机服务不可达: {}", url);
                return Result.error("本机语音识别服务未启动或不可达（" + url + "），请联系管理员");
            }
            log.error("[语音识别] 本机服务调用失败", e);
            return Result.error("本机语音识别失败：" + msg);
        }
    }

    /** 解析自建服务返回：{"text":"...","ms":123} */
    private String parseLocalText(String json) {
        try {
            return objectMapper.readTree(json).path("text").asText("").trim();
        } catch (Exception e) {
            log.warn("解析本机识别响应失败", e);
            return "";
        }
    }

    /** 超时配置：非法值回落默认，并夹在 5~60 秒之间 */
    private int parseLocalTimeout(String value) {
        try {
            int v = Integer.parseInt(String.valueOf(value).trim());
            if (v < MIN_LOCAL_TIMEOUT) return MIN_LOCAL_TIMEOUT;
            if (v > MAX_LOCAL_TIMEOUT) return MAX_LOCAL_TIMEOUT;
            return v;
        } catch (Exception e) {
            return DEFAULT_LOCAL_TIMEOUT;
        }
    }

    /** 错误正文只留前 200 字，避免长响应把提示撑爆 */
    private String trimBody(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    /** Data URL 的媒体类型：优先用上传自带的 Content-Type（浏览器录音为 audio/webm;codecs=opus，需去掉参数） */
    private String resolveMimeType(String fileName, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            String base = contentType.split(";")[0].trim();
            if (base.startsWith("audio/")) return base;
        }
        return getContentType(fileName);
    }

    private String getContentType(String fileName) {
        if (fileName == null) return "audio/webm";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        return "audio/webm";
    }
}
