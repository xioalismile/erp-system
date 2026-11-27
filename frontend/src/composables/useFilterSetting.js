// ====================================================================
// 筛选设置 Composable（2026-09-22 新增；2026-10-14 存储升级为按「登录人+公司」隔离）
//
// 用途：让页面查询区的筛选条件由用户自选（勾选显隐 + 拖拽排序 + 本地记忆），
//       不勾选的条件不渲染，避免"筛选区堆一堆用不上的框"。
//
// 与列设置的关系：完全独立，不共用 stores/columnConfig（那套被全站 26+ 页面在用，
// 不动它是为了零回归）。存储底座与列设置同源（utils/userScopedStorage），
// 真实 key = `filterConfig::v1::u<用户>@t<公司>` —— 换人 / 换公司互不影响。
// 口径对齐 2026-09-23「方案A」：老的无作用域数据**不迁移**（迁移 = 把上一个人
// 的设置送给新用户），升级后每人第一次回到默认值，各自重配一次。
//
// 用法：
//   const { filterSettingVisible, openFilterSetting, visibleFilters, configVersion } =
//       useFilterSetting('SaleOrderDetailReport', () => filterDefs.value)
//   <FilterSettingPanel v-model="filterSettingVisible" page-key="..." :filters="filterDefs" />
//
// 约定：
//   1. 定义里 defaultChecked=true 的条件，用户从未保存过配置时默认勾选；
//   2. 用户保存过配置后，定义里新增的条件按 defaultChecked 补齐（老配置不会"吃掉"新条件）；
//   3. 一律不缓存空值：每次都现读，避免跨用户命中他人缓存。
// ====================================================================
import { ref, computed, unref } from 'vue'
import { scopedGetJSON, scopedSetJSON } from '@/utils/userScopedStorage'

const STORAGE_BASE = 'filterConfig'

function loadAll() {
  return scopedGetJSON(STORAGE_BASE, {}) || {}
}

function persist(all) {
  scopedSetJSON(STORAGE_BASE, all || {})
}

/** 该页面的用户配置（无则 null） */
export function getFilterPageConfig(pageKey) {
  if (!pageKey) return null
  const all = loadAll()
  return all[pageKey] || null
}

/** 写该页面的用户配置 */
export function setFilterPageConfig(pageKey, cfg) {
  if (!pageKey) return
  const all = loadAll()
  if (cfg) all[pageKey] = cfg
  else delete all[pageKey]
  persist(all)
}

/**
 * 计算"当前应渲染的筛选条件"（按用户顺序，只含勾选项）
 * @param {string} pageKey
 * @param {Array|Function|import('vue').Ref} defs 筛选条件定义（数组 / 取值函数 / ref 均可）；
 *                  元素 = { key, label, type, defaultChecked }
 */
export function resolveVisibleFilters(pageKey, defs) {
  // 兼容三种传法：数组 / ref(computed) / 取值函数（useFilterSetting 文档示例用的是函数，必须真实调用）
  const list = typeof defs === 'function' ? (defs() || []) : (unref(defs) || [])
  if (!list.length) return []
  const cfg = getFilterPageConfig(pageKey)
  if (!cfg || !Array.isArray(cfg.order)) {
    return list.filter(f => f.defaultChecked !== false)
  }
  const byKey = new Map(list.map(f => [f.key, f]))
  const out = []
  for (const k of cfg.order) {
    const f = byKey.get(k)
    if (f && cfg.checked?.[k] !== false) out.push(f)
  }
  // 定义里新增、但用户配置里还没有的条件：按 defaultChecked 补齐
  for (const f of list) {
    if (!cfg.order.includes(f.key) && f.defaultChecked !== false) out.push(f)
  }
  return out
}

export function useFilterSetting(pageKey, filters) {
  // 版本号：保存/重置后自增，触发依赖它的 computed 重新计算
  const version = ref(0)
  const filterSettingVisible = ref(false)

  function openFilterSetting() {
    filterSettingVisible.value = true
  }

  const configVersion = computed(() => version.value)

  const visibleFilters = computed(() => {
    void version.value
    return resolveVisibleFilters(pageKey, unref(filters))
  })

  /** 保存（由面板调用）：{ order: [...], checked: {...} } */
  function saveFilterConfig(cfg) {
    setFilterPageConfig(pageKey, cfg)
    version.value++
  }

  /** 恢复默认（由面板调用） */
  function resetFilterConfig() {
    setFilterPageConfig(pageKey, null)
    version.value++
  }

  return {
    filterSettingVisible,
    openFilterSetting,
    visibleFilters,
    configVersion,
    saveFilterConfig,
    resetFilterConfig
  }
}
