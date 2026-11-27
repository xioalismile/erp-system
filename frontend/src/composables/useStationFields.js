/**
 * 进度看板「站点字段」定义与显隐/排序（车间排程看板 + 车间进度跟踪 两页共用）
 *
 * 站点设置面板（勾选显示/隐藏 + 拖动排序，按「登录人 + 所属公司」隔离保存）
 * 与站点卡片/悬停浮层渲染都从这里取字段定义，禁止两页各写一份导致字段认不上。
 *
 * 默认值 = 改造前现有显示（不配置时外观不变）；值为空的字段不占位（不出现 - / [object Object]）。
 */
import { computed, ref } from 'vue'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { loadCustomFieldDefinitions, batchLoadCustomFieldValues } from '@/composables/useCustomField'
import { tt } from '@/i18n'

/** 站点设置持久化 pageKey（两页共用同一份，切换页面设置不丢） */
export const STATION_PAGE_KEY = 'ProductionScheduleBoardStation'

function disp (v) {
  if (v === null || v === undefined) return ''
  const t = typeof v
  if (t === 'string') return v
  if (t === 'number' || t === 'boolean') return String(v)
  return ''
}
function fmtQty (v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? String(+n.toFixed(4)) : ''
}
function fmtAmt (v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : ''
}

const STATION_BASE_FIELDS = [
  { prop: 'operationCode', label: '工序编码', group: '工序信息', get: op => disp(op.operationCode) },
  { prop: 'lineNo', label: '工序号', group: '工序信息', get: op => (op.lineNo == null ? '' : String(op.lineNo)) },
  { prop: 'assigneeName', label: '负责人', group: '工序信息', get: op => disp(op.assigneeName) || tt('待派工') },
  { prop: 'planEndDate', label: '计划完工', group: '工序信息', get: op => disp(op.planEndDate) },
  { prop: 'actualEndDate', label: '实际完工', group: '工序信息', defaultHidden: true, get: op => disp(op.actualEndDate) },
  { prop: 'parentOperationName', label: '父工序', group: '工序信息', defaultHidden: true, get: op => disp(op.parentOperationName) },
  { prop: 'planQuantity', label: '计划数量', group: '工序信息', defaultHidden: true, get: op => fmtQty(op.planQuantity) },
  { prop: 'reportedQuantity', label: '已报工', group: '工序信息', defaultHidden: true, get: op => fmtQty(op.reportedQuantity) },
  { prop: 'approveQty', label: '检验合格', group: '工序信息', defaultHidden: true, get: op => fmtQty(op.approveQty) },
  { prop: 'receivedQty', label: '已入库', group: '工序信息', defaultHidden: true, get: op => fmtQty(op.receivedQty) },
  { prop: 'planAmount', label: '计划金额', group: '工序信息', defaultHidden: true, get: op => fmtAmt(op.planAmount) },
  { prop: 'confirmedAmount', label: '已确认金额', group: '工序信息', defaultHidden: true, get: op => fmtAmt(op.confirmedAmount) },
  { prop: 'materialName', label: '物料名称', group: '物料信息', defaultHidden: true, get: op => disp(op.materialName) },
  { prop: 'specification', label: '规格', group: '物料信息', defaultHidden: true, get: op => disp(op.specification) },
  { prop: 'orderNo', label: '工单号', group: '单据内容', views: ['workcenter'], get: op => disp(op.orderNo) },
  { prop: 'workCenterName', label: '工作中心', group: '单据内容', defaultHidden: true, get: op => disp(op.workCenterName) }
]

/**
 * @param {{ allVisibleByDefault?: boolean }} [opts]
 *   allVisibleByDefault：未配置时所有字段默认可见（车间进度跟踪页用：站点设置改为控制「悬停浮层」字段，
 *   默认不勾=全显示，避免用户点开站点设置看到"大部分没勾、但页面上其实都显示"的矛盾）。
 *   不传 = 旧行为（按字段自带 defaultHidden 决定），车间排程看板沿用。
 */
export function useStationFields (opts = {}) {
  const columnStore = useColumnConfigStore()
  const mcf = useMaterialCustomFields()

  const orderCfDefs = ref([])
  const orderCfValueMap = ref({})

  const materialCfDefs = computed(() => (mcf.allFields.value || []).map(f => ({
    prop: `mcf_${f.fieldKey}`,
    label: f.fieldLabel,
    group: '物料自定义字段',
    defaultHidden: true,
    get: (op) => {
      if (op.materialId == null) return ''
      const vals = mcf.valueMap.value[op.materialId]
      return vals ? disp(vals[f.fieldKey]) : ''
    }
  })))

  const fieldDefs = computed(() => [
    ...STATION_BASE_FIELDS,
    ...materialCfDefs.value,
    ...orderCfDefs.value
  ])

  const sortedFieldDefs = computed(() => {
    void columnStore.version
    const cfg = columnStore.getPageConfig(STATION_PAGE_KEY)
    return [...fieldDefs.value]
      .map((f, idx) => ({ ...f, _order: cfg[f.prop]?.order ?? (idx + 10000) }))
      .sort((a, b) => a._order - b._order)
  })

  function isVisible (prop) {
    void columnStore.version
    const cfg = columnStore.getPageConfig(STATION_PAGE_KEY)
    const c = cfg[prop]
    if (c) return c.visible !== false
    if (opts.allVisibleByDefault) return true
    const def = fieldDefs.value.find(f => f.prop === prop)
    return !(def && def.defaultHidden)
  }

  /**
   * 受站点设置控制的字段（勾选显示/隐藏 + 排序；view 决定 f.views 过滤）。
   * 用途：① 车间排程看板 → 任务块上显示的字段；② 车间进度跟踪 → 工序环的悬停浮层字段（该页传 allVisibleByDefault）。
   */
  function stationFieldsOf (op, view) {
    if (!op) return []
    const out = []
    for (const f of sortedFieldDefs.value) {
      if (f.views && !f.views.includes(view)) continue
      if (!isVisible(f.prop)) continue
      let v
      try {
        v = f.get(op)
      } catch (e) {
        v = ''
      }
      if (v === null || v === undefined || v === '') continue
      out.push({ prop: f.prop, label: f.label, value: String(v) })
    }
    return out
  }

  /** 悬停浮层固定展示的全部字段（不受站点设置显隐影响） */
  function allStationFieldsOf (op) {
    if (!op) return []
    const out = []
    for (const f of sortedFieldDefs.value) {
      let v
      try {
        v = f.get(op)
      } catch (e) {
        v = ''
      }
      if (v === null || v === undefined || v === '') continue
      out.push({ prop: f.prop, label: f.label, value: String(v) })
    }
    return out
  }

  async function loadOrderCfDefs () {
    try {
      const r = await loadCustomFieldDefinitions('production_order')
      orderCfDefs.value = Array.from(r.fieldMap.values()).map(f => ({
        prop: `wcf_${f.fieldKey}`,
        label: f.fieldLabel,
        group: '工单自定义字段',
        defaultHidden: true,
        get: (op) => {
          const vals = orderCfValueMap.value[op.orderId]
          return vals ? disp(vals[f.fieldKey]) : ''
        }
      }))
    } catch (e) {
      orderCfDefs.value = []
    }
  }

  /** 初始化：加载物料/工单自定义字段定义（站点设置面板的候选字段）。失败静默降级，不阻塞页面。 */
  async function init () {
    try { await mcf.init() } catch (e) { /* 降级：站点设置里不出现物料自定义字段 */ }
    await loadOrderCfDefs()
  }

  /** 预取站点字段需要的自定义字段「值」（物料自定义字段 + 工单自定义字段） */
  function preloadValues (ops) {
    const list = Array.isArray(ops) ? ops : []
    const materialIds = [...new Set(list.map(o => o.materialId).filter(id => id != null))]
    if (materialIds.length) {
      try { mcf.ensureValues(materialIds) } catch (e) { /* 降级：不显示物料自定义字段值 */ }
    }
    preloadOrderCfValues(list)
  }

  async function preloadOrderCfValues (list) {
    if (!orderCfDefs.value.length) return
    const ids = [...new Set((list || []).map(o => o.orderId).filter(id => id != null))]
    if (!ids.length) return
    try {
      orderCfValueMap.value = (await batchLoadCustomFieldValues('production_order', ids)) || {}
    } catch (e) {
      orderCfValueMap.value = {}
    }
  }

  return {
    pageKey: STATION_PAGE_KEY,
    fieldDefs,
    stationFieldsOf,
    allStationFieldsOf,
    init,
    preloadValues
  }
}
