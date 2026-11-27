package com.erp.kai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 小楷独立版 · MCP 工具（FEAT-KAI-STANDALONE-011）。
 * 设计蓝本：XXL-AI MCP 模块（McpClientTool/McpToolFactory），按本工程零依赖原则自研极简 MCP 客户端：
 *  - 传输 HTTP   = MCP Streamable HTTP（POST JSON-RPC，应答为 JSON 或 SSE 帧，自动识别，跟随 Mcp-Session-Id）
 *  - 传输 SSE    = MCP 旧版 HTTP+SSE（GET 事件流取 endpoint，POST 提交，应答经事件流回流）
 * 工具清单缓存进库（tool_cache），对话轮次只读缓存不做实时握手；保存/测试/手动刷新时才真实连接。
 * 凭据（请求头 JSON）AES-GCM 加密落库，永不回显明文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiMcpService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiCryptoService cryptoService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SEL = "SELECT id, name, url, transport, auth_headers_enc, enabled, require_confirm, tool_cache, last_error, command, args_json, cwd, env_enc FROM kai_mcp_server WHERE kai_user_id = ?";
    private static final int LIST_TIMEOUT_S = 15;
    private static final int CALL_TIMEOUT_S = 45;

    /** 服务端连接缓存（serverId → 客户端），配置指纹变化即重建。 */
    private record ClientHolder(String fingerprint, McpClient client) {}
    private final Map<Long, ClientHolder> clients = new ConcurrentHashMap<>();
    /** 握手失败冷却（serverId → 失败时间）：60 秒内对话轮次不再重试。 */
    private final Map<Long, Long> failAt = new ConcurrentHashMap<>();

    // ---------------- 管理接口 ----------------

    public List<Map<String, Object>> list(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(SEL + " ORDER BY id DESC", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("url", rs.getString("url"));
            m.put("transport", rs.getString("transport"));
            String enc = rs.getString("auth_headers_enc");
            boolean has = enc != null && !enc.isBlank();
            m.put("hasAuth", has);
            m.put("authMasked", has ? "已配置" : "");
            m.put("enabled", rs.getInt("enabled") == 1);
            m.put("requireConfirm", rs.getInt("require_confirm") == 1);
            m.put("confirmWrite", rs.getInt("require_confirm") == 1);
            List<Map<String, Object>> tools = readToolCache(rs.getString("tool_cache"));
            m.put("toolCount", tools.size());
            m.put("toolNames", tools.stream().map(t -> str(t.get("name"))).toList());
            m.put("lastError", rs.getString("last_error"));
            m.put("command", rs.getString("command"));
            m.put("args", rs.getString("args_json"));
            m.put("cwd", rs.getString("cwd"));
            String envE = rs.getString("env_enc");
            m.put("hasEnv", envE != null && !envE.isBlank());
            out.add(m);
        }, kaiUserId);
        return out;
    }

    public Long save(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name")).trim();
        String url = str(body.get("url")).trim();
        String transportRaw = str(body.get("transport")).trim();
        String authHeaders = str(body.get("authHeaders"));
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());
        // 前端字段 confirmWrite / 后端口径 requireConfirm，双向兼容；缺省=开（安全默认）
        Object rcRaw = body.get("requireConfirm") != null ? body.get("requireConfirm") : body.get("confirmWrite");
        boolean requireConfirm = rcRaw == null || Boolean.parseBoolean(rcRaw.toString());
        if (name.isBlank()) throw new IllegalArgumentException("请填写名称");
        // 传输类型归一：STREAMABLE_HTTP→HTTP；含 SSE 即视为 SSE；STDIO=本地进程
        boolean isStdio = transportRaw.toUpperCase().contains("STDIO");
        String transport = isStdio ? "STDIO" : (transportRaw.toUpperCase().contains("SSE") ? "SSE" : "HTTP");
        String command = str(body.get("command")).trim();
        String argsRaw = str(body.get("args"));
        String cwd = str(body.get("cwd")).trim();
        String envRaw = str(body.get("env"));
        String argsJson = null, envEnc = null;
        if (isStdio) {
            if (command.isBlank()) throw new IllegalArgumentException("本地进程型 MCP 必须填写启动命令，如 node D:\\servers\\xxx.js");
            String a = argsRaw.trim();
            if (!a.isBlank()) {
                if (!a.startsWith("[")) throw new IllegalArgumentException("启动参数必须是 JSON 数组，如 [\"-y\",\"server-xxx\"]");
                try { mapper.readTree(a); } catch (Exception e) { throw new IllegalArgumentException("启动参数不是合法 JSON 数组：" + e.getMessage()); }
                argsJson = a;
            }
            String e2 = envRaw.trim();
            if (!e2.isBlank()) {
                if (!e2.startsWith("{")) throw new IllegalArgumentException("环境变量必须是 JSON 对象，如 {\"API_KEY\":\"xxx\"}");
                try { mapper.readTree(e2); } catch (Exception ex) { throw new IllegalArgumentException("环境变量不是合法 JSON 对象：" + ex.getMessage()); }
                envEnc = cryptoService.encrypt(e2); // 环境变量可能含密钥，加密落库
            }
        } else {
            if (url.isBlank()) throw new IllegalArgumentException("请填写服务地址");
        }
        if (!authHeaders.isBlank()) {
            String trimmed = authHeaders.trim();
            if (!trimmed.startsWith("{")) throw new IllegalArgumentException("请求头凭据必须是 JSON，如 {\"Authorization\":\"Bearer xxx\"}");
            try {
                mapper.readTree(trimmed);
            } catch (Exception e) {
                throw new IllegalArgumentException("请求头凭据不是合法 JSON：" + e.getMessage());
            }
        }
        if (id == null) {
            jdbcTemplate.update(
                    "INSERT INTO kai_mcp_server (kai_user_id, name, url, transport, auth_headers_enc, enabled, require_confirm, command, args_json, cwd, env_enc) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    kaiUserId, name, url, transport,
                    authHeaders.isBlank() ? null : cryptoService.encrypt(authHeaders.trim()),
                    enabled ? 1 : 0, requireConfirm ? 1 : 0,
                    isStdio ? command : null, argsJson, isStdio && !cwd.isBlank() ? cwd : null, envEnc);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM kai_mcp_server WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
        }
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_mcp_server WHERE id=? AND kai_user_id=?", Integer.class, id, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("MCP 服务器不存在或无权操作");
        clients.remove(id); // 配置变了，连接重建
        // 凭据/环境变量编辑留空 = 保持原值
        Map<String, Object> old = jdbcTemplate.queryForMap(
                "SELECT auth_headers_enc, env_enc FROM kai_mcp_server WHERE id=?", id);
        String finalAuth = authHeaders.isBlank() ? (String) old.get("auth_headers_enc")
                : cryptoService.encrypt(authHeaders.trim());
        String finalEnv = envRaw.isBlank() ? (String) old.get("env_enc") : envEnc;
        jdbcTemplate.update(
                "UPDATE kai_mcp_server SET name=?, url=?, transport=?, auth_headers_enc=?, enabled=?, require_confirm=?, command=?, args_json=?, cwd=?, env_enc=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name, url, transport, finalAuth, enabled ? 1 : 0, requireConfirm ? 1 : 0,
                isStdio ? command : null, argsJson, isStdio && !cwd.isBlank() ? cwd : null, finalEnv, id);
        return id;
    }

    public void delete(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_mcp_server WHERE id=? AND kai_user_id=?", id, kaiUserId);
        closeClient(id);
    }

    public void toggle(Long kaiUserId, Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_mcp_server SET enabled=? WHERE id=? AND kai_user_id=?", enabled ? 1 : 0, id, kaiUserId);
    }

    /** 草稿测试（未保存也可测）：真实握手并列出工具。HTTP/SSE 走网络，STDIO 直接拉起本机进程。 */
    public Map<String, Object> test(Long kaiUserId, Map<String, Object> body) {
        String url = str(body.get("url")).trim();
        String transportRaw = str(body.get("transport")).trim();
        String authHeaders = str(body.get("authHeaders"));
        boolean isStdio = transportRaw.toUpperCase().contains("STDIO");
        McpClient client;
        if (isStdio) {
            String command = str(body.get("command")).trim();
            if (command.isBlank()) throw new IllegalArgumentException("本地进程型 MCP 必须填写启动命令");
            List<String> args = parseArgsList(str(body.get("args")));
            Map<String, String> env = parseEnv(str(body.get("env")));
            String cwd = str(body.get("cwd")).trim();
            client = new StdioMcpClient(command, args, cwd.isBlank() ? null : cwd, env);
        } else {
            if (url.isBlank()) throw new IllegalArgumentException("请填写服务地址");
            String transport = transportRaw.toUpperCase().contains("SSE") ? "SSE" : "HTTP";
            Map<String, String> headers = parseAuthHeaders(authHeaders);
            client = "SSE".equals(transport) ? new SseClient(url, headers) : new StreamableHttpClient(url, headers);
        }
        try (McpClient c = client) {
            List<Map<String, Object>> tools = c.listTools();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("tools", tools);
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("连接失败：" + friendly(e.getMessage()));
        }
    }

    /** 已存服务器：真实握手刷新工具清单缓存。 */
    public Map<String, Object> refreshTools(Long kaiUserId, Long id) {
        Map<String, Object> row = loadOwned(kaiUserId, id);
        McpClient client = clientOf(id, row);
        try {
            List<Map<String, Object>> tools = client.listTools();
            jdbcTemplate.update("UPDATE kai_mcp_server SET tool_cache=?, last_error=NULL WHERE id=?",
                    mapper.writeValueAsString(tools), id);
            failAt.remove(id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tools", tools);
            return out;
        } catch (Exception e) {
            String msg = friendly(e.getMessage());
            jdbcTemplate.update("UPDATE kai_mcp_server SET last_error=? WHERE id=?",
                    msg.length() > 480 ? msg.substring(0, 480) : msg, id);
            throw new IllegalArgumentException("连接失败：" + msg);
        }
    }

    // ---------------- 对话集成 ----------------

    /**
     * 启用中服务器的工具清单（对话 buildTools 用）：优先读库内缓存，缓存为空时尝试一次真实握手（短超时）。
     * 单个服务器失败只记 last_error 并进入冷却，不阻断对话。
     */
    public List<Map<String, Object>> enabledTools(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(SEL + " AND enabled = 1 ORDER BY id", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("name", rs.getString("name"));
            row.put("url", rs.getString("url"));
            row.put("transport", rs.getString("transport"));
            row.put("authEnc", rs.getString("auth_headers_enc"));
            row.put("requireConfirm", rs.getInt("require_confirm") == 1);
            row.put("toolCache", rs.getString("tool_cache"));
            row.put("lastError", rs.getString("last_error"));
            row.put("command", rs.getString("command"));
            row.put("argsJson", rs.getString("args_json"));
            row.put("cwd", rs.getString("cwd"));
            row.put("envEnc", rs.getString("env_enc"));
            out.add(row);
        }, kaiUserId);
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map<String, Object> row : out) {
            Long id = (Long) row.get("id");
            List<Map<String, Object>> cached = readToolCache((String) row.get("toolCache"));
            if (cached.isEmpty()) {
                Long failed = failAt.get(id);
                if (failed != null && System.currentTimeMillis() - failed < 60_000L) continue;
                try {
                    cached = clientOf(id, row).listTools();
                    jdbcTemplate.update("UPDATE kai_mcp_server SET tool_cache=?, last_error=NULL WHERE id=?",
                            mapper.writeValueAsString(cached), id);
                    failAt.remove(id);
                } catch (Exception e) {
                    failAt.put(id, System.currentTimeMillis());
                    String msg = friendly(e.getMessage());
                    jdbcTemplate.update("UPDATE kai_mcp_server SET last_error=? WHERE id=?",
                            msg.length() > 480 ? msg.substring(0, 480) : msg, id);
                    log.warn("[小楷独立版] MCP {} 工具发现失败，已跳过: {}", row.get("name"), e.getMessage());
                    continue;
                }
            }
            for (Map<String, Object> t : cached) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("serverId", id);
                item.put("serverName", row.get("name"));
                item.put("name", t.get("name"));
                item.put("description", t.get("description"));
                item.put("parameters", t.get("inputSchema") instanceof Map ? t.get("inputSchema") : t.get("parameters"));
                item.put("requireConfirm", row.get("requireConfirm"));
                tools.add(item);
            }
        }
        return tools;
    }

    /** 调用 MCP 工具（读/写同一入口；写操作由对话层确认后才进来）。 */
    public Map<String, Object> call(Long kaiUserId, Long serverId, String tool, Map<String, Object> args) {
        Map<String, Object> row = loadOwnedEnabled(kaiUserId, serverId);
        try {
            String text = clientOf(serverId, row).callTool(tool, args == null ? Map.of() : args);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("data", text == null || text.isBlank() ? "（工具无文本返回）" : text);
            return resp;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("MCP 工具调用失败：" + friendly(e.getMessage()));
        }
    }

    // ---------------- 客户端层 ----------------

    private interface McpClient extends AutoCloseable {
        List<Map<String, Object>> listTools() throws Exception;
        String callTool(String name, Map<String, Object> args) throws Exception;
        @Override void close();
    }

    private McpClient clientOf(Long serverId, Map<String, Object> row) {
        String transport = String.valueOf(row.get("transport"));
        final String fingerprint;
        if ("STDIO".equals(transport)) {
            String command = String.valueOf(row.get("command"));
            String argsJson = (String) row.get("argsJson");
            String cwd = String.valueOf(row.get("cwd"));
            String envEnc = (String) row.get("envEnc");
            fingerprint = "STDIO|" + command + "|" + (argsJson == null ? "" : argsJson) + "|" + cwd + "|" + (envEnc == null ? "" : envEnc.length());
            ClientHolder holder = clients.compute(serverId, (k, old) -> {
                if (old != null && old.fingerprint().equals(fingerprint)) return old;
                if (old != null) try { old.client().close(); } catch (Exception ignore) { }
                McpClient c = new StdioMcpClient(command, parseArgsList(argsJson), cwd == null || cwd.isBlank() || "null".equals(cwd) ? null : cwd, parseEnvEnc(envEnc));
                return new ClientHolder(fingerprint, c);
            });
            return holder.client();
        }
        String url = String.valueOf(row.get("url"));
        String authEnc = (String) row.get("authEnc");
        Map<String, String> headers = parseAuthHeadersEnc(authEnc);
        String fingerprint2 = transport + "|" + url + "|" + (authEnc == null ? "" : authEnc.length()) + "|" + Objects.hashCode(headers);
        ClientHolder holder = clients.compute(serverId, (k, old) -> {
            if (old != null && old.fingerprint().equals(fingerprint2)) return old;
            if (old != null) try { old.client().close(); } catch (Exception ignore) { }
            McpClient c = "SSE".equals(transport) ? new SseClient(url, headers) : new StreamableHttpClient(url, headers);
            return new ClientHolder(fingerprint2, c);
        });
        return holder.client();
    }

    private void closeClient(Long serverId) {
        ClientHolder h = clients.remove(serverId);
        if (h != null) try { h.client().close(); } catch (Exception ignore) { }
    }

    private Map<String, Object> loadOwned(Long kaiUserId, Long id) {
        Map<String, Object> row = new LinkedHashMap<>();
        jdbcTemplate.query(SEL + " AND id = ?", rs -> {
            row.put("id", rs.getLong("id"));
            row.put("name", rs.getString("name"));
            row.put("url", rs.getString("url"));
            row.put("transport", rs.getString("transport"));
            row.put("authEnc", rs.getString("auth_headers_enc"));
            row.put("enabled", rs.getInt("enabled"));
            row.put("requireConfirm", rs.getInt("require_confirm"));
            row.put("toolCache", rs.getString("tool_cache"));
            row.put("command", rs.getString("command"));
            row.put("argsJson", rs.getString("args_json"));
            row.put("cwd", rs.getString("cwd"));
            row.put("envEnc", rs.getString("env_enc"));
        }, kaiUserId, id);
        if (row.isEmpty()) throw new IllegalArgumentException("MCP 服务器不存在或无权操作");
        return row;
    }

    private Map<String, Object> loadOwnedEnabled(Long kaiUserId, Long id) {
        Map<String, Object> row = loadOwned(kaiUserId, id);
        if (!Integer.valueOf(1).equals(((Number) row.get("enabled")).intValue())) {
            throw new IllegalArgumentException("该 MCP 服务器已停用，请先在「MCP 工具」页启用");
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readToolCache(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<?> list = mapper.readValue(json, List.class);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) if (o instanceof Map) out.add((Map<String, Object>) o);
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, String> parseAuthHeadersEnc(String enc) {
        if (enc == null || enc.isBlank()) return Map.of();
        try {
            return parseAuthHeaders(cryptoService.decrypt(enc));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> parseAuthHeaders(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            JsonNode node = mapper.readTree(json);
            Map<String, String> out = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                out.put(e.getKey(), e.getValue().asText(""));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("请求头凭据不是合法 JSON");
        }
    }

    /** stdio 启动参数：JSON 数组文本 → 字符串列表；空 = 空列表；非法给中文报错。 */
    private List<String> parseArgsList(String json) {
        String a = json == null ? "" : json.trim();
        if (a.isBlank()) return new ArrayList<>();
        try {
            JsonNode node = mapper.readTree(a);
            if (!node.isArray()) throw new IllegalArgumentException("启动参数必须是 JSON 数组");
            List<String> out = new ArrayList<>();
            for (JsonNode n : node) out.add(n.asText());
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("启动参数不是合法 JSON 数组：" + e.getMessage());
        }
    }

    /** stdio 环境变量：JSON 对象文本（明文）→ Map；空 = 空 Map。 */
    private Map<String, String> parseEnv(String json) {
        String e = json == null ? "" : json.trim();
        if (e.isBlank()) return new LinkedHashMap<>();
        try {
            JsonNode node = mapper.readTree(e);
            if (!node.isObject()) throw new IllegalArgumentException("环境变量必须是 JSON 对象");
            Map<String, String> out = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> en = it.next();
                out.put(en.getKey(), en.getValue().asText(""));
            }
            return out;
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception ex) {
            throw new IllegalArgumentException("环境变量不是合法 JSON 对象：" + ex.getMessage());
        }
    }

    /** stdio 环境变量（密文列）→ 解密 → Map；解密失败给中文原因（密钥更换场景）。 */
    private Map<String, String> parseEnvEnc(String enc) {
        if (enc == null || enc.isBlank()) return new LinkedHashMap<>();
        try {
            return parseEnv(cryptoService.decrypt(enc));
        } catch (Exception e) {
            throw new IllegalArgumentException("环境变量解密失败（密钥可能已更换），请重新保存该服务器的环境变量");
        }
    }

    private String friendly(String msg) {
        if (msg == null) return "未知错误";
        if (msg.contains("Connection refused")) return "连不上服务（连接被拒绝），请核对地址与端口";
        if (msg.toLowerCase().contains("timeout") || msg.contains("超时")) return "连接超时，请稍后重试或核对地址";
        if (msg.contains("401") || msg.contains("Unauthorized")) return "鉴权失败（401），请核对请求头凭据";
        if (msg.contains("404")) return "地址不存在（404），请核对服务地址（流式HTTP 通常以 /mss 或 /mcp 结尾）";
        return msg;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ================= Streamable HTTP 客户端 =================

    private class StreamableHttpClient implements McpClient {
        private final String url;
        private final Map<String, String> headers;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        private final AtomicLong idGen = new AtomicLong(1);
        private volatile String sessionId;
        private volatile boolean initialized;

        StreamableHttpClient(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }

        private synchronized void initIfNeeded() throws Exception {
            if (initialized) return;
            JsonNode result = rpc("initialize", Map.of(
                    "protocolVersion", "2025-03-26",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "xiaokai-standalone", "version", "1.0")), true);
            if (result == null) throw new IllegalArgumentException("服务未返回初始化结果，可能不是 MCP 服务器");
            initialized = true;
            // initialized 通知（无 id，不应答）
            postRpc(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"), null);
        }

        @Override
        public List<Map<String, Object>> listTools() throws Exception {
            initIfNeeded();
            JsonNode result = rpc("tools/list", Map.of(), false);
            if (result == null) throw new IllegalArgumentException("服务未返回工具清单");
            JsonNode tools = result.path("tools");
            List<Map<String, Object>> out = new ArrayList<>();
            if (tools.isArray()) {
                for (JsonNode t : tools) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t.path("name").asText(""));
                    m.put("description", t.path("description").asText(""));
                    if (t.hasNonNull("inputSchema")) m.put("inputSchema", mapper.convertValue(t.path("inputSchema"), Map.class));
                    out.add(m);
                }
            }
            return out;
        }

        @Override
        public String callTool(String name, Map<String, Object> args) throws Exception {
            initIfNeeded();
            JsonNode result = rpc("tools/call", Map.of("name", name, "arguments", args), false);
            if (result == null) throw new IllegalArgumentException("工具没有返回结果");
            if (result.path("isError").asBoolean(false)) {
                throw new IllegalArgumentException(extractText(result));
            }
            String text = extractText(result);
            return text == null || text.isBlank() ? mapper.writeValueAsString(result) : text;
        }

        private String extractText(JsonNode result) {
            StringBuilder sb = new StringBuilder();
            JsonNode content = result.path("content");
            if (content.isArray()) {
                for (JsonNode c : content) {
                    if ("text".equals(c.path("type").asText())) sb.append(c.path("text").asText()).append("\n");
                }
            }
            return sb.toString().strip();
        }

        /** JSON-RPC 请求：返回 result 节点（通知类返回 null）。应答兼容 JSON 与 SSE 帧两种报文。 */
        private JsonNode rpc(String method, Object params, boolean captureSession) throws Exception {
            long id = idGen.getAndIncrement();
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("jsonrpc", "2.0");
            req.put("id", id);
            req.put("method", method);
            req.put("params", params);
            JsonNode resp = postRpc(req, id);
            if (resp == null) return null;
            if (resp.has("error") && resp.path("error").isObject()) {
                throw new IllegalArgumentException("服务返回错误：" + resp.path("error").path("message").asText("未知"));
            }
            return resp.path("result");
        }

        /** 发送并解析应答；expectId 非 null 时优先匹配该 id 的应答，null 表示通知（不等待应答）。 */
        private JsonNode postRpc(Map<String, Object> payload, Long expectId) throws Exception {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(expectId == null ? 10 : LIST_TIMEOUT_S))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream");
            if (sessionId != null) rb.header("Mcp-Session-Id", sessionId);
            headers.forEach(rb::header);
            rb.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (expectId != null) {
                String sid = resp.headers().firstValue("Mcp-Session-Id").orElse(null);
                if (sid != null) this.sessionId = sid;
            }
            if (resp.statusCode() == 202) return null;
            if (resp.statusCode() >= 400) throw new IllegalArgumentException("HTTP " + resp.statusCode());
            String body = resp.body() == null ? "" : resp.body().strip();
            if (body.isEmpty()) return null;
            List<JsonNode> candidates = new ArrayList<>();
            if (body.startsWith("{")) {
                try { candidates.add(mapper.readTree(body)); } catch (Exception ignore) { }
            }
            if (candidates.isEmpty()) {
                // SSE 帧：取 data: 行逐个尝试
                for (String line : body.split("\n")) {
                    String line2 = line.strip();
                    if (line2.startsWith("data:")) {
                        String data = line2.substring(5).strip();
                        if (data.isEmpty() || "DONE".equalsIgnoreCase(data)) continue;
                        try { candidates.add(mapper.readTree(data)); } catch (Exception ignore) { }
                    }
                }
            }
            JsonNode matched = null;
            for (JsonNode n : candidates) {
                if (expectId != null && n.has("id") && n.path("id").asLong(-1) == expectId) { matched = n; break; }
                if (matched == null && (n.has("result") || n.has("error"))) matched = n;
            }
            return matched;
        }

        @Override public void close() { /* 无状态连接，无需释放 */ }
    }

    // ================= 旧版 HTTP+SSE 客户端 =================

    private class SseClient implements McpClient {
        private final String url;
        private final Map<String, String> headers;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        private final ObjectMapper om = new ObjectMapper();
        private final AtomicLong idGen = new AtomicLong(1000);
        private final Map<Long, CompletableFuture<JsonNode>> waiting = new ConcurrentHashMap<>();
        private volatile String endpoint;
        private volatile Thread reader;
        private volatile boolean closed;
        /** 当前事件名（读流线程独占使用；提为字段是为了在读取线程 lambda 中可变更）。 */
        private String eventName = "message";

        SseClient(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }

        /** 打开事件流并解析 endpoint；应答经事件流回流到 waiting 表。 */
        private synchronized void ensureConnected() throws Exception {
            if (endpoint != null && reader != null && reader.isAlive()) return;
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "text/event-stream");
            headers.forEach(rb::header);
            HttpResponse<java.io.InputStream> resp =
                    http.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) throw new IllegalArgumentException("HTTP " + resp.statusCode());
            BufferedReader br = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
            StringBuilder dataBuf = new StringBuilder();
            this.eventName = "message";
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while (!closed && (line = br.readLine()) != null) {
                        if (line.isBlank()) {
                            String data = dataBuf.toString().strip();
                            dataBuf.setLength(0);
                            if (!data.isEmpty()) dispatch(eventName, data);
                            eventName = "message";
                            continue;
                        }
                        if (line.startsWith(":")) continue;
                        if (line.startsWith("event:")) { eventName = line.substring(6).strip(); continue; }
                        if (line.startsWith("data:")) { dataBuf.append(line.substring(5).strip()); continue; }
                    }
                } catch (Exception ignore) {
                    // 流断开：endpoint 置空，下次调用重连
                } finally {
                    endpoint = null;
                }
            }, "kai-mcp-sse-" + url);
            t.setDaemon(true);
            t.start();
            reader = t;
            // 等 endpoint 事件（最多 10 秒）
            long deadline = System.currentTimeMillis() + 10_000;
            while (endpoint == null && System.currentTimeMillis() < deadline && !closed) {
                Thread.sleep(100);
            }
            if (endpoint == null) throw new IllegalArgumentException("事件流未返回 endpoint，可能不是 MCP SSE 服务器");
        }

        private void dispatch(String event, String data) {
            if ("endpoint".equals(event)) {
                this.endpoint = resolveEndpoint(data);
                return;
            }
            try {
                JsonNode n = om.readTree(data);
                if (n.has("id")) {
                    CompletableFuture<JsonNode> f = waiting.remove(n.path("id").asLong());
                    if (f != null) f.complete(n);
                }
            } catch (Exception ignore) { }
        }

        private String resolveEndpoint(String data) {
            try {
                URI base = URI.create(url);
                URI ep = URI.create(data);
                return ep.isAbsolute() ? data : base.resolve(ep).toString();
            } catch (Exception e) {
                return data;
            }
        }

        private synchronized JsonNode rpc(String method, Object params, boolean notify) throws Exception {
            ensureConnected();
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("jsonrpc", "2.0");
            Long id = null;
            if (!notify) {
                id = idGen.getAndIncrement();
                req.put("id", id);
                req.put("params", params);
            }
            req.put("method", method);
            CompletableFuture<JsonNode> future = null;
            if (id != null) {
                future = new CompletableFuture<>();
                waiting.put(id, future);
            }
            try {
                HttpRequest.Builder rb = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json");
                headers.forEach(rb::header);
                rb.POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(req), StandardCharsets.UTF_8));
                HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) throw new IllegalArgumentException("HTTP " + resp.statusCode());
                // 兼容部分服务直接在 POST 应答体里同步返回结果
                String body = resp.body() == null ? "" : resp.body().strip();
                if (id != null && body.startsWith("{")) {
                    try {
                        JsonNode n = om.readTree(body);
                        if (n.has("id") && n.path("id").asLong(-1) == id) return n;
                    } catch (Exception ignore) { }
                }
                if (id == null) return null;
                JsonNode n = future.get(LIST_TIMEOUT_S, TimeUnit.SECONDS);
                if (n.has("error") && n.path("error").isObject()) {
                    throw new IllegalArgumentException("服务返回错误：" + n.path("error").path("message").asText("未知"));
                }
                return n;
            } catch (Exception e) {
                throw e;
            } finally {
                if (id != null) waiting.remove(id);
            }
        }

        @Override
        public List<Map<String, Object>> listTools() throws Exception {
            rpc("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "xiaokai-standalone", "version", "1.0")), false);
            rpc("notifications/initialized", Map.of(), true);
            JsonNode resp = rpc("tools/list", Map.of(), false);
            JsonNode result = resp == null ? null : resp.path("result");
            if (result == null || result.isMissingNode()) throw new IllegalArgumentException("服务未返回工具清单");
            JsonNode tools = result.path("tools");
            List<Map<String, Object>> out = new ArrayList<>();
            if (tools.isArray()) {
                for (JsonNode t : tools) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t.path("name").asText(""));
                    m.put("description", t.path("description").asText(""));
                    if (t.hasNonNull("inputSchema")) m.put("inputSchema", om.convertValue(t.path("inputSchema"), Map.class));
                    out.add(m);
                }
            }
            return out;
        }

        @Override
        public String callTool(String name, Map<String, Object> args) throws Exception {
            JsonNode resp = rpc("tools/call", Map.of("name", name, "arguments", args), false);
            JsonNode result = resp == null ? null : resp.path("result");
            if (result == null || result.isMissingNode()) throw new IllegalArgumentException("工具没有返回结果");
            if (result.path("isError").asBoolean(false)) {
                StringBuilder esb = new StringBuilder();
                JsonNode content = result.path("content");
                if (content.isArray()) for (JsonNode c : content) esb.append(c.path("text").asText()).append("\n");
                throw new IllegalArgumentException(esb.toString().strip());
            }
            StringBuilder sb = new StringBuilder();
            JsonNode content = result.path("content");
            if (content.isArray()) for (JsonNode c : content) sb.append(c.path("text").asText()).append("\n");
            String text = sb.toString().strip();
            return text.isEmpty() ? om.writeValueAsString(result) : text;
        }

        @Override
        public void close() {
            closed = true;
            if (reader != null) reader.interrupt();
        }
    }

    // ================= stdio 本地进程客户端 =================

    private class StdioMcpClient implements McpClient {
        private final Process process;
        private final java.io.BufferedWriter stdin;
        private final Map<Long, CompletableFuture<JsonNode>> waiting = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(2000);
        private final StringBuilder errTail = new StringBuilder();
        private volatile boolean closed;

        StdioMcpClient(String command, List<String> args, String cwd, Map<String, String> env) {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(command);
                if (args != null) cmd.addAll(args);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (cwd != null) pb.directory(new java.io.File(cwd));
                if (env != null && !env.isEmpty()) pb.environment().putAll(env);
                process = pb.start();
                stdin = new java.io.BufferedWriter(new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                Thread t = new Thread(() -> {
                    try {
                        String line;
                        while (!closed && (line = out.readLine()) != null) {
                            String s = line.strip();
                            if (s.isEmpty()) continue;
                            try {
                                JsonNode n = mapper.readTree(s);
                                if (n.has("id")) {
                                    CompletableFuture<JsonNode> f = waiting.remove(n.path("id").asLong());
                                    if (f != null) f.complete(n);
                                }
                            } catch (Exception ignore) {
                                // 非应答行（服务器日志）忽略
                            }
                        }
                    } catch (Exception ignore) {
                    } finally {
                        waiting.forEach((k, f) -> f.completeExceptionally(new IllegalStateException("MCP 进程已退出")));
                        waiting.clear();
                    }
                }, "kai-mcp-stdio");
                t.setDaemon(true);
                t.start();
                // 错误输出只留尾部，失败时给用户看得懂的原因
                BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                Thread te = new Thread(() -> {
                    try {
                        String line;
                        while ((line = err.readLine()) != null) {
                            synchronized (errTail) {
                                errTail.append(line).append('\n');
                                if (errTail.length() > 800) errTail.delete(0, errTail.length() - 800);
                            }
                        }
                    } catch (Exception ignore) { }
                }, "kai-mcp-stdio-err");
                te.setDaemon(true);
                te.start();
            } catch (Exception e) {
                throw new IllegalArgumentException("无法启动本地 MCP 进程（" + command + "）：" + e.getMessage());
            }
        }

        private String errHint() {
            synchronized (errTail) {
                String s = errTail.toString().strip();
                return s.isEmpty() ? "" : "；进程输出：" + s.substring(Math.max(0, s.length() - 300));
            }
        }

        private synchronized JsonNode rpc(String method, Object params, boolean notify) throws Exception {
            if (closed || !process.isAlive()) {
                throw new IllegalArgumentException("本地 MCP 进程未在运行（可能启动失败或已退出）" + errHint());
            }
            long id = idGen.getAndIncrement();
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("jsonrpc", "2.0");
            req.put("id", id);
            req.put("method", method);
            req.put("params", params);
            CompletableFuture<JsonNode> future = null;
            if (!notify) {
                future = new CompletableFuture<>();
                waiting.put(id, future);
            }
            try {
                stdin.write(mapper.writeValueAsString(req));
                stdin.newLine();
                stdin.flush();
                if (notify) return null;
                JsonNode n = future.get(30, TimeUnit.SECONDS);
                if (n.has("error") && n.path("error").isObject()) {
                    throw new IllegalArgumentException("服务返回错误：" + n.path("error").path("message").asText("未知"));
                }
                return n;
            } catch (java.util.concurrent.TimeoutException te2) {
                throw new IllegalArgumentException("本地 MCP 进程 30 秒未应答" + errHint());
            } finally {
                if (!notify) waiting.remove(id);
            }
        }

        @Override
        public List<Map<String, Object>> listTools() throws Exception {
            rpc("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "xiaokai-standalone", "version", "1.0")), false);
            rpc("notifications/initialized", Map.of(), true);
            JsonNode resp = rpc("tools/list", Map.of(), false);
            JsonNode result = resp == null ? null : resp.path("result");
            if (result == null || result.isMissingNode()) throw new IllegalArgumentException("进程未返回工具清单");
            JsonNode tools = result.path("tools");
            List<Map<String, Object>> out = new ArrayList<>();
            if (tools.isArray()) {
                for (JsonNode t : tools) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t.path("name").asText(""));
                    m.put("description", t.path("description").asText(""));
                    if (t.hasNonNull("inputSchema")) m.put("inputSchema", mapper.convertValue(t.path("inputSchema"), Map.class));
                    out.add(m);
                }
            }
            return out;
        }

        @Override
        public String callTool(String name, Map<String, Object> args) throws Exception {
            JsonNode resp = rpc("tools/call", Map.of("name", name, "arguments", args), false);
            JsonNode result = resp == null ? null : resp.path("result");
            if (result == null || result.isMissingNode()) throw new IllegalArgumentException("工具没有返回结果");
            if (result.path("isError").asBoolean(false)) {
                StringBuilder esb = new StringBuilder();
                JsonNode content = result.path("content");
                if (content.isArray()) for (JsonNode c : content) esb.append(c.path("text").asText()).append("\n");
                throw new IllegalArgumentException(esb.toString().strip());
            }
            StringBuilder sb = new StringBuilder();
            JsonNode content = result.path("content");
            if (content.isArray()) for (JsonNode c : content) sb.append(c.path("text").asText()).append("\n");
            String text = sb.toString().strip();
            return text.isEmpty() ? mapper.writeValueAsString(result) : text;
        }

        @Override
        public void close() {
            closed = true;
            try { process.destroy(); } catch (Exception ignore) { }
        }
    }
}
