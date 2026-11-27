package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 设备接入密钥加解密（与电商凭据同一套方案）：
 * - 设备密钥 AES-GCM 加密后落库，前缀 ENC:；
 * - 加密密钥首次启动自动生成，存 sys_config（device.crypto.secret），不硬编码、不打日志；
 * - 任何接口只返回尾号，明文只在生成/轮换时返回一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCryptoService {

    private final SysConfigService sysConfigService;

    private static final String CRYPTO_KEY_CONFIG = "device.crypto.secret";

    private SecretKey secretKey;
    private byte[] iv;

    @PostConstruct
    public void init() {
        try {
            initCryptoKey();
            log.info("[设备管理] 接入密钥加密密钥初始化完成");
        } catch (Exception e) {
            log.error("[设备管理] 接入密钥加密密钥初始化失败", e);
        }
    }

    private void initCryptoKey() {
        String storedKey = sysConfigService.getValue(CRYPTO_KEY_CONFIG);
        if (storedKey != null && !storedKey.isBlank()) {
            String[] parts = storedKey.split(":");
            if (parts.length == 2) {
                byte[] keyBytes = Base64.getDecoder().decode(parts[0]);
                this.secretKey = new SecretKeySpec(keyBytes, "AES");
                this.iv = Base64.getDecoder().decode(parts[1]);
                return;
            }
        }
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            this.secretKey = keyGen.generateKey();
            this.iv = new byte[12];
            new SecureRandom().nextBytes(this.iv);
            String keyStr = Base64.getEncoder().encodeToString(secretKey.getEncoded())
                    + ":" + Base64.getEncoder().encodeToString(iv);
            saveConfig(CRYPTO_KEY_CONFIG, keyStr);
            log.info("[设备管理] 首次生成接入密钥加密密钥并存储");
        } catch (Exception e) {
            throw new RuntimeException("生成设备接入密钥失败", e);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "ENC:" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("设备密钥加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith("ENC:")) return cipherText;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] decoded = Base64.getDecoder().decode(cipherText.substring(4));
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[设备管理] 设备密钥解密失败", e);
            return null;
        }
    }

    public String mask(String plainText) {
        if (plainText == null || plainText.length() <= 4) return "****";
        return "****" + plainText.substring(plainText.length() - 4);
    }

    private void saveConfig(String key, String value) {
        SysConfig config = sysConfigService.getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigName("设备接入密钥加密密钥");
            config.setConfigGroup("device");
            config.setConfigType("secret");
            config.setRemark("系统自动生成，请勿修改");
            config.setSortOrder(0);
            config.setConfigValue(value);
            sysConfigService.save(config);
        } else {
            config.setConfigValue(value);
            sysConfigService.updateById(config);
        }
        sysConfigService.refreshCache();
    }
}
