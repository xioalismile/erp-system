/**
 * 单据明细行库存展示（库存量 / 可用量 / 预计可用量）
 *
 * 数据来源：GET /api/inventory/material-stock-summary
 * 口径：按物料跨仓库合计（不区分仓库）；服务端按"可见仓库"数据权限过滤；
 *       预计可用量 = 可用量 - 生效销售订单未发货占用（负数=接单缺口）。
 *
 * 性能约定（用户明确要求：不得影响单据加载数据与打开速度）：
 * - 不进入单据 loadDetail 的 await 链路：明细渲染后由 watch 异步触发；
 * - 明细行物料集合去重后一次批量请求（不逐行请求，避免 N+1）；
 * - 250ms 防抖 + 请求序号防乱序（旧响应回来丢弃，不覆盖新数据）；
 * - 接口失败静默置空（GET 请求本身也不弹窗），不抛错、不打扰用户。
 */
import { watch, onMounted, onBeforeUnmount } from 'vue'
import { getMaterialStockSummary } from '@/api/inventory/inventory'

/**
 * 单据明细行库存展示列的统一定义
 * 各单据页直接把该数组展开到自己的 detailColumns 中即可（列设置抽屉自动可见/可排序）
 * type='billStock' 为统一渲染分支：4 位小数；无物料为空白；负数（预计可用量缺口）显示红字
 */
export const BILL_STOCK_COLUMNS = [
  { prop: 'stockQty', label: '库存量', width: 100, align: 'right', type: 'billStock' },
  { prop: 'stockAvailQty', label: '可用量', width: 100, align: 'right', type: 'billStock' },
  { prop: 'stockExpectedQty', label: '预计可用量', width: 110, align: 'right', type: 'billStock' }
]

/**
 * 明细行库存联动
 *
 * @param {Function} itemsGetter 返回明细行数组的函数（如 () => form.items）
 * @param {Object}   [options]
 * @param {Number}   [options.debounceMs=250]   防抖毫秒数
 * @param {String}   [options.materialField]    物料ID字段名，默认 materialId
 *                                              （打样单子件清单用 childMaterialId）
 *
 * 注意：内部在 onMounted 之后才建立 watch，因此调用点可以写在 form 声明之前，
 *      不会因为 setup 期间访问 form 触发 TDZ（ReferenceError: Cannot access before initialization）。
 */
export function useBillLineStock(itemsGetter, options = {}) {
  const debounceMs = options.debounceMs ?? 250
  const materialField = options.materialField || 'materialId'
  let timer = null
  let seq = 0
  let stopWatch = null

  const idOf = row => (row && row[materialField]) || null

  /** 明细行物料集合指纹：物料变化才触发重新查询 */
  function buildKey() {
    const items = itemsGetter() || []
    return items.map(it => idOf(it) || '').join(',')
  }

  /** 未选物料的行恢复空白（不显示 0，避免误导） */
  function blankRows(rows) {
    for (const row of rows) {
      if (row && !idOf(row)) {
        row.stockQty = null
        row.stockAvailQty = null
        row.stockExpectedQty = null
      }
    }
  }

  async function load() {
    const rows = itemsGetter() || []
    blankRows(rows)
    const ids = [...new Set(rows.map(idOf).filter(Boolean))]
    if (!ids.length) return

    const mySeq = ++seq
    let list = null
    try {
      list = await getMaterialStockSummary(ids)
    } catch (_) {
      list = null
    }
    // 已有更新的请求发出：丢弃本次结果，避免旧数据覆盖新数据
    if (mySeq !== seq) return

    if (!list) {
      // 接口失败：保持空白，静默（不弹诊断卡）
      for (const row of itemsGetter() || []) {
        if (row && idOf(row)) {
          row.stockQty = null
          row.stockAvailQty = null
          row.stockExpectedQty = null
        }
      }
      return
    }

    const map = new Map()
    for (const s of list) {
      if (s && s.materialId != null) map.set(String(s.materialId), s)
    }
    for (const row of itemsGetter() || []) {
      const mid = idOf(row)
      if (!mid) continue
      const s = map.get(String(mid))
      // 命中物料但库存表无记录 = 真实 0；未命中（物料已被删）也按 0 展示
      row.stockQty = s ? Number(s.quantity ?? 0) : 0
      row.stockAvailQty = s ? Number(s.availableQuantity ?? 0) : 0
      row.stockExpectedQty = s ? Number(s.expectedAvailableQuantity ?? 0) : 0
    }
  }

  function schedule() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      load()
    }, debounceMs)
  }

  // onMounted 之后才建立 watch：保证 itemsGetter 依赖的 form 已完成初始化（避免 TDZ 报错）
  onMounted(() => {
    stopWatch = watch(() => buildKey(), schedule)
    load()
  })

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
    if (stopWatch) stopWatch()
    // 递增序号：卸载后迟到的响应不再写回行数据
    seq++
  })
}
