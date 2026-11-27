// 列表页「汇总/明细」刷新保持 · 点击级回归（2026-10-18）
// 覆盖用户实报：设置中心把「列表默认视图」设为明细后，刷新列表页又回到汇总。
// 断言：
//   1) 全局默认=明细 且个人从未切换 → 列表页打开即明细；刷新后仍明细（首屏不再先汇总）
//   2) 个人在该页手动切回汇总 → 刷新后仍汇总（个人优先）
//   3) 个人手动切到明细 → 刷新后仍明细
// 运行：node deploy/local-run/verify-list-view-refresh.cjs [API_BASE] [UI_BASE]
//   默认 http://localhost:8080  http://localhost:3000
// ⚠ 会临时修改测试环境的「列表默认视图」，结束后恢复原值；仅限本地/测试环境执行。
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
  const orig = (all.data || []).find(x => x.configKey === 'sys.list.default.view')?.configValue || '汇总'
  const setGlobal = (v) => fetch(API + '/api/sys-config/batch', {
    method: 'PUT', headers: H,
    body: JSON.stringify([{ configKey: 'sys.list.default.view', configValue: v }])
  })

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  const errs = []
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
    const api = new URL(API)
    u.protocol = api.protocol; u.hostname = api.hostname; u.port = api.port || (api.protocol === 'https:' ? '443' : '80')
    return route.continue({ url: u.toString() })
  })
  const open = async () => {
    await page.goto(BASE + '/sale/orders', { waitUntil: 'domcontentloaded', timeout: 120000 })
    await page.waitForSelector('.bill-list-page', { timeout: 60000 })
    await page.waitForTimeout(2500)
  }
  const reload = async () => {
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 120000 })
    await page.waitForSelector('.bill-list-page', { timeout: 60000 })
    await page.waitForTimeout(1500)
  }
  const openDrawer = async () => {
    await page.locator('.table-tools button.is-circle, .search-actions button.is-circle').first().click()
    await page.waitForSelector('.cs-panel', { timeout: 10000 })
  }
  const isDetail = async () => (await page.locator('.detail-select-bar').count()) > 0

  try {
    await setGlobal('明细')
    await open()
    await page.evaluate(() => {
      for (const k of Object.keys(localStorage)) {
        if (k.includes('columnConfig') || k.includes('listDefaultView')) localStorage.removeItem(k)
      }
    })
    await open()
    assert(await isDetail(), 'global detail applies on open')
    await reload()
    assert(await isDetail(), 'after refresh still detail (no summary flash)')

    await openDrawer()
    await page.locator('.cs-viewmode .el-radio-button', { hasText: '汇总模式' }).click()
    await page.waitForTimeout(800)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
    assert(!(await isDetail()), 'switch to summary')
    await reload()
    assert(!(await isDetail()), 'personal summary survives refresh')

    await openDrawer()
    await page.locator('.cs-viewmode .el-radio-button', { hasText: '明细模式' }).click()
    await page.waitForTimeout(1500)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
    assert(await isDetail(), 'switch to detail')
    await reload()
    assert(await isDetail(), 'personal detail survives refresh')
  } finally {
    await setGlobal(orig)
    await browser.close()
  }
  console.log('CONSOLE_ERRORS=' + JSON.stringify(errs.slice(0, 6)))
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})()
