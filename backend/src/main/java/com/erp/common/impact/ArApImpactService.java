package com.erp.common.impact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 应收/应付影响服务（Phase 2 占位实现，仅打印日志，后续接 FinanceService 真实应收应付账本）。
 *
 * <p>典型影响：
 * <ul>
 *   <li>销售发票 审核 → 生成应收账款（客户欠款增加）</li>
 *   <li>销售发票 作废/红冲 → 应收账款红冲减少</li>
 *   <li>收款单 审核 → 应收账款核销（客户欠款减少，已收增加）</li>
 *   <li>采购发票 审核 → 生成应付账款（供应商欠款增加）</li>
 *   <li>付款单 审核 → 应付账款核销（欠供应商的减少）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class ArApImpactService {

    /** 应收影响 */
    public void impactAr(String billNo, Long customerId, String currency,
                         BigDecimal amount, String direction /* "raise"生成应收 / "clear"核销应收 */) {
        log.info("[AR占位] 客户[{}] 单据[{}] 币别[{}] 应收方向:{} 金额:{}",
                customerId, billNo, currency, direction, amount);
        // TODO PhaseN：调用 FinanceService.createArFromBill / cancelArFromBill(...)
    }

    /** 应付影响 */
    public void impactAp(String billNo, Long supplierId, String currency,
                         BigDecimal amount, String direction /* "raise"生成应付 / "clear"核销应付 */) {
        log.info("[AP占位] 供应商[{}] 单据[{}] 币别[{}] 应付方向:{} 金额:{}",
                supplierId, billNo, currency, direction, amount);
        // TODO PhaseN：调用 FinanceService.createApFromBill / cancelApFromBill(...)
    }
}
