package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import com.erp.tenant.TenantContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI渠道配置管理服务
 * - API Key加密存储到sys_config表（AES-GCM加密）
 * - 加密密钥在首次启动时自动生成，存储在sys_config中，不硬编码
 * - 获取配置时自动解密
 * - 对外返回脱敏信息（sk-****1234）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService {

    /** 平台分配的租户月度总额度键（存主库 sys_config，平台管理→客户管理维护；0/未分配=不限） */
    private static final String PLATFORM_TENANT_QUOTA_PREFIX = "ai.tenant.quota.";

    private final SysConfigService sysConfigService;

    /** 直连主库（不走路由）：读平台统一渠道密钥与平台分配额度（租户上下文下 sysConfigService 只会读到租户库） */
    private final JdbcTemplate businessJdbcTemplate;

    private static final String CONFIG_GROUP = "ai";
    private static final String CRYPTO_KEY_CONFIG = "ai.crypto.secret";
    private static final String CHANNELS_CONFIG = "ai.channels";
    /** 自定义模型服务商列表（多厂商），值为 JSON 数组 */
    private static final String PROVIDERS_CONFIG = "ai.providers";

    private SecretKey secretKey;
    private byte[] iv;

    @PostConstruct
    public void init() {
        try {
            initCryptoKey();
            log.info("[AI配置] 加密密钥初始化完成");
        } catch (Exception e) {
            log.error("[AI配置] 加密密钥初始化失败", e);
        }
    }

    /**
     * 初始化加密密钥：从数据库读取，不存在则自动生成
     */
    private void initCryptoKey() {
        String storedKey = sysConfigService.getValue(CRYPTO_KEY_CONFIG);
        if (storedKey != null && !storedKey.isBlank()) {
            String[] parts = storedKey.split(":");
            if (parts.length == 2) {
                byte[] keyBytes = Base64.getDecoder().decode(parts[0]);
                this.secretKey = new SecretKeySpec(keyBytes, "AES");
                this.iv = Base64.getDecoder().decode(parts[1]);
                return;
            }
        }
        // 首次启动：生成新密钥
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            this.secretKey = keyGen.generateKey();
            this.iv = new byte[12];
            new SecureRandom().nextBytes(this.iv);

            String keyStr = Base64.getEncoder().encodeToString(secretKey.getEncoded())
                    + ":" + Base64.getEncoder().encodeToString(iv);
            saveConfig(CRYPTO_KEY_CONFIG, keyStr, "AI加密密钥", "ai", "secret", "系统自动生成，请勿修改");
            log.info("[AI配置] 首次生成加密密钥并存储");
        } catch (Exception e) {
            throw new RuntimeException("生成加密密钥失败", e);
        }
    }

    /**
     * 加密
     */
    private String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "ENC:" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密
     */
    private String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith("ENC:")) {
            return cipherText; // 未加密的明文直接返回（兼容旧数据）
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] decoded = Base64.getDecoder().decode(cipherText.substring(4));
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败", e);
            return null;
        }
    }

    /**
     * 保存配置到sys_config表
     */
    private void saveConfig(String key, String value, String name, String group, String type, String remark) {
        SysConfig config = sysConfigService.getOne(
                new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigName(name);
            config.setConfigGroup(group);
            config.setConfigType(type);
            config.setRemark(remark);
            config.setSortOrder(0);
            config.setConfigValue(value);
            sysConfigService.save(config);
        } else {
            config.setConfigValue(value);
            sysConfigService.updateById(config);
        }
        sysConfigService.refreshCache();
    }

    /**
     * 保存渠道API Key（加密存储）
     */
    public void saveChannelApiKey(String channel, String apiKey) {
        String key = "ai." + channel.toLowerCase() + ".api-key";
        String encrypted = encrypt(apiKey);
        saveConfig(key, encrypted, channel + " API Key", CONFIG_GROUP, "secret",
                "AI渠道 " + channel + " 的API密钥，加密存储");
        log.info("[AI配置] 渠道 {} API Key已更新", channel);
    }

    /**
     * 获取渠道API Key（解密后的明文，仅内部使用）。
     * 取值优先级：租户自有 key（ai.{channel}.api-key.tenant）→ 租户库渠道 key → 平台统一 key（主库）。
     * 租户配了自有key时，AI调用免配额限制（费用自理）；
     * 租户库未配置任何 key 时自动回退平台统一 key——平台配了 DeepSeek 等渠道，
     * 所有客户（含新增客户）开箱即用，无需各自配置。
     */
    public String getChannelApiKey(String channel) {
        String tenantKey = getTenantChannelApiKey(channel);
        if (tenantKey != null && !tenantKey.isBlank()) {
            return tenantKey;
        }
        String key = "ai." + channel.toLowerCase() + ".api-key";
        String value = sysConfigService.getValue(key);
        String plain = decrypt(value);
        if (plain != null && !plain.isBlank()) {
            return plain;
        }
        // 租户库没配 → 回退平台（主库）统一 key（新客户开箱即用）
        return getPlatformChannelApiKey(channel);
    }

    /** 当前是否处于租户独立库上下文（主库 erp_db / 未登录不算） */
    private boolean isTenantDbContext() {
        String dbName = TenantContextHolder.getTenantDbName();
        return dbName != null && !dbName.isBlank() && !"erp_db".equalsIgnoreCase(dbName.trim());
    }

    /**
     * 读取平台（主库）统一的渠道 API Key（解密后明文），null=平台未配置。
     * 仅租户独立库上下文需要跨库读取；密文统一由主库的加密密钥加解密（单例密钥即主库生成）。
     */
    private String getPlatformChannelApiKey(String channel) {
        if (!isTenantDbContext()) {
            return null;
        }
        try {
            String key = "ai." + channel.toLowerCase() + ".api-key";
            List<Map<String, Object>> rows = businessJdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ?", key);
            if (rows.isEmpty()) {
                return null;
            }
            Object v = rows.get(0).get("config_value");
            return decrypt(v == null ? null : String.valueOf(v));
        } catch (Exception e) {
            log.warn("[AI配置] 读取平台统一渠道Key失败（{}）: {}", channel, e.getMessage());
            return null;
        }
    }

    /** 获取租户级渠道API Key（解密后明文），null=未配置 */
    public String getTenantChannelApiKey(String channel) {
        String key = "ai." + channel.toLowerCase() + ".api-key.tenant";
        String value = sysConfigService.getValue(key);
        return decrypt(value);
    }

    /** 保存租户级渠道API Key（加密存储） */
    public void saveTenantChannelApiKey(String channel, String apiKey) {
        String key = "ai." + channel.toLowerCase() + ".api-key.tenant";
        String encrypted = encrypt(apiKey);
        saveConfig(key, encrypted, channel + " 租户API Key", CONFIG_GROUP, "secret",
                "租户自有API密钥（优先于平台key），加密存储。配置后AI调用免配额限制。");
        log.info("[AI配置] 租户渠道 {} API Key已更新", channel);
    }

    /** 清除租户级渠道API Key（恢复使用平台key） */
    public void clearTenantChannelApiKey(String channel) {
        String key = "ai." + channel.toLowerCase() + ".api-key.tenant";
        sysConfigService.remove(new QueryWrapper<SysConfig>().eq("config_key", key));
        sysConfigService.refreshCache();
        log.info("[AI配置] 租户渠道 {} API Key已清除（回退平台key）", channel);
    }

    /** 租户是否配置了自有API Key */
    public boolean hasTenantChannelApiKey(String channel) {
        String key = getTenantChannelApiKey(channel);
        return key != null && !key.isBlank();
    }

    // ============================================================
    // 平台统一模型密钥（2026-10-24 积分制）
    //   平台管理→客户管理「AI 默认规则」维护；所有客户未单独配置时自动回退这把密钥，
    //   新客户开箱即可在模型下拉里选择对应模型。
    // ============================================================

    /** 平台统一渠道配置（只回是否已配置，不回密钥任何字符） */
    public Map<String, Object> getPlatformChannelSettings(String channel) {
        String ch = channel == null || channel.isBlank() ? "DEEPSEEK" : channel.toUpperCase();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("channel", ch);
        String plain = readPlatformChannelKey(ch);
        out.put("hasApiKey", plain != null && !plain.isBlank());
        out.put("baseUrl", readPlatformConfig("ai." + ch.toLowerCase() + ".base-url", getDefaultBaseUrl(ch)));
        out.put("model", readPlatformConfig("ai." + ch.toLowerCase() + ".model", getDefaultModel(ch)));
        return out;
    }

    /**
     * 保存平台统一渠道配置。apiKey 留空 = 保持原值；"__CLEAR__" = 清空。
     * 写主库 sys_config，租户库读取时按现有回退链路自动使用。
     */
    public void savePlatformChannelSettings(String channel, String baseUrl, String model, String apiKey) {
        String ch = channel == null || channel.isBlank() ? "DEEPSEEK" : channel.toUpperCase();
        if (baseUrl != null && !baseUrl.isBlank()) {
            upsertPlatformConfig("ai." + ch.toLowerCase() + ".base-url", baseUrl.trim(),
                    ch + " 平台调用地址", "平台统一模型调用地址");
        }
        if (model != null && !model.isBlank()) {
            upsertPlatformConfig("ai." + ch.toLowerCase() + ".model", model.trim(),
                    ch + " 平台模型名称", "平台统一模型名称");
        }
        if (apiKey != null) {
            String raw = apiKey.trim();
            if ("__CLEAR__".equals(raw)) {
                upsertPlatformConfig("ai." + ch.toLowerCase() + ".api-key", "",
                        ch + " 平台API Key", "平台统一模型密钥（已清空）");
            } else if (!raw.isEmpty()) {
                String err = validateChannelApiKeyFormat(ch, raw);
                if (err != null) {
                    throw new BusinessException(err);
                }
                upsertPlatformConfig("ai." + ch.toLowerCase() + ".api-key", encrypt(raw),
                        ch + " 平台API Key", "平台统一模型密钥，加密存储；客户未单独配置时自动回退");
            }
        }
        log.info("[AI配置] 平台统一渠道配置已更新: channel={}", ch);
    }

    /** 读主库渠道密钥明文（不依赖当前是否租户上下文） */
    private String readPlatformChannelKey(String channel) {
        String key = "ai." + channel.toLowerCase() + ".api-key";
        try {
            List<Map<String, Object>> rows = businessJdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ?", key);
            if (rows.isEmpty()) return null;
            Object v = rows.get(0).get("config_value");
            return decrypt(v == null ? null : String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private String readPlatformConfig(String key, String defaultValue) {
        try {
            List<Map<String, Object>> rows = businessJdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ?", key);
            if (rows.isEmpty()) return defaultValue;
            Object v = rows.get(0).get("config_value");
            String s = v == null ? "" : String.valueOf(v);
            return s.isBlank() ? defaultValue : s;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void upsertPlatformConfig(String key, String value, String name, String remark) {
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
            throw new BusinessException("保存平台模型配置失败: " + e.getMessage());
        }
    }

    // ===== 全局语音参数（内部）：等待指令时长 =====
    // 存主库 sys_config，全局统一、客户界面无入口；前端唤醒前读取，改完"一键应用"即生效。
    public static final String VOICE_WAIT_SEC_KEY = "ai.voice.cmdWaitSec";
    private static final int VOICE_WAIT_SEC_DEFAULT = 5;

    /** 读取全局「等待指令时长」（秒）；未配置/非法值按 5 秒 */
    public int getVoiceWaitSec() {
        String v = readPlatformConfig(VOICE_WAIT_SEC_KEY, String.valueOf(VOICE_WAIT_SEC_DEFAULT));
        try {
            int n = Integer.parseInt(String.valueOf(v).trim());
            return n > 0 ? n : VOICE_WAIT_SEC_DEFAULT;
        } catch (Exception e) {
            return VOICE_WAIT_SEC_DEFAULT;
        }
    }

    /** 保存全局「等待指令时长」（秒）。只校验必须大于 0，不限制区间。 */
    public void saveVoiceWaitSec(Integer sec) {
        if (sec == null || sec <= 0) {
            throw new BusinessException("等待指令时长必须大于 0 秒");
        }
        upsertPlatformConfig(VOICE_WAIT_SEC_KEY, String.valueOf(sec),
                "语音等待指令时长",
                "全局语音参数：唤醒后等待指令的秒数，超时播报待命并结束本次语音对话");
    }

    /**
     * 获取渠道配置（base-url, model等）
     */
    public String getChannelConfig(String channel, String configName, String defaultValue) {
        String key = "ai." + channel.toLowerCase() + "." + configName;
        String v = sysConfigService.getValue(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        // 租户库没配 → 回退平台统一配置（2026-10-24 积分制：新客户自动带入平台默认模型/地址）
        String platformValue = readPlatformConfig(key, null);
        if (platformValue != null && !platformValue.isBlank()) {
            return platformValue;
        }
        return defaultValue;
    }

    /**
     * 保存渠道配置
     */
    public void saveChannelConfig(String channel, String configName, String value) {
        String key = "ai." + channel.toLowerCase() + "." + configName;
        saveConfig(key, value, channel + " " + configName, CONFIG_GROUP, "string", "");
    }

    /**
     * 获取所有渠道状态（脱敏）
     */
    public List<Map<String, Object>> listChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();

        // 通义千问
        channels.add(buildChannelInfo("QWEN", "通义千问",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                "https://bailian.console.aliyun.com/"));

        // DeepSeek
        channels.add(buildChannelInfo("DEEPSEEK", "DeepSeek",
                "https://api.deepseek.com", "deepseek-flash",
                "https://platform.deepseek.com/"));

        // 本地大模型（Ollama）：数据不出内网，无需API Key
        channels.add(buildChannelInfo("OLLAMA", "本地模型（Ollama）",
                "http://127.0.0.1:11434/v1", "qwen2.5:7b",
                "https://ollama.com/"));

        return channels;
    }

    private Map<String, Object> buildChannelInfo(String channel, String name,
                                                  String defaultBaseUrl, String defaultModel,
                                                  String applyUrl) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("channel", channel);
        info.put("name", name);
        String apiKey = getChannelApiKey(channel);
        // 安全：接口只回"是否已配置"，不回密钥任何字符（含掩码片段），避免 F12/抓包看到密钥
        info.put("hasApiKey", apiKey != null && !apiKey.isBlank());
        info.put("baseUrl", getChannelConfig(channel, "base-url", defaultBaseUrl));
        info.put("model", getChannelConfig(channel, "model", defaultModel));
        info.put("applyUrl", applyUrl);
        info.put("enabled", apiKey != null && !apiKey.isBlank());
        // 租户自有Key标识：配了租户key后AI调用免配额限制（费用自理）
        info.put("hasTenantApiKey", hasTenantChannelApiKey(channel));
        return info;
    }

    /**
     * API Key格式校验
     */
    public String validateApiKey(String channel, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "API Key不能为空";
        }
        if (apiKey.length() < 10) {
            return "API Key长度过短，至少需要10个字符";
        }
        if ("QWEN".equalsIgnoreCase(channel) && !apiKey.startsWith("sk-")) {
            return "通义千问API Key必须以 sk- 开头";
        }
        if ("DEEPSEEK".equalsIgnoreCase(channel) && !apiKey.startsWith("sk-")) {
            return "DeepSeekAPI Key必须以 sk- 开头";
        }
        // OLLAMA为本地服务，无需真实API Key，无格式要求
        if ("OLLAMA".equalsIgnoreCase(channel)) {
            return null;
        }
        return null;
    }

    /**
     * 保存渠道API Key前的格式校验：只校验硬性前缀，不做长度/在线校验。
     * 目的是把"别家厂商的 Key 填进本渠道"挡在保存环节，避免调用时才炸成 401。
     * 返回 null 表示通过。
     */
    public String validateChannelApiKeyFormat(String channel, String apiKey) {
        if ("QWEN".equalsIgnoreCase(channel) && !apiKey.startsWith("sk-")) {
            return "通义千问 API Key 必须以 sk- 开头，当前填入的不是百炼 Key（其它厂商的 Key 请填到「自定义服务商」）";
        }
        if ("DEEPSEEK".equalsIgnoreCase(channel) && !apiKey.startsWith("sk-")) {
            return "DeepSeek API Key 必须以 sk- 开头";
        }
        return null;
    }

    /**
     * 测试渠道连通性（真实调用AI服务，最小Token消耗）
     */
    public Map<String, Object> testChannel(String channel) {
        String apiKey = getChannelApiKey(channel);
        String validationErr = validateApiKey(channel, apiKey);
        if (validationErr != null) {
            return Map.of("success", false, "message", validationErr);
        }

        String baseUrl = getChannelConfig(channel, "base-url", getDefaultBaseUrl(channel));
        String model = getChannelConfig(channel, "model", getDefaultModel(channel));
        // 本地模型首次调用需加载模型到内存，超时放宽
        boolean isLocal = "OLLAMA".equalsIgnoreCase(channel);
        return doChatTest(baseUrl, apiKey, model, isLocal, channel);
    }

    /**
     * 通用连通性测试：向 {baseUrl}/chat/completions 发一条最小请求。
     * 内置渠道与自定义服务商共用，避免逻辑分叉。
     */
    private Map<String, Object> doChatTest(String baseUrl, String apiKey, String model,
                                           boolean isLocal, String label) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Map.of("success", false, "message", "请先填写调用地址");
        }
        if (model == null || model.isBlank()) {
            return Map.of("success", false, "message", "请先填写模型名称");
        }
        try {
            String url = baseUrl + "/chat/completions";
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(isLocal ? 180 : 30, TimeUnit.SECONDS)
                    .build();

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", "hi")),
                    "temperature", 0,
                    "max_tokens", 5
            );
            String json = new ObjectMapper().writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "unknown";
                if (response.isSuccessful()) {
                    log.info("[AI连通性测试] {} 测试成功", label);
                    return Map.of("success", true, "message", "连通性测试成功", "response", "AI服务响应正常");
                }
                String errorMsg = extractErrorMessage(respBody);
                log.warn("[AI连通性测试] {} 测试失败: {} - {}", label, response.code(), errorMsg);
                return Map.of("success", false, "message", errorMsg, "code", response.code());
            }
        } catch (Exception e) {
            log.error("[AI连通性测试] {} 测试异常", label, e);
            return Map.of("success", false, "message", "网络连接失败: " + e.getMessage());
        }
    }

    /** 渠道默认base-url */
    private String getDefaultBaseUrl(String channel) {
        return switch (channel == null ? "" : channel.toUpperCase()) {
            case "DEEPSEEK" -> "https://api.deepseek.com";
            case "OLLAMA" -> "http://127.0.0.1:11434/v1";
            default -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
        };
    }

    /** 渠道默认模型 */
    private String getDefaultModel(String channel) {
        return switch (channel == null ? "" : channel.toUpperCase()) {
            case "DEEPSEEK" -> "deepseek-flash";
            case "OLLAMA" -> "qwen2.5:7b";
            default -> "qwen-plus";
        };
    }

    /**
     * 从AI服务错误响应中提取可读的错误信息
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "未知错误";
        }
        try {
            Map<?, ?> resp = new ObjectMapper().readValue(responseBody, Map.class);
            if (resp.containsKey("error")) {
                Object error = resp.get("error");
                if (error instanceof Map) {
                    Map<?, ?> errMap = (Map<?, ?>) error;
                    String msg = (String) errMap.get("message");
                    String type = (String) errMap.get("type");
                    if (msg != null) {
                        return msg;
                    }
                    if (type != null) {
                        return type;
                    }
                } else if (error instanceof String) {
                    return (String) error;
                }
            }
            return responseBody.substring(0, Math.min(100, responseBody.length())) + "...";
        } catch (Exception e) {
            return responseBody.substring(0, Math.min(100, responseBody.length())) + "...";
        }
    }

    /**
     * 背景服务渠道（语音识别 / 图片识别 / 知识库等无界面场景使用）。
     * 不再由用户手工设置"默认渠道"：按优先级自动取第一个"已配置 Key"的内置渠道
     * （通义千问 → DeepSeek → 本地模型），都没有时回退 QWEN 以便给出明确报错。
     * 自定义服务商不参与这些背景能力（语音/向量化仍绑通义千问专用模型）。
     */
    public String getDefaultChannel() {
        for (String ch : new String[]{"QWEN", "DEEPSEEK", "OLLAMA"}) {
            String key = getChannelApiKey(ch);
            if (key != null && !key.isBlank()) {
                return ch;
            }
        }
        return "QWEN";
    }

    // ============================================================
    // 自定义模型服务商（多厂商）
    // 整体存 sys_config 的 ai.providers，值为 JSON 数组，元素结构：
    // {id,name,baseUrl,apiKey(ENC:加密),models:[],visionModel,applyUrl,enabled,sortOrder}
    // 不建独立表：老库零 DDL 迁移风险；服务商数量少、无按字段查询需求。
    // ============================================================

    /** 读取服务商列表（apiKey 已解密，仅内部使用） */
    @SuppressWarnings("unchecked")
    public synchronized List<Map<String, Object>> listCustomProvidersRaw() {
        List<Map<String, Object>> list = new ArrayList<>();
        String json = sysConfigService.getValue(PROVIDERS_CONFIG);
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            list = new ObjectMapper().readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[AI服务商] 解析 {} 失败: {}", PROVIDERS_CONFIG, e.getMessage());
            return new ArrayList<>();
        }
        for (Map<String, Object> p : list) {
            Object k = p.get("apiKey");
            if (k instanceof String s && s.startsWith("ENC:")) {
                p.put("apiKey", decrypt(s));
            }
        }
        return list;
    }

    /** 对外列表：只回"是否已配置"，不回密钥任何字符（含掩码片段） */
    public List<Map<String, Object>> listCustomProviders() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : listCustomProvidersRaw()) {
            Map<String, Object> m = new LinkedHashMap<>(p);
            String key = str(p.get("apiKey"));
            m.remove("apiKey");
            m.put("hasApiKey", !key.isBlank());
            result.add(m);
        }
        return result;
    }

    /** 按 id 取单个服务商（apiKey 已解密，仅内部使用） */
    public Map<String, Object> getCustomProvider(String id) {
        if (id == null || id.isBlank()) return null;
        for (Map<String, Object> p : listCustomProvidersRaw()) {
            if (id.equals(p.get("id"))) return p;
        }
        return null;
    }

    /** 新增或更新服务商；apiKey 留空表示不修改 */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> saveCustomProvider(Map<String, Object> body) {
        if (body == null) throw new BusinessException("参数不能为空");
        String id = str(body.get("id"));
        String name = str(body.get("name"));
        String baseUrl = str(body.get("baseUrl"));
        String apiKey = str(body.get("apiKey"));
        String visionModel = str(body.get("visionModel"));
        String applyUrl = str(body.get("applyUrl"));
        List<String> models = toStringList(body.get("models"));
        boolean enabled = !"false".equalsIgnoreCase(str(body.get("enabled")));

        if (name.isBlank()) throw new BusinessException("厂商名称不能为空");
        if (name.length() > 50) throw new BusinessException("厂商名称不能超过50个字符");
        if (!isHttpUrl(baseUrl)) throw new BusinessException("调用地址必须以 http:// 或 https:// 开头");
        if (!applyUrl.isBlank() && !isHttpUrl(applyUrl)) throw new BusinessException("厂商链接必须以 http:// 或 https:// 开头");
        if (models.isEmpty()) throw new BusinessException("至少填写一个模型名称");

        List<Map<String, Object>> list = listCustomProvidersRaw();
        Map<String, Object> target = null;
        for (Map<String, Object> p : list) {
            if (!id.isBlank() && id.equals(p.get("id"))) { target = p; break; }
        }
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("id", UUID.randomUUID().toString().replace("-", ""));
            target.put("sortOrder", list.size());
            list.add(target);
        }
        target.put("name", name);
        target.put("baseUrl", baseUrl);
        target.put("models", models);
        target.put("visionModel", visionModel);
        target.put("applyUrl", applyUrl);
        target.put("enabled", enabled);
        // 联网搜索支持标记（可选字段，未传时保留原值不动——原地改写目标对象，未知字段不受影响）
        if (body.containsKey("webSearch")) {
            Object wv = body.get("webSearch");
            target.put("webSearch", Boolean.TRUE.equals(wv) || "true".equalsIgnoreCase(String.valueOf(wv)));
        }
        if (!apiKey.isBlank()) {
            if (apiKey.length() < 10) throw new BusinessException("API Key 长度至少需要10个字符");
            target.put("apiKey", apiKey);
        } else if (str(target.get("apiKey")).isBlank()) {
            throw new BusinessException("API Key 不能为空");
        }
        persistProviders(list);
        Map<String, Object> result = new LinkedHashMap<>(target);
        // 安全：不回传密钥任何字符（仅回"已配置"）
        result.remove("apiKey");
        result.put("hasApiKey", true);
        return result;
    }

    /** 删除服务商 */
    public synchronized void deleteCustomProvider(String id) {
        if (id == null || id.isBlank()) throw new BusinessException("缺少服务商ID");
        List<Map<String, Object>> list = listCustomProvidersRaw();
        boolean removed = list.removeIf(p -> id.equals(p.get("id")));
        if (!removed) throw new BusinessException("服务商不存在");
        persistProviders(list);
    }

    /** 测试自定义服务商连通性（id 或 直接传配置都支持） */
    public Map<String, Object> testCustomProvider(Map<String, Object> body) {
        String id = str(body.get("id"));
        String baseUrl = str(body.get("baseUrl"));
        String apiKey = str(body.get("apiKey"));
        String model = str(body.get("model"));
        if (!id.isBlank()) {
            Map<String, Object> p = getCustomProvider(id);
            if (p != null) {
                if (baseUrl.isBlank()) baseUrl = str(p.get("baseUrl"));
                if (apiKey.isBlank()) apiKey = str(p.get("apiKey"));
                if (model.isBlank()) {
                    List<String> ms = toStringList(p.get("models"));
                    if (!ms.isEmpty()) model = ms.get(0);
                }
            }
        }
        return doChatTest(baseUrl, apiKey, model, false, "自定义服务商");
    }

    /** 写回服务商列表（apiKey 加密） */
    private void persistProviders(List<Map<String, Object>> list) {
        try {
            List<Map<String, Object>> enc = new ArrayList<>();
            for (Map<String, Object> p : list) {
                Map<String, Object> c = new LinkedHashMap<>(p);
                String k = str(c.get("apiKey"));
                if (!k.isBlank() && !k.startsWith("ENC:")) {
                    c.put("apiKey", encrypt(k));
                }
                enc.add(c);
            }
            String json = new ObjectMapper().writeValueAsString(enc);
            saveConfig(PROVIDERS_CONFIG, json, "AI模型服务商列表", CONFIG_GROUP, "json",
                    "自定义模型服务商（多厂商），API Key加密存储");
        } catch (Exception e) {
            throw new BusinessException("保存模型服务商失败: " + e.getMessage());
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object v) {
        List<String> result = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                String s = str(o);
                if (!s.isBlank()) result.add(s);
            }
        } else if (v instanceof String s) {
            for (String part : s.split("[,\\n]")) {
                String t = part.trim();
                if (!t.isBlank()) result.add(t);
            }
        }
        return result;
    }

    // ============================================================
    // Token配额配置（配额值存sys_config；0=不限，用户未设专属值时继承全局默认）
    // ============================================================

    /** 全局默认月配额（tokens，0=不限） */
    public long getDefaultQuota() {
        return parseQuotaValue(sysConfigService.getValue("ai.quota.default"), 0L);
    }

    public void setDefaultQuota(long quota) {
        saveConfig("ai.quota.default", String.valueOf(quota), "AI默认月配额", CONFIG_GROUP, "number",
                "每个用户每月可用的AI token总额，0=不限");
    }

    /** 租户月度总额度（tokens，0=不限）。租户未设时继承全局默认。 */
    public long getTenantQuota() {
        String v = sysConfigService.getValue("ai.quota.tenant");
        if (v == null || v.isBlank()) {
            return getDefaultQuota();
        }
        return parseQuotaValue(v, getDefaultQuota());
    }

    /** 租户是否设置了专属总额度（非继承全局默认） */
    public boolean hasTenantQuota() {
        String v = sysConfigService.getValue("ai.quota.tenant");
        return v != null && !v.isBlank();
    }

    public void setTenantQuota(long quota) {
        saveConfig("ai.quota.tenant", String.valueOf(quota), "AI租户月总额度", CONFIG_GROUP, "number",
                "租户每月AI token总额，用户专属配额之和不可超过此值，0=不限");
    }

    public void clearTenantQuota() {
        sysConfigService.remove(new QueryWrapper<SysConfig>().eq("config_key", "ai.quota.tenant"));
        sysConfigService.refreshCache();
    }

    // ============================================================
    // 平台分配的租户月度总额度（平台管理→客户管理维护；存主库 sys_config）
    //   口径（用户 2026-09-05 定）：平台给租户分配 → 租户再给员工分配。
    //   该额度 = 该客户名下所有用户共享的月度总消耗上限；0/未分配 = 不限。
    //   每库存一份密文/配置互不相通，故平台分配值统一存主库，按租户ID区分。
    // ============================================================

    /** 读取平台分配给某客户的月度总额度（tokens；未分配/非法 = 0 不限） */
    public long getPlatformTenantQuota(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        try {
            List<Map<String, Object>> rows = businessJdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ?",
                    PLATFORM_TENANT_QUOTA_PREFIX + tenantId);
            if (rows.isEmpty()) {
                return 0L;
            }
            Object v = rows.get(0).get("config_value");
            return parseQuotaValue(v == null ? null : String.valueOf(v), 0L);
        } catch (Exception e) {
            log.warn("[AI配额] 读取平台分配额度失败（tenantId={}）: {}", tenantId, e.getMessage());
            return 0L;
        }
    }

    /** 平台分配/调整某客户的月度总额度（quota<=0 表示不限，删除配置行） */
    public void savePlatformTenantQuota(Long tenantId, long quota) {
        if (tenantId == null) {
            throw new BusinessException("缺少客户标识");
        }
        String key = PLATFORM_TENANT_QUOTA_PREFIX + tenantId;
        try {
            if (quota <= 0) {
                businessJdbcTemplate.update("DELETE FROM sys_config WHERE config_key = ?", key);
                return;
            }
            int updated = businessJdbcTemplate.update(
                    "UPDATE sys_config SET config_value = ?, updated_at = CURRENT_TIMESTAMP WHERE config_key = ?",
                    String.valueOf(quota), key);
            if (updated == 0) {
                businessJdbcTemplate.update(
                        "INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order) " +
                                "VALUES (?, ?, 'AI客户月总额度-平台分配', 'ai', 'number', '平台管理→客户管理分配的该客户每月AI token总额，名下所有用户共享，0/删除=不限', 0)",
                        key, String.valueOf(quota));
            }
        } catch (Exception e) {
            throw new BusinessException("保存客户AI额度失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 租户消耗系数（消耗计量口径）
    //   记账token = 实际token × (1 + 系数)；默认 0.5（实际 100 记 150）
    //   仅作用于"消耗"与"配额扣减"，【不影响额度分配】(ai.quota.* 不参与换算)
    // ============================================================

    /** 默认消耗系数：0.5（即 1.5 倍） */
    public static final double DEFAULT_USAGE_RATIO = 0.5;

    /** 系数上限：防误填天文数字把用户额度瞬间打爆 */
    public static final double MAX_USAGE_RATIO = 10.0;

    /** 租户消耗系数（0 ~ MAX；未配置/非法/负数一律回落默认 0.5，绝不让配置问题影响功能） */
    public double getTenantUsageRatio() {
        String v = sysConfigService.getValue("ai.usage.tenant.ratio");
        if (v == null || v.isBlank()) {
            return DEFAULT_USAGE_RATIO;
        }
        try {
            double d = Double.parseDouble(v.trim());
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 0) {
                log.warn("[AI用量] 消耗系数非法（{}），回落默认 {}", v, DEFAULT_USAGE_RATIO);
                return DEFAULT_USAGE_RATIO;
            }
            return Math.min(d, MAX_USAGE_RATIO);
        } catch (NumberFormatException e) {
            log.warn("[AI用量] 消耗系数读取失败（{}），回落默认 {}", v, DEFAULT_USAGE_RATIO);
            return DEFAULT_USAGE_RATIO;
        }
    }

    /** 设置租户消耗系数（0~10；0 = 不加价，即升级前口径） */
    public void setTenantUsageRatio(double ratio) {
        saveConfig("ai.usage.tenant.ratio", String.valueOf(ratio), "AI租户消耗系数", CONFIG_GROUP, "number",
                "消耗计量系数：记账token = 实际token × (1+系数)，默认0.5（即1.5倍）；仅影响消耗与配额扣减，不影响额度分配");
    }

    /** 租户下所有用户专属配额之和（用于聚合校验：sum(用户配额) ≤ 租户总额度） */
    public long getAllUserQuotaSum() {
        try {
            List<SysConfig> configs = sysConfigService.list(
                    new QueryWrapper<SysConfig>().likeRight("config_key", "ai.quota.user."));
            long sum = 0L;
            for (SysConfig c : configs) {
                sum += parseQuotaValue(c.getConfigValue(), 0L);
            }
            return sum;
        } catch (Exception e) {
            log.warn("[AI配额] 统计用户配额之和失败: {}", e.getMessage());
            return 0L;
        }
    }

    /** 用户专属月配额原始值（null=未设置，继承全局默认） */
    public String getUserQuota(Long userId) {
        return sysConfigService.getValue("ai.quota.user." + userId);
    }

    public void setUserQuota(Long userId, long quota) {
        saveConfig("ai.quota.user." + userId, String.valueOf(quota), "AI用户月配额-" + userId,
                CONFIG_GROUP, "number", "用户专属月配额，0=不限");
    }

    /** 清除用户专属配额（恢复继承全局默认） */
    public void clearUserQuota(Long userId) {
        sysConfigService.remove(new QueryWrapper<SysConfig>()
                .eq("config_key", "ai.quota.user." + userId));
        sysConfigService.refreshCache();
    }

    /** 配额字符串解析（非法或缺省返回defaultValue） */
    private long parseQuotaValue(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ============================================================
    // 数据使用政策配置（Data Usage Policy）
    // ============================================================

    /**
     * 是否在发送给AI前对敏感字段做脱敏（手机号/身份证/银行卡/邮箱/座机）
     * 默认开启。开启后，发送给AI的内容中所有敏感字段都会被替换为脱敏形式
     * （如 13800138000 → 138****0000），AI仍能理解字段含义但拿不到真实数据
     */
    public boolean isDataMaskEnabled() {
        return "true".equalsIgnoreCase(sysConfigService.getValue("ai.policy.mask-sensitive", "true"));
    }

    public void setDataMaskEnabled(boolean enabled) {
        saveConfig("ai.policy.mask-sensitive", String.valueOf(enabled),
                "AI请求敏感字段脱敏", CONFIG_GROUP, "boolean",
                "开启后发送给AI的内容中手机号/身份证/银行卡/邮箱等会被脱敏");
    }

    /**
     * 是否在日志中记录AI请求/响应原文
     * 生产环境建议关闭，避免日志文件泄漏敏感信息
     * 关闭后日志只记录 traceId、意图、参数keys，不记录具体内容
     */
    public boolean isLogRawContentEnabled() {
        return "true".equalsIgnoreCase(sysConfigService.getValue("ai.policy.log-raw-content", "false"));
    }

    public void setLogRawContentEnabled(boolean enabled) {
        saveConfig("ai.policy.log-raw-content", String.valueOf(enabled),
                "日志记录AI原文", CONFIG_GROUP, "boolean",
                "生产环境建议关闭，避免日志文件泄漏敏感信息");
    }

    /**
     * 严格模式：禁止涉及敏感实体的查询发送给AI
     * 开启后，当用户查询 客户/供应商/员工/订单 等敏感实体时，
     * 系统不会把查询结果发送给AI做进一步推理，只返回原始数据给前端
     */
    public boolean isStrictModeEnabled() {
        return "true".equalsIgnoreCase(sysConfigService.getValue("ai.policy.strict-mode", "false"));
    }

    public void setStrictModeEnabled(boolean enabled) {
        saveConfig("ai.policy.strict-mode", String.valueOf(enabled),
                "AI严格模式", CONFIG_GROUP, "boolean",
                "开启后敏感实体的查询结果不会发送给AI做进一步推理");
    }

    /**
     * 获取数据使用政策汇总
     */
    public Map<String, Object> getDataPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("maskSensitive", isDataMaskEnabled());
        policy.put("logRawContent", isLogRawContentEnabled());
        policy.put("strictMode", isStrictModeEnabled());
        // AI厂商数据使用政策说明（用于前端展示）
        Map<String, Object> vendorPolicies = new LinkedHashMap<>();
        vendorPolicies.put("QWEN", Map.of(
                "name", "通义千问（阿里云百炼）",
                "url", "https://dashscope.aliyuncs.com",
                "dataForTraining", false,
                "description", "调用商用API，数据默认不用于模型训练。数据会传输到阿里云服务器做推理。"
        ));
        vendorPolicies.put("DEEPSEEK", Map.of(
                "name", "DeepSeek",
                "url", "https://api.deepseek.com",
                "dataForTraining", false,
                "description", "调用商用API，数据默认不用于模型训练。数据会传输到DeepSeek服务器做推理。"
        ));
        vendorPolicies.put("OLLAMA", Map.of(
                "name", "本地模型（Ollama）",
                "url", "http://127.0.0.1:11434",
                "dataForTraining", false,
                "description", "本地部署大模型，数据全程不出内网，无第三方留存风险。需本机已安装Ollama并拉取模型。"
        ));
        policy.put("vendorPolicies", vendorPolicies);
        return policy;
    }

    /**
     * 更新数据使用政策
     */
    public void updateDataPolicy(Map<String, Object> policy) {
        if (policy == null) return;
        if (policy.containsKey("maskSensitive")) {
            setDataMaskEnabled(Boolean.TRUE.equals(policy.get("maskSensitive")));
        }
        if (policy.containsKey("logRawContent")) {
            setLogRawContentEnabled(Boolean.TRUE.equals(policy.get("logRawContent")));
        }
        if (policy.containsKey("strictMode")) {
            setStrictModeEnabled(Boolean.TRUE.equals(policy.get("strictMode")));
        }
    }

    // ============================================================
    // 对话能力配置（联网搜索 / 回答严谨度 / 回答详细度 / 会话记忆轮数）
    // ============================================================

    /**
     * 是否开启联网搜索。
     * 仅对支持联网的渠道生效（通义千问附加 enable_search；自定义服务商需同时勾选"支持联网搜索"）；
     * DeepSeek 官方接口不带联网参数，开启后对该渠道无影响（界面如实提示，不做假能力）。
     * 默认关闭。
     */
    public boolean isWebSearchEnabled() {
        return "true".equalsIgnoreCase(sysConfigService.getValue("ai.chat.web-search", "false"));
    }

    public void setWebSearchEnabled(boolean enabled) {
        saveConfig("ai.chat.web-search", String.valueOf(enabled), "AI联网搜索", CONFIG_GROUP, "boolean",
                "开启后小楷可联网检索公开资料回答问题；仅对支持联网的服务商生效");
    }

    /**
     * 回答严谨度（0~1，数值越低越严谨越发散越低）。默认 0.3（与历史写死值一致，升级后行为不变）。
     */
    public double getChatTemperature() {
        double v = aiConfDouble(sysConfigService.getValue("ai.chat.temperature", "0.3"), 0.3);
        return (Double.isNaN(v) || v < 0 || v > 1) ? 0.3 : v;
    }

    public void setChatTemperature(double temperature) {
        double v = (Double.isNaN(temperature)) ? 0.3 : Math.max(0.0, Math.min(1.0, temperature));
        saveConfig("ai.chat.temperature", String.valueOf(v), "AI回答严谨度", CONFIG_GROUP, "number",
                "数值越低回答越严谨，越高越发散；范围 0~1，默认 0.3");
    }

    /**
     * 回答详细度（单次回答最大输出长度），范围 256~8192。默认 2000（与历史写死值一致）。
     * 仅影响普通对话与图片识别；工具调用链路保持 4096 不变（写工具参数需要更长输出）。
     */
    public int getChatMaxTokens() {
        int v = aiConfInt(sysConfigService.getValue("ai.chat.max-tokens", "2000"), 2000);
        return (v < 256 || v > 8192) ? 2000 : v;
    }

    public void setChatMaxTokens(int maxTokens) {
        int v = Math.max(256, Math.min(8192, maxTokens));
        saveConfig("ai.chat.max-tokens", String.valueOf(v), "AI回答详细度", CONFIG_GROUP, "number",
                "小楷单次回答的篇幅上限；默认 2000");
    }

    /**
     * 会话记忆轮数（保留最近 N 轮"用户+小楷"对话），范围 20~80。默认 60（2026-10-10 由 20 提升）。
     * 读取方：Agent 链路与网关意图识别链路（两者共用同一会话键，口径必须一致）。
     */
    public int getChatHistoryRounds() {
        int v = aiConfInt(sysConfigService.getValue("ai.chat.history-rounds", "60"), 60);
        return (v < 20 || v > 80) ? 60 : v;
    }

    public void setChatHistoryRounds(int rounds) {
        int v = Math.max(20, Math.min(80, rounds));
        saveConfig("ai.chat.history-rounds", String.valueOf(v), "AI会话记忆轮数", CONFIG_GROUP, "number",
                "每次对话注入模型的上下文窗口轮数（会话记录本身永久保留）；默认 60");
    }

    /**
     * 上下文上限（tokens）：每次对话最多注入模型的上下文总预算。
     * 2026-11-13 用户要求由"按轮数"改为"按上下文"，范围 8000~1000000，默认 128000。
     * 说明：这是"发给模型的输入预算"，不影响服务器算力；真正的上限还受所选模型窗口约束，
     * 超出模型窗口时上游会直接报错，所以默认按常见模型窗口（128K）设定，最大值留到 100 万。
     */
    public int getChatContextMaxTokens() {
        int v = aiConfInt(sysConfigService.getValue("ai.chat.context-max-tokens", "128000"), 128000);
        return (v < 8000 || v > 1000000) ? 128000 : v;
    }

    public void setChatContextMaxTokens(int tokens) {
        int v = Math.max(8000, Math.min(1000000, tokens));
        saveConfig("ai.chat.context-max-tokens", String.valueOf(v), "AI上下文上限", CONFIG_GROUP, "number",
                "每次对话最多注入模型的历史上下文总量，单位 tokens；默认 128000，最大 1000000");
    }

    /**
     * 对话能力汇总（设置中心「对话能力」卡片回显数据源）。
     */
    public Map<String, Object> getChatCapabilities() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("webSearch", isWebSearchEnabled());
        caps.put("temperature", getChatTemperature());
        caps.put("maxTokens", getChatMaxTokens());
        caps.put("historyRounds", getChatHistoryRounds());
        caps.put("contextMaxTokens", getChatContextMaxTokens());
        // 各内置渠道对联网搜索的支持情况（前端据此如实提示，不做假能力）
        Map<String, Boolean> webSearchSupport = new LinkedHashMap<>();
        webSearchSupport.put("QWEN", true);
        webSearchSupport.put("DEEPSEEK", false);
        webSearchSupport.put("OLLAMA", false);
        caps.put("webSearchSupport", webSearchSupport);
        return caps;
    }

    /**
     * 更新对话能力设置（只处理传入的字段）。
     */
    public void updateChatCapabilities(Map<String, Object> body) {
        if (body == null) return;
        if (body.containsKey("webSearch")) {
            Object v = body.get("webSearch");
            setWebSearchEnabled(Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v)));
        }
        if (body.containsKey("temperature")) {
            setChatTemperature(aiConfDouble(body.get("temperature"), 0.3));
        }
        if (body.containsKey("maxTokens")) {
            setChatMaxTokens(aiConfInt(body.get("maxTokens"), 2000));
        }
        if (body.containsKey("historyRounds")) {
            setChatHistoryRounds(aiConfInt(body.get("historyRounds"), 60));
        }
        if (body.containsKey("contextMaxTokens")) {
            setChatContextMaxTokens(aiConfInt(body.get("contextMaxTokens"), 128000));
        }
    }

    /** 配置值转 double（非法值回退默认） */
    private double aiConfDouble(Object raw, double def) {
        if (raw == null) return def;
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 配置值转 int（非法值回退默认） */
    private int aiConfInt(Object raw, int def) {
        if (raw == null) return def;
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
