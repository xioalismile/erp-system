/**
 * 数值精度：设置中心「系统参数 → 小数位数设置」的统一消费入口（全站唯一一份）
 *
 * 为什么必须有它：设置中心本来就有「数量/金额/单价/汇率/百分比小数位数 + 省略多余的0」这些配置项，
 * 但此前**全站没有任何一处读它** —— 各页各写一份 `:precision="4"` / `toFixed(2)`，
 * 导致用户改了设置毫无效果（用户口径：「小数点的设置功能貌似只是一个摆设」）。
 * 本组合式函数把 6 个配置键收成唯一一份，供所有明细/列表/编辑页与物料库弹窗共用。
 *
 * 读取键（sys_config）：
 *   precision.quantity 数量     precision.price 单价     precision.amount 金额
 *   precision.rate 汇率         precision.percent 百分比 precision.rounding.mode 舍入（后端用）
 *   precision.trim.zero 省略多余的0（显示用）
 *
 * 容错：缺键/非法/越界 → 回落默认值（对齐当前系统主流位数，保证"不改设置时观感不变"）
 * 登录/切公司后由 sessionCacheReset 重新拉取 sys_config，本组合式函数自动跟随（响应式）。
 */
import { computed } from 'vue'
import { useSysConfig } from './useSysConfig'

/** 配置键 → 精度类别 */
export const PRECISION_KEYS = {
  qty: 'precision.quantity',
  price: 'precision.price',
  amount: 'precision.amount',
  rate: 'precision.rate',
  percent: 'precision.percent'
}

/** 默认位数：对齐当前系统各页写死的主流位数（数量4 / 单价4 / 金额2 / 汇率6 / 百分比2） */
export const PRECISION_DEFAULTS = { qty: 4, price: 4, amount: 2, rate: 6, percent: 2 }

/** 允许范围（与后端一致） */
export const PRECISION_MIN = 0
export const PRECISION_MAX = 6

/** 字段名 → 精度类别的关键词规则（顺序敏感：先具体后宽泛） */
const PROP_RULES = [
  { key: 'rate', re: /exchange|汇率/i },
  { key: 'percent', re: /tax.*rate|discount.*rate|percent|税率|折扣率|百分比/i },
  { key: 'price', re: /price|cost|fee|单价|成本|加工费|售价|进价/i },
  { key: 'amount', re: /amount|money|金额|税额|价税/i },
  { key: 'qty', re: /quantity|qty|stock|数量|库存|换算率/i }
]

function toScale(raw, dft) {
  const n = Number(raw)
  if (!Number.isFinite(n)) return dft
  const i = Math.floor(n)
  if (i < PRECISION_MIN || i > PRECISION_MAX) return dft
  return i
}

/** 位数格式化：末位补零；trimZero 开启时去掉末尾多余的 0（"1.50" → "1.5"） */
export function formatWith(value, scale, trimZero) {
  const n = Number(value)
  const num = Number.isFinite(n) ? n : 0
  let s = num.toFixed(scale)
  if (trimZero && s.indexOf('.') >= 0) {
    s = s.replace(/0+$/, '').replace(/\.$/, '')
  }
  return s
}

// —— 模块级单例（config 本身是模块级共享 ref，这里只算一次，避免每个单元格各建一份 computed）——
const sysConfig = useSysConfig()

/** 5 类小数位（响应式：设置中心保存 / 切公司后自动跟随） */
export const precisionScales = computed(() => ({
  qty: toScale(sysConfig.config.value[PRECISION_KEYS.qty], PRECISION_DEFAULTS.qty),
  price: toScale(sysConfig.config.value[PRECISION_KEYS.price], PRECISION_DEFAULTS.price),
  amount: toScale(sysConfig.config.value[PRECISION_KEYS.amount], PRECISION_DEFAULTS.amount),
  rate: toScale(sysConfig.config.value[PRECISION_KEYS.rate], PRECISION_DEFAULTS.rate),
  percent: toScale(sysConfig.config.value[PRECISION_KEYS.percent], PRECISION_DEFAULTS.percent)
}))

/** 「省略小数点后多余的 0」开关 */
export const precisionTrimZero = computed(() => String(sysConfig.config.value['precision.trim.zero']) === 'true')

/** 按类别取位数（未知类别回落金额位） */
export function scaleFor(key) {
  const s = precisionScales.value
  return s[key] != null ? s[key] : s.amount
}

/** 按字段名推断类别（无命中返回 null） */
export function keyForProp(prop) {
  if (!prop) return null
  for (const r of PROP_RULES) {
    if (r.re.test(String(prop))) return r.key
  }
  return null
}

/**
 * 明细列/输入框的位数：显式 col.scaleKey > 字段名推断 > 旧写法 col.precision > fallback
 * @param {Object|string} col 列定义对象或字段名
 * @param {number} [fallback] 旧行为位数
 */
export function precisionFor(col, fallback) {
  const scaleKey = typeof col === 'string' ? null : (col && col.scaleKey)
  const prop = typeof col === 'string' ? col : (col && col.prop)
  const key = scaleKey || keyForProp(prop)
  if (key) return scaleFor(key)
  // 字段名不含关键词时按「列标题」推断（如字段叫 num、标题叫“数量”）：全站数量列统一走设置中心
  if (typeof col === 'object' && col && col.label) {
    const labelKey = keyForProp(col.label)
    if (labelKey) return scaleFor(labelKey)
  }
  if (typeof col === 'object' && col && col.precision != null) return col.precision
  return fallback
}

export function usePrecision() {
  // 幂等触发一次拉取（未加载时才真正发请求；不 await，避免阻塞渲染）
  try { sysConfig.loadSysConfig() } catch (e) { /* ignore */ }

  return {
    scales: precisionScales,
    trimZero: precisionTrimZero,
    scaleFor,
    keyForProp,
    precisionFor,
    formatWith,
    fmtQty: (v) => formatWith(v, scaleFor('qty'), precisionTrimZero.value),
    fmtPrice: (v) => formatWith(v, scaleFor('price'), precisionTrimZero.value),
    fmtAmount: (v) => formatWith(v, scaleFor('amount'), precisionTrimZero.value)
  }
}