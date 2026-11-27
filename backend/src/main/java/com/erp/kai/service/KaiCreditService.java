package com.erp.kai.service;

import com.erp.kai.entity.KaiUser;
import com.erp.kai.mapper.KaiUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 积分钱包（2026-10-18）。
 * 口径：1 积分 = 1000 tokens（可配置）；注册赠送 1000 积分；
 *      用户用自己的模型密钥不扣积分；平台默认 DeepSeek 才扣。
 * 所有配置存 kai_config（键值），未配置时用本类默认值兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiCreditService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiUserMapper kaiUserMapper;
    private final KaiCryptoService cryptoService;

    public static final String K_TOKENS_PER_CREDIT   = "credit.tokens_per_credit";
    public static final String K_REGISTER_GIFT_ON   = "credit.register_gift_enabled";
    public static final String K_REGISTER_GIFT      = "credit.register_gift";
    public static final String K_MONTHLY_GIFT_ON    = "credit.monthly_gift_enabled";
    public static final String K_MONTHLY_GIFT       = "credit.monthly_gift";
    public static final String K_LOW_BALANCE        = "credit.low_balance";
    public static final String K_OWN_MODEL_FREE     = "credit.own_model_free";
    public static final String K_DEEP_MULTIPLIER    = "credit.deep_thinking_multiplier";
    public static final String K_MODEL_RATES        = "credit.model_rates";
    public static final String K_REGISTER_ENABLED   = "credit.register_enabled";
    public static final String K_DEFAULT_NAME       = "model.default.name";
    public static final String K_DEFAULT_BASE_URL   = "model.default.base_url";
    public static final String K_DEFAULT_MODEL      = "model.default.model";
    public static final String K_DEFAULT_API_KEY    = "model.default.api_key";

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    /** 深度思考档内置倍率（2026-10-19 定稿 1.5；标准档恒为 1.0）。 */
    public static final java.math.BigDecimal DEEP_THINKING_MULTIPLIER = new java.math.BigDecimal("1.5");
    static {
        DEFAULTS.put(K_TOKENS_PER_CREDIT, "1000");
        DEFAULTS.put(K_REGISTER_GIFT_ON, "true");
        DEFAULTS.put(K_REGISTER_GIFT, "1000");
        DEFAULTS.put(K_MONTHLY_GIFT_ON, "false");
        DEFAULTS.put(K_MONTHLY_GIFT, "0");
        DEFAULTS.put(K_LOW_BALANCE, "100");
        // 2026-10-21 用户定稿：所有花费都扣积分（自带模型不再免费）
        DEFAULTS.put(K_OWN_MODEL_FREE, "false");
        DEFAULTS.put(K_DEEP_MULTIPLIER, "1.5");
        DEFAULTS.put(K_REGISTER_ENABLED, "true");
        DEFAULTS.put(K_DEFAULT_NAME, "DeepSeek");
        DEFAULTS.put(K_DEFAULT_BASE_URL, "https://api.deepseek.com/v1");
        DEFAULTS.put(K_DEFAULT_MODEL, "deepseek-flash");
        DEFAULTS.put(K_DEFAULT_API_KEY, "");
    }

    /** 读取配置（无记录时用默认值）。 */
    public String get(String key) {
        String def = DEFAULTS.getOrDefault(key, "");
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT config_value FROM kai_config WHERE config_key = ?", String.class, key);
            if (rows.isEmpty()) return def;
            String v = rows.get(0);
            return v == null ? "" : v;
        } catch (Exception e) {
            return def;
        }
    }

    public long getLong(String key) {
        try { return Long.parseLong(get(key).trim()); } catch (Exception e) { return Long.parseLong(DEFAULTS.getOrDefault(key, "0")); }
    }

    public boolean getBool(String key) {
        String v = get(key).trim().toLowerCase();
        if (v.isEmpty()) v = DEFAULTS.getOrDefault(key, "false");
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    public long tokensPerCredit() {
        long v = getLong(K_TOKENS_PER_CREDIT);
        return v <= 0 ? 1000 : v;
    }

    /** 深度思考档倍率（可配置，默认 1.5；小于 1 时按 1 处理，保证不反向打折）。 */
    public java.math.BigDecimal deepThinkingMultiplier() {
        try {
            String raw = get(K_DEEP_MULTIPLIER).trim();
            if (raw.isEmpty()) return DEEP_THINKING_MULTIPLIER;
            java.math.BigDecimal v = new java.math.BigDecimal(raw);
            if (v.compareTo(java.math.BigDecimal.ONE) < 0) return java.math.BigDecimal.ONE;
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return DEEP_THINKING_MULTIPLIER;
        }
    }

    /**
     * 模型积分倍率（2026-10-19 新增）：不同模型消耗不同积分。
     * 配置存储为多行文本「模型名=倍率」（credit.model_rates）；未配置/未命中按 1.0。
     * 实际扣减 = tokens ÷ 每积分tokens × 模型倍率 ×（深度思考轮再乘深度倍率）。
     */
    public java.math.BigDecimal modelRate(String model) {
        BigDecimal rate = modelRateOf(get(K_MODEL_RATES), model);
        return rate == null ? java.math.BigDecimal.ONE : rate;
    }

    /** 按配置文本解析某模型的倍率；未命中返回 null。行格式「模型名=倍率」，# 开头忽略。 */
    private java.math.BigDecimal modelRateOf(String text, String model) {
        if (text == null || text.isBlank() || model == null || model.isBlank()) return null;
        String target = model.trim();
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
            int eq = t.indexOf('=');
            String name = t.substring(0, eq).trim();
            String val = t.substring(eq + 1).trim();
            if (!name.equalsIgnoreCase(target)) continue;
            try {
                java.math.BigDecimal v = new java.math.BigDecimal(val);
                if (v.compareTo(java.math.BigDecimal.ZERO) < 0) return java.math.BigDecimal.ZERO;
                return v.setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                return null; // 该行数值非法视为未配置
            }
        }
        return null;
    }

    // ---------------- 钱包读取 ----------------

    public BigDecimal balance(Long kaiUserId) {
        try {
            BigDecimal b = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(credit_balance,0) FROM kai_user WHERE id = ?", BigDecimal.class, kaiUserId);
            return b == null ? BigDecimal.ZERO : b;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public Map<String, Object> wallet(Long kaiUserId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("balance", balance(kaiUserId));
        out.put("granted", moneyOf(kaiUserId, "credit_granted"));
        out.put("consumed", moneyOf(kaiUserId, "credit_consumed"));
        out.put("tokensPerCredit", tokensPerCredit());
        out.put("lowBalance", getLong(K_LOW_BALANCE));
        out.put("ownModelFree", getBool(K_OWN_MODEL_FREE));
        return out;
    }

    private BigDecimal moneyOf(Long id, String column) {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(" + column + ",0) FROM kai_user WHERE id = ?", BigDecimal.class, id);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 最近积分流水。 */
    public List<Map<String, Object>> ledger(Long kaiUserId, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            jdbcTemplate.query("""
                    SELECT id, change_type, credits, balance_after, tokens, model, remark, created_at
                    FROM kai_credit_ledger WHERE kai_user_id = ? ORDER BY id DESC LIMIT ?
                    """, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("type", rs.getString("change_type"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("balanceAfter", rs.getBigDecimal("balance_after"));
                m.put("tokens", rs.getLong("tokens"));
                m.put("model", rs.getString("model"));
                m.put("remark", rs.getString("remark"));
                m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                out.add(m);
            }, kaiUserId, Math.max(1, Math.min(limit, 100)));
        } catch (Exception e) {
            log.warn("[小楷独立版] 读取积分流水失败: {}", e.getMessage());
        }
        return out;
    }

    /** 用量页数据：钱包 + 最近流水。 */
    public Map<String, Object> overview(Long kaiUserId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(wallet(kaiUserId));
        out.put("ledger", ledger(kaiUserId, 20));
        return out;
    }

    // ---------------- 积分变动 ----------------

    /** 注册赠送（开关关闭或数量 0 时不处理）。 */
    public void grantRegisterGift(Long kaiUserId) {
        if (!getBool(K_REGISTER_GIFT_ON)) return;
        long amount = getLong(K_REGISTER_GIFT);
        if (amount <= 0) return;
        grant(kaiUserId, BigDecimal.valueOf(amount), "REGISTER_GIFT", "注册赠送");
    }

    /** 增加积分（注册赠送 / 每月赠送 / 管理员增加）。 */
    public void grant(Long kaiUserId, BigDecimal amount, String type, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal amt = amount.setScale(4, RoundingMode.HALF_UP);
        try {
            BigDecimal after = jdbcTemplate.queryForObject("""
                    UPDATE kai_user SET credit_balance = COALESCE(credit_balance,0) + ?,
                                        credit_granted = COALESCE(credit_granted,0) + ?
                    WHERE id = ? RETURNING credit_balance
                    """, BigDecimal.class, amt, amt, kaiUserId);
            insertLedger(kaiUserId, type, amt, after == null ? amt : after, 0, null, remark);
        } catch (Exception e) {
            log.warn("[小楷独立版] 赠送积分失败: {}", e.getMessage());
        }
    }

    /** 对话前判定：平台默认模型且余额为 0 → 中文拦截；自带模型免费时直接放行。 */
    public void checkBeforeCall(Long kaiUserId, boolean ownModel) {
        if (ownModel && getBool(K_OWN_MODEL_FREE)) return;
        BigDecimal b = balance(kaiUserId);
        if (b.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("积分不足，请联系管理员充值后再继续对话（1 积分 = "
                    + tokensPerCredit() + " tokens）");
        }
    }

    /**
     * 对话后扣减：按本轮真实 tokens 折算积分，深度思考轮按内置倍率上浮。
     * 自带模型且开启「自带模型免费」→ 扣 0，仅记调用明细。
     * 返回实际扣除积分。
     */
    public BigDecimal consume(Long kaiUserId, long promptTokens, long completionTokens,
                             long totalTokens, String model, boolean ownModel, boolean deepThinking) {
        return consume(kaiUserId, promptTokens, completionTokens, totalTokens, model, ownModel, deepThinking, null);
    }

    /** 同上，remark 可指定流水备注（如「图片识别」）；null 时按档位默认备注。 */
    public BigDecimal consume(Long kaiUserId, long promptTokens, long completionTokens,
                             long totalTokens, String model, boolean ownModel, boolean deepThinking,
                             String remarkOverride) {
        return consume(kaiUserId, promptTokens, completionTokens, totalTokens, model, ownModel, deepThinking,
                remarkOverride, null);
    }

    /**
     * 同上，rateOverride 供内置模型传自己的独立倍率（2026-10-20）；null = 按模型倍率配置文本解析。
     */
    public BigDecimal consume(Long kaiUserId, long promptTokens, long completionTokens,
                             long totalTokens, String model, boolean ownModel, boolean deepThinking,
                             String remarkOverride, BigDecimal rateOverride) {
        BigDecimal cost = BigDecimal.ZERO;
        boolean free = ownModel && getBool(K_OWN_MODEL_FREE);
        if (!free && totalTokens > 0) {
            // 最终倍率 = 模型倍率 ×（深度思考轮再乘深度倍率）；内置模型直接用运营配置的独立倍率，其余按配置文本
            BigDecimal baseRate = rateOverride != null ? rateOverride : modelRate(model);
            BigDecimal multiplier = baseRate
                    .multiply(deepThinking ? deepThinkingMultiplier() : BigDecimal.ONE);
            cost = BigDecimal.valueOf(totalTokens)
                    .divide(BigDecimal.valueOf(tokensPerCredit()), 6, RoundingMode.HALF_UP)
                    .multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
            // 长尾轮次防零扣减失真
            if (cost.compareTo(BigDecimal.ZERO) > 0 && cost.compareTo(new BigDecimal("0.01")) < 0) {
                cost = new BigDecimal("0.01");
            }
        }
        BigDecimal charged = BigDecimal.ZERO;
        try {
            if (cost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal bal = balance(kaiUserId);
                charged = cost.min(bal.max(BigDecimal.ZERO)).setScale(4, RoundingMode.HALF_UP);
                if (charged.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal after = jdbcTemplate.queryForObject("""
                            UPDATE kai_user SET credit_balance = COALESCE(credit_balance,0) - ?,
                                                credit_consumed = COALESCE(credit_consumed,0) + ?
                            WHERE id = ? RETURNING credit_balance
                            """, BigDecimal.class, charged, charged, kaiUserId);
                    String remark = remarkOverride != null && !remarkOverride.isBlank()
                            ? remarkOverride : (deepThinking ? "对话消耗（深度思考）" : "对话消耗");
                    insertLedger(kaiUserId, "CONSUME", charged.negate(),
                            after == null ? BigDecimal.ZERO : after, totalTokens, model, remark);
                }
            }
            logUsage(kaiUserId, model, promptTokens, completionTokens, totalTokens, charged, ownModel);
        } catch (Exception e) {
            log.warn("[小楷独立版] 扣减积分失败: {}", e.getMessage());
        }
        return charged;
    }

    /** 记录每轮调用明细（不含 prompt/completion 拆分时传 0）。 */
    public void logUsage(Long kaiUserId, String model, long promptTokens, long completionTokens,
                         long totalTokens, BigDecimal credits, boolean ownModel) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO kai_usage_log (kai_user_id, model, prompt_tokens, completion_tokens, total_tokens, credits, own_model)
                    VALUES (?,?,?,?,?,?,?)
                    """, kaiUserId, model, promptTokens, completionTokens, totalTokens,
                    credits == null ? BigDecimal.ZERO : credits, ownModel ? 1 : 0);
        } catch (Exception e) {
            log.warn("[小楷独立版] 调用明细落库失败: {}", e.getMessage());
        }
    }

    private void insertLedger(Long kaiUserId, String type, BigDecimal credits, BigDecimal after,
                              long tokens, String model, String remark) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO kai_credit_ledger (kai_user_id, change_type, credits, balance_after, tokens, model, remark)
                    VALUES (?,?,?,?,?,?,?)
                    """, kaiUserId, type, credits, after, tokens, model,
                    remark == null ? "" : (remark.length() > 300 ? remark.substring(0, 300) : remark));
        } catch (Exception e) {
            log.warn("[小楷独立版] 积分流水落库失败: {}", e.getMessage());
        }
    }

    // ---------------- 管理员 ----------------

    public boolean isAdmin(Long kaiUserId) {
        try {
            KaiUser u = kaiUserMapper.selectById(kaiUserId);
            return u != null && u.getIsAdmin() != null && u.getIsAdmin() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 管理员：客户列表（不含运营账号）。 */
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT id, username, display_name, company_name, phone, email, remark, status,
                       COALESCE(credit_balance,0) AS credit_balance,
                       COALESCE(credit_granted,0) AS credit_granted,
                       COALESCE(credit_consumed,0) AS credit_consumed,
                       COALESCE(is_admin,0) AS is_admin,
                       created_at, last_login_at
                FROM kai_user WHERE COALESCE(is_admin,0) = 0 ORDER BY id DESC
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("username", rs.getString("username"));
            m.put("displayName", rs.getString("display_name"));
            m.put("companyName", rs.getString("company_name"));
            m.put("phone", rs.getString("phone"));
            m.put("email", rs.getString("email"));
            m.put("remark", rs.getString("remark"));
            m.put("status", rs.getInt("status"));
            m.put("creditBalance", rs.getBigDecimal("credit_balance"));
            m.put("creditGranted", rs.getBigDecimal("credit_granted"));
            m.put("creditConsumed", rs.getBigDecimal("credit_consumed"));
            m.put("isAdmin", rs.getInt("is_admin") == 1);
            m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
            m.put("lastLoginAt", String.valueOf(rs.getTimestamp("last_login_at")));
            out.add(m);
        });
        return out;
    }

    /** 管理员：单个用户调整积分（正数增加 / 负数扣减，扣减不把余额打成负数）。 */
    public BigDecimal adjust(Long targetUserId, BigDecimal delta, String remark) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("请填写要调整的积分数量");
        }
        BigDecimal bal = balance(targetUserId);
        BigDecimal amt = delta.setScale(4, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal maxCut = bal.max(BigDecimal.ZERO).negate();
            if (amt.compareTo(maxCut) < 0) amt = maxCut;
        }
        BigDecimal after = jdbcTemplate.queryForObject("""
                UPDATE kai_user SET credit_balance = COALESCE(credit_balance,0) + ?,
                                    credit_granted = COALESCE(credit_granted,0) + ?
                WHERE id = ? RETURNING credit_balance
                """, BigDecimal.class, amt, amt.max(BigDecimal.ZERO), targetUserId);
        insertLedger(targetUserId, "ADMIN_ADJUST", amt, after == null ? BigDecimal.ZERO : after,
                0, null, remark == null || remark.isBlank() ? "管理员调整" : remark);
        return after;
    }

    /** 管理员：启用/停用账号。 */
    public void setStatus(Long targetUserId, int status) {
        jdbcTemplate.update("UPDATE kai_user SET status = ? WHERE id = ?", status == 0 ? 0 : 1, targetUserId);
    }

    // ---------------- 配置 ----------------

    /** 当前运营管理员账号（只用于展示，权限由显式初始化的运营账号决定）。 */
    private String operatorUsername() {
        try {
            String u = jdbcTemplate.queryForObject(
                    "SELECT username FROM kai_user WHERE COALESCE(is_admin,0) = 1 ORDER BY id LIMIT 1", String.class);
            return u == null ? "" : u;
        } catch (Exception e) {
            return "";
        }
    }

    /** 运营管理员自己的账号 id（用户管理页「给自己调整积分」入口用；未初始化返回 null）。 */
    private Long operatorId() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM kai_user WHERE COALESCE(is_admin,0) = 1 ORDER BY id LIMIT 1", Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 运营管理员自己的积分余额（同上入口展示用）。 */
    private BigDecimal operatorBalance() {
        try {
            BigDecimal b = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(credit_balance,0) FROM kai_user WHERE COALESCE(is_admin,0) = 1 ORDER BY id LIMIT 1",
                    BigDecimal.class);
            return b == null ? BigDecimal.ZERO : b;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 管理员：单个客户的 token 消耗明细（月度汇总 + 最近调用）。 */
    public Map<String, Object> userUsage(Long kaiUserId) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> user = new LinkedHashMap<>();
            jdbcTemplate.query("""
                    SELECT id, username, display_name, company_name, phone, email, remark
                    FROM kai_user WHERE id = ?
                    """, rs -> {
                user.put("id", rs.getLong("id"));
                user.put("username", rs.getString("username"));
                user.put("displayName", rs.getString("display_name"));
                user.put("companyName", rs.getString("company_name"));
                user.put("phone", rs.getString("phone"));
                user.put("email", rs.getString("email"));
                user.put("remark", rs.getString("remark"));
            }, kaiUserId);
            out.put("user", user);
            List<Map<String, Object>> months = new ArrayList<>();
            jdbcTemplate.query("""
                    SELECT month, prompt_tokens, completion_tokens, total_tokens, call_count
                    FROM kai_usage WHERE kai_user_id = ? ORDER BY month DESC LIMIT 12
                    """, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("month", rs.getString("month"));
                m.put("promptTokens", rs.getLong("prompt_tokens"));
                m.put("completionTokens", rs.getLong("completion_tokens"));
                m.put("totalTokens", rs.getLong("total_tokens"));
                m.put("callCount", rs.getInt("call_count"));
                months.add(m);
            }, kaiUserId);
            out.put("months", months);
            List<Map<String, Object>> calls = new ArrayList<>();
            jdbcTemplate.query("""
                    SELECT model, prompt_tokens, completion_tokens, total_tokens, credits, own_model, created_at
                    FROM kai_usage_log WHERE kai_user_id = ? ORDER BY id DESC LIMIT 50
                    """, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("model", rs.getString("model"));
                m.put("promptTokens", rs.getLong("prompt_tokens"));
                m.put("completionTokens", rs.getLong("completion_tokens"));
                m.put("totalTokens", rs.getLong("total_tokens"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("ownModel", rs.getInt("own_model") == 1);
                m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                calls.add(m);
            }, kaiUserId);
            out.put("calls", calls);
        } catch (Exception e) {
            log.warn("[小楷独立版] 读取客户用量失败: {}", e.getMessage());
        }
        return out;
    }

    /** 前台可见配置（注册页文案）。 */
    public Map<String, Object> publicConfig() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tokensPerCredit", tokensPerCredit());
        out.put("registerGift", getLong(K_REGISTER_GIFT));
        out.put("registerGiftEnabled", getBool(K_REGISTER_GIFT_ON));
        out.put("registerEnabled", getBool(K_REGISTER_ENABLED));
        return out;
    }

    /** 管理员配置页数据（密钥只回显是否已配置 + 尾号）。 */
    public Map<String, Object> adminConfig() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tokensPerCredit", tokensPerCredit());
        out.put("registerGiftEnabled", getBool(K_REGISTER_GIFT_ON));
        out.put("registerGift", getLong(K_REGISTER_GIFT));
        out.put("monthlyGiftEnabled", getBool(K_MONTHLY_GIFT_ON));
        out.put("monthlyGift", getLong(K_MONTHLY_GIFT));
        out.put("lowBalance", getLong(K_LOW_BALANCE));
        out.put("ownModelFree", getBool(K_OWN_MODEL_FREE));
        out.put("deepThinkingMultiplier", deepThinkingMultiplier());
        out.put("modelRatesText", get(K_MODEL_RATES));
        out.put("registerEnabled", getBool(K_REGISTER_ENABLED));
        out.put("operatorUsername", operatorUsername());
        out.put("operatorId", operatorId());
        out.put("operatorBalance", operatorBalance());
        out.put("defaultName", get(K_DEFAULT_NAME));
        out.put("defaultBaseUrl", get(K_DEFAULT_BASE_URL));
        out.put("defaultModel", get(K_DEFAULT_MODEL));
        String enc = get(K_DEFAULT_API_KEY);
        boolean has = !enc.isBlank();
        out.put("hasDefaultKey", has);
        out.put("defaultKeyMasked", has ? cryptoService.maskTail(safeDecrypt(enc)) : "");
        return out;
    }

    /** 管理员保存配置；apiKey 留空 = 保持原值，"__CLEAR__" = 清空。 */
    public void saveConfig(Map<String, Object> body) {
        putIfPresent(body, "tokensPerCredit", K_TOKENS_PER_CREDIT, 1);
        putIfPresent(body, "registerGift", K_REGISTER_GIFT, 0);
        putIfPresent(body, "monthlyGift", K_MONTHLY_GIFT, 0);
        putIfPresent(body, "lowBalance", K_LOW_BALANCE, 0);
        putDecimalIfPresent(body, "deepThinkingMultiplier", K_DEEP_MULTIPLIER);
        if (body.containsKey("modelRatesText")) {
            Object v = body.get("modelRatesText");
            String text = v == null ? "" : String.valueOf(v);
            // 校验每行「模型名=倍率」，倍率必须是非负数字；非法行直接拒绝，避免静默按 1.0
            for (String line : text.split("\\r?\\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                int eq = t.indexOf('=');
                if (eq <= 0 || eq == t.length() - 1) {
                    throw new IllegalArgumentException("模型积分倍率格式应为「模型名=倍率」，每行一条：「" + t + "」");
                }
                try {
                    if (new java.math.BigDecimal(t.substring(eq + 1).trim())
                            .compareTo(java.math.BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("模型倍率不能为负数：「" + t + "」");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("模型倍率必须是数字：「" + t + "」");
                }
            }
            put(K_MODEL_RATES, text.trim());
        }
        putBoolIfPresent(body, "registerGiftEnabled", K_REGISTER_GIFT_ON);
        putBoolIfPresent(body, "monthlyGiftEnabled", K_MONTHLY_GIFT_ON);
        putBoolIfPresent(body, "ownModelFree", K_OWN_MODEL_FREE);
        putBoolIfPresent(body, "registerEnabled", K_REGISTER_ENABLED);
        putTextIfPresent(body, "defaultName", K_DEFAULT_NAME, 100);
        putTextIfPresent(body, "defaultBaseUrl", K_DEFAULT_BASE_URL, 300);
        putTextIfPresent(body, "defaultModel", K_DEFAULT_MODEL, 100);
        if (body.containsKey("defaultApiKey")) {
            String raw = body.get("defaultApiKey") == null ? "" : String.valueOf(body.get("defaultApiKey")).trim();
            if ("__CLEAR__".equals(raw)) {
                put(K_DEFAULT_API_KEY, "");
            } else if (!raw.isEmpty()) {
                put(K_DEFAULT_API_KEY, cryptoService.encrypt(raw));
            }
        }
    }

    private void putIfPresent(Map<String, Object> body, String field, String key, long min) {
        if (!body.containsKey(field) || body.get(field) == null) return;
        long v;
        try { v = Long.parseLong(String.valueOf(body.get(field)).trim()); }
        catch (Exception e) { throw new IllegalArgumentException("配置项 " + field + " 必须是数字"); }
        if (v < min) v = min;
        put(key, String.valueOf(v));
    }

    /** 小数配置（如倍率）：小于下限按下限处理，保留 2 位。 */
    private void putDecimalIfPresent(Map<String, Object> body, String field, String key) {
        if (!body.containsKey(field) || body.get(field) == null) return;
        java.math.BigDecimal v;
        try {
            v = new java.math.BigDecimal(String.valueOf(body.get(field)).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("配置项 " + field + " 必须是数字");
        }
        if (v.compareTo(java.math.BigDecimal.ONE) < 0) v = java.math.BigDecimal.ONE;
        put(key, v.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private void putBoolIfPresent(Map<String, Object> body, String field, String key) {
        if (!body.containsKey(field) || body.get(field) == null) return;
        Object v = body.get(field);
        boolean b = (v instanceof Boolean) ? (Boolean) v : "true".equals(String.valueOf(v));
        put(key, b ? "true" : "false");
    }

    private void putTextIfPresent(Map<String, Object> body, String field, String key, int max) {
        if (!body.containsKey(field) || body.get(field) == null) return;
        String v = String.valueOf(body.get(field)).trim();
        if (v.length() > max) v = v.substring(0, max);
        put(key, v);
    }

    private void put(String key, String value) {
        jdbcTemplate.update("""
                INSERT INTO kai_config (config_key, config_value, updated_at) VALUES (?,?,CURRENT_TIMESTAMP)
                ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = CURRENT_TIMESTAMP
                """, key, value);
    }

    /** 平台默认模型（DeepSeek）；密钥未配置时返回空密钥，由调用方回退系统默认渠道。 */
    public DefaultModel defaultModel() {
        String key = "";
        try { key = safeDecrypt(get(K_DEFAULT_API_KEY)); } catch (Exception ignore) { }
        return new DefaultModel(get(K_DEFAULT_NAME), get(K_DEFAULT_BASE_URL), get(K_DEFAULT_MODEL), key);
    }

    private String safeDecrypt(String enc) {
        try {
            if (enc == null || enc.isBlank()) return "";
            return cryptoService.decrypt(enc);
        } catch (Exception e) {
            return "";
        }
    }

    public record DefaultModel(String name, String baseUrl, String model, String apiKey) {
        public boolean usable() {
            return apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }
}
