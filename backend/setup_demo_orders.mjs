// setup_demo_orders.mjs — 创建不同状态的销售订单用于前端验证
import https from 'http'

const BASE = 'http://localhost:8080/api'
let TOKEN = null

function request(method, path, body = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(BASE + path)
    const headers = { 'Content-Type': 'application/json' }
    if (TOKEN) headers['Authorization'] = `Bearer ${TOKEN}`
    const data = body ? JSON.stringify(body) : null
    const req = https.request({ hostname: url.hostname, port: url.port, path: url.pathname + url.search, method, headers }, (res) => {
      let body = ''
      res.on('data', d => body += d)
      res.on('end', () => {
        try { resolve({ ...JSON.parse(body), __http: res.statusCode }) }
        catch { resolve({ code: res.statusCode, raw: body.substring(0, 300) }) }
      })
    })
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

async function main() {
  // 1. 登录
  const login = await request('POST', '/auth/login', { username: 'admin', password: 'admin123' })
  TOKEN = login.data?.token
  console.log('登录:', login.code === 200 ? 'OK' : 'FAIL')

  // 查询客户和物料
  const [cust, mat] = await Promise.all([request('GET', '/sale/customers'), request('GET', '/sale/materials')])
  const customerId = cust.data?.[0]?.customerId || 2
  const customerName = cust.data?.[0]?.customerName || '测试客户'
  const materialId = mat.data?.[0]?.materialId || 1
  const materialCode = mat.data?.[0]?.materialCode || 'MAT001'
  const materialName = mat.data?.[0]?.materialName || '测试物料'
  console.log(`客户: ${customerName}(${customerId}), 物料: ${materialName}(${materialId})`)

  const orderData = {
    customerId, customerName, orderDate: '2026-08-15', deliveryDate: '2026-08-25',
    salerId: 1, currencyId: 1, exchangeRate: 1, warehouseId: 1,
    status: '草稿', approvalStatus: '草稿',
    items: [{ lineNo: 1, materialId, materialCode, materialName, unitId: 1,
      quantity: 10, unitPrice: 200, amount: 2000, taxRate: 13, taxAmount: 260, netAmount: 2260 }]
  }

  const results = {}

  // 2. 草稿状态订单
  const draft = await request('POST', '/sale/orders', { ...orderData, remark: '前端验证-草稿状态' })
  results.draft = { id: draft.data?.orderId, no: draft.data?.orderNo }
  console.log(`草稿订单: ${results.draft.no} (ID=${results.draft.id})`)

  // 3. 待审核状态订单
  const pending = await request('POST', '/sale/orders', { ...orderData, remark: '前端验证-待审核状态' })
  if (pending.data?.orderId) {
    await request('POST', `/sale/orders/${pending.data.orderId}/submit`)
    results.pending = { id: pending.data.orderId, no: pending.data.orderNo }
    console.log(`待审核订单: ${results.pending.no} (ID=${results.pending.id})`)
  }

  // 4. 已审核状态订单
  const approved = await request('POST', '/sale/orders', { ...orderData, remark: '前端验证-已审核状态' })
  if (approved.data?.orderId) {
    await request('POST', `/sale/orders/${approved.data.orderId}/submit`)
    await request('POST', `/sale/orders/${approved.data.orderId}/approve`)
    // 清理AR
    const arList = await request('GET', '/finance/ar?size=200')
    if (arList.data?.records) {
      for (const ar of arList.data.records) {
        if (ar.sourceBillNo === approved.data.orderNo || (ar.remark && ar.remark.includes(approved.data.orderNo))) {
          await request('DELETE', `/finance/ar/${ar.arId}`)
          break
        }
      }
    }
    results.approved = { id: approved.data.orderId, no: approved.data.orderNo }
    console.log(`已审核订单: ${results.approved.no} (ID=${results.approved.id})`)
  }

  // 5. 已结案状态订单
  const closed = await request('POST', '/sale/orders', { ...orderData, remark: '前端验证-已结案状态' })
  if (closed.data?.orderId) {
    await request('POST', `/sale/orders/${closed.data.orderId}/submit`)
    await request('POST', `/sale/orders/${closed.data.orderId}/approve`)
    // 清理AR
    const arList2 = await request('GET', '/finance/ar?size=200')
    if (arList2.data?.records) {
      for (const ar of arList2.data.records) {
        if (ar.sourceBillNo === closed.data.orderNo || (ar.remark && ar.remark.includes(closed.data.orderNo))) {
          await request('DELETE', `/finance/ar/${ar.arId}`)
          break
        }
      }
    }
    await request('POST', `/sale/orders/${closed.data.orderId}/close-order`)
    results.closed = { id: closed.data.orderId, no: closed.data.orderNo }
    console.log(`已结案订单: ${results.closed.no} (ID=${results.closed.id})`)
  }

  // 6. 已作废状态订单
  const voided = await request('POST', '/sale/orders', { ...orderData, remark: '前端验证-已作废状态' })
  if (voided.data?.orderId) {
    await request('POST', `/sale/orders/${voided.data.orderId}/submit`)
    await request('POST', `/sale/orders/${voided.data.orderId}/approve`)
    // 清理AR
    const arList3 = await request('GET', '/finance/ar?size=200')
    if (arList3.data?.records) {
      for (const ar of arList3.data.records) {
        if (ar.sourceBillNo === voided.data.orderNo || (ar.remark && ar.remark.includes(voided.data.orderNo))) {
          await request('DELETE', `/finance/ar/${ar.arId}`)
          break
        }
      }
    }
    await request('POST', `/sale/orders/${voided.data.orderId}/void`, { reason: '前端验证作废' })
    results.voided = { id: voided.data.orderId, no: voided.data.orderNo }
    console.log(`已作废订单: ${results.voided.no} (ID=${results.voided.id})`)
  }

  console.log('\n=== 演示订单创建完成 ===')
  console.log(JSON.stringify(results, null, 2))
  console.log('\n前端验证URL:')
  if (results.draft) console.log(`  草稿: http://localhost:3000/#/sale/orders/edit/${results.draft.id}`)
  if (results.pending) console.log(`  待审核: http://localhost:3000/#/sale/orders/edit/${results.pending.id}`)
  if (results.approved) console.log(`  已审核: http://localhost:3000/#/sale/orders/edit/${results.approved.id}`)
  if (results.closed) console.log(`  已结案: http://localhost:3000/#/sale/orders/edit/${results.closed.id}`)
  if (results.voided) console.log(`  已作废: http://localhost:3000/#/sale/orders/edit/${results.voided.id}`)
}

main().catch(e => console.error('Error:', e))
