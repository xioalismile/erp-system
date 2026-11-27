// ====================================================================
// 数据字典选项 composable - 按类型编码拉取/新增/删除字典选项
// 模块级缓存：同 typeCode 多处使用只发一次请求，增删后所有实例同步
// ====================================================================
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listDictDataByCode, createDictDataByCode, deleteDictData } from '@/api/system/dict'
import { tt } from '@/i18n'

// typeCode -> { options, loading, loaded, promise }
const dictCache = new Map()

/**
 * 清空字典缓存（登出 / 切换公司时调用）。
 * 字典按租户配置，切公司后必须重拉，否则整站选项还是上一家公司的。
 */
export function clearDictCache() {
  dictCache.clear()
}

// 后端字段 dictDataId/dictLabel/dictValue 映射为 { id, label, value }
function normalizeDictItem(item) {
  const label = item?.dictLabel ?? item?.label ?? ''
  return {
    id: item?.dictDataId ?? item?.id ?? null,
    label,
    value: item?.dictValue ?? item?.value ?? label
  }
}

export function useDict(typeCode) {
  let entry = dictCache.get(typeCode)
  if (!entry) {
    entry = { options: ref([]), loading: ref(false), loaded: false, promise: null }
    dictCache.set(typeCode, entry)
  }
  const { options, loading } = entry

  // 首次调用拉取（失败静默，允许下次重试）
  if (!entry.loaded && !entry.promise) {
    entry.promise = listDictDataByCode(typeCode)
      .then(list => {
        options.value = (Array.isArray(list) ? list : []).map(normalizeDictItem)
        entry.loaded = true
      })
      .catch(() => {
        entry.promise = null
      })
      .finally(() => {
        loading.value = false
      })
    loading.value = true
  }

  // 新增选项（幂等），成功后更新缓存
  async function addOption(label) {
    const text = String(label ?? '').trim()
    if (!text) return null
    if (options.value.some(o => o.label === text || o.value === text)) return null
    try {
      const saved = await createDictDataByCode(typeCode, text)
      const opt = normalizeDictItem(saved || { dictLabel: text, dictValue: text })
      if (!options.value.some(o => o.id === opt.id || o.label === opt.label)) {
        options.value.push(opt)
      }
      return opt
    } catch (e) {
      ElMessage.warning(`${tt("选项「")}${text}${tt("」保存失败")}`)
      return null
    }
  }

  // 删除选项，成功后从缓存移除
  async function removeOption(opt) {
    if (!opt) return false
    const id = opt.id ?? opt
    if (id == null) return false
    try {
      await deleteDictData(id)
      options.value = options.value.filter(o => o.id !== id)
      return true
    } catch (e) {
      return false
    }
  }

  return { options, loading, addOption, removeOption }
}
