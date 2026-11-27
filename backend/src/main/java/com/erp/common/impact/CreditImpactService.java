package com.erp.common.impact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 客户信用额度占用/释放服务（Phase 2 占位实现，仅打印日志）。
 *
 * <p>典型影响：
 * <ul>
 *   <li>销售订单 审核 → 占用客户信用额度（未执行订单金额）</li>
 *   <li>销售出库/开票 审核 → 释放占用，转入"已发生应收"</li>
 *   <li>销售订单 作废/结案 → 释放未执行部分的信用额度</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class CreditImpactService {

    /** 占用信用额度 */
    public void occupyCredit(String billNo, Long customerId, String currency, BigDecimal amount) {
        log.info("[信用占位] 客户[{}] 单据[{}] 占用信用额度 币别[{}] 金额:{}",
                customerId, billNo, currency, amount);
        // TODO PhaseN：调用 CustomerCreditService.occupy(customerId, billNo, currency, amount)
    }

    /** 释放信用额度 */
    public void releaseCredit(String billNo, Long customerId, String currency, BigDecimal amount) {
        log.info("[信用占位] 客户[{}] 单据[{}] 释放信用额度 币别[{}] 金额:{}",
                customerId, billNo, currency, amount);
        // TODO PhaseN：调用 CustomerCreditService.release(customerId, billNo, currency, amount)
    }
}
