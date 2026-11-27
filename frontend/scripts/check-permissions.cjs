/* eslint-disable no-console */
/**
 * 权限运行体检（功能体检第二层）
 *
 * 用途：用"受限账号"和"管理员账号"各调一遍列表 / 明细报表接口，核对：
 *   1. 字段级权限：受限账号的响应里，配置为不可见的字段是否真的被剔除；
 *   2. 数据权限：受限账号（本人数据）看到的行数是否真的少于管理员；
 *   3. 覆盖缺口：配置了字段权限但接口没接过滤的，会表现为"字段仍在"。
 *
 * 用法（不要把密码写进代码，用环境变量）：
 *   $env:QA_USER='qa_perm'; $env:QA_PASS='...'; $env:ADMIN_USER='admin'; $env:ADMIN_PASS='...'
 *   node scripts/check-permissions.cjs [--base http://localhost:8080]
 *
 * 只读接口调用，不创建/修改业务数据。
 */
const BASE = process.env.ERP_BASE || 'http://localhost:8080'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASS = process.env.ADMIN_PASS || ''
const QA_USER = process.env.QA_USER || ''
const QA_PASS = process.env.QA_PASS || ''

async function login(username, password) {
  const r = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const j = await r.json()
  if (j.code !== 200 || !j.data || !j.data.token) {
    throw new Error(`登录失败 ${username}: ${j.message || j.code}`)
  }
  return j.data.token
}

async function get(token, path) {
  const r = await fetch(BASE + path, { headers: { Authorization: 'Bearer ' + token } })
  const text = await r.text()
  let j = null
  try { j = JSON.parse(text) } catch (_) { /* 非 JSON */ }
  return { status: r.status, json: j }
}

const recordsOf = (j) => (j && j.data && (j.data.records || j.data.list || j.data.rows)) || []
const totalOf = (j) => {
  if (!j || !j.data) return null
  const t = j.data.total
  return typeof t === 'number' ? t : (Array.isArray(j.data) ? j.data.length : null)
}

const QA_SCOPE = (process.env.QA_SCOPE || 'ALL').toUpperCase()

/** 列表接口体检配置：hidden=该页配置为不可见的字段（用于验证是否真的剔除） */
const LIST_ENDPOINTS = [
  { name: '销售订单', path: '/api/sale/orders', hidden: ['contactPhone', 'contactPerson'] },
  { name: '销售出库单', path: '/api/sale/deliveries', hidden: ['contactPhone'] },
  { name: '采购订单', path: '/api/purchase/orders', hidden: ['contactPhone'] },
  { name: '采购申请单', path: '/api/purchase/requests', hidden: ['remark'] },
  { name: '采购入库单', path: '/api/purchase/inbounds', hidden: ['contactPhone'] },
  { name: '其他入库单', path: '/api/inventory/other-receipts/page', hidden: ['remark'] },
  { name: '生产工单', path: '/api/production/workorders', hidden: [] },
  { name: '收款单', path: '/api/finance/receipts', hidden: [] },
  { name: '费用分摊', path: '/api/finance/operation-expense/page', hidden: [] }
]

/** 明细报表接口：验证报表路径是否接字段权限 */
const REPORT_ENDPOINTS = [
  { name: '销售订单明细报表（已登记字段权限）', path: '/api/report/sale/order-detail?page=1&size=3&cols=orderNo,contactPhone,contactPerson', hidden: ['contactPhone', 'contactPerson'] },
  { name: '采购申请明细报表（未登记字段权限）', path: '/api/report/purchase/request-detail?page=1&size=3&cols=requestNo,remark', hidden: ['remark'] }
]

async function main() {
  if (!ADMIN_PASS || !QA_USER || !QA_PASS) {
    console.error('缺少账号环境变量：ADMIN_PASS / QA_USER / QA_PASS（密码不要写进代码）')
    process.exit(2)
  }
  const admin = await login(ADMIN_USER, ADMIN_PASS)
  const qa = await login(QA_USER, QA_PASS)

  const rows = []
  for (const ep of LIST_ENDPOINTS) {
    const [ra, rq] = await Promise.all([
      get(admin, ep.path + '?current=1&size=20'),
      get(qa, ep.path + '?current=1&size=20')
    ])
    const adminRec = recordsOf(ra.json)[0] || {}
    const qaRec = recordsOf(rq.json)[0] || {}
    const hiddenChecks = ep.hidden.map((f) => ({
      field: f,
      adminHas: Object.prototype.hasOwnProperty.call(adminRec, f),
      qaHas: Object.prototype.hasOwnProperty.call(qaRec, f)
    }))
    rows.push({
      kind: 'list',
      name: ep.name,
      path: ep.path,
      adminStatus: ra.status,
      qaStatus: rq.status,
      adminTotal: totalOf(ra.json),
      qaTotal: totalOf(rq.json),
      hiddenChecks,
      verdict: judgeList(totalOf(ra.json), totalOf(rq.json), hiddenChecks)
    })
  }

  for (const ep of REPORT_ENDPOINTS) {
    const [ra, rq] = await Promise.all([get(admin, ep.path), get(qa, ep.path)])
    const adminRec = recordsOf(ra.json)[0] || {}
    const qaRec = recordsOf(rq.json)[0] || {}
    const hiddenChecks = ep.hidden.map((f) => ({
      field: f,
      adminHas: Object.prototype.hasOwnProperty.call(adminRec, f),
      qaHas: Object.prototype.hasOwnProperty.call(qaRec, f)
    }))
    rows.push({
      kind: 'report',
      name: ep.name,
      path: ep.path,
      adminStatus: ra.status,
      qaStatus: rq.status,
      adminTotal: totalOf(ra.json),
      qaTotal: totalOf(rq.json),
      hiddenChecks,
      verdict: hiddenChecks.length
        ? (hiddenChecks.some((h) => h.qaHas) ? '字段未剔除（接口未接字段权限）' : '字段已剔除')
        : '未配置隐藏字段（仅看接口可达）'
    })
  }

  console.log(JSON.stringify(rows, null, 2))
  const fs = require('fs')
  const path = require('path')
  const out = path.resolve(__dirname, '../../docs/功能体检-权限运行体检.json')
  fs.writeFileSync(out, JSON.stringify({ generatedAt: new Date().toISOString(), rows }, null, 2), 'utf8')
  console.log('JSON:', out)
}

function judgeList(adminTotal, qaTotal, hiddenChecks) {
  const parts = []
  if (adminTotal != null && qaTotal != null) {
    if (QA_SCOPE === 'SELF') {
      if (adminTotal > 0 && qaTotal === 0) parts.push('数据权限生效（0 行）')
      else if (adminTotal > 0 && qaTotal >= adminTotal) parts.push('数据权限未生效（行数相同）')
      else if (adminTotal > 0) parts.push(`数据权限部分生效（管理员 ${adminTotal} / 受限 ${qaTotal}）`)
      else parts.push('数据为空，无法判定')
    } else {
      parts.push(adminTotal > 0 && qaTotal < adminTotal
        ? '数据被额外过滤（ALL 范围下不应减少）'
        : '数据权限按 ALL 放行（本阶段只验字段）')
    }
  }
  if (hiddenChecks.length) {
    if (qaTotal === 0) {
      parts.push('隐藏字段无法判定（受限账号无数据）')
    } else {
      const leaked = hiddenChecks.filter((h) => h.qaHas && h.adminHas)
      parts.push(leaked.length ? `字段未剔除：${leaked.map((h) => h.field).join('、')}` : '隐藏字段已剔除')
    }
  }
  return parts.join('；')
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
