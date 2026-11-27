package com.erp.ecommerce.service;

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
 * 电商平台凭据加解密服务（店铺授权）
 *
 * 口径（与 AI 渠道配置同一套方案）：
 * - 卖家平台密钥（客户端密钥/刷新令牌）AES-GCM 加密后落库，前缀 ENC:；
 * - 加密密钥首次启动自动生成，存 sys_config，不硬编码、不打日志；
 * - 任何接口返回一律脱敏（只回是否已配置，不回明文）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcomCryptoService {

    private final SysConfigService sysConfigService;

    private static final String CRYPTO_KEY_CONFIG = "ecom.crypto.secret";

    private SecretKey secretKey;
    private byte[] iv;

    @PostConstruct
    public void init() {
        try {
            initCryptoKey();
            log.info("[电商平台] 凭据加密密钥初始化完成");
        } catch (Exception e) {
            log.error("[电商平台] 凭据加密密钥初始化失败", e);
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
            saveConfig(CRYPTO_KEY_CONFIG, keyStr, "电商平台凭据加密密钥", "ecommerce", "secret", "系统自动生成，请勿修改");
            log.info("[电商平台] 首次生成凭据加密密钥并存储");
        } catch (Exception e) {
            throw new RuntimeException("生成电商平台凭据加密密钥失败", e);
        }
    }

    /** 加密（明文 → ENC:Base64） */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "ENC:" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("凭据加密失败", e);
        }
    }

    /** 解密（ENC:Base64 → 明文；非 ENC: 前缀原样返回，兼容历史数据） */
    public String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith("ENC:")) {
            return cipherText;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] decoded = Base64.getDecoder().decode(cipherText.substring(4));
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[电商平台] 凭据解密失败", e);
            return null;
        }
    }

    /** 脱敏展示：只露末 4 位 */
    public String mask(String plainText) {
        if (plainText == null || plainText.length() <= 4) return "****";
        return "****" + plainText.substring(plainText.length() - 4);
    }

    private void saveConfig(String key, String value, String name, String group, String type, String remark) {
        SysConfig config = sysConfigService.getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigName(name);
            config.setConfigGroup(group);
            config.setConfigType(type);
            config.setRemark(remark);
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
