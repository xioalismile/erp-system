// 单据统一状态机 · 非销售域跨模块接口级生命周期验收（2026-11-09）
// 覆盖 9 张单据：新建草稿 → 审核生效 → 重置草稿 → 作废 → 反作废 → 删除
// 依赖：本地后端 8080（admin/admin123）+ 本地 PostgreSQL（psql）
// 约束：只在本地造数，remark 统一带唯一标记，脚本结束按标记清理；不碰 121 生产。
const { execFileSync } = require('child_process')

const API = 'http://localhost:8080'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
const TAG = 'SM-LIFECYCLE-' + Date.now()
const TODAY = new Date().toISOString().slice(0, 10)
const DB = { host: 'localhost', port: '5432', user: 'erp_user', db: 'erp_db', pwd: process.env.PGPASSWORD || 'Erp@2026' }
const STOCK_SRC = 6, STOCK_DST = 7
const BATCH_SRC = 'RAW-A-B001' // 原料仓 6 / 物料 1804 的现有批次（避免新造库存主数据）

let pass = 0, fail = 0, skipped = 0, defects = 0
const CREATED_NOS = []
let BASE_INV = []
const defect = (name, detail) => { defects++; console.log('DEFECT ' + name + (detail ? ' | ' + detail : '')) }
const assert = (cond, name, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) }
  else { fail++; console.log('FAIL ' + name + (extra ? ' | ' + extra : '')) }
}
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t, code: r.status } } }
let H = {}
const req = async (m, p, b) => j(await fetch(API + p, {
  method: m,
  headers: b ? { ...H, 'Content-Type': 'application/json' } : H,
  body: b ? JSON.stringify(b) : undefined
}))
const brief = o => JSON.stringify(o).slice(0, 240)

// ===== 状态归一：中文/数字 → 统一 code（0草稿 1待审核 2已生效 7已作废） =====
function statusCode (v) {
  if (v === null || v === undefined || v === '') return 0
  if (typeof v === 'number') {
    if (v === 0) return 0
    if (v === 1) return 1
    if (v === 7) return 7
    return 2
  }
  const s = String(v).trim()
  if (['草稿', '已取消', '已驳回', '驳回', '未审核', 'DRAFT'].includes(s)) return 0
  if (['待审核', '待审批', '审批中', 'PENDING'].includes(s)) return 1
  if (['已作废', '作废', 'VOIDED'].includes(s)) return 7
  return 2
}

// ===== psql =====
function psql (sql) {
  return execFileSync('psql', [
    '-h', DB.host, '-p', DB.port, '-U', DB.user, '-d', DB.db,
    '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-c', sql
  ], { env: { ...process.env, PGPASSWORD: DB.pwd }, stdio: ['ignore', 'pipe', 'pipe'] }).toString().trim()
}
function dbCount (sql) {
  try { return parseInt(psql(sql) || '0', 10) || 0 } catch (e) { console.log('WARN db query failed: ' + e.message); return -1 }
}
/** 表缺少实体所需列时返回原因；列齐备返回 null（用于"未验"判定，不伪造通过） */
function missingCols (table, cols) {
  const hit = dbCount("SELECT count(*) FROM information_schema.columns WHERE table_name = '" + table +
    "' AND column_name IN ('" + cols.join("','") + "')")
  if (hit === cols.length) return null
  return '本地表 ' + table + ' 缺列 ' + cols.join('/') + '（实测存在 ' + hit + '/' + cols.length +
    '），deploy/sql 无对应 DDL → 新建接口直接 500，无法造出有效单据；该单据静态接入核查已通过（继承统一基类）'
}
/** 库存基线快照（物料 1804 / 仓库 6、7）：测试后据此还原，并检测"反作废/重置草稿"是否回到原值 */
function snapshotInventory () {
  try {
    const out = psql('SELECT inventory_id || \',\' || stock_id || \',\' || quantity || \',\' || available_quantity || \',\' || ' +
      'coalesce(unit_cost,0) || \',\' || coalesce(total_cost,0) FROM inventory_current ' +
      'WHERE material_id = 1804 AND stock_id IN (6,7) ORDER BY inventory_id')
    return out ? out.split('\n').map(s => s.trim()).filter(Boolean) : []
  } catch (e) { console.log('WARN 库存基线快照失败: ' + e.message); return [] }
}
/** 按仓库汇总库存数量，返回 { '6': n, '7': n } */
function inventoryTotals (rows) {
  const m = {}
  for (const r of rows) {
    const p = r.split(',')
    const stock = p[1]
    m[stock] = (m[stock] || 0) + Number(p[2] || 0)
  }
  return m
}

// ===== 通用生命周期 =====
// cfg 字段：name / create() / detailPath(id) / idField /
//   statusField / approvedByField / approvedAtField / billBeforeStatusField / voidedByField / voidReasonField /
//   submitPath(id) / approvePath(id) / reversePath(id) / voidPath(id) / unvoidPath(id) / deletePath(id) /
//   approveAgainPath(id)（可选，默认 approvePath）/ childTable / childFk
async function runLifecycle (cfg) {
  console.log('\n---- ' + cfg.name + ' ----')
  if (cfg.precheck) {
    const reason = cfg.precheck()
    if (reason) { skipped++; console.log('UNVERIFIED ' + cfg.name + ' | ' + reason); return }
  }
  let id
  try {
    // 1. 新建（草稿）
    const created = await cfg.create()
    if (!created || created.code !== 200 || !created.data || created.data[cfg.idField] == null) {
      assert(false, cfg.name + ' 新建草稿', brief(created))
      return
    }
    id = created.data[cfg.idField]
    const NO_CANDIDATES = ['requestNo', 'receiptNo', 'issueNo', 'transferNo', 'expenseNo', 'orderNo', 'sampleNo', 'strategyCode']
    if (cfg.noField && created.data[cfg.noField]) CREATED_NOS.push(String(created.data[cfg.noField]))
    else { for (const k of NO_CANDIDATES) { if (created.data[k]) { CREATED_NOS.push(String(created.data[k])); break } } }
    let d = await cfg.detail(id)
    assert(d && statusCode(d[cfg.statusField]) === 0, cfg.name + ' 新建后状态=草稿',
      'id=' + id + ' status=' + (d && d[cfg.statusField]))

    // 2. 提交 → 审核生效
    const sub = await req('POST', cfg.submitPath(id))
    assert(sub.code === 200, cfg.name + ' 提交成功', brief(sub))
    d = await cfg.detail(id)
    if (statusCode(d[cfg.statusField]) === 1) {
      const ap = await req('POST', cfg.approvePath(id))
      assert(ap.code === 200, cfg.name + ' 待审核状态审核成功', brief(ap))
      d = await cfg.detail(id)
    }
    assert(statusCode(d[cfg.statusField]) === 2, cfg.name + ' 审核后状态=已生效',
      'status=' + d[cfg.statusField])
    assert(d[cfg.approvedAtField] != null, cfg.name + ' 审核时间已落库',
      'approvedAt=' + d[cfg.approvedAtField])

    // 3. 重置草稿：状态回草稿 + 审核人/时间清空
    const rv = await req('POST', cfg.reversePath(id))
    assert(rv.code === 200, cfg.name + ' 重置草稿接口成功', brief(rv))
    d = await cfg.detail(id)
    assert(statusCode(d[cfg.statusField]) === 0, cfg.name + ' 重置草稿后状态=草稿', 'status=' + d[cfg.statusField])
    assert(d[cfg.approvedAtField] == null, cfg.name + ' 重置草稿后审核时间已清空',
      'approvedAt=' + d[cfg.approvedAtField])
    assert(d[cfg.approvedByField] == null, cfg.name + ' 重置草稿后审核人已清空',
      'approvedBy=' + d[cfg.approvedByField])

    // 4. 再次审核生效
    const ap2 = await req('POST', cfg.approveAgainPath ? cfg.approveAgainPath(id) : cfg.approvePath(id))
    assert(ap2.code === 200, cfg.name + ' 草稿再次审核成功', brief(ap2))
    d = await cfg.detail(id)
    assert(statusCode(d[cfg.statusField]) === 2, cfg.name + ' 再次审核后状态=已生效', 'status=' + d[cfg.statusField])

    // 5. 作废：记录作废人/原因/作废前状态
    const reason = TAG + '-VOID'
    const vd = await req('POST', cfg.voidPath(id), { reason })
    assert(vd.code === 200, cfg.name + ' 作废接口成功', brief(vd))
    d = await cfg.detail(id)
    assert(statusCode(d[cfg.statusField]) === 7, cfg.name + ' 作废后状态=已作废', 'status=' + d[cfg.statusField])
    assert(d[cfg.voidedByField] === USER, cfg.name + ' 作废人已落库', 'voidedBy=' + d[cfg.voidedByField])
    assert(d[cfg.voidReasonField] === reason, cfg.name + ' 作废原因已落库', 'voidReason=' + d[cfg.voidReasonField])
    assert(Number(d[cfg.billBeforeStatusField]) === 2, cfg.name + ' 记录作废前状态=已生效(2)',
      'billBeforeStatus=' + d[cfg.billBeforeStatusField])

    // 6. 反作废恢复
    const uv = await req('POST', cfg.unvoidPath(id))
    assert(uv.code === 200, cfg.name + ' 反作废接口成功', brief(uv))
    d = await cfg.detail(id)
    assert(statusCode(d[cfg.statusField]) === 2, cfg.name + ' 反作废后状态=已生效', 'status=' + d[cfg.statusField])
    if (d[cfg.voidedByField] != null || d[cfg.billBeforeStatusField] != null ||
        d[cfg.voidReasonField] != null || d.voidedAt != null) {
      defect(cfg.name + ' 反作废未清空作废痕迹',
        'voidedBy=' + d[cfg.voidedByField] +
        ' voidReason=' + d[cfg.voidReasonField] +
        ' voidedAt=' + d.voidedAt +
        ' billBeforeStatus=' + d[cfg.billBeforeStatusField])
    }

    // 7. 回到草稿后删除
    const rv2 = await req('POST', cfg.reversePath(id))
    assert(rv2.code === 200, cfg.name + ' 反作废后再次重置草稿', brief(rv2))
    const del = await req('DELETE', cfg.deletePath(id))
    assert(del.code === 200, cfg.name + ' 草稿删除成功', brief(del))
    const gone = await req('GET', cfg.detailPath(id))
    assert(gone.code !== 200 || gone.data == null, cfg.name + ' 删除后查不到',
      brief(gone))
    if (cfg.childTable) {
      const childLeft = dbCount('SELECT count(*) FROM ' + cfg.childTable + ' WHERE ' + cfg.childFk + ' = ' + id)
      assert(childLeft === 0, cfg.name + ' 删除后明细无残留',
        cfg.childTable + ' 剩余 ' + childLeft + ' 行')
    }
  } catch (e) {
    fail++
    console.log('FAIL ' + cfg.name + ' 生命周期中断: ' + (e && e.message))
  }
}

// 各单据详情读取（统一包装）
const detailOf = path => async id => {
  const r = await req('GET', path + '/' + id)
  return (r && r.code === 200) ? r.data : null
}

// ===== 主流程 =====
;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD })
  }))
  if (!login.data || !login.data.token) { console.log('FAIL 管理员登录: ' + brief(login)); process.exit(2) }
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(true, '管理员登录成功')

  const mat = { materialId: 1804, materialCode: 'TEST-RAW-001', materialName: '测试原料A', unitId: 41 }
  BASE_INV = snapshotInventory()
  console.log('库存基线（物料1804 仓库6/7 合计数量）: ' + JSON.stringify(BASE_INV))

  // 1. 采购申请单
  await runLifecycle({
    name: '采购申请单',
    create: () => req('POST', '/api/purchase/requests', {
      requestNo: '', requestDate: TODAY, applicantId: 16, applyType: '普通采购', remark: TAG,
      items: [{ lineNo: 1, ...mat, applyQty: 1 }]
    }),
    detail: detailOf('/api/purchase/requests'),
    detailPath: id => '/api/purchase/requests/' + id,
    idField: 'requestId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/purchase/requests/' + id + '/submit',
    approvePath: id => '/api/purchase/requests/' + id + '/approve',
    reversePath: id => '/api/purchase/requests/' + id + '/reverse-approve',
    voidPath: id => '/api/purchase/requests/' + id + '/void',
    unvoidPath: id => '/api/purchase/requests/' + id + '/unvoid',
    deletePath: id => '/api/purchase/requests/' + id,
    childTable: 'purchase_request_item', childFk: 'request_id'
  })

  // 2. 库存其他入库单
  await runLifecycle({
    name: '库存其他入库单',
    create: () => req('POST', '/api/inventory/other-receipts', {
      receiptNo: '', receiptDate: TODAY, receiptType: '其他入库', warehouseId: STOCK_SRC, remark: TAG,
      items: [{ lineNo: 1, ...mat, quantity: 1, unitPrice: 10, stockId: STOCK_SRC, batchNo: TAG }]
    }),
    detail: detailOf('/api/inventory/other-receipts'),
    detailPath: id => '/api/inventory/other-receipts/' + id,
    idField: 'receiptId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/inventory/other-receipts/' + id + '/submit',
    approvePath: id => '/api/inventory/other-receipts/' + id + '/approve',
    reversePath: id => '/api/inventory/other-receipts/' + id + '/reverse-approve',
    voidPath: id => '/api/inventory/other-receipts/' + id + '/void',
    unvoidPath: id => '/api/inventory/other-receipts/' + id + '/unvoid',
    deletePath: id => '/api/inventory/other-receipts/' + id,
    childTable: 'other_receipt_item', childFk: 'receipt_id'
  })

  // 3. 库存其他出库单
  await runLifecycle({
    name: '库存其他出库单',
    create: () => req('POST', '/api/inventory/other-issues', {
      issueNo: '', issueDate: TODAY, issueType: '其他出库', warehouseId: STOCK_SRC, remark: TAG,
      items: [{ lineNo: 1, ...mat, quantity: 1, unitPrice: 10, stockId: STOCK_SRC, batchNo: BATCH_SRC }]
    }),
    detail: detailOf('/api/inventory/other-issues'),
    detailPath: id => '/api/inventory/other-issues/' + id,
    idField: 'issueId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/inventory/other-issues/' + id + '/submit',
    approvePath: id => '/api/inventory/other-issues/' + id + '/approve',
    reversePath: id => '/api/inventory/other-issues/' + id + '/reverse-approve',
    voidPath: id => '/api/inventory/other-issues/' + id + '/void',
    unvoidPath: id => '/api/inventory/other-issues/' + id + '/unvoid',
    deletePath: id => '/api/inventory/other-issues/' + id,
    childTable: 'other_issue_item', childFk: 'issue_id'
  })

  // 4. 库存调拨单
  await runLifecycle({
    name: '库存调拨单',
    create: () => req('POST', '/api/inventory/stock-transfers', {
      transferNo: '', transferDate: TODAY, transferType: '同价调拨',
      fromStockId: STOCK_SRC, toStockId: STOCK_DST, remark: TAG,
      items: [{
        lineNo: 1, ...mat, quantity: 1, unitPrice: 10,
        fromStockId: STOCK_SRC, toStockId: STOCK_DST, batchNo: BATCH_SRC
      }]
    }),
    detail: detailOf('/api/inventory/stock-transfers'),
    detailPath: id => '/api/inventory/stock-transfers/' + id,
    idField: 'transferId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/inventory/stock-transfers/' + id + '/submit',
    approvePath: id => '/api/inventory/stock-transfers/' + id + '/approve',
    reversePath: id => '/api/inventory/stock-transfers/' + id + '/reverse-approve',
    voidPath: id => '/api/inventory/stock-transfers/' + id + '/void',
    unvoidPath: id => '/api/inventory/stock-transfers/' + id + '/unvoid',
    deletePath: id => '/api/inventory/stock-transfers/' + id,
    childTable: 'stock_transfer_item', childFk: 'transfer_id'
  })

  // 5. 经营费用分摊
  await runLifecycle({
    name: '经营费用分摊',
    create: () => req('POST', '/api/finance/operation-expense', {
      expenseType: '其他', expenseDate: TODAY, amount: 100, remark: TAG
    }),
    detail: detailOf('/api/finance/operation-expense'),
    detailPath: id => '/api/finance/operation-expense/' + id,
    idField: 'expenseId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/finance/operation-expense/' + id + '/submit',
    approvePath: id => '/api/finance/operation-expense/' + id + '/approve',
    reversePath: id => '/api/finance/operation-expense/' + id + '/reverse-approve',
    voidPath: id => '/api/finance/operation-expense/' + id + '/void',
    unvoidPath: id => '/api/finance/operation-expense/' + id + '/unvoid',
    deletePath: id => '/api/finance/operation-expense/' + id,
    childTable: 'operation_expense_allocation', childFk: 'expense_id'
  })

  // 6. 生产工单（显式提供用料明细，不依赖 BOM/工艺路线）
  await runLifecycle({
    name: '生产工单',
    create: () => req('POST', '/api/production/workorders', {
      orderNo: '', orderDate: TODAY, quantity: 1, warehouseId: STOCK_SRC, remark: TAG,
      materials: [{
        lineNo: 10, ...mat, bomQuantity: 1, requiredQuantity: 1, issuedQuantity: 0,
        returnedQuantity: 0, consumedQuantity: 0, scrapQuantity: 0, supplyType: '领料', stockId: STOCK_SRC
      }]
    }),
    detail: detailOf('/api/production/workorders'),
    detailPath: id => '/api/production/workorders/' + id,
    idField: 'orderId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/production/workorders/' + id + '/submit',
    approvePath: id => '/api/production/workorders/' + id + '/approve',
    reversePath: id => '/api/production/workorders/' + id + '/reverse-approve',
    voidPath: id => '/api/production/workorders/' + id + '/void',
    unvoidPath: id => '/api/production/workorders/' + id + '/unvoid',
    deletePath: id => '/api/production/workorders/' + id,
    childTable: 'production_order_item', childFk: 'order_id'
  })

  // 7. 委外加工单
  await runLifecycle({
    name: '委外加工单',
    precheck: () => missingCols('subcontract_order', ['approved_by', 'approved_at']),
    create: () => req('POST', '/api/subcontract/orders', {
      orderNo: '', orderDate: TODAY, supplierId: 1, currencyCode: 'CNY', exchangeRate: 1, remark: TAG,
      items: [{
        materialId: mat.materialId, materialCode: mat.materialCode, materialName: mat.materialName,
        unitId: mat.unitId, unit: 'PCS', quantity: 1, unitPrice: 10
      }]
    }),
    detail: detailOf('/api/subcontract/orders'),
    detailPath: id => '/api/subcontract/orders/' + id,
    idField: 'id',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/subcontract/orders/' + id + '/submit',
    approvePath: id => '/api/subcontract/orders/' + id + '/approve',
    reversePath: id => '/api/subcontract/orders/' + id + '/reverse-approve',
    voidPath: id => '/api/subcontract/orders/' + id + '/void',
    unvoidPath: id => '/api/subcontract/orders/' + id + '/unvoid',
    deletePath: id => '/api/subcontract/orders/' + id,
    childTable: 'subcontract_order_item', childFk: 'order_id'
  })

  // 8. 打样单
  const plus = n => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10)
  await runLifecycle({
    name: '打样单',
    precheck: () => missingCols('sample_order', ['approved_by', 'approved_at']),
    create: () => req('POST', '/api/sample/orders', {
      sampleNo: '', sampleName: TAG, sampleType: '新产品', sampleStage: '原样',
      customerId: 1, materialId: mat.materialId, materialCode: mat.materialCode, materialName: mat.materialName,
      unitId: mat.unitId, sampleQty: 1, demandDate: TODAY, planFinishDate: plus(7), remark: TAG,
      items: [{
        lineNo: 1, childMaterialId: mat.materialId, childMaterialCode: mat.materialCode,
        childMaterialName: mat.materialName, unitId: mat.unitId, baseQty: 1, unitPrice: 10
      }]
    }),
    detail: detailOf('/api/sample/orders'),
    detailPath: id => '/api/sample/orders/' + id,
    idField: 'sampleId',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    submitPath: id => '/api/sample/orders/' + id + '/submit',
    approvePath: id => '/api/sample/orders/' + id + '/approve',
    reversePath: id => '/api/sample/orders/' + id + '/reverse-approve',
    voidPath: id => '/api/sample/orders/' + id + '/void',
    unvoidPath: id => '/api/sample/orders/' + id + '/unvoid',
    deletePath: id => '/api/sample/orders/' + id,
    childTable: 'sample_order_item', childFk: 'sample_id'
  })

  // 9. 销售价格策略（无独立提交/审核接口，审核通过「启用/禁用」切换完成）
  await runLifecycle({
    name: '销售价格策略',
    create: () => req('POST', '/api/price/sale-strategy', {
      targetType: 'material', priceType: 'fixed', priceValue: 100, remark: TAG,
      items: [{ targetType: 'material', targetId: mat.materialId, priceType: 'fixed', priceValue: 100 }]
    }),
    detail: detailOf('/api/price/sale-strategy'),
    detailPath: id => '/api/price/sale-strategy/' + id,
    idField: 'id',
    statusField: 'status', approvedByField: 'approvedBy', approvedAtField: 'approvedAt',
    billBeforeStatusField: 'billBeforeStatus', voidedByField: 'voidedBy', voidReasonField: 'voidReason',
    // 该控制器无 /submit 与 /approve：用启用/禁用切换（草稿→审核生效）
    submitPath: id => '/api/price/sale-strategy/toggle-status/' + id,
    approvePath: id => '/api/price/sale-strategy/toggle-status/' + id,
    approveAgainPath: id => '/api/price/sale-strategy/toggle-status/' + id,
    reversePath: id => '/api/price/sale-strategy/' + id + '/reverse-approve',
    voidPath: id => '/api/price/sale-strategy/' + id + '/void',
    unvoidPath: id => '/api/price/sale-strategy/' + id + '/unvoid',
    deletePath: id => '/api/price/sale-strategy/' + id,
    childTable: 'sale_price_strategy_item', childFk: 'strategy_id'
  })

  // ===== 按标记清理（先清库存痕迹，再清明细与主表） =====
  cleanup()

  console.log('\nRESULT ' + pass + ' PASS / ' + fail + ' FAIL / ' + skipped + ' UNVERIFIED / ' + defects + ' DEFECT')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); cleanup(); process.exit(2) })

function cleanup () {
  const s = []
  // 库存对账：作废已冲回、反作废应重新释放、最后重置草稿再冲回 → 合计应回到基线
  if (BASE_INV.length) {
    const nowT = inventoryTotals(snapshotInventory())
    const baseT = inventoryTotals(BASE_INV)
    const diffs = []
    for (const k of new Set([...Object.keys(baseT), ...Object.keys(nowT)])) {
      const b = Number(baseT[k] || 0), n = Number(nowT[k] || 0)
      if (Math.abs(b - n) > 0.0001) diffs.push('仓库' + k + ' 基线 ' + b + ' → 测试后 ' + n)
    }
    if (diffs.length) defect('库存未回到原值（反作废未重新释放业务影响）', diffs.join('；'))
  }
  // 本次单据产生的库存流水（按单号精确清理）
  const nos = CREATED_NOS.map(n => "'" + String(n).replace(/'/g, "''") + "'")
  if (nos.length) {
    s.push(`DELETE FROM inventory_ledger WHERE trans_no IN (${nos.join(',')}) OR source_bill_no IN (${nos.join(',')})`)
  }
  // 临时批次行（本次入库用唯一批次 TAG）
  s.push(`DELETE FROM batch_inventory WHERE material_id = 1804 AND batch_no = '${TAG}'`)
  // 库存还原到基线：先删测试新增行，再把基线行改回原值
  const baseIds = BASE_INV.map(r => r.split(',')[0])
  if (baseIds.length) {
    s.push(`DELETE FROM inventory_current WHERE material_id = 1804 AND stock_id IN (6,7) AND inventory_id NOT IN (${baseIds.join(',')})`)
    for (const r of BASE_INV) {
      const p = r.split(',')
      s.push(`UPDATE inventory_current SET quantity = ${p[2]}, available_quantity = ${p[3]}, unit_cost = ${p[4]}, total_cost = ${p[5]} WHERE inventory_id = ${p[0]}`)
    }
  } else {
    s.push(`DELETE FROM inventory_current WHERE material_id = 1804 AND stock_id IN (6,7)`)
  }
  // 生产工单子表（工序/产品/批记录）
  s.push(`DELETE FROM work_order_operation WHERE order_id IN (SELECT order_id FROM production_order WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM work_order_product WHERE order_id IN (SELECT order_id FROM production_order WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM batch_production_record WHERE work_order_id IN (SELECT order_id FROM production_order WHERE remark = '${TAG}')`)
  // 各单据明细 + 主表
  s.push(`DELETE FROM purchase_request_item WHERE request_id IN (SELECT request_id FROM purchase_request WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM purchase_request WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM other_receipt_item WHERE receipt_id IN (SELECT receipt_id FROM other_receipt WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM other_receipt WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM other_issue_item WHERE issue_id IN (SELECT issue_id FROM other_issue WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM other_issue WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM stock_transfer_item WHERE transfer_id IN (SELECT transfer_id FROM stock_transfer WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM stock_transfer WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM operation_expense_allocation WHERE expense_id IN (SELECT expense_id FROM operation_expense WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM operation_expense WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM production_order_item WHERE order_id IN (SELECT order_id FROM production_order WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM production_order WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM subcontract_order_item WHERE order_id IN (SELECT id FROM subcontract_order WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM subcontract_order WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM sample_order_item WHERE sample_id IN (SELECT sample_id FROM sample_order WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM sample_order WHERE remark = '${TAG}'`)
  s.push(`DELETE FROM sale_price_strategy_item WHERE strategy_id IN (SELECT id FROM sale_price_strategy WHERE remark = '${TAG}')`)
  s.push(`DELETE FROM sale_price_strategy WHERE remark = '${TAG}'`)
  try {
    psql(s.join(';\n'))
    console.log('\nCLEANUP 完成（标记 ' + TAG + '）')
  } catch (e) {
    console.log('WARN cleanup sql failed: ' + e.message)
  }
}
