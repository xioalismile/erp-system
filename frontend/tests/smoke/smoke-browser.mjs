/**
 * 冒烟测试浏览器公共库
 *
 * 提供：登录、页面就绪等待、三层监控（console.error / pageerror / HTTP 5xx）、
 *       侧边栏真实渲染读取、store 权限读取、白屏检测、按钮点击可观测反馈判定。
 *
 * 设计要点（都来自项目真实踩坑）：
 *   - 8cloud 根路径 / 是官网宣传页 → 一律走 cfg.entryPath（默认 /index.html）
 *   - 权限/菜单读 localStorage.menuRoutePaths（与路由守卫同源，最精确）
 *   - 白屏判定不能只看"编译通过"，要看 DOM 是否真的有内容
 *   - 点击按钮后必须"有可观测反馈"，否则视为 FAIL（抓"点了没反应"类缺陷）
 */
import { cfg } from './smoke-env.mjs'

/** 项目既有 e2e-utils.js 的忽略清单，去掉过宽的部分，保留"确实不是前端缺陷"的噪声 */
const IGNORED_ERROR_PATTERNS = [
  /favicon/i,
  /Failed to load resource/i,
  /net::ERR_/i,
  /ResizeObserver loop/i,
  /Download the Vue Devtools/i,
  /\[Element Plus/i,
  /deprecated/i,
  /ECharts|echarts/i,
  /401|Unauthorized|登录已过期|未登录/i,
  /status of 404/i
]

const shouldIgnore = (msg) => IGNORED_ERROR_PATTERNS.some(re => re.test(msg))

/**
 * 启动三层监控。返回收集器，用例结束调用 assertClean()。
 * 注意：不是"启动即断言"，而是持续收集，最后统一比对 —— 避免中途一个噪声炸掉整条用例。
 */
export function startMonitors(page) {
  const consoleErrors = []
  const pageErrors = []
  const http5xx = []
  const failedApi = []

  const onConsole = (msg) => {
    if (msg.type() !== 'error') return
    const t = msg.text()
    if (shouldIgnore(t)) return
    consoleErrors.push(t)
  }
  const onPageError = (err) => {
    const t = err && err.message ? err.message : String(err)
    if (shouldIgnore(t)) return
    pageErrors.push(t)
  }
  const onResponse = (resp) => {
    const st = resp.status()
    if (st >= 500) http5xx.push(`HTTP ${st} ${resp.url()}`)
    // 后端业务错误一律 HTTP 200 + code!=200；这里只记"接口层异常"
    if (st === 401 || st === 403) failedApi.push(`HTTP ${st} ${resp.url()}`)
  }

  page.on('console', onConsole)
  page.on('pageerror', onPageError)
  page.on('response', onResponse)

  return {
    consoleErrors,
    pageErrors,
    http5xx,
    failedApi,
    stop() {
      page.off('console', onConsole)
      page.off('pageerror', onPageError)
      page.off('response', onResponse)
    },
    /** 汇总为失败原因数组（空数组 = 干净） */
    problems(ctx = '') {
      const out = []
      if (consoleErrors.length) out.push(`${ctx} console.error ${consoleErrors.length} 条: ${consoleErrors.slice(0, 3).join(' | ')}`)
      if (pageErrors.length) out.push(`${ctx} pageerror ${pageErrors.length} 条: ${pageErrors.slice(0, 3).join(' | ')}`)
      if (http5xx.length) out.push(`${ctx} HTTP 5xx ${http5xx.length} 条: ${http5xx.slice(0, 3).join(' | ')}`)
      return out
    },
    reset() {
      consoleErrors.length = 0
      pageErrors.length = 0
      http5xx.length = 0
      failedApi.length = 0
    }
  }
}

/** 打开系统入口页（不能用 '/'：8cloud 根路径是官网宣传页） */
export async function gotoApp(page, pathSuffix = '') {
  const url = pathSuffix ? pathSuffix : cfg.entryPath
  await page.goto(url, { waitUntil: 'domcontentloaded' })
}

/**
 * UI 登录（真实走登录页，不用注入 token —— 要验证的正是"用户真实登录路径"）
 *
 * 多租户关键点（必须处理，否则是"假成功"）：
 *   登录后若该账号挂了多个租户，路由守卫会把人停在 /tenant-select。
 *   而原先的等待条件只是"离开 /login"，此时 URL 已是 /tenant-select —— 会被误判成登录成功，
 *   随后读到的是**未带 tenantId 的旧 token**，业务请求会落到主库 erp_db。
 *   故：必须显式等待并完成选租户，且只能选中 E2E_TENANT_CODE 指定的那一个（不允许"点第一个"）。
 *
 * @returns {Promise<string>} token（已带 tenantId）
 */
export async function loginByUi(page, username, password) {
  // 先清残留登录态，保证是干净登录
  await page.goto(cfg.entryPath, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => {
    try {
      localStorage.removeItem('token')
      localStorage.removeItem('menuRoutePaths')
      localStorage.removeItem('roles')
      localStorage.removeItem('permissions')
      localStorage.removeItem('menuIds')
      // 租户三项必须一起清：Login.vue 选中租户后会写入，残留会导致跨租户串库
      localStorage.removeItem('tenantId')
      localStorage.removeItem('tenantName')
      localStorage.removeItem('tenantCode')
      localStorage.removeItem('pendingTenants')
    } catch (_) { /* 忽略隐私模式等异常 */ }
  })
  await page.goto(cfg.entryPath, { waitUntil: 'domcontentloaded' })

  // 未登录会被路由守卫送到 /login
  await page.waitForURL(u => u.pathname.includes('/login'), { timeout: 30000 }).catch(() => {})

  const userInput = page.locator('input[placeholder="用户名"], input[placeholder*="用户名"], input[placeholder*="账号"]').first()
  const pwdInput = page.locator('input[placeholder="密码"], input[placeholder*="密码"]').first()
  await userInput.waitFor({ state: 'visible', timeout: 30000 })
  await userInput.fill('')
  await userInput.fill(username)
  await pwdInput.fill('')
  await pwdInput.fill(password)

  const loginBtn = page.locator('button:has-text("登")').first()
  await loginBtn.click()

  // 等待离开登录页（失败会停在 /login 并弹错误提示）
  try {
    await page.waitForURL(u => !u.pathname.includes('/login'), { timeout: 30000 })
  } catch (_) {
    const errText = await page.locator('.el-message, .el-message-box, .login-error').allInnerTexts().catch(() => [])
    throw new Error(`登录后未离开登录页（账号=${username}）。页面提示: ${errText.join(' | ') || '(无提示)'}`)
  }

  // ===== 选公司页：必须选中测试租户，禁止"点第一个" =====
  if (page.url().includes('tenant-select')) {
    await selectTenantInUi(page, username)
  }

  await page.waitForLoadState('domcontentloaded')
  const token = await page.evaluate(() => localStorage.getItem('token'))
  if (!token) throw new Error(`登录成功但 localStorage 无 token（账号=${username}）`)
  return token
}

/**
 * 在选公司页选中 E2E_TENANT_CODE 指定的租户。
 * 匹配依据：TenantSelect.vue 从 localStorage.pendingTenants 读列表（L81-86），
 * 点击某一项后调 /api/tenant/select 并把 token/tenantId/tenantCode 写回 LS（L116-120）。
 */
async function selectTenantInUi(page, username) {
  const want = cfg.expectTenantCode
  await page.waitForSelector('.tenant-item', { timeout: 20000 }).catch(() => {})
  const tenants = await page.evaluate(() => {
    try { return JSON.parse(localStorage.getItem('pendingTenants') || '[]') } catch (_) { return [] }
  })
  const idx = tenants.findIndex(t => String(t.tenantCode || '') === want)
  if (idx < 0) {
    throw new Error(
      `选公司页里找不到期望租户 "${want}"（账号=${username}）。` +
      `可选：${tenants.map(t => t.tenantCode || t.tenantName).join(', ') || '(列表为空)'}。` +
      `请核对 E2E_TENANT_CODE 与建租户编码是否一致。`
    )
  }
  await page.locator('.tenant-item').nth(idx).click()
  try {
    await page.waitForURL(u => !u.pathname.includes('tenant-select'), { timeout: 30000 })
  } catch (_) {
    throw new Error(`点击租户 "${want}" 后未离开选公司页，可能选择失败（账号=${username}）`)
  }
  // 隔离断言：写回的 tenantCode 必须与期望一致
  const actual = await page.evaluate(() => localStorage.getItem('tenantCode'))
  if (actual && String(actual) !== want) {
    throw new Error(`★隔离断言失败：选中的租户是 "${actual}"，期望 "${want}"`)
  }
  console.log(`[租户闸门-UI] 已选定测试租户：tenantCode=${actual || want}`)
}

/**
 * 等应用就绪：侧边栏渲染完成或出现"无菜单权限"提示。
 * 不用 networkidle —— 本应用有 AI 助手轮询等持续网络活动，networkidle 可能永不到达。
 */
export async function waitAppReady(page) {
  await page.waitForLoadState('domcontentloaded')
  await page.waitForFunction(() => {
    const menu = document.querySelector('.sidebar .el-menu')
    const empty = document.querySelector('.menu-empty-tip')
    return !!menu || !!empty
  }, { timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(600)
}

/** 读取前端拿到的权限（与路由守卫同源，精确） */
export async function readStoreAuth(page) {
  return await page.evaluate(() => {
    const j = (k) => { try { return JSON.parse(localStorage.getItem(k) || 'null') } catch (_) { return null } }
    return {
      token: localStorage.getItem('token'),
      menuRoutePaths: j('menuRoutePaths') || [],
      roles: j('roles') || [],
      permissions: j('permissions') || [],
      menuIds: j('menuIds') || []
    }
  })
}

/**
 * 读取侧边栏"真实渲染"的菜单（用户肉眼所见），用于隔离断言第二层。
 *
 * DOM 依据 MainLayout.vue L10-L47：
 *   .sidebar > .el-menu.menu
 *     单项: > .el-menu-item
 *     分组: > .el-sub-menu > .el-sub-menu__title (组名) + el-menu-item / 嵌套 el-sub-menu (三级)
 *
 * 必须"逐个展开子菜单再读"：
 *   Element Plus 的 el-sub-menu 折叠时内层元素不可见，innerText 取不到子项，
 *   而 el-menu 又开了 unique-opened（展开一个会收起另一个），
 *   所以做法是：逐个点开顶层分组 → 点开其中的三级子菜单 → 收集文案 → 再处理下一个。
 * 这样断言口径是"菜单里存在的项 == 已授权项"，不依赖折叠这个纯 UI 状态。
 */
export async function readSidebar(page) {
  const root = page.locator('.sidebar .el-menu').first()
  if ((await root.count()) === 0) {
    return { present: false, groups: [], singleItems: [], allLabels: [] }
  }
  const clean = (s) => String(s || '').trim().replace(/\s+/g, ' ')
  const singleItems = []
  const groups = []

  const topCount = await root.locator('> *').count()
  for (let i = 0; i < topCount; i++) {
    const el = root.locator('> *').nth(i)
    const cls = (await el.getAttribute('class')) || ''
    if (cls.includes('el-menu-item')) {
      singleItems.push(clean(await el.innerText().catch(() => '')))
      continue
    }
    if (!cls.includes('el-sub-menu')) continue

    const titleEl = el.locator('> .el-sub-menu__title').first()
    const groupLabel = clean(await titleEl.innerText().catch(() => ''))
    // 展开该顶层分组
    await titleEl.click({ timeout: 5000 }).catch(() => {})
    await page.waitForTimeout(280)

    const items = []
    const body = el.locator('> .el-menu').first()
    if (await body.count()) {
      const n = await body.locator('> *').count()
      for (let j = 0; j < n; j++) {
        const it = body.locator('> *').nth(j)
        const c2 = (await it.getAttribute('class')) || ''
        if (c2.includes('el-menu-item')) {
          items.push(clean(await it.innerText().catch(() => '')))
        } else if (c2.includes('el-sub-menu')) {
          // 三级子菜单：先收标题，再展开收子项
          const st = it.locator('> .el-sub-menu__title').first()
          items.push(clean(await st.innerText().catch(() => '')))
          await st.click({ timeout: 5000 }).catch(() => {})
          await page.waitForTimeout(250)
          const inner = it.locator('.el-menu-item')
          const m = await inner.count()
          for (let k = 0; k < m; k++) {
            items.push(clean(await inner.nth(k).innerText().catch(() => '')))
          }
        }
      }
    }
    groups.push({ label: groupLabel, items: items.filter(Boolean) })
  }

  const allLabels = [...singleItems, ...groups.map(g => g.label), ...groups.flatMap(g => g.items)]
    .filter(Boolean)
  return { present: true, groups, singleItems, allLabels }
}


/** 未授权页面直访应被踢到 /403 */
export async function expectForbiddenOrDashboard(page, path) {
  await page.goto(path, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const url = new URL(page.url())
  return { pathname: url.pathname, kickedTo403: url.pathname.startsWith('/403') }
}

/** 白屏检测：DOM 是否真的有内容（抓"整页空白"） */
export async function detectBlank(page) {
  return await page.evaluate(() => {
    const app = document.querySelector('#app')
    if (!app) return { blank: true, reason: '找不到 #app 根节点', textLen: 0, childCount: 0 }
    const text = (app.innerText || '').trim()
    const childCount = app.querySelectorAll('*').length
    const blank = text.length < 8 && childCount < 20
    return { blank, reason: blank ? `文本 ${text.length} 字 / 元素 ${childCount} 个` : '', textLen: text.length, childCount }
  })
}

/** 页面正文是否出现不该出现的占位符（用户偏好：空数据应留白，不能显示 [object Object]） */
export async function findPlaceholders(page) {
  return await page.evaluate(() => {
    const app = document.querySelector('#app')
    if (!app) return []
    const t = app.innerText || ''
    const hits = []
    if (t.includes('[object Object]')) hits.push('[object Object]')
    if (t.includes('undefined')) hits.push('undefined')
    if (t.includes('NaN')) hits.push('NaN')
    return hits
  })
}

/** 读取 Element Plus 消息提示（成功/错误 toast） */
export async function readToasts(page) {
  return await page.evaluate(() => {
    const out = []
    document.querySelectorAll('.el-message, .el-message-box, .el-notification').forEach(el => {
      const txt = (el.innerText || '').trim()
      if (!txt) return
      const cls = el.className || ''
      let kind = 'info'
      if (cls.includes('--success')) kind = 'success'
      else if (cls.includes('--error')) kind = 'error'
      else if (cls.includes('--warning')) kind = 'warning'
      out.push({ kind, text: txt.replace(/\s+/g, ' ').slice(0, 200) })
    })
    return out
  })
}

/**
 * 等待"点击后有可观测反馈"：成功 toast / 错误 toast / 弹窗 / 路由变化，任一即可。
 * 用于抓 D7/D8（点了没反应 = 静默失败）。
 */
export async function waitForObservableFeedback(page, { fromUrl, timeout = 12000 } = {}) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const toasts = await readToasts(page)
    if (toasts.length) return { kind: 'toast', toasts }
    const urlChanged = fromUrl && page.url() !== fromUrl
    if (urlChanged) return { kind: 'url', url: page.url() }
    const dialogOpen = await page.locator(
      '.el-dialog:visible, .el-overlay-message-box:visible, .el-drawer:visible,' +
      // 2026-11-12：浮层类反馈也必须算"有反应"，否则「筛选设置」(fs-panel)、下拉菜单(下推)、
      // 气泡面板点击后被误判成"点了无任何反馈"（实测：26 张列表页筛选设置全部假红）。
      '.fs-panel:visible, .el-popover:visible, .el-dropdown-menu:visible, .el-popper:visible'
    ).count().catch(() => 0)
    if (dialogOpen > 0) return { kind: 'dialog' }
    await page.waitForTimeout(300)
  }
  return { kind: 'none' }
}

export function sleep(ms) {
  return new Promise(r => setTimeout(r, ms))
}
