// 明细模式切菜单自动刷新 + 不可编辑状态只读 · 点击级回归（2026-11-09）
// 覆盖：
//   ① 销售订单列表在「明细模式」下，切到别的菜单再切回，明细数据会重新取数一次；
//   ② 已生效的销售订单/销售合同，编辑页业务字段全部只读（不可输入）；
//   ③ 已取消的销售合同，编辑页业务字段全部只读（2026-11-09 修复 canEdit 对已取消的判定）。
// 依赖：本地后端 8080 + 前端开发服务 3000 均在运行。
const Path = require('path')
const { execFileSync } = require('child_process')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = 'http://localhost:8080'
const BASE = 'http://localhost:3000'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
const TAG = 'DETAIL-REFRESH-TEST-' + Date.now()
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
const today = new Date().toISOString().slice(0, 10)
let H = {}
const req = async (m, p, b) => j(await fetch(API + p, { method: m, headers: b ? { ...H, 'Content-Type': 'application/json' } : H, body: b ? JSON.stringify(b) : undefined }))
const setCfg = (k, v) => req('PUT', '/api/sys-config/' + encodeURIComponent(k), { configValue: v })

async function createOrder () {
  const r = await req('POST', '/api/sale/orders', {
    orderNo: '', orderDate: today, customerId: 15, currencyId: 1, exchangeRate: 1, discountRate: 0,
    deliveryDate: today, remark: TAG,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建订单失败: ' + JSON.stringify(r).slice(0, 200))
  return r.data
}
async function createContract () {
  const r = await req('POST', '/api/sale/contracts', {
    contractNo: '', contractDate: today, contractType: '销售合同', customerId: 15, currencyId: 1,
    exchangeRate: 1, discountRate: 0, deliveryDate: today, remark: TAG,
    items: [{ materialId: 1683, materialCode: '0001', materialName: '小助手', quantity: 10, unitPrice: 100, taxRate: 13, discountRate: 0 }]
  })
  if (r.code !== 200) throw new Error('创建合同失败: ' + JSON.stringify(r).slice(0, 200))
  return r.data
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

/** 扫描页面上"可见且可输入"的业务控件（排除全局菜单搜索/分页等非单据字段） */
async function scanEditable (page) {
  return await page.evaluate(() => {
    const out = []
    const skip = el => {
      const ph = String(el.getAttribute('placeholder') || '')
      if (ph.includes('搜索菜单')) return true
      if (el.closest('.el-pagination')) return true
      if (el.closest('.header')) return true
      if (el.closest('.tags-view')) return true
      if (el.closest('.ai-assistant')) return true
      return false
    }
    document.querySelectorAll('input, textarea').forEach(el => {
      const st = getComputedStyle(el)
      if (st.display === 'none' || st.visibility === 'hidden' || el.offsetParent === null) return
      const r = el.getBoundingClientRect(); if (!r.width || !r.height) return
      if (el.disabled || el.readOnly) return
      if (skip(el)) return
      out.push({ ph: el.getAttribute('placeholder') || '', cls: String(el.className || '').slice(0, 60) })
    })
    return out
  })
}

;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  H = { Authorization: 'Bearer ' + login.data.token, 'Content-Type': 'application/json' }
  assert(!!login.data?.token, '管理员登录')

  // 关闭自动下推，避免造数时联动生成下游单
  await setCfg('sale.auto.push.contract.to.order', 'false')
  await setCfg('sale.auto.push.order.to.contract', 'false')

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  const errs = []
  const detailReqs = []
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
  page.on('pageerror', e => errs.push('pageerror: ' + String(e).slice(0, 200)))
  page.on('request', r => {
    const u = r.url()
    if (u.includes('/api/report/sale/order-detail') && !u.includes('/columns')) detailReqs.push({ t: Date.now(), u: u.slice(0, 160) })
  })
  await page.addInitScript(t => {
    localStorage.setItem('token', t)
    localStorage.setItem('username', 'admin'); localStorage.setItem('realName', 'admin')
    localStorage.setItem('userId', '1'); localStorage.setItem('tenantId', '1'); localStorage.setItem('tenantName', 'A公司-完整模式')
    localStorage.setItem('roles', JSON.stringify(['ADMIN'])); localStorage.setItem('permissions', JSON.stringify(['*']))
  }, login.data.token)

  // ================= A. 明细模式切菜单 → 切回自动刷新 =================
  await page.goto(BASE + '/sale/orders', { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForTimeout(5000)

  // 防御：若出现开账引导/新手教程遮罩，先关掉
  for (const txt of ['跳过', '关闭', '我知道了', '暂不设置']) {
    const b = page.locator('.el-overlay button:visible', { hasText: txt }).first()
    if (await b.count()) { try { await b.click({ timeout: 1500 }) } catch (e) {} }
  }

  // 打开列设置（表格右上角的圆形设置按钮，无文字）→ 切到明细模式
  const colBtn = page.locator('.table-tools button:visible').first()
  assert(await colBtn.count() > 0, '销售订单列表存在列设置按钮')
  await colBtn.click()
  await page.waitForTimeout(600)
  const detailRadio = page.locator('.cs-viewmode .el-radio-button', { hasText: '明细模式' }).first()
  assert(await detailRadio.count() > 0, '列设置中出现「视图模式」切换')
  await detailRadio.click()
  await page.waitForTimeout(3500)
  const beforeSwitch = detailReqs.length
  assert(beforeSwitch >= 1, '切到明细模式后明细数据已取数', '明细请求数=' + beforeSwitch)
  const closeBtn = page.locator('.cs-close-btn').first()
  if (await closeBtn.count()) await closeBtn.click()
  await page.waitForTimeout(500)

  // 关掉列设置后，确认明细表格真的有行（不是空表）
  const detailRows = await page.locator('.el-table__body tr.el-table__row').count()
  assert(detailRows > 0, '明细模式表格有数据行', '行数=' + detailRows)

  // 点左侧菜单切到「销售合同」
  async function clickMenu (label) {
    const exact = new RegExp('^\\s*' + label + '\\s*$')
    let item = page.locator('.el-menu-item:visible').filter({ hasText: exact }).first()
    if (!(await item.count())) {
      const parent = page.locator('.el-sub-menu__title:visible', { hasText: '销售管理' }).first()
      if (await parent.count()) { await parent.click(); await page.waitForTimeout(600) }
      item = page.locator('.el-menu-item:visible').filter({ hasText: exact }).first()
    }
    if (await item.count()) { await item.click(); return true }
    return false
  }
  assert(await clickMenu('销售合同'), '左侧菜单可点击「销售合同」')
  await page.waitForTimeout(2500)
  const urlAfterLeave = page.url()
  assert(urlAfterLeave.includes('/sale/contracts'), '已切换到销售合同列表', urlAfterLeave)

  const afterLeaveCount = detailReqs.length
  assert(await clickMenu('销售订单'), '左侧菜单可点击切回「销售订单」')
  await page.waitForTimeout(4000)
  const afterBackCount = detailReqs.length
  assert(page.url().includes('/sale/orders'), '已切回销售订单列表', page.url())
  assert(afterBackCount > afterLeaveCount,
    '切回菜单后明细数据自动重新取数', '切走时=' + afterLeaveCount + ' 切回后=' + afterBackCount)

  // ================= B. 已生效销售订单：字段全部只读 =================
  const o = await createOrder()
  await req('POST', '/api/sale/orders/' + o.orderId + '/approve')
  const oa = (await req('GET', '/api/sale/orders/' + o.orderId)).data
  assert(oa?.status === '已生效', '测试订单已生效', oa?.status)
  await page.goto(BASE + '/sale/orders/' + o.orderId, { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForTimeout(6000)
  const oEditable = await scanEditable(page)
  assert(oEditable.length === 0, '已生效销售订单的业务字段全部只读', JSON.stringify(oEditable))

  // ================= C. 已生效销售合同：字段全部只读 =================
  const c = await createContract()
  await req('POST', '/api/sale/contracts/' + c.contractId + '/approve')
  const ca = (await req('GET', '/api/sale/contracts/' + c.contractId)).data
  assert(ca?.status === '已生效', '测试合同已生效', ca?.status)
  await page.goto(BASE + '/sale/contracts/' + c.contractId, { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForTimeout(6000)
  const cEditable = await scanEditable(page)
  assert(cEditable.length === 0, '已生效销售合同的业务字段全部只读', JSON.stringify(cEditable))

  // ================= D. 已取消销售合同：字段全部只读 =================
  const c2 = await createContract()
  await req('POST', '/api/sale/contracts/' + c2.contractId + '/cancel', { reason: 'readonly test' })
  const c2a = (await req('GET', '/api/sale/contracts/' + c2.contractId)).data
  assert(c2a?.status === '已取消', '测试合同已取消', c2a?.status)
  await page.goto(BASE + '/sale/contracts/' + c2.contractId, { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForTimeout(6000)
  const c2Editable = await scanEditable(page)
  assert(c2Editable.length === 0, '已取消销售合同的业务字段全部只读', JSON.stringify(c2Editable))

  console.log('CONSOLE_ERRORS=' + JSON.stringify(errs.slice(0, 8)))
  assert(errs.length === 0, '页面控制台 0 报错')
  await browser.close()

  cleanupBySql()
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); cleanupBySql(); process.exit(2) })
