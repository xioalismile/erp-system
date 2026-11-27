// 自动下推 · 重置草稿守卫 + 已取消只读 · 回归（2026-11-09）
// 覆盖：
//   ① 自动生成的销售订单/销售合同不能单独重置草稿；
//   ② 源单存在自动生成的有效下游时，源单也不能重置草稿；
//   ③ 已取消单据的字段全部只读。
const Path = require('path')
const { execFileSync } = require('child_process')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = 'http://localhost:8080'
const BASE = 'http://localhost:3000'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
const TAG = 'RESET-GUARD-TEST-' + Date.now()
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const today = new Date().toISOString().slice(0, 10)
let H = {}
const req = async (m, p, b) => j(await fetch(API + p, { method: m, headers: b ? { ...H, 'Content-Type': 'application/json' } : H, body: b ? JSON.stringify(b) : undefined }))
const setCfg = (k, v) => req('PUT', '/api/sys-config/' + encodeURIComponent(k), { configValue: v })

async function createContract (manualNo) {
  const r = await req('POST', '/api/sale/contracts', {
    contractNo: manualNo, contractDate: today, contractType: '销售合同', customerId: 15, currencyId: 1,
    exchangeRate: 1, discountRate: 0, deliveryDate: today, remark: TAG,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建合同失败: ' + JSON.stringify(r).slice(0, 200))
  return r.data
}
async function createOrder (manualNo) {
  const r = await req('POST', '/api/sale/orders', {
    orderNo: manualNo, orderDate: today, customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0,
    deliveryDate: today, remark: TAG,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建订单失败: ' + JSON.stringify(r).slice(0, 200))
  return r.data
}
async function approveContract (id) {
  await req('POST', '/api/sale/contracts/' + id + '/approve')
  return (await req('GET', '/api/sale/contracts/' + id)).data
}
async function approveOrder (id) {
  await req('POST', '/api/sale/orders/' + id + '/approve')
  return (await req('GET', '/api/sale/orders/' + id)).data
}
function cleanupBySql () {
  const sql = [
    "DELETE FROM sale_order_item WHERE order_id IN (SELECT order_id FROM sale_order WHERE remark LIKE '%" + TAG + "%');",
    "DELETE FROM sale_order WHERE remark LIKE '%" + TAG + "%';",
    "DELETE FROM sale_contract_item WHERE contract_id IN (SELECT contract_id FROM sale_contract WHERE remark LIKE '%" + TAG + "%');",
    "DELETE FROM sale_contract WHERE remark LIKE '%" + TAG + "%';"
  ].join(' ')
  try {
    execFileSync('psql', ['-h', 'localhost', '-p', '5432', '-U', 'erp_user', '-d', 'erp_db', '-v', 'ON_ERROR_STOP=1', '-c', sql], {
      env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD || 'Erp@2026' },
      stdio: 'ignore'
    })
  } catch (e) {
    console.log('WARN cleanup sql failed: ' + e.message)
  }
}

;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '管理员登录')

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  const errs = []
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
  page.on('pageerror', e => errs.push('pageerror: ' + String(e).slice(0, 200)))
  await page.addInitScript(t => {
    localStorage.setItem('token', t)
    localStorage.setItem('username', 'admin'); localStorage.setItem('realName', 'admin')
    localStorage.setItem('userId', '1'); localStorage.setItem('tenantId', '1'); localStorage.setItem('tenantName', 'A公司-完整模式')
    localStorage.setItem('roles', JSON.stringify(['ADMIN'])); localStorage.setItem('permissions', JSON.stringify(['*']))
  }, login.data.token)

  // A. 合同 → 自动订单
  await setCfg('sale.auto.push.contract.to.order', 'true')
  await setCfg('sale.auto.push.order.to.contract', 'false')
  const c = await createContract()
  const ca = await approveContract(c.contractId)
  assert(ca?.status === '已生效', '源合同已生效', ca?.status)
  const orders = (await req('GET', '/api/sale/orders?current=1&size=500')).data.records || []
  const ao = orders.find(o => o.sourceBillNo === c.contractNo)
  assert(!!ao && ao.status === '已生效', '自动订单已生成并生效')

  if (ao) {
    const r1 = await req('POST', '/api/sale/orders/' + ao.orderId + '/reverse-approve')
    assert(r1.code !== 200 && String(r1.message || '').includes('自动生成'), '自动生成的订单单独重置草稿被拦截', JSON.stringify(r1).slice(0, 200))
    const r2 = await req('POST', '/api/sale/contracts/' + c.contractId + '/reverse-approve')
    assert(r2.code !== 200 && String(r2.message || '').includes('下游'), '源合同存在自动订单时重置草稿被拦截', JSON.stringify(r2).slice(0, 200))

    await page.goto(BASE + '/sale/orders/' + ao.orderId, { waitUntil: 'domcontentloaded', timeout: 120000 })
    await page.waitForTimeout(6000)
    const resetCount = await page.locator('button:visible', { hasText: '重置草稿' }).count()
    assert(resetCount === 0, '自动订单编辑页不显示重置草稿按钮', '实际=' + resetCount)

    await page.goto(BASE + '/sale/contracts/' + c.contractId, { waitUntil: 'domcontentloaded', timeout: 120000 })
    await page.waitForTimeout(5000)
    const pushCount = await page.locator('button:visible', { hasText: '下推生成销售订单' }).count()
    assert(pushCount === 0, '源合同不再显示下推生成销售订单')
  }

  // B. 订单 → 自动合同
  await setCfg('sale.auto.push.contract.to.order', 'false')
  await setCfg('sale.auto.push.order.to.contract', 'true')
  const o2 = await createOrder()
  const o2a = await approveOrder(o2.orderId)
  assert(o2a?.status === '已生效', '源订单已生效', o2a?.status)
  const contracts = (await req('GET', '/api/sale/contracts?current=1&size=500')).data.records || []
  const ac = contracts.find(x => x.sourceBillNo === o2.orderNo)
  assert(!!ac && ac.status === '已生效', '自动合同已生成并生效')
  if (ac) {
    const r3 = await req('POST', '/api/sale/contracts/' + ac.contractId + '/reverse-approve')
    assert(r3.code !== 200 && String(r3.message || '').includes('自动生成'), '自动生成的合同单独重置草稿被拦截', JSON.stringify(r3).slice(0, 200))
    const r4 = await req('POST', '/api/sale/orders/' + o2.orderId + '/reverse-approve')
    assert(r4.code !== 200 && String(r4.message || '').includes('下游'), '源订单存在自动合同时重置草稿被拦截', JSON.stringify(r4).slice(0, 200))

    await page.goto(BASE + '/sale/contracts/' + ac.contractId, { waitUntil: 'domcontentloaded', timeout: 120000 })
    await page.waitForTimeout(6000)
    const resetCount2 = await page.locator('button:visible', { hasText: '重置草稿' }).count()
    assert(resetCount2 === 0, '自动合同编辑页不显示重置草稿按钮', '实际=' + resetCount2)
  }

  // C. 已取消单据只读
  await setCfg('sale.auto.push.contract.to.order', 'false')
  await setCfg('sale.auto.push.order.to.contract', 'false')
  const c3 = await createContract()
  await req('POST', '/api/sale/contracts/' + c3.contractId + '/cancel', { reason: 'readonly test' })
  const c3a = (await req('GET', '/api/sale/contracts/' + c3.contractId)).data
  assert(c3a?.status === '已取消', '合同已取消', c3a?.status)
  await page.goto(BASE + '/sale/contracts/' + c3.contractId, { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForTimeout(6000)
  const editable = await page.evaluate(() => {
    const out = []
    document.querySelectorAll('input, textarea').forEach(el => {
      const st = getComputedStyle(el)
      if (st.display === 'none' || st.visibility === 'hidden' || el.offsetParent === null) return
      const r = el.getBoundingClientRect(); if (!r.width || !r.height) return
      if (el.disabled || el.readOnly) return
      out.push({ ph: el.getAttribute('placeholder') || '', cls: String(el.className || '').slice(0, 60) })
    })
    return out
  })
  const formEditable = editable.filter(x => !String(x.ph).includes('搜索菜单'))
  assert(formEditable.length === 0, '已取消合同的业务字段全部只读', JSON.stringify(formEditable))

  console.log('CONSOLE_ERRORS=' + JSON.stringify(errs.slice(0, 6)))
  assert(errs.length === 0, '页面控制台 0 报错')
  await browser.close()

  cleanupBySql()
  await setCfg('sale.auto.push.contract.to.order', 'false')
  await setCfg('sale.auto.push.order.to.contract', 'false')
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); cleanupBySql(); process.exit(2) })
