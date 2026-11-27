package com.erp.ai.service;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.config.CacheService;
import com.erp.config.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AI写操作两段式确认服务
 *
 * 流程：
 * 1. ReAct循环中AI决定调用写工具时，不直接执行，而是 createPending() 暂存操作（Redis，5分钟）
 *    并向前端推送 CONFIRM_NEEDED 步骤（含 token + 摘要），中止本轮写操作
 * 2. 用户在确认卡片点"确认" → 前端调 confirm(token) → 服务端二次校验权限与用户一致性后真正执行
 *
 * 安全要点：
 * - token 一次性：确认/取消后立即删除，防重放
 * - 用户一致性：确认者必须是发起者（按 userId 比对）
 * - 执行前二次权限校验（开关可能在等待期间被关闭）
 */
@Slf4j
@Service
public class AiConfirmService {

    private static final String KEY_PREFIX = "ai:confirm:tool:";
    private static final long TTL_MINUTES = 5;

    private final CacheService cache;
    private final ToolRegistry toolRegistry;
    private final SysConfigService sysConfigService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 部署级兜底值（application.yml / 环境变量 erp.ai.confirm-write-enabled，默认 true）。
     * 仅当 sys_config 中没有 ai.confirm.write.enabled 时才使用 —— 保证"宁可要确认，也不误执行"。
     */
    private final boolean envConfirmWriteEnabled;

    public AiConfirmService(CacheService cache, ToolRegistry toolRegistry, Environment env,
                            SysConfigService sysConfigService) {
        this.cache = cache;
        this.toolRegistry = toolRegistry;
        this.sysConfigService = sysConfigService;
        this.envConfirmWriteEnabled = env.getProperty("erp.ai.confirm-write-enabled", Boolean.class, true);
    }

    /**
     * 是否开启「写操作强制人工确认」。
     *
     * <p>运行时读取 sys_config（设置中心 → AI 工具权限 → 「AI写操作需人工确认」），
     * 改完**立即生效、无需重启**；配置缺失或读取异常时回退部署级兜底值（默认 true = 需要确认）。
     */
    public boolean isConfirmWriteEnabled() {
        try {
            return sysConfigService.getBooleanValue("ai.confirm.write.enabled", envConfirmWriteEnabled);
        } catch (Exception e) {
            log.warn("[AI确认] 读取写确认开关失败，回退默认值 {}: {}", envConfirmWriteEnabled, e.getMessage());
            return envConfirmWriteEnabled;
        }
    }

    /**
     * 是否属于「不可逆动作」—— 即使关闭了写确认开关，这类动作**仍必须**弹卡片二次确认（安全底线，用户选定 B 方案）。
     *
     * <p>规则（写死，不开放给客户自行放开）：
     * <ul>
     *   <li>工具名含 delete 的各类删除工具（如 crud_delete、setting_*_delete）；</li>
     *   <li>bill_action 的 delete（删除）/ void（作废）；</li>
     *   <li>bill_batch_action 的 delete（批量删除）——批量同样不可逆，必须与单张口径一致。</li>
     * </ul>
     * 可逆动作（审批 / 提交 / 驳回 / 重置草稿 / 下推 / 关闭 / 结案等都有对应反动作）不受此限。
     */
    public boolean isIrreversibleCall(String toolName, Map<String, Object> params) {
        if (toolName == null) return false;
        String t = toolName.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("delete")) return true;
        if ("bill_action".equals(t) || "bill_batch_action".equals(t)) {
            Object action = params == null ? null : params.get("action");
            String a = action == null ? "" : String.valueOf(action).trim();
            return "delete".equals(a) || "void".equals(a);
        }
        return false;
    }

    /**
     * 暂存待确认的写操作，返回确认token
     */
    public String createPending(String toolName, Map<String, Object> params, AiDataScope scope, String summary) throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", toolName);
        data.put("params", params);
        data.put("userId", scope != null ? scope.getUserId() : null);
        data.put("username", scope != null ? scope.getUsername() : null);
        data.put("summary", summary);
        // 展示口径偏好随待确认记录一起暂存：用户点"确认执行"时是**另一次请求**，
        // 执行结果文案/语音播报必须与确认卡片同口径（服务端暂存，不依赖前端二次传值）
        data.put("displayPrefs", scope != null ? scope.getDisplayPrefs() : null);
        cache.set(KEY_PREFIX + token, mapper.writeValueAsString(data), TTL_MINUTES, TimeUnit.MINUTES);
        log.info("[AI确认] 待确认写操作已暂存 tool={} user={} summary={}", toolName, scope != null ? scope.getUsername() : null, summary);
        return token;
    }

    /**
     * 确认并执行（一次性token，执行前二次权限校验）
     */
    public Map<String, Object> confirm(String token, AiDataScope scope) {
        String key = KEY_PREFIX + token;
        String json = cache.get(key);
        if (json == null) {
            return Map.of("success", false, "message", "确认令牌无效或已过期，请重新发起操作");
        }
        try {
            // 先删（一次性），无论成功失败都不可重放
            cache.delete(key);

            Map<String, Object> data = mapper.readValue(json, Map.class);
            String toolName = (String) data.get("toolName");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) data.get("params");
            Long pendingUserId = data.get("userId") != null ? Long.valueOf(data.get("userId").toString()) : null;

            // 打码值防呆（2026-11-17 C 方案）：打码单向不可还原，含 **** 的参数一律不落库
            String maskedPath = com.erp.ai.util.SensitiveDataMasker.findMaskedValuePath(params, "");
            if (maskedPath != null) {
                log.warn("[AI确认] 参数含打码值，拒绝执行 tool={} path={}", toolName, maskedPath);
                return Map.of("success", false, "message",
                        "参数「" + maskedPath + "」里包含打码值（形如 138****8000），不能保存。请把真实内容发给小楷后重新发起。");
            }

            // 用户一致性：确认者必须是发起者
            if (scope == null || scope.getUserId() == null || !scope.getUserId().equals(pendingUserId)) {
                log.warn("[AI确认] 用户不一致拒绝执行 pendingUser={} confirmUser={}", pendingUserId, scope != null ? scope.getUserId() : null);
                return Map.of("success", false, "message", "确认用户与发起用户不一致，已拒绝执行");
            }

            // 执行前二次权限校验
            String denyReason = toolRegistry.checkPermission(toolName, scope);
            if (denyReason != null) {
                return Map.of("success", false, "message", "权限拒绝: " + denyReason);
            }

            // 还原发起时的展示口径偏好（单据标识方式），保证执行结果文案/播报与确认卡片一致
            applyDisplayPrefs(scope, data.get("displayPrefs"));

            AiTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                return Map.of("success", false, "message", "工具不存在: " + toolName);
            }

            log.info("[AI确认] 用户确认执行 tool={} user={} params={}", toolName, scope.getUsername(), params);
            Map<String, Object> result = tool.execute(params, scope);
            log.info("[AI确认] 执行完成 tool={} success={}", toolName, result.get("success"));
            return result;
        } catch (Exception e) {
            log.error("[AI确认] 执行失败", e);
            return Map.of("success", false, "message", "执行失败: " + e.getMessage());
        }
    }

    /**
     * 把待确认记录里暂存的展示口径偏好还原到 scope（纯文案措辞，无任何权限语义）。
     * 口径在发起时就经过服务端白名单校验并随记录暂存，确认阶段不再信任前端传值。
     */
    @SuppressWarnings("unchecked")
    private void applyDisplayPrefs(AiDataScope scope, Object prefs) {
        if (scope == null || !(prefs instanceof Map)) return;
        Object style = ((Map<String, Object>) prefs).get(com.erp.ai.bill.BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE);
        if (style == null) return;
        String s = String.valueOf(style).trim();
        if (!com.erp.ai.bill.BillCodeResolver.allowedStyles().contains(s)) return;
        Map<String, String> clean = new LinkedHashMap<>();
        clean.put(com.erp.ai.bill.BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE, s);
        scope.setDisplayPrefs(clean);
    }

    /** 取消待确认操作 */
    public Map<String, Object> cancel(String token) {
        String key = KEY_PREFIX + token;
        String json = cache.get(key);
        cache.delete(key);
        log.info("[AI确认] 用户取消待确认操作 token={} exists={}", token, json != null);
        return Map.of("success", true, "message", "已取消该操作，未做任何数据变更");
    }
}
