package com.erp.common.credit;

import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.finance.service.FinanceService;
import com.erp.masterdata.entity.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 客户信用额度守卫（2026-09-07 客户需求：额度超限预警并禁止提交销售单据）
 * <p>
 * 口径说明（与销售订单既有校验完全一致，实时未收口径、无占用台账）：
 *  - 已用额度 = 该客户应收未收余额（account_receivable 实时聚合 unreceivedAmount）
 *  - 收款审核确认后未收自动变小 = 额度自动释放，无需额外释放逻辑
 *  - 校验公式：客户信用额度 > 0 且「已用额度 + 本次单据金额 > 信用额度」→ 拦截
 * <p>
 * 后台总开关 sale.credit.limit.block（sys_config，group=sale，默认 false）：
 *  - false = 关闭（历史 sale.credit.check 迁移而来，旧默认 true 迁移为 false，由用户在设置中心开启）
 *  - true 且客户 credit_limit>0 才生效；客户额度填 0 表示不限制该客户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditGuardService {

    /** 信用额度超限阻断总开关 key */
    public static final String CFG_CREDIT_LIMIT_BLOCK = "sale.credit.limit.block";

    private final SysConfigService sysConfigService;
    private final FinanceService financeService;

    /**
     * 查询客户当前已用信用额度（应收未收余额，实时聚合）。
     * 查询失败按 0 处理（放行由外层金额比较决定，与销售订单既有降级逻辑一致）。
     */
    public BigDecimal queryUsedCredit(Long customerId) {
        if (customerId == null) return BigDecimal.ZERO;
        try {
            Map<String, Object> summary = financeService.getReceivableSummary(null, null, null, customerId, null);
            if (summary == null) return BigDecimal.ZERO;
            Object unreceived = summary.get("unreceivedAmount");
            if (unreceived instanceof BigDecimal) return (BigDecimal) unreceived;
            if (unreceived != null) return new BigDecimal(unreceived.toString());
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("查询客户应收余额失败，customerId: {}, err={}", customerId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * 信用额度超限校验（开关开 + 客户额度>0 才生效）。
     *
     * @param billName       单据中文名（如 销售合同 / 销售订单），用于报错文案
     * @param customer       客户（可为空，空则跳过）
     * @param currentAmount  本次单据净额
     */
    public void validateCreditLimit(String billName, Customer customer, BigDecimal currentAmount) {
        if (customer == null) return;
        boolean block = sysConfigService.getBooleanValue(CFG_CREDIT_LIMIT_BLOCK, false);
        if (!block) return;
        BigDecimal creditLimit = customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO;
        if (creditLimit.compareTo(BigDecimal.ZERO) <= 0) return; // 额度填 0 = 该客户不限制
        BigDecimal usedCredit = queryUsedCredit(customer.getCustomerId());
        BigDecimal amount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
        if (usedCredit.add(amount).compareTo(creditLimit) > 0) {
            throw new BusinessException(String.format(
                    "【设置中心-信用额度管控】客户[%s]信用额度不足，禁止提交%s。额度: %.2f，已用: %.2f，本单: %.2f",
                    customer.getCustomerName(), billName, creditLimit, usedCredit, amount));
        }
    }
}
