import { onActivated, onDeactivated } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 全局页面刷新注册表：route.fullPath -> 该页面的数据加载函数
 * 供 TagsView「刷新当前页」按钮做软刷新（只重拉数据，不销毁组件，保留筛选条件和页码）
 */
const refreshRegistry = new Map()

/**
 * 列表页接入自动刷新机制（一行接入）：
 * 1. 切回标签页时自动重新加载最新数据（首次激活跳过，因为 onMounted 刚加载过）
 * 2. 同时注册到全局注册表，供标签栏「刷新当前页」按钮调用
 *
 * 用法：在列表页 script setup 中，紧挨着 onMounted 调用同样参数即可：
 *   onMounted(fetchData)
 *   useAutoRefresh(fetchData)   // ← 加这一行
 *
 * 注意：编辑页禁止接入（刷新会覆盖用户未保存的草稿）。
 *
 * @param {Function} loadFn 数据加载函数（按当前筛选条件/页码重新查询）
 */
export function useAutoRefresh(loadFn) {
  if (typeof loadFn !== 'function') return
  const route = useRoute()
  refreshRegistry.set(route.fullPath, loadFn)

  // 只在"真的被切走之后再切回"时刷新；紧跟 onMounted 的那次激活（页面刚加载完）跳过，避免重复请求。
  // 历史 BUG（2026-09-17）：原来写的是"第一次激活一律跳过"，但本项目 keep-alive 首次渲染并不稳定触发
  // onActivated（页签刚打开时组件往往是直接挂载而非"激活"）→ 于是"第一次激活"恰好就是用户第一次切回来的
  // 那一刻，被当成首次误跳过 → 现象：切回标签页列表/卡片不刷新，要手动点一下才更新。
  // 改为以 onDeactivated 为准：没被切走过 = 紧跟挂载的那次（跳过）；被切走过 = 真·切回（刷新）。
  let everDeactivated = false
  onDeactivated(() => { everDeactivated = true })
  onActivated(() => {
    if (!everDeactivated) return
    try {
      Promise.resolve(loadFn()).catch(() => {})
    } catch (_) {
      // 刷新失败不打断用户操作
    }
  })
}

/**
 * 触发指定页面的软刷新（TagsView 刷新按钮调用）
 * @param {string} fullPath 当前路由完整路径（含 query）
 * @returns {boolean} true=该页面已注册并触发刷新；false=未注册（调用方自行兜底）
 */
export function triggerPageRefresh(fullPath) {
  if (!fullPath) return false
  const fn = refreshRegistry.get(fullPath)
  if (typeof fn !== 'function') return false
  try {
    Promise.resolve(fn()).catch(() => {})
    return true
  } catch (_) {
    return false
  }
}

/**
 * 按路径前缀软刷新：编辑页操作成功后通知所有"匹配到该前缀"的列表页/同列表页刷新。
 * 例如：编辑页 URL 可能是 /sale/orders/12345，列表页可能是 /sale/orders 或 /sale/orders?status=draft。
 * 传 '/sale/orders' 前缀能同时命中这两种 fullPath。
 *
 * @param {string|string[]} prefixes 需要匹配刷新的路径前缀（不带 id/不包含 query，如 '/sale/orders'）
 * @returns {number} 实际触发刷新的页面数量
 */
export function triggerListRefresh(prefixes) {
  const list = Array.isArray(prefixes) ? prefixes : [prefixes]
  let count = 0
  for (const [fullPath, fn] of refreshRegistry.entries()) {
    if (typeof fn !== 'function') continue
    const hit = list.some(p => p && (fullPath === p || fullPath.startsWith(p + '/') || fullPath.startsWith(p + '?') || fullPath.startsWith(p + '&')))
    if (!hit) continue
    try {
      Promise.resolve(fn()).catch(() => {})
      count++
    } catch (_) {
      // 静默：某一页刷新失败不影响其它页
    }
  }
  return count
}

/** 返回当前注册的所有 fullPath（诊断用） */
export function getRegisteredRefreshPaths() {
  return [...refreshRegistry.keys()]
}
