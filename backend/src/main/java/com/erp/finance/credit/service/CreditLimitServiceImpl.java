package com.erp.finance.credit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.service.CurrencyService;
import com.erp.config.BusinessException;
import com.erp.common.util.SortHelper;
import com.erp.finance.credit.dto.CreditCheckResult;
import com.erp.finance.credit.dto.CreditStatistics;
import com.erp.finance.credit.entity.CreditLimit;
import com.erp.finance.credit.entity.CreditLimitLog;
import com.erp.finance.credit.mapper.CreditLimitLogMapper;
import com.erp.finance.credit.mapper.CreditLimitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditLimitServiceImpl extends ServiceImpl<CreditLimitMapper, CreditLimit>
        implements CreditLimitService {

    private final CreditLimitLogMapper creditLimitLogMapper;
    private final CurrencyService currencyService;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤，创建人列 create_by */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    public static final String CHANGE_TYPE_INIT = "INIT";
    public static final String CHANGE_TYPE_INCREASE = "INCREASE";
    public static final String CHANGE_TYPE_DECREASE = "DECREASE";
    public static final String CHANGE_TYPE_ADJUST = "ADJUST";
    public static final String CHANGE_TYPE_ORDER_OCCUPY = "ORDER_OCCUPY";
    public static final String CHANGE_TYPE_ORDER_RELEASE = "ORDER_RELEASE";
    public static final String CHANGE_TYPE_PAYMENT_RECOVERY = "PAYMENT_RECOVERY";

    public static final int STATUS_FROZEN = 0;
    public static final int STATUS_NORMAL = 1;

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "available_amount",
            "base_available_amount",
            "base_credit_limit",
            "base_used_amount",
            "create_by",
            "create_time",
            "credit_limit",
            "currency_code",
            "customer_id",
            "customer_name",
            "effective_date",
            "expiry_date",
            "id",
            "remark",
            "status",
            "update_time",
            "used_amount",
            "warning_ratio"
        );

    @Override
    public Page<CreditLimit> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder) {
        Page<CreditLimit> page = new Page<>(current, size);
        QueryWrapper<CreditLimit> wrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("customer_name", keyword)
                    .or().like("customer_id", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 数据权限（按角色 data_scope）：创建人列 create_by（实体 CreditLimit.createBy），total 随过滤变化
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) wrapper.orderByDesc("create_time");
        return page(page, wrapper);
    }

    @Override
    public CreditLimit getDetail(Long id) {
        if (id == null) {
            throw new BusinessException("ID不能为空");
        }
        CreditLimit creditLimit = getById(id);
        if (creditLimit == null) {
            throw new BusinessException("信誉额度记录不存在");
        }
        return creditLimit;
    }

    @Override
    public CreditLimit getByCustomerId(Long customerId) {
        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }
        QueryWrapper<CreditLimit> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_id", customerId).orderByDesc("create_time").last("LIMIT 1");
        return getOne(wrapper);
    }

    @Override
    @Transactional
    public CreditLimit createCreditLimit(CreditLimit creditLimit) {
        if (creditLimit.getCustomerId() == null) {
            throw new BusinessException("客户ID不能为空");
        }

        CreditLimit existing = getByCustomerId(creditLimit.getCustomerId());
        if (existing != null) {
            throw new BusinessException("该客户已存在信誉额度记录");
        }

        if (creditLimit.getCreditLimit() == null || creditLimit.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("信誉额度不能为空且不能为负数");
        }

        if (creditLimit.getUsedAmount() == null) {
            creditLimit.setUsedAmount(BigDecimal.ZERO);
        }

        creditLimit.setAvailableAmount(
                creditLimit.getCreditLimit().subtract(creditLimit.getUsedAmount())
        );

        if (creditLimit.getStatus() == null) {
            creditLimit.setStatus(STATUS_NORMAL);
        }

        if (creditLimit.getWarningRatio() == null) {
            creditLimit.setWarningRatio(new BigDecimal("0.8"));
        }

        if (creditLimit.getCurrencyCode() == null) {
            Currency baseCurrency = getBaseCurrency();
            if (baseCurrency != null) {
                creditLimit.setCurrencyCode(baseCurrency.getCurrencyCode());
            }
        }

        calculateBaseAmounts(creditLimit);

        save(creditLimit);

        saveLog(creditLimit.getId(), CHANGE_TYPE_INIT,
                BigDecimal.ZERO, creditLimit.getCreditLimit(), creditLimit.getCreditLimit(),
                null, null, null, "初始化额度", null);

        log.info("创建客户信誉额度: customerId={}, creditLimit={}",
                creditLimit.getCustomerId(), creditLimit.getCreditLimit());

        return creditLimit;
    }

    @Override
    @Transactional
    public CreditLimit updateCreditLimit(CreditLimit creditLimit) {
        if (creditLimit.getId() == null) {
            throw new BusinessException("ID不能为空");
        }

        CreditLimit existing = getById(creditLimit.getId());
        if (existing == null) {
            throw new BusinessException("信誉额度记录不存在");
        }

        if (creditLimit.getCreditLimit() != null && creditLimit.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("信誉额度不能为负数");
        }

        BigDecimal oldCreditLimit = existing.getCreditLimit();
        BigDecimal newCreditLimit = creditLimit.getCreditLimit() != null ?
                creditLimit.getCreditLimit() : oldCreditLimit;

        if (creditLimit.getUsedAmount() != null) {
            existing.setUsedAmount(creditLimit.getUsedAmount());
        }

        existing.setAvailableAmount(newCreditLimit.subtract(existing.getUsedAmount()));
        existing.setCreditLimit(newCreditLimit);

        if (creditLimit.getCustomerName() != null) {
            existing.setCustomerName(creditLimit.getCustomerName());
        }
        if (creditLimit.getWarningRatio() != null) {
            existing.setWarningRatio(creditLimit.getWarningRatio());
        }
        if (creditLimit.getStatus() != null) {
            existing.setStatus(creditLimit.getStatus());
        }
        if (creditLimit.getEffectiveDate() != null) {
            existing.setEffectiveDate(creditLimit.getEffectiveDate());
        }
        if (creditLimit.getExpiryDate() != null) {
            existing.setExpiryDate(creditLimit.getExpiryDate());
        }
        if (creditLimit.getCurrencyCode() != null) {
            existing.setCurrencyCode(creditLimit.getCurrencyCode());
        }
        if (creditLimit.getRemark() != null) {
            existing.setRemark(creditLimit.getRemark());
        }

        calculateBaseAmounts(existing);

        updateById(existing);

        BigDecimal changeAmount = newCreditLimit.subtract(oldCreditLimit);
        if (changeAmount.compareTo(BigDecimal.ZERO) != 0) {
            String changeType = changeAmount.compareTo(BigDecimal.ZERO) > 0 ?
                    CHANGE_TYPE_INCREASE : CHANGE_TYPE_DECREASE;
            saveLog(existing.getId(), changeType,
                    oldCreditLimit, changeAmount, newCreditLimit,
                    null, null, null, "修改额度", null);
        }

        log.info("修改客户信誉额度: id={}", creditLimit.getId());

        return existing;
    }

    @Override
    @Transactional
    public CreditLimit adjustCreditLimit(Long id, BigDecimal changeAmount, String reason) {
        if (id == null) {
            throw new BusinessException("ID不能为空");
        }
        if (changeAmount == null) {
            throw new BusinessException("调整金额不能为空");
        }

        CreditLimit existing = getById(id);
        if (existing == null) {
            throw new BusinessException("信誉额度记录不存在");
        }

        BigDecimal oldCreditLimit = existing.getCreditLimit();
        BigDecimal newCreditLimit = oldCreditLimit.add(changeAmount);

        if (newCreditLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("调整后额度不能为负数");
        }

        existing.setCreditLimit(newCreditLimit);
        existing.setAvailableAmount(newCreditLimit.subtract(existing.getUsedAmount()));

        calculateBaseAmounts(existing);

        updateById(existing);

        saveLog(id, CHANGE_TYPE_ADJUST,
                oldCreditLimit, changeAmount, newCreditLimit,
                null, null, null, reason != null ? reason : "调整额度", null);

        log.info("调整客户信誉额度: id={}, changeAmount={}", id, changeAmount);

        return existing;
    }

    @Override
    @Transactional
    public void freezeCreditLimit(Long id) {
        if (id == null) {
            throw new BusinessException("ID不能为空");
        }
        CreditLimit existing = getById(id);
        if (existing == null) {
            throw new BusinessException("信誉额度记录不存在");
        }
        if (existing.getStatus() == STATUS_FROZEN) {
            throw new BusinessException("额度已处于冻结状态");
        }
        existing.setStatus(STATUS_FROZEN);
        updateById(existing);
        log.info("冻结客户信誉额度: id={}", id);
    }

    @Override
    @Transactional
    public void unfreezeCreditLimit(Long id) {
        if (id == null) {
            throw new BusinessException("ID不能为空");
        }
        CreditLimit existing = getById(id);
        if (existing == null) {
            throw new BusinessException("信誉额度记录不存在");
        }
        if (existing.getStatus() == STATUS_NORMAL) {
            throw new BusinessException("额度已处于正常状态");
        }
        existing.setStatus(STATUS_NORMAL);
        updateById(existing);
        log.info("解冻客户信誉额度: id={}", id);
    }

    @Override
    public Page<CreditLimitLog> getCreditLog(Long creditLimitId, int current, int size) {
        if (creditLimitId == null) {
            throw new BusinessException("额度ID不能为空");
        }
        Page<CreditLimitLog> page = new Page<>(current, size);
        QueryWrapper<CreditLimitLog> wrapper = new QueryWrapper<>();
        wrapper.eq("credit_limit_id", creditLimitId)
                .orderByDesc("operate_time");
        return creditLimitLogMapper.selectPage(page, wrapper);
    }

    @Override
    public CreditCheckResult checkCreditLimit(Long customerId, BigDecimal amount) {
        CreditCheckResult result = new CreditCheckResult();

        if (customerId == null || amount == null) {
            result.setPass(false);
            result.setAvailableAmount(BigDecimal.ZERO);
            result.setUsedAmount(BigDecimal.ZERO);
            result.setWarning(false);
            return result;
        }

        CreditLimit creditLimit = getByCustomerId(customerId);
        if (creditLimit == null) {
            result.setPass(false);
            result.setAvailableAmount(BigDecimal.ZERO);
            result.setUsedAmount(BigDecimal.ZERO);
            result.setWarning(false);
            return result;
        }

        if (creditLimit.getStatus() == STATUS_FROZEN) {
            result.setPass(false);
            result.setAvailableAmount(creditLimit.getAvailableAmount());
            result.setUsedAmount(creditLimit.getUsedAmount());
            result.setWarning(false);
            return result;
        }

        BigDecimal available = creditLimit.getAvailableAmount();
        BigDecimal used = creditLimit.getUsedAmount();
        BigDecimal totalLimit = creditLimit.getCreditLimit();

        result.setAvailableAmount(available);
        result.setUsedAmount(used);

        boolean pass = available.compareTo(amount) >= 0;
        result.setPass(pass);

        BigDecimal warningRatio = creditLimit.getWarningRatio();
        if (warningRatio != null && totalLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newUsed = used.add(amount);
            BigDecimal usageRatio = newUsed.divide(totalLimit, 4, RoundingMode.HALF_UP);
            result.setWarning(usageRatio.compareTo(warningRatio) >= 0);
        } else {
            result.setWarning(false);
        }

        return result;
    }

    @Override
    @Transactional
    public void occupyCredit(Long customerId, BigDecimal amount,
                             Long relatedDocId, String relatedDocNo, String relatedDocType) {
        if (customerId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        CreditLimit creditLimit = getByCustomerId(customerId);
        if (creditLimit == null) {
            throw new BusinessException("该客户不存在信誉额度记录");
        }

        if (creditLimit.getStatus() == STATUS_FROZEN) {
            throw new BusinessException("客户信誉额度已冻结，无法占用");
        }

        BigDecimal available = creditLimit.getAvailableAmount();
        if (available.compareTo(amount) < 0) {
            throw new BusinessException("客户信誉额度不足，可用额度: " + available);
        }

        BigDecimal oldUsed = creditLimit.getUsedAmount();
        BigDecimal newUsed = oldUsed.add(amount);
        BigDecimal newAvailable = creditLimit.getCreditLimit().subtract(newUsed);

        creditLimit.setUsedAmount(newUsed);
        creditLimit.setAvailableAmount(newAvailable);

        calculateBaseAmounts(creditLimit);

        updateById(creditLimit);

        saveLog(creditLimit.getId(), CHANGE_TYPE_ORDER_OCCUPY,
                oldUsed, amount, newUsed,
                relatedDocId, relatedDocNo, relatedDocType,
                "订单占用额度", null);

        log.info("占用客户信誉额度: customerId={}, amount={}, docNo={}",
                customerId, amount, relatedDocNo);
    }

    @Override
    @Transactional
    public void releaseCredit(Long customerId, BigDecimal amount,
                              Long relatedDocId, String relatedDocNo, String relatedDocType) {
        if (customerId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        CreditLimit creditLimit = getByCustomerId(customerId);
        if (creditLimit == null) {
            return;
        }

        BigDecimal oldUsed = creditLimit.getUsedAmount();
        BigDecimal releaseAmount = amount.min(oldUsed);
        BigDecimal newUsed = oldUsed.subtract(releaseAmount);
        BigDecimal newAvailable = creditLimit.getCreditLimit().subtract(newUsed);

        creditLimit.setUsedAmount(newUsed);
        creditLimit.setAvailableAmount(newAvailable);

        calculateBaseAmounts(creditLimit);

        updateById(creditLimit);

        saveLog(creditLimit.getId(), CHANGE_TYPE_ORDER_RELEASE,
                oldUsed, releaseAmount.negate(), newUsed,
                relatedDocId, relatedDocNo, relatedDocType,
                "订单释放额度", null);

        log.info("释放客户信誉额度: customerId={}, amount={}, docNo={}",
                customerId, releaseAmount, relatedDocNo);
    }

    @Override
    @Transactional
    public void recoveryCredit(Long customerId, BigDecimal amount,
                               Long relatedDocId, String relatedDocNo, String relatedDocType) {
        if (customerId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        CreditLimit creditLimit = getByCustomerId(customerId);
        if (creditLimit == null) {
            return;
        }

        BigDecimal oldUsed = creditLimit.getUsedAmount();
        BigDecimal recoveryAmount = amount.min(oldUsed);
        BigDecimal newUsed = oldUsed.subtract(recoveryAmount);
        BigDecimal newAvailable = creditLimit.getCreditLimit().subtract(newUsed);

        creditLimit.setUsedAmount(newUsed);
        creditLimit.setAvailableAmount(newAvailable);

        calculateBaseAmounts(creditLimit);

        updateById(creditLimit);

        saveLog(creditLimit.getId(), CHANGE_TYPE_PAYMENT_RECOVERY,
                oldUsed, recoveryAmount.negate(), newUsed,
                relatedDocId, relatedDocNo, relatedDocType,
                "收款恢复额度", null);

        log.info("恢复客户信誉额度: customerId={}, amount={}, docNo={}",
                customerId, recoveryAmount, relatedDocNo);
    }

    @Override
    public CreditStatistics getCreditStatistics() {
        CreditStatistics stats = new CreditStatistics();

        // 数据权限（按角色 data_scope）：统计口径与列表一致（创建人列 create_by）
        QueryWrapper<CreditLimit> scopeWrapper = new QueryWrapper<>();
        dataScopeResolver.applyIfLimited(scopeWrapper, null, "create_by");
        List<CreditLimit> allList = list(scopeWrapper);

        BigDecimal totalCreditLimit = BigDecimal.ZERO;
        BigDecimal totalUsedAmount = BigDecimal.ZERO;
        BigDecimal totalAvailableAmount = BigDecimal.ZERO;
        int warningCount = 0;
        int frozenCount = 0;

        for (CreditLimit cl : allList) {
            BigDecimal baseLimit = cl.getBaseCreditLimit() != null ?
                    cl.getBaseCreditLimit() : cl.getCreditLimit();
            BigDecimal baseUsed = cl.getBaseUsedAmount() != null ?
                    cl.getBaseUsedAmount() : cl.getUsedAmount();
            BigDecimal baseAvailable = cl.getBaseAvailableAmount() != null ?
                    cl.getBaseAvailableAmount() : cl.getAvailableAmount();

            totalCreditLimit = totalCreditLimit.add(baseLimit);
            totalUsedAmount = totalUsedAmount.add(baseUsed);
            totalAvailableAmount = totalAvailableAmount.add(baseAvailable);

            if (cl.getStatus() != null && cl.getStatus() == STATUS_FROZEN) {
                frozenCount++;
            }

            if (cl.getWarningRatio() != null && baseLimit.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal usageRatio = baseUsed.divide(baseLimit, 4, RoundingMode.HALF_UP);
                if (usageRatio.compareTo(cl.getWarningRatio()) >= 0) {
                    warningCount++;
                }
            }
        }

        stats.setTotalCreditLimit(totalCreditLimit);
        stats.setTotalUsedAmount(totalUsedAmount);
        stats.setTotalAvailableAmount(totalAvailableAmount);
        stats.setWarningCustomerCount(warningCount);
        stats.setFrozenCustomerCount(frozenCount);
        stats.setTotalCustomerCount(allList.size());

        return stats;
    }

    private void calculateBaseAmounts(CreditLimit creditLimit) {
        if (creditLimit.getCurrencyCode() == null) {
            creditLimit.setBaseCreditLimit(creditLimit.getCreditLimit());
            creditLimit.setBaseUsedAmount(creditLimit.getUsedAmount());
            creditLimit.setBaseAvailableAmount(creditLimit.getAvailableAmount());
            return;
        }

        Currency currency = currencyService.getByCode(creditLimit.getCurrencyCode());
        if (currency == null || currency.getIsBaseCurrency() != null && currency.getIsBaseCurrency() == 1) {
            creditLimit.setBaseCreditLimit(creditLimit.getCreditLimit());
            creditLimit.setBaseUsedAmount(creditLimit.getUsedAmount());
            creditLimit.setBaseAvailableAmount(creditLimit.getAvailableAmount());
            return;
        }

        BigDecimal rate = currency.getExchangeRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            rate = BigDecimal.ONE;
        }

        creditLimit.setBaseCreditLimit(
                creditLimit.getCreditLimit().multiply(rate).setScale(2, RoundingMode.HALF_UP)
        );
        creditLimit.setBaseUsedAmount(
                creditLimit.getUsedAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP)
        );
        creditLimit.setBaseAvailableAmount(
                creditLimit.getAvailableAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private Currency getBaseCurrency() {
        try {
            // 必须按 currency_id 升序再 LIMIT 1：库里存在多行 is_base_currency=1 的脏数据，
            // 不排序时 LIMIT 1 是随机命中，会导致默认币种不可预期（可能取到非第一行的重复币种）。
            QueryWrapper<Currency> wrapper = new QueryWrapper<>();
            wrapper.eq("is_base_currency", 1).orderByAsc("currency_id").last("LIMIT 1");
            return currencyService.getOne(wrapper);
        } catch (Exception e) {
            log.warn("读取基础币种失败: {}", e.getMessage());
            throw new BusinessException("信用额度计算失败：读取基础币种失败：" + e.getMessage());
        }
    }

    private void saveLog(Long creditLimitId, String changeType,
                         BigDecimal beforeAmount, BigDecimal changeAmount, BigDecimal afterAmount,
                         Long relatedDocId, String relatedDocNo, String relatedDocType,
                         String reason, String operator) {
        CreditLimitLog log = new CreditLimitLog();
        log.setCreditLimitId(creditLimitId);
        log.setChangeType(changeType);
        log.setBeforeAmount(beforeAmount);
        log.setChangeAmount(changeAmount);
        log.setAfterAmount(afterAmount);
        log.setRelatedDocId(relatedDocId);
        log.setRelatedDocNo(relatedDocNo);
        log.setRelatedDocType(relatedDocType);
        log.setReason(reason);
        log.setOperator(operator != null ? operator : "system");
        log.setOperateTime(LocalDateTime.now());
        creditLimitLogMapper.insert(log);
    }
}
