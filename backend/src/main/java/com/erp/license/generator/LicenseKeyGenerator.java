package com.erp.license.generator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * RSA 密钥对生成工具
 * <p>
 * 供应商内部使用：生成 RSA 2048 位密钥对
 * - 私钥：用于签发 License（绝不能泄露给客户）
 * - 公钥：通过环境变量注入到客户部署的服务器，用于校验 License 签名
 */
public class LicenseKeyGenerator {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // 转为 Base64 字符串（不含 PEM 头尾标记，纯 Base64）
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        System.out.println("===== RSA 2048 密钥对生成成功 =====\n");
        System.out.println("===== 私钥（PKCS#8 Base64，仅供应商保留，绝不能交付客户）=====");
        System.out.println(privateKeyBase64);
        System.out.println("\n===== 公钥（X.509 Base64，注入到客户服务器的环境变量 ERP_LICENSE_PUBLIC_KEY）=====");
        System.out.println(publicKeyBase64);
        System.out.println("\n===== 保存方式 =====");
        System.out.println("1. 将私钥保存到 private_key_base64.txt（用于 LicenseGenerator）");
        System.out.println("2. 将公钥保存到 public_key_base64.txt");
        System.out.println("3. 客户部署时：export ERP_LICENSE_PUBLIC_KEY=" + publicKeyBase64.substring(0, 20) + "...");
    }
}
