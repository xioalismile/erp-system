package com.erp.ai.controller;

import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;
import com.erp.ai.service.AiGatewayService;
import com.erp.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI网关控制器 - 旧版写死意图路由入口（已废弃）
 *
 * 已被工具化链路取代（/api/ai/chat/agent，见 AgentController）。
 * 由配置 erp.ai.gateway.legacy-enabled 控制（默认 false=禁用），
 * 禁用时所有旧入口直接返回提示，防止双链路并存造成行为不一致。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiGatewayController {

    private final AiGatewayService gatewayService;

    /** 旧版写死链路是否启用（默认false，保留仅为紧急回退开关） */
    private final boolean legacyEnabled;

    public AiGatewayController(AiGatewayService gatewayService,
                               org.springframework.core.env.Environment env) {
        this.gatewayService = gatewayService;
        this.legacyEnabled = env.getProperty("erp.ai.gateway.legacy-enabled", Boolean.class, false);
    }

    /**
     * AI对话入口(Web端/通用) - 已废弃，请使用 /api/ai/chat/agent
     */
    @PostMapping("/chat")
    public Result<UnifiedResponse> chat(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        if (!legacyEnabled) {
            return Result.error("旧版AI对话入口已停用，请使用新版AI助手（工具化链路）");
        }
        UnifiedRequest request = new UnifiedRequest();
        request.setChannel(body.get("channel") != null ? (String) body.get("channel") : "QWEN");
        request.setChannelUserId(String.valueOf(req.getAttribute("currentUsername")));
        request.setErpUserId((Long) req.getAttribute("currentUserId"));
        request.setUserInput((String) body.get("message"));
        request.setInputType("TEXT");
        // 图片URL列表（视觉识别用）
        if (body.get("imageUrls") != null) {
            @SuppressWarnings("unchecked")
            List<String> imageUrls = (List<String>) body.get("imageUrls");
            request.setImageUrls(imageUrls);
            if (imageUrls != null && !imageUrls.isEmpty()) {
                request.setInputType("IMAGE");
            }
        }
        // 支持多轮对话：前端传sessionId
        if (body.get("sessionId") != null) {
            request.setSessionId((String) body.get("sessionId"));
        }

        UnifiedResponse response = gatewayService.process(request);
        return Result.success(response);
    }

    /**
     * AI创建订单 - 已废弃
     */
    @PostMapping("/order/create")
    public Result<UnifiedResponse> createOrder(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!legacyEnabled) return Result.error("旧版AI入口已停用，请使用新版AI助手");
        UnifiedRequest request = new UnifiedRequest();
        request.setChannel(body.getOrDefault("channel", "QWEN"));
        request.setChannelUserId(String.valueOf(req.getAttribute("currentUsername")));
        request.setErpUserId((Long) req.getAttribute("currentUserId"));
        request.setUserInput(body.get("message"));

        UnifiedResponse response = gatewayService.process(request);
        return Result.success(response);
    }

    /**
     * AI查询订单
     */
    @PostMapping("/order/query")
    public Result<UnifiedResponse> queryOrder(@RequestBody Map<String, String> body, HttpServletRequest req) {
        UnifiedRequest request = new UnifiedRequest();
        request.setChannel("QWEN");
        request.setChannelUserId(String.valueOf(req.getAttribute("currentUsername")));
        request.setErpUserId((Long) req.getAttribute("currentUserId"));
        request.setUserInput(body.get("message"));

        UnifiedResponse response = gatewayService.process(request);
        return Result.success(response);
    }

    /**
     * AI查询库存
     */
    @PostMapping("/inventory/query")
    public Result<UnifiedResponse> queryInventory(@RequestBody Map<String, String> body, HttpServletRequest req) {
        UnifiedRequest request = new UnifiedRequest();
        request.setChannel("QWEN");
        request.setChannelUserId(String.valueOf(req.getAttribute("currentUsername")));
        request.setErpUserId((Long) req.getAttribute("currentUserId"));
        request.setUserInput(body.get("message"));

        UnifiedResponse response = gatewayService.process(request);
        return Result.success(response);
    }

    /**
     * AI生成报表 - 已废弃
     */
    @PostMapping("/report/sales")
    public Result<UnifiedResponse> generateReport(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!legacyEnabled) return Result.error("旧版AI入口已停用，请使用新版AI助手");
        UnifiedRequest request = new UnifiedRequest();
        request.setChannel("QWEN");
        request.setChannelUserId(String.valueOf(req.getAttribute("currentUsername")));
        request.setErpUserId((Long) req.getAttribute("currentUserId"));
        request.setUserInput(body.get("message"));

        UnifiedResponse response = gatewayService.process(request);
        return Result.success(response);
    }

    /**
     * 确认写操作(输入验证码)
     */
    @PostMapping("/confirm")
    public Result<UnifiedResponse> confirm(@RequestBody Map<String, String> body) {
        String token = body.get("confirmToken");
        UnifiedResponse response = gatewayService.confirmAndExecute(token);
        return Result.success(response);
    }
}
