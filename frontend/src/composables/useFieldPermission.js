import { ref, readonly } from 'vue'
import { getCurrentUserFieldPermission } from '@/api/system/fieldPermission'

const permissionMap = ref({})
const loadedPages = ref(new Set())
const loadingPromises = {}

/**
 * 清理字段权限缓存（解决跨用户/跨实例的缓存污染问题）
 * 1) 不指定 key：清掉所有已加载页面缓存（登出/切用户时用）
 * 2) 指定 key：仅清掉某个页面的缓存（如字段权限配置页保存后，让业务表单下次重新拉权限）
 * 3) 注意：所有共享本 composable 的组件实例会立即看到缓存被清，下次访问 isFieldXxx 时
 *    会再次触发 loadFieldPermission 拉后端（性能影响微乎其微，管理员后端是 O(1) Map.of 短路）
 */
export function clearFieldPermissionCache(key) {
  if (key) {
    delete permissionMap.value[key]
    loadedPages.value.delete(key)
    const lp = loadingPromises[key]
    if (lp) delete loadingPromises[key]
  } else {
    permissionMap.value = {}
    loadedPages.value = new Set()
    Object.keys(loadingPromises).forEach(k => delete loadingPromises[k])
  }
}

/**
 * 布尔归一化（经验691299 单一真相源：Integer 0/1 与 boolean 混用必须统一转换）
 *   判定规则（仅以下值视为真值，其余按 def 缺省值兜底）：
 *     true / 1 / "1" / "true" / "TRUE"            → true
 *     false / 0 / "0" / "false" / "FALSE"         → false
 *     null / undefined / NaN / 其他字符串/其他数字 → def（缺省值）
 *   4 条源码断言：toBool(1)===true, toBool(0)===false, toBool(null,true)===true, toBool(undefined,false)===false
 */
function toBool(v, def) {
  if (v === true || v === 1 || v === '1' || v === 'true' || v === 'TRUE') return true
  if (v === false || v === 0 || v === '0' || v === 'false' || v === 'FALSE') return false
  return def
}

/**
 * 字段权限 composable
 * pageKey = 单据表名/billType 小写（如 sale_order），与后端 FieldMetaService、
 * 自定义字段 entity_type、EntityMetadataRegistry 数据源 key 同口径。
 * 后端 /current/page/{pageKey} 返回 Map<fieldKey, {fieldKey, visible, editable, required}>（0/1 整数）。
 */
export function useFieldPermission(pageKey) {
  const fieldPermissionMap = ref({})

  async function loadFieldPermission(key = pageKey) {
    if (!key) return {}
    if (loadedPages.value.has(key)) {
      fieldPermissionMap.value = permissionMap.value[key] || {}
      return fieldPermissionMap.value
    }
    if (loadingPromises[key]) return loadingPromises[key]
    loadingPromises[key] = (async () => {
      try {
        const data = await getCurrentUserFieldPermission(key)
        const map = {}
        // 后端返回 Map<fieldKey, PermissionDTO>（visible/editable/required 为 0/1 Integer）
        // 注意：此处不在 map 内直接提前转 boolean，保持后端原值，toBool 放在 isFieldXxx 三函数里（Single Source of Truth）
        if (data && typeof data === 'object' && !Array.isArray(data)) {
          Object.values(data).forEach(item => {
            if (!item || !item.fieldKey) return
            map[item.fieldKey] = {
              visible: item.visible,
              editable: item.editable,
              required: item.required
            }
          })
        }
        // === 缓存策略（跨用户污染修复，核心关键点） ===
        //   - 空 map = 两种语义：管理员后端短路返回 Map.of()，或者普通角色从未保存过权限
        //   - 两种情况下都**不应该**写入全局缓存，否则：
        //       1) 管理员先访问 → 缓存 {} → 切普通角色再访问 → 命中空缓存 → 本该隐藏的字段全部显示（权限越权）
        //       2) 用户在字段权限页刚保存配置 → 切回业务表单 → 仍命中旧缓存，修改不生效
        //   - 只有当后端返回了"明确存在至少1条字段级权限配置"时才缓存（非空 map 必然是 DB 真实数据）
        //   - 另外：登出/登录时会调 clearFieldPermissionCache() 做保险兜底
        const hasAnyPerm = Object.keys(map).length > 0
        if (hasAnyPerm) {
          permissionMap.value[key] = map
          loadedPages.value.add(key)
        }
        fieldPermissionMap.value = map
        return map
      } finally {
        delete loadingPromises[key]
      }
    })()
    return loadingPromises[key]
  }

  function isFieldVisible(fieldKey) {
    const perm = fieldPermissionMap.value[fieldKey]
    // perm不存在=无配置=默认 true（可见），否则用 toBool 归一化后端 Integer 0/1
    return perm ? toBool(perm.visible, true) : true
  }

  function isFieldEditable(fieldKey) {
    const perm = fieldPermissionMap.value[fieldKey]
    return perm ? toBool(perm.editable, true) : true
  }

  function isFieldRequired(fieldKey) {
    const perm = fieldPermissionMap.value[fieldKey]
    return perm ? toBool(perm.required, false) : false
  }

  if (pageKey) {
    loadFieldPermission(pageKey)
  }

  return {
    fieldPermissionMap: readonly(fieldPermissionMap),
    loadFieldPermission,
    isFieldVisible,
    isFieldEditable,
    isFieldRequired
  }
}
