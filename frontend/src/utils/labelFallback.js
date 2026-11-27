/**
 * 下拉选项兜底工具（2026-09-27）
 *
 * 背景：系统大量下拉（客户/物料/仓库/部门/结算方式/币种…）的 v-model 绑定的是关联记录的主键，
 * 若候选项列表里没有该主键（例如客户下拉只放「正式客户」，或远程下拉尚未加载），
 * Element Plus 会把原始主键（数字）直接显示在下拉框里。
 *
 * 做法：当「当前值」不在候选项中、而同一份数据（表单/明细行）里带有对应名称字段时，
 * 临时补一个只用于显示的选项，避免界面上露出数字主键。不改变任何数据、不改变候选项来源。
 *
 * 使用：传入候选项数组、绑定的字段名、名称字段名（可选，缺省按命名规则推断）与数据来源对象。
 */

/** 根据主键字段名推断可能的名称字段名，如 materialId -> materialName / materialCode / materialNo */
export function namePropCandidates(prop) {
  if (!prop) return []
  const camel = String(prop).replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase())
  const base = camel.endsWith('Id') ? camel.slice(0, -2) : camel
  if (!base) return []
  return [...new Set([base + 'Name', base + 'Code', base + 'No'])]
}

function pickName(source, candidates) {
  for (const k of candidates) {
    if (!k) continue
    const v = source[k]
    if (v !== null && v !== undefined && String(v).trim() !== '') return { key: k, label: v }
  }
  return null
}

/**
 * 生成"带兜底"的候选项数组（不修改原数组）。
 *
 * 兼容两种候选项形态：
 *   ① 原始对象形态（如 `{customerId, customerName}`，模板读 `c.customerId`/`c.customerName`）
 *   ② 通用形态（`{value, label}`，模板读 `o.value`/`o.label`）
 * 兜底项同时带上这两种键（`value/label` + `<valueField>/<nameField>`），
 * 因此不论模板怎么读都能正确显示为名称；并按"值"去重，不会重复追加。
 *
 * @param {Array} list 原候选项
 * @param {string} valueField 值字段名（= 表单/明细行里绑定的字段名，如 customerId）
 * @param {string} [nameField] 名称字段名（如 customerName）；不传则按命名规则推断
 * @param {object} [source] 数据来源（表单对象 / 明细行对象）
 * @param {string[]} [extraFields] 需要一并复制到兜底项上的额外字段（如 materialCode，供模板拼 label 用）
 */
/** 可能承载"主键"的候选项键名（用于按候选项实际形态自动适配） */
const PRIMARY_VALUE_KEYS = ['value', 'stockId', 'warehouseId', 'materialId', 'customerId', 'supplierId', 'productId', 'unitId', 'deptId', 'employeeId']
/** 可能承载"名称"的候选项键名 */
const PRIMARY_NAME_KEYS = ['label', 'stockName', 'warehouseName', 'materialName', 'customerName', 'supplierName', 'productName', 'unitName', 'deptName', 'employeeName']

export function withFallbackOption(list, valueField, nameField, source, extraFields) {
  const arr = Array.isArray(list) ? list : []
  if (!source || !valueField) return arr
  const value = source[valueField]
  if (value === null || value === undefined || value === '') return arr

  // 1) 名称来源：优先调用方显式指定的名称字段，其次按命名规则推断
  const nameCandidates = nameField ? [nameField, ...namePropCandidates(valueField)] : namePropCandidates(valueField)
  const found = pickName(source, nameCandidates)
  if (!found) return arr

  // 2) 参考候选项的实际形态，自动推断"模板读取的值键/名称键"
  //    （如仓库对象用 stockId/stockName，而表单字段叫 warehouseId/warehouseName）
  const sample = arr.find(o => o && typeof o === 'object') || null
  const sampleKeys = sample ? Object.keys(sample) : []
  const valueKeys = [...new Set([valueField, ...PRIMARY_VALUE_KEYS.filter(k => k === 'value' || sampleKeys.includes(k))])]
  const nameKeys = [...new Set([found.key, ...(nameField ? [nameField] : []), ...PRIMARY_NAME_KEYS.filter(k => k === 'label' || sampleKeys.includes(k))])]

  // 3) 已存在同值候选项 → 不追加（按上面推断出的值键去重，兼容两种形态）
  if (arr.some(o => o && valueKeys.some(k => o[k] === value))) return arr

  // 4) 组装兜底项：把两种形态的键都带上，保证无论模板怎么读都显示为名称
  const fallback = { value, label: found.label, __displayFallback: true }
  valueKeys.forEach(k => { if (k && fallback[k] === undefined) fallback[k] = value })
  nameKeys.forEach(k => { if (k && fallback[k] === undefined) fallback[k] = found.label })
  if (Array.isArray(extraFields)) {
    for (const k of extraFields) {
      if (k && fallback[k] === undefined && source[k] !== null && source[k] !== undefined) fallback[k] = source[k]
    }
  }
  return [...arr, fallback]
}
