import { nextTick, onBeforeUnmount, onMounted, unref } from 'vue'
import { getFieldMetaList } from '@/api/system/fieldPermission'
import { useFieldPermission } from '@/composables/useFieldPermission'

/**
 * 「角色字段权限 = 必填」的星号标注（**机制层，全站自动生效**）
 *
 * 【为什么不逐页接入】
 * 历史上这套星号靠"每个编辑页自己 import 一行 useFieldRequiredMark(...)"，
 * 结果必然是"单页跑通、换页不生效"，再逐页缝补 —— 一类问题反复出现。
 * 现在改成：**路由级自动标注**（见 MainLayout 挂载的全局钩子）+ 页面键自动推导
 * （resolveFieldPageKey），页面本身不需要任何接入代码；新增页面只要路由能推导出
 * 目标表名，就自动生效；推导不出来会被发布闸门拦下，不再靠肉眼。
 *
 * 【匹配顺序】（宁可漏标，绝不误标）
 *   ① `el-form-item` 的 prop 与字段名**精确相等** → 直接用（最可靠）
 *   ② 标签（去掉星号/冒号）与后端字段名**完全相等**
 *   ③ 标签与字段名**归一化后**相等（去掉"编号/号码/号/ID/名称/日期/时间/备注"等尾巴，
 *      修掉「订单编号 ↔ 订单号」「客户 ↔ 客户ID」这类不一致）；同名多条时跳过
 *   未命中的必填字段会记入 `window.__fieldRequiredMisses`，供发布闸门判定"配了必填但页面上标不出来"。
 */

// 进程内缓存：pageKey -> { requiredKeys:Set<string>, labelToKey:Object, coreToKey:Object }
const indexCache = new Map()

/** 清缓存（角色权限变更、切换用户后需要重新标注时调用） */
export function clearFieldRequiredMarkCache(pageKey) {
  if (pageKey) indexCache.delete(pageKey)
  else indexCache.clear()
}

function normLabel(s) {
  return String(s == null ? '' : s)
    .replace(/\*/g, '')
    .replace(/[:：]\s*$/, '')
    .trim()
}

/** 一级核心词：只把"编号/号码/号"看作同一族（订单编号 ↔ 订单号），保留 ID/名称 的区别 */
function coreLabelA(s) {
  return normLabel(s).replace(/(编号|号码|号)$/, '')
}

/** 二级核心词：再去掉 ID/名称/日期/时间/备注（付款条件 ↔ 付款条件ID、客户 ↔ 客户名称） */
function coreLabel(s) {
  return coreLabelA(s).replace(/(ID|Id|id|名称|日期|时间|备注)$/, '')
}

async function loadIndex(pageKey) {
  const cached = indexCache.get(pageKey)
  if (cached) return cached

  // 1) 角色必填字段集合（与提交校验同口径：required=1 且 visible!=0）
  //    复用 useFieldPermission 的模块级缓存，避免同页重复请求
  const requiredKeys = new Set()
  let permMap = {}
  let permErr = null
  try {
    const { loadFieldPermission } = useFieldPermission()
    permMap = (await loadFieldPermission(pageKey)) || {}
    // 注意：权限表是 "字段名 → {visible,editable,required}" 的结构，**值里不带字段名**，
    // 字段名必须从 map 的 key 取（历史 bug：误取 p.fieldKey 恒为空 → 必填集合永远为空 → 配了必填也没有星号）
    Object.entries(permMap).forEach(([key, p]) => {
      if (p && Number(p.required) === 1 && Number(p.visible) !== 0) {
        requiredKeys.add(p.fieldKey || key)
      }
    })
  } catch (e) {
    // 取不到权限就不标注（与后端"取不到就不叠加"一致），但把原因留痕
    permErr = String(e && (e.stack || e.message || e))
  }

  // 2) 字段名索引（后端字段元数据：fieldKey + fieldName）
  const labelToKey = {}
  const coreAToKey = {}
  const coreToKey = {}
  const put = (map, label, key) => {
    const t = label
    if (!key || !t) return
    map[t] = map[t] ? '__dup__' : key
  }
  let metaErr = null
  let metas = []
  try {
    metas = await getFieldMetaList(pageKey)
    ;(Array.isArray(metas) ? metas : []).forEach(m => {
      if (!m || !m.fieldKey) return
      put(labelToKey, normLabel(m.fieldName || m.fieldKey), m.fieldKey)
      put(coreAToKey, coreLabelA(m.fieldName || m.fieldKey), m.fieldKey)
      put(coreToKey, coreLabel(m.fieldName || m.fieldKey), m.fieldKey)
    })
  } catch (e) {
    metaErr = String(e && (e.stack || e.message || e))
  }

  // 3) 字段名归一：权限表存的是**蛇形**（order_date / customer_id），字段元数据与页面
  //    el-form-item 的 prop 是**驼峰**（orderDate / customerId）。不归一会出现两个后果
  //    （2026-11-11 实锤：销售订单复制后提交被误拦，提示 order_date/customer_id 不能为空）：
  //      ① 星号匹配不上（按驼峰 prop / 中文标签都命中不了蛇形键）
  //      ② 提交校验按 model['order_date'] 取值恒为空 → 明明填了客户和日期也报"未填写"
  const toCamel = (k) => String(k).replace(/_([a-z])/g, (_, c) => c.toUpperCase())
  const normalizedRequired = new Set()
  requiredKeys.forEach(k => {
    normalizedRequired.add(k)
    normalizedRequired.add(toCamel(k))
  })

  const idx = { requiredKeys: normalizedRequired, labelToKey, coreAToKey, coreToKey }
  indexCache.set(pageKey, idx)
  if (typeof window !== 'undefined' && (permErr || metaErr)) {
    // 取权限 / 取字段名出错时留痕（排查"配了必填却没有星号"用，不再静默失败）
    window.__fieldRequiredError = (permErr ? 'permission: ' + permErr : '') + (metaErr ? ' | meta: ' + metaErr : '')
  }
  return idx
}

/** DOM 里跳过这些区域（弹窗/抽屉里的表单不是本页主表单） */
const SKIP_SELECTOR = '.el-dialog, .el-drawer, .el-overlay, .el-message-box, .el-select-dropdown'

/**
 * 单个 el-form-item → 命中的字段键 + 页面标签（匹配顺序 ①②③④；未命中时 key 为空串）。
 * 抽成公用函数的原因：星号标注与「提交缺项清单显示页面叫法」必须共用同一套匹配，
 * 否则会出现"页面上带星号的叫客户、提交提示里叫客户ID"这类口径漂移。
 */
function matchItemKey(item, idx) {
  const { requiredKeys, labelToKey, coreAToKey, coreToKey } = idx
  const labelEl = item.querySelector('.el-form-item__label')
  const labelText = labelEl ? normLabel(labelEl.textContent) : ''
  // ① 优先用 prop 精确匹配（prop 就是字段名）
  const propKey = item.getAttribute('prop') || item.getAttribute('data-prop') || ''
  let key = propKey && requiredKeys.has(propKey) ? propKey : ''
  // ② 再按标签与字段名完全相等
  if (!key && labelText) {
    const byLabel = labelToKey[labelText]
    if (byLabel && byLabel !== '__dup__' && requiredKeys.has(byLabel)) key = byLabel
  }
  // ③ 一级同义：只把"编号/号码/号"当同族（订单编号 ↔ 订单号）
  if (!key && labelText) {
    const byA = coreAToKey[coreLabelA(labelText)]
    if (byA && byA !== '__dup__' && requiredKeys.has(byA)) key = byA
  }
  // ④ 二级同义：再去掉 ID/名称/日期 等（付款条件 ↔ 付款条件ID、客户 ↔ 客户名称）
  if (!key && labelText) {
    const byCore = coreToKey[coreLabel(labelText)]
    if (byCore && byCore !== '__dup__' && requiredKeys.has(byCore)) key = byCore
  }
  return { key, labelText }
}

/**
 * 一次性标注。
 * @param {string} pageKey 与字段权限 pageKey 同口径（目标表名，如 'sale_order'）
 * @param {Element|Document} [rootEl] 标注范围；不传则用 document
 * @returns {Promise<number>} 本次新增星号数
 */
export async function applyFieldRequiredMark(pageKey, rootEl) {
  if (!pageKey) return 0
  const root = rootEl || (typeof document !== 'undefined' ? document : null)
  if (!root || !root.querySelectorAll) return 0

  let marked = 0
  try {
    const { requiredKeys, labelToKey, coreAToKey, coreToKey } = await loadIndex(pageKey)
    if (!requiredKeys.size) return 0

    await nextTick()
    const hitKeys = new Set()
    const idx = { requiredKeys, labelToKey, coreAToKey, coreToKey }
    root.querySelectorAll('.el-form-item').forEach(item => {
      if (item.closest(SKIP_SELECTOR)) return            // 弹窗/抽屉里的表单不标
      const { key } = matchItemKey(item, idx)
      if (!key) return
      hitKeys.add(key)
      if (item.classList.contains('is-required')) return // 已有星号（静态必填）不动
      item.classList.add('is-required')
      marked++
    })

    // 记录"配了必填但页面上找不到表单项"的字段（发布闸门据此判定，不再靠人眼抽查）
    if (typeof window !== 'undefined') {
      const miss = [...requiredKeys].filter(k => !hitKeys.has(k))
      window.__fieldRequiredMisses = window.__fieldRequiredMisses || {}
      window.__fieldRequiredMisses[pageKey] = miss
    }
  } catch (e) {
    // 不影响页面，但把原因留痕（发布闸门/自检脚本可读），避免"静默失败、没人知道为什么没星号"
    if (typeof window !== 'undefined') window.__fieldRequiredError = String(e && (e.stack || e.message || e))
  }
  return marked
}

/**
 * 解析「字段名 → 页面上的叫法」映射（与星号标注共用同一套 ①②③④ 匹配，保证口径一致）。
 *
 * 用途：提交时的缺项清单要显示**页面叫法**（客户、付款条件、交货日期），
 * 而不是字段元数据叫法（客户ID、付款条件ID）—— 用户只认页面上的名字。
 * 未匹配到页面表单项的字段（页面上没有输入口的）不在此映射中，由调用方回退元数据叫法。
 *
 * @returns {Promise<Object>} { [fieldKey]: 页面标签 }
 */
export async function resolvePageFieldLabels(pageKey, rootEl) {
  const map = {}
  if (!pageKey) return map
  const root = rootEl || (typeof document !== 'undefined' ? document : null)
  if (!root || !root.querySelectorAll) return map
  try {
    const idx = await loadIndex(pageKey)
    if (!idx.requiredKeys.size) return map
    await nextTick()
    root.querySelectorAll('.el-form-item').forEach(item => {
      if (item.closest(SKIP_SELECTOR)) return
      const { key, labelText } = matchItemKey(item, idx)
      if (key && labelText && !map[key]) map[key] = labelText
    })
  } catch (e) {
    // 取不到页面叫法就回退元数据叫法，不阻断提交校验
  }
  return map
}

/**
 * 页面接入用（机制层已自动调用，保留仅供个别特殊页面手动补标；幂等）：
 *   useFieldRequiredMark('sale_order', '.sale-order-edit')
 */
export function useFieldRequiredMark(pageKey, root) {
  if (!pageKey) return
  let observer = null
  let timer = null

  const resolveRoot = () => {
    const r = unref(root)
    if (!r) return typeof document !== 'undefined' ? document.body : null
    if (typeof r === 'string') {
      return (typeof document !== 'undefined' && document.querySelector(r)) || null
    }
    return r
  }

  const run = () => {
    const el = resolveRoot()
    if (el) applyFieldRequiredMark(pageKey, el)
  }

  const scheduleRun = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(run, 150)
  }

  onMounted(async () => {
    await nextTick()
    run()
    scheduleRun()
    const el = resolveRoot()
    if (el && typeof MutationObserver !== 'undefined') {
      observer = new MutationObserver(scheduleRun)
      observer.observe(el, { childList: true, subtree: true })
    }
  })

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
    if (observer) { observer.disconnect(); observer = null }
  })
}

/* 路由 → 页面键的推导逻辑抽到纯模块 utils/fieldPageKey.js：
   运行时（此处调用）与发布闸门（Node 里跑）**共用同一份**，避免"闸门一套、运行时另一套"。 */
export { PATH_PAGE_KEY_EXCEPTIONS, resolveFieldPageKey } from './fieldPageKey'
