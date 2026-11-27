/**
 * 自定义字段「可搜索」通用能力（列表页 / 选择弹窗 / 下拉过滤 共用一份）
 *
 * 背景：设置中心 → 自定义字段配置页的「可搜索」开关，页面承诺
 *   「可搜索字段会同步到业务表 ext_data JSONB 列，支持 WHERE 过滤」。
 *   实际过滤走的是 EAV 值表 sys_custom_field_value（后端 applyCustomSearch 生成 EXISTS 子查询），
 *   ext_data 只是 is_search=1 字段的快照列，不参与过滤。
 *
 * 为什么抽公共：这套「取该实体勾了可搜索的字段 → 渲染搜索项 → 打包后端入参」的逻辑
 *   要在 20+ 个单据列表页、物料库选择弹窗里反复用。若各页各写一份，后续改一处要改 20 多遍。
 *
 * 用法（三步）：
 *   const cf = useCustomFieldSearch('material')
 *   await cf.load()                                   // 挂载时调一次
 *   // 模板：<CustomFieldSearchBar v-model="cf.values" :fields="cf.searchFields" />
 *   // 请求：{ ...其它查询条件, ...cf.buildParams() }   // 无有效条件时 buildParams() 返回 {}，旧行为零变化
 *
 * 容错原则：任何一步失败都静默降级（空字段列表），**绝不因自定义字段问题阻塞列表加载或选料**。
 */
import { ref, reactive, computed } from 'vue'
import { loadCustomFieldDefinitions } from '@/composables/useCustomField'

export function useCustomFieldSearch(entityType) {
  /** 该实体全部启用的字段定义 */
  const fields = ref([])
  /** 搜索项输入值：{ fieldKey: 用户输入 } */
  const values = reactive({})
  const loading = ref(false)

  /** 勾了「可搜索」的字段 —— 搜索项就渲染这些 */
  const searchFields = computed(() => (fields.value || []).filter(f => f && f.isSearch === 1))

  /** 补齐输入值的 key（字段定义变化后调，避免 v-model 指向不存在的属性导致响应式失效） */
  function ensureValueKeys() {
    searchFields.value.forEach(f => {
      if (!(f.fieldKey in values)) values[f.fieldKey] = ''
    })
  }

  /** 加载字段定义；失败静默降级为空列表并返回空数组
   *  @param {Array} [preloaded] 已别处加载好的字段定义（传入则不发请求，避免同一个接口请求两遍）
   */
  async function load(preloaded) {
    if (Array.isArray(preloaded)) {
      fields.value = preloaded
      ensureValueKeys()
      return fields.value
    }
    loading.value = true
    try {
      const r = await loadCustomFieldDefinitions(entityType)
      fields.value = Array.from((r && r.fieldMap) ? r.fieldMap.values() : [])
    } catch (e) {
      console.warn('[cf-search] 加载自定义字段定义失败:', e)
      fields.value = []
    } finally {
      loading.value = false
      ensureValueKeys()
    }
    return fields.value
  }

  /**
   * 打包成后端入参：{ customSearch: '{"fieldKey":"值"}' }
   * 全部为空时返回 {}（保证不传该参数 = 与改造前完全一致）
   */
  function buildParams() {
    const q = {}
    searchFields.value.forEach(f => {
      const v = values[f.fieldKey]
      if (v === undefined || v === null) return
      const s = String(v).trim()
      if (s !== '') q[f.fieldKey] = s
    })
    return Object.keys(q).length > 0 ? { customSearch: JSON.stringify(q) } : {}
  }

  /** 清空全部搜索项 */
  function reset() {
    Object.keys(values).forEach(k => { values[k] = '' })
  }

  /**
   * 写入某个搜索项的值（供搜索栏组件回传：子组件 emit('change', { fieldKey, value })）
   * @param {{fieldKey: String, value: any}} payload
   */
  function setValue(payload) {
    if (!payload || !payload.fieldKey) return
    values[payload.fieldKey] = payload.value
  }

  /**
   * 本地匹配：关键字是否命中「某个字段值对象」里的任一可搜索字段
   * 用于值已经在前端拿到、不需要走接口的场景（如明细行物料下拉的输入过滤）
   * @param {Object} valueObj 形如 { fieldKey: value }
   * @param {String} keyword
   */
  function matchValues(valueObj, keyword) {
    const kw = keyword === undefined || keyword === null ? '' : String(keyword).trim().toLowerCase()
    if (kw === '') return true
    if (!valueObj) return false
    return searchFields.value.some(f => {
      const v = valueObj[f.fieldKey]
      if (v === undefined || v === null) return false
      return String(v).toLowerCase().includes(kw)
    })
  }

  return {
    fields,
    searchFields,
    values,
    loading,
    load,
    ensureValueKeys,
    buildParams,
    reset,
    setValue,
    matchValues
  }
}
