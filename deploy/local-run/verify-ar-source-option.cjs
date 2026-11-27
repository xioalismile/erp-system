// 设置中心「收款单生成方式」下拉选项 · 点击级回归（2026-10-18，设计图 v3）
// 断言：设置中心 → 系统参数设置 → 财务参数 → 「收款单生成方式」下拉包含
//       不生成 / 销售订单审核 / 销售出库单审核 / 销售发票审核 四项；
//       选择「不生成」→ 保存 → 提交值为 none。
// 注意：本项控制的是"业务单据审核后是否自动生成收款单草稿"，不是应收单。
// 运行：node deploy/local-run/verify-ar-source-option.cjs [API_BASE] [UI_BASE]
//   默认 http://localhost:8080  http://localhost:3000
const Path = require('path')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = process.argv[2] || 'http://localhost:8080'
const BASE = process.argv[3] || 'http://localhost:3000'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }

;(async () => {
  const login = await (await fetch(API + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD })
  })).json()
  const TOKEN = login?.data?.token
  assert(login?.code === 200 && !!TOKEN, 'login')
  const H = { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }
  const all = await (await fetch(API + '/api/sys-config', { headers: H })).json()
  const origValue = (all.data || []).find(x => x.configKey === 'finance.ar.source_bill_type')?.configValue || 'none'
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  const errs = []
  let savedPayload = null
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)) })
  page.on('pageerror', e => errs.push('pageerror: ' + String(e).slice(0, 160)))
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
    if (u.pathname === '/api/sys-config/batch' && route.request().method() === 'PUT') {
      savedPayload = JSON.parse(route.request().postData() || '[]')
    }
    const api = new URL(API)
    u.protocol = api.protocol; u.hostname = api.hostname; u.port = api.port || (api.protocol === 'https:' ? '443' : '80')
    return route.continue({ url: u.toString() })
  })
  await page.goto(BASE + '/system/settings?group=system&item=sys-param', { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForSelector('.kgw-sys-param', { timeout: 60000 })
  await page.waitForTimeout(2000)
  await page.locator('.param-tabs .el-tabs__item:visible', { hasText: '财务参数' }).click()
  await page.waitForTimeout(2000)
  const label = page.locator('.param-item:visible', { hasText: '收款单生成方式' }).first()
  await label.waitFor({ state: 'visible', timeout: 15000 })
  assert(await label.count() > 0, 'finance param item rendered')
  await label.locator('.el-select').click()
  await page.waitForTimeout(600)
  const opts = await page.locator('.el-select-dropdown__item:visible').allInnerTexts()
  console.log('OPTIONS=' + JSON.stringify(opts))
  assert(opts.includes('不生成'), 'dropdown contains 不生成')
  assert(opts.includes('销售订单审核') && opts.includes('销售出库单审核') && opts.includes('销售发票审核'), 'other three options kept')
  await page.locator('.el-select-dropdown__item:visible', { hasText: '不生成' }).first().click()
  await page.waitForTimeout(400)
  await page.locator('.param-toolbar .el-button', { hasText: '保存' }).click()
  await page.waitForTimeout(1000)
  const item = (savedPayload || []).find(x => x.configKey === 'finance.ar.source_bill_type')
  assert(item && item.configValue === 'none', 'saved value = none', JSON.stringify(item))
  // 恢复原值（本脚本只验证下拉与保存链路，不改变测试环境配置）
  await fetch(API + '/api/sys-config/batch', {
    method: 'PUT', headers: H,
    body: JSON.stringify([{ configKey: 'finance.ar.source_bill_type', configValue: origValue }])
  })
  console.log('CONSOLE_ERRORS=' + JSON.stringify(errs.slice(0, 6)))
  await browser.close()
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})()
