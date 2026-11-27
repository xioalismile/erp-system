/**
 * 单据明细「多单位换算」列（全站唯一一份 · 2026-11-10 多单位换算第三阶段）
 *
 * 为什么自动注入：单据明细通用组件（BillItemsTable）被 40 个单据页共用，若逐页复制这 5 列，
 * 就是用户明令反对的"缝缝补补"。这里沿用既有的「物料档案列自动并入」模式
 * （useMaterialCustomFields.mergeMaterialArchiveColumns）：页面无需任何改动，
 * 只要明细列定义里同时有「物料」列（materialId 同族列）与「单位」列（unitId / unit / unitName），
 * 就在单位列之后自动插入：
 *   副单位 → 副单位数量 → 辅助数量 → 浮动单位 → 浮动数量 → 浮动换算率
 * 已有同名列不重复加（幂等，页面自己写了也以页面为准）。
 *
 * 取值口径与 useUnitConvert 同源（1 基本单位 = X 该辅单位）：
 * - 副单位：录基本单位 → 第一个「固定」换算行的辅单位；录某辅单位 → 基本单位；
 *   未开启辅计量 / 换算行未加载 → 基本单位。
 * - 副单位数量 = 行数量 × 副单位折算率；副单位等于基本单位时辅助数量不显示（避免与「数量」列重复）。
 * - 浮动单位候选 = 该物料换算行里「换算方式=浮动」的单位；浮动数量 = 行上 floatQuantity（随行保存）。
 * - 浮动换算率（2026-11-11）= 基本单位数量 ÷ 浮动数量，只读由系统算，不让填单人自己算；
 *   两个数量任一为空即留空，不按档案瞎预填。
 * - 浮动两列带 floatOnly 标记，由通用组件按全局开关「启用浮动单位」整列过滤。
 *
 * 只读列（副单位 / 副单位数量 / 辅助数量）不做行数据落库：取值一律由行（unitId/quantity）+ 物料 + 换算行
 * 实时算出，单位或数量一变即跟着变，不会出现"算过一次就写死在行上"的陈旧值。
 */
import { reactive, ref } from 'vue'
import { getMaterial } from '@/api/basedata/material'
import { listAllUnit } from '@/api/basedata/unit'
import { formatWith, precisionTrimZero, scaleFor } from '@/composables/usePrecision'
import { loadConversions, subUnitOf, floatingRowsOf, fixedRowsOf, resolveUnitRate, syncConversions } from '@/composables/useUnitConvert'

/** 5 个多单位列的 prop 名（顺序即展示顺序） */
export const MULTI_UNIT_COLUMN_PROPS = ['subUnitName', 'subQuantity', 'auxQuantityText', 'floatUnitId', 'floatQuantity', 'floatConversionRate']

/**
 * 列定义（与项目既有列定义同风格：prop / label / width / align / type / editor）
 * - order：列设置内的相对顺序（从 1 起，插入时按此排序）
 * - readonly：只读语义（前 3 列只读；浮动数量可编辑）
 * - floatOnly：受全局开关「启用浮动单位」控制整列显隐
 */
export const MULTI_UNIT_COLUMNS = [
  { prop: 'subUnitName', label: '副单位', width: 100, align: 'center', readonly: false, order: 1 },
  { prop: 'subQuantity', label: '副单位数量', width: 90, align: 'right', type: 'text', readonly: true, order: 2 },
  { prop: 'auxQuantityText', label: '辅助数量', width: 100, align: 'right', type: 'text', readonly: true, order: 3 },
  { prop: 'floatUnitId', label: '浮动单位', width: 110, align: 'center', readonly: false, floatOnly: true, order: 4 },
  { prop: 'floatQuantity', label: '浮动数量', width: 110, align: 'right', editor: 'number', scaleKey: 'qty', readonly: false, floatOnly: true, order: 5 },
  // 浮动换算率：1 浮动单位 = X 基本单位；由系统按「基本单位数量 ÷ 浮动数量」算出（2026-11-11 用户口径）
  // 只读展示，不再让填单人自己算；保存时后端按同一口径回算落库
  { prop: 'floatConversionRate', label: '浮动换算率', width: 120, align: 'right', type: 'text', readonly: true, floatOnly: true, order: 6 }
]

/** 「单位」列识别口径：这三种命名覆盖全站所有单据明细的单位列 */
const UNIT_COLUMN_PROPS = ['unitId', 'unit', 'unitName']
/** 「物料」列识别口径：与通用组件的物料档案列同口径（materialId / childMaterialId 等同族列）
 *  2026-11-11：补 product_id / productId（组装单、拆卸单明细沿用历史下划线口径）——
 *  否则这两张单只能由页面自己注入 5 列并自写渲染，副单位不可手选、浮动数量不回写。 */
const MATERIAL_ID_RE = /(materialId|product_?id|childMaterialId)$/i

/**
 * 本次需要注入的多单位列（无单位列 / 无物料列 / 已存在同名列 → 返回空数组，幂等）
 * 必须同时具备「单位列 + 物料列」：副单位/副单位数量要按该行物料的基本单位与换算行算，
 * 拿不到物料（如成本核算那种只有手填"商品名称+单位"的明细）就不注入，避免出现 3 个永远空白的列。
 * @param {Array} columns 页面传入的明细列定义
 * @returns {Array}
 */
export function multiUnitColumnsFor(columns) {
  const list = (columns || []).filter(c => c && c.prop)
  if (!list.some(c => UNIT_COLUMN_PROPS.includes(c.prop))) return []
  if (!list.some(c => MATERIAL_ID_RE.test(c.prop))) return []
  const existing = new Set(list.map(c => c.prop))
  return MULTI_UNIT_COLUMNS
    .filter(c => !existing.has(c.prop))
    .slice()
    .sort((a, b) => a.order - b.order)
}

/**
 * 把多单位列并入明细列定义：插在单位列之后；无单位列 / 全部已存在 → 原样返回（幂等）
 * @param {Array} columns
 * @returns {Array} 合并后的新数组（不修改入参）
 */
export function mergeMultiUnitColumns(columns) {
  const list = (columns || []).slice()
  const toAdd = multiUnitColumnsFor(list)
  if (!toAdd.length) return list
  const anchor = list.findIndex(c => c && UNIT_COLUMN_PROPS.includes(c.prop))
  const next = list.slice()
  next.splice(anchor + 1, 0, ...toAdd)
  return next
}

/** 数量文本：按设置中心「数量小数位数 + 省略多余的 0」格式化；空值返回空白（不留占位符） */
export function unitQtyText(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (!Number.isFinite(n)) return ''
  return formatWith(n, scaleFor('qty'), precisionTrimZero.value)
}

/**
 * 基本单位数量 = 行数量 × 行单位折算率（行单位就是基本单位时等于数量本身）。
 * 2026-11-11：浮动换算率改由它和浮动数量算出，这里只做纯计算，空值返回 null（界面留空）。
 */
export function baseQtyOf(row, material, allUnits) {
  if (!row || row.quantity == null || row.quantity === '') return null
  const q = Number(row.quantity)
  if (!Number.isFinite(q)) return null
  const rate = material ? resolveUnitRate(material, row.unitId, allUnits) : 1
  const base = q * (Number(rate) || 1)
  return Number(base.toFixed(4))
}

/**
 * 浮动换算率 = 基本单位数量 ÷ 浮动数量（语义：1 浮动单位 = X 基本单位）。
 * 没选浮动单位 / 浮动数量为空或 0 / 基本单位数量为空 → 返回 null（界面留空，不瞎算）。
 */
export function floatRateOf(row, material, allUnits) {
  if (!row || row.floatUnitId == null) return null
  const fq = Number(row.floatQuantity)
  if (!Number.isFinite(fq) || fq <= 0) return null
  const base = baseQtyOf(row, material, allUnits)
  if (base == null) return null
  return Number((base / fq).toFixed(8))
}

/** 副单位信息（对侧规则：录基本单位 → 第一个固定行的辅单位；录辅单位 → 基本单位） */
export function subUnitInfoOf(row, material, allUnits) {
  // 行上手选的副单位优先（row.subUnitId），未选则按「录基本→第一个固定行 / 录辅→基本单位」默认规则
  return subUnitOf(material, row ? row.unitId : null, allUnits, row ? row.subUnitId : null)
}

/** 副单位名（只读文本列） */
export function subUnitNameOf(row, material, allUnits) {
  return subUnitInfoOf(row, material, allUnits).unitName || ''
}

/** 副单位数量 = 行数量 × 副单位折算率（物料未取到 / 数量为空 → null，界面显示空白） */
export function subUnitQtyOf(row, material, allUnits) {
  if (!row || !material) return null
  if (row.quantity == null || row.quantity === '') return null
  const q = Number(row.quantity)
  if (!Number.isFinite(q)) return null
  return q * (subUnitInfoOf(row, material, allUnits).rate || 1)
}

/** 副单位数量文本（按数量位数显示） */
export function subUnitQtyTextOf(row, material, allUnits) {
  return unitQtyText(subUnitQtyOf(row, material, allUnits))
}

/** 辅助数量文本：「600件」= 副单位数量 + 副单位名；副单位即基本单位（未开辅计量）时不显示 */
export function auxQtyTextOf(row, material, allUnits) {
  const v = subUnitQtyOf(row, material, allUnits)
  if (v == null) return ''
  const info = subUnitInfoOf(row, material, allUnits)
  if (info.unitId == null || !material || info.unitId === material.baseUnitId) return ''
  return `${unitQtyText(v)}${info.unitName || ''}`
}

/** 浮动单位候选：该物料换算行里「换算方式=浮动」的单位（名称取自单位列表） */
export function floatOptionsOf(material, allUnits) {
  const nameOf = (id) => {
    if (id == null) return ''
    const u = (allUnits || []).find(x => x.unitId === id)
    return u ? u.unitName : ''
  }
  return floatingRowsOf(material).map(r => ({ value: r.unitId, label: nameOf(r.unitId) }))
}

/** 副单位候选：该物料档案里「固定」换算行的辅单位（单据行可手选，2026-11-10） */
export function subOptionsOf(material, allUnits) {
  const nameOf = (id) => {
    if (id == null) return ''
    const u = (allUnits || []).find(x => x.unitId === id)
    return u ? u.unitName : ''
  }
  return fixedRowsOf(material).map(r => ({ value: r.unitId, label: nameOf(r.unitId) }))
}

/**
 * 选浮动单位（2026-11-11 修订）：
 * 不再按档案预填「浮动换算率」——浮动率由「基本单位数量 ÷ 浮动数量」算出，
 * 两个数量任一为空就留空，绝不让填单人去猜一个率。
 * 清空浮动单位时一并清空浮动数量与率，避免残留旧值。
 */
export function applyFloatUnitToRow(row, material, allUnits) {
  if (!row) return
  if (row.floatUnitId == null) {
    row.floatQuantity = null
    row.floatConversionRate = null
    return
  }
  row.floatConversionRate = floatRateOf(row, material, allUnits)
}

/**
 * 单元格取值能力（每个明细组件一份实例：物料实体 / 单位名 / 换算行都在本实例内缓存，
 * 组件卸载即释放，不做跨用户、跨公司的模块级残留）
 *
 * @param {Object} options
 * @param {Function} options.getIdProp 取行上物料ID字段名（无物料列时返回 null）
 * @param {Function} options.getRows 取当前明细行数组
 */
export function useMultiUnitCells(options = {}) {
  const getIdProp = typeof options.getIdProp === 'function' ? options.getIdProp : () => options.idProp || 'materialId'
  const getRows = typeof options.getRows === 'function' ? options.getRows : () => []

  /** 物料实体缓存（reactive：批量回填后单元格自动刷新） */
  const materials = reactive(new Map())
  const materialLoading = new Map()
  /** 全部单位（单位名解析用；一次页面实例只拉一次） */
  const units = ref([])
  let unitsLoading = null
  /** 行 → 上次物料标识（WeakMap：不往行对象写辅助属性，避免辅助字段随单据保存提交到后端） */
  const rowMaterialMark = new WeakMap()

  /** 记一份物料实体（由调用方的既有取数链路回填，避免同一物料重复请求） */
  function remember(materialId, material) {
    if (materialId == null) return
    // 物料详情自带换算行（2026-11-11）：以档案最新值为准覆盖共享缓存，
    // 否则本会话缓存的旧换算行会让"档案改了、单据还是旧单位"（用户实测：改完盒/套不生效）
    syncConversions(materialId, material && material.unitConversions)
    if (!materials.has(materialId)) materials.set(materialId, material || null)
  }

  /** 行对应的物料实体（未取到返回 null，单元格显示空白） */
  function materialOf(row) {
    const idProp = getIdProp()
    if (!row || !idProp || row[idProp] == null) return null
    return materials.get(row[idProp]) || null
  }

  /** 批量补物料实体（已缓存跳过；请求失败不落缓存，行数据再变时可重试） */
  async function ensureMaterials(materialIds) {
    const ids = [...new Set((materialIds || []).filter(id => id != null && !materials.has(id)))]
    if (!ids.length) return materials
    await Promise.all(ids.map(id => {
      if (materialLoading.has(id)) return materialLoading.get(id)
      const task = getMaterial(id)
        .then(m => remember(id, m))
        .catch(() => {})
        .finally(() => materialLoading.delete(id))
      materialLoading.set(id, task)
      return task
    }))
    return materials
  }

  /** 加载全部单位（名称解析用；失败降级为空白，不阻塞明细渲染） */
  async function loadUnits() {
    if (units.value.length) return units.value
    if (!unitsLoading) {
      unitsLoading = listAllUnit()
        .then(list => { units.value = Array.isArray(list) ? list : [] })
        .catch(e => { console.warn('[multi-unit] 单位列表加载失败（单位名暂显示空白）:', e) })
        .finally(() => { unitsLoading = null })
    }
    return unitsLoading
  }

  /** 整单预取：物料实体 + 单位名 + 换算行（各一次批量请求） */
  async function ensureLoaded() {
    const idProp = getIdProp()
    if (!idProp) return
    const ids = (getRows() || []).map(r => (r && r[idProp] != null ? r[idProp] : null)).filter(id => id != null)
    if (!ids.length) return
    await Promise.all([ensureMaterials(ids), loadUnits()])
    await loadConversions(ids)
  }

  /**
   * 换料同步：物料变了 → 清空该行浮动单位 / 浮动数量（旧物料的浮动单位不再适用），与试点页口径一致。
   * 仅在同一行首次出现/物料变化时动手，首次装载不清空（历史单据的浮动字段原样保留）。
   */
  function syncRowMaterials() {
    const idProp = getIdProp()
    if (!idProp) return
    ;(getRows() || []).forEach(row => {
      if (!row) return
      const id = row[idProp]
      if (id == null) return
      const key = String(id)
      const prev = rowMaterialMark.get(row)
      if (prev === key) return
      if (prev !== undefined) {
        row.floatUnitId = null
        row.floatQuantity = null
      }
      rowMaterialMark.set(row, key)
    })
  }

  return {
    materials,
    units,
    remember,
    materialOf,
    ensureMaterials,
    loadUnits,
    ensureLoaded,
    syncRowMaterials,
    // 单元格取值（模板里直接调：内部读 reactive 缓存，换算行回填后自动刷新）
    subUnitName: (row) => subUnitNameOf(row, materialOf(row), units.value),
    subUnitQtyText: (row) => subUnitQtyTextOf(row, materialOf(row), units.value),
    auxQtyText: (row) => auxQtyTextOf(row, materialOf(row), units.value),
    subOptions: (row) => subOptionsOf(materialOf(row), units.value),
    floatOptions: (row) => floatOptionsOf(materialOf(row), units.value),
    onFloatUnitChange: (row) => applyFloatUnitToRow(row, materialOf(row), units.value),
    /** 浮动换算率只读文本：= 基本单位数量 ÷ 浮动数量；缺值留空（2026-11-11 不让填单人自己算） */
    floatRateText: (row) => {
      const v = floatRateOf(row, materialOf(row), units.value)
      return v == null ? '' : formatWith(v, scaleFor('rate'), precisionTrimZero.value)
    },
    /**
     * 浮动数量变化：按「数量 × 行单位率 = 基本单位数量」回写基本单位数量快照，
     * 再由「基本单位数量 ÷ 浮动数量」算出浮动换算率（只读展示，保存时后端同口径回算）。
     * 不再反写「数量」——数量由填单人自己填。
     */
    onFloatQuantityChange: (row) => {
      if (!row) return
      const m = materialOf(row)
      const base = baseQtyOf(row, m, units.value)
      if (base != null) {
        row.baseQuantity = base
        if ('basic_quantity' in row) row.basic_quantity = base
      }
      row.floatConversionRate = floatRateOf(row, m, units.value)
    },
    /** 主数量（数量/基本单位数量）变化：填了浮动单位就同步重算浮动换算率 */
    onMainQtyChange: (row) => {
      if (!row || row.floatUnitId == null) return
      const m = materialOf(row)
      const base = baseQtyOf(row, m, units.value)
      if (base != null) {
        row.baseQuantity = base
        if ('basic_quantity' in row) row.basic_quantity = base
      }
      row.floatConversionRate = floatRateOf(row, m, units.value)
    }
  }
}
