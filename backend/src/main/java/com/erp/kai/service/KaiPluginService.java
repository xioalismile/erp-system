package com.erp.kai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 插件框架（FEAT-KAI-STANDALONE-005）。
 * 插件 = 一个暴露两个接口的 HTTP 服务（OpenAI 兼容工具协议的极简版）：
 *   GET  {base_url}/tools  → [{"name","description","parameters"}, ...]  工具发现
 *   POST {base_url}/call   ← {"tool","args"} → {"ok":true,"data":...}    工具调用
 * 小楷把启用插件的工具并入本轮可用工具，模型调用时经此转发到插件服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiPluginService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiCryptoService cryptoService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SEL = "SELECT id, name, description, base_url, api_key_enc, enabled FROM kai_plugin WHERE kai_user_id = ?";

    public List<Map<String, Object>> list(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(SEL + " ORDER BY id DESC", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            m.put("baseUrl", rs.getString("base_url"));
            String enc = rs.getString("api_key_enc");
            boolean has = enc != null && !enc.isBlank();
            m.put("hasKey", has);
            m.put("keyMasked", has ? mask(enc) : "");
            m.put("enabled", rs.getInt("enabled") == 1);
            out.add(m);
        }, kaiUserId);
        return out;
    }

    private String mask(String enc) {
        try {
            return cryptoService.maskTail(cryptoService.decrypt(enc));
        } catch (Exception e) {
            return "****";
        }
    }

    public Long save(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name"));
        String baseUrl = str(body.get("baseUrl"));
        if (name.isBlank()) throw new IllegalArgumentException("请填写插件名称");
        if (baseUrl.isBlank()) throw new IllegalArgumentException("请填写插件服务地址");
        String key = str(body.get("apiKey"));
        if (id == null) {
            jdbcTemplate.update(
                    "INSERT INTO kai_plugin (kai_user_id, name, description, base_url, api_key_enc, enabled) VALUES (?,?,?,?,?,1)",
                    kaiUserId, name.trim(), str(body.get("description")), baseUrl.trim(),
                    key.isBlank() ? null : cryptoService.encrypt(key.trim()));
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM kai_plugin WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
        }
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_plugin WHERE id=? AND kai_user_id=?", Integer.class, id, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("插件不存在或无权操作");
        if (key.isBlank()) {
            jdbcTemplate.update("UPDATE kai_plugin SET name=?, description=?, base_url=? WHERE id=?",
                    name.trim(), str(body.get("description")), baseUrl.trim(), id);
        } else {
            jdbcTemplate.update("UPDATE kai_plugin SET name=?, description=?, base_url=?, api_key_enc=? WHERE id=?",
                    name.trim(), str(body.get("description")), baseUrl.trim(), cryptoService.encrypt(key.trim()), id);
        }
        return id;
    }

    public void delete(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_plugin WHERE id=? AND kai_user_id=?", id, kaiUserId);
    }

    public void toggle(Long kaiUserId, Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_plugin SET enabled=? WHERE id=? AND kai_user_id=?", enabled ? 1 : 0, id, kaiUserId);
    }

    /** 插件工具发现失败缓存（userId → 失败时间戳）：60 秒内不重试，避免插件不可达拖慢每轮对话。 */
    private final Map<Long, Long> failCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long FAIL_COOLDOWN_MS = 60_000L;
    private static final int TOOLS_TIMEOUT_S = 8;

    /** 拉取该用户全部启用插件的工具清单（含插件来源信息）。失败插件跳过并短期缓存，不阻断对话。 */
    public List<Map<String, Object>> enabledTools(Long kaiUserId) {
        Long lastFail = failCache.get(kaiUserId);
        if (lastFail != null && System.currentTimeMillis() - lastFail < FAIL_COOLDOWN_MS) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> plugins = new ArrayList<>();
        jdbcTemplate.query(SEL + " AND enabled = 1", rs -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", rs.getLong("id"));
            p.put("name", rs.getString("name"));
            p.put("baseUrl", rs.getString("base_url"));
            p.put("api_key_enc", rs.getString("api_key_enc"));
            plugins.add(p);
        }, kaiUserId);
        if (plugins.isEmpty()) return out;
        boolean anyFail = false;
        for (Map<String, Object> p : plugins) {
            try {
                String body = http(String.valueOf(p.get("baseUrl")), "GET", "/tools", null, cred(p), TOOLS_TIMEOUT_S);
                List<?> tools = mapper.readValue(body, List.class);
                for (Object o : tools) {
                    if (o instanceof Map<?, ?> t) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("pluginId", p.get("id"));
                        item.put("pluginName", p.get("name"));
                        item.put("name", t.get("name"));
                        item.put("description", t.get("description"));
                        item.put("parameters", t.get("parameters"));
                        item.put("write", Boolean.parseBoolean(String.valueOf(t.get("write"))));
                        out.add(item);
                    }
                }
                failCache.remove(kaiUserId);
            } catch (Exception e) {
                anyFail = true;
                log.warn("[小楷独立版] 插件 {} 工具发现失败，已跳过: {}", p.get("name"), e.getMessage());
            }
        }
        if (anyFail && out.isEmpty()) {
            failCache.put(kaiUserId, System.currentTimeMillis());
        }
        return out;
    }

    /** 调用插件工具。 */
    public Map<String, Object> call(Long kaiUserId, Long pluginId, String tool, Map<String, Object> args) {
        Map<String, Object> p = jdbcTemplate.queryForMap(
                "SELECT id, name, base_url, api_key_enc FROM kai_plugin WHERE id=? AND kai_user_id=? AND enabled=1",
                pluginId, kaiUserId);
        String body = http(String.valueOf(p.get("base_url")), "POST", "/call",
                Map.of("tool", tool, "args", args == null ? Map.of() : args), cred(p));
        try {
            return mapper.readValue(body, Map.class);
        } catch (Exception e) {
            return Map.of("ok", true, "data", body);
        }
    }

    private String cred(Map<String, Object> p) {
        Object enc = p.get("api_key_enc");
        if (enc == null || String.valueOf(enc).isBlank()) return "";
        try {
            return cryptoService.decrypt(String.valueOf(enc));
        } catch (Exception e) {
            return "";
        }
    }

    private String http(String baseUrl, String method, String path, Object payload, String apiKey) {
        return http(baseUrl, method, path, payload, apiKey, 20);
    }

    private String http(String baseUrl, String method, String path, Object payload, String apiKey, int timeoutSeconds) {
        String base = baseUrl.replaceAll("/+$", "");
        String url = base + (path.startsWith("/") ? "" : "/") + path;
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds));
            if (apiKey != null && !apiKey.isBlank()) rb.header("X-API-Key", apiKey);
            if (payload != null) {
                rb.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            } else {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = HttpClient.newHttpClient().send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IllegalArgumentException("插件服务返回 " + resp.statusCode());
            }
            return resp.body() == null ? "" : resp.body();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("调用插件服务失败（" + url + "）：" + e.getMessage());
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
