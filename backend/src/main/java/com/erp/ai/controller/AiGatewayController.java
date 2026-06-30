package com.erp.ai.controller;

import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;
import com.erp.ai.service.AiGatewayService;
import com.erp.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI网关控制器 - 统一AI入口
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiGatewayService gatewayService;

    /**
     * AI对话入口(Web端/通用)
     * 所有渠道的AI请求统一走这个接口
     */
    @PostMapping("/chat")
    public Result<UnifiedResponse> chat(@RequestBody Map<String, String> body, HttpServletRequest req) {
        UnifiedRequest request = new UnifiedRequest();
        request.setChannel("QWEN"); // 默认通义千问
        request.setChannelUserId(String.valueOf(req.getAttribute("currentUsername")));
        request.setErpUserId((Long) req.getAttribute("currentUserId"));
        request.setUserInput(body.get("message"));
        request.setInputType("TEXT");

        UnifiedResponse response = gatewayService.process(request);
        return Result.success(response);
    }

    /**
     * AI创建订单
     */
    @PostMapping("/order/create")
    public Result<UnifiedResponse> createOrder(@RequestBody Map<String, String> body, HttpServletRequest req) {
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
     * AI生成报表
     */
    @PostMapping("/report/sales")
    public Result<UnifiedResponse> generateReport(@RequestBody Map<String, String> body, HttpServletRequest req) {
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
