// ====================================================================
// 列宽持久化 Composable（2026-09-22 新增，`useColumnSetting` 与 `useColumnConfig` 共用同一实现）
//
// 背景（用户报障）：
//   1) Element Plus 只有 el-table 带 border 时才放开列宽拖拽 —— 源码
//      `table-header/event-helper.mjs` 的 handleMouseMove 第一行就是 `if (!props.border) return`，
//      不带 border 的表格鼠标移到表头边界连 col-resize 光标都不出。
//   2) 拖完的宽度不做任何持久化 —— 刷新即还原。
//
// 本模块提供两条链路（两个 composable 都转发到这里，避免两处各写一份）：
//   ① 保存：表格容器统一挂 `@header-dragend="onHeaderDragend"` → 写入 columnConfig store 的
//      `columns[prop].width`（与 visible/fixed/order 同一条记录，互不干扰）
//   ② 恢复：挂载/列配置变化后调 `applySavedWidths(tableRef)`，把已保存宽度写回 el-table 自己的列对象
//      并 `doLayout()` —— 与 Element Plus 内部拖拽改的是同一批列对象，
//      只用它 expose 出来的 `columns` / `doLayout`，不碰私有 store.states。
//
// 零回归保证：**没有宽度记忆的列一律不动**，仍按列定义里的 width / min-width 自适应。
// ====================================================================
import { unref } from 'vue'
import { useColumnConfigStore } from '@/stores/columnConfig'

export function useColWidth(pageKey) {
  const store = useColumnConfigStore()

  /** 该列已保存的宽度；无记忆返回 null */
  function getColWidth(prop) {
    if (!pageKey || !prop) return null
    const colCfg = store.config[pageKey]?.columns?.[prop]
    return colCfg && typeof colCfg.width === 'number' && colCfg.width > 0 ? colCfg.width : null
  }

  /** el-table 的 @header-dragend 处理器：(newWidth, oldWidth, column) */
  function onHeaderDragend(newWidth, oldWidth, column) {
    const prop = column && column.property
    if (!prop || !pageKey) return
    const w = Math.round(Number(newWidth) || 0)
    if (w > 0) store.setColumnWidth(pageKey, prop, w)
  }

  /** 把已保存的列宽应用到表格（挂载后 / 列配置变化后调用） */
  function applySavedWidths(tableRef) {
    const table = unref(tableRef)
    if (!table || !pageKey) return
    // el-table 通过 expose 暴露 columns（Vue 的 proxyRefs 会解包 computed）
    const cols = table.columns
    if (!cols || !cols.length) return
    let changed = false
    const walk = (arr) => {
      (arr || []).forEach((c) => {
        if (!c) return
        if (c.children && c.children.length) { walk(c.children); return }
        const w = getColWidth(c.property)
        if (w && c.width !== w) {
          c.width = w
          c.realWidth = w
          changed = true
        }
      })
    }
    walk(cols)
    if (changed && typeof table.doLayout === 'function') table.doLayout()
  }

  /** 清除本页列宽记忆，并把表格恢复到列定义默认宽度 */
  function resetColWidths(tableRef) {
    if (pageKey) store.resetColumnWidths(pageKey)
    const table = unref(tableRef)
    if (!table) return
    const walk = (arr) => {
      (arr || []).forEach((c) => {
        if (!c) return
        if (c.children && c.children.length) { walk(c.children); return }
        c.width = undefined
        c.realWidth = 0
      })
    }
    walk(table.columns)
    if (typeof table.doLayout === 'function') table.doLayout()
  }

  return { getColWidth, onHeaderDragend, applySavedWidths, resetColWidths }
}
