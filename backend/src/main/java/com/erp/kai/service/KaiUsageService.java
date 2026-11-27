package com.erp.kai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 用量统计与月配额（FEAT-KAI-STANDALONE-004）。
 * 每轮对话结束后采集 token 用量按用户按月聚合落库；
 * 月配额存 kai_user.monthly_quota（0=不限），超额拒绝并给中文提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiUsageService {

    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 累加本月用量（并发安全：ON CONFLICT 原子累加）。 */
    public void collect(Long kaiUserId, long promptTokens, long completionTokens, long totalTokens) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO kai_usage (kai_user_id, month, prompt_tokens, completion_tokens, total_tokens, call_count)
                    VALUES (?,?,?,?,?,1)
                    ON CONFLICT (kai_user_id, month) DO UPDATE SET
                        prompt_tokens = kai_usage.prompt_tokens + EXCLUDED.prompt_tokens,
                        completion_tokens = kai_usage.completion_tokens + EXCLUDED.completion_tokens,
                        total_tokens = kai_usage.total_tokens + EXCLUDED.total_tokens,
                        call_count = kai_usage.call_count + 1
                    """, kaiUserId, MONTH.format(LocalDate.now()), promptTokens, completionTokens, totalTokens);
        } catch (Exception e) {
            log.warn("[小楷独立版] 用量落库失败: {}", e.getMessage());
        }
    }

    /** 本月已用总量。 */
    public long monthUsed(Long kaiUserId) {
        try {
            Long t = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(total_tokens),0) FROM kai_usage WHERE kai_user_id=? AND month=?",
                    Long.class, kaiUserId, MONTH.format(LocalDate.now()));
            return t == null ? 0 : t;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 月配额（0=不限）。 */
    public long quotaOf(Long kaiUserId) {
        try {
            Long q = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(monthly_quota,0) FROM kai_user WHERE id=?", Long.class, kaiUserId);
            return q == null ? 0 : q;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 配额判定：超额抛中文异常。 */
    public void checkQuota(Long kaiUserId) {
        long quota = quotaOf(kaiUserId);
        if (quota <= 0) return;
        long used = monthUsed(kaiUserId);
        if (used >= quota) {
            throw new IllegalArgumentException("本月 AI 用量已用完（已消耗 " + used + " / 配额 " + quota
                    + " tokens），可在「设置 → 用量管理」调整配额后继续");
        }
    }

    /** 用量页数据：本月 + 近 6 个月。 */
    public Map<String, Object> overview(Long kaiUserId) {
        String cur = MONTH.format(LocalDate.now());
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> months = new java.util.ArrayList<>();
        jdbcTemplate.query("""
                SELECT month, prompt_tokens, completion_tokens, total_tokens, call_count
                FROM kai_usage WHERE kai_user_id = ? ORDER BY month DESC LIMIT 6
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", rs.getString("month"));
            m.put("promptTokens", rs.getLong("prompt_tokens"));
            m.put("completionTokens", rs.getLong("completion_tokens"));
            m.put("totalTokens", rs.getLong("total_tokens"));
            m.put("callCount", rs.getInt("call_count"));
            months.add(m);
        }, kaiUserId);
        Map<String, Object> curRow = new LinkedHashMap<>();
        curRow.put("month", cur);
        curRow.put("totalTokens", monthUsed(kaiUserId));
        curRow.put("quota", quotaOf(kaiUserId));
        out.put("current", curRow);
        out.put("months", months);
        return out;
    }

    /** 设置月配额（0=不限）。 */
    public void setQuota(Long kaiUserId, long quota) {
        jdbcTemplate.update("UPDATE kai_user SET monthly_quota = ? WHERE id = ?",
                Math.max(0, quota), kaiUserId);
    }
}
