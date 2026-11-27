/**
 * 明细行「物料」下拉的输入过滤 + 背景提示文字（全站共用一份）
 *
 * 用户口径（2026-09-15）：
 *   「物料字段下拉框里面，不是有选择物料的背景文字吗？在里面声明就行了，不需要额外的搜索框，
 *     统一用物料字段的下拉」
 *   → 明细行**不加搜索框**：可搜索范围在下拉框的背景提示文字里声明，输入即过滤。
 *
 * 过滤范围（与"看得见的"对齐）：
 *   物料编码 / 物料名称 / 规格型号
 *   ＋ 该物料的自定义字段**值**（下拉选项里渲染的那些「字段名：值」段）
 *   ＋ 自定义字段**名**（输入字段名时，只筛出"该字段有填值"的物料，不会把全部物料都命中）
 *
 * 兼容性：物料对象在个别单据页是 snake_case（组装单/拆卸单整页用 snake_case），
 *   故字段一律按 camelCase → snake_case 依次取值。
 *
 * 容错原则：关键字为空直接返回原列表；任何数据缺失都不抛错（只当不匹配）。
 */
import { tt } from '@/i18n'

/** 取物料主键：兼容 camelCase / snake_case */
export function materialIdOf(m) {
  if (!m) return null
  return m.materialId != null ? m.materialId : (m.material_id != null ? m.material_id : null)
}

/** 依次取第一个有值的字段（兼容两种命名） */
function pick(obj, ...keys) {
  if (!obj) return undefined
  for (const k of keys) {
    const v = obj[k]
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

/** 物料自身的可搜索文本：编码 + 名称 + 规格型号 */
function materialBaseText(m) {
  return [
    pick(m, 'materialCode', 'material_code'),
    pick(m, 'materialName', 'material_name'),
    pick(m, 'model', 'specification')
  ]
    .filter(v => v !== undefined && v !== null)
    .map(String)
    .join(' ')
}

/** 取某物料的「字段值」对象：{ fieldKey: value }（valueMap 的 key 可能是数字或字符串，两种都试） */
function valuesOf(valueMap, materialId) {
  if (!valueMap || materialId === null || materialId === undefined) return null
  return valueMap[materialId] || valueMap[String(materialId)] || null
}

/** 某物料在该字段上是否有值（用于"输入字段名 → 筛出填过该字段的物料"） */
function hasValue(vals, fieldKey) {
  if (!vals) return false
  const v = vals[fieldKey]
  return v !== undefined && v !== null && String(v).trim() !== ''
}

/**
 * 按关键字过滤物料列表（明细行物料下拉的 filter-method 用）
 * @param {Array} materials 候选物料（页面已加载的列表）
 * @param {String} keyword 用户输入
 * @param {Object} valueMap 物料自定义字段值：{ materialId: { fieldKey: value } }
 * @param {Array} searchableFields 参与搜索的自定义字段定义：[{ fieldKey, fieldLabel }]
 * @returns {Array} 过滤后的物料列表（关键字为空时原样返回）
 */
export function filterMaterials(materials, keyword, valueMap, searchableFields) {
  const list = materials || []
  const kw = keyword === undefined || keyword === null ? '' : String(keyword).trim().toLowerCase()
  if (kw === '') return list
  const fields = searchableFields || []
  // 关键字命中的字段名（用户直接输入字段名 → 只筛出该字段有值的物料）
  const matchedNameKeys = fields
    .filter(f => f && f.fieldLabel && String(f.fieldLabel).toLowerCase().includes(kw))
    .map(f => f.fieldKey)

  return list.filter(m => {
    if (materialBaseText(m).toLowerCase().includes(kw)) return true
    const vals = valuesOf(valueMap, materialIdOf(m))
    if (!vals) return false
    // ① 命中字段值
    for (const f of fields) {
      if (!f || !f.fieldKey) continue
      const v = vals[f.fieldKey]
      if (v !== undefined && v !== null && String(v).toLowerCase().includes(kw)) return true
    }
    // ② 命中字段名：该物料在该字段上确实有值
    for (const key of matchedNameKeys) {
      if (hasValue(vals, key)) return true
    }
    return false
  })
}

/**
 * 拼接物料下拉的背景提示文字：没有可搜索的自定义字段时**原样返回**（零回归）
 * @param {String} baseText 页面原文案（如 tt('请选择物料')）
 * @param {Array} searchableFields 参与搜索的自定义字段定义
 * @returns {String} 如「请选择物料（可按 编码/名称/规格/芯片 搜索）」
 */
export function buildMaterialPlaceholder(baseText, searchableFields) {
  const names = (searchableFields || []).map(f => f && f.fieldLabel).filter(Boolean)
  if (names.length === 0) return baseText
  const parts = [tt('编码'), tt('名称'), tt('规格'), ...names.map(n => String(n))]
  return `${baseText}${tt('（可按 ')}${parts.join('/')}${tt(' 搜索）')}`
}
