package com.erp.license.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * License 心跳校验定时任务
 * <p>
 * 每 24 小时重新校验一次 License，防止运行期间 License 文件被替换或删除
 * 校验失败会设置 licenseValid=false，由拦截器统一返回 403
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseHeartbeatTask {

    private final LicenseValidator licenseValidator;

    /**
     * 每 24 小时执行一次（固定延迟 24h）
     * fixedDelay = 24 * 60 * 60 * 1000 = 86400000 毫秒
     * initialDelay = 24h 后首次执行（启动时已校验过，无需立即再校验）
     */
    @Scheduled(fixedDelay = 86400000L, initialDelay = 86400000L)
    public void dailyCheck() {
        log.info("开始 License 24h 心跳校验...");
        licenseValidator.heartbeatCheck();
        if (licenseValidator.isLicenseValid()) {
            log.info("License 心跳校验通过");
        } else {
            log.error("License 心跳校验失败：{}", licenseValidator.getFailureReason());
        }
    }
}
