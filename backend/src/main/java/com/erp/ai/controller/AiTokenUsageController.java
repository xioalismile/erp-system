package com.erp.ai.controller;

import com.erp.ai.credit.AiCreditService;
import com.erp.ai.service.AiConfigService;
import com.erp.ai.service.AiTokenUsageService;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI Token用量管理控制器
 * 设置中心「AI用量管理」页签：租户额度 + 用户额度 + 月度用量统计
 *
 * 权限码（不按角色名，按 sys_menu.permission_code）：
 *   ai:quota:tenant — 管理租户总额度
 *   ai:quota:user   — 管理用户专属额度
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/token-usage")
@RequiredArgsConstructor
public class AiTokenUsageController {

    private final AiTokenUsageService aiTokenUsageService;
    private final AiConfigService aiConfigService;
    private final AiCreditService aiCreditService;

    /**
     * 月度用量统计（month缺省=当月）
     * 返回 {month, months, tenantQuota, tenantQuotaAllocated, tenantQuotaRemaining,
     *        hasTenantOwnKey, defaultQuota, rows:[...]}
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) String month) {
        return Result.success(aiTokenUsageService.monthlyStats(month));
    }

    /** 获取全局默认配额（只读，无需特殊权限） */
    @GetMapping("/quota")
    public Result<Map<String, Object>> getQuota() {
        return Result.success(Map.of("defaultQuota", aiConfigService.getDefaultQuota()));
    }

    /** 设置全局默认配额 body: {quota: number}，0=不限。需租户额度管理权限。 */
    @PostMapping("/quota")
    public Result<Void> setQuota(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:quota:tenant")) {
            return Result.error(403, "无权限修改全局默认配额");
        }
        Long quota = parseQuota(body.get("quota"));
        if (quota == null) {
            return Result.error("配额值无效（须为不小于0的整数）");
        }
        aiConfigService.setDefaultQuota(quota);
        log.info("[AI用量] 全局默认配额已更新: {}", quota);
        return Result.success(null);
    }

    /**
     * 获取租户额度汇总：总额度、已分配给用户的额度之和、剩余可用。
     * 返回 {tenantQuota, allocated, remaining, hasTenantOwnKey}
     */
    @GetMapping("/tenant-quota")
    public Result<Map<String, Object>> getTenantQuota() {
        long tenantQuota = aiConfigService.getTenantQuota();
        long allocated = aiConfigService.getAllUserQuotaSum();
        long remaining = tenantQuota > 0 ? Math.max(0, tenantQuota - allocated) : -1;
        return Result.success(Map.of(
                "tenantQuota", tenantQuota,
                "allocated", allocated,
                "remaining", remaining,
                "hasTenantOwnKey", aiConfigService.hasTenantChannelApiKey(aiConfigService.getDefaultChannel())
        ));
    }

    /**
     * 设置租户月度总额度 body: {quota: number}，0=不限。
     * 需 ai:quota:tenant 权限。
     */
    @PostMapping("/tenant-quota")
    public Result<Void> setTenantQuota(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:quota:tenant")) {
            return Result.error(403, "无权限修改租户总额度");
        }
        Long quota = parseQuota(body.get("quota"));
        if (quota == null) {
            return Result.error("配额值无效（须为不小于0的整数）");
        }
        // 若新额度 < 已分配给用户的额度之和，拒绝（避免总额度小于已分配）
        long allocated = aiConfigService.getAllUserQuotaSum();
        if (quota > 0 && allocated > quota) {
            return Result.error("租户总额度不能小于已分配给用户的额度之和（已分配：" + allocated + "）");
        }
        aiConfigService.setTenantQuota(quota);
        log.info("[AI用量] 租户总额度已更新: {}（已分配用户额度之和：{}）", quota, allocated);
        return Result.success(null);
    }

    /**
     * 设置/清除用户专属配额 body: {userId: number, quota?: number}
     * quota缺省=null=清除专属配置（恢复继承租户额度）。
     * 需 ai:quota:user 权限。聚合校验：sum(用户配额) ≤ 租户总额度。
     */
    @PostMapping("/user-quota")
    public Result<Void> setUserQuota(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:quota:user")) {
            return Result.error(403, "无权限修改用户专属配额");
        }
        Object userIdObj = body.get("userId");
        if (!(userIdObj instanceof Number n)) {
            return Result.error("用户ID无效");
        }
        Long userId = n.longValue();
        Object quotaObj = body.get("quota");

        // 清除专属配额
        if (quotaObj == null) {
            aiConfigService.clearUserQuota(userId);
            log.info("[AI用量] 用户 {} 专属配额已清除（继承租户额度）", userId);
            return Result.success(null);
        }

        Long quota = parseQuota(quotaObj);
        if (quota == null) {
            return Result.error("配额值无效（须为不小于0的整数）");
        }

        // 聚合校验：新配额 + 其他用户配额之和 ≤ 租户总额度
        long tenantQuota = aiConfigService.getTenantQuota();
        if (tenantQuota > 0) {
            long otherUsersSum = aiConfigService.getAllUserQuotaSum();
            String existing = aiConfigService.getUserQuota(userId);
            if (existing != null && !existing.isBlank()) {
                try { otherUsersSum -= Long.parseLong(existing.trim()); } catch (NumberFormatException ignore) {}
            }
            long newAllocated = otherUsersSum + quota;
            if (newAllocated > tenantQuota) {
                long remaining = Math.max(0, tenantQuota - otherUsersSum);
                return Result.error("已超出租户可用额度（剩余：" + remaining + " tokens），"
                        + "租户总额度：" + tenantQuota + "，已分配给其他用户：" + otherUsersSum);
            }
        }

        aiConfigService.setUserQuota(userId, quota);
        log.info("[AI用量] 用户 {} 专属配额已更新: {}", userId, quota);
        return Result.success(null);
    }

    /**
     * 获取租户消耗系数（默认 0.5，即消耗按 1.5 倍计量）。
     * 只读，无需特殊权限（页面回显用）。
     */
    @GetMapping("/usage-ratio")
    public Result<Map<String, Object>> getUsageRatio() {
        return Result.success(Map.of(
                "ratio", aiConfigService.getTenantUsageRatio(),
                "defaultRatio", AiConfigService.DEFAULT_USAGE_RATIO,
                "maxRatio", AiConfigService.MAX_USAGE_RATIO
        ));
    }

    /**
     * 设置租户消耗系数 body: {ratio: number}，取值 0 ~ MAX，0=不加价。
     * 口径：仅影响"消耗"与"配额扣减"（记账token = 实际token × (1+系数)），不影响额度分配。
     * 需 ai:quota:tenant 权限。
     */
    @PostMapping("/usage-ratio")
    public Result<Void> setUsageRatio(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:quota:tenant")) {
            return Result.error(403, "无权限修改租户消耗系数");
        }
        Double ratio = parseRatio(body.get("ratio"));
        if (ratio == null) {
            return Result.error("系数无效（须为 0 ~ " + AiConfigService.MAX_USAGE_RATIO + " 之间的数字）");
        }
        aiConfigService.setTenantUsageRatio(ratio);
        log.info("[AI用量] 租户消耗系数已更新: {}", ratio);
        return Result.success(null);
    }

    /** 系数参数解析：0 ~ MAX 之间的有限数字才有效 */
    private Double parseRatio(Object v) {
        double d;
        if (v instanceof Number n) {
            d = n.doubleValue();
        } else if (v instanceof String s && !s.isBlank()) {
            try {
                d = Double.parseDouble(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        } else {
            return null;
        }
        if (Double.isNaN(d) || Double.isInfinite(d) || d < 0 || d > AiConfigService.MAX_USAGE_RATIO) {
            return null;
        }
        return d;
    }

    // ============================================================
    // 积分制（2026-10-24）：与独立版小楷同口径
    // ============================================================

    /** 设置中心积分页：全局积分规则 + 公司总积分汇总 */
    @GetMapping("/credit-config")
    public Result<Map<String, Object>> creditConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rules", aiCreditService.globalRules());
        data.put("tenant", aiCreditService.tenantSummary());
        return Result.success(data);
    }

    /** 管理员：从公司总积分划拨给员工（amount 必须大于 0） */
    @PostMapping("/user-credit")
    public Result<Map<String, Object>> allocateUserCredit(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:quota:user")) {
            return Result.error(403, "无权限给员工分配积分");
        }
        Object uid = body.get("userId");
        if (!(uid instanceof Number n)) {
            return Result.error("请先选择员工");
        }
        BigDecimal amount = parseDecimal(body.get("amount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("分配积分必须大于 0");
        }
        String username = body.get("username") == null ? "" : String.valueOf(body.get("username"));
        String remark = body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
        BigDecimal balance = aiCreditService.allocateToUser(n.longValue(), username, amount, remark);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", balance);
        data.put("tenant", aiCreditService.tenantSummary());
        return Result.success(data);
    }

    /** 管理员：收回员工个人积分，恢复共用公司总积分 */
    @PostMapping("/user-credit/reclaim")
    public Result<Map<String, Object>> reclaimUserCredit(@RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:quota:user")) {
            return Result.error(403, "无权限收回员工积分");
        }
        Object uid = body.get("userId");
        if (!(uid instanceof Number n)) {
            return Result.error("请先选择员工");
        }
        String username = body.get("username") == null ? "" : String.valueOf(body.get("username"));
        String remark = body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
        aiCreditService.reclaimFromUser(n.longValue(), username, remark);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenant", aiCreditService.tenantSummary());
        return Result.success(data);
    }

    /** 小数解析（积分支持 4 位小数） */
    private BigDecimal parseDecimal(Object v) {
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    /** 配额参数解析：非负整数才有效 */
    private Long parseQuota(Object v) {
        if (v instanceof Number n) {
            long q = n.longValue();
            return q >= 0 ? q : null;
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                long q = Long.parseLong(s.trim());
                return q >= 0 ? q : null;
            } catch (NumberFormatException ignore) {
                // 落入兜底返回null
            }
        }
        return null;
    }
}
