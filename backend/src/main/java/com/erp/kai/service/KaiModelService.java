package com.erp.kai.service;

import com.erp.ai.adapter.CustomProviderAdapter;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.service.AiAdapterResolver;
import com.erp.ai.service.AiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 用户级模型服务商配置（FEAT-KAI-STANDALONE-003）。
 * 用户自己填服务商（OpenAI 兼容：调用地址 + 密钥 + 模型清单），密钥加密落库；
 * 对话/文档分析解析模型时**用户配置优先**，未配置则兜底系统默认渠道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiModelService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiCryptoService cryptoService;
    private final KaiCreditService creditService;
    private final AiAdapterResolver adapterResolver;
    private final AiConfigService aiConfigService;

    private static final String SEL = "SELECT id, name, base_url, api_key_enc, models, enabled FROM kai_model_provider WHERE kai_user_id = ?";

    /** 我的模型服务商列表（密钥脱敏）。 */
    public List<Map<String, Object>> list(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(SEL + " ORDER BY id DESC", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("baseUrl", rs.getString("base_url"));
            m.put("models", rs.getString("models"));
            String enc = rs.getString("api_key_enc");
            boolean has = enc != null && !enc.isBlank();
            m.put("hasKey", has);
            m.put("keyMasked", has ? cryptoService.maskTail(safeDecrypt(enc)) : "");
            m.put("enabled", rs.getInt("enabled") == 1);
            out.add(m);
        }, kaiUserId);
        return out;
    }

    private String safeDecrypt(String enc) {
        try {
            return cryptoService.decrypt(enc);
        } catch (Exception e) {
            return "****";
        }
    }

    /** 新建/更新。id=null 新建；密钥留空=保持原值。 */
    public Long save(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name"));
        String baseUrl = str(body.get("baseUrl"));
        String models = str(body.get("models"));
        if (name.isBlank()) throw new IllegalArgumentException("请填写服务商名称");
        if (baseUrl.isBlank()) throw new IllegalArgumentException("请填写调用地址");
        if (models.isBlank()) throw new IllegalArgumentException("请填写模型名（多个用英文逗号分隔）");
        String key = str(body.get("apiKey"));
        if (id == null) {
            if (key.isBlank()) throw new IllegalArgumentException("请填写 API 密钥");
            jdbcTemplate.update(
                    "INSERT INTO kai_model_provider (kai_user_id, name, base_url, api_key_enc, models, enabled) VALUES (?,?,?,?,?,1)",
                    kaiUserId, name.trim(), baseUrl.trim(), cryptoService.encrypt(key.trim()), models.trim());
        } else {
            Integer owner = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kai_model_provider WHERE id = ? AND kai_user_id = ?", Integer.class, id, kaiUserId);
            if (owner == null || owner == 0) throw new IllegalArgumentException("服务商不存在或无权操作");
            if (key.isBlank()) {
                jdbcTemplate.update("UPDATE kai_model_provider SET name=?, base_url=?, models=? WHERE id=?",
                        name.trim(), baseUrl.trim(), models.trim(), id);
            } else {
                jdbcTemplate.update("UPDATE kai_model_provider SET name=?, base_url=?, models=?, api_key_enc=? WHERE id=?",
                        name.trim(), baseUrl.trim(), models.trim(), cryptoService.encrypt(key.trim()), id);
            }
        }
        return id == null ? jdbcTemplate.queryForObject(
                "SELECT id FROM kai_model_provider WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId) : id;
    }

    public void delete(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_model_provider WHERE id = ? AND kai_user_id = ?", id, kaiUserId);
    }

    /**
     * 测试连接（FEAT-KAI-STANDALONE-010）：草稿（未保存）或已存配置都可用。
     * 真实调用一次对方的 /chat/completions（OpenAI 兼容），用第一个模型名发一句测试话，
     * 成功返回应答摘要，失败给中文原因（密钥无效/地址不对/连不上/超时）。
     * 已存连接密钥留空 = 用库里保存的原密钥测试。
     */
    public Map<String, Object> test(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String baseUrl = str(body.get("baseUrl")).trim();
        String models = str(body.get("models")).trim();
        String key = str(body.get("apiKey")).trim();
        if (id != null && key.isBlank()) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT api_key_enc FROM kai_model_provider WHERE id = ? AND kai_user_id = ?", id, kaiUserId);
            if (rows.isEmpty()) throw new IllegalArgumentException("服务商不存在或无权操作");
            key = safeDecrypt(String.valueOf(rows.get(0).get("api_key_enc")));
        }
        if (baseUrl.isBlank()) throw new IllegalArgumentException("请填写调用地址");
        if (models.isBlank()) throw new IllegalArgumentException("请填写模型名");
        if (key.isBlank()) throw new IllegalArgumentException("请填写 API 密钥");
        String model = models.split(",")[0].trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        String url = baseUrl + "/chat/completions";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(Map.of("role", "user", "content", "连接测试，请只回复两个字：正常")));
        payload.put("max_tokens", 16);
        payload.put("temperature", 0);

        long start = System.currentTimeMillis();
        String reply;
        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc == 401 || sc == 403) {
                throw new IllegalArgumentException("API 密钥无效或无权限（对方返回 " + sc + "），请核对密钥");
            }
            if (sc == 404) {
                throw new IllegalArgumentException("对方返回 404：接口地址不对，请检查调用地址（OpenAI 兼容地址通常以 /v1 结尾）");
            }
            if (sc == 429) {
                throw new IllegalArgumentException("对方限流（429），请稍后再试或检查账户额度");
            }
            if (sc < 200 || sc >= 300) {
                String msg = errSnippet(resp.body());
                throw new IllegalArgumentException("对方返回异常（" + sc + "）" + (msg.isEmpty() ? "" : "：" + msg));
            }
            Map<String, Object> parsed = new ObjectMapper().readValue(resp.body(), Map.class);
            List<?> choices = parsed.get("choices") instanceof List<?> l ? l : null;
            if (choices == null || choices.isEmpty()) {
                throw new IllegalArgumentException("对方应答里没有模型回复，请确认该地址是 OpenAI 兼容接口");
            }
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = choice.get("message") instanceof Map<?, ?> m ? m : null;
            Object content = message == null ? null : message.get("content");
            reply = content == null ? "" : String.valueOf(content);
        } catch (java.net.http.HttpConnectTimeoutException e) {
            throw new IllegalArgumentException("连接超时（30 秒无响应），请检查调用地址与网络");
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalArgumentException("对方 30 秒内没有应答，请稍后再试或换一个模型名");
        } catch (java.net.ConnectException e) {
            throw new IllegalArgumentException("无法连接对方服务（" + baseUrl + "），请检查调用地址");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("测试失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        long costMs = System.currentTimeMillis() - start;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "连接成功，模型 " + model + " 已正常应答（用时 " + (costMs / 1000.0) + " 秒）");
        out.put("reply", reply.length() > 100 ? reply.substring(0, 100) + "…" : reply);
        return out;
    }

    /** 从错误响应体里截取可读原因（取 message/error 字段，兜底截前 120 字）。 */
    private String errSnippet(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            Map<String, Object> m = new ObjectMapper().readValue(body, Map.class);
            Object err = m.get("error");
            if (err instanceof Map<?, ?> em && em.get("message") != null) return String.valueOf(em.get("message"));
            if (m.get("message") != null) return String.valueOf(m.get("message"));
        } catch (Exception ignore) {
        }
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }


    public void toggle(Long kaiUserId, Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_model_provider SET enabled=? WHERE id=? AND kai_user_id=?",
                enabled ? 1 : 0, id, kaiUserId);
    }

    /**
     * 解析该用户应使用的模型适配器，并标记是否「用户自带模型」：
     * 用户有启用中的服务商 → 用最新一条（自带模型，按配置免扣积分）；
     * 否则平台默认 DeepSeek（平台计费）；平台密钥未配置时兜底主站 AI 默认渠道。
     */
    public ResolvedModel resolveModel(Long kaiUserId, Long providerId) {
        // 内置模型：前端用负数 id 选择（负号后为内置模型 ID，避免与客户自建服务商主键冲突）；
        // 显式选择却已下架/停用 → 明确中文报错，不静默回退（避免客户以为在用 A 实际扣的是 B）
        if (providerId != null && providerId < 0) {
            BuiltinRow b = builtinById(-providerId);
            if (b == null || b.enabled() != 1) throw new IllegalArgumentException("所选内置模型已下架，请重新选择模型");
            CustomProviderAdapter a = new CustomProviderAdapter(
                    "builtin-" + b.id(), b.name(), b.baseUrl(), safeDecrypt(b.apiKeyEnc()), b.model(), null, false);
            a.setConfigService(aiConfigService);
            return new ResolvedModel(a, false, b.rate());
        }
        if (providerId != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        SEL + " AND enabled = 1 AND id = ?", kaiUserId, providerId);
                if (!rows.isEmpty()) {
                    AiChannelAdapter a = buildUserAdapter(rows.get(0));
                    if (a != null) return new ResolvedModel(a, true);
                }
            } catch (Exception e) {
                log.warn("[小楷独立版] 指定模型服务商解析失败，回退默认: {}", e.getMessage());
            }
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    SEL + " AND enabled = 1 ORDER BY id DESC LIMIT 1", kaiUserId);
            if (!rows.isEmpty()) {
                AiChannelAdapter a = buildUserAdapter(rows.get(0));
                if (a != null) return new ResolvedModel(a, true);
                log.warn("[小楷独立版] 用户模型服务商配置不完整，回退平台默认");
            }
        } catch (Exception e) {
            log.warn("[小楷独立版] 解析用户模型配置失败，回退平台默认: {}", e.getMessage());
        }
        try {
            KaiCreditService.DefaultModel dm = creditService.defaultModel();
            if (dm.usable()) {
                CustomProviderAdapter a = new CustomProviderAdapter(
                        "platform-default", dm.name(), dm.baseUrl(), dm.apiKey(), dm.model(), null, false);
                a.setConfigService(aiConfigService);
                if (a.isEnabled()) return new ResolvedModel(a, false);
                log.warn("[小楷独立版] 平台默认 DeepSeek 配置不完整，回退主站默认渠道");
            }
        } catch (Exception e) {
            log.warn("[小楷独立版] 平台默认模型不可用，回退主站默认渠道: {}", e.getMessage());
        }
        return new ResolvedModel(adapterResolver.resolve(null, null, null), false);
    }

    /** 视觉识别目标（图片识别用）：与 resolveModel 同顺序，返回原始连接信息；全部不可用返回 null。builtinRate=内置模型独立倍率（非内置为 null）。 */
    public record VisionTarget(String baseUrl, String apiKey, String model, String displayName, boolean ownModel,
                               java.math.BigDecimal builtinRate) {
        public VisionTarget(String baseUrl, String apiKey, String model, String displayName, boolean ownModel) {
            this(baseUrl, apiKey, model, displayName, ownModel, null);
        }
    }

    public VisionTarget resolveVisionTarget(Long kaiUserId, Long providerId) {
        if (providerId != null && providerId < 0) {
            BuiltinRow b = builtinById(-providerId);
            if (b == null || b.enabled() != 1) throw new IllegalArgumentException("所选内置模型已下架，请重新选择模型");
            return new VisionTarget(b.baseUrl(), safeDecrypt(b.apiKeyEnc()), b.model(), b.name(), false, b.rate());
        }
        if (providerId != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        SEL + " AND enabled = 1 AND id = ?", kaiUserId, providerId);
                if (!rows.isEmpty()) return toVisionTarget(rows.get(0), true);
            } catch (Exception e) {
                log.warn("[小楷独立版] 指定模型服务商解析（视觉）失败，回退默认: {}", e.getMessage());
            }
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    SEL + " AND enabled = 1 ORDER BY id DESC LIMIT 1", kaiUserId);
            if (!rows.isEmpty()) return toVisionTarget(rows.get(0), true);
        } catch (Exception e) {
            log.warn("[小楷独立版] 用户模型解析（视觉）失败，回退平台默认: {}", e.getMessage());
        }
        try {
            KaiCreditService.DefaultModel dm = creditService.defaultModel();
            if (dm.usable()) {
                return new VisionTarget(dm.baseUrl(), dm.apiKey(), dm.model(), dm.name(), false);
            }
        } catch (Exception e) {
            log.warn("[小楷独立版] 平台默认模型不可用（视觉）: {}", e.getMessage());
        }
        return null;
    }

    private VisionTarget toVisionTarget(Map<String, Object> r, boolean ownModel) {
        String models = String.valueOf(r.getOrDefault("models", ""));
        String first = models.split(",")[0].trim();
        return new VisionTarget(String.valueOf(r.get("base_url")), safeDecrypt(String.valueOf(r.get("api_key_enc"))),
                first, String.valueOf(r.get("name")), ownModel);
    }

    private AiChannelAdapter buildUserAdapter(Map<String, Object> r) {
        List<String> models = new ArrayList<>();
        for (String s : String.valueOf(r.getOrDefault("models", "")).split(",")) {
            if (!s.isBlank()) models.add(s.trim());
        }
        CustomProviderAdapter a = new CustomProviderAdapter(
                String.valueOf(r.get("id")),
                String.valueOf(r.get("name")),
                String.valueOf(r.get("base_url")),
                safeDecrypt(String.valueOf(r.get("api_key_enc"))),
                models.isEmpty() ? "" : models.get(0), null, false);
        a.setConfigService(aiConfigService);
        return a.isEnabled() ? a : null;
    }

    // ================= 内置模型（2026-10-20）：运营统一供模型，客户免配置按积分计费 =================

    /** 内置模型行。 */
    public record BuiltinRow(Long id, String name, String baseUrl, String model, String apiKeyEnc,
                             java.math.BigDecimal rate, int enabled) {}

    private static final String BUILTIN_SEL = "SELECT id, name, base_url, model, api_key_enc, rate, enabled FROM kai_builtin_model";

    private BuiltinRow builtinById(Long id) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(BUILTIN_SEL + " WHERE id = ?", id);
            return rows.isEmpty() ? null : toBuiltinRow(rows.get(0));
        } catch (Exception e) {
            log.warn("[小楷独立版] 读取内置模型失败: {}", e.getMessage());
            return null;
        }
    }

    private BuiltinRow toBuiltinRow(Map<String, Object> r) {
        return new BuiltinRow(((Number) r.get("id")).longValue(),
                String.valueOf(r.get("name")), String.valueOf(r.get("base_url")), String.valueOf(r.get("model")),
                String.valueOf(r.get("api_key_enc")),
                r.get("rate") == null ? java.math.BigDecimal.ONE : new java.math.BigDecimal(String.valueOf(r.get("rate"))),
                ((Number) r.get("enabled")).intValue());
    }

    /** 运营：内置模型列表（密钥只回显尾号）。 */
    public List<Map<String, Object>> listBuiltin() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(BUILTIN_SEL + " ORDER BY id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("baseUrl", rs.getString("base_url"));
            m.put("model", rs.getString("model"));
            String enc = rs.getString("api_key_enc");
            boolean has = enc != null && !enc.isBlank();
            m.put("hasKey", has);
            m.put("keyMasked", has ? cryptoService.maskTail(safeDecrypt(enc)) : "");
            m.put("rate", rs.getBigDecimal("rate"));
            m.put("enabled", rs.getInt("enabled") == 1);
            out.add(m);
        });
        return out;
    }

    /** 客户端：启用中的内置模型（仅 id/名称/模型名，不含密钥）。 */
    public List<Map<String, Object>> listEnabledBuiltin() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(BUILTIN_SEL + " WHERE enabled = 1 ORDER BY id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("model", rs.getString("model"));
            out.add(m);
        });
        return out;
    }

    /**
     * 运营：新增/更新内置模型。
     * 新增：密钥必填（手填，或 reuseFromId 复用已有行密文——同一平台多模型共用密钥）；
     * 更新：密钥留空且未选复用 = 保持原值。倍率 ≥0 默认 1.00。
     */
    public Long saveBuiltin(Map<String, Object> body) {
        Long id = body.get("id") == null || String.valueOf(body.get("id")).isBlank()
                ? null : Long.valueOf(String.valueOf(body.get("id")));
        String name = str(body.get("name"));
        String baseUrl = str(body.get("baseUrl"));
        String model = str(body.get("model"));
        if (name.isBlank()) throw new IllegalArgumentException("请填写模型名称");
        if (baseUrl.isBlank()) throw new IllegalArgumentException("请填写调用地址");
        if (model.isBlank()) throw new IllegalArgumentException("请填写模型名");
        java.math.BigDecimal rate = java.math.BigDecimal.ONE;
        if (body.get("rate") != null && !String.valueOf(body.get("rate")).isBlank()) {
            try {
                rate = new java.math.BigDecimal(String.valueOf(body.get("rate")).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("积分倍率必须是数字");
            }
            if (rate.compareTo(java.math.BigDecimal.ZERO) < 0) throw new IllegalArgumentException("积分倍率不能是负数");
            rate = rate.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        String key = str(body.get("apiKey")).trim();
        Long reuseFrom = body.get("reuseFromId") == null || String.valueOf(body.get("reuseFromId")).isBlank()
                ? null : Long.valueOf(String.valueOf(body.get("reuseFromId")));
        String encToSave = null;
        if (reuseFrom != null) {
            BuiltinRow src = builtinById(reuseFrom);
            if (src == null) throw new IllegalArgumentException("要复用密钥的模型不存在，请重新选择");
            encToSave = src.apiKeyEnc();
        } else if (!key.isBlank()) {
            encToSave = cryptoService.encrypt(key);
        }
        if (id == null) {
            if (encToSave == null) throw new IllegalArgumentException("请填写 API 密钥，或选择复用已有模型密钥");
            int enabled = body.get("enabled") == null || "true".equalsIgnoreCase(String.valueOf(body.get("enabled"))) ? 1 : 0;
            jdbcTemplate.update("INSERT INTO kai_builtin_model (name, base_url, model, api_key_enc, rate, enabled) VALUES (?,?,?,?,?,?)",
                    name.trim(), baseUrl.trim(), model.trim(), encToSave, rate, enabled);
            return jdbcTemplate.queryForObject("SELECT id FROM kai_builtin_model ORDER BY id DESC LIMIT 1", Long.class);
        }
        BuiltinRow cur = builtinById(id);
        if (cur == null) throw new IllegalArgumentException("内置模型不存在或已删除");
        String finalEnc = encToSave != null ? encToSave : cur.apiKeyEnc();
        int enabled = body.get("enabled") == null ? cur.enabled()
                : ("true".equalsIgnoreCase(String.valueOf(body.get("enabled"))) ? 1 : 0);
        jdbcTemplate.update("UPDATE kai_builtin_model SET name=?, base_url=?, model=?, api_key_enc=?, rate=?, enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name.trim(), baseUrl.trim(), model.trim(), finalEnc, rate, enabled, id);
        return id;
    }

    /** 运营：删除内置模型（历史流水与调用明细保留）。 */
    public void deleteBuiltin(Long id) {
        jdbcTemplate.update("DELETE FROM kai_builtin_model WHERE id = ?", id);
    }

    /** 运营：启停内置模型（停用后客户下拉不可见，已选中该模型的对话报中文提示）。 */
    public void toggleBuiltin(Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_builtin_model SET enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id = ?",
                enabled ? 1 : 0, id);
    }

    /** 解析适配器（兼容旧调用；不关心是否自带模型时使用）。 */
    public AiChannelAdapter resolveAdapter(Long kaiUserId) {
        return resolveModel(kaiUserId, null).adapter();
    }

    /** 指定服务商解析适配器（聊天页模型选择用）。 */
    public AiChannelAdapter resolveAdapter(Long kaiUserId, Long providerId) {
        return resolveModel(kaiUserId, providerId).adapter();
    }

    /** 解析结果：adapter + 是否用户自带模型（自带模型按配置免扣积分）+ 内置模型独立倍率（非内置为 null）。 */
    public record ResolvedModel(AiChannelAdapter adapter, boolean ownModel, java.math.BigDecimal builtinRate) {
        public ResolvedModel(AiChannelAdapter adapter, boolean ownModel) { this(adapter, ownModel, null); }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
