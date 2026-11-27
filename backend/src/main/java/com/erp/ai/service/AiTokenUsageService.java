package com.erp.ai.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Token用量服务
 * - 用量记录：用户维度月聚合（ai_token_usage表，每用户每月一行，UPSERT原子累加）
 * - 配额判定：用户专属配额 > 全局默认配额；0=不限；未设专属值=继承默认
 * - 月度统计：设置中心「AI用量管理」页签数据源
 * - 自动建表：启动时幂等创建ai_token_usage（老库/服务器部署免手工建表）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTokenUsageService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AiConfigService aiConfigService;

    /** 积分钱包（2026-10-24）：设置中心积分页数据源 */
    private final com.erp.ai.credit.AiCreditService aiCreditService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            ensureTableExists();
        } catch (Exception e) {
            log.warn("[AI用量] ai_token_usage 初始化失败: {}", e.getMessage());
        }
    }

    /** 自动建表 + 幂等补列 */
    private void ensureTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_token_usage", Integer.class);
        } catch (Exception e) {
            log.info("[AI用量] ai_token_usage 表不存在，开始创建...");
            String createSql = """
                CREATE TABLE IF NOT EXISTS ai_token_usage (
                    id               BIGSERIAL PRIMARY KEY,
                    user_id          BIGINT       NOT NULL,
                    username         VARCHAR(100),
                    month            VARCHAR(7)   NOT NULL,
                    prompt_tokens    BIGINT       NOT NULL DEFAULT 0,
                    completion_tokens BIGINT      NOT NULL DEFAULT 0,
                    total_tokens     BIGINT       NOT NULL DEFAULT 0,
                    call_count       BIGINT       NOT NULL DEFAULT 0,
                    raw_prompt_tokens     BIGINT  NOT NULL DEFAULT 0,
                    raw_completion_tokens BIGINT  NOT NULL DEFAULT 0,
                    raw_total_tokens      BIGINT  NOT NULL DEFAULT 0,
                    prompt_cache_hit_tokens BIGINT NOT NULL DEFAULT 0,
                    credits          NUMERIC(14,4) NOT NULL DEFAULT 0,
                    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_ai_token_usage_user_month UNIQUE (user_id, month)
                )
                """;
            jdbcTemplate.execute(createSql);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ai_token_usage_month ON ai_token_usage (month, total_tokens DESC)");
            log.info("[AI用量] ai_token_usage 表创建完成。");
        }
        // 老库幂等补列：消耗系数改造新增的 3 个原始值列 + 缓存命中统计列（2026-10-10 小楷能力接入整合）
        // （表已存在时上面的 CREATE 不会执行，故这里必须单独补；失败仅告警，不影响 AI 主流程）
        try {
            jdbcTemplate.execute("ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS raw_prompt_tokens BIGINT NOT NULL DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS raw_completion_tokens BIGINT NOT NULL DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS raw_total_tokens BIGINT NOT NULL DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS prompt_cache_hit_tokens BIGINT NOT NULL DEFAULT 0");
            // 积分制改造新增列（2026-10-24）：本月消耗积分
            jdbcTemplate.execute("ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS credits NUMERIC(14,4) NOT NULL DEFAULT 0");
        } catch (Exception e) {
            log.warn("[AI用量] 原始值列补齐失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /** 配额判定结果 */
    public record QuotaCheck(boolean allowed, String message) {
    }

    /**
     * 配额判定：超额则拒绝。配额为0（不限）时不查库直接放行。
     * 租户配了自有API Key时免配额限制（费用自理）。
     * 平台分配额度（平台管理→客户管理）：该客户名下所有用户共享的月度总消耗上限，超限拦截。
     */
    public QuotaCheck checkQuota(Long userId) {
        // 租户配了自有API Key → 免配额限制（费用自理），仍统计用量但不拦截
        if (tenantHasOwnApiKey()) {
            return new QuotaCheck(true, null);
        }
        // 平台分配的客户月总额度（0/未分配=不限）：名下所有用户共享，按实际记账消耗判定
        long platformQuota = aiConfigService.getPlatformTenantQuota(
                com.erp.tenant.TenantContextHolder.getTenantId());
        if (platformQuota > 0) {
            long tenantUsed = tenantUsedThisMonth();
            if (tenantUsed >= platformQuota) {
                return new QuotaCheck(false,
                        "本月公司AI总额度已用完（已消耗 " + tenantUsed + " / 平台分配额度 " + platformQuota + " tokens），请联系平台管理员调整。");
            }
        }
        long quota = getEffectiveQuota(userId);
        if (quota <= 0) {
            return new QuotaCheck(true, null);
        }
        long used = usedTokensThisMonth(userId);
        if (used < quota) {
            return new QuotaCheck(true, null);
        }
        return new QuotaCheck(false,
                "本月AI额度已用完（已消耗 " + used + " / 配额 " + quota + " tokens），"
                        + "如需调整请联系管理员在设置中心「AI用量管理」中修改。");
    }

    /** 租户是否配置了自有API Key（默认渠道），配了则免配额限制 */
    private boolean tenantHasOwnApiKey() {
        try {
            String channel = aiConfigService.getDefaultChannel();
            return aiConfigService.hasTenantChannelApiKey(channel);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 用户生效配额：用户专属 > 租户总额度 > 全局默认（0=不限）。
     */
    public long getEffectiveQuota(Long userId) {
        String custom = aiConfigService.getUserQuota(userId);
        if (custom != null && !custom.isBlank()) {
            try {
                return Long.parseLong(custom.trim());
            } catch (NumberFormatException ignore) {
                // 非法配置回退租户额度
            }
        }
        return aiConfigService.getTenantQuota();
    }

    private long usedTokensThisMonth(Long userId) {
        try {
            Long used = jdbcTemplate.queryForObject(
                    "SELECT total_tokens FROM ai_token_usage WHERE user_id = ? AND month = ?",
                    Long.class, userId, currentMonth());
            return used != null ? used : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 当前租户库本月全部用户记账消耗合计（平台分配总额度判定用；异常按 0 处理，不让统计故障拦死AI） */
    private long tenantUsedThisMonth() {
        try {
            Long used = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(total_tokens), 0) FROM ai_token_usage WHERE month = ?",
                    Long.class, currentMonth());
            return used != null ? used : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 记录一次请求的token消耗（UPSERT原子累加，并发安全）。
     * <p>
     * 计费口径：记账 token = 实际 token × (1 + 租户消耗系数)，默认系数 0.5（实际 100 记 150）。
     * 实际值另存 raw_* 三列（仅平台内部对账，界面不展示）；调用次数不是 token，不乘系数。
     * cacheHitTokens = 模型侧命中上下文缓存的输入 tokens（同样乘系数保持口径一致，界面「缓存命中」列展示）。
     * 失败仅告警，绝不阻断AI主流程。
     */
    public void recordUsage(Long userId, String username, long promptTokens,
                            long completionTokens, long totalTokens, long calls, long cacheHitTokens) {
        recordUsage(userId, username, promptTokens, completionTokens, totalTokens, calls, cacheHitTokens,
                java.math.BigDecimal.ZERO);
    }

    /**
     * 记录一次请求的 token 消耗 + 积分消耗（2026-10-24 积分制）。
     * <p>token 口径保持旧版：记账 token = 实际 token × (1 + 租户消耗系数)；积分由积分服务按真实 tokens 结算后传入。</p>
     */
    public void recordUsage(Long userId, String username, long promptTokens,
                            long completionTokens, long totalTokens, long calls, long cacheHitTokens,
                            java.math.BigDecimal credits) {
        if (userId == null || totalTokens <= 0) {
            return;
        }
        try {
            double factor = 1.0 + aiConfigService.getTenantUsageRatio();
            long billedPrompt = Math.round(promptTokens * factor);
            long billedCompletion = Math.round(completionTokens * factor);
            long billedTotal = Math.round(totalTokens * factor);
            long billedCacheHit = Math.round(Math.max(0, cacheHitTokens) * factor);
            java.math.BigDecimal credit = credits == null ? java.math.BigDecimal.ZERO : credits;
            jdbcTemplate.update("""
                    INSERT INTO ai_token_usage
                        (user_id, username, month, prompt_tokens, completion_tokens, total_tokens, call_count,
                         raw_prompt_tokens, raw_completion_tokens, raw_total_tokens, prompt_cache_hit_tokens,
                         credits, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, month) DO UPDATE SET
                        prompt_tokens = ai_token_usage.prompt_tokens + EXCLUDED.prompt_tokens,
                        completion_tokens = ai_token_usage.completion_tokens + EXCLUDED.completion_tokens,
                        total_tokens = ai_token_usage.total_tokens + EXCLUDED.total_tokens,
                        call_count = ai_token_usage.call_count + EXCLUDED.call_count,
                        raw_prompt_tokens = ai_token_usage.raw_prompt_tokens + EXCLUDED.raw_prompt_tokens,
                        raw_completion_tokens = ai_token_usage.raw_completion_tokens + EXCLUDED.raw_completion_tokens,
                        raw_total_tokens = ai_token_usage.raw_total_tokens + EXCLUDED.raw_total_tokens,
                        prompt_cache_hit_tokens = ai_token_usage.prompt_cache_hit_tokens + EXCLUDED.prompt_cache_hit_tokens,
                        credits = ai_token_usage.credits + EXCLUDED.credits,
                        username = EXCLUDED.username,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    userId, username, currentMonth(),
                    billedPrompt, billedCompletion, billedTotal, calls,
                    promptTokens, completionTokens, totalTokens, billedCacheHit, credit);
            log.debug("[AI用量] 用户{} 实际消耗 {} tokens（缓存命中 {}），按系数 {} 记 {} tokens，扣 {} 积分",
                    username, totalTokens, cacheHitTokens, factor - 1.0, billedTotal, credit);
        } catch (Exception e) {
            log.warn("[AI用量] 用量记录失败: {}", e.getMessage());
        }
    }

    /**
     * 月度统计（设置中心AI用量管理数据源）。
     * 返回 {month, months, defaultQuota, rows:[{userId, username, promptTokens, ..., quota, quotaSource, exceeded}]}
     */
    public Map<String, Object> monthlyStats(String month) {
        String m = (month == null || month.isBlank()) ? currentMonth() : month.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", m);
        result.put("defaultQuota", aiConfigService.getDefaultQuota());
        // 租户消耗系数（供「AI用量管理」页回显；0.5 = 消耗按 1.5 倍计量）
        result.put("usageRatio", aiConfigService.getTenantUsageRatio());
        // 积分制（2026-10-24）：公司总积分汇总 + 全局积分规则
        result.put("credit", aiCreditService.tenantSummary());
        result.put("creditRules", aiCreditService.globalRules());

        List<String> months = listMonths();
        if (!months.contains(currentMonth())) {
            months.add(0, currentMonth());
        }
        result.put("months", months);

        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            // LEFT JOIN sys_user：显示所有用户（含无消耗记录、刚设了配额但没用过AI的用户）
            rows = jdbcTemplate.queryForList(
                    "SELECT u.user_id, u.username, " +
                            "COALESCE(a.prompt_tokens, 0) AS prompt_tokens, " +
                            "COALESCE(a.prompt_cache_hit_tokens, 0) AS prompt_cache_hit_tokens, " +
                            "COALESCE(a.completion_tokens, 0) AS completion_tokens, " +
                            "COALESCE(a.total_tokens, 0) AS total_tokens, " +
                            "COALESCE(a.credits, 0) AS credits, " +
                            "COALESCE(a.call_count, 0) AS call_count " +
                            "FROM sys_user u " +
                            "LEFT JOIN ai_token_usage a ON a.user_id = u.user_id AND a.month = ? " +
                            "ORDER BY COALESCE(a.total_tokens, 0) DESC, u.user_id ASC", m);
        } catch (Exception e) {
            log.warn("[AI用量] 查询月度统计失败: {}", e.getMessage());
        }
        for (Map<String, Object> row : rows) {
            Long userId = row.get("user_id") instanceof Number n ? n.longValue() : null;
            if (userId == null) {
                continue;
            }
            String custom = aiConfigService.getUserQuota(userId);
            long quota;
            String source;
            if (custom != null && !custom.isBlank()) {
                quota = parseLongSafe(custom, 0L);
                source = "custom";
            } else {
                // 未设专属配额 → 继承租户总额度（租户未设则继承全局默认）
                quota = aiConfigService.getTenantQuota();
                source = aiConfigService.hasTenantQuota() ? "tenant" : "default";
            }
            long total = row.get("total_tokens") instanceof Number n ? n.longValue() : 0L;
            row.put("quota", quota);
            row.put("quotaSource", source);
            row.put("exceeded", quota > 0 && total >= quota);
            // 积分制（2026-10-24）：员工个人积分优先；未分配则展示公司共享积分
            try {
                Map<String, Object> wallet = aiCreditService.wallet(userId, String.valueOf(row.get("username")));
                row.put("creditBalance", wallet.get("balance"));
                row.put("creditOwner", wallet.get("ownerType"));
                row.put("creditConsumed", wallet.get("consumed"));
                row.put("creditGranted", wallet.get("granted"));
                row.put("tenantBalance", wallet.get("tenantBalance"));
            } catch (Exception ignore) {
                row.put("creditBalance", 0);
                row.put("creditOwner", "TENANT");
                row.put("creditConsumed", 0);
                row.put("creditGranted", 0);
                row.put("tenantBalance", 0);
            }
        }
        result.put("rows", rows);

        // 租户额度汇总（供前端"租户额度"Tab展示）
        long tenantQuota = aiConfigService.getTenantQuota();
        long allocated = aiConfigService.getAllUserQuotaSum();
        result.put("tenantQuota", tenantQuota);
        result.put("tenantQuotaAllocated", allocated);
        result.put("tenantQuotaRemaining", tenantQuota > 0 ? Math.max(0, tenantQuota - allocated) : -1);
        result.put("hasTenantOwnKey", tenantHasOwnApiKey());

        return result;
    }

    private List<String> listMonths() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT month FROM ai_token_usage ORDER BY month DESC", String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String currentMonth() {
        return LocalDate.now().format(MONTH_FMT);
    }

    private long parseLongSafe(String value, long defaultValue) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
