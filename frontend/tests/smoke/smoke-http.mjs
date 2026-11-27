/**
 * 冒烟测试用 HTTP 客户端（Node 侧直连后端，不经浏览器）
 *
 * 项目铁律（不遵守会把"登录过期"误判成"接口挂了"）：
 *   1) 业务错误约定为 HTTP 200 + body.code !== 200；
 *   2) 鉴权失败是唯一例外 —— 裸 401 无响应体（project_memory.md 2026-09-11 铁律①），
 *      所以不能假设 401 一定带 JSON。
 *   3) 禁止空 catch 吞异常；所有失败必须带原始响应体片段抛出。
 */
import { cfg, assertTargetAllowed } from './smoke-env.mjs'

const TIMEOUT_MS = 30000

/**
 * ======================= 测试租户三步闸门（本文件的核心防线）=======================
 *
 * 为什么必须存在（源码实证，不是防御性编程）：
 *   1) `/api/auth/login` 返回的 token **不含 tenantId**（JwtUtil.java:31-39 的 generateToken）
 *   2) `TenantRoutingDataSource.determineCurrentLookupKey()`（L24-30）在拿不到 tenantDbName 时
 *      返回 "default" → 落到 `erp_db`（主库）
 *   3) 所以"登录成功但还没选租户"这段时间里发出的**任何业务请求都会写主库**
 *
 * 处置：把"选租户"做成强制中间态 —— 未 select 前，除白名单外一律拒绝发请求。
 *   白名单只有 3 条：登录 / 列我的租户 / 选租户。其余全部要先 select。
 */
const TENANT_FREE_PATHS = [
  /^\/api\/auth\/login$/,
  /^\/api\/tenant\/my$/,
  /^\/api\/tenant\/select$/
]

/** 当前会话选中的租户（login 成功后由 selectTenant 写入） */
let tenantCtx = null

function isTenantFree(urlPath) {
  const p = String(urlPath || '').split('?')[0]
  return TENANT_FREE_PATHS.some(re => re.test(p))
}

/** 业务请求前的闸门：未选租户即抛错，杜绝"静默写主库" */
export function assertTenantSelected(urlPath) {
  if (isTenantFree(urlPath)) return
  if (!tenantCtx) {
    throw new Error(
      `拒绝执行 ${urlPath}：尚未选定测试租户。\n` +
      `此时 token 不含 tenantId，请求会被后端路由到主库 erp_db。\n` +
      `请先通过 login() 完成 login → /api/tenant/select 三步闸门。`
    )
  }
}

/** 取当前选中的租户上下文（供用例打印与断言） */
export function currentTenant() {
  return tenantCtx ? { ...tenantCtx } : null
}

/** 仅测试内部使用：重置闸门状态 */
export function resetTenantContext() {
  tenantCtx = null
}

function fullUrl(p) {
  if (/^https?:\/\//i.test(p)) return p
  return cfg.baseUrl + (p.startsWith('/') ? p : '/' + p)
}

async function rawFetch(method, urlPath, { token, body, headers = {}, timeout = TIMEOUT_MS } = {}) {
  // 每个出网请求都单独复核目标主机（不只依赖入口校验），不在白名单即拒绝
  assertTargetAllowed()
  const target = fullUrl(urlPath)
  assertTargetAllowed(target)
  if (!target.startsWith(cfg.baseUrl)) {
    throw new Error(`拒绝执行：请求地址 ${target} 不属于当前目标环境 ${cfg.baseUrl}`)
  }
  // 租户闸门：必须在 rawFetch 层做，才能覆盖所有调用路径（含未来新增的调用方式）
  assertTenantSelected(urlPath)
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeout)
  const h = { Accept: 'application/json', ...headers }
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (token) h['Authorization'] = `Bearer ${token}`
  let res, text
  try {
    res = await fetch(fullUrl(urlPath), {
      method,
      headers: h,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: ctrl.signal
    })
    // 可能是空体（401 场景），必须容错
    text = await res.text()
  } catch (e) {
    clearTimeout(timer)
    throw new Error(`${method} ${urlPath} 请求失败: ${e.message}`)
  }
  clearTimeout(timer)

  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch (_) {
    // 保留原文即可，下面按"非 JSON"处理
  }
  return { status: res.status, ok: res.ok, text, json }
}

/** 只发请求，把"HTTP 状态 + 业务 code"统一成结果对象，不抛业务异常 */
export async function api(method, urlPath, { token, body, headers, timeout } = {}) {
  const r = await rawFetch(method, urlPath, { token, body, headers, timeout })
  const bizCode = r.json && typeof r.json.code !== 'undefined' ? r.json.code : null
  const message = (r.json && (r.json.message || r.json.msg)) || ''
  return {
    httpStatus: r.status,
    bizCode,
    message,
    data: r.json ? r.json.data : null,
    raw: r.text ? r.text.slice(0, 2000) : '',
    ok: r.status === 200 && (bizCode === null || bizCode === 200),
    authFailed: r.status === 401 || r.status === 403
  }
}

/** 要求成功的调用：失败即抛（带原始响应片段，便于定位） */
export async function apiOrThrow(method, urlPath, opts = {}) {
  const r = await api(method, urlPath, opts)
  if (!r.ok) {
    const hint = r.authFailed
      ? '（鉴权失败，登录态无效或已过期）'
      : (r.bizCode !== null ? `（业务码 ${r.bizCode}）` : '（非 JSON/异常响应）')
    throw new Error(`${method} ${urlPath} 调用失败${hint}: ${r.message || r.raw || `HTTP ${r.httpStatus}`}`)
  }
  return r.data
}

/**
 * 选定测试租户（三步闸门第 3 步）：调 /api/tenant/select 换回**带 tenantId 的 token**。
 *
 * 依据：TenantController./select（L66-150）校验 sys_user_tenant 关联后
 *       用 JwtUtil.generateTokenWithTenant（L41-50）签发新 token。
 * 断言：选中租户的 tenantCode 必须等于 E2E_TENANT_CODE，否则中止 ——
 *       这是"测试真的落在独立租户库"的第一道硬证据。
 */
async function selectTenant(loginToken, loginData, username) {
  const want = cfg.expectTenantCode
  const tenants = Array.isArray(loginData.tenants) ? loginData.tenants : []

  let target = tenants.find(t => String(t.tenantCode || '') === want)

  // 登录响应没给租户列表（单租户/兜底分支）→ 用 /api/tenant/my 补一次
  if (!target && tenants.length === 0) {
    const my = await api('GET', '/api/tenant/my', { token: loginToken })
    const list = Array.isArray(my.data) ? my.data : ((my.data && my.data.records) || [])
    target = list.find(t => String(t.tenantCode || '') === want)
    if (!target && list.length === 1) target = list[0]
    if (!target && list.length > 1) {
      throw new Error(
        `租户列表里找不到期望租户 "${want}"（账号 ${username}）。` +
        `实际有 ${list.length} 个：${list.map(t => t.tenantCode).join(', ')}。` +
        `请核对 E2E_TENANT_CODE 是否与建租户时填的编码一致。`
      )
    }
  }

  if (!target) {
    throw new Error(
      `未能确定测试租户：期望 tenantCode="${want}"，` +
      `登录响应 tenants=${tenants.length} 条${tenants.length ? `（${tenants.map(t => t.tenantCode).join(', ')}）` : ''}。\n` +
      `可能原因：① E2E_TENANT_CODE 与建租户编码不一致；② 该管理员未关联到该租户（sys_user_tenant 缺行）。`
    )
  }

  const sel = await api('POST', '/api/tenant/select', { token: loginToken, body: { tenantId: target.tenantId } })
  if (!sel.ok) {
    throw new Error(
      `选择租户失败（tenantId=${target.tenantId}, tenantCode=${target.tenantCode}）: ` +
      `${sel.message || sel.raw || `HTTP ${sel.httpStatus}`}`
    )
  }
  // 兼容两种返回形态：data 是对象（含 token）或 data 就是 token 字符串
  const d = sel.data
  const newToken = (d && typeof d === 'object' && (d.token || d.accessToken)) || (typeof d === 'string' ? d : null)
  if (!newToken) {
    throw new Error(`选择租户成功但响应里没有新 token。原始响应: ${sel.raw}`)
  }

  const actualCode = (d && typeof d === 'object' && (d.tenantCode || d.code)) || target.tenantCode
  if (want && String(actualCode) !== want) {
    throw new Error(
      `★隔离断言失败：选中的租户是 "${actualCode}"，期望 "${want}"。\n` +
      `继续执行会把测试数据写进错误的租户库，已中止。`
    )
  }

  tenantCtx = {
    tenantId: target.tenantId,
    tenantCode: String(actualCode),
    tenantName: (d && typeof d === 'object' && d.tenantName) || target.tenantName || '',
    dbName: cfg.expectTenantDb || `erp_tenant_${String(actualCode).toLowerCase().replace(/-/g, '_')}`
  }
  console.log(
    `[租户闸门] 已选定测试租户：tenantCode=${tenantCtx.tenantCode} tenantId=${tenantCtx.tenantId} ` +
    `目标库=${tenantCtx.dbName}（后续请求均落该库）`
  )
  return { token: newToken, ...tenantCtx }
}

/**
 * 登录并完成三步闸门：login → /api/tenant/select → 可用 token。
 *
 * 严禁默认口令兜底：账号/口令必须显式提供（见 smoke-env.mjs assertTenantCredsPresent）。
 * 返回的 token 是**已带 tenantId** 的那个，调用方必须用它发业务请求。
 */
export async function login(username = cfg.adminUser, password = cfg.adminPassword) {
  if (!username || !password) {
    throw new Error(
      'login() 缺少账号或口令：本套测试禁止默认口令兜底，' +
      '请设置 E2E_TENANT_ADMIN_USER / E2E_TENANT_ADMIN_PASSWORD 环境变量。'
    )
  }
  const r = await api('POST', '/api/auth/login', { body: { username, password } })
  if (!r.ok) {
    const hint = r.authFailed ? '（用户名或密码错误，或该账号被禁用）' : ''
    throw new Error(`登录失败 ${hint}: ${r.message || r.raw || `HTTP ${r.httpStatus}`}`)
  }
  const loginToken = r.data && r.data.token
  if (!loginToken) {
    throw new Error(`登录成功但响应里没有 token 字段。原始响应: ${r.raw}`)
  }
  const tenant = await selectTenant(loginToken, r.data, username)
  return { token: tenant.token, loginData: r.data, tenant }
}

/** 取用户权限信息（menuRoutePaths / permissions / roles） */
export async function userinfo(token) {
  const d = await apiOrThrow('GET', '/api/auth/userinfo', { token })
  return {
    userId: d && d.userId,
    username: d && d.username,
    realName: d && d.realName,
    roles: (d && d.roles) || [],
    menuIds: (d && d.menuIds) || [],
    menuRoutePaths: (d && d.menuRoutePaths) || [],
    permissions: (d && d.permissions) || []
  }
}

export { fullUrl }
