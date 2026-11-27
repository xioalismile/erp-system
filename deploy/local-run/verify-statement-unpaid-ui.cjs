// 客户对账单 / 供应商对账单 · 已收未收剩余 点击级验证（真实后端 8080 + 前端 3000）
// 断言：汇总页签含各类来源「已收/未收」与「本期未收/剩余未收」列；明细页签含「未收金额/剩余未收」列；
//       首行金额与接口值一致（不是空白/占位符）；控制台 0 报错。
const Path = require('path')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = process.argv[2] || 'http://localhost:8080'
const BASE = process.argv[3] || 'http://localhost:3000'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const money = v => '¥' + Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'admin123' }) }))
  const TOKEN = login?.data?.token
  assert(!!TOKEN, '登录')
  const H = { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const errs = []
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
  page.on('pageerror', e => errs.push('pageerror: ' + String(e).slice(0, 200)))
  await page.addInitScript(t => {
    localStorage.setItem('token', t)
    localStorage.setItem('username', 'admin')
    localStorage.setItem('realName', 'admin')
    localStorage.setItem('userId', '1')
    localStorage.setItem('tenantId', '1')
    localStorage.setItem('tenantName', 'A公司-完整模式')
    localStorage.setItem('roles', JSON.stringify(['ADMIN']))
    localStorage.setItem('permissions', JSON.stringify(['*']))
  }, TOKEN)
  await page.route('**/api/**', route => {
    const u = new URL(route.request().url())
    if (!u.pathname.startsWith('/api/')) return route.continue()
    const api = new URL(API)
    u.protocol = api.protocol; u.hostname = api.hostname; u.port = api.port
    return route.continue({ url: u.toString() })
  })

  try {
    // ==================== 客户对账单 ====================
    const cs = await j(await fetch(API + '/api/report/standard/sale/customer-statement/summary', { headers: H }))
    const csRow = (cs.data?.records || [])[0] || {}
    const csDetail = await j(await fetch(API + '/api/report/standard/sale/customer-statement/detail?page=1&size=20', { headers: H }))
    const csDetailRow = (csDetail.data?.records || [])[0] || {}
    await page.goto(BASE + '/sale/report/customer-statement', { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.el-table__body tr', { timeout: 30000 })
    await page.waitForTimeout(1200)
    let heads = (await page.locator('.el-tab-pane:visible thead').allInnerTexts()).join('\n')
    for (const h of ['订单已收', '订单未收', '出库已收', '出库未收', '发票已收', '发票未收', '本期未收', '剩余未收']) {
      assert(heads.includes(h), '客户汇总含列：' + h)
    }
    let firstRow = await page.locator('.el-tab-pane:visible .el-table__body').first().locator('tr').first().innerText()
    assert(firstRow.includes(money(csRow.saleOrderPaidAmount)), '客户汇总销售订单已收与实际值一致', money(csRow.saleOrderPaidAmount))
    assert(firstRow.includes(money(csRow.saleOrderUnpaidAmount)), '客户汇总销售订单未收与实际值一致', money(csRow.saleOrderUnpaidAmount))
    assert(firstRow.includes(money(csRow.currentUnpaid)), '客户汇总本期未收与实际值一致', money(csRow.currentUnpaid))
    assert(firstRow.includes(money(csRow.endBalance)), '客户汇总剩余未收与实际值一致', money(csRow.endBalance))

    // 切明细页签：以销售订单为主表，下推金额挂在订单行
    await page.getByRole('tab', { name: /明细统计/ }).click()
    await page.waitForTimeout(1500)
    heads = (await page.locator('.el-tab-pane:visible thead').allInnerTexts()).join('\n')
    for (const h of ['订单金额', '订单已收', '订单未收', '出库金额', '出库已收', '出库未收', '发票金额', '发票已收', '发票未收', '退货金额']) {
      assert(heads.includes(h), '客户明细含列：' + h)
    }
    assert(!heads.includes('单据类型'), '客户明细不再按单据类型分行')
    const csLastTh = page.locator('.el-tab-pane:visible th').filter({ hasText: '退货金额' }).first()
    const csLastBox = await csLastTh.boundingBox()
    assert(!!csLastBox && csLastBox.x + csLastBox.width <= 1440, '客户明细最后一列首屏可见', csLastBox ? String(Math.round(csLastBox.x + csLastBox.width)) : 'no-box')
    firstRow = await page.locator('.el-tab-pane:visible .el-table__body').first().locator('tr').first().innerText()
    assert(firstRow.includes(money(csDetailRow.orderAmount)), '客户明细订单金额与实际值一致', money(csDetailRow.orderAmount))
    assert(firstRow.includes(money(csDetailRow.paidAmount)), '客户明细订单已收与实际值一致', money(csDetailRow.paidAmount))
    assert(firstRow.includes(money(csDetailRow.unpaidAmount)), '客户明细订单未收与实际值一致', money(csDetailRow.unpaidAmount))
    assert(firstRow.includes(money(csDetailRow.deliveryAmount)), '客户明细出库金额与实际值一致', money(csDetailRow.deliveryAmount))
    assert(firstRow.includes(money(csDetailRow.invoiceAmount)), '客户明细发票金额与实际值一致', money(csDetailRow.invoiceAmount))
    const csDetailRowCount = await page.locator('.el-tab-pane:visible .el-table__body').first().locator('tr').count()
    assert(csDetailRowCount === (csDetail.data?.total || 0), '客户明细一张销售订单一行', `${csDetailRowCount} vs ${csDetail.data?.total}`)
    // 未关联订单的下推单据必须也能看到发票/出库金额
    const csAll = await j(await fetch(API + '/api/report/standard/sale/customer-statement/detail?page=1&size=200', { headers: H }))
    const csUnlinked = (csAll.data?.records || []).find(r => r.billNo === '未关联订单' && Number(r.invoiceAmount || 0) !== 0)
    assert(!!csUnlinked, '客户明细含未关联订单行且带发票金额')
    if (csUnlinked) {
      const body = await page.locator('.el-tab-pane:visible .el-table__body').first().innerText()
      assert(body.includes('未关联订单'), '客户明细页面显示未关联订单行')
      assert(body.includes(money(csUnlinked.invoiceAmount)), '未关联订单行的发票金额可见', money(csUnlinked.invoiceAmount))
    }

    // ==================== 供应商对账单 ====================
    const ss = await j(await fetch(API + '/api/report/standard/purchase/supplier-statement/summary', { headers: H }))
    const ssRow = (ss.data?.records || [])[0] || {}
    const ssDetail = await j(await fetch(API + '/api/report/standard/purchase/supplier-statement/detail?page=1&size=20', { headers: H }))
    const ssDetailRow = (ssDetail.data?.records || [])[0] || {}
    await page.goto(BASE + '/purchase/report/supplier-statement', { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.el-table__body tr', { timeout: 30000 })
    await page.waitForTimeout(1200)
    heads = (await page.locator('thead').allInnerTexts()).join('\n')
    for (const h of ['订单已付', '订单未付', '入库已付', '入库未付', '发票已付', '发票未付', '本期未付', '剩余未付']) {
      assert(heads.includes(h), '供应商汇总含列：' + h)
    }
    firstRow = await page.locator('.el-tab-pane:visible .el-table__body').first().locator('tr').first().innerText()
    assert(firstRow.includes(money(ssRow.purchaseOrderPaidAmount)), '供应商汇总采购订单已付与实际值一致', money(ssRow.purchaseOrderPaidAmount))
    assert(firstRow.includes(money(ssRow.purchaseOrderUnpaidAmount)), '供应商汇总采购订单未付与实际值一致', money(ssRow.purchaseOrderUnpaidAmount))
    assert(firstRow.includes(money(ssRow.currentUnpaid)), '供应商汇总本期未付与实际值一致', money(ssRow.currentUnpaid))
    assert(firstRow.includes(money(ssRow.endBalance)), '供应商汇总剩余未付与实际值一致', money(ssRow.endBalance))

    await page.getByRole('tab', { name: /明细统计/ }).click()
    await page.waitForTimeout(1500)
    heads = (await page.locator('.el-tab-pane:visible thead').allInnerTexts()).join('\n')
    for (const h of ['订单金额', '订单已付', '订单未付', '入库金额', '入库已付', '入库未付', '发票金额', '发票已付', '发票未付', '退货金额']) {
      assert(heads.includes(h), '供应商明细含列：' + h)
    }
    assert(!heads.includes('单据类型'), '供应商明细不再按单据类型分行')
    const ssLastTh = page.locator('.el-tab-pane:visible th').filter({ hasText: '退货金额' }).first()
    const ssLastBox = await ssLastTh.boundingBox()
    assert(!!ssLastBox && ssLastBox.x + ssLastBox.width <= 1440, '供应商明细最后一列首屏可见', ssLastBox ? String(Math.round(ssLastBox.x + ssLastBox.width)) : 'no-box')
    firstRow = await page.locator('.el-tab-pane:visible .el-table__body').first().locator('tr').first().innerText()
    assert(firstRow.includes(money(ssDetailRow.orderAmount)), '供应商明细订单金额与实际值一致', money(ssDetailRow.orderAmount))
    assert(firstRow.includes(money(ssDetailRow.paidAmount)), '供应商明细订单已付与实际值一致', money(ssDetailRow.paidAmount))
    assert(firstRow.includes(money(ssDetailRow.unpaidAmount)), '供应商明细订单未付与实际值一致', money(ssDetailRow.unpaidAmount))
    assert(firstRow.includes(money(ssDetailRow.receiptAmount)), '供应商明细入库金额与实际值一致', money(ssDetailRow.receiptAmount))
    assert(firstRow.includes(money(ssDetailRow.invoiceAmount)), '供应商明细发票金额与实际值一致', money(ssDetailRow.invoiceAmount))
    const ssDetailRowCount = await page.locator('.el-tab-pane:visible .el-table__body').first().locator('tr').count()
    assert(ssDetailRowCount === (ssDetail.data?.total || 0), '供应商明细一张采购订单一行', `${ssDetailRowCount} vs ${ssDetail.data?.total}`)
    const ssAll = await j(await fetch(API + '/api/report/standard/purchase/supplier-statement/detail?page=1&size=200', { headers: H }))
    const ssUnlinked = (ssAll.data?.records || []).find(r => r.billNo === '未关联订单'
      && (Number(r.invoiceAmount || 0) !== 0 || Number(r.receiptAmount || 0) !== 0))
    assert(!!ssUnlinked, '供应商明细含未关联订单行且带发票/入库金额')
    if (ssUnlinked) {
      const body = await page.locator('.el-tab-pane:visible .el-table__body').first().innerText()
      assert(body.includes('未关联订单'), '供应商明细页面显示未关联订单行')
      const amt = Number(ssUnlinked.invoiceAmount || 0) !== 0 ? ssUnlinked.invoiceAmount : ssUnlinked.receiptAmount
      assert(body.includes(money(amt)), '未关联订单行的金额可见', money(amt))
    }
  } catch (e) {
    fail++
    console.log('FAIL 执行异常 | ' + String(e).slice(0, 300))
  } finally {
    await browser.close()
  }
  assert(errs.length === 0, '控制台报错 0', errs.slice(0, 3).join(' | '))
  console.log(`\n结果：PASS ${pass} / FAIL ${fail}`)
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
