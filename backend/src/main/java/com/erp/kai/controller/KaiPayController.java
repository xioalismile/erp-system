package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.service.KaiRechargeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * 小楷独立版 · 支付宝充值（2026-11-07）。
 *
 * <p>客户侧：充值等级列表、创建充值订单、订单状态查询、我的充值记录。
 * 支付宝侧：异步通知（公开、验签）与支付完成跳回（公开）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone/pay")
@RequiredArgsConstructor
public class KaiPayController {

    private final KaiRechargeService rechargeService;

    /** 充值首页状态：在线充值是否可用 + 可用充值等级。 */
    @GetMapping("/packages")
    public Result<?> packages() {
        return Result.success(rechargeService.publicState());
    }

    /** 创建充值订单，返回跳转支付宝收银台的表单。 */
    @PostMapping("/orders")
    public Result<?> createOrder(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("kaiUserId");
        try {
            Long packageId = body.get("packageId") == null ? null
                    : Long.parseLong(String.valueOf(body.get("packageId")).trim());
            return Result.success(rechargeService.createOrder(userId, packageId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[小楷独立版] 创建充值订单失败：{}", e.getMessage());
            return Result.error(500, "下单失败，请稍后再试");
        }
    }

    /** 订单状态：待支付订单会按节流主动向支付宝查单，支付成功即时到账。 */
    @GetMapping("/orders/{orderNo}")
    public Result<?> orderState(HttpServletRequest request, @PathVariable String orderNo) {
        Long userId = (Long) request.getAttribute("kaiUserId");
        try {
            return Result.success(rechargeService.orderState(userId, orderNo));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "查询充值订单失败");
        }
    }

    /** 我的充值记录。 */
    @GetMapping("/orders")
    public Result<?> myOrders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("kaiUserId");
        return Result.success(rechargeService.myOrders(userId));
    }

    /** 支付宝异步通知：必须验签通过才采信，处理成功回 success。 */
    @PostMapping("/alipay/notify")
    public String notify(@RequestParam Map<String, String> params) {
        return rechargeService.handleNotify(params) ? "success" : "failure";
    }

    /** 支付宝支付完成跳回：只带订单号转回本站，支付结果由前台继续查单确认。 */
    @GetMapping("/alipay/return")
    public void payReturn(@RequestParam(name = "out_trade_no", required = false) String outTradeNo,
                          HttpServletResponse response) throws IOException {
        response.sendRedirect(rechargeService.returnRedirectUrl(outTradeNo));
    }
}
