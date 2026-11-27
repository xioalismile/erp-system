package com.erp.crm.scheduler;

import com.erp.crm.service.SalesWarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 销售过程动作量化 · 定时任务（2026-10-07）
 *
 * <p>与既有任务错开：公海回收 01:00、消息通知扫描每 2 分钟（这两个不动）；
 * 本组任务只做过程结算与预警推进，避免把每 2 分钟的扫描放大。</p>
 *
 * <p><b>说明</b>：按立项登记表，每日 00:05 的「生成当日任务清单」未做单独落库——今日任务清单由
 * 「销售过程」页按「下次跟进日期 = 当天」实时查询呈现，无需预生成（少一张表、少一处不一致）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesWarningScheduler {

    private final SalesWarningService salesWarningService;

    /** 每日 09:00：结算前一天过程指标 + 生成销售预警（黄/橙/红分档） */
    @Scheduled(cron = "0 0 9 * * ?")
    public void settleDaily() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            log.info("[销售预警] 每日结算开始：{}", yesterday);
            salesWarningService.generate(yesterday);
        } catch (Exception e) {
            log.error("[销售预警] 每日结算异常", e);
        }
    }

    /** 每周一 09:00：结算上周（周任务：寄样品 / 朋友圈推广 / RFID 覆盖） */
    @Scheduled(cron = "0 0 9 ? * MON")
    public void settleWeekly() {
        try {
            LocalDate lastSunday = LocalDate.now().minusDays(1);
            log.info("[销售预警] 每周结算开始：上周（截至 {}）", lastSunday);
            salesWarningService.generate(lastSunday);
        } catch (Exception e) {
            log.error("[销售预警] 每周结算异常", e);
        }
    }

    /** 每月 1 日 09:00：结算上月（生成上月最后一天口径的预警；奖金核算由页面按需计算，不落库） */
    @Scheduled(cron = "0 0 9 1 * ?")
    public void settleMonthly() {
        try {
            LocalDate lastDayOfPrevMonth = LocalDate.now().minusDays(1);
            log.info("[销售预警] 每月结算开始：上月（截至 {}）", lastDayOfPrevMonth);
            salesWarningService.generate(lastDayOfPrevMonth);
        } catch (Exception e) {
            log.error("[销售预警] 每月结算异常", e);
        }
    }

    /**
     * 每 15 分钟：超时未处理的预警自动升级一级并重新计时。
     *
     * <p>注：立项登记表里这条原本是「每 15 分钟拉取物流异常」。物流三态中「拒收/签收失败」需要实时调
     * 快递100 轨迹，本期未接（避免每 15 分钟消耗接口额度）；本地可判定的「样品发货 15 天未跟进」已并入
     * 每日结算。故此处承载的是同样时间敏感的「超时升级」。</p>
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void escalateTimeout() {
        try {
            salesWarningService.escalate();
        } catch (Exception e) {
            log.error("[销售预警] 超时升级异常", e);
        }
    }

    /**
     * 每 15 分钟：物流异常拉取（轨迹 24h 无更新 / 拒收签收失败 / 在途超期未签收）。
     *
     * <p>与 escalateTimeout 同为每 15 分钟（错开 30 秒启动，避免同一秒并发）。快递100 未配置授权码时
     * {@code SalesWarningService.checkLogistics()} 内部整体跳过，不产生误报警。</p>
     */
    @Scheduled(cron = "30 */15 * * * ?")
    public void checkLogistics() {
        try {
            salesWarningService.checkLogistics();
        } catch (Exception e) {
            log.error("[销售预警] 物流异常检查异常", e);
        }
    }
}
