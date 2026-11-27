package com.erp.scheduler;

import com.erp.config.service.SysConfigService;
import com.erp.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 客户公海池自动回收 / 跟进预警 定时任务
 * 来源：2026-07-01 客户录音访谈需求
 * - 每日凌晨1点扫描长期未跟进/未开单客户，自动转入公海
 * - 每日凌晨1点半扫描预警客户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerPoolScheduler {

    private final CustomerService customerService;
    private final SysConfigService sysConfigService;

    /** 公海池自动回收：每日 01:00 执行 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void autoRecycleToPool() {
        // 读取设置中心开关：是否启用公海自动回收
        boolean recycleEnabled = sysConfigService.getBooleanValue("crm.pool.recycle.enabled", true);
        if (!recycleEnabled) {
            log.info("【设置中心】公海自动回收已关闭，跳过执行");
            return;
        }
        try {
            int n = customerService.autoRecycleToPool();
            log.info("[定时任务] 公海池自动回收完成，回收客户数: {}", n);
        } catch (Exception e) {
            log.error("[定时任务] 公海池自动回收失败", e);
        }
    }

    /** 客户跟进预警检查：每日 01:30 执行 */
    @Scheduled(cron = "0 30 1 * * ?")
    public void followUpWarningCheck() {
        // 读取设置中心开关：是否启用跟进预警
        boolean followWarnEnabled = sysConfigService.getBooleanValue("crm.warning.follow.enabled", true);
        if (!followWarnEnabled) {
            log.info("【设置中心】跟进预警已关闭，跳过执行");
            return;
        }
        try {
            int n = customerService.checkFollowUpWarning().size();
            log.info("[定时任务] 客户跟进预警检查完成，预警客户数: {}", n);
        } catch (Exception e) {
            log.error("[定时任务] 客户跟进预警检查失败", e);
        }
    }
}
