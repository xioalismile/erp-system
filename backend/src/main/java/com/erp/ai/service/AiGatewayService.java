package com.erp.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.ai.adapter.AbstractOpenAiCompatibleAdapter;
import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.config.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI网关核心服务
 * 职责: 渠道选择 → 意图识别 → 参数提取 → 业务路由 → 审计记录
 */
@Slf4j
@Service
public class AiGatewayService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private List<AiChannelAdapter> adapters;

    @Value("${erp.ai.gateway.default-channel:QWEN}")
    private String defaultChannel;

    @Value("${erp.ai.gateway.write-confirm-required:true}")
    private boolean writeConfirmRequired;

    @Resource
    private com.erp.config.CacheService cache;

    private static final String SYSTEM_PROMPT = """
            你是智能ERP系统的AI助手。用户会用自然语言描述需求，你需要识别意图并提取参数。

            支持的意图:
            1. CREATE_ORDER - 创建销售订单 (需要: 客户名称customerName, 商品列表items[物料编码materialCode/名称materialName, 数量quantity, 单价unitPrice], 交货日期deliveryDate)
            2. QUERY_ORDER - 查询订单 (可选: 客户名称customerName, 状态status, 月份month)
            3. QUERY_INVENTORY - 查询库存 (可选: 物料编码materialCode, 物料名称materialName)
            4. QUERY_CUSTOMER - 查询客户 (可选: 客户名称customerName)
            5. GENERATE_REPORT - 生成报表 (需要: 报表类型reportType: sales/inventory, 维度dimension, 时间范围dateRange)
            6. FAQ - 业务问答

            请以JSON格式回复:
            {"intent":"意图","params":{参数对象},"reply":"给用户的友好回复"}

            示例:
            用户: 帮我在华南精密制造下100个精密轴承A，单价25.5元，7月15号交货
            回复: {"intent":"CREATE_ORDER","params":{"customerName":"华南精密制造","items":[{"materialName":"精密轴承A","quantity":100,"unitPrice":25.5}],"deliveryDate":"2026-07-15"},"reply":"已为您解析订单信息：客户华南精密制造，精密轴承A × 100个 @ 25.5元，交货日期2026-07-15。请确认是否创建？"}
            """;

    /**
     * 获取当前可用的适配器
     */
    public AiChannelAdapter getAdapter(String channel) {
        // 指定渠道优先
        if (channel != null && !channel.isBlank()) {
            for (AiChannelAdapter a : adapters) {
                if (a.getChannel().equalsIgnoreCase(channel) && a.isEnabled()) {
                    return a;
                }
            }
        }
        // 默认渠道
        for (AiChannelAdapter a : adapters) {
            if (a.getChannel().equalsIgnoreCase(defaultChannel) && a.isEnabled()) {
                return a;
            }
        }
        // 任意可用
        for (AiChannelAdapter a : adapters) {
            if (a.isEnabled()) {
                return a;
            }
        }
        throw new BusinessException("没有可用的AI渠道，请检查API Key配置");
    }

    /**
     * 处理AI请求 - 主入口
     */
    public UnifiedResponse process(UnifiedRequest request) {
        if (request.getTraceId() == null) {
            request.setTraceId(IdUtil.fastSimpleUUID());
        }
        if (request.getChannel() == null) {
            request.setChannel(defaultChannel);
        }

        log.info("[AI网关] traceId={} channel={} input={}", request.getTraceId(), request.getChannel(), request.getUserInput());

        // 限流检查
        checkRateLimit(request.getChannelUserId());

        try {
            AiChannelAdapter adapter = getAdapter(request.getChannel());

            // 调用大模型识别意图
            String aiResponse = adapter.chat(SYSTEM_PROMPT, request.getUserInput());
            log.info("[AI网关] AI原始回复: {}", aiResponse);

            // 解析AI返回的JSON
            Map<String, Object> parsed = parseAiResponse(aiResponse);
            String intent = (String) parsed.getOrDefault("intent", "UNKNOWN");
            Map<String, Object> params = (Map<String, Object>) parsed.getOrDefault("params", new HashMap<>());
            String reply = (String) parsed.getOrDefault("reply", "我理解了您的需求");

            UnifiedResponse response = new UnifiedResponse();
            response.setTraceId(request.getTraceId());
            response.setIntent(intent);
            response.setExtractedParams(params);
            response.setReplyText(reply);
            response.setSuccess(true);

            // 写操作需要确认
            if (isWriteOperation(intent) && writeConfirmRequired) {
                String token = RandomUtil.randomNumbers(6);
                response.setNeedConfirm(true);
                response.setConfirmToken(token);
                // 将待确认数据存入Redis, 5分钟过期
                String key = "ai:confirm:" + token;
                Map<String, Object> confirmData = new HashMap<>();
                confirmData.put("intent", intent);
                confirmData.put("params", params);
                confirmData.put("erpUserId", request.getErpUserId());
                confirmData.put("channel", request.getChannel());
                cache.set(key, mapper.writeValueAsString(confirmData), 5, TimeUnit.MINUTES);
                response.setReplyText(reply + "\n\n请输入验证码 " + token + " 确认操作");
            } else {
                // 读操作直接执行
                Object result = executeAction(intent, params, request.getErpUserId());
                if (result instanceof Map) {
                    response.setActionResult((Map<String, Object>) result);
                } else {
                    response.setActionResult(Map.of("result", result));
                }
            }

            return response;
        } catch (Exception e) {
            log.error("[AI网关] 处理失败", e);
            UnifiedResponse resp = UnifiedResponse.error(e.getMessage());
            resp.setTraceId(request.getTraceId());
            return resp;
        }
    }

    /**
     * 确认并执行写操作
     */
    public UnifiedResponse confirmAndExecute(String confirmToken) {
        String key = "ai:confirm:" + confirmToken;
        String json = cache.get(key);
        if (json == null) {
            return UnifiedResponse.error("验证码无效或已过期");
        }
        try {
            Map<String, Object> data = mapper.readValue(json, Map.class);
            String intent = (String) data.get("intent");
            Map<String, Object> params = (Map<String, Object>) data.get("params");
            Long erpUserId = ((Number) data.get("erpUserId")).longValue();

            Object result = executeAction(intent, params, erpUserId);
            cache.delete(key);

            UnifiedResponse resp = UnifiedResponse.ok("操作成功完成");
            resp.setIntent(intent);
            resp.setActionResult((Map<String, Object>) result);
            return resp;
        } catch (Exception e) {
            log.error("确认执行失败", e);
            return UnifiedResponse.error(e.getMessage());
        }
    }

    /**
     * 执行业务动作
     */
    private Object executeAction(String intent, Map<String, Object> params, Long erpUserId) {
        return switch (intent) {
            case "CREATE_ORDER" -> aiOrderService.createDraftOrder(params, erpUserId);
            case "QUERY_ORDER" -> aiOrderService.queryOrders(params);
            case "QUERY_INVENTORY" -> aiInventoryService.queryInventory(params);
            case "QUERY_CUSTOMER" -> aiOrderService.queryCustomer(params);
            case "GENERATE_REPORT" -> aiReportService.generateReport(params);
            case "FAQ" -> Map.of("answer", "这是一个业务FAQ回答");
            default -> Map.of("message", "未识别的操作类型");
        };
    }

    @Resource(name = "aiOrderService")
    private AiOrderService aiOrderService;

    @Resource(name = "aiInventoryService")
    private AiInventoryService aiInventoryService;

    @Resource(name = "aiReportService")
    private AiReportService aiReportService;

    private boolean isWriteOperation(String intent) {
        return "CREATE_ORDER".equals(intent);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAiResponse(String aiResponse) {
        try {
            // 尝试提取JSON部分
            String json = aiResponse.trim();
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
            }
            json = json.trim();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("AI返回非JSON格式, 原文: {}", aiResponse);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("intent", "FAQ");
            fallback.put("reply", aiResponse);
            return fallback;
        }
    }

    private void checkRateLimit(String userId) {
        if (userId == null) userId = "anonymous";
        String minuteKey = "ai:rate:m:" + userId;
        String dayKey = "ai:rate:d:" + userId;

        Long minuteCount = cache.increment(minuteKey);
        Long dayCount = cache.increment(dayKey);
        if (minuteCount == 1) cache.expire(minuteKey, 60, TimeUnit.SECONDS);
        if (dayCount == 1) cache.expire(dayKey, 1, TimeUnit.DAYS);

        if (minuteCount > 5) {
            throw new BusinessException("请求过于频繁, 每分钟限5次");
        }
        if (dayCount > 200) {
            throw new BusinessException("今日AI请求次数已达上限(200次)");
        }
    }
}
