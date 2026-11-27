/**
 * 单据链造数与自检（走业务接口，禁止直插数据库）
 *
 * 为什么需要它（2026-11-11 与用户对齐的口径）：
 *   手写数据直插库会绕过业务代码 → 编号规则/客户简称回读/单位换算/来源单号等派生字段全缺，
 *   单据一引用就是空。所以本脚本只做两类事：
 *     1) 手动建的只有"链头"（销售订单 / 采购申请 / BOM / 生产工单），字段名全部有源码证据；
 *     2) 下游单据一律调系统自己的「下推」接口生成，派生数据由系统产生，关联天然成立。
 *
 * 链路（三链）：
 *   销售：销售订单(手动) → 审核 → 下推销售出库 → 提交/审核 → 下推销售发票 → 提交/审核
 *   采购：采购申请(手动) → 提交/审核 → 下推采购订单 → 提交/审核 → 下推采购入库 → 提交/审核
 *   生产：BOM(手动) → 生产工单(手动, 成品行挂 BOM) → 提交/审核 → 下推领料单 → 下推生产入库单
 *
 * 建数自检（本脚本的核心价值）：
 *   每建一个对象立刻回读详情，逐字段断言非空/有值；缺哪一环当场报"哪张单缺哪个字段"，
 *   绝不等 UI 测试时神秘空白。任何一步失败 → 汇总报错、退出码非 0（门禁不通过）。
 *
 * 幂等与清理：
 *   - 基础资料复用 seed-tenant-data.mjs（子进程调用，幂等）；
 *   - 本脚本创建的单据登记到 .out/seed-chain-registry.json；
 *   - node seed-bill-chain.mjs --cleanup 按 registry 逆序尽力删除（反审核→删除，失败仅记录）。
 *
 * 用法：
 *   node tests/smoke/seed-bill-chain.mjs             # 造数 + 自检
 *   node tests/smoke/seed-bill-chain.mjs --cleanup   # 按 registry 逆序清理
 */
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cfg, bootSummary } from './smoke-env.mjs'
import { login, api } from './smoke-http.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(HERE, '.out')
const REGISTRY_FILE = join(OUT_DIR, 'seed-chain-registry.json')
const SUMMARY_FILE = join(OUT_DIR, 'seed-chain-summary.json')
const P = 'E2E-'
/**
 * 每轮唯一标记：造出来的单据备注里都带它。
 * 为什么必须唯一（2026-11-11 实测踩坑）：早先复用"同备注的旧单据"，
 * 结果复用的是上一轮建坏的半成品单据（缺收款管控/缺数量），自检一直报同一处失败却修不好。
 * 现在改为：开跑前先清理上一轮注册的单据 → 本轮全部新建 → 不会再复用脏数据。
 */
const stamp = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}
const RUN_TAG = `E2E-${stamp()}`
const REMARK = `E2E自动造数 ${RUN_TAG}`

if (!existsSync(OUT_DIR)) mkdirSync(OUT_DIR, { recursive: true })

/* ---------------- 小工具 ---------------- */
const today = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const num = (v) => (v === null || v === undefined || v === '' ? null : Number(v))
const okNum = (v) => num(v) !== null && !Number.isNaN(num(v)) && num(v) > 0
const nonEmpty = (v) => v !== null && v !== undefined && v !== '' && String(v) !== 'null' && String(v) !== 'undefined'

/**
 * 分页列表，返回 records。
 * 三种取数风格（后端各控制器不一致，实测核实）：
 *   'query' — GET {path}?current=&size=   （销售/采购单据、生产工单：前端列表就是这么取的）
 *   'page'  — GET {path}/page?...         （领料单、生产入库单、基础资料）
 *   'list'  — GET {path}/list             （BOM 全量列表，返回数组或 records）
 */
async function listRecords(token, path, params = {}, style = 'page') {
  let url
  if (style === 'query') {
    const q = new URLSearchParams({ current: '1', size: '50', ...params }).toString()
    url = `${path}?${q}`
  } else if (style === 'list') {
    url = `${path}/list`
  } else {
    const q = new URLSearchParams({ current: '1', size: '50', ...params }).toString()
    url = `${path}/page?${q}`
  }
  const r = await api('GET', url, { token })
  if (!r.ok) return []
  const d = r.data || {}
  return Array.isArray(d) ? d : (d.records || [])
}

/** 按"整行 JSON 含关键词"找一条（与 seed-tenant-data 同口径） */
async function findOneByKeyword(token, path, keyword, style = 'page') {
  const records = await listRecords(token, path, {}, style)
  return records.find((x) => JSON.stringify(x).includes(keyword)) || null
}

const steps = []
function step(name, ok, detail = '') {
  steps.push({ name, ok, detail })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail ? ' —— ' + detail : ''}`)
  return ok
}

/* ---------------- 详情自检 ---------------- */
/**
 * 断言详情关键字段非空/有值。checkers: [{ field | get, label, kind: 'filled'|'positive' }]
 * 返回问题清单（空数组 = 通过）。
 */
function checkDetail(detail, checkers, label) {
  const problems = []
  if (!detail || typeof detail !== 'object') {
    return [`${label}: 详情为空，无法自检`]
  }
  for (const c of checkers) {
    const v = c.get ? c.get(detail) : detail[c.field]
    const bad =
      c.kind === 'positive' ? !okNum(v) : !nonEmpty(v)
    if (bad) problems.push(`${label}: 字段「${c.label}」无值${c.kind === 'positive' ? '或非正数' : ''}`)
  }
  return problems
}

/** 详情 JSON 里是否包含某个 id 值（用于确认来源单关联成立，避免猜关联字段名） */
function jsonHasId(detail, id) {
  if (id === null || id === undefined) return false
  return JSON.stringify(detail).includes(`:${Number(id)}`) || JSON.stringify(detail).includes(`"${id}"`)
}

/* ---------------- 单据操作 ---------------- */
async function createBill(token, path, body, label) {
  const r = await api('POST', path, { token, body })
  if (!r.ok) {
    step(`创建${label}`, false, (r.message || r.raw || `HTTP ${r.httpStatus}`).slice(0, 300))
    return null
  }
  const d = r.data || {}
  const id = d.id || d.orderId || d.bomId || d.requisitionId || d.receiptId || d.inboundId || d.deliveryId || d.invoiceId || d.requestId
  return { id, data: d }
}

/** 单据状态字段（各单据统一 status 文本） */
function statusOf(detail) {
  return detail ? String(detail.status ?? detail.billStatus ?? '') : ''
}
const EFFECTIVE = ['已生效', '已审核', '生效']

/** 提交 → （若未生效）审核 → 断言最终生效 */
async function makeEffective(token, cfgBill, id, label) {
  const { path } = cfgBill
  let detail = await getDetail(token, path, id)
  let st = statusOf(detail)
  if (EFFECTIVE.includes(st)) {
    step(`${label} 已生效（${st}）`, true)
    return detail
  }
  // 少数单据没有「提交」接口，草稿直接审核生效（生产入库单：ProductionReceiptController 只有 /approve）
  if (!cfgBill.noSubmit) {
    const sr = await api('POST', `${path}/${id}/submit`, { token })
    if (!sr.ok) {
      step(`${label} 提交`, false, (sr.message || sr.raw || '').slice(0, 300))
      return null
    }
    detail = await getDetail(token, path, id)
    st = statusOf(detail)
    step(`${label} 提交`, true, `状态=${st || '（详情未返回状态）'}`)
    if (EFFECTIVE.includes(st)) return detail
  }

  const ar = await api('POST', `${path}/${id}/approve`, { token })
  if (!ar.ok) {
    // 审批开关开启时需要审批人参数等场景，先原样报出
    step(`${label} 审核`, false, (ar.message || ar.raw || '').slice(0, 300))
    return null
  }
  detail = await getDetail(token, path, id)
  st = statusOf(detail)
  const pass = EFFECTIVE.includes(st)
  step(`${label} 审核`, pass, `状态=${st || '（详情未返回状态）'}`)
  return pass ? detail : null
}

async function getDetail(token, path, id) {
  const r = await api('GET', `${path}/${id}`, { token })
  return r.ok ? r.data : null
}

/** 下推生成下游单：优先取响应里的新单，否则在最新几条里按"详情含来源单 id"定位 */
async function pushDownstream(token, pushPath, sourceId, downCfg, sourceLabel, downLabel) {
  const r = await api('POST', pushPath, { token })
  if (!r.ok) {
    step(`下推：${sourceLabel} → ${downLabel}`, false, (r.message || r.raw || `HTTP ${r.httpStatus}`).slice(0, 300))
    return null
  }
  const d = r.data
  let newId = rid(d, downCfg)
  if (newId) {
    step(`下推：${sourceLabel} → ${downLabel}`, true, `新单 id=${newId}`)
    return { id: newId, data: d }
  }
  // 响应没带新单：在最新记录里按"详情 JSON 含来源单 id"定位（不猜关联字段名）
  const candidates = await listRecords(token, downCfg.path, {}, downCfg.listStyle)
  for (const rec of candidates.slice(0, 5)) {
    const cid = rid(rec, downCfg)
    if (!cid) continue
    const det = await getDetail(token, downCfg.path, cid)
    if (det && jsonHasId(det, sourceId)) {
      step(`下推：${sourceLabel} → ${downLabel}`, true, `新单 id=${cid}（按来源关联定位）`)
      return { id: cid, data: rec }
    }
  }
  step(`下推：${sourceLabel} → ${downLabel}`, false, '接口成功但未定位到新单（响应无 id 且最新 5 条均无来源关联）')
  return null
}

/* ---------------- 基础资料取数 ---------------- */
async function loadBaseData(token) {
  const pick = (rec) => ({
    unitId: rec.unitId || rec.id,
    currencyId: rec.currencyId || rec.id,
    warehouseId: rec.stockId || rec.warehouseId || rec.id,
    categoryId: rec.categoryId || rec.id,
    materialId: rec.materialId || rec.id,
    customerId: rec.customerId || rec.id,
    supplierId: rec.supplierId || rec.id,
    deptId: rec.deptId || rec.id,
    employeeId: rec.employeeId || rec.id,
    name: rec.materialName || rec.customerName || rec.supplierName || rec.stockName || rec.deptName || rec.employeeName || rec.unitName || rec.currencyName || ''
  })

  const unit = await findOneByKeyword(token, '/api/basedata/unit', `${P}个`)
  const currencyList = await listRecords(token, '/api/basedata/currency')
  const currency = (currencyList.find((c) => c.isBaseCurrency === 1 || c.isBaseCurrency === true)) || currencyList[0]
  const warehouse = await findOneByKeyword(token, '/api/basedata/warehouse', 'E2E-WH-01')
  const materialA = await findOneByKeyword(token, '/api/basedata/material', `${P}成品A`)
  const materialC = await findOneByKeyword(token, '/api/basedata/material', `${P}原料C`)
  const customer = await findOneByKeyword(token, '/api/basedata/customer', `${P}客户甲`)
  const supplier = await findOneByKeyword(token, '/api/basedata/supplier', `${P}供应商甲`)
  const deptPurchase = await findOneByKeyword(token, '/api/basedata/department', `${P}采购部`)
  const empSaler = await findOneByKeyword(token, '/api/basedata/employee', `${P}销售员甲`)
  const empBuyer = await findOneByKeyword(token, '/api/basedata/employee', `${P}采购员甲`)

  const missing = []
  if (!unit) missing.push('计量单位')
  if (!currency) missing.push('币种')
  if (!warehouse) missing.push('仓库')
  if (!materialA) missing.push('物料-成品A')
  if (!materialC) missing.push('物料-原料C')
  if (!customer) missing.push('客户')
  if (!supplier) missing.push('供应商')
  if (!deptPurchase) missing.push('部门-采购部')
  if (!empSaler) missing.push('员工-销售员')
  if (!empBuyer) missing.push('员工-采购员')
  if (missing.length) {
    console.error(`  ✗ 基础资料缺失：${missing.join('、')}（请先跑 seed-tenant-data.mjs）`)
    return null
  }
  return {
    unit: pick(unit),
    currency: pick(currency),
    warehouse: pick(warehouse),
    materialA: pick(materialA),
    materialC: pick(materialC),
    customer: pick(customer),
    supplier: pick(supplier),
    deptPurchase: pick(deptPurchase),
    empSaler: pick(empSaler),
    empBuyer: pick(empBuyer)
  }
}

/* ---------------- 三链定义（idField/listStyle 均按后端实体与控制器实测核实，禁止猜） ---------------- */
const SALE = {
  order: { path: '/api/sale/orders', codeField: 'orderNo', idField: 'orderId', listStyle: 'query' },
  delivery: { path: '/api/sale/deliveries', codeField: 'deliveryNo', idField: 'deliveryId', listStyle: 'query' },
  invoice: { path: '/api/sale/invoices', codeField: 'invoiceNo', idField: 'invoiceId', listStyle: 'query' }
}
const PURCHASE = {
  request: { path: '/api/purchase/requests', codeField: 'requestNo', idField: 'requestId', listStyle: 'query' },
  order: { path: '/api/purchase/orders', codeField: 'orderNo', idField: 'orderId', listStyle: 'query' },
  inbound: { path: '/api/purchase/inbounds', codeField: 'inboundNo', idField: 'receiptId', listStyle: 'query' }
}
const PROD = {
  bom: { path: '/api/masterdata/bom', codeField: 'bomCode', idField: 'bomId', listStyle: 'list' },
  workOrder: { path: '/api/production/workorders', codeField: 'orderNo', idField: 'orderId', listStyle: 'query' },
  requisition: { path: '/api/inventory/material-requisitions', codeField: 'requisitionNo', idField: 'requisitionId', listStyle: 'page' },
  receipt: { path: '/api/production/receipts', codeField: 'receiptNo', idField: 'receiptId', listStyle: 'page', noSubmit: true }
}
/** 其他入库单：只为给原料备库存（领料单要求库存充足，OtherReceipt.java L16 主键 receiptId） */
const INV = {
  otherReceipt: { path: '/api/inventory/other-receipts', idField: 'receiptId', listStyle: 'page' }
}

/** 按单据配置解析主键（各单据主键字段名不同：orderId/requestId/receiptId/bomId/...） */
function rid(rec, cfgBill) {
  if (rec === null || rec === undefined) return null
  if (typeof rec === 'number') return rec
  if (typeof rec === 'string') { const n = Number(rec); return Number.isFinite(n) && n > 0 ? n : null }
  const v = rec[cfgBill.idField] !== undefined ? rec[cfgBill.idField] : rec.id
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? n : null
}

/* ---------------- 链一：销售 ---------------- */
async function chainSale(token, base, registry) {
  const d = today()
  // 1. 销售订单（链头，手动建；字段源码证据：SaleOrderEdit rules + addItem 行字段）
  const orderBody = {
    orderDate: d,
    customerId: base.customer.customerId,
    salerId: base.empSaler.employeeId,
    currencyId: base.currency.currencyId,
    // 发货收款管控（源头订单上选定，出库环节只读绕不过）：测试单选 none，
    // 否则默认模式会在下推出库时被「发货前需全额收款」拦截（SaleDeliveryServiceImpl 收款管控校验）。
    paymentControl: 'none',
    remark: REMARK,
    items: [
      {
        materialId: base.materialA.materialId,
        unitId: base.unit.unitId,
        warehouseId: base.warehouse.warehouseId,
        quantity: 10,
        unitPrice: 200,
        taxRate: 13,
        discountRate: 0
      }
    ]
  }
  let order = await findOneByKeyword(token, SALE.order.path, RUN_TAG, SALE.order.listStyle)
  let orderId = rid(order, SALE.order)
  if (orderId) {
    step('销售订单（本轮已建，复用）', true, `id=${orderId}`)
  } else {
    const r = await createBill(token, SALE.order.path, orderBody, '销售订单')
    if (!r) return false
    order = await findOneByKeyword(token, SALE.order.path, RUN_TAG, SALE.order.listStyle)
    orderId = rid(order, SALE.order)
    if (!orderId) { step('销售订单 回查', false, '创建成功但列表查不到'); return false }
    registry.push({ chain: 'sale', name: '销售订单', path: SALE.order.path, id: rid(order, SALE.order) })
    step('创建销售订单', true, `id=${rid(order, SALE.order)}`)
  }
  order = (await getDetail(token, SALE.order.path, orderId)) || order
  let problems = checkDetail(order, [
    { field: 'customerId', label: '客户' },
    { field: 'salerId', label: '销售员' },
    { field: 'currencyId', label: '币种' },
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
    { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '明细-数量', kind: 'positive' },
    { get: (x) => (x.items || [])[0] && x.items[0].unitPrice, label: '明细-单价', kind: 'positive' }
  ], '销售订单')
  step('销售订单 自检（客户/销售员/币种/明细非空）', problems.length === 0, problems.join('；'))

  // 2. 审核 → 备成品库存 → 下推出库 → 审核 → 下推发票 → 审核
  // 备成品库存的原因（2026-11-11 实测）：下推出库会真实校验库存，
  // 反复跑链会把成品库存扣到低于订购量，报「物料[1]在仓库[1]库存不足」。
  if (!(await makeEffective(token, SALE.order, orderId, '销售订单'))) return false
  if (!(await ensureStock(token, base, registry, {
    materialId: base.materialA.materialId,
    materialName: '成品A',
    qty: 50,
    tag: 'FG'
  }))) return false
  const del = await pushDownstream(token, `${SALE.order.path}/${orderId}/push-to-delivery`, orderId, SALE.delivery, '销售订单', '销售出库')
  if (!del) return false
  registry.push({ chain: 'sale', name: '销售出库单', path: SALE.delivery.path, id: del.id })
  // 只发部分货（订购 10 → 发 4）：设置中心默认「销售发货单」完结口径下，
  // 全额发货会让销售订单立即自动结案，订单结案后按业务规则禁止再下推开票
  // （SaleInvoiceServiceImpl L322-324）。造数保留"未发完"状态，才能继续走"出库→发票"。
  // 下推生成的出库单是自动生效的，生效单不可改 → 先反审核退回，再改数量，最后重新提交审核。
  const partQty = 4
  const ra = await api('POST', `${SALE.delivery.path}/${del.id}/reverse-approve`, { token })
  if (!ra.ok) {
    step('销售出库单 反审核（为改部分发货数量）', false, (ra.message || ra.raw || '').slice(0, 240))
    return false
  }
  const delPre = await getDetail(token, SALE.delivery.path, del.id)
  if (delPre && Array.isArray(delPre.items) && delPre.items.length) {
    delPre.items.forEach((it) => { it.quantity = partQty })
    const ur = await api('PUT', `${SALE.delivery.path}/${del.id}`, { token, body: delPre })
    if (!ur.ok) {
      step('销售出库单 改部分发货数量', false, (ur.message || ur.raw || '').slice(0, 240))
      return false
    }
    step('销售出库单 改部分发货数量', true, `每行 ${partQty}（订单 10 未发完，订单不会自动结案）`)
  }
  const delDetail = await makeEffective(token, SALE.delivery, del.id, '销售出库单')
  if (!delDetail) return false
  problems = checkDetail(delDetail, [
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
    { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '明细-数量', kind: 'positive' },
    { get: (x) => jsonHasId(x, orderId) ? 1 : null, label: '来源单关联（详情含销售订单 id）' }
  ], '销售出库单')
  step('销售出库单 自检（明细/来源关联）', problems.length === 0, problems.join('；'))

  const inv = await pushDownstream(token, `${SALE.delivery.path}/${del.id}/push-to-invoice`, del.id, SALE.invoice, '销售出库单', '销售发票')
  if (!inv) return false
  registry.push({ chain: 'sale', name: '销售发票', path: SALE.invoice.path, id: inv.id })
  // 发票类型是必填项（SaleInvoiceEdit rules invoiceType），下推生成的草稿/生效单默认未带 → 补上再审核
  // 下推的发票可能已自动生效（生效单不可改）→ 先反审核退回草稿，补类型后再审核
  let invPre = await getDetail(token, SALE.invoice.path, inv.id)
  if (invPre && !invPre.invoiceType) {
    if (EFFECTIVE.includes(statusOf(invPre))) {
      const ra = await api('POST', `${SALE.invoice.path}/${inv.id}/reverse-approve`, { token })
      if (!ra.ok) {
        step('销售发票 反审核（为补发票类型）', false, (ra.message || ra.raw || '').slice(0, 240))
        return false
      }
      invPre = await getDetail(token, SALE.invoice.path, inv.id)
    }
    invPre.invoiceType = '增值税专用发票'
    const ur = await api('PUT', `${SALE.invoice.path}/${inv.id}`, { token, body: invPre })
    step('销售发票 补发票类型', ur.ok, ur.ok ? '增值税专用发票' : (ur.message || ur.raw || '').slice(0, 240))
    if (!ur.ok) return false
  }
  const invDetail = await makeEffective(token, SALE.invoice, inv.id, '销售发票')
  if (!invDetail) return false
  problems = checkDetail(invDetail, [
    { field: 'invoiceType', label: '发票类型' },
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' }
  ], '销售发票')
  step('销售发票 自检（发票类型/明细）', problems.length === 0, problems.join('；'))
  return true
}

/* ---------------- 链二：采购 ---------------- */
async function chainPurchase(token, base, registry) {
  const d = today()
  // 1. 采购申请（链头；字段证据：PurchaseRequestEdit rules requestDate/departmentId/applicantId/applyType/demandDate）
  const reqBody = {
    requestDate: d,
    demandDate: d,
    departmentId: base.deptPurchase.deptId,
    applicantId: base.empBuyer.employeeId,
    applyType: '普通采购',
    remark: REMARK,
    items: [
      // 采购申请行的数量字段是「申请数量」applyQty（PurchaseRequestItem.java L46），不是 quantity
      // 建议供应商必填：采购申请下推采购订单时供应商从「第一行建议供应商」带出（PurchaseRequestServiceImpl L593-598）
      {
        materialId: base.materialC.materialId,
        unitId: base.unit.unitId,
        applyQty: 20,
        suggestedSupplierId: base.supplier.supplierId,
        suggestedSupplierName: base.supplier.name
      }
    ]
  }
  let req = await findOneByKeyword(token, PURCHASE.request.path, RUN_TAG, PURCHASE.request.listStyle)
  let requestId = rid(req, PURCHASE.request)
  if (requestId) {
    step('采购申请单（本轮已建，复用）', true, `id=${requestId}`)
  } else {
    const r = await createBill(token, PURCHASE.request.path, reqBody, '采购申请单')
    if (!r) return false
    req = await findOneByKeyword(token, PURCHASE.request.path, RUN_TAG, PURCHASE.request.listStyle)
    requestId = rid(req, PURCHASE.request)
    if (!requestId) { step('采购申请单 回查', false, '创建成功但列表查不到'); return false }
    registry.push({ chain: 'purchase', name: '采购申请单', path: PURCHASE.request.path, id: rid(req, PURCHASE.request) })
    step('创建采购申请单', true, `id=${rid(req, PURCHASE.request)}`)
  }
  req = (await getDetail(token, PURCHASE.request.path, requestId)) || req
  let problems = checkDetail(req, [
    { field: 'departmentId', label: '申请部门' },
    { field: 'applicantId', label: '申请人' },
    { field: 'applyType', label: '申请类型' },
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
    { get: (x) => (x.items || [])[0] && x.items[0].applyQty, label: '明细-申请数量', kind: 'positive' }
  ], '采购申请单')
  step('采购申请单 自检（部门/申请人/类型/明细）', problems.length === 0, problems.join('；'))

  if (!(await makeEffective(token, PURCHASE.request, requestId, '采购申请单'))) return false
  const po = await pushDownstream(token, `${PURCHASE.request.path}/${requestId}/push-to-order`, requestId, PURCHASE.order, '采购申请单', '采购订单')
  if (!po) return false
  registry.push({ chain: 'purchase', name: '采购订单', path: PURCHASE.order.path, id: po.id })
  const poDetail = await makeEffective(token, PURCHASE.order, po.id, '采购订单')
  if (!poDetail) return false
  problems = checkDetail(poDetail, [
    { field: 'supplierId', label: '供应商' },
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
    { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '明细-数量', kind: 'positive' }
  ], '采购订单')
  step('采购订单 自检（供应商/明细）', problems.length === 0, problems.join('；'))

  const inbound = await pushDownstream(token, `${PURCHASE.order.path}/${po.id}/push-to-inbound`, po.id, PURCHASE.inbound, '采购订单', '采购入库单')
  if (!inbound) return false
  registry.push({ chain: 'purchase', name: '采购入库单', path: PURCHASE.inbound.path, id: inbound.id })
  // 明细行仓库必填（PurchaseInboundItem.stockId L151）：提交时报「未选择仓库」，下推单默认不带行仓库
  const inPre = await getDetail(token, PURCHASE.inbound.path, inbound.id)
  if (inPre && Array.isArray(inPre.items) && inPre.items.length && inPre.items.every((it) => !it.stockId)) {
    inPre.items.forEach((it) => { it.stockId = base.warehouse.warehouseId })
    if (!inPre.warehouseId) inPre.warehouseId = base.warehouse.warehouseId
    const ur = await api('PUT', `${PURCHASE.inbound.path}/${inbound.id}`, { token, body: inPre })
    step('采购入库单 补明细行仓库', ur.ok, ur.ok ? `每行仓库=${base.warehouse.name}` : (ur.message || ur.raw || '').slice(0, 240))
    if (!ur.ok) return false
  }
  const inDetail = await makeEffective(token, PURCHASE.inbound, inbound.id, '采购入库单')
  if (!inDetail) return false
  problems = checkDetail(inDetail, [
    { field: 'supplierId', label: '供应商' },
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
    { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '明细-数量', kind: 'positive' }
  ], '采购入库单')
  step('采购入库单 自检（供应商/明细）', problems.length === 0, problems.join('；'))
  return true
}

/* ---------------- 链三：生产 ---------------- */
/**
 * 备料：建一张「其他入库单」并审核生效，把原料进到主仓。
 * 为什么需要：工单下推领料时会真实校验库存，实测报「库存不足，仓库[E2E-主仓]可用 0.00，需领料 20.00」。
 * 字段证据：OtherReceiptEdit rules receiptDate/receiptType/warehouseId/currencyId + 明细 materialId/quantity。
 */
async function ensureStock(token, base, registry, { materialId, materialName, qty, tag }) {
  const d = today()
  // 同一张单据里可能要给多种物料备料，用 tag 区分（备注里带 tag，按 tag 精确回查，避免复用错单据）
  const key = `${RUN_TAG} ${tag}`
  let doc = await findOneByKeyword(token, INV.otherReceipt.path, key, INV.otherReceipt.listStyle)
  let id = rid(doc, INV.otherReceipt)
  if (id) {
    step(`其他入库单（${materialName}，本轮已建，复用）`, true, `id=${id}`)
  } else {
    const body = {
      receiptDate: d,
      receiptType: '其他',
      warehouseId: base.warehouse.warehouseId,
      currencyId: base.currency.currencyId,
      remark: `E2E自动造数 ${key}`,
      items: [{
        materialId,
        unitId: base.unit.unitId,
        // 明细行仓库必填（OtherReceiptItem.stockId L65），不填提交时会报「未选择仓库」
        stockId: base.warehouse.warehouseId,
        quantity: qty,
        unitPrice: 30,
        taxRate: 13
      }]
    }
    const r = await createBill(token, INV.otherReceipt.path, body, `其他入库单（${materialName}）`)
    if (!r) return false
    doc = await findOneByKeyword(token, INV.otherReceipt.path, key, INV.otherReceipt.listStyle)
    id = rid(doc, INV.otherReceipt)
    if (!id) { step(`其他入库单（${materialName}）回查`, false, '创建成功但列表查不到'); return false }
    registry.push({ chain: 'stock', name: `其他入库单（${materialName}）`, path: INV.otherReceipt.path, id })
    step(`创建其他入库单（${materialName}）`, true, `id=${id}`)
  }
  const detail = await makeEffective(token, INV.otherReceipt, id, `其他入库单（${materialName}）`)
  if (!detail) return false
  return step(`${materialName}库存已备（主仓）`, true, `入库 ${qty}`)
}

async function chainProduction(token, base, registry) {
  const d = today()
  // 1. BOM（链头；字段证据：BomEdit rules bomDate/materialId/unitId/quantity + 子件 materialId/quantity）
  let bom = await findOneByKeyword(token, PROD.bom.path, `${P}成品A`, PROD.bom.listStyle)
  const bomId = rid(bom, PROD.bom)
  if (bomId) {
    step('BOM（复用已有）', true, `id=${bomId}`)
  } else {
    const body = {
      bomDate: d,
      materialId: base.materialA.materialId,
      unitId: base.unit.unitId,
      quantity: 1,
      remark: REMARK,
      items: [{ materialId: base.materialC.materialId, unitId: base.unit.unitId, quantity: 2 }]
    }
    const r = await createBill(token, PROD.bom.path, body, 'BOM')
    if (!r) return false
    bom = await findOneByKeyword(token, PROD.bom.path, `${P}成品A`, PROD.bom.listStyle)
    if (!rid(bom, PROD.bom)) { step('BOM 回查', false, '创建成功但列表查不到'); return false }
    registry.push({ chain: 'production', name: 'BOM', path: PROD.bom.path, id: rid(bom, PROD.bom) })
    step('创建 BOM', true, `id=${rid(bom, PROD.bom)}`)
  }
  // 自检必须用详情（列表摘要不含子件明细）
  bom = (await getDetail(token, PROD.bom.path, bomId)) || bom
  let problems = checkDetail(bom, [
    { field: 'materialId', label: '母件物料' },
    { field: 'unitId', label: '基本单位' },
    { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '子件明细-物料' },
    { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '子件明细-用量', kind: 'positive' }
  ], 'BOM')
  step('BOM 自检（母件/单位/子件明细）', problems.length === 0, problems.join('；'))

  // 2. 生产工单（链头；成品行挂 BOM；字段证据：WorkOrderEdit rules orderDate/warehouseId + addProductRow 行字段）
  // ⚠ 工单表头也有独立的「物料/生产数量」（WorkOrder.java L35-57）：提交时校验的是表头 quantity，
  //   只传 products 不传表头 → 提交报「计划数量必须大于0」（2026-11-11 实测）。故表头与两套明细一次传齐。
  // ⚠ 原料行数量字段是「需求数量」requiredQuantity（WorkOrderMaterial.java L43-44）。
  let wo = await findOneByKeyword(token, PROD.workOrder.path, RUN_TAG, PROD.workOrder.listStyle)
  let workOrderId = rid(wo, PROD.workOrder)
  if (workOrderId) {
    step('生产工单（本轮已建，复用）', true, `id=${workOrderId}`)
  } else {
    const body = {
      orderDate: d,
      warehouseId: base.warehouse.warehouseId,
      materialId: base.materialA.materialId,
      unitId: base.unit.unitId,
      quantity: 10,
      bomId,
      remark: REMARK,
      products: [
        {
          lineNo: 10,
          materialId: base.materialA.materialId,
          unitId: base.unit.unitId,
          quantity: 10,
          bomId
        }
      ],
      materials: [
        {
          lineNo: 10,
          materialId: base.materialC.materialId,
          unitId: base.unit.unitId,
          bomQuantity: 2,
          requiredQuantity: 20
        }
      ]
    }
    const r = await createBill(token, PROD.workOrder.path, body, '生产工单')
    if (!r) return false
    wo = await findOneByKeyword(token, PROD.workOrder.path, RUN_TAG, PROD.workOrder.listStyle)
    workOrderId = rid(wo, PROD.workOrder)
    if (!workOrderId) { step('生产工单 回查', false, '创建成功但列表查不到'); return false }
    registry.push({ chain: 'production', name: '生产工单', path: PROD.workOrder.path, id: rid(wo, PROD.workOrder) })
    step('创建生产工单', true, `id=${rid(wo, PROD.workOrder)}`)
  }
  const woDetail = await getDetail(token, PROD.workOrder.path, workOrderId)
  if (!woDetail) step('生产工单 详情读取', false, `GET ${PROD.workOrder.path}/${workOrderId} 未返回详情`)
  problems = checkDetail(woDetail, [
    { get: (x) => (x.products || [])[0] && x.products[0].materialId, label: '成品明细-物料' },
    { get: (x) => (x.products || [])[0] && x.products[0].quantity, label: '成品明细-计划数量', kind: 'positive' },
    { get: (x) => (x.materials || [])[0] && x.materials[0].materialId, label: '原料明细-物料' },
    { get: (x) => (x.materials || [])[0] && (x.materials[0].requiredQuantity ?? x.materials[0].quantity), label: '原料明细-需求数量', kind: 'positive' }
  ], '生产工单')
  step('生产工单 自检（成品/原料明细）', problems.length === 0, problems.join('；'))

  // 3. 审核工单 → 备料 → 下推领料 → 下推生产入库
  if (!(await makeEffective(token, PROD.workOrder, workOrderId, '生产工单'))) return false
  if (!(await ensureStock(token, base, registry, {
    materialId: base.materialC.materialId,
    materialName: '原料C',
    qty: 50,
    tag: 'RM'
  }))) return false
  const requisition = await pushDownstream(token, `${PROD.workOrder.path}/${workOrderId}/push-material-requisition`, workOrderId, PROD.requisition, '生产工单', '领料单')
  if (!requisition) return false
  registry.push({ chain: 'production', name: '领料单', path: PROD.requisition.path, id: requisition.id })
  const reqDetail = await makeEffective(token, PROD.requisition, requisition.id, '领料单')
  if (reqDetail) {
    problems = checkDetail(reqDetail, [
      { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
      { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '明细-数量', kind: 'positive' }
    ], '领料单')
    step('领料单 自检（明细非空）', problems.length === 0, problems.join('；'))
  }

  const receipt = await pushDownstream(token, `${PROD.workOrder.path}/${workOrderId}/push-production-receipt`, workOrderId, PROD.receipt, '生产工单', '生产入库单')
  if (!receipt) return false
  registry.push({ chain: 'production', name: '生产入库单', path: PROD.receipt.path, id: receipt.id })
  const rcDetail = await makeEffective(token, PROD.receipt, receipt.id, '生产入库单')
  if (rcDetail) {
    problems = checkDetail(rcDetail, [
      { get: (x) => (x.items || [])[0] && x.items[0].materialId, label: '明细-物料' },
      { get: (x) => (x.items || [])[0] && x.items[0].quantity, label: '明细-数量', kind: 'positive' }
    ], '生产入库单')
    step('生产入库单 自检（明细非空）', problems.length === 0, problems.join('；'))
  }
  return true
}

/* ---------------- 清理 ---------------- */
/** 下游单据名称 → 配置（用于解析"已被下游单据引用: 采购订单:PO-xxx"并先删下游） */
const DOWNSTREAM_CFG = {
  销售订单: SALE.order,
  销售出库单: SALE.delivery,
  销售发票: SALE.invoice,
  采购申请单: PURCHASE.request,
  采购订单: PURCHASE.order,
  采购入库单: PURCHASE.inbound,
  生产工单: PROD.workOrder,
  领料单: PROD.requisition,
  生产入库单: PROD.receipt,
  其他入库单: INV.otherReceipt,
  BOM: PROD.bom
}

/** 按单号找到单据并删除（先反结案→反审核→删除） */
async function deleteByCode(token, cfgBill, code) {
  const records = await listRecords(token, cfgBill.path, {}, cfgBill.listStyle)
  const rec = records.find((x) => JSON.stringify(x).includes(code))
  const id = rec ? rid(rec, cfgBill) : null
  if (!id) return false
  await api('POST', `${cfgBill.path}/${id}/reopen`, { token })
  await api('POST', `${cfgBill.path}/${id}/reverse-approve`, { token })
  const dr = await api('DELETE', `${cfgBill.path}/${id}`, { token })
  if (dr.ok) console.log(`  ✓ 先删下游单据 ${code}（id=${id}）`)
  return dr.ok
}

/** 按注册表逆序尽力删除（先反审核再删；失败只记录不中断） */
async function purgeRegistry(token, registry, label = '清理') {
  let ok = 0
  let fail = 0
  for (const item of [...registry].reverse()) {
    // 清理顺序（实测踩坑 2026-11-11）：已结案的单据必须先【反结案】才能【反审核】退回草稿，
    // 否则报「已结案，不能重置草稿」，单据就永远删不掉、变成测试残留。
    await api('POST', `${item.path}/${item.id}/reopen`, { token })
    let rr = await api('POST', `${item.path}/${item.id}/reverse-approve`, { token })
    let dr = await api('DELETE', `${item.path}/${item.id}`, { token })
    if (!dr.ok) {
      // 被下游引用：从报错里解析下游单号，先把下游删掉再重试（下推生成的下游单据备注不含造数标记，扫不到）
      // 注意："已被下游单据引用"这句出现在反审核的报错里，删除报错只说"非草稿不能删除"
      const msg = (rr.ok ? '' : (rr.message || rr.raw || '')) || dr.message || dr.raw || ''
      const m = /([\u4e00-\u9fa5A-Za-z]+)[:：]([A-Za-z0-9-]+)、?/.exec(msg.split('引用')[1] || '')
      const cfgDown = m && DOWNSTREAM_CFG[m[1]]
      if (cfgDown && m[2]) await deleteByCode(token, cfgDown, m[2])
      await api('POST', `${item.path}/${item.id}/reopen`, { token })
      rr = await api('POST', `${item.path}/${item.id}/reverse-approve`, { token })
      dr = await api('DELETE', `${item.path}/${item.id}`, { token })
    }
    if (dr.ok) { ok++; console.log(`  ✓ ${label}：已删除 ${item.name} id=${item.id}`) }
    else {
      fail++
      const rrMsg = rr.ok ? '' : `（反审核失败：${(rr.message || '').slice(0, 100)}）`
      console.log(`  ✗ ${label}：删除失败 ${item.name} id=${item.id}: ${(dr.message || dr.raw || '').slice(0, 160)}${rrMsg}`)
    }
  }
  console.log(`  ${label}结果：成功 ${ok} / 失败 ${fail}`)
  return { ok, fail }
}

/**
 * 扫单顺序：上游 → 下游。
 * purgeRegistry 内部会倒序执行，因此这里按上游在前排列，实际删除就是"先删下游再删源单"，
 * 否则会报「已被下游单据引用，请先作废下游」而删不掉（2026-11-11 实测）。
 */
const SWEEP_ORDER = [
  { name: 'BOM', ...PROD.bom },
  { name: '生产工单', ...PROD.workOrder },
  { name: '其他入库单', ...INV.otherReceipt },
  { name: '领料单', ...PROD.requisition },
  { name: '生产入库单', ...PROD.receipt },
  { name: '销售订单', ...SALE.order },
  { name: '销售出库单', ...SALE.delivery },
  { name: '销售发票', ...SALE.invoice },
  { name: '采购申请单', ...PURCHASE.request },
  { name: '采购订单', ...PURCHASE.order },
  { name: '采购入库单', ...PURCHASE.inbound }
]

/**
 * 扫掉"历史遗留的测试单据"：备注含 E2E自动造数、但不是本轮标记的。
 * 目的：不让上一轮建坏/被复用的单据一直堆在测试租户里（守则 12 同理——不留测试残留）。
 */
async function sweepOldTestDocs(token) {
  const leftovers = []
  const reqNos = []
  for (const cfgBill of SWEEP_ORDER) {
    const records = await listRecords(token, cfgBill.path, {}, cfgBill.listStyle)
    for (const rec of records) {
      const s = JSON.stringify(rec)
      if (!s.includes('E2E自动造数') || s.includes(RUN_TAG)) continue
      const id = rid(rec, cfgBill)
      if (!id) continue
      leftovers.push({ name: cfgBill.name, path: cfgBill.path, id })
      if (cfgBill === PURCHASE.request && rec.requestNo) reqNos.push(rec.requestNo)
    }
  }
  // 下推生成的采购订单备注是「由采购申请单 XXX 下推生成」，不含造数标记 → 按来源申请单号补抓；
  // 不抓就会变成"申请单永远被下游引用、删不掉"的残留。
  if (reqNos.length) {
    const pos = await listRecords(token, PURCHASE.order.path, {}, PURCHASE.order.listStyle)
    for (const rec of pos) {
      const s = JSON.stringify(rec)
      if (!reqNos.some((no) => no && s.includes(no))) continue
      const id = rid(rec, PURCHASE.order)
      if (id && !leftovers.some((x) => x.path === PURCHASE.order.path && x.id === id)) {
        leftovers.push({ name: '采购订单', path: PURCHASE.order.path, id })
      }
    }
  }
  if (!leftovers.length) return { ok: 0, fail: 0 }
  console.log(`\n===== 清理历史测试单据（${leftovers.length} 张）=====`)
  return purgeRegistry(token, leftovers, '历史测试单据')
}

async function cleanup() {
  await bootSummary()
  const { token } = await login()
  if (!existsSync(REGISTRY_FILE)) {
    console.log('未找到 seed-chain-registry.json，无链上单据可清理')
    return
  }
  const registry = JSON.parse(readFileSync(REGISTRY_FILE, 'utf8'))
  await purgeRegistry(token, registry)
  writeFileSync(REGISTRY_FILE, JSON.stringify([], null, 2))
}

/* ---------------- 主流程 ---------------- */
async function main() {
  if (process.argv.includes('--cleanup')) return cleanup()

  await bootSummary()
  console.log('\n===== 前置：基础资料准备（seed-tenant-data，幂等）=====')
  const seed = spawnSync(process.execPath, [join(HERE, 'seed-tenant-data.mjs')], { stdio: 'inherit' })
  if (seed.status !== 0) {
    console.error('  ✗ 基础资料准备失败，单据链中止')
    process.exit(1)
  }

  console.log('\n===== 登录测试租户 =====')
  const { token } = await login()

  // 开跑前先清掉上一轮注册的链上单据：避免复用上一轮建坏的半成品单据（2026-11-11 实测坑）
  if (existsSync(REGISTRY_FILE)) {
    try {
      const prev = JSON.parse(readFileSync(REGISTRY_FILE, 'utf8'))
      if (Array.isArray(prev) && prev.length) {
        console.log(`\n===== 清理上一轮链上单据（${prev.length} 张）=====`)
        await purgeRegistry(token, prev, '上一轮')
        writeFileSync(REGISTRY_FILE, JSON.stringify([], null, 2))
      }
    } catch (e) {
      console.log(`  [提示] 上一轮注册表读取/清理跳过：${e.message}`)
    }
  }

  // 再扫一遍历史遗留测试单据（含早先被"复用"、未登记进注册表的那些），保证每轮从干净状态开始
  await sweepOldTestDocs(token)

  const base = await loadBaseData(token)
  if (!base) process.exit(1)
  console.log('  ✓ 基础资料齐备（单位/币种/仓库/成品A/原料C/客户/供应商/采购部/销售员/采购员）')

  const registry = []
  console.log('\n===== 链一：销售（订单→出库→发票）=====')
  let saleOk = false
  try { saleOk = await chainSale(token, base, registry) } catch (e) { step('销售链 异常', false, e.message) }

  console.log('\n===== 链二：采购（申请→订单→入库）=====')
  let purchaseOk = false
  try { purchaseOk = await chainPurchase(token, base, registry) } catch (e) { step('采购链 异常', false, e.message) }

  console.log('\n===== 链三：生产（BOM→工单→领料→入库）=====')
  let prodOk = false
  try { prodOk = await chainProduction(token, base, registry) } catch (e) { step('生产链 异常', false, e.message) }

  // 汇总落盘
  const passed = steps.filter((s) => s.ok).length
  const failed = steps.filter((s) => !s.ok).length
  const summary = {
    finishedAt: new Date().toISOString(),
    chains: { sale: saleOk, purchase: purchaseOk, production: prodOk },
    passed,
    failed,
    steps
  }
  writeFileSync(SUMMARY_FILE, JSON.stringify(summary, null, 2))
  writeFileSync(REGISTRY_FILE, JSON.stringify(registry, null, 2))

  console.log('\n===== 单据链造数自检汇总 =====')
  console.log(`  链路完成：销售=${saleOk ? '是' : '否'} / 采购=${purchaseOk ? '是' : '否'} / 生产=${prodOk ? '是' : '否'}`)
  console.log(`  自检项：${passed} 项 PASS / ${failed} 项 FAIL`)
  if (failed) {
    console.log('  失败明细：')
    for (const s of steps.filter((x) => !x.ok)) console.log(`    · ${s.name} —— ${s.detail}`)
    console.log('  ★ 单据链不完整：请先修复上列失败项，再跑冒烟闭环（未修复前相关单据页面会缺数据）')
    process.exit(1)
  }
  console.log('  ★ 全链路就绪：34 张单据的闭环用例现在有真实数据可跑')
}

main().catch((e) => {
  console.error('单据链造数异常中止:', e.message)
  process.exit(1)
})
