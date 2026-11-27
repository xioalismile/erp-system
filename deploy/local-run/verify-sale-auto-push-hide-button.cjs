// 自动下推后「下推」按钮隐藏 · 点击级回归（2026-11-08）
// 场景：销售合同在编辑页点「审核」生效 → 自动生成销售订单 → 同一页面下推按钮立即更新：
//       不再出现「下推生成销售订单」；如仍有按钮必须是「下推生成采购合同」，不能是笼统的「下推」。
const Path = require('path')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = process.argv[2] || 'http://localhost:8080'
const BASE = process.argv[3] || 'http://localhost:3000'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }

;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  const H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  const req = async (m, p, b) => j(await fetch(API + p, { method: m, headers: b ? { ...H, 'Content-Type': 'application/json' } : H, body: b ? JSON.stringify(b) : undefined }))
  const setCfg = (k, v) => req('PUT', '/api/sys-config/' + encodeURIComponent(k), { configValue: v })
  await setCfg('sale.auto.push.contract.to.order', 'true')
  await setCfg('sale.auto.push.order.to.contract', 'false')

  const today = new Date().toISOString().slice(0, 10)
  const c = await req('POST', '/api/sale/contracts', {
    contractDate: today, contractType: '销售合同', customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0, deliveryDate: today,
    remark: 'PUSH-HIDE-CHECK',
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  const id = c.data.contractId
  assert(!!id, '创建销售合同草稿')
  const before = (await req('GET', '/api/sale/contracts/' + id)).data
  assert(before.status === '草稿', '合同初始为草稿', before.status)

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
  await page.goto(BASE + '/sale/contracts/' + id, { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForTimeout(5000)

  // 草稿态：按全站口径不显示下推入口
  const draftPush = page.locator('button:visible', { hasText: '下推' })
  assert(await draftPush.count() === 0, '草稿态不显示下推入口')

  // 在页面内点「审核」→ 确认 → 合同生效 → 自动生成订单
  const approveBtn = page.locator('button:visible', { hasText: '审核' }).first()
  assert(await approveBtn.count() > 0, '草稿态可见「审核」按钮')
  await approveBtn.click()
  await page.waitForTimeout(700)
  const confirmBtn = page.locator('.el-message-box__btns button', { hasText: '确定' })
  assert(await confirmBtn.count() > 0, '点审核后弹出确认框')
  await confirmBtn.first().click()
  await page.waitForTimeout(7000)

  const after = (await req('GET', '/api/sale/contracts/' + id)).data
  assert(after.status === '已生效', '页面点审核后合同为已生效', after.status)
  const orderList = await req('GET', '/api/sale/orders?current=1&size=500')
  const autoOrder = (orderList.data?.records || []).find(o => o.sourceBillNo === after.contractNo)
  assert(!!autoOrder && autoOrder.status === '已生效', '页面审核后已自动生成已生效销售订单', JSON.stringify(autoOrder && { no: autoOrder.orderNo, st: autoOrder.status }))

  // 同页面不刷新：下推入口必须立即更新
  await page.waitForTimeout(2500)
  const pushTexts = await page.locator('button:visible', { hasText: '下推' }).allInnerTexts()
  console.log('PUSH_TEXTS_AFTER=' + JSON.stringify(pushTexts))
  assert(!pushTexts.some(t => t.includes('下推生成销售订单')), '同页面不再出现「下推生成销售订单」')
  assert(!pushTexts.some(t => t.trim() === '下推'), '不再出现笼统的「下推」按钮')
  assert(pushTexts.every(t => t.includes('采购合同')), '剩余下推入口只针对采购合同', JSON.stringify(pushTexts))

  // 服务端状态佐证：销售订单目标 FULL
  const ps = await req('GET', '/api/bill-link/push-status?billType=SALE_CONTRACT&ids=' + id)
  const so = ps.data?.[String(id)]?.targets?.SALE_ORDER
  assert(so?.state === 'FULL', '服务端下推状态：销售订单 FULL', JSON.stringify(so))

  console.log('CONSOLE_ERRORS=' + JSON.stringify(errs.slice(0, 6)))
  assert(errs.length === 0, '页面控制台 0 报错')
  await browser.close()

  // cleanup
  if (autoOrder) { await req('POST', '/api/sale/orders/' + autoOrder.orderId + '/reverse-approve'); await req('DELETE', '/api/sale/orders/' + autoOrder.orderId) }
  await req('POST', '/api/sale/contracts/' + id + '/reverse-approve')
  await req('DELETE', '/api/sale/contracts/' + id)
  await setCfg('sale.auto.push.contract.to.order', 'false')
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
