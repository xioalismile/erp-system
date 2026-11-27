package com.erp.kai.service.adapter;

import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.service.KaiCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 通用 REST 适配器（FEAT-KAI-STANDALONE-001）。
 * 面向"客户自研/杂牌系统给了 API 文档"的场景：按连接配置发标准 HTTP 请求。
 *
 * 附加配置 extra_config（JSON）约定：
 * {
 *   "endpoints": {
 *     "material": {"list": "/api/materials", "create": "/api/materials"},
 *     "customer": {"list": "/api/customers", "create": "/api/customers"},
 *     "sale_order": {"list": "/api/orders", "create": "/api/orders"},
 *     "login": "/api/login"                       // 鉴权方式=LOGIN_TOKEN 时必填
 *   },
 *   "fieldMap": { "material": {"name": "matName"} }   // 可选：小楷字段 → 对方字段
 * }
 *
 * 响应约定：数组 或 {"data": [...]}；开单/建档返回含 id/code 的 JSON 对象。
 * 对方接口不符合约定时，错误信息会用中文说明缺什么配置，便于照文档补配。
 */
@Slf4j
@Component
public class GenericRestAdapter implements KaiExtAdapter {

    private final KaiCryptoService cryptoService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenericRestAdapter(KaiCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public String type() {
        return "GENERIC_REST";
    }

    @Override
    public Map<String, Object> testConnection(KaiExtConnection conn) {
        Map<String, Object> endpoints = endpoints(conn);
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("请在连接的「附加配置」里先按文档填写各对象的接口地址（endpoints）");
        }
        // 探活：取第一个 list 端点发一次 GET
        for (Object o : endpoints.values()) {
            if (o instanceof Map<?, ?> ep && ep.get("list") != null) {
                String body = http(conn, "GET", String.valueOf(ep.get("list")), null);
                // 对方用 HTTP 200 包业务错误时如实报错（实锤：gzzd 错密钥返回 {"error":"bad authId or authKey","code":400}）
                // 成功业务码两种口径都认：200 与 0（大量系统用 0 表示成功，如云枢实锤 {"code":0,...}）
                Object bizCode = businessCode(body);
                if (bizCode instanceof Number && ((Number) bizCode).intValue() != 200 && ((Number) bizCode).intValue() != 0) {
                    throw new IllegalArgumentException("对方系统校验未通过（业务码 " + bizCode + "）：" + truncate(body));
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("message", "连接可通（已按文档地址成功调用一次查询接口）");
                out.put("preview", body.length() > 300 ? body.substring(0, 300) + "…" : body);
                return out;
            }
        }
        throw new IllegalArgumentException("附加配置里至少要给一个对象配置 list（查询）接口地址");
    }

    /** 读响应体里的业务码字段（code/status），非数值返回 null（无包装或纯数组视为无业务码）。 */
    private Object businessCode(String body) {
        try {
            Object json = mapper.readValue(body, Object.class);
            if (json instanceof Map<?, ?> m) {
                Object code = m.get("code") != null ? m.get("code") : m.get("status");
                return code instanceof Number ? code : null;
            }
        } catch (Exception ignore) {
            // 非 JSON 按无业务码处理（后续解析会给出中文指引）
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> listRecords(KaiExtConnection conn, String objectType, Map<String, Object> filters) {
        String path = endpoint(conn, objectType, "list");
        String url = path + (path.contains("?") ? "&" : "?") + "keyword="
                + java.net.URLEncoder.encode(filters == null || filters.get("keyword") == null ? ""
                        : String.valueOf(filters.get("keyword")), StandardCharsets.UTF_8);
        String body = http(conn, "GET", url, null);
        // 响应侧反向映射：按 fieldMap 把对方字段名自动转回小楷标准字段名（FEAT-KAI-STANDALONE-002）
        return applyReverseFieldMap(conn, objectType, extractRows(body, objectType));
    }

    /** fieldMap 反向应用：对方字段 → 小楷字段（只转映射过的键，其余原样保留）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> applyReverseFieldMap(KaiExtConnection conn, String objectType,
                                                           List<Map<String, Object>> rows) {
        Map<String, Object> extra = extra(conn);
        Object fm = extra.get("fieldMap");
        if (!(fm instanceof Map<?, ?> fmMap)) return rows;
        Object per = ((Map<String, Object>) fmMap).get(objectType);
        if (!(per instanceof Map<?, ?> perMap) || perMap.isEmpty()) return rows;
        // 反转：对方字段名 → 小楷字段名
        Map<String, String> reverse = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) perMap).entrySet()) {
            reverse.put(String.valueOf(e.getValue()), String.valueOf(e.getKey()));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String key = reverse.getOrDefault(e.getKey(), e.getKey());
                mapped.put(key, e.getValue());
            }
            out.add(mapped);
        }
        return out;
    }

    @Override
    public Map<String, Object> createRecords(KaiExtConnection conn, String objectType, List<Map<String, Object>> records) {
        String path = endpoint(conn, objectType, "create");
        List<Map<String, Object>> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> rec : records) {
            i++;
            try {
                String resp = http(conn, "POST", path, applyFieldMap(conn, objectType, rec));
                ensureBizOk(resp);
                Map<String, Object> saved = firstObject(resp);
                Map<String, Object> ok = new LinkedHashMap<>(rec);
                ok.put("result", "对方系统返回成功" + (saved.isEmpty() ? "" : "：" + saved));
                success.add(ok);
            } catch (Exception e) {
                String name = rec.get("name") == null ? "(未知名)" : String.valueOf(rec.get("name"));
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("index", i);
                f.put("name", name);
                f.put("reason", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", records.size());
        out.put("successCount", success.size());
        out.put("failCount", failed.size());
        out.put("success", success);
        out.put("failed", failed);
        return out;
    }

    @Override
    public Map<String, Object> createBill(KaiExtConnection conn, Map<String, Object> bill) {
        String path = endpoint(conn, "sale_order", "create");
        String resp = http(conn, "POST", path, applyFieldMap(conn, "sale_order", bill));
        ensureBizOk(resp);
        Map<String, Object> saved = firstObject(resp);
        Map<String, Object> out = new LinkedHashMap<>(bill);
        out.putAll(saved);
        out.put("result", "对方系统已接受开单请求（返回：" + (saved.isEmpty() ? resp : saved) + "）");
        return out;
    }

    @Override
    public Map<String, Object> executeAction(KaiExtConnection conn, String action, Map<String, Object> params) {
        Map<String, Object> endpoints = endpoints(conn);
        Object actionPath = endpoints.get("actions");
        if (!(actionPath instanceof Map<?, ?> acts) || acts.get(action) == null) {
            throw new IllegalArgumentException("请在连接的「附加配置」endpoints.actions 里配置动作「" + action + "」的接口地址");
        }
        String path = String.valueOf(((Map<?, ?>) acts).get(action));
        String resp = http(conn, "POST", path, params);
        ensureBizOk(resp);
        Map<String, Object> out = new LinkedHashMap<>(params);
        out.put("result", "对方系统已接受动作「" + action + "」（返回：" + resp + "）");
        return out;
    }

    @Override
    public Map<String, String> supportedObjectTypes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("material", "物料");
        m.put("customer", "客户");
        m.put("supplier", "供应商");
        m.put("sale_order", "销售订单");
        return m;
    }

    @Override
    public Map<String, String> writableFields(String objectType) {
        // 通用 REST 的字段以对方接口文档为准；这里给通用骨架，具体以 fieldMap 映射
        Map<String, String> m = new LinkedHashMap<>();
        m.put("*", "字段以对方接口文档为准，可在连接的「附加配置」fieldMap 里把小楷字段名映射为对方字段名");
        return m;
    }

    // ---------------- 内部：配置解析与 HTTP ----------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> endpoints(KaiExtConnection conn) {
        Map<String, Object> extra = extra(conn);
        Object ep = extra.get("endpoints");
        return ep instanceof Map ? (Map<String, Object>) ep : new LinkedHashMap<>();
    }

    private String endpoint(KaiExtConnection conn, String objectType, String action) {
        Map<String, Object> endpoints = endpoints(conn);
        Object ep = endpoints.get(objectType);
        if (!(ep instanceof Map<?, ?> m) || m.get(action) == null) {
            throw new IllegalArgumentException("请在连接的「附加配置」endpoints." + objectType
                    + " 里配置 " + action + " 接口地址（照对方 API 文档填）");
        }
        return String.valueOf(m.get(action));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extra(KaiExtConnection conn) {
        if (conn.getExtraConfig() == null || conn.getExtraConfig().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(conn.getExtraConfig(), Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyFieldMap(KaiExtConnection conn, String objectType, Map<String, Object> payload) {
        Map<String, Object> extra = extra(conn);
        Object fm = extra.get("fieldMap");
        if (!(fm instanceof Map<?, ?> fmMap)) return payload;
        Object per = ((Map<String, Object>) fmMap).get(objectType);
        if (!(per instanceof Map<?, ?> perMap)) return payload;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            Object mapped = ((Map<?, ?>) perMap).get(e.getKey());
            out.put(mapped != null ? String.valueOf(mapped) : e.getKey(), e.getValue());
        }
        return out;
    }

    private String http(KaiExtConnection conn, String method, String pathOrUrl, Map<String, Object> payload) {
        String base = conn.getBaseUrl() == null ? "" : conn.getBaseUrl().replaceAll("/+$", "");
        String rawUrl = pathOrUrl.startsWith("http") ? pathOrUrl : base + (pathOrUrl.startsWith("/") ? "" : "/") + pathOrUrl;
        // 密钥对拼地址（API_KEY_PAIR）：authId/authKey 以查询参数拼到请求地址；错误提示用不含密钥的原始地址，防密钥泄漏
        String url = "API_KEY_PAIR".equals(conn.getAuthType()) ? appendPairAuth(conn, rawUrl) : rawUrl;
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(conn.getTimeoutSeconds() == null ? 30 : conn.getTimeoutSeconds()));
            applyAuth(conn, rb, url);
            if (payload != null) {
                rb.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            } else {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = HttpClient.newHttpClient().send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IllegalArgumentException("对方系统返回 " + resp.statusCode()
                        + (resp.body() == null || resp.body().isBlank() ? "" : "：" + truncate(resp.body())));
            }
            return resp.body() == null ? "" : resp.body();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("调用对方系统失败（" + rawUrl + "）：" + e.getMessage());
        }
    }

    /** 密钥对拼地址：把凭据里的 authId/authKey 作为查询参数拼到请求地址（URL 编码）。
     * 凭据 JSON {"authId":"...","authKey":"..."}（分框：账号框=authId，密码框=authKey）；缺失给中文指引。 */
    private String appendPairAuth(KaiExtConnection conn, String url) {
        String credential = conn.getCredentialEnc() == null ? "" : cryptoService.decrypt(conn.getCredentialEnc());
        String authId = "";
        String authKey = "";
        if (credential.startsWith("{")) {
            try {
                Map<?, ?> m = mapper.readValue(credential, Map.class);
                authId = m.get("authId") == null ? "" : String.valueOf(m.get("authId"));
                authKey = m.get("authKey") == null ? "" : String.valueOf(m.get("authKey"));
            } catch (Exception ignore) {
                // 非 JSON 按缺失处理，走下方中文指引
            }
        }
        if (authId.isBlank() || authKey.isBlank()) {
            throw new IllegalArgumentException("鉴权方式为「密钥对拼地址」时，请在账号框填 authId、密码框填 authKey（编辑时留空 = 保持原值）");
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "authId=" + java.net.URLEncoder.encode(authId, StandardCharsets.UTF_8)
                + "&authKey=" + java.net.URLEncoder.encode(authKey, StandardCharsets.UTF_8);
    }

    private void applyAuth(KaiExtConnection conn, HttpRequest.Builder rb, String url) {
        String credential = conn.getCredentialEnc() == null ? "" : cryptoService.decrypt(conn.getCredentialEnc());
        switch (conn.getAuthType() == null ? "API_KEY" : conn.getAuthType()) {
            case "API_KEY" -> {
                if (!credential.isBlank()) rb.header("X-API-Key", plainOrField(credential, "key"));
            }
            case "BEARER_TOKEN" -> {
                if (!credential.isBlank()) rb.header("Authorization", "Bearer " + plainOrField(credential, "token"));
            }
            case "BASIC" -> {
                // 凭据 JSON {"username","password"}（分框新格式）或旧格式 "账号:密码"
                String raw = plainOrField(credential, "username:password");
                String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                rb.header("Authorization", "Basic " + encoded);
            }
            case "LOGIN_TOKEN" -> {
                Map<String, Object> endpoints = endpoints(conn);
                Object loginPath = endpoints.get("login");
                if (loginPath == null) {
                    throw new IllegalArgumentException("鉴权方式为「登录换令牌」时，请在附加配置 endpoints.login 里配置登录接口地址");
                }
                String token = loginForToken(conn, String.valueOf(loginPath), credential);
                rb.header("Authorization", "Bearer " + token);
            }
            case "API_KEY_PAIR" -> {
                // 密钥对拼地址：authId/authKey 已由 appendPairAuth 拼进请求地址，无需请求头
            }
            default -> { /* 未知方式不带鉴权头 */ }
        }
    }

    /** 凭据兼容：新格式 JSON {"key"/"token"/"username"/"password"} 按字段取；旧格式原样返回。
     * BASIC 的 username:password 组合取 {"username","password"} 拼接。 */
    private String plainOrField(String credential, String field) {
        if (credential.startsWith("{")) {
            try {
                Map<?, ?> m = mapper.readValue(credential, Map.class);
                switch (field) {
                    case "key", "token" -> {
                        Object v = m.get("key") != null ? m.get("key") : m.get("token");
                        return v == null ? "" : String.valueOf(v);
                    }
                    case "username:password" -> {
                        return m.get("username") + ":" + m.get("password");
                    }
                    default -> { /* fallthrough */ }
                }
            } catch (Exception ignore) {
                // 非 JSON 按旧格式处理
            }
        }
        return credential;
    }

    private String loginForToken(KaiExtConnection conn, String loginPath, String credentialJson) {
        try {
            // 凭据格式约定：JSON（账号密码类为 {"username":..,"password":..}；对方字段名自定义时整体原样作为登录请求体）
            Map<?, ?> cred = mapper.readValue(credentialJson, Map.class);
            String base = conn.getBaseUrl() == null ? "" : conn.getBaseUrl().replaceAll("/+$", "");
            String url = loginPath.startsWith("http") ? loginPath : base + (loginPath.startsWith("/") ? "" : "/") + loginPath;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(conn.getTimeoutSeconds() == null ? 30 : conn.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(credentialJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            Object token = tokenOf(body);
            if (token == null) {
                throw new IllegalArgumentException("对方登录接口没有返回令牌（识别位置：顶层/ data / result 的 token、accessToken、access_token），请核对接口文档");
            }
            return String.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("登录换令牌失败，请检查凭据格式与登录接口地址");
        }
    }

    /** 从登录应答里找令牌：顶层 / data / result 三层 × token / accessToken / access_token 三种字段名。 */
    private Object tokenOf(Map<?, ?> body) {
        for (String root : new String[]{"", "data", "result"}) {
            Map<?, ?> m = root.isEmpty() ? body : (body.get(root) instanceof Map<?, ?> mm ? mm : null);
            if (m == null) {
                continue;
            }
            for (String k : new String[]{"token", "accessToken", "access_token"}) {
                Object v = m.get(k);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(String body, String objectType) {
        try {
            Object json = mapper.readValue(body, Object.class);
            List<?> rows = null;
            String bizError = null;
            if (json instanceof List<?> l) {
                rows = l;
            } else if (json instanceof Map<?, ?> m) {
                // 对方把业务错包在 HTTP 200 里（code 非 0/200 且没有数组）：如实报错，不误报"格式不对"
                for (String k : new String[]{"data", "list", "rows", "records", "items", "result", "results"}) {
                    if (m.get(k) instanceof List<?> l && !l.isEmpty()) {
                        rows = l;
                        break;
                    }
                }
                if (rows == null) {
                    Object code = m.get("code");
                    int c = code instanceof Number n ? n.intValue() : 0;
                    if (code != null && c != 0 && c != 200) {
                        bizError = "对方返回业务错误（code=" + c + "）" + (m.get("message") == null ? "" : "：" + m.get("message"));
                    }
                }
            }
            if (rows == null) {
                if (bizError != null) {
                    throw new IllegalArgumentException(bizError);
                }
                throw new IllegalArgumentException("对方查询接口返回里没有记录数组（认得的信封键：data/list/rows/records/items/result/results），无法解析" + objectType + "列表");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : rows) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("对方查询接口返回的不是约定的 JSON 数组（或 {data:[...]}），无法解析" + objectType + "列表");
        }
    }

    @SuppressWarnings("unchecked")
    /** 对方 HTTP 200 包业务错误时如实抛错（10.3 坑同源：错密钥返回 {"error":"bad authId or authKey","code":400}；
     * 写路径（导入/开单/动作）同样必须防"假成功"——否则用户看到写入成功，对方系统实际什么都没落。 */
    private void ensureBizOk(String resp) {
        if (resp == null || resp.isBlank()) throw new IllegalArgumentException("对方系统返回空响应");
        String t = resp.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) return; // 非 JSON 响应交由后续解析
        try {
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(t);
            if (node.isObject()) {
                com.fasterxml.jackson.databind.JsonNode err = node.get("error");
                if (err != null && !err.isNull() && !err.asText("").isBlank()) {
                    throw new IllegalArgumentException("对方系统拒绝：" + truncate(t));
                }
                Object bizCode = businessCode(t);
                if (bizCode instanceof Number && ((Number) bizCode).intValue() != 200 && ((Number) bizCode).intValue() != 0) {
                    throw new IllegalArgumentException("对方系统未接受（业务码 " + bizCode + "）：" + truncate(t));
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ignore) {
            // 业务码解析不出的响应不做判定，交由后续流程
        }
    }

    private Map<String, Object> firstObject(String body) {
        try {
            Object json = mapper.readValue(body, Object.class);
            if (json instanceof Map) {
                Object data = ((Map<String, Object>) json).get("data");
                return data instanceof Map ? (Map<String, Object>) data : (Map<String, Object>) json;
            }
            if (json instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map) {
                return (Map<String, Object>) l.get(0);
            }
        } catch (Exception ignore) {
            // 对方返回非 JSON 时按空处理，调用方会带原始文本
        }
        return new LinkedHashMap<>();
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
