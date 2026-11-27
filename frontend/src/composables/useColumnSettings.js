/**
 * 通用列设置 Composable
 * 用法：
 *   const { visibleColumns, columnSettingsRef } = useColumnSettings('crm-opportunity', allColumns)
 *   // 模板中：
 *   // <ColumnSettingsButton ref="columnSettingsRef" :storage-key="'crm-opportunity'" :all-columns="allColumns" @update="onColumnsUpdate" />
 *   // <el-table-column v-for="col in visibleColumns" :key="col.prop" v-bind="col" />
 */
import { ref, watch } from 'vue'

const STORAGE_PREFIX = 'erp-col-config:'

/** 默认配置：全部列显示，按原始顺序 */
function buildDefaultConfig(columns) {
  return columns.map(c => ({
    prop: c.prop,
    label: c.label,
    visible: c.visible !== false, // 默认显示，除非显式声明 visible: false
    fixed: c.fixed || false
  }))
}

/** 从 localStorage 读取配置，合并默认值（处理新增列） */
function loadConfig(storageKey, defaultColumns) {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + storageKey)
    if (!raw) return buildDefaultConfig(defaultColumns)
    const saved = JSON.parse(raw)
    // 合并：以默认列定义为基础，用 saved 覆盖 visible/fixed，新增的列默认显示
    return defaultColumns.map(def => {
      const s = saved.find(x => x.prop === def.prop)
      return {
        prop: def.prop,
        label: def.label,
        visible: s ? s.visible : (def.visible !== false),
        fixed: s ? s.fixed : (def.fixed || false)
      }
    })
  } catch (e) {
    return buildDefaultConfig(defaultColumns)
  }
}

/** 保存配置到 localStorage */
function saveConfig(storageKey, config) {
  try {
    localStorage.setItem(STORAGE_PREFIX + storageKey, JSON.stringify(config))
  } catch (e) {
    console.warn('列配置保存失败', e)
  }
}

/**
 * @param {string} storageKey 存储键，建议格式：模块-页面，如 'crm-opportunity'
 * @param {Array} allColumns 所有列定义数组 [{prop, label, visible?, fixed?, ...el-table-column-props}]
 * @returns {{visibleColumns: Ref, config: Ref, updateConfig: Function, resetConfig: Function}}
 */
export function useColumnSettings(storageKey, allColumns) {
  const config = ref(loadConfig(storageKey, allColumns))

  // visibleColumns：过滤出可见列，并合并原始列定义的其他属性（width/align等）
  const visibleColumns = ref([])

  function refreshVisible() {
    visibleColumns.value = config.value
      .filter(c => c.visible)
      .map(c => {
        // 从原始 allColumns 找到完整列定义，合并 visible/fixed 配置
        const def = allColumns.find(d => d.prop === c.prop) || {}
        return { ...def, fixed: c.fixed }
      })
  }
  refreshVisible()

  function updateConfig(newConfig) {
    config.value = newConfig
    saveConfig(storageKey, newConfig)
    refreshVisible()
  }

  function resetConfig() {
    const def = buildDefaultConfig(allColumns)
    config.value = def
    saveConfig(storageKey, def)
    refreshVisible()
  }

  return {
    config,
    visibleColumns,
    updateConfig,
    resetConfig
  }
}
