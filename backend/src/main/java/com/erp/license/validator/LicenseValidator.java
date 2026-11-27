package com.erp.license.validator;

import com.erp.license.entity.LicenseInfo;
import com.erp.license.loader.LicenseLoader;
import com.erp.license.util.LicenseSignatureUtil;
import com.erp.license.util.MacAddressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;

/**
 * License 校验器
 * <p>
 * 校验流程：
 * 1. 加载 license.lic 文件
 * 2. 验证 RSA 签名（防篡改）
 * 3. 验证 MAC 地址（防拷贝到其他机器运行）
 * 4. 验证有效期（防超期使用）
 * 5. 验证功能模块（可选，按需）
 * <p>
 * 失败处理：
 * - 启动时失败：抛 RuntimeException 阻止 Spring Boot 启动
 * - 运行中失败（心跳）：设置 licenseValid=false，拦截器返回 403
 * <p>
 * 安全设计：
 * - SECRET_KEY 通过环境变量 ERP_LICENSE_PUBLIC_KEY 注入，不硬编码
 * - 公钥仅用于验证签名，无法用于伪造 License
 * - license.lic 文件采用 RSA 数字签名，客户修改任何字段都会校验失败
 */
@Slf4j
@Component
public class LicenseValidator {

    @Value("${erp.license.file-path:license.lic}")
    private String licenseFilePath;

    /**
     * RSA 公钥（Base64），从环境变量注入，不硬编码在代码中
     * 配置方式：export ERP_LICENSE_PUBLIC_KEY=MIIBIjANBgkqhkiG...
     * application.yml 中已配置 ${ERP_LICENSE_PUBLIC_KEY:} 占位
     */
    @Value("${erp.license.public-key:}")
    private String publicKey;

    /** 当前 License 信息 */
    private LicenseInfo currentLicense;

    /** 校验是否通过 */
    private volatile boolean licenseValid = false;

    /** 失败原因 */
    private String failureReason;

    @Value("${erp.license.enabled:true}")
    private boolean licenseEnabled;

    @PostConstruct
    public void init() {
        if (!licenseEnabled) {
            log.warn("==========================================");
            log.warn("  License 校验已跳过（开发模式：erp.license.enabled=false）");
            log.warn("  正式部署请确保 erp.license.enabled=true");
            log.warn("==========================================");
            licenseValid = true;
            return;
        }
        validate();
        if (!licenseValid) {
            log.error("==========================================");
            log.error("  License 校验失败：{}", failureReason);
            log.error("  系统将无法正常使用，请联系供应商获取有效授权");
            log.error("==========================================");
            throw new RuntimeException("License 校验失败: " + failureReason);
        }
        log.info("License 校验通过 - 客户:{} 项目:{} 到期:{}",
                currentLicense.getCustomer(),
                currentLicense.getProjectName(),
                currentLicense.getExpireDate());
    }

    /**
     * 执行完整校验
     */
    public void validate() {
        try {
            // 1. 加载 License 文件
            currentLicense = LicenseLoader.load(licenseFilePath);
            if (currentLicense == null) {
                failureReason = "License 文件为空";
                licenseValid = false;
                return;
            }

            // 2. 获取公钥（从环境变量注入）
            if (publicKey == null || publicKey.isEmpty()) {
                failureReason = "RSA 公钥未配置（请设置环境变量 ERP_LICENSE_PUBLIC_KEY）";
                licenseValid = false;
                return;
            }

            // 3. 验证签名
            String payload = currentLicense.toSignPayload();
            boolean signValid = LicenseSignatureUtil.verify(
                    publicKey, payload, currentLicense.getSignature());
            if (!signValid) {
                failureReason = "License 签名校验失败（文件可能被篡改）";
                licenseValid = false;
                return;
            }

            // 4. 验证 MAC 地址（硬件绑定）
            if (!MacAddressUtil.isLocalMacAllowed(currentLicense.getMacAddresses())) {
                failureReason = "当前机器 MAC 地址不在授权范围内";
                licenseValid = false;
                return;
            }

            // 5. 验证有效期
            LocalDate today = LocalDate.now();
            if (currentLicense.getExpireDate() != null && today.isAfter(currentLicense.getExpireDate())) {
                failureReason = "License 已过期（到期日：" + currentLicense.getExpireDate() + "）";
                licenseValid = false;
                return;
            }

            // 6. 验证未生效
            if (currentLicense.getIssueDate() != null && today.isBefore(currentLicense.getIssueDate())) {
                failureReason = "License 尚未生效（生效日：" + currentLicense.getIssueDate() + "）";
                licenseValid = false;
                return;
            }

            licenseValid = true;
            failureReason = null;
        } catch (Exception e) {
            failureReason = "License 加载失败: " + e.getMessage();
            licenseValid = false;
        }
    }

    /**
     * 心跳校验（运行中定期调用）
     * 重新读取 license.lic 并校验，防止运行期间 License 文件被替换
     * <p>
     * 注意：开发模式（erp.license.enabled=false）必须直接跳过。
     * 2026-08-24 踩坑实录：此处原不检查开关，导致 enabled=false 启动的服务
     * 在运行满 24h 后首次心跳把 licenseValid 从 true 改成 false（RSA 公钥未配置），
     * 全站 /api/** 被 LicenseInterceptor 返回 403。
     */
    public void heartbeatCheck() {
        if (!licenseEnabled) {
            return;
        }
        boolean previous = licenseValid;
        validate();
        if (previous && !licenseValid) {
            log.error("License 心跳校验失败：{}", failureReason);
        } else if (!previous && licenseValid) {
            log.info("License 恢复有效");
        }
    }

    /** License 校验总开关是否启用（供拦截器等做防御性放行判断） */
    public boolean isLicenseEnabled() {
        return licenseEnabled;
    }

    public boolean isLicenseValid() {
        return licenseValid;
    }

    public LicenseInfo getCurrentLicense() {
        return currentLicense;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean isFeatureAllowed(String feature) {
        if (!licenseEnabled) {
            return true;
        }
        if (!licenseValid || currentLicense == null) {
            return false;
        }
        if (currentLicense.getFeatures() == null || currentLicense.getFeatures().isEmpty()) {
            return true;
        }
        return currentLicense.getFeatures().contains(feature.toLowerCase());
    }

    public boolean isUserCountExceeded(int currentUserCount) {
        if (!licenseValid || currentLicense == null) {
            return true;
        }
        int maxUsers = currentLicense.getMaxUsers();
        if (maxUsers <= 0) {
            return false;
        }
        return currentUserCount > maxUsers;
    }

    public int getMaxUsers() {
        if (currentLicense == null) {
            return 0;
        }
        return currentLicense.getMaxUsers();
    }
}
