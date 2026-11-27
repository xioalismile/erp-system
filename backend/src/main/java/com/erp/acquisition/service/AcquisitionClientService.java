package com.erp.acquisition.service;

import com.erp.acquisition.entity.AcquisitionTask;
import com.erp.config.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 调用外部独立采集服务（Node.js服务）的客户端
 * 默认地址: http://localhost:3005
 * 可通过配置项 erp.acquisition.service-url 修改
 */
@Slf4j
@Service
public class AcquisitionClientService {

    @Value("${erp.acquisition.service-url:http://localhost:3005}")
    private String serviceUrl;

    private OkHttpClient httpClient;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 触发采集
     * POST /api/capture/start
     *
     * @param task 采集任务
     * @param callerToken 启动此采集任务的前端用户 JWT token（用于采集服务定期校验用户登录态，
     *                    如果用户登录过期，采集服务应停止采集）
     * @return 采集ID（captureId）
     */
    public String triggerCapture(AcquisitionTask task, String callerToken) {
        String url = serviceUrl + "/api/capture/start";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("taskId", task.getTaskId());
            body.put("taskNo", task.getTaskNo());
            // platform字段的映射：qichacha -> qcc
            String platform = task.getPlatform();
            String qccPlatform = "qcc";
            if ("tianyancha".equals(platform)) qccPlatform = "tyc";
            else if ("aiqicha".equals(platform)) qccPlatform = "aqc";
            else if ("gsxt".equals(platform)) qccPlatform = "gsxt";
            else if ("fengniao".equals(platform)) qccPlatform = "fnb";  // 风鸟
            body.put("platform", qccPlatform);
            // keywords字段：数据库里存的是JSON数组字符串（如 ["制造业","软件"]）
            // 需要解析成List再传给采集服务，否则会被再次转义为字符串
            java.util.List<String> keywordList = parseKeywords(task.getKeywords());
            body.put("keywords", keywordList);
            body.put("industry", task.getIndustry());
            body.put("area", task.getArea());
            body.put("maxPages", task.getMaxPages() != null ? task.getMaxPages() : 5);
            body.put("maxCompanies", task.getMaxCompanies() != null ? task.getMaxCompanies() : 50);
            body.put("loginCookie", task.getLoginCookie());
            // 前端用户 token（用于采集服务校验用户登录态，登录过期则停止采集）
            body.put("callerToken", callerToken != null ? callerToken : "");

            String response = postJson(url, body);
            log.info("触发采集成功: taskNo={} response={}", task.getTaskNo(), response);
            return response;
        } catch (Exception e) {
            log.error("触发采集失败: taskNo={}", task.getTaskNo(), e);
            throw new BusinessException("触发采集失败: " + e.getMessage());
        }
    }

    /**
     * 解析keywords字符串为List
     * 兼容JSON数组字符串 ["a","b"] 和 逗号分隔 "a,b"
     */
    private java.util.List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return java.util.Collections.emptyList();
        }
        String trimmed = keywords.trim();
        if (trimmed.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(trimmed, mapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class));
            } catch (Exception e) {
                log.warn("解析keywords JSON失败，按逗号分隔处理: {}", keywords);
            }
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询采集状态
     * GET /api/capture/{id}/status
     */
    public String getCaptureStatus(String captureId) {
        // TODO: 实现具体状态查询逻辑
        String url = serviceUrl + "/api/capture/" + captureId + "/status";
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BusinessException("查询采集状态失败: HTTP " + response.code() + " body=" + body);
                }
                log.info("查询采集状态: captureId={} response={}", captureId, body);
                return body;
            }
        } catch (IOException e) {
            log.error("查询采集状态IO异常: captureId={}", captureId, e);
            throw new BusinessException("查询采集状态失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("查询采集状态异常: captureId={}", captureId, e);
            throw new BusinessException("查询采集状态失败: " + e.getMessage());
        }
    }

    /**
     * 停止采集
     * POST /api/capture/{id}/stop
     */
    public void stopCapture(String captureId) {
        // TODO: 实现具体停止采集逻辑
        String url = serviceUrl + "/api/capture/" + captureId + "/stop";
        try {
            RequestBody requestBody = RequestBody.create("{}", JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BusinessException("停止采集失败: HTTP " + response.code() + " body=" + body);
                }
                log.info("停止采集成功: captureId={}", captureId);
            }
        } catch (IOException e) {
            log.error("停止采集IO异常: captureId={}", captureId, e);
            throw new BusinessException("停止采集失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("停止采集异常: captureId={}", captureId, e);
            throw new BusinessException("停止采集失败: " + e.getMessage());
        }
    }

    /**
     * 发送POST JSON请求
     */
    private String postJson(String url, Map<String, Object> body) throws IOException {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " body=" + responseBody);
            }
            return responseBody;
        }
    }
}
