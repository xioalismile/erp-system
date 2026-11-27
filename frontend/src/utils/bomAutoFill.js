/**
 * 明细「选物料自动带出BOM」的并发防护（2026-10-26）
 *
 * 背景：带出 BOM 是异步请求。选完物料到结果返回之间，用户可能再次改选物料、或再次从物料库带入，
 * 两次请求都会通过「明细为空才带出」的前置判断，返回后各自追加明细 ——
 * 结果是两套 BOM 叠加，或同一套 BOM 重复两遍。
 * 组装单/拆解单生效时按 BOM 明细逐行扣库存，重复行 = 重复扣料（库存与成本直接错）。
 *
 * 用法：调用前 begin() 取令牌 → await 取数 → isLatest(令牌) 为真才落地。
 */
export function createAutoFillToken() {
  let seq = 0
  return {
    begin() {
      return ++seq
    },
    isLatest(token) {
      return token === seq
    }
  }
}

/**
 * 明细里是否已有「已选物料」的行。
 * 带出前后各判一次：只在空表时带出，绝不覆盖、绝不重复追加。
 */
export function hasFilledRows(rows, key) {
  if (!Array.isArray(rows)) return false
  return rows.some(r => r && r[key] != null)
}
