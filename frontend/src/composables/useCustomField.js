/**
 * 自定义字段组合式函数
 * 用于业务表单（BaseEdit）加载自定义字段定义和值，并把字段定义转换为 BaseEdit 的 sections 格式
 */
import { ref } from 'vue'
import {
  listEnabledDefinition,
  getFieldValues,
  saveFieldValues,
  batchGetFieldValues
} from '@/api/system/customField'
import { evaluateFormula, extractFormulaDeps } from '@/utils/formulaEvaluator'

/**
 * 加载实体的自定义字段定义，并转换为 BaseEdit 的 sections 字段配置
 * @param {string} entityType 实体类型，如 'customer'
 * @returns {Promise<{ sections: Array, fieldMap: Map, formulaFields: Array }>}
 */
export async function loadCustomFieldDefinitions(entityType) {
  try {
    const fields = await listEnabledDefinition(entityType) || []
    if (fields.length === 0) {
      return { sections: [], fieldMap: new Map(), formulaFields: [] }
    }

    const fieldMap = new Map() // fieldKey -> field定义
    const formulaFields = [] // 公式字段列表
    const sectionMap = new Map() // sectionName -> fields[]

    for (const f of fields) {
      fieldMap.set(f.fieldKey, f)
      if (f.fieldType === 'formula') {
        formulaFields.push(f)
      }
      const section = f.sectionName || '自定义信息'
      if (!sectionMap.has(section)) sectionMap.set(section, [])
      sectionMap.get(section).push(fieldDefToBaseEditField(f))
    }

    // 转换为 sections 数组
    const sections = []
    let sortOrder = 100 // 自定义分区排在业务字段后面
    for (const [sectionName, sectionFields] of sectionMap) {
      sections.push({
        title: sectionName,
        // 不再写死副标题「自定义扩展字段」：字段的「所属分区」本身就是标题，
        // 写死副标题会让多个自定义分区显示成两个一模一样的页签（用户 2026-09-20 反馈）
        customField: true, // 标记为自定义字段分区
        sortOrder: sortOrder++,
        fields: sectionFields
      })
    }
    return { sections, fieldMap, formulaFields }
  } catch (e) {
    console.warn('[customField] 加载字段定义失败:', e)
    return { sections: [], fieldMap: new Map(), formulaFields: [] }
  }
}

/**
 * 把后端的字段定义转换为 BaseEdit 的 field 配置
 */
function fieldDefToBaseEditField(f) {
  const field = {
    prop: f.fieldKey,
    label: f.fieldLabel,
    required: f.isRequired === 1,
    placeholder: f.placeholder || '',
    default: f.defaultValue || '',
    customField: true, // 标记为自定义字段
    customFieldType: f.fieldType
  }
  switch (f.fieldType) {
    case 'text':
      field.type = 'input'
      break
    case 'textarea':
      field.type = 'textarea'
      field.rows = 2
      break
    case 'number':
      field.type = 'number'
      field.precision = f.precisionVal || 2
      if (f.minValue != null) field.min = Number(f.minValue)
      if (f.maxValue != null) field.max = Number(f.maxValue)
      break
    case 'select': {
      field.type = 'select'
      field.filterable = true
      try {
        field.options = JSON.parse(f.fieldOptions || '[]').map(o => ({ label: o.label, value: o.value }))
      } catch {
        field.options = []
      }
      break
    }
    case 'date':
      field.type = 'date'
      break
    case 'datetime':
      field.type = 'date'
      field.valueFormat = 'YYYY-MM-DD HH:mm:ss'
      break
    case 'switch':
      field.type = 'switch'
      field.activeValue = 1
      field.inactiveValue = 0
      break
    case 'formula':
      // 公式字段：只读，前端实时计算
      field.type = 'number'
      field.readonly = true
      field.precision = f.precisionVal || 2
      field.formulaExpr = f.formulaExpr
      field.formulaDeps = extractFormulaDeps(f.formulaExpr)
      break
    default:
      field.type = 'input'
  }
  return field
}

/**
 * 加载实体实例的自定义字段值
 * @param {string} entityType
 * @param {number} entityId
 * @returns {Promise<Object>} { fieldKey: value }
 */
export async function loadCustomFieldValues(entityType, entityId) {
  if (!entityId) return {}
  try {
    return await getFieldValues(entityType, entityId) || {}
  } catch (e) {
    console.warn('[customField] 加载字段值失败:', e)
    return {}
  }
}

/**
 * 保存实体实例的自定义字段值
 * @param {string} entityType
 * @param {number} entityId
 * @param {Object} values { fieldKey: value }
 * @param {Array} formulaFields 公式字段定义（值由前端计算后传入）
 */
export async function saveCustomFieldValues(entityType, entityId, values, formulaFields = []) {
  if (!entityId || !values) return
  try {
    // 收集所有自定义字段的值（包括公式字段的计算结果）
    const toSave = { ...values }
    // 计算公式字段值
    for (const f of formulaFields) {
      const result = evaluateFormula(f.formulaExpr, toSave)
      if (result !== null) {
        toSave[f.fieldKey] = result
      }
    }
    await saveFieldValues(entityType, entityId, toSave)
  } catch (e) {
    console.warn('[customField] 保存字段值失败:', e)
    throw e
  }
}

/**
 * 单据保存后，把明细行的主键重新对齐到"保存后的新主键"。
 *
 * 【为什么必须有这个函数 / 2026-09-22 实锤 bug】
 * 单据的更新接口走的是"明细先删后插"（见《防复发坑记录》1.1：主表与明细一起落库、
 * 更新时先删旧明细再插新明细），所以**每次保存明细行的主键都会变**（实测 506 → 507）。
 * 而编辑页在保存完主表后，才去写"明细级自定义字段"，此时若仍用保存前抓到的旧主键，
 * 值就会挂到一个**已被删除的明细行**上 →
 *   - 编辑页再次打开读不到（按当前主键批量查值）
 *   - 各类报表（一行 = 一条明细）也永远读不到该值，表现为"自定义字段那一列空白"
 * 历史库里大量孤儿值（如 sale_order_item 的 entity_id=447/453/21）就是这么攒出来的。
 *
 * 对齐口径：优先按行号 lineNo 匹配（后端按 line_no 排序返回），行号缺失时退化为按下标匹配
 * —— 因为提交时已按"有物料的行"过滤，前后顺序一致，下标匹配是安全的。
 *
 * @param {Array} rows      页面上的明细行（会被就地改主键）
 * @param {Array} freshItems 保存后从详情接口重新取回的明细行
 * @param {string} idField  该单据明细主键的字段名（如 orderItemId / receiptItemId）
 */
export function remapItemIdsAfterSave(rows, freshItems, idField) {
  if (!Array.isArray(rows) || !Array.isArray(freshItems) || !idField) return
  const byLine = new Map()
  freshItems.forEach((it, i) => byLine.set(String(it.lineNo !== undefined && it.lineNo !== null ? it.lineNo : i + 1), it))
  rows.forEach((row, i) => {
    const key = String(row.lineNo !== undefined && row.lineNo !== null ? row.lineNo : i + 1)
    const hit = byLine.get(key) || freshItems[i]
    if (!hit) return
    const newId = hit[idField] !== undefined && hit[idField] !== null ? hit[idField] : hit.id
    if (newId === undefined || newId === null) return
    row[idField] = newId
    // 清掉历史遗留的错误键：各页普遍写成 `row.itemId || row.<idField>`，
    // 只要 itemId 还在，它就会继续盖住正确的新主键 → 必须删掉
    if (idField !== 'itemId') delete row.itemId
  })
}

/**
 * 批量加载多个实体实例的自定义字段值（列表显示用）
 * @param {string} entityType
 * @param {number[]} entityIds
 * @returns {Promise<Object>} { entityId: { fieldKey: value } }
 */
export async function batchLoadCustomFieldValues(entityType, entityIds) {
  if (!entityIds || entityIds.length === 0) return {}
  try {
    return await batchGetFieldValues(entityType, entityIds) || {}
  } catch (e) {
    console.warn('[customField] 批量加载字段值失败:', e)
    return {}
  }
}

/**
 * 实时计算公式字段值（表单输入时调用）
 * @param {Array} formulaFields 公式字段定义列表
 * @param {Object} formData 当前表单数据
 * @returns {Object} { fieldKey: computedValue } 需要更新的公式字段值
 */
export function computeFormulaFields(formulaFields, formData) {
  const result = {}
  for (const f of formulaFields) {
    const v = evaluateFormula(f.formulaExpr, formData)
    if (v !== null) {
      result[f.fieldKey] = v
    }
  }
  return result
}

/**
 * 把字段定义转换为 el-table 列配置（用于明细表格动态增加自定义字段列）
 * @param {Array} fields 字段定义列表（从 loadCustomFieldDefinitions 返回的 fieldMap 或原始字段）
 * @param {Object} options 配置项
 *   - editable: 列是否可编辑（默认 true）
 *   - listShowOnly: true=只返回 isListShow=1 的字段（纯展示场景）；
 *                   false=返回全部字段（编辑态需能填值），但 isListShow=0 的列标记 defaultVisible:false
 * @returns {Array} el-table-column 配置数组（含 required/isListShow/isSearch 元数据，供校验与列设置使用）
 */
export function customFieldsToTableColumns(fields, options = {}) {
  const { editable = true, listShowOnly = true } = options
  return fields
    .filter(f => !listShowOnly || f.isListShow === 1)
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map(f => {
      const col = {
        prop: f.fieldKey,
        label: f.fieldLabel,
        width: 140,
        customField: true,
        fieldType: f.fieldType,
        editable,
        // 元数据透传：必填校验 / 列设置显隐 / 搜索栏生成
        required: f.isRequired === 1,
        isListShow: f.isListShow === 1,
        isSearch: f.isSearch === 1,
        // isListShow=0 的列默认不在列表展示（编辑态仍返回，由调用方决定是否渲染输入控件）
        defaultVisible: f.isListShow !== 0,
        fieldDef: f
      }
      switch (f.fieldType) {
        case 'select':
          try {
            col.options = JSON.parse(f.fieldOptions || '[]')
          } catch {
            col.options = []
          }
          break
        case 'number':
          col.precision = f.precisionVal || 2
          break
      }
      return col
    })
}

/**
 * 把源实体的自定义字段值复制到目标对象（先按 fieldKey 精确匹配，再按 fieldLabel 同名匹配）
 * 用于：物料字段带入明细行、上游单据字段下推到下游
 * 同名关联规则（用户需求：物料档案的"芯片"改值后，单据明细同名"芯片"自动带出）：
 *   1. fieldKey 相同 → 直接复制（含用户显式同 key 场景）
 *   2. fieldKey 不同但 fieldLabel 相同 → 把源 label 对应的值写入目标 label 对应的 key
 * @param {Object} sourceValues 源字段值 { fieldKey: value }
 * @param {Object} target 目标对象（会被修改）
 * @param {Array} targetFieldDefs 目标实体的字段定义列表（用于只复制目标存在的字段）
 * @param {Array} sourceFieldDefs 源实体的字段定义列表（用于 label 同名匹配；不传则仅按 key 匹配）
 * @returns {Object} 修改后的目标对象
 */
export function copyCustomFieldValues(sourceValues, target, targetFieldDefs, sourceFieldDefs) {
  if (!sourceValues || !target) return target
  const targetKeys = targetFieldDefs
    ? new Set(targetFieldDefs.map(f => f.fieldKey))
    : null
  // 1. 按 fieldKey 直接匹配
  for (const [key, value] of Object.entries(sourceValues)) {
    if (targetKeys && !targetKeys.has(key)) continue
    target[key] = value
  }
  // 2. 按 fieldLabel 同名匹配（fieldKey 对不上但字段名相同的场景）
  if (sourceFieldDefs && targetFieldDefs && sourceFieldDefs.length > 0 && targetFieldDefs.length > 0) {
    for (const sf of sourceFieldDefs) {
      if (!sf || !sf.fieldLabel) continue
      // 源字段在目标实体无同 key 定义，但存在同名 label 的目标字段 → 关联
      const twin = targetFieldDefs.find(tf => tf && tf.fieldLabel === sf.fieldLabel && tf.fieldKey !== sf.fieldKey)
      if (twin && Object.prototype.hasOwnProperty.call(sourceValues, sf.fieldKey)) {
        const v = sourceValues[sf.fieldKey]
        // 目标行已有值时不覆盖（用户手填优先）；仅空值时自动带出
        const cur = target[twin.fieldKey]
        if (cur === undefined || cur === null || cur === '') {
          target[twin.fieldKey] = v
        }
      }
    }
  }
  return target
}

/**
 * 批量加载物料自定义字段值（用于选择物料后带入明细）
 * @param {Array} materialIds 物料ID数组
 * @returns {Promise<Object>} { materialId: { fieldKey: value } }
 */
export async function batchLoadMaterialCustomValues(materialIds) {
  return await batchLoadCustomFieldValues('material', materialIds)
}

/**
 * 为明细行注入物料自定义字段值（选择物料时调用）
 * @param {Object} row 明细行对象
 * @param {Number} materialId 物料ID
 * @param {Object} materialValueMap 物料自定义字段值Map { materialId: {fieldKey: value} }
 * @param {Array} itemFieldDefs 明细行实体的字段定义（用于确定哪些字段需要带入）
 * @param {Array} materialFieldDefs 物料实体的字段定义（用于 label 同名匹配关联；不传则仅按 key 匹配）
 * @returns {Object} 修改后的明细行
 */
export function injectMaterialCustomFields(row, materialId, materialValueMap, itemFieldDefs, materialFieldDefs) {
  if (!row || !materialId || !materialValueMap) return row
  const materialValues = materialValueMap[materialId]
  if (!materialValues) return row
  copyCustomFieldValues(materialValues, row, itemFieldDefs, materialFieldDefs)
  return row
}

/**
 * 校验明细行自定义字段必填项（保存前调用）
 * @param {Array} items 明细行数组
 * @param {Array} fieldDefs 明细字段定义
 * @returns {string|null} 第一个未填的必填字段提示文案；全部通过返回 null
 */
export function validateItemCustomRequired(items, fieldDefs) {
  if (!items || !fieldDefs || fieldDefs.length === 0) return null
  const requiredDefs = fieldDefs.filter(f => f && f.isRequired === 1)
  if (requiredDefs.length === 0) return null
  for (let i = 0; i < items.length; i++) {
    const row = items[i]
    for (const def of requiredDefs) {
      const v = row ? row[def.fieldKey] : undefined
      if (v === undefined || v === null || v === '') {
        return `第${i + 1}行：请填写「${def.fieldLabel}」`
      }
    }
  }
  return null
}

/**
 * 从单据明细数据中提取自定义字段值（保存时用）
 * @param {Array} items 明细行数组
 * @param {Array} fieldDefs 明细字段定义
 * @returns {Array} [{ itemIndex, values }]
 */
export function extractItemCustomValues(items, fieldDefs) {
  if (!items || !fieldDefs || fieldDefs.length === 0) return []
  const keys = new Set(fieldDefs.map(f => f.fieldKey))
  return items.map((row, idx) => {
    const values = {}
    for (const key of keys) {
      if (key in row) {
        values[key] = row[key]
      }
    }
    return { itemIndex: idx, values }
  })
}
