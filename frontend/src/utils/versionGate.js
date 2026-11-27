/**
 * 版本/权限可见性工具（2026-10-10 版本隔离收口）
 *
 * 背景：后端登录时已按「租户版本 + 角色权限」下发可见菜单（menuRoutePaths）。
 * 但页面里的一些"跨模块跳转"入口（下推目标、看板卡片、来源去向单号）是写死的，
 * 不会自动跟着版本走 → 标准版/CRM版页面上会冒出旗舰版功能的入口（点了进 403）。
 *
 * 口径：**目标路由所属模块不在当前账号可见菜单里 → 该入口不渲染 / 降级为纯文本**。
 * 判定用一级路径前缀（如 /production/workorders → /production），与左侧菜单渲染同口径，
 * 同时覆盖版本与角色两道过滤。
 *
 * 说明：无权限信息（未登录/旧缓存为空）时一律放行，避免误伤。
 */
import { useUserStore } from '@/stores/user'

/** 当前账号可见菜单路径（响应式取用） */
export function useVisibleMenuPaths () {
  const store = useUserStore()
  return () => (store.menuRoutePaths || [])
}

/** 目标路由是否对当前账号可见；paths 为空视为放行 */
export function isRouteVisible (path, paths) {
  if (!path) return true
  const list = paths || []
  if (!Array.isArray(list) || list.length === 0) return true
  if (list.includes(path)) return true
  const first = '/' + String(path).split('/').filter(Boolean)[0]
  return list.includes(first)
}

/** 便捷版：直接按当前登录账号判断（组件内调用，无需自行取 store） */
export function canSeeRoute (path) {
  const store = useUserStore()
  return isRouteVisible(path, store.menuRoutePaths || [])
}
