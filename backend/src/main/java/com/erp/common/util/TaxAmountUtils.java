package com.erp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单据明细税额统一算法（2026-10-18 立，唯一口径）。
 *
 * <p>背景：含税价单据原先按「折后不含税金额 × 税率」单独四舍五入算税额，
 * 再与不含税金额相加得出价税合计 —— 两条舍入路径必然出现 1 分尾差
 * （实测：含税 4.00×140=560.00 → 不含税 495.58 + 税 64.43 = 560.01）。
 *
 * <p>正确口径：
 * <ul>
 *   <li>不含税价：税额 = 折后金额 × 税率（原逻辑不变）</li>
 *   <li>含税价：税额 = 折后含税合计 − 折后不含税金额（倒算，保证价税合计正好等于含税单价×数量）</li>
 *   <li>不计税：税额恒 0</li>
 * </ul>
 *
 * <p>所有单据（销售/采购/库存/生产/委外）的明细税额必须调用本类，禁止各处再写一份。
 */
public final class TaxAmountUtils {

    /** 金额小数位（与各单据 SCALE 对齐） */
    public static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TaxAmountUtils() {
    }

    /**
     * 计算明细税额。
     *
     * @param amount       折后不含税金额（已按 2 位落库口径）
     * @param unitPrice    明细单价（含税价口径时=含税单价）
     * @param quantity     数量
     * @param taxRate      税率（百分数，如 13 表示 13%）
     * @param discountRate 折扣率（百分数，如 5 表示 5%）
     * @param inclusive    是否含税价
     * @param noTax        是否不计税
     * @return 税额（2 位小数）
     */
    public static BigDecimal calcTaxAmount(BigDecimal amount,
                                           BigDecimal unitPrice,
                                           BigDecimal quantity,
                                           BigDecimal taxRate,
                                           BigDecimal discountRate,
                                           boolean inclusive,
                                           boolean noTax) {
        if (noTax) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal amt = nvl(amount).setScale(SCALE, RoundingMode.HALF_UP);
        if (!inclusive) {
            return amt.multiply(nvl(taxRate)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        }
        // 含税价：先还原「折后含税合计」，税额 = 含税合计 − 不含税金额
        BigDecimal gross = nvl(unitPrice).multiply(nvl(quantity)).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal discountedGross = gross
                .multiply(HUNDRED.subtract(nvl(discountRate)))
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        return discountedGross.subtract(amt).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
