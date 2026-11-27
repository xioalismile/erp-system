package com.erp.common.qrcode;

import com.erp.auth.util.JwtSecretProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 二维码签名（2026-09-27，P1 对外发码安全）
 *
 * 背景：码内容原本只是"类型 + 主键"，主键递增 → 任何人可手动编造一个码/网址，
 * 冒充"本系统打印出来的码"。对外发码必须先解决这个。
 *
 * 做法：用与 JWT 同源的密钥（{@link JwtSecretProvider}，环境变量/密钥文件/自动落盘）
 * 派生一把"二维码专用"密钥，对 (版本|类型|单据类型|主键) 做 HMAC-SHA256，取前 16 字节做签名，
 * 附在码的 s 参数上；解析时校验，不匹配即判为伪造。
 *
 * 兼容：历史已打印的旧码没有 s 参数 —— 默认仍放行（系统内使用），
 * 可用系统参数 qrcode.sign.strict=1 收紧为"无签名一律拒绝"（对外发码建议开启）。
 * 只打印来源与结果，绝不打印密钥或签名明文。
 */
@Component
public class QrCodeSigner {

    /** 协议版本：以后换算法/换内容格式时升版本，便于灰度 */
    public static final String VERSION = "1";

    private static final String PURPOSE = "erp-qr-code-sign-v1";
    private static final int SIG_BYTES = 16;

    private final String derivedKey;

    public QrCodeSigner(JwtSecretProvider jwtSecretProvider) {
        this.derivedKey = derive(jwtSecretProvider.getSecret(), PURPOSE);
    }

    /** 生成签名（无副作用的纯计算，便于单测） */
    public String sign(String type, String docType, Long id) {
        return sign(type, docType, id, null);
    }

    /**
     * 生成签名（可纳入公司标识）：带公司标识时签名与公司绑定，
     * 别的公司的码无法在自家系统通过校验（多公司独立库主键会重复，2026-10-25）。
     */
    public String sign(String type, String docType, Long id, String tenant) {
        String payload = VERSION + "|" + nz(type) + "|" + nz(docType) + "|" + (id == null ? "" : id)
                + (tenant == null || tenant.isBlank() ? "" : "|" + tenant.trim());
        return hmac(payload);
    }

    /** 校验签名（常量时间比较，避免时序猜测） */
    public boolean verify(String type, String docType, Long id, String signature) {
        return verify(type, docType, id, signature, null);
    }

    /** 校验签名（公司标识参与签名；旧码无公司标识时按旧算法校验，保持兼容） */
    public boolean verify(String type, String docType, Long id, String signature, String tenant) {
        if (signature == null || signature.isBlank()) return false;
        String expect = sign(type, docType, id, tenant);
        return constantTimeEquals(expect, signature.trim());
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derivedKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] cut = new byte[SIG_BYTES];
            System.arraycopy(full, 0, cut, 0, SIG_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cut);
        } catch (Exception e) {
            throw new IllegalStateException("二维码签名计算失败: " + e.getMessage(), e);
        }
    }

    /** 用主密钥 + 用途串派生独立密钥（不同用途互不影响，泄露一个不牵连另一个） */
    private static String derive(String masterSecret, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(nz(masterSecret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("二维码密钥派生失败: " + e.getMessage(), e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
