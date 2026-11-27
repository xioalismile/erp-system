import http from 'http'
const BASE = 'http://localhost:8080/api'
let TOKEN = null
function req(method, path, body) {
  return new Promise((resolve) => {
    const url = new URL(BASE + path)
    const headers = { 'Content-Type': 'application/json' }
    if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN
    const data = body ? JSON.stringify(body) : null
    const r = http.request({ hostname: url.hostname, port: url.port, path: url.pathname + url.search, method, headers }, (res) => {
      let b = ''
      res.on('data', d => b += d)
      res.on('end', () => { try { resolve(JSON.parse(b)) } catch { resolve({ code: res.statusCode, raw: b.substring(0, 200) }) } })
    })
    r.on('error', e => resolve({ error: e.message }))
    if (data) r.write(data)
    r.end()
  })
}
async function main() {
  const login = await req('POST', '/auth/login', { username: 'admin', password: 'admin123' })
  TOKEN = login.data?.token
  const [cust, mat] = await Promise.all([req('GET', '/sale/customers'), req('GET', '/sale/materials')])
  const cid = cust.data?.[0]?.customerId || 2, cn = cust.data?.[0]?.customerName || '测试客户'
  const mid = mat.data?.[0]?.materialId || 1, mc = mat.data?.[0]?.materialCode || 'MAT001', mn = mat.data?.[0]?.materialName || '测试物料'
  const od = { customerId: cid, customerName: cn, orderDate: '2026-08-15', deliveryDate: '2026-08-25', salerId: 1, currencyId: 1, exchangeRate: 1, warehouseId: 1, status: '草稿', approvalStatus: '草稿', items: [{ lineNo: 1, materialId: mid, materialCode: mc, materialName: mn, unitId: 1, quantity: 10, unitPrice: 200, amount: 2000, taxRate: 13, taxAmount: 260, netAmount: 2260 }] }
  async function cleanAR(no) { const l = await req('GET', '/finance/ar?size=200'); if (l.data?.records) { for (const a of l.data.records) { if (a.sourceBillNo === no || (a.remark && a.remark.includes(no))) { await req('DELETE', '/finance/ar/' + a.arId); break } } } }
  const R = {}
  const d = await req('POST', '/sale/orders', { ...od, remark: '前端验证-草稿' }); R.draft = { id: d.data?.orderId, no: d.data?.orderNo }
  const p = await req('POST', '/sale/orders', { ...od, remark: '前端验证-待审核' }); if (p.data?.orderId) { await req('POST', '/sale/orders/' + p.data.orderId + '/submit'); R.pending = { id: p.data.orderId, no: p.data.orderNo } }
  const a = await req('POST', '/sale/orders', { ...od, remark: '前端验证-已审核' }); if (a.data?.orderId) { await req('POST', '/sale/orders/' + a.data.orderId + '/submit'); await req('POST', '/sale/orders/' + a.data.orderId + '/approve'); await cleanAR(a.data.orderNo); R.approved = { id: a.data.orderId, no: a.data.orderNo } }
  const c = await req('POST', '/sale/orders', { ...od, remark: '前端验证-已结案' }); if (c.data?.orderId) { await req('POST', '/sale/orders/' + c.data.orderId + '/submit'); await req('POST', '/sale/orders/' + c.data.orderId + '/approve'); await cleanAR(c.data.orderNo); await req('POST', '/sale/orders/' + c.data.orderId + '/close-order'); R.closed = { id: c.data.orderId, no: c.data.orderNo } }
  const v = await req('POST', '/sale/orders', { ...od, remark: '前端验证-已作废' }); if (v.data?.orderId) { await req('POST', '/sale/orders/' + v.data.orderId + '/submit'); await req('POST', '/sale/orders/' + v.data.orderId + '/approve'); await cleanAR(v.data.orderNo); await req('POST', '/sale/orders/' + v.data.orderId + '/void', { reason: '验证作废' }); R.voided = { id: v.data.orderId, no: v.data.orderNo } }
  console.log(JSON.stringify(R, null, 2))
}
main().catch(e => console.error(e))
