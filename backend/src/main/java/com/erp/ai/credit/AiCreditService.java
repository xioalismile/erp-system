package com.erp.ai.credit;

import com.erp.config.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷助手积分钱包（2026-10-24）。
 *
 * <p>口径与独立版小楷一致：</p>
 * <ul>
 *   <li>1 积分 = 1000 tokens（平台可配置）；</li>
 *   <li>扣减 = tokens ÷ 每积分 tokens × 模型倍率 ×（深度思考轮再乘深度倍率）；</li>
 *   <li>余额不足在下一轮开始前拦截，不中断正在生成的回答；</li>
 *   <li>积分记在员工账号上；默认全员共用公司总积分，管理员给员工单独分配后只扣该员工积分。</li>
 * </ul>
 *
 * <p>全局规则存主库 sys_config；各客户的公司总积分/员工积分存各客户独立库（物理隔离）。</p>
 */
@Slf4j
@Service
public class AiCreditService {

    public static final String K_TOKENS_PER_CREDIT = "ai.credit.tokens-per-credit";
    public static final String K_MODEL_RATES = "ai.credit.model-rates";
    public static final String K_DEEP_MULTIPLIER = "ai.credit.deep-thinking-multiplier";
    public static final String K_OWN_MODEL_FREE = "ai.credit.own-model-free";
    public static final String K_LOW_BALANCE = "ai.credit.low-balance";
    public static final String K_NEW_TENANT_CREDITS = "ai.credit.new-tenant-credits";
    public static final String K_NEW_USER_CREDITS = "ai.credit.new-user-credits";
    public static final String K_DEFAULT_CHANNEL = "ai.credit.default-channel";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal MIN_COST = new BigDecimal("0.01");

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(K_TOKENS_PER_CREDIT, "1000");
        DEFAULTS.put(K_MODEL_RATES, "");
        DEFAULTS.put(K_DEEP_MULTIPLIER, "1.5");
        DEFAULTS.put(K_OWN_MODEL_FREE, "false");
        DEFAULTS.put(K_LOW_BALANCE, "100");
        DEFAULTS.put(K_NEW_TENANT_CREDITS, "1000");
        DEFAULTS.put(K_NEW_USER_CREDITS, "0");
        DEFAULTS.put(K_DEFAULT_CHANNEL, "DEEPSEEK");
    }

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 主库直连：全局规则（兑换比例/模型倍率/默认值）统一存主库，各客户共用 */
    @Resource
    @Qualifier("businessJdbcTemplate")
    private JdbcTemplate businessJdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            AiCreditSchema.ensure(jdbcTemplate);
            log.info("[AI积分] 积分体系就绪：1积分={}tokens，深度倍率={}，新客户初始积分={}，新员工默认分配={}",
                    tokensPerCredit(), deepThinkingMultiplier(), newTenantCredits(), newUserCredits());
        } catch (Exception e) {
            log.warn("[AI积分] 初始化跳过: {}", e.getMessage());
        }
    }

    // ==================== 全局规则 ====================

    public String global(String key) {
        String def = DEFAULTS.getOrDefault(key, "");
        try {
            List<Map<String, Object>> rows = businessJdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ?", key);
            if (rows.isEmpty()) {
                return def;
            }
            Object v = rows.get(0).get("config_value");
            String s = v == null ? "" : String.valueOf(v);
            return s.isBlank() ? def : s;
        } catch (Exception e) {
            return def;
        }
    }

    public void saveGlobal(String key, String value, String name, String remark) {
        try {
            int n = businessJdbcTemplate.update(
                    "UPDATE sys_config SET config_value = ?, updated_at = CURRENT_TIMESTAMP WHERE config_key = ?",
                    value, key);
            if (n == 0) {
                businessJdbcTemplate.update(
                        "INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order) "
                                + "VALUES (?, ?, ?, 'ai', 'string', ?, 0)",
                        key, value, name, remark);
            }
        } catch (Exception e) {
            throw new BusinessException("保存积分规则失败: " + e.getMessage());
        }
    }

    public long tokensPerCredit() {
        long v = longOf(global(K_TOKENS_PER_CREDIT), 1000L);
        return v <= 0 ? 1000L : v;
    }

    public String modelRatesText() {
        return global(K_MODEL_RATES);
    }

    public BigDecimal deepThinkingMultiplier() {
        try {
            BigDecimal v = new BigDecimal(global(K_DEEP_MULTIPLIER).trim());
            if (v.compareTo(BigDecimal.ONE) < 0) return BigDecimal.ONE;
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new BigDecimal("1.50");
        }
    }

    public boolean ownModelFree() {
        String v = global(K_OWN_MODEL_FREE).trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    public long lowBalance() {
        return Math.max(0L, longOf(global(K_LOW_BALANCE), 100L));
    }

    public long newTenantCredits() {
        return Math.max(0L, longOf(global(K_NEW_TENANT_CREDITS), 1000L));
    }

    public long newUserCredits() {
        return Math.max(0L, longOf(global(K_NEW_USER_CREDITS), 0L));
    }

    /** 平台默认模型服务商（DEEPSEEK / QWEN / OLLAMA） */
    public String defaultChannel() {
        String v = global(K_DEFAULT_CHANNEL).trim().toUpperCase();
        return v.isEmpty() ? "DEEPSEEK" : v;
    }

    /** 模型积分倍率：配置文本「模型名=倍率」，未命中按 1.0。 */
    public BigDecimal modelRate(String model) {
        String text = modelRatesText();
        if (text == null || text.isBlank() || model == null || model.isBlank()) {
            return BigDecimal.ONE;
        }
        String target = model.trim();
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
            int eq = t.indexOf('=');
            String name = t.substring(0, eq).trim();
            String val = t.substring(eq + 1).trim();
            if (!name.equalsIgnoreCase(target)) continue;
            try {
                BigDecimal v = new BigDecimal(val);
                return v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                return BigDecimal.ONE;
            }
        }
        return BigDecimal.ONE;
    }

    /** 平台积分规则（客户管理「AI 默认规则」弹窗数据源）。 */
    public Map<String, Object> globalRules() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tokensPerCredit", tokensPerCredit());
        out.put("modelRatesText", modelRatesText());
        out.put("deepThinkingMultiplier", deepThinkingMultiplier());
        out.put("ownModelFree", ownModelFree());
        out.put("lowBalance", lowBalance());
        out.put("newTenantCredits", newTenantCredits());
        out.put("newUserCredits", newUserCredits());
        out.put("defaultChannel", defaultChannel());
        return out;
    }

    /** 保存平台积分规则；非法值直接中文拒绝，不静默按默认值。 */
    public void saveGlobalRules(Map<String, Object> body) {
        if (body == null) return;
        if (body.containsKey("tokensPerCredit")) {
            long v = requireLong(body.get("tokensPerCredit"), "每积分对应 tokens");
            if (v <= 0) throw new BusinessException("每积分对应 tokens 必须大于 0");
            saveGlobal(K_TOKENS_PER_CREDIT, String.valueOf(v), "AI每积分对应tokens", "1 积分兑换多少 tokens，默认 1000");
        }
        if (body.containsKey("modelRatesText")) {
            String text = body.get("modelRatesText") == null ? "" : String.valueOf(body.get("modelRatesText"));
            for (String line : text.split("\\r?\\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                int eq = t.indexOf('=');
                if (eq <= 0 || eq == t.length() - 1) {
                    throw new BusinessException("模型积分倍率格式应为「模型名=倍率」，每行一条：" + t);
                }
                try {
                    if (new BigDecimal(t.substring(eq + 1).trim()).compareTo(BigDecimal.ZERO) < 0) {
                        throw new BusinessException("模型倍率不能为负数：" + t);
                    }
                } catch (NumberFormatException e) {
                    throw new BusinessException("模型倍率必须是数字：" + t);
                }
            }
            saveGlobal(K_MODEL_RATES, text.trim(), "AI模型积分倍率", "每行「模型名=倍率」，未命中按 1.0");
        }
        if (body.containsKey("deepThinkingMultiplier")) {
            BigDecimal v;
            try {
                v = new BigDecimal(String.valueOf(body.get("deepThinkingMultiplier")).trim());
            } catch (Exception e) {
                throw new BusinessException("深度思考倍率必须是数字");
            }
            if (v.compareTo(BigDecimal.ONE) < 0) v = BigDecimal.ONE;
            saveGlobal(K_DEEP_MULTIPLIER, v.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "AI深度思考积分倍率", "开启深度思考时额外相乘，最小 1.0");
        }
        if (body.containsKey("ownModelFree")) {
            Object v = body.get("ownModelFree");
            boolean b = (v instanceof Boolean) ? (Boolean) v : "true".equals(String.valueOf(v));
            saveGlobal(K_OWN_MODEL_FREE, b ? "true" : "false", "AI自带模型是否免费",
                    "开启后用自己配置的模型不扣积分；默认关闭（所有花费都扣）");
        }
        if (body.containsKey("lowBalance")) {
            long v = requireLong(body.get("lowBalance"), "低余额提醒阈值");
            saveGlobal(K_LOW_BALANCE, String.valueOf(Math.max(0, v)), "AI低余额提醒阈值", "低于该积分时提示充值");
        }
        if (body.containsKey("newTenantCredits")) {
            long v = requireLong(body.get("newTenantCredits"), "新客户初始积分");
            saveGlobal(K_NEW_TENANT_CREDITS, String.valueOf(Math.max(0, v)), "AI新客户初始积分",
                    "新建客户时进入该公司总积分的初始值");
        }
        if (body.containsKey("newUserCredits")) {
            long v = requireLong(body.get("newUserCredits"), "新员工默认分配积分");
            saveGlobal(K_NEW_USER_CREDITS, String.valueOf(Math.max(0, v)), "AI新员工默认分配积分",
                    "新建员工账号时自动从公司总积分划拨的积分；0=不单独分配，全员共用公司总积分");
        }
        if (body.containsKey("defaultChannel")) {
            String ch = String.valueOf(body.get("defaultChannel")).trim().toUpperCase();
            if (!"DEEPSEEK".equals(ch) && !"QWEN".equals(ch) && !"OLLAMA".equals(ch)) {
                throw new BusinessException("默认模型服务商只能是 DEEPSEEK / QWEN / OLLAMA");
            }
            saveGlobal(K_DEFAULT_CHANNEL, ch, "AI默认模型服务商", "平台统一模型密钥对应的服务商");
        }
        log.info("[AI积分] 平台积分规则已更新");
    }

    // ==================== 钱包读取 ====================

    /** 对话前判定：有个人积分看个人，否则看公司总积分；存量客户未启用时不拦截。 */
    public record CheckResult(boolean allowed, String message) {
    }

    public CheckResult check(Long userId) {
        if (userId == null) {
            return new CheckResult(true, null);
        }
        try {
            Map<String, Object> personal = account("USER", userId);
            if (personal != null) {
                if (money(personal, "credit_balance").compareTo(BigDecimal.ZERO) > 0) {
                    return new CheckResult(true, null);
                }
                return new CheckResult(false, insuffientMessage());
            }
            Map<String, Object> tenant = account("TENANT", 0L);
            if (tenant == null || intOf(tenant, "enforced") != 1) {
                return new CheckResult(true, null);
            }
            if (money(tenant, "credit_balance").compareTo(BigDecimal.ZERO) > 0) {
                return new CheckResult(true, null);
            }
            return new CheckResult(false, insuffientMessage());
        } catch (Exception e) {
            // 积分表尚未就绪时不阻断 AI 主流程（启动自愈会补齐）
            log.warn("[AI积分] 余额判定跳过: {}", e.getMessage());
            return new CheckResult(true, null);
        }
    }

    private String insuffientMessage() {
        return "积分不足，请联系管理员充值后再继续对话（1 积分 = " + tokensPerCredit() + " tokens）";
    }

    /** 员工钱包视图：个人优先，未分配则展示公司总积分（共享）。 */
    public Map<String, Object> wallet(Long userId, String username) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal tenantBalance = BigDecimal.ZERO;
        int enforced = 0;
        try {
            Map<String, Object> tenant = account("TENANT", 0L);
            if (tenant != null) {
                tenantBalance = money(tenant, "credit_balance");
                enforced = intOf(tenant, "enforced");
            }
            Map<String, Object> personal = userId == null ? null : account("USER", userId);
            if (personal != null) {
                out.put("balance", money(personal, "credit_balance"));
                out.put("granted", money(personal, "credit_granted"));
                out.put("consumed", money(personal, "credit_consumed"));
                out.put("ownerType", "USER");
            } else {
                out.put("balance", tenantBalance);
                out.put("granted", BigDecimal.ZERO);
                out.put("consumed", BigDecimal.ZERO);
                out.put("ownerType", "TENANT");
            }
        } catch (Exception e) {
            out.put("balance", BigDecimal.ZERO);
            out.put("granted", BigDecimal.ZERO);
            out.put("consumed", BigDecimal.ZERO);
            out.put("ownerType", "TENANT");
        }
        out.put("tenantBalance", tenantBalance);
        out.put("enforced", enforced);
        out.put("tokensPerCredit", tokensPerCredit());
        out.put("lowBalance", lowBalance());
        out.put("deepThinkingMultiplier", deepThinkingMultiplier());
        out.put("ownModelFree", ownModelFree());
        out.put("modelRatesText", modelRatesText());
        out.put("username", username);
        return out;
    }

    public List<Map<String, Object>> ledger(Long userId, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (userId == null) return out;
        try {
            boolean personal = account("USER", userId) != null;
            String sql = personal
                    ? "SELECT change_type, credits, balance_after, tokens, model, remark, created_at FROM ai_credit_ledger "
                      + "WHERE owner_type='USER' AND owner_id=? ORDER BY id DESC LIMIT ?"
                    : "SELECT change_type, credits, balance_after, tokens, model, remark, created_at FROM ai_credit_ledger "
                      + "WHERE owner_type='TENANT' AND user_id=? ORDER BY id DESC LIMIT ?";
            jdbcTemplate.query(sql, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", rs.getString("change_type"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("balanceAfter", rs.getBigDecimal("balance_after"));
                m.put("tokens", rs.getLong("tokens"));
                m.put("model", rs.getString("model"));
                m.put("remark", rs.getString("remark"));
                m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                out.add(m);
            }, userId, Math.max(1, Math.min(limit, 100)));
        } catch (Exception e) {
            log.warn("[AI积分] 读取积分流水失败: {}", e.getMessage());
        }
        return out;
    }

    // ==================== 扣减 ====================

    public record ConsumeResult(BigDecimal cost, BigDecimal charged, BigDecimal balance, String ownerType) {
    }

    /**
     * 一轮对话结束后按真实 tokens 结算。
     *
     * @param ownModel    是否用户自带模型（自定义服务商）
     * @param deepThinking 本轮是否显式开启深度思考
     */
    public ConsumeResult consume(Long userId, String username,
                                 long promptTokens, long completionTokens, long totalTokens,
                                 String model, boolean ownModel, boolean deepThinking, String remark) {
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;
        String ownerType = "TENANT";
        try {
            boolean free = ownModel && ownModelFree();
            if (!free && totalTokens > 0) {
                BigDecimal rate = modelRate(model);
                BigDecimal multiplier = deepThinking ? deepThinkingMultiplier() : BigDecimal.ONE;
                cost = BigDecimal.valueOf(totalTokens)
                        .divide(BigDecimal.valueOf(tokensPerCredit()), 6, RoundingMode.HALF_UP)
                        .multiply(rate).multiply(multiplier)
                        .setScale(4, RoundingMode.HALF_UP);
                if (cost.compareTo(BigDecimal.ZERO) > 0 && cost.compareTo(MIN_COST) < 0) {
                    cost = MIN_COST;
                }
            }
            if (cost.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> personal = userId == null ? null : account("USER", userId);
                Map<String, Object> tenant = account("TENANT", 0L);
                boolean usePersonal = personal != null;
                Map<String, Object> target = usePersonal ? personal : tenant;
                ownerType = usePersonal ? "USER" : "TENANT";
                if (target == null) {
                    return new ConsumeResult(cost, BigDecimal.ZERO, BigDecimal.ZERO, ownerType);
                }
                if (!usePersonal && intOf(target, "enforced") != 1) {
                    // 存量客户尚未启用积分控制：只统计 tokens，不扣积分
                    return new ConsumeResult(cost, BigDecimal.ZERO, money(target, "credit_balance"), ownerType);
                }
                balance = money(target, "credit_balance");
                charged = cost.min(balance.max(BigDecimal.ZERO)).setScale(4, RoundingMode.HALF_UP);
                if (charged.compareTo(BigDecimal.ZERO) > 0) {
                    // 公司共享积分扣减也要记到具体员工名下，员工端「我的流水」才能看到自己这一轮
                    BigDecimal after = charge(ownerType, 0L, userId, username, charged, totalTokens, model,
                            remark == null || remark.isBlank()
                                    ? (deepThinking ? "对话消耗（深度思考）" : "对话消耗") : remark);
                    balance = after;
                }
            }
        } catch (Exception e) {
            log.warn("[AI积分] 扣减失败: {}", e.getMessage());
        }
        return new ConsumeResult(cost, charged, balance, ownerType);
    }

    private BigDecimal charge(String ownerType, long ownerId, Long userId, String username,
                              BigDecimal amount, long tokens, String model, String remark) {
        BigDecimal after = jdbcTemplate.queryForObject(
                "UPDATE ai_credit_account SET credit_balance = credit_balance - ?,"
                        + " credit_consumed = credit_consumed + ?, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE owner_type = ? AND owner_id = ? RETURNING credit_balance",
                BigDecimal.class, amount, amount, ownerType, ownerId);
        BigDecimal bal = after == null ? BigDecimal.ZERO : after;
        insertLedger(ownerType, ownerId, userId, username, "CONSUME", amount.negate(), bal, tokens, model, remark);
        return bal;
    }

    // ==================== 公司总积分 ====================

    /** 新建客户：按平台规则建公司总积分账户并赠送初始积分（幂等）。 */
    public void grantNewTenantCreditsOn(JdbcTemplate target) {
        if (target == null) return;
        AiCreditSchema.ensure(target);
        long initial = newTenantCredits();
        int inserted = target.update(
                "INSERT INTO ai_credit_account (owner_type, owner_id, credit_balance, credit_granted, enforced)"
                        + " VALUES ('TENANT', 0, ?, ?, 1) ON CONFLICT (owner_type, owner_id) DO NOTHING",
                BigDecimal.valueOf(initial), BigDecimal.valueOf(initial));
        if (inserted > 0) {
            insertLedgerOn(target, "TENANT", 0L, null, null, "GRANT", BigDecimal.valueOf(initial),
                    BigDecimal.valueOf(initial), 0, null, "新客户初始积分");
            log.info("[AI积分] 新客户公司总积分已初始化: {}", initial);
        } else {
            // 已存在（老客户/重复调用）：仅确保启用标记
            target.update("UPDATE ai_credit_account SET enforced = 1 WHERE owner_type='TENANT' AND owner_id=0");
        }
    }

    public void grantNewTenantCredits() {
        grantNewTenantCreditsOn(jdbcTemplate);
    }

    /** 平台给客户增加/扣减公司总积分（正数增加，负数扣减，不会扣成负数）。 */
    @Transactional
    public BigDecimal adjustTenantCredits(BigDecimal delta, String remark) {
        return adjustTenantCreditsOn(jdbcTemplate, delta, remark);
    }

    public BigDecimal adjustTenantCreditsOn(JdbcTemplate target, BigDecimal delta, String remark) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("请填写要调整的积分数量");
        }
        AiCreditSchema.ensure(target);
        ensureTenantAccountOn(target);
        Map<String, Object> acc = queryAccount(target, "TENANT", 0L);
        BigDecimal bal = acc == null ? BigDecimal.ZERO : money(acc, "credit_balance");
        BigDecimal amt = delta.setScale(4, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal maxCut = bal.max(BigDecimal.ZERO).negate();
            if (amt.compareTo(maxCut) < 0) amt = maxCut;
        }
        BigDecimal after = target.queryForObject(
                "UPDATE ai_credit_account SET credit_balance = credit_balance + ?,"
                        + " credit_granted = credit_granted + ?, enforced = 1, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE owner_type='TENANT' AND owner_id=0 RETURNING credit_balance",
                BigDecimal.class, amt, amt.max(BigDecimal.ZERO));
        insertLedgerOn(target, "TENANT", 0L, null, null, "ADMIN_ADJUST", amt,
                after == null ? BigDecimal.ZERO : after, 0, null,
                remark == null || remark.isBlank() ? "平台调整积分" : remark);
        return after;
    }

    /** 公司积分汇总（设置中心「公司积分」页 + 平台「积分」弹窗）。 */
    public Map<String, Object> tenantSummary() {
        return tenantSummaryOn(jdbcTemplate);
    }

    public Map<String, Object> tenantSummaryOn(JdbcTemplate target) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            AiCreditSchema.ensure(target);
            ensureTenantAccountOn(target);
            Map<String, Object> acc = queryAccount(target, "TENANT", 0L);
            BigDecimal balance = acc == null ? BigDecimal.ZERO : money(acc, "credit_balance");
            BigDecimal granted = acc == null ? BigDecimal.ZERO : money(acc, "credit_granted");
            BigDecimal consumed = acc == null ? BigDecimal.ZERO : money(acc, "credit_consumed");
            int enforced = acc == null ? 0 : intOf(acc, "enforced");
            BigDecimal allocated = BigDecimal.ZERO;
            int allocatedUsers = 0;
            try {
                BigDecimal v = target.queryForObject(
                        "SELECT COALESCE(SUM(credit_balance),0) FROM ai_credit_account WHERE owner_type='USER'",
                        BigDecimal.class);
                allocated = v == null ? BigDecimal.ZERO : v;
                Integer c = target.queryForObject(
                        "SELECT COUNT(*) FROM ai_credit_account WHERE owner_type='USER'", Integer.class);
                allocatedUsers = c == null ? 0 : c;
            } catch (Exception ignore) {
                // 分配统计失败不影响余额展示
            }
            BigDecimal monthConsumed = BigDecimal.ZERO;
            try {
                BigDecimal v = target.queryForObject(
                        "SELECT COALESCE(SUM(credits),0) FROM ai_token_usage WHERE month = ?",
                        BigDecimal.class, LocalDate.now().format(MONTH));
                monthConsumed = v == null ? BigDecimal.ZERO : v;
            } catch (Exception ignore) {
                // 月用量表缺失时按 0
            }
            out.put("balance", balance);
            out.put("granted", granted);
            out.put("consumed", consumed);
            out.put("monthConsumed", monthConsumed);
            out.put("allocated", allocated);
            out.put("allocatedUsers", allocatedUsers);
            out.put("enforced", enforced);
        } catch (Exception e) {
            out.put("balance", BigDecimal.ZERO);
            out.put("granted", BigDecimal.ZERO);
            out.put("consumed", BigDecimal.ZERO);
            out.put("monthConsumed", BigDecimal.ZERO);
            out.put("allocated", BigDecimal.ZERO);
            out.put("allocatedUsers", 0);
            out.put("enforced", 0);
        }
        out.putAll(globalRules());
        return out;
    }

    // ==================== 员工积分分配 ====================

    /** 从公司总积分划拨给员工（管理员分配）。 */
    @Transactional
    public BigDecimal allocateToUser(Long userId, String username, BigDecimal amount, String remark) {
        if (userId == null) throw new BusinessException("请先选择员工");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("分配积分必须大于 0");
        }
        BigDecimal amt = amount.setScale(4, RoundingMode.HALF_UP);
        BigDecimal after = drainTenant(amt, "分配给员工 " + safe(username), userId, username);
        BigDecimal userBalance = upsertUserCredit(userId, username, amt);
        insertLedger("USER", userId, userId, username, "ALLOCATE_IN", amt, userBalance, 0, null,
                remark == null || remark.isBlank() ? "管理员分配" : remark);
        insertLedger("TENANT", 0L, userId, username, "ALLOCATE_OUT", amt.negate(), after, 0, null,
                "分配给员工 " + safe(username));
        return userBalance;
    }

    /** 收回员工个人积分并恢复共享公司总积分。 */
    @Transactional
    public void reclaimFromUser(Long userId, String username, String remark) {
        if (userId == null) throw new BusinessException("请先选择员工");
        Map<String, Object> personal = account("USER", userId);
        if (personal == null) {
            return;
        }
        BigDecimal amount = money(personal, "credit_balance").max(BigDecimal.ZERO);
        jdbcTemplate.update("DELETE FROM ai_credit_account WHERE owner_type='USER' AND owner_id=?", userId);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal after = jdbcTemplate.queryForObject(
                    "UPDATE ai_credit_account SET credit_balance = credit_balance + ?, updated_at = CURRENT_TIMESTAMP"
                            + " WHERE owner_type='TENANT' AND owner_id=0 RETURNING credit_balance",
                    BigDecimal.class, amount);
            insertLedger("USER", userId, userId, username, "RECLAIM_OUT", amount.negate(), BigDecimal.ZERO,
                    0, null, remark == null || remark.isBlank() ? "收回个人积分" : remark);
            insertLedger("TENANT", 0L, userId, username, "RECLAIM_IN", amount,
                    after == null ? BigDecimal.ZERO : after, 0, null, "收回员工 " + safe(username) + " 的积分");
        }
    }

    /**
     * 新员工开通：按平台规则自动分配（0=不分配，直接共用公司总积分）。
     * <p>独立事务：分配失败只回滚分配本身，不影响建号主流程（调用方负责 catch 并记录）。</p>
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void applyNewUserRule(Long userId, String username) {
        if (userId == null) return;
        long rule = newUserCredits();
        if (rule <= 0) return;
        AiCreditSchema.ensure(jdbcTemplate);
        ensureTenantAccountOn(jdbcTemplate);
        Map<String, Object> tenant = queryAccount(jdbcTemplate, "TENANT", 0L);
        if (tenant == null || intOf(tenant, "enforced") != 1) return;
        BigDecimal bal = money(tenant, "credit_balance");
        BigDecimal amt = BigDecimal.valueOf(rule).min(bal.max(BigDecimal.ZERO));
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[AI积分] 新员工默认分配跳过（公司总积分不足）: userId={}", userId);
            return;
        }
        BigDecimal after = drainTenant(amt, "新员工默认分配", userId, username);
        BigDecimal userBalance = upsertUserCredit(userId, username, amt);
        insertLedger("USER", userId, userId, username, "ALLOCATE_IN", amt, userBalance, 0, null, "新员工默认分配");
        insertLedger("TENANT", 0L, userId, username, "ALLOCATE_OUT", amt.negate(), after, 0, null,
                "新员工默认分配 " + safe(username));
    }

    private BigDecimal drainTenant(BigDecimal amount, String remark, Long userId, String username) {
        ensureTenantAccountOn(jdbcTemplate);
        BigDecimal after = jdbcTemplate.queryForObject(
                "UPDATE ai_credit_account SET credit_balance = credit_balance - ?, enforced = 1,"
                        + " updated_at = CURRENT_TIMESTAMP WHERE owner_type='TENANT' AND owner_id=0"
                        + " AND credit_balance >= ? RETURNING credit_balance",
                BigDecimal.class, amount, amount);
        if (after == null) {
            Map<String, Object> tenant = queryAccount(jdbcTemplate, "TENANT", 0L);
            BigDecimal bal = tenant == null ? BigDecimal.ZERO : money(tenant, "credit_balance");
            throw new BusinessException("公司总积分不足（当前 " + bal.stripTrailingZeros().toPlainString()
                    + " 积分），无法分配 " + amount.stripTrailingZeros().toPlainString() + " 积分");
        }
        return after;
    }

    private BigDecimal upsertUserCredit(Long userId, String username, BigDecimal amount) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO ai_credit_account (owner_type, owner_id, user_id, username,"
                        + " credit_balance, credit_granted, enforced) VALUES ('USER', ?, ?, ?, ?, ?, 1)"
                        + " ON CONFLICT (owner_type, owner_id) DO UPDATE SET"
                        + " credit_balance = ai_credit_account.credit_balance + EXCLUDED.credit_balance,"
                        + " credit_granted = ai_credit_account.credit_granted + EXCLUDED.credit_granted,"
                        + " user_id = EXCLUDED.user_id, username = EXCLUDED.username,"
                        + " updated_at = CURRENT_TIMESTAMP RETURNING credit_balance",
                BigDecimal.class, userId, userId, username, amount, amount);
    }

    // ==================== 表操作辅助 ====================

    private void ensureTenantAccountOn(JdbcTemplate target) {
        AiCreditSchema.ensure(target);
        target.update("INSERT INTO ai_credit_account (owner_type, owner_id, credit_balance, credit_granted, enforced)"
                + " VALUES ('TENANT', 0, 0, 0, 0) ON CONFLICT (owner_type, owner_id) DO NOTHING");
    }

    private Map<String, Object> account(String ownerType, long ownerId) {
        return queryAccount(jdbcTemplate, ownerType, ownerId);
    }

    private Map<String, Object> queryAccount(JdbcTemplate target, String ownerType, long ownerId) {
        try {
            List<Map<String, Object>> rows = target.queryForList(
                    "SELECT owner_type, owner_id, user_id, username, credit_balance, credit_granted,"
                            + " credit_consumed, enforced FROM ai_credit_account"
                            + " WHERE owner_type = ? AND owner_id = ?", ownerType, ownerId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void insertLedger(String ownerType, long ownerId, Long userId, String username,
                              String type, BigDecimal credits, BigDecimal after,
                              long tokens, String model, String remark) {
        insertLedgerOn(jdbcTemplate, ownerType, ownerId, userId, username, type, credits, after, tokens, model, remark);
    }

    private void insertLedgerOn(JdbcTemplate target, String ownerType, long ownerId, Long userId, String username,
                                String type, BigDecimal credits, BigDecimal after,
                                long tokens, String model, String remark) {
        try {
            target.update("INSERT INTO ai_credit_ledger (owner_type, owner_id, user_id, username, change_type,"
                            + " credits, balance_after, tokens, model, remark) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    ownerType, ownerId, userId, username, type, credits, after, tokens, model,
                    remark == null ? "" : (remark.length() > 300 ? remark.substring(0, 300) : remark));
        } catch (Exception e) {
            log.warn("[AI积分] 积分流水落库失败: {}", e.getMessage());
        }
    }

    private BigDecimal money(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private int intOf(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private long longOf(String v, long def) {
        try {
            return Long.parseLong(v == null ? "" : v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private long requireLong(Object v, String label) {
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new BusinessException(label + "必须是整数");
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
