// 收款单 v3.1 · 编辑页 + 选单弹窗点击级验证（真实后端 / 前端）
// 断言：一个核销明细列表；弹窗四页签（全部/销售订单/销售出库单/销售发票）；弹窗只勾选不填金额；
//       勾选确认后核销明细落行金额 0.00；收款明细含预收单号列 + 预收冲抵按钮；无③独立面板；控制台 0 报错。
const Path = require('path')
const { chromium } = require(Path.resolve(__dirname, '../../frontend/node_modules/playwright'))
const API = process.argv[2] || 'http://localhost:8082'
const BASE = process.argv[3] || 'http://localhost:3001'
const USER = process.env.ERP_USER || 'admin'
const PWD = process.env.ERP_PWD || 'admin123'
let pass = 0, fail = 0
const assert = (c, n, extra) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (extra ? ' | ' + extra : '')) } }
const j = async r => { const t = await r.text(); try { return JSON.parse(t) } catch (e) { return { raw: t } } }
;(async () => {
  const login = await j(await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PWD }) }))
  const TOKEN = login?.data?.token
  assert(!!TOKEN, '登录')
  const H = { Authorization: 'Bearer ' + TOKEN, 'Content-Type': 'application/json' }
  const all = await j(await fetch(API + '/api/sys-config', { headers: H }))
  const recvCfg = (all.data || []).find(x => x.configKey === 'sale.receipt.receive.mode')
  const origMode = recvCfg?.configValue || 'summary'
  // 设计图 v3：收款方式默认「明细收款」，弹窗才是明细行列表；测试时确保为该值，结束后还原
  await fetch(API + '/api/sys-config/batch', { method: 'PUT', headers: H, body: JSON.stringify([{ configKey: 'sale.receipt.receive.mode', configValue: 'detail' }]) })
  await fetch(API + '/api/sys-config/refresh', { method: 'POST', headers: H })

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
    u.protocol = api.protocol; u.hostname = api.hostname; u.port = api.port
    return route.continue({ url: u.toString() })
  })
  try {
    await page.goto(BASE + '/finance/receipt/new', { waitUntil: 'domcontentloaded' })
    await page.locator('.collapsible-card').filter({ hasText: '核销明细' }).first().waitFor({ timeout: 30000 }).catch(() => {})
    await page.waitForTimeout(1500)
    const bodyText = await page.locator('body').innerText()
    assert(!bodyText.includes('应收单'), '编辑页无「应收单」字样')
    assert(!bodyText.includes('应收核销'), '编辑页无「应收核销」页签/入口')
    assert(bodyText.includes('核销明细'), '存在核销明细区')
    assert(!bodyText.includes('这次收到多少钱'), '① 收款明细标题无「（这次收到多少钱）」')
    assert(!bodyText.includes('按明细行收款'), '② 核销明细标题无「（按明细行收款）」')
    assert(!bodyText.includes('请先选择客户'), '无「请先选择客户」提示')
    assert(!bodyText.includes('③ 预收冲抵'), '③ 预收冲抵独立面板已取消')
    assert(!bodyText.includes('订单直收'), '无「订单直收」旧入口')
    const allHeads = await page.locator('thead').allInnerTexts()
    assert(allHeads.some(h => h.includes('预收单号') && h.includes('收款账户') && h.includes('银行账号') && h.includes('收款金额')),
      '① 收款明细含「预收单号」列（第一列）+ 收款账户/银行账号/收款金额')
    const itemsToolbar = page.locator('.collapsible-card, .bill-items-table').filter({ hasText: '收款明细' }).first()
    assert(await page.getByRole('button', { name: /预收冲抵/ }).count() >= 1, '① 收款明细工具栏有「预收冲抵」按钮')
    const pickBtnInHeader = page.locator('.card-header').filter({ hasText: '② 核销明细' }).getByRole('button', { name: /选择来源单/ })
    assert(await pickBtnInHeader.count() === 1, '选单按钮在核销明细标题栏右侧')

    await pickBtnInHeader.click()
    await page.waitForTimeout(2500)
    const dlg = page.locator('.el-dialog').filter({ hasText: '选择来源单' }).first()
    assert(await dlg.count() > 0, '选单弹窗打开')
    assert(await dlg.locator('.el-dialog__footer').count() === 0, '弹窗底部无按钮（按钮全在标题栏右侧）')
    // 四个页签独立一行（el-tabs），不与搜索框同行
    const tabCount = await dlg.locator('.el-tabs__item').count()
    assert(tabCount === 4, '类型页签共 4 个（全部/销售订单/销售出库单/销售发票）', '实际 ' + tabCount)
    const dlgText = await dlg.innerText()
    assert(!dlgText.includes('仅显示未收完'), '弹窗无「仅显示未收完…」说明')
    const heads = await dlg.locator('thead').innerText()
    assert(heads.includes('类型'), '明细行有「类型」列')
    assert(heads.includes('物料编码') && heads.includes('物料名称') && heads.includes('规格型号'), '明细行带物料全信息')
    assert(!heads.includes('本次收款'), '弹窗无「本次收款」输入列（金额回写后到核销明细再填）')
    assert(!dlgText.includes('应收单'), '选单弹窗无「应收单」')
    // 勾选第一行 → 确认
    const rowTexts = await dlg.locator('.el-table__row').allInnerTexts()
    const typeSet = new Set()
    rowTexts.forEach(t => {
      ['销售订单', '销售出库单', '销售发票'].forEach(x => { if (t.includes(x)) typeSet.add(x) })
    })
    console.log('弹窗内来源类型：', Array.from(typeSet).join(' / '), '｜行数：', rowTexts.length)
    assert(typeSet.size >= 1, '弹窗出现业务来源明细行')
    const firstCheckbox = dlg.locator('.el-table__row').first().locator('.el-checkbox').first()
    await firstCheckbox.click()
    await page.waitForTimeout(400)
    const confirmBtn = dlg.getByRole('button', { name: /确认明细收款（1）/ }).first()
    assert(await confirmBtn.count() > 0, '确认按钮显示勾选行数（1）')
    await confirmBtn.click()
    await page.waitForTimeout(1200)
    const after = await page.locator('body').innerText()
    assert(!after.includes('请选择来源单以添加核销明细'), '核销明细已落入列表')
    const woCard = page.locator('.collapsible-card').filter({ hasText: '核销明细' }).first()
    const woText = await woCard.innerText()
    assert(/销售订单|销售出库单|销售发票/.test(woText), '核销明细行显示来源类型', woText.slice(0, 120))
    assert(woText.includes('¥0.00'), '回写后核销金额默认 0.00（待在核销明细里填）', woText.slice(0, 200))
    assert(errs.length === 0, '控制台 0 报错', errs.join(' || '))
  } finally {
    await browser.close().catch(() => {})
    await fetch(API + '/api/sys-config/batch', { method: 'PUT', headers: H, body: JSON.stringify([{ configKey: 'sale.receipt.receive.mode', configValue: origMode }]) })
    await fetch(API + '/api/sys-config/refresh', { method: 'POST', headers: H })
    console.log('（收款单收款方式已还原为 ' + origMode + '）')
  }
  console.log(`\n结果：PASS ${pass} / FAIL ${fail}`)
  process.exit(fail ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
