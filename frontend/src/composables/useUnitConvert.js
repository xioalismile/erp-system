/**
 * 多单位换算（2026-11-10 重构：物料 1 对多换算关系版 · 单据明细行公共机制）
 *
 * 口径（与后端 UnitConversionService 同源，最终以后端落库为准）：
 * - 换算率语义：1 个基本单位 = X 个该辅单位（物料档案换算行 conversionRate）；
 *   因此「辅单位 → 基本单位」的折算率 = 1 / X；
 * - 单据行录「基本单位」→ 副单位显示第一个「固定」换算行的辅单位，副单位数量 = 数量 × X；
 * - 单据行录「某辅单位」→ 副单位显示基本单位，副单位数量 = 数量折回基本单位（数量 × 1/X）；
 * - 「浮动」换算行：全局开关「启用浮动单位」（sys.float.unit.enabled）开启时，
 *   单据行允许手填浮动数量（floatUnitId / floatQuantity，随行保存）；
 * - 物料未开启辅计量 / 换算行未加载：副单位 = 基本单位、副单位数量 = 数量（旧行为）。
 *
 * 缓存：换算行按物料缓存（模块级 reactive Map）、浮动开关缓存（模块级 ref），
 * 登出/切换公司由 sessionCacheReset → clearUnitConvertCache() 统一清空，禁止跨用户/跨公司残留。
 */
import { reactive, ref } from 'vue'
import { listMaterialUnitConversionsBatch } from '@/api/basedata/material-unit-conversion'
import { getFloatUnitEnabled } from '@/api/system/sysConfig'

/** { materialId: 换算行[] } —— reactive：只读单元格直接读它，批量回填后自动刷新 */
const conversionsCache = reactive(new Map())
/** 全局开关「启用浮动单位」（null = 未加载；null/false 都按关闭处理 = 旧行为） */
const floatUnitEnabledState = ref(null)
let floatUnitLoading = null

/** 清空换算行缓存与浮动开关缓存（登出/切换公司时由 sessionCacheReset 调用） */
export function clearUnitConvertCache() {
  conversionsCache.clear()
  floatUnitEnabledState.value = null
  floatUnitLoading = null
}

/** 全局开关「启用浮动单位」响应式引用：单据页浮动两列的整列显隐直接绑它 */
export const floatUnitEnabled = floatUnitEnabledState

/** 加载全局开关（幂等；失败按关闭处理，不阻塞页面渲染） */
export async function loadFloatUnitEnabled() {
  if (floatUnitEnabledState.value != null) return floatUnitEnabledState.value
  if (!floatUnitLoading) {
    floatUnitLoading = getFloatUnitEnabled()
      .then(v => { floatUnitEnabledState.value = !!v })
      .catch(() => { floatUnitEnabledState.value = false })
      .finally(() => { floatUnitLoading = null })
  }
  return floatUnitLoading
}

/**
 * 批量拉取物料换算行（已缓存的物料跳过；无配置的物料缓存空数组，避免反复重复请求；
 * 请求失败不落缓存，行数据变化时可重试）
 * @param {Array<number>} materialIds
 * @returns {Map} 换算行缓存
 */
export async function loadConversions(materialIds) {
  const ids = [...new Set((materialIds || []).filter(id => id != null && !conversionsCache.has(id)))]
  if (ids.length) {
    ids.forEach(id => conversionsCache.set(id, []))
    try {
      const grouped = (await listMaterialUnitConversionsBatch(ids)) || {}
      Object.keys(grouped).forEach(k => {
        const id = Number(k)
        if (conversionsCache.has(id)) conversionsCache.set(id, grouped[k] || [])
      })
    } catch (e) {
      console.warn('[unit-convert] 换算行批量加载失败（本次不缓存，可重试）:', e)
      ids.forEach(id => conversionsCache.delete(id))
    }
  }
  return conversionsCache
}

/**
 * 作废某个物料的换算行缓存（2026-11-11）：物料档案「辅计量换算」保存成功后调用。
 * 此前缓存只在登出/切换公司时清空，导致改完档案不刷新浏览器再进单据仍按旧换算行显示。
 * @param {number|string} materialId
 */
export function invalidateConversions(materialId) {
  if (materialId == null || materialId === '') return
  conversionsCache.delete(materialId)
  conversionsCache.delete(Number(materialId))
}

/**
 * 用物料档案详情里的换算行覆盖缓存（2026-11-11）：
 * 单据打开时会按行批量取物料详情（详情自带最新换算行），以此为准刷新，
 * 避免"档案已改、本会话缓存的旧换算行还在"。
 * @param {number|string} materialId
 * @param {Array} rows 物料详情返回的 unitConversions；非数组（老接口/列表行）不动缓存
 */
export function syncConversions(materialId, rows) {
  if (materialId == null || materialId === '' || !Array.isArray(rows)) return
  conversionsCache.set(Number(materialId), rows.filter(Boolean))
}

/** 物料的换算行（未开启辅计量 / 缓存未加载返回空数组） */
function conversionRowsOf(material) {
  if (!material || material.materialId == null || !material.multiUnitEnabled) return []
  return conversionsCache.get(material.materialId) || []
}

/** 物料上配置过的可用单位：基本、库存、采购、销售、生产、辅计量（旧行为保留） */
export function materialUnitIds(material) {
  if (!material) return []
  return [material.baseUnitId, material.stockUnitId, material.purchaseUnitId,
    material.saleUnitId, material.productionUnitId, material.auxUnitId]
    .filter(Boolean)
}

/**
 * 单位候选：基本单位 + 该物料换算行的单位（2026-11-10 新口径）。
 * 换算行未加载 / 未开启辅计量 / 无换算行配置 → 旧行为（按物料配置过的单位过滤，未配置退回全部），
 * 保证尚未接入 loadConversions 的旧调用点（盘点等）行为不变。
 */
export function unitOptionsFor(material, allUnits) {
  const list = allUnits || []
  if (!material) return list
  const rows = conversionRowsOf(material)
  if (!conversionsCache.has(material.materialId) || !rows.length) {
    const ids = materialUnitIds(material)
    if (!ids.length) return list
    const picked = list.filter(u => ids.includes(u.unitId))
    return picked.length ? picked : list
  }
  const ids = [material.baseUnitId, ...rows.map(r => r.unitId)].filter(Boolean)
  const picked = []
  ids.forEach(id => {
    const u = list.find(x => x.unitId === id)
    if (u && !picked.includes(u)) picked.push(u)
  })
  return picked.length ? picked : list
}

/**
 * 旧口径兜底：物料档案辅计量字段 / 同单位组换算率相除（换算行未覆盖到时保持旧版行为）。
 * @returns {number} 1 个所选单位 = rate 个基本单位
 */
function legacyRate(material, unitId, allUnits) {
  if (!material || !unitId) return 1
  const baseUnitId = material.baseUnitId
  if (!baseUnitId || unitId === baseUnitId) return 1

  // ① 物料档案的辅计量单位：1 基本 = auxRate 辅 → 1 辅 = 1 / auxRate 基本
  if (material.multiUnitEnabled && unitId === material.auxUnitId) {
    const aux = Number(material.auxConversionRate)
    if (aux > 0) return Number((1 / aux).toFixed(8))
  }

  // ② 同一单位组：按相对组基准的换算率相除
  const list = allUnits || []
  const unit = list.find(u => u.unitId === unitId)
  const base = list.find(u => u.unitId === baseUnitId)
  if (unit && base && unit.unitGroup && unit.unitGroup === base.unitGroup) {
    const ur = Number(unit.conversionRate)
    const br = Number(base.conversionRate)
    if (ur > 0 && br > 0) return Number((ur / br).toFixed(8))
  }
  return 1
}

/**
 * 「所选单位 → 基本单位」的换算信息（2026-11-10 新增）。
 * @param {Object} material
 * @param {number} unitId 所选单位
 * @param {Array} [allUnits] 全部单位（旧口径兜底用，可选）
 * @returns {{rate:number, mode:(string|null), row:(Object|null)}}
 *   rate：1 个所选单位 = rate 个基本单位；mode：换算方式（固定/浮动；非换算行单位为 null）；row：命中的换算行
 */
export function rateInfoOf(material, unitId, allUnits) {
  if (!material || !unitId) return { rate: 1, mode: null, row: null }
  const baseUnitId = material.baseUnitId
  if (!baseUnitId || unitId === baseUnitId) return { rate: 1, mode: null, row: null }
  const rows = conversionRowsOf(material)
  const row = rows.find(r => r.unitId === unitId)
  if (row) {
    const archive = Number(row.conversionRate)
    const rate = archive > 0 ? Number((1 / archive).toFixed(8)) : 1
    return { rate, mode: row.rateMode || '固定', row }
  }
  return { rate: legacyRate(material, unitId, allUnits), mode: null, row: null }
}

/**
 * 计算「所选单位 → 基本单位」的换算率（旧导出签名保留，盘点等旧调用点不炸）。
 * @returns {number} 1 个所选单位 = rate 个基本单位
 */
export function resolveUnitRate(material, unitId, allUnits) {
  return rateInfoOf(material, unitId, allUnits).rate
}

/**
 * 「副单位」对侧规则（2026-11-10 新增）：
 * - 录基本单位（或未选单位）→ 副单位 = 第一个「固定」换算行的辅单位，副单位数量 = 数量 × 档案换算率；
 * - 录某辅单位 → 副单位 = 基本单位，副单位数量 = 数量 × (1 / 档案换算率)（折回基本单位）；
 * - 未开启辅计量 / 换算行未加载 / 无固定行 → 副单位 = 基本单位，副单位数量 = 数量。
 * @param {Object} material
 * @param {number} unitId 行所选单位
 * @param {Array} [allUnits] 全部单位（单位名解析用，可选）
 * @returns {{unitId, unitName, rate}} rate：副单位数量 = 行数量 × rate
 */
export function subUnitOf(material, unitId, allUnits, preferredSubUnitId) {
  if (!material) return { unitId: null, unitName: '', rate: 1 }
  const baseUnitId = material.baseUnitId
  const nameOf = (id) => {
    if (id == null) return ''
    if (id === baseUnitId && material.baseUnitName) return material.baseUnitName
    const u = (allUnits || []).find(x => x.unitId === id)
    return u ? u.unitName : ''
  }
  const rows = conversionRowsOf(material)
  if (rows.length && baseUnitId) {
    // 单据行手选副单位优先（2026-11-10）：选了就按所选单位的档案率算，清空回落到默认规则
    if (preferredSubUnitId != null) {
      if (preferredSubUnitId === baseUnitId) {
        return { unitId: baseUnitId, unitName: nameOf(baseUnitId), rate: 1 }
      }
      const picked = rows.find(r => r.unitId === preferredSubUnitId)
      if (picked) {
        const archive = Number(picked.conversionRate)
        return { unitId: picked.unitId, unitName: nameOf(picked.unitId), rate: archive > 0 ? archive : 1 }
      }
    }
    if (!unitId || unitId === baseUnitId) {
      // 录基本单位 → 第一个「固定」换算行的辅单位
      const fixed = rows.find(r => r.rateMode !== '浮动')
      if (fixed) {
        const archive = Number(fixed.conversionRate)
        return { unitId: fixed.unitId, unitName: nameOf(fixed.unitId), rate: archive > 0 ? archive : 1 }
      }
    } else {
      // 录某辅单位 → 副单位显示基本单位，数量折回基本单位
      const row = rows.find(r => r.unitId === unitId)
      if (row) {
        const archive = Number(row.conversionRate)
        const rate = archive > 0 ? Number((1 / archive).toFixed(8)) : 1
        return { unitId: baseUnitId, unitName: nameOf(baseUnitId), rate }
      }
    }
  }
  return { unitId: baseUnitId || null, unitName: nameOf(baseUnitId), rate: 1 }
}

/** 「浮动」换算行：全局开关开启时单据行可手填浮动数量的单位候选（mode=浮动 的行） */
export function floatingRowsOf(material) {
  return conversionRowsOf(material).filter(r => r.rateMode === '浮动')
}

/** 「固定」换算行：单据行「副单位」可手选的候选（mode=固定，或未标方式的行） */
export function fixedRowsOf(material) {
  return conversionRowsOf(material).filter(r => r.rateMode !== '浮动')
}

/** 单据行单位/数量变化后回填换算率快照与折算数量（折算数量仅用于界面提示，库存以后端为准） */
export function applyUnitToRow(row, material, allUnits) {
  if (!row) return row
  const rate = resolveUnitRate(material, row.unitId, allUnits)
  row.conversionRate = rate
  const qty = Number(row.quantity)
  if (Number.isFinite(qty)) {
    row.baseQuantity = Number((qty * rate).toFixed(4))
  }
  return row
}
