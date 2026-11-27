// 含税价金额尾差修复 · 接口级回归（2026-10-18）
// 断言：含税价明细的税额 = 含税合计 - 不含税金额，价税合计正好等于 含税单价×数量（不再多 1 分）
// 用例：4.00×140=560.00 / 2.20×2000=4400.00 / 8.20×150=1230.00（121 生产实报的三张单）
// 运行：node deploy/local-run/verify-tax-rounding-fix.cjs [API_BASE]
const API = process.argv[2] || 'http://localhost:8080'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra !== undefined ? ' | ' + JSON.stringify(extra).slice(0, 300) : '')) } }

;(async () => {
  const login = await (await fetch(API + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD })
  })).json()
  const token = login?.data?.token
  if (!token) { console.log('LOGIN FAILED: ' + JSON.stringify(login).slice(0, 200)); process.exit(1) }
  const H = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
  const api = async (method, path, body) => {
    const r = await fetch(API + '/api' + path, { method, headers: H, body: body ? JSON.stringify(body) : undefined })
    try { return await r.json() } catch (e) { return { httpStatus: r.status } }
  }
  const today = new Date().toISOString().slice(0, 10)

  // 含税业务开关：临时开启（结束恢复）
  const cfg = await api('GET', '/sys-config/sys.tax.enabled')
  const origTaxEnabled = String(cfg?.data?.configValue ?? cfg?.data ?? 'false')
  if (origTaxEnabled !== 'true') {
    await api('PUT', '/sys-config/sys.tax.enabled', { configValue: 'true' })
    await api('POST', '/sys-config/refresh', {})
  }

  let orderId = null
  try {
    const body = {
      orderDate: today, customerId: 1, customerName: '【公海客户】示例客户公司', salerId: 21,
      currencyId: 1, warehouseId: 9, taxCalcMethod: '含税价', remark: 'TAX-ROUNDING-TEST',
      exchangeRate: 1,
      items: [
        { materialId: 1612, materialCode: 'G10406', materialName: '巡更卡', specification: '30mm', unitId: 1, quantity: 140, unitPrice: 4.00, taxRate: 13, deliveryDate: today, discountRate: 0 },
        { materialId: 1612, materialCode: 'G10406', materialName: '巡更卡', specification: '30mm', unitId: 1, quantity: 2000, unitPrice: 2.20, taxRate: 13, deliveryDate: today, discountRate: 0 },
        { materialId: 1612, materialCode: 'G10406', materialName: '巡更卡', specification: '30mm', unitId: 1, quantity: 150, unitPrice: 8.20, taxRate: 13, deliveryDate: today, discountRate: 0 }
      ]
    }
    const saved = await api('POST', '/sale/orders', body)
    assert(saved?.code === 200, 'create draft order (含税价)', saved)
    orderId = saved?.data?.orderId
    const det = await api('GET', '/sale/orders/' + orderId)
    const d = det?.data || {}
    const items = d.items || []
    const want = [
      { qty: 140, price: 4.00, amount: 495.58, tax: 64.42, net: 560.00 },
      { qty: 2000, price: 2.20, amount: 3893.81, tax: 506.19, net: 4400.00 },
      { qty: 150, price: 8.20, amount: 1088.50, tax: 141.50, net: 1230.00 }
    ]
    assert(items.length === 3, 'detail has 3 lines', items.length)
    want.forEach((w, i) => {
      const it = items[i] || {}
      assert(Math.abs(Number(it.amount) - w.amount) < 0.001, `line${i + 1}.amount=${w.amount}`, it.amount)
      assert(Math.abs(Number(it.taxAmount) - w.tax) < 0.001, `line${i + 1}.tax=${w.tax} (含税合计-不含税)`, it.taxAmount)
      assert(Math.abs(Number(it.netAmount) - w.net) < 0.001, `line${i + 1}.net=${w.net} (含税单价×数量)`, it.netAmount)
      assert(Math.abs(Number(it.priceWithTax) * Number(it.quantity) - w.net) < 0.001, `line${i + 1}.gross=qty×price`, { p: it.priceWithTax, q: it.quantity })
    })
    assert(Math.abs(Number(d.taxAmount) - 712.11) < 0.001, 'header.tax=712.11', d.taxAmount)
    assert(Math.abs(Number(d.netAmount) - 6190.00) < 0.001, 'header.net=6190.00', d.netAmount)
  } finally {
    if (orderId) {
      const del = await api('DELETE', '/sale/orders/' + orderId)
      console.log((del?.code === 200 ? 'CLEANUP OK' : 'CLEANUP FAILED') + ' orderId=' + orderId)
    }
    if (origTaxEnabled !== 'true') {
      await api('PUT', '/sys-config/sys.tax.enabled', { configValue: origTaxEnabled })
      await api('POST', '/sys-config/refresh', {})
    }
  }
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})()
