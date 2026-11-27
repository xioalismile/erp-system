/**
 * 单据明细税额统一算法（2026-10-18 立，唯一口径；与后端 TaxAmountUtils 同构）。
 *
 * 背景：含税价单据原先按「折后不含税金额 × 税率」单独四舍五入算税额，
 * 再与不含税金额相加得出价税合计 —— 两条舍入路径必然出现 1 分尾差
 * （实测：含税 4.00×140=560.00 → 不含税 495.58 + 税 64.43 = 560.01）。
 *
 * 正确口径：
 * - 不含税价：税额 = 折后金额 × 税率（原逻辑不变）
 * - 含税价：税额 = 折后含税合计 − 折后不含税金额（倒算，保证价税合计正好等于含税单价×数量）
 * - 不计税：税额恒 0
 *
 * 所有单据明细税额必须调用本方法，禁止各页面再写一份。
 */
export function calcTaxAmount ({ amount, unitPrice, quantity, taxRate, discountRate = 0, inclusive = false, noTax = false }) {
  const amt = Number(amount || 0)
  if (noTax) return 0
  const rate = Number(taxRate || 0)
  if (!inclusive) return Number((amt * rate / 100).toFixed(2))
  const gross = Number((Number(unitPrice || 0) * Number(quantity || 0)).toFixed(2))
  const discountedGross = Number((gross * (100 - Number(discountRate || 0)) / 100).toFixed(2))
  return Number((discountedGross - amt).toFixed(2))
}

/** 表头级合计税额（无明细行单价时）：含税价用「含税合计 − 不含税金额」倒算 */
export function calcTaxAmountByGross ({ amount, gross, taxRate = 0, inclusive = false, noTax = false }) {
  const amt = Number(amount || 0)
  if (noTax) return 0
  if (!inclusive) return Number((amt * Number(taxRate || 0) / 100).toFixed(2))
  return Number((Number(gross || 0) - amt).toFixed(2))
}
