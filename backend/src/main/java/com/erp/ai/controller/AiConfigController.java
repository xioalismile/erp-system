package com.erp.ai.controller;

import com.erp.ai.service.AiConfigService;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * AI配置管理控制器
 * 用户可在前端设置/切换AI渠道的API Key
 * API Key加密存储，接口返回脱敏信息
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;

    /**
     * 是否可修改「调用地址」（base-url）（安全审计 H-1，采用"只禁调用地址"方案）。
     * 调用地址决定后端把哪个 Key 发往哪台服务器：普通用户改它 = 把平台级 API Key
     * 外送到攻击者服务器（AiConfigService.doChatTest 会用该地址发 Bearer <真实Key>）。
     * 因此仅管理岗可改；普通用户仍可正常填写/更新 API Key 与模型名（功能不变）。
     */
    private static boolean canEditBaseUrl() {
        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        return SecurityUtils.hasAnyRole(req, "ADMIN", "SUPER_ADMIN")
                || SecurityUtils.hasVersionAdminRole(req)
                || SecurityUtils.hasPermission(req, "*")
                || SecurityUtils.hasPermission(req, "system:settings:view");
    }

    /**
     * 获取所有AI渠道配置（脱敏）
     */
    @GetMapping("/channels")
    public Result<List<Map<String, Object>>> listChannels() {
        return Result.success(aiConfigService.listChannels());
    }

    /**
     * 保存渠道API Key
     * 用户在前端输入API Key，后端加密存储
     */
    @PostMapping("/apikey")
    public Result<Void> saveApiKey(@RequestBody Map<String, String> body) {
        String channel = body.get("channel");
        String apiKey = body.get("apiKey");

        if (channel == null || channel.isBlank()) {
            return Result.error("渠道不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Result.error("API Key不能为空");
        }

        String formatErr = aiConfigService.validateChannelApiKeyFormat(channel, apiKey);
        if (formatErr != null) {
            return Result.error(formatErr);
        }

        aiConfigService.saveChannelApiKey(channel, apiKey);
        log.info("[AI配置] 渠道 {} API Key已更新", channel);
        return Result.success(null);
    }

    /**
     * 保存渠道配置（baseUrl, model等）
     */
    @PostMapping("/channel-config")
    public Result<Void> saveChannelConfig(@RequestBody Map<String, String> body) {
        String channel = body.get("channel");
        String baseUrl = body.get("baseUrl");
        String model = body.get("model");

        if (channel == null || channel.isBlank()) {
            return Result.error("渠道不能为空");
        }
        // 安全审计 H-1：调用地址仅管理岗可改（防止把平台 Key 外送到攻击者服务器）
        if (baseUrl != null && !baseUrl.isBlank() && !canEditBaseUrl()) {
            return Result.error(403, "调用地址仅管理员可修改");
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            aiConfigService.saveChannelConfig(channel, "base-url", baseUrl);
        }
        if (model != null && !model.isBlank()) {
            aiConfigService.saveChannelConfig(channel, "model", model);
        }
        return Result.success(null);
    }

    /**
     * 测试渠道连通性
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> testChannel(@RequestBody Map<String, String> body) {
        String channel = body.get("channel");
        if (channel == null || channel.isBlank()) {
            return Result.error("渠道不能为空");
        }
        return Result.success(aiConfigService.testChannel(channel));
    }

    // ============================================================
    // 自定义模型服务商（多厂商）
    // 存储：sys_config 的 ai.providers（JSON 数组），API Key 加密存储、接口脱敏返回
    // ============================================================

    /**
     * 获取自定义服务商列表（apiKey 脱敏）
     */
    @GetMapping("/providers")
    public Result<List<Map<String, Object>>> listProviders() {
        return Result.success(aiConfigService.listCustomProviders());
    }

    /**
     * 新增/更新自定义服务商。
     * body: {id?, name, baseUrl, apiKey, models[], visionModel?, applyUrl?, enabled?}
     * id 为空=新增；apiKey 留空=不修改（编辑时不回显明文）
     */
    @PostMapping("/providers")
    public Result<Map<String, Object>> saveProvider(@RequestBody Map<String, Object> body) {
        // 安全审计 H-1：改动"已有服务商"的调用地址仅管理岗可做；新建自有服务商不受影响
        if (!canEditBaseUrl()) {
            String baseUrl = body.get("baseUrl") == null ? "" : String.valueOf(body.get("baseUrl")).trim();
            String id = body.get("id") == null ? "" : String.valueOf(body.get("id")).trim();
            if (!baseUrl.isBlank() && !id.isBlank()) {
                for (Map<String, Object> p : aiConfigService.listCustomProviders()) {
                    if (id.equals(String.valueOf(p.get("id")))) {
                        String old = p.get("baseUrl") == null ? "" : String.valueOf(p.get("baseUrl")).trim();
                        if (!old.equalsIgnoreCase(baseUrl)) {
                            return Result.error(403, "调用地址仅管理员可修改");
                        }
                        break;
                    }
                }
            }
        }
        return Result.success(aiConfigService.saveCustomProvider(body));
    }

    /**
     * 删除自定义服务商
     */
    @DeleteMapping("/providers")
    public Result<Void> deleteProvider(@RequestParam String id) {
        aiConfigService.deleteCustomProvider(id);
        return Result.success(null);
    }

    /**
     * 测试自定义服务商连通性。
     * body: {id} 或 {baseUrl, apiKey, model}
     */
    @PostMapping("/providers/test")
    public Result<Map<String, Object>> testProvider(@RequestBody Map<String, Object> body) {
        // 安全审计 H-1：带 baseUrl 的连通性测试会把该服务商已存的 Key 发到指定地址，
        // 仅管理岗可指定地址；按 id 测已有服务商（地址取自库中配置）不受影响。
        Object baseUrl = body.get("baseUrl");
        if (!canEditBaseUrl() && baseUrl != null && !String.valueOf(baseUrl).isBlank()) {
            return Result.error(403, "调用地址仅管理员可修改");
        }
        return Result.success(aiConfigService.testCustomProvider(body));
    }

    // ============================================================
    // 租户自有API Key（配置后AI调用免配额限制，费用自理）
    // 权限码：ai:key:tenant
    // ============================================================

    /**
     * 保存租户自有API Key（加密存储）。
     * 配置后该渠道优先使用租户key，AI调用免配额限制。
     */
    @PostMapping("/tenant-apikey")
    public Result<Void> saveTenantApiKey(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:key:tenant")) {
            return Result.error(403, "无权限配置租户自有API Key");
        }
        String channel = body.get("channel");
        String apiKey = body.get("apiKey");
        if (channel == null || channel.isBlank()) {
            return Result.error("渠道不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Result.error("API Key不能为空");
        }
        String formatErr = aiConfigService.validateChannelApiKeyFormat(channel, apiKey);
        if (formatErr != null) {
            return Result.error(formatErr);
        }
        aiConfigService.saveTenantChannelApiKey(channel, apiKey);
        log.info("[AI配置] 租户渠道 {} 自有API Key已更新", channel);
        return Result.success(null);
    }

    /**
     * 清除租户自有API Key（恢复使用平台key，恢复配额限制）。
     */
    @DeleteMapping("/tenant-apikey")
    public Result<Void> clearTenantApiKey(@RequestParam String channel, HttpServletRequest request) {
        if (!SecurityUtils.hasPermission(request, "ai:key:tenant")) {
            return Result.error(403, "无权限清除租户自有API Key");
        }
        if (channel == null || channel.isBlank()) {
            return Result.error("渠道不能为空");
        }
        aiConfigService.clearTenantChannelApiKey(channel);
        log.info("[AI配置] 租户渠道 {} 自有API Key已清除", channel);
        return Result.success(null);
    }

    /**
     * 获取数据使用政策
     * 包含：是否脱敏、是否记录日志原文、是否严格模式、AI厂商数据政策说明
     */
    @GetMapping("/data-policy")
    public Result<Map<String, Object>> getDataPolicy() {
        return Result.success(aiConfigService.getDataPolicy());
    }

    /**
     * 更新数据使用政策
     * body: { maskSensitive?: boolean, logRawContent?: boolean, strictMode?: boolean }
     */
    @PostMapping("/data-policy")
    public Result<Void> updateDataPolicy(@RequestBody Map<String, Object> body) {
        aiConfigService.updateDataPolicy(body);
        log.info("[AI配置] 数据使用政策已更新: {}", body.keySet());
        return Result.success(null);
    }

    /**
     * 获取对话能力设置（联网搜索 / 回答严谨度 / 回答详细度 / 上下文上限 + 渠道支持说明）
     */
    @GetMapping("/chat-capabilities")
    public Result<Map<String, Object>> getChatCapabilities() {
        return Result.success(aiConfigService.getChatCapabilities());
    }

    /**
     * 更新对话能力设置
     * body: { webSearch?: boolean, temperature?: number, maxTokens?: number, contextMaxTokens?: number }
     */
    @PostMapping("/chat-capabilities")
    public Result<Void> updateChatCapabilities(@RequestBody Map<String, Object> body) {
        aiConfigService.updateChatCapabilities(body);
        log.info("[AI配置] 对话能力设置已更新: {}", body == null ? null : body.keySet());
        return Result.success(null);
    }

    /**
     * 语音运行参数（内部）：等待指令时长（秒）。
     * 全局统一、客户界面无入口（平台参数在「AI 默认规则」里维护）；客户端唤醒前读取，改完无需重登。
     */
    @GetMapping("/voice-runtime")
    public Result<Map<String, Object>> getVoiceRuntime() {
        return Result.success(Map.of("cmdWaitSec", aiConfigService.getVoiceWaitSec()));
    }
}
