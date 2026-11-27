// ====================================================================
// 列设置 Composable - 封装列配置的读取、过滤、应用顺序逻辑
// 用法:
//   const { visibleColumns, allColumns, openSetting, resetConfig } = useColumnConfig('SaleOrderList', columns)
//   <el-table-column v-for="col in visibleColumns" :key="col.prop" ... />
// ====================================================================
import { computed, ref } from 'vue'
import { useColumnConfigStore } from '@/stores/columnConfig'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColWidth } from './useColWidth'

/**
 * @param {string} pageKey 页面唯一标识，如 'SaleOrderList'、'MaterialList'
 * @param {import('vue').Ref|array} originalColumns 原始列定义数组
 *   每项结构: { prop, label, width, minWidth, fixed, slot, formatter, noLink, type, sortable, align, ... }
 *   type='selection'|'index'|'action' 的特殊列会被标记为不可配置
 * @returns {{
 *   visibleColumns: import('vue').ComputedRef<Array>,
 *   allColumns: import('vue').ComputedRef<Array>,
 *   columnSettingVisible: import('vue').Ref<boolean>,
 *   openSetting: Function,
 *   resetConfig: Function
 * }}
 */
export function useColumnConfig(pageKey, originalColumns) {
  const store = useColumnConfigStore()
  const columnSettingVisible = ref(false)

  // 标准化列定义：补全 isConfigurable / fixed / order
  const allColumns = computed(() => {
    const cols = Array.isArray(originalColumns) ? originalColumns : (originalColumns.value || [])
    const userCfg = store.getPageConfig(pageKey)
    return cols.map((col, idx) => {
      // 特殊列不可配置（选择列/序号列/操作列）
      const isSpecial = col.type === 'selection' || col.type === 'index' || col.type === 'action' || col.prop === 'action'
      const u = userCfg[col.prop] || {}
      return {
        ...col,
        // 用户配置覆盖默认值
        visible: u.visible !== undefined ? u.visible : true,
        fixed: u.fixed !== undefined ? u.fixed : (col.fixed ?? false),
        order: u.order !== undefined ? u.order : idx,
        isConfigurable: !isSpecial
      }
    }).sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
  })

  // 仅可见列（用于 v-for 渲染）
  const visibleColumns = computed(() => allColumns.value.filter(c => c.visible))

  function openSetting() {
    columnSettingVisible.value = true
  }

  function resetConfig() {
    store.resetPage(pageKey)
  }

  // 列宽持久化（2026-09-22 新增）：与 useColumnSetting 共用同一份实现（同一个 store）
  const { getColWidth, onHeaderDragend, applySavedWidths, resetColWidths } = useColWidth(pageKey)

  return {
    visibleColumns,
    allColumns,
    columnSettingVisible,
    openSetting,
    resetConfig,
    // 列宽
    getColWidth,
    onHeaderDragend,
    applySavedWidths,
    resetColWidths,
    // 暴露 store 引用，便于 Drawer 组件直接使用
    store,
    pageKey
  }
}

export { ColumnSettingDrawer }
