import { fetchPrice } from '@/api/priceStrategy'

/**
 * 单据取价（2026-09-20 价格策略修复）
 * ------------------------------------------------------------------
 * 背景：价格策略/取价优先级配置早已存在，但后台从未被调用（"录了不生效"）。
 * 本函数把「按取价优先级取价」下沉为一个共用调用，供各单据编辑页在
 * 「选物料 / 改数量」时取价回填单价使用。
 *
 * 取价顺序由设置中心决定：
 *   销售（side='sale'）    ：价格策略 → 价格等级 → 物料默认售价
 *   采购（side='purchase'）：价格策略 → 物料采购价
 *
 * 约定：
 *   1) 失败不阻断录入 —— 任何异常/取不到价都返回 null，由调用方保留原价；
 *   2) 只做"带价"，不做校验 —— 保存仍不校验（既定口径），严格校验在提交环节由后端执行。
 *
 * @param {Object} opts
 * @param {'sale'|'purchase'} [opts.side='sale']  销售/采购
 * @param {number|string|null} [opts.partnerId]   销售=客户ID；采购=供应商ID
 * @param {number|string|null} [opts.materialId]  物料ID（为空直接返回 null）
 * @param {number|string|null} [opts.quantity]    数量（策略数量区间匹配用）
 * @param {string|null} [opts.taxType]            计税方式（'exclusive' 等，可空）
 * @returns {Promise<{price:number, source:string, sourceLabel:string}|null>}
 */
export async function resolveUnitPrice(opts = {}) {
  const { side = 'sale', partnerId = null, materialId = null, quantity = null, taxType = null } = opts
  if (!materialId) return null
  try {
    const res = await fetchPrice({ side, partnerId, materialId, quantity, taxType })
    if (!res || res.price === null || res.price === undefined) return null
    const price = Number(res.price)
    if (!Number.isFinite(price)) return null
    return { price, source: res.source || 'none', sourceLabel: res.sourceLabel || '' }
  } catch (e) {
    // 取价失败不阻断录入（价格会在提交环节由后端按策略校验）
    return null
  }
}

/** 判断某个取价来源是否来自「价格策略」档（用于需要区分时） */
export function isStrategySource(result) {
  return !!(result && result.source === 'strategy')
}

/**
 * 选物料后"先填物料默认价、再异步用取价优先级结果覆盖"（推荐用法）
 * ------------------------------------------------------------------
 * 用法（替换原来的一行赋值即可，不改变页面同步流程）：
 *   applyFetchedUnitPrice(row, { side:'sale', partnerId: form.customerId, materialId: m.materialId, quantity: row.quantity, taxType: row.taxCalcMethod }, m.salePrice, onApplied)
 *
 * 行为约定：
 *   1) 立即写入 baseUnitPrice / unitPrice / _autoUnitPrice = 物料档案价（保证不出现空白/0 闪烁）；
 *   2) 异步取价成功后，仅当单价仍等于 _autoUnitPrice（用户未手改）才覆盖，并刷新 _autoUnitPrice；
 *   3) 取价失败或取不到 → 保留物料档案价，绝不阻断录入。
 *
 * @param {Object} row 明细行（响应式对象）
 * @param {Object} opts resolveUnitPrice 的入参
 * @param {number|string|null} fallbackPrice 物料档案价（销售=售价，采购=采购价）
 * @param {(row:Object, priced:{price:number,source:string,sourceLabel:string})=>void} [onApplied] 覆盖成功后的回调（如重算行金额）
 */
export function applyFetchedUnitPrice(row, opts = {}, fallbackPrice = null, onApplied = null) {
  if (!row) return
  const fb = Number(fallbackPrice || 0)
  row.baseUnitPrice = fb
  row.unitPrice = fb
  row._autoUnitPrice = fb
  resolveUnitPrice(opts).then((priced) => {
    if (!priced) return
    // 用户在请求返回前手改过单价 → 不覆盖
    if (row._autoUnitPrice !== undefined && Number(row.unitPrice) !== Number(row._autoUnitPrice)) return
    row.unitPrice = priced.price
    row._autoUnitPrice = priced.price
    if (typeof onApplied === 'function') onApplied(row, priced)
  }).catch(() => { /* 取价失败保留物料档案价 */ })
}
