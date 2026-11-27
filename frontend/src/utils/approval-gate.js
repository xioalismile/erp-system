/**
 * 全局审批开关 + 单据级审批开关 的统一缓存（单例 Promise 缓存 + 内存值缓存）。
 *
 * 用途：BillActions / BillRowActions / useBillActions 需要读 sys_config：
 *        - approval.enabled (全局总开关)
 *        - approval.bill.{BILL_TYPE}.enabled (单据级开关)
 *       列表页每行一个 BillRowActions，如果不加缓存会在首屏刷出几百次相同请求。
 *
 * 使用：
 *   import { isApprovalGloballyEnabled, isBillApprovalEnabled } from '@/utils/approval-gate'
 *   // 组件里：await isApprovalGloballyEnabled()                 -> true/false
 *   //         await isBillApprovalEnabled('SALE_ORDER')         -> true=该单据需审批
 *   // 没网/接口挂时默认返回 true（走旧逻辑，避免 UI 进一步破坏）
 *
 * 规则（与后端 ApprovalGateHelper 一致）：
 *   - 全局关 → 所有单据免审批
 *   - 单据级关（或未配置）→ 该单据免审批
 *   - 全局开 且 单据级开 → 需审批
 */
import request from '@/utils/request'

let cachedGlobal = null // true / false / null(null=还没问过)
let pendingGlobalPromise = null
// 按 billType 缓存，键 = billType 大写
const cachedBillMap = Object.create(null) // { [billType]: true/false/null }
const pendingBillPromises = Object.create(null)

/**
 * 兼容两种返回结构（后端某天改结构也不坏）：
 *   ① request 拦截器已把 Result<T> 解包 → 拿到裸值本身（布尔）；
 *   ② 未解包的统一返回体 → { code, message, data }，取 data。
 */
function unwrapPayload (res) {
  let v = res
  if (v !== null && typeof v === 'object' && 'data' in v) v = v.data
  return v
}

/** 宽松转布尔：只认 true / 'true'，其余（false / 'false' / null / 对象）一律 false */
function toBool (v) {
  return v === true || v === 'true'
}

export async function isApprovalGloballyEnabled() {
  if (cachedGlobal !== null) return cachedGlobal
  if (pendingGlobalPromise) return pendingGlobalPromise
  pendingGlobalPromise = (async () => {
    try {
      const res = await request.get('/sys-config/approval.enabled')
      const v = res?.data?.configValue
      cachedGlobal = !(v === 'false' || v === false)
    } catch (e) {
      cachedGlobal = true
    } finally {
      pendingGlobalPromise = null
    }
    return cachedGlobal
  })()
  return pendingGlobalPromise
}

/**
 * 判断某单据类型是否需要人工审批。
 * 综合了：全局开关 + 单据级开关（任意关闭都返回 false）
 *
 * @param {string|null|undefined} billType 单据编码，如 'SALE_ORDER'；空值直接返回 false（未接入开关的单据一律免审批，与后端 AbstractBillService 语义一致）
 */
export async function isBillApprovalEnabled(billType) {
  if (!billType || typeof billType !== 'string' || !billType.trim()) return false
  const key = billType.trim().toUpperCase()
  if (cachedBillMap[key] !== undefined && cachedBillMap[key] !== null) return cachedBillMap[key]
  const globalOk = await isApprovalGloballyEnabled()
  if (!globalOk) {
    cachedBillMap[key] = false
    return false
  }
  if (pendingBillPromises[key]) return pendingBillPromises[key]
  pendingBillPromises[key] = (async () => {
    try {
      const res = await request.get(`/approval/bill-enabled/${encodeURIComponent(key)}`)
      cachedBillMap[key] = toBool(unwrapPayload(res))
    } catch (e) {
      // 接口挂掉就默认 false（保守：当开关未启用，隐藏审批按钮，让用户直接提交即生效）
      cachedBillMap[key] = false
    } finally {
      pendingBillPromises[key] = null
    }
    return cachedBillMap[key]
  })()
  return pendingBillPromises[key]
}

/** 显式清空缓存（设置中心改了开关后调用） */
export function clearApprovalGateCache(billType) {
  if (billType != null) {
    const key = String(billType).trim().toUpperCase()
    cachedBillMap[key] = null
    pendingBillPromises[key] = null
  } else {
    for (const k of Object.keys(cachedBillMap)) cachedBillMap[k] = null
    for (const k of Object.keys(pendingBillPromises)) pendingBillPromises[k] = null
    cachedGlobal = null
    pendingGlobalPromise = null
  }
}

/** 同步取缓存值（拿不到返回 null，组件用 v-if 兜底）。 */
export function getApprovalGlobalCached() {
  return cachedGlobal
}

/** 同步取单据级缓存（拿不到返回 null）。 */
export function getApprovalBillCached(billType) {
  if (!billType) return null
  const key = String(billType).trim().toUpperCase()
  const v = cachedBillMap[key]
  return v === undefined ? null : v
}
