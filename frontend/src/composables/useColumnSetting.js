// ====================================================================
// 列设置 Composable - 业务单据列表页/编辑页通用方案
// 适用于静态 template 列结构（带 v-if 显隐控制和插槽）
// 用法:
//   const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('SaleOrderList', columns)
//   <el-table-column prop="orderNo" v-if="isColVisible('orderNo')" :fixed="getColFixed('orderNo')" ... />
//   <el-table :data="..." :key="configVersion">  <!-- 用 key 强制 el-table 在配置变化后重新渲染 -->
//   <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'SaleOrderList'" :columns="columns" />
//
// 字段权限（可选第三个参数）：
//   useColumnSetting('SaleOrderList', columns, 'sale_order')
//   传入 billType 小写后，isColVisible 会同时校验「设置中心-字段权限」配置，
//   被角色关闭的字段（visible=0）对应列直接隐藏（后端响应层也会剔除该字段数据）。
//
// 自定义字段列（2026-10-09 新增）：
//   若 pageKey 在 config/listCustomFieldEntities.js 登记过（单据类列表页），会自动把该业务对象
//   勾了「列表显示」的自定义字段追加为列（不需要各页面改代码）。字段值由 BillListPage 批量回填到行数据。
//
// 响应式说明：
//   直接基于 Pinia store 的 reactive state 建立响应式依赖
//   isColVisible / getColFixed 函数内部访问 store 的响应式数据
//   configVersion 是一个 computed，基于 store.version，确保保存后触发重新渲染
//   由于直接访问 store.reactive state，Vue 的响应式系统会自动追踪依赖并触发更新
// ====================================================================
import { ref, computed, unref } from 'vue'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { useFieldPermission } from './useFieldPermission'
import { useColWidth } from './useColWidth'
import { listCustomColumnsRef } from '@/config/listCustomFieldEntities'
import { permKeyOfPage } from '@/config/billPagePermKeys'

export function useColumnSetting(pageKey, columns, fieldPermKey) {
  const store = useColumnConfigStore()
  const columnSettingVisible = ref(false)

  // 字段权限（可选）：页面显式传 fieldPermKey 优先；未传则按「页面 → 单据主表」登记表自动取
  // （2026-11-27 系统性改造：不再逐页手传，新页面登记后自动生效；明细列页键 ::detail 自动归一）
  const resolvedPermKey = fieldPermKey || permKeyOfPage(pageKey)
  const fieldPerm = resolvedPermKey ? useFieldPermission(resolvedPermKey) : null

  // 「列表显示」自定义字段列（单据类列表页自动追加；未登记的页面为空数组 → 与改动前完全一致）
  const customColumns = listCustomColumnsRef(pageKey)
  // 业务列 + 自定义字段列（列设置的顺序/显隐、模板 v-for 渲染都以这份为准）
  const mergedColumns = computed(() => [...(unref(columns) || []), ...customColumns.value])

  // 配置版本号：基于 store.version 的 computed，确保响应式更新
  // 保存配置时 store.version 自增 -> 此 computed 重新计算 -> 触发依赖此值的渲染更新
  const configVersion = computed(() => store.version)

  function openColumnSetting() {
    columnSettingVisible.value = true
  }

  // 判断某列是否可见
  // 注意：此函数内部访问了 store.config（reactive）和 store.version（通过 configVersion）
  // 当在模板中调用时，Vue 会自动收集这些响应式依赖
  // 强制显示列：来源单号/来源行号（业务关键信息，不允许隐藏）
  const FORCE_VISIBLE_PROPS = ['sourceBillNo', 'sourceNo', 'sourceLineNo']
  // 列定义默认隐藏集合（defaultHidden: true 的列，无用户配置时默认不显示；
  // 用于自定义字段 isListShow=0 的列——编辑态可填值但列表默认不展示，用户可在列设置里手动开启）
  const defaultHiddenProps = computed(() => {
    const cols = mergedColumns.value
    if (!cols || !cols.length) return new Set()
    return new Set(cols.filter(c => c && c.defaultHidden).map(c => c.prop))
  })
  function isColVisible(prop) {
    // 访问 configVersion 建立对 store.version 的依赖
    // 确保保存配置时（version 自增）能触发重新渲染
    void configVersion.value
    if (FORCE_VISIBLE_PROPS.includes(prop)) return true
    const pageCfg = store.config[pageKey]
    // 该列在用户配置里是否存在：不存在 = 从未保存过该列，或该列是「保存过列设置之后新增的自定义字段」
    const colCfg = pageCfg?.columns?.[prop]
    let visible
    if (colCfg) {
      visible = colCfg.visible !== false
    } else {
      // 无该列的用户配置：尊重列定义的 defaultHidden（自定义字段 isListShow=0 默认不显示）
      // 与列设置抽屉的默认勾选状态保持一致，避免「抽屉里没勾、表格里却显示」
      visible = !defaultHiddenProps.value.has(prop)
    }
    // 字段权限：被角色关闭的字段（visible=0）对应列强制隐藏
    if (visible && fieldPerm) visible = fieldPerm.isFieldVisible(prop)
    return visible
  }

  // 获取某列的固定方式
  // defaultFixed：列定义中的默认固定方式（可选）。无用户配置时回落该默认值，
  // 与列设置抽屉 initLocalColumns 的回落语义一致（u.fixed !== undefined ? u.fixed : col.fixed ?? false）
  function getColFixed(prop, defaultFixed) {
    void configVersion.value
    const pageCfg = store.config[pageKey]
    if (!pageCfg?.columns) return defaultFixed || false
    const colCfg = pageCfg.columns[prop]
    if (!colCfg) return defaultFixed || false
    if (!colCfg?.fixed || colCfg.fixed === false) return false
    return colCfg.fixed
  }

  // 获取某列的顺序
  function getColOrder(prop) {
    void configVersion.value
    const pageCfg = store.config[pageKey]
    if (!pageCfg?.columns) return null
    const colCfg = pageCfg.columns[prop]
    return colCfg?.order ?? null
  }

  // 按配置顺序排序后的列数组（用于动态渲染列）：业务列 + 自定义字段列
  // 兼容静态数组 / ref / computed 传入；无配置的列保持原索引（稳定排序）
  const sortedColumns = computed(() => {
    void store.version
    const cols = mergedColumns.value
    if (!cols || !cols.length) return []
    const pageCfg = store.config[pageKey]
    if (!pageCfg?.columns) return [...cols]

    return [...cols]
      .map((col, idx) => ({ col, order: pageCfg.columns[col.prop]?.order ?? idx + 10000 }))
      .sort((a, b) => a.order - b.order)
      .map(x => x.col)
  })

  // 列宽持久化（2026-09-22 新增）：实现下沉到 useColWidth，与 useColumnConfig 共用一份
  // （两个 composable 读写的是同一个 columnConfig store，所以共用实现不会出现口径分叉）
  const { getColWidth, onHeaderDragend, applySavedWidths, resetColumnWidths } = useColWidth(pageKey)

  return {
    columnSettingVisible,
    openColumnSetting,
    isColVisible,
    getColFixed,
    getColOrder,
    configVersion,
    sortedColumns,
    // 业务列 + 自定义字段列（供列设置抽屉展示全部可选列）
    allColumns: mergedColumns,
    // ===== 列宽持久化（2026-09-22 新增）=====
    getColWidth,
    onHeaderDragend,
    applySavedWidths,
    resetColumnWidths
  }
}
