// ====================================================================
// 列表页「明细模式」自动接线器（2026-10-12 铺开批次）
//
// 页面零代码接入：列表页通用壳按 storageKey 在 config/billDetailView.js 查到配置后，
// 调 createAutoDetailView() 得到明细模式运行时（列清单/取数/导出/跳转/筛选映射），
// 壳内已有明细表格渲染 / 行级分页 / 合计行 / 勾选导出 / 单号跳转 通用实现。
//
// 口径与销售订单试点完全一致（登记表《列表页汇总明细双视图_立项登记表》）：
//   - 列清单 = 后端明细报表 /columns（表头字段 + 明细行字段 + 自定义字段 + 行级回写列）
//   - 默认显示列按通用规则推导：单号(固定左) → 单据日期 → 客户/供应商 → 商品编号 →
//     商品名称 → 单位 → 数量 → 金额 → 全部行级回写列 → 状态；其余默认隐藏、列设置可勾
//   - 筛选映射从 /columns 的筛选元数据自动推导（单号文本 / 状态 / 日期区间 /
//     客户 / 供应商 / 业务员），与汇总页同一套筛选条件
// ====================================================================
import { ref, computed } from 'vue'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

/** 单元格渲染类型映射（通用规则，勿按单据特判） */
function mapColType (c) {
  if (c.fixed === 'left') return 'billNo'
  if (c.key === 'status') return 'status'
  if (c.type === 'image') return 'image'          // 图片列 → 缩略图渲染件（此前漏映射，图片显示成了 URL 文本）
  if (/^row\w*Status$/.test(c.key || '')) return 'writeback'
  if (c.type === 'amount') return 'amount'
  // 数量/整数列：合计行按数值列回值（此前一律返回空类型 → 自动接线页「数量合计」为空）
  if (c.type === 'number' || c.type === 'int') return 'number'
  return ''
}

function colMinWidth (c) {
  if (c.fixed === 'left') return 150
  if (c.type === 'image') return 80
  if (['customerName', 'supplierName', 'materialName'].includes(c.key)) return 140
  if (/^row\w*Status$/.test(c.key || '')) return 110
  if (c.type === 'amount' || c.type === 'number' || c.type === 'int') return 100
  if (c.type === 'date' || c.type === 'datetime') return 100
  return 100
}

/** 从 /columns 元数据推导筛选入参键（单号/状态/日期/客户/供应商/业务员） */
function resolveFilterKeys (filters, docNoKey) {
  const out = {}
  for (const f of filters || []) {
    const pk = f.paramKeys || {}
    if (f.defaultCurrentMonth) { out.dateFrom = pk.from; out.dateTo = pk.to; continue }
    if (f.optionSource === 'status') { out.status = pk.value; continue }
    if (f.optionSource === 'customer') { out.customer = pk.value; continue }
    if (f.optionSource === 'supplier') { out.supplier = pk.value; continue }
    if (f.optionSource === 'employee') { out.employee = pk.value; continue }
    if (f.type === 'text' && f.key === docNoKey) out.keyword = pk.value
  }
  return out
}

/**
 * @param cfg    billDetailView.js 里该页的配置 { apiBase, linkPrefix, exportName }
 * @param getKeyword () => 汇总页关键字（壳传入）
 * @param getModel   () => 汇总页筛选模型（壳传入，标准维度 status/startDate/endDate/customerId/supplierId/employeeId）
 */
export function createAutoDetailView (cfg, getKeyword, getModel) {
  const api = createBillDetailReportApi(cfg.apiBase)
  const raw = ref(null) // null = 加载中；[] = 加载失败（明细能力静默降级）
  const rawFilters = ref([])

  api.getColumns()
    .then(res => {
      raw.value = res?.columns || []
      rawFilters.value = res?.filters || []
    })
    .catch(() => { raw.value = []; rawFilters.value = [] })

  const columnList = computed(() => raw.value || [])
  const docNoKey = computed(() => (columnList.value.find(c => c.fixed === 'left') || {}).key || '')
  const dateFilterKey = computed(() => ((rawFilters.value || []).find(f => f.defaultCurrentMonth) || {}).key || '')
  const filterKeys = computed(() => resolveFilterKeys(rawFilters.value, docNoKey.value))

  /** 默认显示列 key 集合（与销售订单试点同一套口径）：
   *  单号 + 单据日期 + 客户/供应商 + 图片 + 商品列 + 单位/数量/仓库/单价/金额
   *  + 行级回写列（发货/收款/开票/生产情况等）+ 物料/单据自定义字段 + 单据状态
   *
   *  【2026-10-19 修复"明细字段比汇总还少"】白名单是"商品型单据"形状（商品编码/名称/数量/金额…），
   *  财务收/付款单、预收预付、费用分摊、工资核算这类**无商品列**的单据，命中白名单的只有
   *  单号/日期/客户/状态 4 列，其余业务列（收款金额/实收金额/结算方式/收款人/账户…）全部默认隐藏
   *  → 明细模式比汇总模式字段还少（用户实报）。这类单据改为按**后端明细报表 /columns 的
   *  visible 标志**兜底展开（报表口径：默认全部显示、不需要的用列设置关，2026-09-22 用户口径一致）；
   *  商品型单据（销售/采购/库存/生产等试点页面）维持原骨架不动（列布局已按用户口径确认）。 */
  const defaultVisibleKeys = computed(() => {
    const cols = columnList.value
    const keys = new Set()
    if (docNoKey.value) keys.add(docNoKey.value)
    if (dateFilterKey.value) keys.add(dateFilterKey.value)
    // 「明细类型」（多明细合并报表）：行来源区分列，默认显示
    if (cols.some(c => c.key === 'detailType')) keys.add('detailType')
    for (const k of ['customerName', 'supplierName', 'image', 'materialCode', 'materialName',
      'specification', 'unitName', 'quantity', 'warehouseName', 'itemWarehouseName', 'unitPrice', 'amount']) {
      if (cols.some(c => c.key === k)) keys.add(k)
    }
    // 行级回写列与自定义字段默认一律显示（用户 2026-10-14 口径：明细模式必须看到回写值与自定义字段）
    for (const c of cols) {
      const k = c.key || ''
      if (/^row\w*Status$/.test(k) || /^cf_/.test(k)) keys.add(k)
    }
    if (cols.some(c => c.key === 'status')) keys.add('status')
    // 非商品型单据：按后端列配置的默认可见标志展开（visible !== false = 后端未把它列进隐藏清单）
    const isGoodsBill = cols.some(c => ['materialCode', 'materialName'].includes(c.key || ''))
    if (!isGoodsBill) {
      for (const c of cols) {
        if ((c.key || '') && c.visible !== false) keys.add(c.key)
      }
    }
    return keys
  })

  /** 默认排列骨架（与销售订单试点逐位对齐）：
   *  单号 → 单据日期 → 客户 → 图片 → 商品编号 → 商品名称 → 规格 → [物料自定义字段]
   *  → 单位/数量/仓库/单价/金额 → [行级回写列] → [单据自定义字段] → 单据状态 */
  const ORDER_SKELETON = ['image', 'materialCode', 'materialName', 'specification',
    'unitName', 'quantity', 'warehouseName', 'itemWarehouseName', 'unitPrice', 'amount']
  function defaultOrder(c, idx) {
    const k = c.key || ''
    if (k === docNoKey.value) return 0
    if (k === dateFilterKey.value) return 1
    if (k === 'customerName' || k === 'supplierName') return 2
    if (k === 'salerName' || k === 'buyerName') return 2.5
    if (k === 'detailType') return 2.8           // 明细类型：紧排客户/业务员之后，先看清行来源
    if (/^cf_item_/.test(k)) return 6.6        // 物料自定义字段：紧排规格之后
    if (/^row\w*Status$/.test(k)) return 20    // 行级回写列：金额列之后
    if (/^cf_/.test(k)) return 20.5            // 单据自定义字段：回写列之后、状态之前
    if (k === 'status') return 21
    const i = ORDER_SKELETON.indexOf(k)
    if (i >= 0) return 3 + i
    return 30 + idx
  }

  /** 列定义（壳的明细表格 + 列设置抽屉共用）；已按默认骨架排好序 */
  const columns = computed(() => columnList.value
    .map((c, idx) => ({
      prop: c.key,
      label: c.label,
      type: mapColType(c),
      minWidth: colMinWidth(c),
      align: c.type === 'image' ? 'center' : (['amount', 'number', 'int'].includes(c.type) ? 'right' : 'left'),
      sortable: !!c.sortable,
      fixed: c.key === 'status' ? 'right' : (c.fixed || false),
      defaultHidden: !defaultVisibleKeys.value.has(c.key),
      _order: defaultOrder(c, idx)
    }))
    .filter(c => c.prop)
    .sort((a, b) => a._order - b._order)
    .map(({ _order, ...c }) => c))

  /** 汇总页筛选 → 明细取数 f_* 入参 */
  function paramsBuilder () {
    const p = {}
    const fk = filterKeys.value || {}
    const put = (key, v) => {
      if (key && v !== undefined && v !== null && String(v).trim() !== '') p[key] = String(v).trim()
    }
    put(fk.keyword, getKeyword && getKeyword())
    const m = (getModel && getModel()) || {}
    put(fk.status, m.status)
    put(fk.dateFrom, m.startDate)
    put(fk.dateTo, m.endDate)
    put(fk.customer, m.customerId)
    put(fk.supplier, m.supplierId)
    put(fk.employee, m.employeeId)
    return p
  }

  async function fetcher ({ page, size, params }) {
    return await api.query({ page, size, ...params })
  }

  /** 导出：壳透传 params（已合并明细模式筛选条件与当前排序）时直接使用，保证导出与屏幕所见一致 */
  async function exporter ({ cols, itemIds, params }) {
    const p = { cols, ...(params || paramsBuilder()) }
    if (itemIds && itemIds.length) p._itemIds = itemIds.join(',')
    const blob = await api.exportFile(p)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url
    a.download = `${cfg.exportName}_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  }

  function linkBuilder (row) {
    // linkPrefix 为空 = 该单据无独立编辑页（如工序工资核算），单号不可点
    if (!row || !row._docId || !cfg.linkPrefix) return ''
    return cfg.linkPrefix + row._docId
  }

  return { columns, fetcher, exporter, linkBuilder, paramsBuilder }
}
