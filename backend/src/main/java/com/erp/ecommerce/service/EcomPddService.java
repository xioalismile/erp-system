package com.erp.ecommerce.service;

import com.erp.config.BusinessException;
import com.erp.ecommerce.entity.EcomShop;
import com.erp.ecommerce.mapper.EcomShopMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * 拼多多开放平台协议服务（商家自研应用直连）
 *
 * 官方口径（2026-10 核实）：
 * - 调用入口：POST https://gw-api.pinduoduo.com/api/router，只收 POST；
 * - 公共参数：type(接口名)/client_id/timestamp(秒)/data_type=JSON/sign_method=md5/access_token(业务接口必带)/sign；
 * - 签名：全部参数（不含 sign）按参数名 ASCII 升序「key+value」无缝拼接，首尾拼 client_secret，
 *   MD5 后转大写；
 * - 授权令牌：OAuth2.0，code 换令牌与刷新令牌都走 https://open-api.pinduoduo.com/oauth/token；
 *   商家自研应用上线后授权有效期 1 年（测试期约 1 天），refresh_token 有效期内可刷新 access_token；
 * - 错误响应：{"error_response":{"error_code":xxx,"error_msg":"..."}}。
 *
 * 凭据加解密：统一走电商平台加密服务（同一把密钥），本类不另造加密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcomPddService {

    private final EcomCryptoService cryptoService;
    private final EcomShopMapper shopMapper;
    private final ObjectMapper objectMapper;

    private static final String GATEWAY_URL = "https://gw-api.pinduoduo.com/api/router";
    private static final String OAUTH_TOKEN_URL = "https://open-api.pinduoduo.com/oauth/token";

    /** 服务器时间接口（免令牌，用于校验客户端编号/密钥/签名是否正确） */
    public static final String TYPE_TIME_GET = "pdd.time.get";
    /** 订单增量接口（需令牌；校验令牌权限 / 拉单） */
    public static final String TYPE_ORDER_INCREMENT = "pdd.order.number.list.increment.get";
    /** 订单详情接口 */
    public static final String TYPE_ORDER_DETAIL = "pdd.order.information.get";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    // ==================== 授权测试 ====================

    /**
     * 拼多多授权测试（两步）：
     * ① 调服务器时间接口（免令牌）——验证客户端编号/密钥/签名是否正确；
     * ② 试调订单增量接口一页 —— 验证访问令牌是否有效、权限包是否含订单查询。
     * 成功更新店铺授权状态；失败置已失效并抛中文原因（由调用方统一处理状态回写）。
     */
    public void testAuth(EcomShop shop) {
        String clientId = cryptoService.decrypt(shop.getClientId());
        String clientSecret = cryptoService.decrypt(shop.getClientSecret());
        String accessToken = cryptoService.decrypt(shop.getAccessToken());
        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new BusinessException("拼多多店铺凭据不完整，请填写客户端编号与客户端密钥");
        }
        // ① 免令牌接口：验证凭据与签名
        callRouter(clientId, clientSecret, null, TYPE_TIME_GET, new TreeMap<>());
        // ② 带令牌接口：验证访问令牌与权限
        if (isBlank(accessToken)) {
            throw new BusinessException("请填写访问令牌：请到拼多多商家后台完成应用授权，把生成的访问令牌粘贴进店铺档案");
        }
        TreeMap<String, String> biz = new TreeMap<>();
        biz.put("order_status", "1");
        biz.put("page", "1");
        biz.put("page_size", "1");
        biz.put("start_updated_at", String.valueOf(System.currentTimeMillis() / 1000 - 3600));
        biz.put("end_updated_at", String.valueOf(System.currentTimeMillis() / 1000));
        callRouter(clientId, clientSecret, accessToken, TYPE_ORDER_INCREMENT, biz);
        shop.setAuthStatus("已授权");
        shop.setLastAuthTime(LocalDateTime.now());
        shopMapper.updateById(shop);
        log.info("[电商平台·拼多多] 店铺[{}]授权测试成功", shop.getShopName());
    }

    // ==================== 访问令牌（带到期刷新） ====================

    /**
     * 取可用的访问令牌：
     * - 未到期 → 直接用；
     * - 已到期且有刷新令牌 → 自动刷新并回写店铺档案（加密落库）；
     * - 已到期且无刷新令牌 → 中文报错要求重新授权。
     */
    public String getAccessToken(EcomShop shop) {
        String accessToken = cryptoService.decrypt(shop.getAccessToken());
        if (isBlank(accessToken)) {
            throw new BusinessException("拼多多店铺[" + shop.getShopName() + "]未配置访问令牌，请完成应用授权后填写");
        }
        if (shop.getTokenExpireAt() != null && LocalDateTime.now().isBefore(shop.getTokenExpireAt().minusMinutes(10))) {
            return accessToken;
        }
        String refreshToken = cryptoService.decrypt(shop.getRefreshToken());
        if (isBlank(refreshToken)) {
            throw new BusinessException("拼多多店铺[" + shop.getShopName() + "]访问令牌已过期且没有刷新令牌，请重新授权并更新店铺档案");
        }
        String clientId = cryptoService.decrypt(shop.getClientId());
        String clientSecret = cryptoService.decrypt(shop.getClientSecret());
        JsonNode json = requestOAuthToken(clientId, clientSecret, "refresh_token", refreshToken);
        String newAccess = json.path("access_token").asText(null);
        String newRefresh = json.path("refresh_token").asText(null);
        long expiresIn = json.path("expires_in").asLong(0);
        if (isBlank(newAccess)) {
            throw new BusinessException("拼多多访问令牌刷新失败：" + translateOAuthError(json));
        }
        shop.setAccessToken(cryptoService.encrypt(newAccess));
        if (!isBlank(newRefresh)) shop.setRefreshToken(cryptoService.encrypt(newRefresh));
        shop.setTokenExpireAt(expiresIn > 0 ? LocalDateTime.now().plusSeconds(expiresIn) : LocalDateTime.now().plusDays(1));
        shopMapper.updateById(shop);
        log.info("[电商平台·拼多多] 店铺[{}]访问令牌已自动刷新", shop.getShopName());
        return newAccess;
    }

    /** 用授权码换令牌并回写店铺档案（店铺授权页「粘贴授权码换令牌」用） */
    public void exchangeCodeAndSave(EcomShop shop, String code) {
        String clientId = cryptoService.decrypt(shop.getClientId());
        String clientSecret = cryptoService.decrypt(shop.getClientSecret());
        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new BusinessException("请先填写客户端编号与客户端密钥，再粘贴授权码换取令牌");
        }
        JsonNode json = requestOAuthToken(clientId, clientSecret, "authorization_code", code);
        String accessToken = json.path("access_token").asText(null);
        String refreshToken = json.path("refresh_token").asText(null);
        long expiresIn = json.path("expires_in").asLong(0);
        if (isBlank(accessToken)) throw new BusinessException("拼多多未返回访问令牌，请重新授权");
        shop.setAccessToken(cryptoService.encrypt(accessToken));
        if (!isBlank(refreshToken)) shop.setRefreshToken(cryptoService.encrypt(refreshToken));
        shop.setTokenExpireAt(expiresIn > 0 ? LocalDateTime.now().plusSeconds(expiresIn) : LocalDateTime.now().plusDays(1));
        shop.setAuthStatus("已授权");
        shop.setLastAuthTime(LocalDateTime.now());
        shopMapper.updateById(shop);
    }

    // ==================== 网关调用 ====================

    /** 调拼多多网关业务接口（自动取/刷新令牌并带签名） */
    public JsonNode callApi(EcomShop shop, String type, Map<String, String> bizParams) {
        String clientId = cryptoService.decrypt(shop.getClientId());
        String clientSecret = cryptoService.decrypt(shop.getClientSecret());
        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new BusinessException("拼多多店铺[" + shop.getShopName() + "]凭据不完整，请到店铺授权页补齐");
        }
        String accessToken = getAccessToken(shop);
        return callRouter(clientId, clientSecret, accessToken, type, bizParams);
    }

    /** 订单详情接口便捷方法 */
    public JsonNode getOrderDetail(EcomShop shop, String orderSn) {
        TreeMap<String, String> biz = new TreeMap<>();
        biz.put("order_sn", orderSn);
        return callApi(shop, TYPE_ORDER_DETAIL, biz);
    }

    /** 订单增量接口便捷方法（startUpdatedAt/endUpdatedAt 为秒级时间戳） */
    public JsonNode getIncrementOrders(EcomShop shop, long startUpdatedAt, long endUpdatedAt, int page, int pageSize) {
        TreeMap<String, String> biz = new TreeMap<>();
        biz.put("order_status", "1"); // 1 = 全部（官方枚举）
        biz.put("start_updated_at", String.valueOf(startUpdatedAt));
        biz.put("end_updated_at", String.valueOf(endUpdatedAt));
        biz.put("page", String.valueOf(page));
        biz.put("page_size", String.valueOf(pageSize));
        return callApi(shop, TYPE_ORDER_INCREMENT, biz);
    }

    /** 网关调用核心：组公共参数 → 签名 → POST JSON（业务参数任意 Map，签名前统一按 ASCII 升序） */
    private JsonNode callRouter(String clientId, String clientSecret, String accessToken, String type, Map<String, String> bizParams) {
        TreeMap<String, String> all = new TreeMap<>();
        all.put("type", type);
        all.put("client_id", clientId);
        all.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        all.put("data_type", "JSON");
        all.put("sign_method", "md5");
        if (!isBlank(accessToken)) all.put("access_token", accessToken);
        if (bizParams != null) all.putAll(bizParams);
        all.put("sign", sign(clientSecret, all));

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(e.getKey())).append(':').append(quote(e.getValue()));
        }
        json.append('}');

        Request request = new Request.Builder()
                .url(GATEWAY_URL)
                .post(RequestBody.create(json.toString(), JSON_TYPE))
                .header("Content-Type", "application/json")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("error_response")) {
                JsonNode err = root.path("error_response");
                String msg = err.path("error_msg").asText("");
                int code = err.path("error_code").asInt(0);
                throw new BusinessException(translatePddError(code, msg));
            }
            if (!response.isSuccessful()) {
                throw new BusinessException("连接拼多多平台失败（平台返回 " + response.code() + "）");
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[电商平台·拼多多] 调用网关接口异常 type={}", type, e);
            throw new BusinessException("连接拼多多平台失败：" + e.getMessage());
        }
    }

    /**
     * 官方签名算法：全部参数按参数名 ASCII 升序「key+value」无缝拼接，
     * 首尾拼接 client_secret，MD5 转大写。
     */
    static String sign(String clientSecret, TreeMap<String, String> params) {
        StringBuilder sb = new StringBuilder(clientSecret);
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(e.getKey()).append(e.getValue() == null ? "" : e.getValue());
        }
        sb.append(clientSecret);
        return md5Upper(sb.toString());
    }

    static String md5Upper(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02X", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5签名失败", e);
        }
    }

    /** OAuth：code 换令牌 / 刷新令牌（grant_type = authorization_code | refresh_token） */
    public JsonNode requestOAuthToken(String clientId, String clientSecret, String grantType, String value) {
        String json = "{\"client_id\":" + quote(clientId)
                + ",\"client_secret\":" + quote(clientSecret)
                + ",\"grant_type\":" + quote(grantType)
                + ",\"" + ("authorization_code".equals(grantType) ? "code" : "refresh_token") + "\":" + quote(value) + "}";
        Request request = new Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .post(RequestBody.create(json, JSON_TYPE))
                .header("Content-Type", "application/json")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            JsonNode root = objectMapper.readTree(body);
            if (!response.isSuccessful() || root.hasNonNull("error")) {
                throw new BusinessException("拼多多授权失败：" + translateOAuthError(root));
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[电商平台·拼多多] OAuth令牌请求异常", e);
            throw new BusinessException("连接拼多多授权服务失败：" + e.getMessage());
        }
    }

    // ==================== 错误翻译 ====================

    private String translatePddError(int code, String msg) {
        String lower = msg == null ? "" : msg.toLowerCase();
        if (code == 40001 || lower.contains("sign")) {
            return "拼多多接口签名校验失败：请检查客户端编号/客户端密钥是否填对（平台原话：" + safe(msg) + "）";
        }
        if (code == 40002 || lower.contains("token")) {
            return "拼多多访问令牌无效或已过期：请重新授权店铺并更新访问令牌（平台原话：" + safe(msg) + "）";
        }
        if (msg != null && msg.contains("权限")) {
            return "拼多多应用权限不足：请到开放平台确认应用已申请订单查询权限包（平台原话：" + safe(msg) + "）";
        }
        if (code == 43001 || lower.contains("throttl")) {
            return "拼多多接口请求过于频繁被限流，请稍后再试";
        }
        return "拼多多平台返回错误" + (code > 0 ? "(" + code + ")" : "") + "：" + safe(msg);
    }

    private String translateOAuthError(JsonNode root) {
        JsonNode err = root.hasNonNull("error") ? root.path("error") : root;
        String msg = err.path("error_description").asText(err.path("error_msg").asText(""));
        return safe(msg).isBlank() ? "平台未给出具体原因" : safe(msg);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String quote(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
