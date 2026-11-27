// 设置中心「自动下推设置」· 点击级回归（2026-11-08）
// 断言：页面渲染两个开关与文案；开→保存→刷新仍开；关→保存→刷新仍关；
//       两个开关同一保存按钮、值落库到 sys_config。
// 运行：node deploy/local-run/verify-sale-auto-push-ui.cjs [API_BASE] [UI_BASE]
const Path = require('path')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = process.argv[2] || 'http://localhost:8080'
const BASE = process.argv[3] || 'http://localhost:3000'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
const CFG_C2O = 'sale.auto.push.contract.to.order'
const CFG_O2C = 'sale.auto.push.order.to.contract'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }

const switchChecked = async (rowLocator) =>
  rowLocator.locator('.el-switch').evaluate(el => el.classList.contains('is-checked'))

;(async () => {
  const login = await (await fetch(API + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PWD })
  })).json()
  const TOKEN = login?.data?.token
  assert(login?.code === 200 && !!TOKEN, '管理员登录')
  const H = { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }
  const setCfg = (key, value) => fetch(API + '/api/sys-config/' + encodeURIComponent(key), {
    method: 'PUT', headers: H, body: JSON.stringify({ configValue: value })
  })
  // 初始置为关闭，保证断言起点确定
  await setCfg(CFG_C2O, 'false')
  await setCfg(CFG_O2C, 'false')

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
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
    u.protocol = api.protocol
    u.hostname = api.hostname
    u.port = api.port || (api.protocol === 'https:' ? '443' : '80')
    return route.continue({ url: u.toString() })
  })

  // 1) 从设置中心真实入口进入：设置中心 → 业务 → 自动下推设置
  await page.goto(BASE + '/system/settings', { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForSelector('.kgw-settings', { timeout: 90000 })
  await page.waitForTimeout(1500)
  await page.locator('.col1-item', { hasText: '业务' }).first().click()
  await page.waitForTimeout(600)
  const entry = page.locator('.col2-item', { hasText: '自动下推设置' })
  assert(await entry.count() > 0, '设置中心业务分组下能看到自动下推设置入口')
  await entry.first().click()
  await page.waitForSelector('.kgw-auto-push', { timeout: 60000 })
  assert(true, '点击入口能打开自动下推设置页')

  // 2) 带 query 重新进入，后续刷新保持在同一页
  await page.goto(BASE + '/system/settings?group=business&item=auto-push', { waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForSelector('.kgw-auto-push', { timeout: 90000 })
  await page.waitForTimeout(2500)

  const rows = page.locator('.switch-row')
  assert(await rows.count() === 2, '自动下推设置页渲染两个开关', '实际=' + await rows.count())

  const row1 = rows.nth(0)
  const row2 = rows.nth(1)
  const text1 = (await row1.innerText()).replace(/\s+/g, ' ')
  const text2 = (await row2.innerText()).replace(/\s+/g, ' ')
  assert(text1.includes('销售合同生效后自动生成销售订单'), '开关一文案正确', text1)
  assert(text2.includes('销售订单生效后自动生成销售合同'), '开关二文案正确', text2)
  assert(!/（|）|\(|\)/.test(text1 + text2), '界面文案没有自行追加括号备注')

  assert(!(await switchChecked(row1)) && !(await switchChecked(row2)), '初始两个开关均为关闭')

  // 开开关一 → 保存
  await row1.locator('.el-switch').click()
  await page.waitForTimeout(300)
  assert(await switchChecked(row1), '点击后开关一立即变为开')
  await page.locator('.kgw-toolbar .el-button', { hasText: '保存' }).click()
  await page.waitForTimeout(1500)
  let cfg = await (await fetch(API + '/api/sys-config/' + encodeURIComponent(CFG_C2O), { headers: H })).json()
  assert(cfg?.data?.configValue === 'true', '开关一保存后落库为 true', JSON.stringify(cfg?.data))

  // 刷新后仍在
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForSelector('.kgw-auto-push', { timeout: 90000 })
  await page.waitForTimeout(2500)
  assert(await switchChecked(page.locator('.switch-row').nth(0)), '刷新后开关一仍为开')
  assert(!(await switchChecked(page.locator('.switch-row').nth(1))), '刷新后开关二仍为关')

  // 关开关一 → 开开关二 → 保存
  await page.locator('.switch-row').nth(0).locator('.el-switch').click()
  await page.locator('.switch-row').nth(1).locator('.el-switch').click()
  await page.waitForTimeout(300)
  await page.locator('.kgw-toolbar .el-button', { hasText: '保存' }).click()
  await page.waitForTimeout(1500)
  const c1 = await (await fetch(API + '/api/sys-config/' + encodeURIComponent(CFG_C2O), { headers: H })).json()
  const c2 = await (await fetch(API + '/api/sys-config/' + encodeURIComponent(CFG_O2C), { headers: H })).json()
  assert(c1?.data?.configValue === 'false' && c2?.data?.configValue === 'true', '两个开关各自保存值与界面一致',
    `c2o=${c1?.data?.configValue}, o2c=${c2?.data?.configValue}`)

  // 刷新后仍在
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 120000 })
  await page.waitForSelector('.kgw-auto-push', { timeout: 90000 })
  await page.waitForTimeout(2500)
  assert(!(await switchChecked(page.locator('.switch-row').nth(0))), '刷新后开关一仍为关')
  assert(await switchChecked(page.locator('.switch-row').nth(1)), '刷新后开关二仍为开')

  console.log('CONSOLE_ERRORS=' + JSON.stringify(errs.slice(0, 8)))
  assert(errs.length === 0, '页面控制台 0 报错')

  // 还原为默认关闭
  await setCfg(CFG_C2O, 'false')
  await setCfg(CFG_O2C, 'false')
  await browser.close()
  console.log('RESULT ' + pass + ' PASS / ' + fail + ' FAIL')
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
