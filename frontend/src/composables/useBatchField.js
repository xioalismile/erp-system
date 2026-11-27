import { ref } from 'vue'
import { getBatchRuleInfo } from '@/api/inventory/inventory'

/**
 * 批次字段联动
 *
 * 口径（2026-11-27 调整：取消「批次功能总开关」）：
 *  - 只看物料档案「批次管理」：开了就该行显示并必填，没开完全不显示；
 *  - 单据明细里没有任何批次管理物料时，整张单不显示「批次号」列；
 *  - 规则「自动生成批次号」开启 → 选物料后自动带出下一个可用批次号；
 *  - 规则「允许修改批次号」关闭 → 字段只读。
 *
 * 页面用法：
 *   const { loadBatchSetting, applyBatchRule, batchVisible, batchDisabled } = useBatchField()
 *   const shownColumns = computed(() => detailColumns.filter(c => c.prop !== 'batchNo' || hasLotControlRow(form.items)))
 */
const defaultAllowModify = ref(true)

export function useBatchField() {
  async function loadBatchSetting() {
    try {
      const res = await getBatchRuleInfo()
      const d = res?.data || res || {}
      defaultAllowModify.value = d.allowModify !== false
    } catch (e) {
      // 取不到规则时保持默认：允许修改
    }
  }

  /**
   * 选物料后套用批次规则：回填批次管理标志、允许修改标志，并在规则开启自动生成且当前为空时带出批次号。
   * @param {Object} row 当前明细行
   * @param {Array} allItems 当前单据全部明细（用于生成时跳过已用号）
   */
  async function applyBatchRule(row, allItems) {
    if (!row || !row.materialId) return
    const used = (allItems || [])
      .filter(r => r && r !== row && r.batchNo)
      .map(r => String(r.batchNo).trim())
      .filter(Boolean)
    try {
      const res = await getBatchRuleInfo({ materialId: row.materialId, used: used.join(',') })
      const d = res?.data || res || {}
      row._isLotControl = d.lotControl ? 1 : 0
      row._batchAllowModify = d.allowModify !== false
      if (d.lotControl && d.batchNo && !row.batchNo) {
        row.batchNo = d.batchNo
      }
    } catch (e) {
      // 取不到规则时保持旧行为：字段可编辑、不自动生成
    }
  }

  /** 该行是否显示批次号：只看物料档案批次管理 */
  function batchVisible(row) {
    return !!row && row._isLotControl === 1
  }

  /** 该行批次号是否只读（单据只读 / 规则不允许修改） */
  function batchDisabled(row, readOnly = false) {
    if (readOnly) return true
    const canModify = row && row._batchAllowModify !== undefined
      ? row._batchAllowModify !== false
      : defaultAllowModify.value
    return !canModify
  }

  /** 明细里是否存在批次管理物料：决定整张单是否显示「批次号」列 */
  function hasLotControlRow(items) {
    return Array.isArray(items) && items.some(r => r && r._isLotControl === 1)
  }

  /**
   * 是否隐藏整张单的「批次号」列：
   * 只有每一行都明确是「非批次管理」时才隐藏；有任意一行还没回填过物料标志（未接入本机制的页面）
   * 或存在批次管理物料时保持显示，避免把还在用的列藏掉。
   */
  function batchColumnHidden(items) {
    if (!Array.isArray(items) || items.length === 0) return false
    const anyLot = items.some(r => r && r._isLotControl === 1)
    if (anyLot) return false
    const anyUnknown = items.some(r => r && r._isLotControl === undefined)
    return !anyUnknown
  }

  /** 载入单据时回填批次管理标志（物料列表已加载的场景） */
  function backfillLotControl(items, materials) {
    if (!Array.isArray(items) || !Array.isArray(materials)) return
    const map = new Map(materials.map(m => [m.materialId, m]))
    items.forEach(row => {
      if (!row || row.materialId == null) return
      if (row._isLotControl === undefined) {
        const m = map.get(row.materialId)
        if (m) row._isLotControl = m.isLotControl ? 1 : 0
      }
      if (row._batchAllowModify === undefined) {
        row._batchAllowModify = defaultAllowModify.value
      }
    })
  }

  return {
    defaultAllowModify,
    loadBatchSetting,
    applyBatchRule,
    batchVisible,
    batchDisabled,
    hasLotControlRow,
    batchColumnHidden,
    backfillLotControl
  }
}
