import { ref, computed, onBeforeUnmount } from 'vue'
import { getFinanceBoard, getSaleBoard, getInventoryBoard } from '@/api/dashboard/dataBoard'
import { getOperationBoard } from '@/api/dashboard/operationBoard'
import { listSaleOrder } from '@/api/sale/saleOrder'
import { listPurchaseOrder } from '@/api/purchase/purchaseOrder'
import { pageWorkOrder } from '@/api/production/workOrder'
import { listInventory } from '@/api/inventory/inventory'

export const PERIODS = [
  { key: 'day', label: '今日' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'quarter', label: '本季' },
  { key: 'year', label: '本年' }
]

export function toNum(v) {
  if (typeof v === 'number' && Number.isFinite(v)) return v
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

export function fmtMoney(v, digits = 2) {
  const n = toNum(v)
  if (n === null) return '暂无数据'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

export function fmtInt(v) {
  const n = toNum(v)
  if (n === null) return '暂无数据'
  return Math.round(n).toLocaleString('zh-CN')
}

export function fmtPercent(v, digits = 1) {
  const n = toNum(v)
  if (n === null) return '暂无数据'
  return n.toFixed(digits) + '%'
}

/** 日期只留 yyyy-MM-dd，兼容带 T 的时间串；空值留空（守则：不得用占位符） */
export function fmtDate(v) {
  if (!v) return ''
  const s = String(v)
  return s.length >= 10 ? s.slice(0, 10) : s
}

/** 单据状态 → 颜色类（按全站既有状态色） */
export function statusClass(status) {
  const s = String(status || '')
  if (s === '草稿') return 'draft'
  if (s === '待审核' || s === '待审批') return 'pending'
  if (s === '已生效' || s === '已确认' || s === '执行中' || s === '已下达') return 'approved'
  if (s === '已完工' || s === '全部入库' || s === '已完成') return 'done'
  if (s === '部分入库') return 'partial'
  if (s === '已作废') return 'void'
  if (s === '已结案' || s === '已关闭') return 'closed'
  return 'plain'
}

function ratio(part, total) {
  const a = toNum(part)
  const b = toNum(total)
  if (a === null || b === null || b === 0) return null
  return (a / b) * 100
}

function buildKpis(d) {
  const f = d.finance || {}
  const s = d.sale || {}
  const inv = d.inventory || {}
  const op = d.operation || {}
  const pro = op.production || {}
  const pur = op.purchase || {}
  const qc = op.quality || {}

  return [
    { key: 'sale', label: '本期销售额', value: s.periodSale ?? f.periodRevenue, money: true, sub: '订单回款率 ' + fmtPercent(s.paybackRate), tone: 'primary' },
    { key: 'orders', label: '订单数', value: s.orderCount, int: true, sub: '客单价 ¥' + fmtMoney(s.avgOrderAmount), tone: 'primary' },
    { key: 'gross', label: '毛利率', value: f.grossMarginRate, percent: true, sub: '净利润 ¥' + fmtMoney(f.netProfit), tone: f.grossMarginRate !== null && f.grossMarginRate !== undefined && toNum(f.grossMarginRate) < 20 ? 'bad' : 'good' },
    { key: 'target', label: '目标达成', value: f.targetRate ?? s.targetRate, percent: true, sub: '目标 ¥' + fmtMoney(f.targetAmount ?? s.targetAmount), tone: 'primary' },
    { key: 'purchase', label: '本期采购额', value: pur.orderAmount, money: true, sub: '订单 ' + fmtInt(pur.orderCount) + ' 单 / 待处理 ' + fmtInt(pur.pendingCount), tone: 'primary' },
    { key: 'wip', label: '在制工单', value: pro.inProgress, int: true, sub: '延期 ' + fmtInt(pro.delayed) + ' 单 / 已完工 ' + fmtInt(pro.finished), tone: (toNum(pro.delayed) || 0) > 0 ? 'warn' : 'good' },
    { key: 'ar', label: '应收未收', value: f.arBalance, money: true, sub: '逾期 ¥' + fmtMoney(f.arOverdue), tone: (toNum(f.arOverdue) || 0) > 0 ? 'bad' : 'good' },
    { key: 'stock', label: '库存总额', value: inv.stockTotalAmount, money: true, sub: '呆滞 ¥' + fmtMoney(inv.staleAmount) + ' / 缺货 ' + fmtInt(inv.shortageSkuCount) + ' 个', tone: (toNum(inv.shortageSkuCount) || 0) > 0 ? 'warn' : 'good' },
    { key: 'qc', label: '批次合格率', value: qc.passRate, percent: true, sub: '本期检验 ' + fmtInt(qc.inspectCount) + ' 批 / 不合格 ' + fmtInt(qc.defectCount), tone: (toNum(qc.passRate) || 100) < 95 ? 'bad' : 'good' },
    { key: 'cash', label: '现金流净额', value: f.cashFlowNet, money: true, sub: '收 ¥' + fmtMoney(f.cashIn) + ' / 付 ¥' + fmtMoney(f.cashOut), tone: 'primary' }
  ]
}

function buildFlow(d) {
  const f = d.finance || {}
  const s = d.sale || {}
  const order = toNum(s.periodSale) ?? toNum(f.periodRevenue)
  const received = toNum(f.cashIn)
  const open = toNum(f.arBalance)
  const overdue = toNum(f.arOverdue)
  const risk = toNum(f.riskAmount)
  const max = Math.max(order || 0, received || 0, open || 0, overdue || 0, risk || 0) || 1
  return [
    { key: 'order', label: '订单额', value: order, width: ((order || 0) / max) * 100, rate: null, rateLabel: '本期已生效订单', tone: 'base' },
    { key: 'received', label: '已收款', value: received, width: ((received || 0) / max) * 100, rate: ratio(received, order), rateLabel: '订单回款率', tone: 'good' },
    { key: 'open', label: '应收未收', value: open, width: ((open || 0) / max) * 100, rate: null, rateLabel: '含历史未结清', tone: 'neutral' },
    { key: 'overdue', label: '逾期', value: overdue, width: ((overdue || 0) / max) * 100, rate: ratio(overdue, open), rateLabel: '逾期占应收', tone: 'warn' },
    { key: 'risk', label: '高风险', value: risk, width: ((risk || 0) / max) * 100, rate: ratio(risk, overdue), rateLabel: '高风险占逾期', tone: 'bad' }
  ]
}

function buildActions(d) {
  const s = d.sale || {}
  const inv = d.inventory || {}
  const op = d.operation || {}
  const qc = op.quality || {}
  const pro = op.production || {}
  const list = []

  ;(s.overdueByCustomer || []).forEach((r, i) => {
    const amount = toNum(r.overdueAmount)
    const days = toNum(r.overdueDays)
    if (amount === null || amount <= 0) return
    list.push({
      id: 'collect-' + i, type: 'collect', typeLabel: '催收',
      title: String(r.customerName ?? r.name ?? '未命名客户'), amount,
      meta: days === null ? '已逾期' : `已逾期 ${Math.round(days)} 天`,
      urgency: days !== null && days >= 90 ? 'bad' : days !== null && days >= 30 ? 'warn' : 'normal',
      path: '/finance/report/ar-summary', query: { overdueOnly: '1' }
    })
  })
  ;(inv.shortageList || []).forEach((r, i) => {
    const qty = toNum(r.shortageQuantity ?? r.quantity ?? r.amount)
    list.push({
      id: 'restock-' + i, type: 'restock', typeLabel: '补货',
      title: String(r.materialName ?? r.name ?? r.materialCode ?? '未命名物料'),
      amount: null, meta: qty === null ? '低于安全库存' : `缺口 ${fmtInt(qty)}`,
      urgency: 'warn', path: '/inventory/current', query: { belowMinStock: '1' }
    })
  })
  ;(inv.staleList || []).forEach((r, i) => {
    list.push({
      id: 'clear-' + i, type: 'clear', typeLabel: '清库',
      title: String(r.materialName ?? r.name ?? r.materialCode ?? '未命名物料'),
      amount: toNum(r.amount), meta: '超过 180 天无出库',
      urgency: 'normal', path: '/inventory/current', query: { staleOnly: '1' }
    })
  })
  const delayed = toNum(pro.delayed)
  if (delayed !== null && delayed > 0) {
    list.push({
      id: 'chase-delay', type: 'chase', typeLabel: '追交',
      title: `${Math.round(delayed)} 张工单已过计划完工日`, amount: null,
      meta: '需要重新排期或催料', urgency: 'bad',
      path: '/production/workorders', query: { delayed: '1' }
    })
  }
  const defect = toNum(qc.defectCount)
  if (defect !== null && defect > 0) {
    list.push({
      id: 'qc-defect', type: 'qc', typeLabel: '质检',
      title: `${Math.round(defect)} 批检验不合格待处理`, amount: null,
      meta: '返工、补料或报废', urgency: 'bad',
      path: '/production/qc-records', query: { result: '不合格' }
    })
  }
  const rank = { bad: 0, warn: 1, normal: 2 }
  return list.sort((a, b) => {
    const av = a.amount === null ? -1 : a.amount
    const bv = b.amount === null ? -1 : b.amount
    if (bv !== av) return bv - av
    return rank[a.urgency] - rank[b.urgency]
  })
}

function pickRecords(res) {
  if (Array.isArray(res)) return res
  if (res && Array.isArray(res.records)) return res.records
  if (res && res.data && Array.isArray(res.data.records)) return res.data.records
  return []
}

export function useCockpit(initial = {}) {
  const period = ref(initial.period || 'month')
  const loading = ref(false)
  const failed = ref([])
  const finance = ref({})
  const sale = ref({})
  const inventory = ref({})
  const operation = ref({})
  const updatedAt = ref('')
  const autoRefresh = ref(true)
  const activeFlow = ref('overdue')

  const saleOrders = ref([])
  const purchaseOrders = ref([])
  const workOrders = ref([])
  const inventoryRows = ref([])

  const selected = ref(null)

  let timer = null

  async function load() {
    loading.value = true
    failed.value = []
    const aggregates = [
      ['finance', getFinanceBoard],
      ['sale', getSaleBoard],
      ['inventory', getInventoryBoard],
      ['operation', getOperationBoard]
    ]
    const lists = [
      ['saleOrders', () => listSaleOrder({ current: 1, size: 8 })],
      ['purchaseOrders', () => listPurchaseOrder({ current: 1, size: 8 })],
      ['workOrders', () => pageWorkOrder({ current: 1, size: 8 })],
      ['inventoryRows', () => listInventory({ current: 1, size: 8 })]
    ]

    const results = await Promise.allSettled([
      ...aggregates.map(([, fn]) => fn(period.value)),
      ...lists.map(([, fn]) => fn())
    ])

    aggregates.forEach(([key], idx) => {
      const r = results[idx]
      if (r.status === 'fulfilled') {
        const val = r.value || {}
        if (key === 'finance') finance.value = val
        if (key === 'sale') sale.value = val
        if (key === 'inventory') inventory.value = val
        if (key === 'operation') operation.value = val
      } else {
        failed.value.push(key)
      }
    })

    const offset = aggregates.length
    lists.forEach(([key], idx) => {
      const r = results[offset + idx]
      const rows = r.status === 'fulfilled' ? pickRecords(r.value) : []
      if (key === 'saleOrders') saleOrders.value = rows.slice(0, 8)
      if (key === 'purchaseOrders') purchaseOrders.value = rows.slice(0, 8)
      if (key === 'workOrders') workOrders.value = rows.slice(0, 8)
      if (key === 'inventoryRows') inventoryRows.value = rows.slice(0, 8)
      if (r.status === 'rejected') failed.value.push(key)
    })

    const now = new Date()
    updatedAt.value = [now.getHours(), now.getMinutes(), now.getSeconds()]
      .map(n => String(n).padStart(2, '0')).join(':')
    loading.value = false
  }

  function startAuto() {
    stopAuto()
    if (autoRefresh.value) timer = setInterval(load, 30000)
  }
  function stopAuto() {
    if (timer) clearInterval(timer)
    timer = null
  }
  function setAuto(on) {
    autoRefresh.value = on
    if (on) startAuto()
    else stopAuto()
  }

  onBeforeUnmount(stopAuto)

  const dataset = computed(() => ({
    finance: finance.value, sale: sale.value, inventory: inventory.value, operation: operation.value
  }))
  const kpis = computed(() => buildKpis(dataset.value))
  const flow = computed(() => buildFlow(dataset.value))
  const actions = computed(() => buildActions(dataset.value))
  const actionAmount = computed(() => actions.value.reduce((sum, a) => sum + (a.amount || 0), 0))

  return {
    period, loading, failed, updatedAt, autoRefresh, activeFlow,
    sale, finance, inventory, operation,
    kpis, flow, actions, actionAmount,
    saleOrders, purchaseOrders, workOrders, inventoryRows,
    selected,
    load, startAuto, stopAuto, setAuto
  }
}
