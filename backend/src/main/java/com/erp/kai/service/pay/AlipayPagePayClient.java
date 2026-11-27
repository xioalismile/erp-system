package com.erp.kai.service.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 支付宝电脑网站支付客户端（2026-11-07）。
 *
 * <p>只做三件事：生成「电脑网站支付」跳转表单、按商户订单号查单、校验异步通知。
 * 采用 JDK 原生 RSA2（SHA256withRSA）签名，不额外引入第三方支付 SDK。</p>
 *
 * <p>安全口径：应用私钥只在服务端内存中使用，不打印、不回显；异步通知必须先验签再采信。</p>
 */
public class AlipayPagePayClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final String gateway;
    private final String appId;
    private final String privateKey;
    private final String alipayPublicKey;

    public AlipayPagePayClient(String gateway, String appId, String privateKey, String alipayPublicKey) {
        this.gateway = gateway == null ? "" : gateway.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.alipayPublicKey = alipayPublicKey == null ? "" : alipayPublicKey.trim();
    }

    public boolean usable() {
        return !gateway.isEmpty() && !appId.isEmpty()
                && !privateKey.isEmpty() && !alipayPublicKey.isEmpty();
    }

    /**
     * 生成自动提交到支付宝收银台的页面表单。
     *
     * @param outTradeNo 商户订单号，必须唯一
     * @param amount     订单金额，元，两位小数
     * @param subject    订单标题
     */
    public String buildPagePayForm(String outTradeNo, String amount, String subject,
                                   String notifyUrl, String returnUrl) {
        String biz = "{"
                + "\"out_trade_no\":\"" + escapeJson(outTradeNo) + "\","
                + "\"total_amount\":\"" + escapeJson(amount) + "\","
                + "\"subject\":\"" + escapeJson(subject) + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\","
                + "\"timeout_express\":\"2h\""
                + "}";
        Map<String, String> params = common("alipay.trade.page.pay", biz);
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("sign", sign(params, privateKey));
        return formHtml(gateway, params);
    }

    /** 按商户订单号查单。返回空状态表示未查到或调用失败。 */
    public TradeStatus query(String outTradeNo) {
        String biz = "{\"out_trade_no\":\"" + escapeJson(outTradeNo) + "\"}";
        Map<String, String> params = common("alipay.trade.query", biz);
        params.put("sign", sign(params, privateKey));
        try {
            String body = postForm(gateway, params);
            JsonNode root = MAPPER.readTree(body);
            JsonNode node = root.path("alipay_trade_query_response");
            return new TradeStatus(
                    node.path("code").asText(""),
                    node.path("trade_status").asText(""),
                    node.path("trade_no").asText(""),
                    node.path("total_amount").asText(""),
                    node.path("sub_msg").asText(node.path("msg").asText("")));
        } catch (Exception e) {
            return new TradeStatus("", "", "", "", "查单失败：" + e.getMessage());
        }
    }

    /** 校验支付宝异步通知签名。 */
    public static boolean verifyNotify(Map<String, String> params, String alipayPublicKey) {
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) return false;
        String content = signContent(params, false);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(parsePublicKey(alipayPublicKey));
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> common(String method, String bizContent) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("app_id", appId);
        p.put("method", method);
        p.put("format", "json");
        p.put("charset", "utf-8");
        p.put("sign_type", "RSA2");
        p.put("timestamp", LocalDateTime.now(BEIJING).format(TS));
        p.put("version", "1.0");
        p.put("biz_content", bizContent);
        return p;
    }

    private static String sign(Map<String, String> params, String privateKey) {
        String content = signContent(params, true);
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(parsePrivateKey(privateKey));
            signer.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("支付宝签名失败，请检查应用私钥格式", e);
        }
    }

    /** 待签名串：除 sign 外全部非空参数按键名升序，k=v 用 & 连接。 */
    private static String signContent(Map<String, String> params, boolean includeSignType) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if ("sign".equals(e.getKey())) continue;
            if (!includeSignType && "sign_type".equals(e.getKey())) continue;
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            keys.add(e.getKey());
        }
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(params.get(k));
        }
        return sb.toString();
    }

    private static String formHtml(String gateway, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                .append("<title>正在前往支付宝收银台</title></head>")
                .append("<body><form id=\"alipaySubmit\" method=\"POST\" action=\"")
                .append(escapeHtml(gateway)).append("\">");
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append("<input type=\"hidden\" name=\"").append(escapeHtml(e.getKey()))
                    .append("\" value=\"").append(escapeHtml(e.getValue())).append("\">");
        }
        sb.append("</form><script>document.getElementById('alipaySubmit').submit();</script>")
                .append("</body></html>");
        return sb.toString();
    }

    private static String postForm(String gateway, Map<String, String> params) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(gateway))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private static PrivateKey parsePrivateKey(String raw) throws Exception {
        String cleaned = cleanKey(raw);
        byte[] der = Base64.getDecoder().decode(cleaned);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        try {
            return factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ignore) {
            // 支付宝密钥工具也可能导出 PKCS#1，补一层 PKCS#8 包装后重试
            return factory.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1(der)));
        }
    }

    private static PublicKey parsePublicKey(String raw) throws Exception {
        byte[] der = Base64.getDecoder().decode(cleanKey(raw));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static String cleanKey(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    /** PKCS#1 → PKCS#8 包装。 */
    private static byte[] wrapPkcs1(byte[] pkcs1) throws Exception {
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] algorithm = new byte[]{
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] octetHeader = derHeader((byte) 0x04, pkcs1.length);
        int innerLen = version.length + algorithm.length + octetHeader.length + pkcs1.length;
        byte[] seqHeader = derHeader((byte) 0x30, innerLen);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(seqHeader);
        out.write(version);
        out.write(algorithm);
        out.write(octetHeader);
        out.write(pkcs1);
        return out.toByteArray();
    }

    private static byte[] derHeader(byte tag, int len) {
        if (len < 128) return new byte[]{tag, (byte) len};
        if (len < 256) return new byte[]{tag, (byte) 0x81, (byte) len};
        return new byte[]{tag, (byte) 0x82, (byte) (len >> 8), (byte) (len & 0xff)};
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 支付宝查单结果。code=10000 且 trade_status 为成功态才算已支付。 */
    public record TradeStatus(String code, String tradeStatus, String tradeNo,
                              String totalAmount, String message) {
        public boolean success() {
            return "10000".equals(code);
        }

        public boolean paid() {
            return success() && ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus));
        }

        public boolean closed() {
            return success() && "TRADE_CLOSED".equals(tradeStatus);
        }
    }
}
