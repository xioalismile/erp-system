package com.erp.ai.adapter;

import com.erp.ai.service.AiConfigService;

import java.util.Map;

/**
 * 自定义模型服务商适配器（多厂商）。
 * 由 {@link com.erp.ai.service.AiAdapterResolver} 在每次请求时按"服务商配置 + 用户所选模型"动态构造，
 * 不是 Spring Bean，因此：
 * - httpClient 走基类懒加载（否则运行时 new 出来的实例拿不到客户端）
 * - AiConfigService 由解析器显式注入（用于复用"敏感字段脱敏"开关）
 */
public class CustomProviderAdapter extends AbstractOpenAiCompatibleAdapter {

    private final String providerId;
    private final String providerName;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String visionModel;
    /** 该服务商是否支持联网搜索（与服务商一起保存；全局「联网搜索」开关也开启时才附加参数） */
    private final boolean webSearch;

    public CustomProviderAdapter(String providerId, String providerName, String baseUrl,
                                 String apiKey, String model, String visionModel) {
        this(providerId, providerName, baseUrl, apiKey, model, visionModel, false);
    }

    public CustomProviderAdapter(String providerId, String providerName, String baseUrl,
                                 String apiKey, String model, String visionModel, boolean webSearch) {
        this.providerId = providerId;
        this.providerName = providerName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.visionModel = visionModel;
        this.webSearch = webSearch;
    }

    /** 注入配置服务（运行时构造的实例不走 Spring 字段注入） */
    public void setConfigService(AiConfigService configService) {
        this.aiConfigService = configService;
    }

    @Override
    public String getChannel() {
        return "CUSTOM";
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && baseUrl != null && !baseUrl.isBlank()
                && model != null && !model.isBlank();
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    protected String getBaseUrl() {
        return baseUrl;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected String getVisionModel() {
        return (visionModel == null || visionModel.isBlank()) ? model : visionModel;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    /**
     * 联网搜索：全局「联网搜索」开关开启 且 本服务商勾选了"支持联网搜索"时，
     * 附加 enable_search 参数（OpenAI 兼容扩展参数，仅部分厂商支持——因此按服务商勾选，避免误伤不认识的厂商）。
     */
    @Override
    protected Map<String, Object> extraBodyParams() {
        try {
            if (webSearch && aiConfigService != null && aiConfigService.isWebSearchEnabled()) {
                return Map.of("enable_search", true);
            }
        } catch (Exception ignore) {
        }
        return null;
    }
}
