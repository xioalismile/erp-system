package com.erp.kai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 小楷独立版 · 凭据加密（FEAT-KAI-STANDALONE-001）。
 * AES-GCM 加密落库，前缀 ENC:；密钥来源与 AI 配置加密同口径（配置 > 自动生成落盘）。
 * 密钥仅存在于服务端，任何接口都不回显明文凭据。
 */
@Slf4j
@Service
public class KaiCryptoService {

    private static final String PREFIX = "ENC:";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public KaiCryptoService(@Value("${kai.crypto.secret:}") String configuredSecret) {
        this.secretKey = resolveKey(configuredSecret);
    }

    private SecretKey resolveKey(String configured) {
        try {
            byte[] keyBytes;
            if (configured != null && !configured.isBlank()) {
                keyBytes = Arrays.copyOf(sha256(configured), 32);
            } else {
                KeyGenerator gen = KeyGenerator.getInstance("AES");
                gen.init(256);
                keyBytes = gen.generateKey().getEncoded();
                log.warn("[小楷独立版] 未配置凭据加密密钥，已自动生成临时密钥（重启后已存凭据将无法解密，请配置 kai.crypto.secret）");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("小楷独立版凭据加密密钥初始化失败", e);
        }
    }

    private byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 加密：明文 → ENC:Base64(iv+cipher)。失败抛中文异常。 */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败：" + e.getMessage(), e);
        }
    }

    /** 解密：ENC:Base64 → 明文。无前缀视为历史明文原样返回（兼容）。 */
    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LEN);
            byte[] enc = Arrays.copyOfRange(all, GCM_IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败（密钥可能已更换），请重新保存该连接的凭据", e);
        }
    }

    /** 脱敏尾号：只留最后 4 位，供列表展示。 */
    public String maskTail(String plain) {
        if (plain == null || plain.isBlank()) return "";
        String s = plain.trim();
        if (s.length() <= 4) return "****";
        return "****" + s.substring(s.length() - 4);
    }
}
