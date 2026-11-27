package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.system.entity.CommissionRate;
import com.erp.system.mapper.CommissionRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 提成率配置服务 - 历史版本留痕
 *
 * 核心规则：
 *   1. 新增版本时，自动把上一版的 expiry_date 设为新版 effective_date - 1，is_current 设为 0
 *   2. 新版本 is_current = 1，成为当前生效版本
 *   3. 查询某天生效的版本：effective_date <= date AND (expiry_date IS NULL OR expiry_date >= date)
 *   4. 查询区间内所有生效版本：用于跨月分段计算提成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionRateService {

    private final CommissionRateMapper commissionRateMapper;

    /** 获取当前生效版本 */
    public CommissionRate getCurrent() {
        return commissionRateMapper.selectOne(
                new LambdaQueryWrapper<CommissionRate>()
                        .eq(CommissionRate::getIsCurrent, (short) 1)
                        .orderByDesc(CommissionRate::getEffectiveDate)
                        .last("LIMIT 1"));
    }

    /** 获取某天生效的版本（用于按订单日期匹配历史提成率） */
    public CommissionRate getEffectiveAt(final LocalDate date) {
        if (date == null) return getCurrent();
        return commissionRateMapper.selectOne(
                new LambdaQueryWrapper<CommissionRate>()
                        .le(CommissionRate::getEffectiveDate, date)
                        .and(w -> w.isNull(CommissionRate::getExpiryDate)
                                .or().ge(CommissionRate::getExpiryDate, date))
                        .orderByDesc(CommissionRate::getEffectiveDate)
                        .last("LIMIT 1"));
    }

    /** 获取区间内所有生效的版本（按生效日期升序），用于跨月分段计算 */
    public List<CommissionRate> getRangeVersions(LocalDate startDate, LocalDate endDate) {
        final LocalDate start = startDate == null ? LocalDate.of(2000, 1, 1) : startDate;
        final LocalDate end = endDate == null ? LocalDate.of(2100, 1, 1) : endDate;
        return commissionRateMapper.selectList(
                new LambdaQueryWrapper<CommissionRate>()
                        .le(CommissionRate::getEffectiveDate, end)
                        .and(w -> w.isNull(CommissionRate::getExpiryDate)
                                .or().ge(CommissionRate::getExpiryDate, start))
                        .orderByAsc(CommissionRate::getEffectiveDate));
    }

    /** 分页查询历史版本列表（按版本号倒序） */
    public List<CommissionRate> listHistory(int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return commissionRateMapper.selectList(
                new LambdaQueryWrapper<CommissionRate>()
                        .orderByDesc(CommissionRate::getVersionNo)
                        .last("LIMIT " + size + " OFFSET " + offset));
    }

    /** 总版本数 */
    public int countAll() {
        return Math.toIntExact(commissionRateMapper.selectCount(null));
    }

    /**
     * 新增一版提成率配置。
     * 事务内完成：
     *   1. 计算新版本号 = 最大版本号 + 1
     *   2. 把当前生效版本的 expiry_date 设为新版 effective_date - 1，is_current = 0
     *   3. 插入新版本，is_current = 1
     */
    @Transactional
    public CommissionRate createVersion(CommissionRate newRate, String operator) {
        // 1. 计算新版本号
        Integer maxVersion = commissionRateMapper.selectList(
                new LambdaQueryWrapper<CommissionRate>()
                        .orderByDesc(CommissionRate::getVersionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(CommissionRate::getVersionNo).orElse(0);
        newRate.setVersionNo(maxVersion + 1);

        if (newRate.getEffectiveDate() == null) {
            newRate.setEffectiveDate(LocalDate.now());
        }
        if (newRate.getBasis() == null) newRate.setBasis("order");
        if (newRate.getBaseRate() == null)
            throw new IllegalArgumentException("基础提成率 baseRate 不能为空");
        if (newRate.getOverAchievementMultiplier() == null)
            newRate.setOverAchievementMultiplier(new BigDecimal("1.20"));
        if (newRate.getOverAchievementBonus() == null)
            newRate.setOverAchievementBonus(new BigDecimal("0.0300"));
        newRate.setCreatedBy(operator);
        newRate.setIsCurrent((short) 1);
        newRate.setExpiryDate(null);

        // 2. 关闭当前生效版本：expiry_date 设为新版 effective_date - 1
        CommissionRate current = getCurrent();
        if (current != null) {
            // 不允许新版本的生效日期早于当前版本的生效日期
            if (newRate.getEffectiveDate().isBefore(current.getEffectiveDate())) {
                throw new IllegalArgumentException(
                        "新版本生效日期(" + newRate.getEffectiveDate()
                                + ")不能早于当前版本生效日期(" + current.getEffectiveDate() + ")");
            }
            // 不允许新版本与当前版本同一天生效（同一天等于没改）
            if (newRate.getEffectiveDate().equals(current.getEffectiveDate())) {
                throw new IllegalArgumentException(
                        "新版本生效日期不能与当前版本相同(" + current.getEffectiveDate() + ")");
            }
            current.setExpiryDate(newRate.getEffectiveDate().minusDays(1));
            current.setIsCurrent((short) 0);
            commissionRateMapper.updateById(current);
        }

        // 3. 插入新版本
        commissionRateMapper.insert(newRate);
        log.info("新增提成率版本 v{}: baseRate={} effective={} operator={}",
                newRate.getVersionNo(), newRate.getBaseRate(), newRate.getEffectiveDate(), operator);
        return newRate;
    }
}
