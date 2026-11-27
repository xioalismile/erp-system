// 行级 BOM 通用逻辑（2026-11-06）
// 用途：销售各单据编辑页的明细行展示「BOM清单 / BOM版本」，按物料主 BOM 自动带出（只读，销售不可改）。
// 口径：默认BOM优先 → 已生效 → 最新；物料没有BOM则两列留空，不显示占位符。
import { ref } from 'vue'
import { listAllBom } from '@/api/basedata/bom'

const bomList = ref([])
let bomsLoaded = false

/** 明细行 BOM 两列定义（各销售编辑页 detailColumns 直接展开使用） */
export const LINE_BOM_COLUMNS = [
  { prop: 'bomCode', label: 'BOM清单', width: 180, type: 'text' },
  { prop: 'bomVersion', label: 'BOM版本', width: 100, type: 'text' }
]

export function useLineBom() {
  /** BOM 全量只加载一次（同一页面多行复用，避免每行一次请求） */
  async function ensureBoms() {
    if (bomsLoaded) return bomList.value
    try {
      const res = await listAllBom()
      bomList.value = Array.isArray(res) ? res : (res?.records || [])
      bomsLoaded = true
    } catch (e) {
      bomList.value = []
    }
    return bomList.value
  }

  /** 按行上物料带出主 BOM（只写行，不改单据其它字段） */
  async function fillRowBom(row) {
    if (!row || row.materialId == null) return
    const list = await ensureBoms()
    const boms = list.filter(b => b && String(b.materialId) === String(row.materialId))
    if (!boms.length) {
      row.bomId = null
      row.bomCode = ''
      row.bomVersion = ''
      return
    }
    const pick = boms.find(b => b.isDefault === 1 && b.status === '已生效')
      || boms.find(b => b.isDefault === 1)
      || boms.find(b => b.status === '已生效')
      || boms[0]
    row.bomId = pick.bomId
    row.bomCode = pick.bomCode || ''
    row.bomVersion = pick.bomVersion || ''
  }

  return { fillRowBom, LINE_BOM_COLUMNS, ensureBoms }
}
