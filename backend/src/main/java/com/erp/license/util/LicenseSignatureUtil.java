package com.erp.license.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 签名工具类
 * <p>
 * 算法：SHA256withRSA
 * 密钥长度：2048 bit
 * 编码：Base64
 * <p>
 * 使用方式：
 * - 生成端：用私钥对 LicenseInfo.toSignPayload() 签名，得到 signature 字段
 * - 校验端：用公钥验证 signature 与 LicenseInfo.toSignPayload() 匹配
 */
public final class LicenseSignatureUtil {

    private static final String ALGORITHM = "RSA";
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    private LicenseSignatureUtil() {}

    /**
     * 用私钥对原文签名
     *
     * @param privateKeyBase64 私钥 Base64 字符串
     * @param payload          待签名原文
     * @return 签名 Base64 字符串
     */
    public static String sign(String privateKeyBase64, String payload) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
        PrivateKey privateKey = kf.generatePrivate(spec);

        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 用公钥验证签名
     *
     * @param publicKeyBase64 公钥 Base64 字符串
     * @param payload         原文
     * @param signatureBase64 签名 Base64
     * @return true 校验通过，false 校验失败
     */
    public static boolean verify(String publicKeyBase64, String payload, String signatureBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            PublicKey publicKey = kf.generatePublic(spec);

            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }
}
