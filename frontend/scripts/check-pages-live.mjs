import { chromium } from '@playwright/test'
import { waitAppReady, detectBlank, findPlaceholders } from '../tests/smoke/smoke-browser.mjs'
import fs from 'fs'

const BASE = 'http://localhost:3000'
const IGNORE = /favicon|ResizeObserver|Vue Devtools|Extension context invalidated/i

// 路由：router 里所有以 List.vue 结尾的页面
function routes() {
  if (process.env.ROUTES_FILE) {
    return fs.readFileSync(process.env.ROUTES_FILE, 'utf8').split(String.fromCharCode(10)).map((x) => x.trim())
      .filter((x) => x.startsWith('/') && !x.startsWith('/login') && !x.startsWith('/acquisition/login'))
  }
  const t = fs.readFileSync('src/router/index.js', 'utf8')
  const re = /path:\s*'([^']+)'[^\n]*component:\s*\(\)\s*=>\s*import\('([^']+)'\)/g
  const out = []
  let m
  while ((m = re.exec(t))) if (/List\.vue$/.test(m[2])) out.push('/' + m[1].replace(/^\//, ''))
  return [...new Set(out)]
}

/**
 * 页面巡检（真实登录 + 逐页打开，收集控制台报错/业务码/白屏/占位符）
 *
 * 用法（PowerShell）：
 *   $env:E2E_TENANT_CODE='DEFAULT'; $env:ERP_USER='admin'; $env:ERP_PASS='<密码>'
 *   node scripts/check-pages-live.mjs
 * 可选：
 *   ROUTES_FILE  指定路由清单文件（每行一个路径）；不传则巡检 router 里所有 List 页
 *   WAIT_MS      每页等待毫秒（默认 900，重页面可调 3000）
 *   PW_EXE       指定 Chromium 可执行文件；或用 PW_CHANNEL=msedge/chrome
 *   需要本机已安装 Playwright 浏览器（npx playwright install chromium）或指定 PW_EXE
 */
const browser = await chromium.launch({
  headless: true,
  ...(process.env.PW_EXE ? { executablePath: process.env.PW_EXE } : {}),
  ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}),
  args: ['--no-proxy-server']
})
const ctx = await browser.newContext({ baseURL: BASE })
const boot = await ctx.newPage()
await boot.goto('/login', { waitUntil: 'domcontentloaded' })
await boot.waitForTimeout(1000)
await boot.locator('input[placeholder="用户名"]').fill(process.env.ERP_USER || 'admin')
await boot.locator('input[placeholder="密码"]').fill(process.env.ERP_PASS || 'admin123')
// 登录页是原生 form submit（el-button 点击在缩放画布下不稳定），直接触发 requestSubmit
await boot.evaluate(() => document.querySelector('form')?.requestSubmit())
await boot.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 })
if (boot.url().includes('tenant-select')) {
  await boot.waitForSelector('.tenant-item', { timeout: 20000 })
  const want = process.env.E2E_TENANT_CODE || 'DEFAULT'
  const tenants = await boot.evaluate(() => JSON.parse(localStorage.getItem('pendingTenants') || '[]'))
  const idx = tenants.findIndex((t) => String(t.tenantCode) === want)
  if (idx < 0) throw new Error('未找到租户 ' + want + '，可选：' + tenants.map((t) => t.tenantCode).join(','))
  await boot.locator('.tenant-item').nth(idx).click()
  await boot.waitForURL((u) => !u.pathname.includes('tenant-select'), { timeout: 30000 })
}
await waitAppReady(boot)
await boot.close()

const list = routes()
console.log('ROUTES=' + list.length)
const bad = []
for (const p of list) {
  const page = await ctx.newPage()
  const errs = []
  page.on('console', (m) => { if (m.type() === 'error' && !IGNORE.test(m.text())) errs.push('console:' + m.text().slice(0, 300)) })
  page.on('pageerror', (e) => { const t = String(e.message || e); if (!IGNORE.test(t)) errs.push('pageerror:' + t.slice(0, 300)) })
  page.on('response', async (r) => {
    if (IGNORE.test(r.url())) return
    if (r.status() >= 400) { errs.push('http:' + r.status() + ' ' + r.url().slice(0, 200)); return }
    // 后端业务错误是 HTTP 200 + code!=200：只看 HTTP 状态会漏掉
    if (r.status() === 200 && r.url().includes('/api/')) {
      const ct = r.headers()['content-type'] || ''
      if (ct.includes('json')) {
        try {
          const j = await r.json()
          if (j && typeof j.code === 'number' && j.code !== 200) errs.push('api-code:' + j.code + ' ' + r.url().slice(0, 200) + ' ' + String(j.message || '').slice(0, 80))
        } catch (_) { /* 非 JSON 或流式响应 */ }
      }
    }
  })
  const entry = { path: p, ok: true, reasons: [] }
  try {
    await page.goto(BASE + p, { waitUntil: 'domcontentloaded', timeout: 20000 })
    await page.waitForTimeout(Number(process.env.WAIT_MS || 900))
    const blank = await detectBlank(page)
    if (blank.blank) entry.reasons.push('白屏:' + blank.reason)
    const ph = await findPlaceholders(page)
    if (ph.length) entry.reasons.push('占位符:' + ph.join('/'))
    entry.reasons.push(...errs.slice(0, 4))
    if (entry.reasons.length) entry.ok = false
  } catch (e) {
    entry.ok = false
    entry.reasons.push('EXC:' + String(e.message || e).split('\n')[0].slice(0, 160))
  }
  await page.close()
  if (entry.ok) process.stdout.write('.')
  else { bad.push(entry); console.log('X ' + p + ' :: ' + entry.reasons.join(' || ').slice(0, 500)) }
}
console.log('')
console.log('BAD=' + bad.length + '/' + list.length)
await browser.close()
