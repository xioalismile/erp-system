/**
 * 冒烟测试环境与页面清单解析（唯一数据源：src/config/menuConfig.js）
 *
 * 为什么要求值而不是正则解析：
 *   menuConfig.js 用了 import.meta.env，Node 无法直接 import；
 *   但它同时又是"左侧菜单 + 分组 + 角色可见性"的唯一权威定义。
 *   正则解析一旦失配会静默产出空表 → 测试"全绿但啥也没测"。
 *   这里改用「stub 掉 import.meta.env 后真实求值」，并配自检门控。
 *
 * 立项依据：d:\erp\.trae\documents\前端冒烟测试_立项登记表.md
 *   - R1  8cloud 根路径是官网宣传页 → 入口用 E2E_ENTRY_PATH（默认 /index.html）
 *   - R4  模块/页面清单解析必须有自检门控，解析异常立即抛错
 *   - 边界4 环境白名单：只允许 8cloud / localhost，命中 121 直接拒绝
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/**
 * 加载本地凭据文件 .env.local（与 smoke-env.mjs 同目录，**不进仓库**）。
 *
 * 为什么需要：本套测试禁止内置任何口令（守则 03 · 凭据不入库），
 * 但要求每次手敲环境变量对使用者太重。折中：把测试租户凭据写在本地文件里读一次。
 * 规则：已存在的环境变量优先（便于命令行/CI 临时覆盖），# 开头为注释。
 */
function loadLocalEnv() {
  const p = path.join(__dirname, '.env.local')
  if (!fs.existsSync(p)) return
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const s = line.trim()
    if (!s || s.startsWith('#')) continue
    const i = s.indexOf('=')
    if (i <= 0) continue
    const k = s.slice(0, i).trim()
    const v = s.slice(i + 1).trim()
    if (!process.env[k]) process.env[k] = v
  }
}
loadLocalEnv()

export const SMOKE_DIR = __dirname
export const OUT_DIR = path.join(__dirname, '.out')
export const FRONTEND_DIR = path.resolve(__dirname, '..', '..')
const MENU_CONFIG_FILE = path.join(FRONTEND_DIR, 'src', 'config', 'menuConfig.js')

/**
 * 允许被测试的目标主机（硬白名单）
 * 只认白名单：不在名单内一律拒绝，因此不需要、也不允许出现任何客户服务器地址。
 * 目的：本套测试只在自营环境（8cloud / 本机）运行。
 */
const ALLOWED_HOSTS = ['kaiyu.pro', 'www.kaiyu.pro', '8.148.229.2', 'localhost', '127.0.0.1']

function envStr(name, dflt) {
  const v = process.env[name]
  return (v === undefined || v === '') ? dflt : v
}
function envBool(name, dflt) {
  const v = process.env[name]
  if (v === undefined || v === '') return dflt
  return String(v).toLowerCase() === 'true'
}

/**
 * 凭据一律来自环境变量，代码里不写任何口令（守则 03 · 凭据不入库）。
 * 注意：这里刻意**不再**回退到 admin/admin123 —— 本套测试禁止使用平台超管账号，
 * 否则一旦路由落到 default 池就会写主库 erp_db（见 smoke-http.mjs 三步闸门说明）。
 */
const tenantAdminUserFromEnv = envStr('E2E_TENANT_ADMIN_USER', '')
const tenantAdminPasswordFromEnv = envStr('E2E_TENANT_ADMIN_PASSWORD', '')
const adminPasswordFromEnv = !!tenantAdminPasswordFromEnv

export const cfg = {
  baseUrl: (envStr('E2E_BASE_URL', 'https://kaiyu.pro')).replace(/\/+$/, ''),
  /**
   * 入口必须是 SPA 白名单路由，不能是 /index.html。
   *
   * 实测教训（2026-09-13）：把入口设成 /index.html 时，SPA 的 URL 路径就是 /index.html，
   * 而路由守卫把"不在 menuRoutePaths 里的路径"一律判为无权 → 登录成功后直接被踢到 /403
   * （实测页面显示"您没有权限访问该页面，当前角色：ADMIN"）。
   * 注意区分：部署健康探针要打 /index.html 是为了读 build-version meta，
   * 那是"探版本"，不等于"用户访问入口"。
   * /login 在守卫 WHITE 名单内，登录后守卫会自动送 /dashboard。
   */
  entryPath: envStr('E2E_ENTRY_PATH', '/login'),

  adminUser: tenantAdminUserFromEnv,
  adminPassword: tenantAdminPasswordFromEnv,
  // 供日志提示"当前是否在使用默认凭据"
  adminPasswordFromEnv,
  /**
   * 测试租户标识：所有业务请求必须落在该租户自己的库里（erp_tenant_<code>）。
   * 用两个字段做"隔离真的生效"的硬断言：
   *   expectTenantCode  期望选中的租户编码（E2E_TENANT_CODE，默认 sc_wudong）
   *   expectTenantDb    期望的租户库名（E2E_TENANT_DB，默认 erp_tenant_<code>）
   * 断言点见 smoke-http.mjs 的 assertTenantSelected()：实际租户编码与期望不符即中止，
   * 避免"以为在测试库跑、实际写了主库"。
   *
   * 8cloud 上的测试租户（2026-09-20 创建）：编码 sc_wudong → 库 erp_tenant_sc_wudong
   */
  expectTenantCode: envStr('E2E_TENANT_CODE', 'sc_wudong'),
  expectTenantDb: envStr('E2E_TENANT_DB', ''),
  moduleFilter: envStr('E2E_MODULE_FILTER', '').split(',').map(s => s.trim()).filter(Boolean),
  rolePrefix: envStr('E2E_ROLE_PREFIX', 'SMOKE_'),
  gradual: envBool('E2E_GRADUAL', true),
  writeLoop: envBool('E2E_WRITE_LOOP', true),
  headless: envBool('E2E_HEADLESS', true),
  // 测试账号密码：后端 SysUserServiceImpl 建号时无条件覆写为 admin123（风险 R3）
  testUserPassword: envStr('E2E_TEST_USER_PASSWORD', 'admin123'),
  // 业务测试数据前缀（写入单据编号/名称，供清理脚本按前缀回收）
  dataPrefix: envStr('E2E_DATA_PREFIX', 'SMOKE-'),
  allowedHosts: ALLOWED_HOSTS
}

/** 目标环境白名单校验（唯一防线）：不在白名单内直接拒绝，不发出任何请求 */
export function assertTargetAllowed(baseUrl = cfg.baseUrl) {
  let host
  try {
    host = new URL(baseUrl).hostname
  } catch (e) {
    throw new Error(`E2E_BASE_URL 不是合法 URL: ${baseUrl}`)
  }
  if (!ALLOWED_HOSTS.includes(host)) {
    throw new Error(
      `拒绝执行：目标主机 ${host} 不在白名单内。只允许 ${ALLOWED_HOSTS.join(' / ')}。` +
      `本套测试仅在自营环境运行；如需新增目标，必须先经确认并说明理由。`
    )
  }
  return host
}

/** 求值 menuConfig.js，返回 MENU_GROUPS（真实结构，含 children） */
export async function loadMenuGroups() {
  if (!fs.existsSync(MENU_CONFIG_FILE)) {
    throw new Error(`找不到菜单配置文件：${MENU_CONFIG_FILE}`)
  }
  const src = fs.readFileSync(MENU_CONFIG_FILE, 'utf8')
  // 只替换 import.meta.env，其余原样；stub 值决定 platform 等条件分组的取舍
  const stub = {
    VITE_HIDDEN_MODULES: envStr('E2E_VITE_HIDDEN_MODULES', ''),
    VITE_HIDDEN_BILLS: envStr('E2E_VITE_HIDDEN_BILLS', ''),
    VITE_PLATFORM_ADMIN: envStr('E2E_VITE_PLATFORM_ADMIN', ''),
    VITE_DEPLOY_ADMIN: envStr('E2E_VITE_DEPLOY_ADMIN', '')
  }
  // menuConfig 只从 hiddenBills 取 isBillHidden；临时文件在同级没有该模块，
  // 这里按同样口径内联一份，避免求值时报 "Cannot find module ./hiddenBills"
  const inlineDeps =
    `const __HIDDEN_BILLS__ = new Set(String(__ENV__.VITE_HIDDEN_BILLS || '').split(',').map(s => s.trim().toUpperCase()).filter(Boolean));\n` +
    `const isBillHidden = (code) => !!code && __HIDDEN_BILLS__.has(String(code).trim().toUpperCase());\n`
  const patched =
    `const __ENV__ = ${JSON.stringify(stub)};\n` +
    inlineDeps +
    src
      .replace(/import\s*\{[^}]*\}\s*from\s*['"]\.\/hiddenBills['"];?\s*/g, '')
      .replace(/import\.meta\.env/g, '__ENV__')

  fs.mkdirSync(OUT_DIR, { recursive: true })
  const tmp = path.join(OUT_DIR, `_menuconfig.tmp.${Date.now()}.mjs`)
  fs.writeFileSync(tmp, patched, 'utf8')
  let groups
  try {
    const mod = await import(pathToFileURL(tmp).href + `?t=${Date.now()}`)
    groups = mod.MENU_GROUPS
  } finally {
    try { fs.unlinkSync(tmp) } catch (_) { /* 清理失败不影响测试 */ }
  }
  if (!Array.isArray(groups)) {
    throw new Error('menuConfig.js 求值结果 MENU_GROUPS 不是数组，解析失败')
  }
  return groups
}

/** 扁平化某分组下所有叶子菜单项（含 children） */
export function groupLeafItems(group) {
  const out = []
  for (const item of (group.items || [])) {
    if (item.path) out.push(item)
    if (Array.isArray(item.children)) {
      for (const c of item.children) if (c.path) out.push(c)
    }
  }
  return out
}

/** 全量叶子菜单项（带 groupKey） */
export function flattenAll(groups) {
  const out = []
  for (const g of groups) {
    for (const item of groupLeafItems(g)) {
      out.push({ groupKey: g.key, groupLabel: g.label, ...item })
    }
  }
  return out
}

/**
 * 自检门控（对应风险 R4）：解析异常必须立即抛错，禁止静默产出空表。
 * 返回 { groups, allItems, moduleCount, pageCount }
 */
export function selfCheck(groups) {
  const problems = []
  if (!groups.length) problems.push('模块数为 0')
  if (groups.length < 14) problems.push(`模块数 ${groups.length} < 14（预期至少 14 个业务模块）`)
  for (const g of groups) {
    if (!g.key) problems.push(`存在无 key 的模块: ${JSON.stringify(g.label)}`)
    if (!g.label) problems.push(`模块 ${g.key} 缺 label`)
    const leaves = groupLeafItems(g)
    if (!leaves.length) problems.push(`模块 ${g.key}(${g.label}) 没有任何菜单项 → 该模块会"测了等于没测"`)
  }
  const allItems = flattenAll(groups)
  const pathSet = new Set(allItems.map(i => i.path))
  if (pathSet.size < 80) {
    problems.push(`解析出的唯一菜单路径仅 ${pathSet.size} 条（预期 ≥ 80），疑似解析失配`)
  }
  for (const p of pathSet) {
    if (!p.startsWith('/')) problems.push(`菜单路径格式异常（未以 / 开头）: ${p}`)
  }
  if (problems.length) {
    throw new Error('menuConfig 自检未通过，已中止（防止解析失配导致"全绿但未测"）：\n  - ' + problems.join('\n  - '))
  }
  return { groups, allItems, moduleCount: groups.length, pageCount: pathSet.size }
}

/** 按模块 key 取分组 */
export function groupMap(groups) {
  const m = new Map()
  for (const g of groups) m.set(g.key, g)
  return m
}

/**
 * 复刻前端 getMenuGroupsByRoutePaths 的可见性算法（menuConfig.js L395-L465），
 * 用于精确计算"某角色登录后左侧应可见的菜单项"。
 *
 * 关键事实（已读源码确认）：menuRoutePaths 非空时**不再**按 group.roles 过滤，
 * 纯按"路径首段 === 模块 key"决定分组是否出现；组内再按 path 精确过滤。
 * 另外 /system/settings 及其子项永不渲染在侧边栏（走齿轮图标入口）。
 *
 * @returns {{groups: string[], items: string[]}} 期望可见的模块 key 与菜单路径
 */
export function expectedSidebar(groups, menuRoutePaths, roles = []) {
  const paths = Array.isArray(menuRoutePaths) ? menuRoutePaths : []
  // 规则1：menuRoutePaths 为空 → 按静态 roles 兜底（复刻 getMenuGroupsByRoles）
  if (paths.length === 0) {
    let byRoles
    if (roles.includes('SUPER_ADMIN')) byRoles = groups
    else if (roles.includes('admin') || roles.includes('ADMIN') || roles.includes('MANAGER')) {
      byRoles = groups.filter(g => g.key !== 'platform')
    } else if (!roles.length) byRoles = []
    else byRoles = groups.filter(g => !g.roles || g.roles.some(r => roles.includes(r)))
    return {
      groups: byRoles.map(g => g.key),
      items: flattenAll(byRoles).map(i => i.path)
    }
  }

  const pathSet = new Set(paths)
  const visGroups = []
  const visItems = []
  for (const g of groups) {
    const groupHasAnyMatch =
      paths.some(p => p.split('/').filter(Boolean)[0] === g.key) ||
      (g.items || []).some(item => pathSet.has(item.path))
    if (!groupHasAnyMatch) continue

    const kept = []
    for (const item of (g.items || [])) {
      // 设置中心及其子功能不在侧边栏渲染
      if (item.path && item.path.startsWith('/system/settings')) continue
      if (Array.isArray(item.children) && item.children.length) {
        const itemHasPerm = pathSet.has(item.path)
        const kids = item.children.filter(
          c => pathSet.has(c.path) && !(c.path && c.path.startsWith('/system/settings'))
        )
        if (itemHasPerm || kids.length > 0) {
          const useKids = itemHasPerm ? item.children.filter(c => !(c.path && c.path.startsWith('/system/settings'))) : kids
          for (const c of useKids) kept.push(c.path)
        }
        continue
      }
      if (pathSet.has(item.path)) kept.push(item.path)
    }
    if (kept.length > 0) {
      visGroups.push(g.key)
      visItems.push(...kept)
    }
  }
  return { groups: visGroups, items: visItems }
}

/** 测试账号 / 角色命名（与立项登记表 第三节 一致） */
export function accountFor(moduleKey) {
  const up = moduleKey.toUpperCase()
  return {
    roleCode: `${cfg.rolePrefix}${up}`,
    roleName: `冒烟-${moduleKey}`,
    username: `smoke_${moduleKey}`
  }
}

/**
 * 把路径集合映射回"侧边栏应显示的文案"，用于 DOM 层（用户肉眼可见）断言。
 * 注意：菜单名可能被"设置中心→菜单名称设置"改写，故同时返回 path→label 映射，
 * 断言失败时能直接看出是"权限没生效"还是"菜单被改名"。
 */
export function labelsForPaths(groups, paths) {
  const want = new Set(paths)
  const labels = []
  const map = {}
  for (const g of groups) {
    for (const item of groupLeafItems(g)) {
      if (want.has(item.path)) {
        labels.push(item.label)
        map[item.path] = item.label
      }
    }
  }
  return { labels, map }
}

/** 取某分组下第一个叶子菜单路径（用于"跨模块越权访问"探测） */
export function firstPathOfGroup(group) {
  const leaves = groupLeafItems(group)
  return leaves.length ? leaves[0].path : null
}

/**
 * 精确复刻侧边栏"应显示的文案"（对应 MainLayout.vue L17-L46 的渲染分支 +
 * menuConfig.js getMenuGroupsByRoutePaths L412-L465 的过滤逻辑）。
 * 与 expectedSidebar 的区别：本函数产出"用户肉眼看到的文字"，用于 DOM 层断言。
 * 注意三级菜单（如 销售管理→报表→分析报表）：父项没有 path，
 * 只要其任一子项被授权，父项标题也会渲染出来，必须一并计入，否则会误报"多出菜单"。
 */
export function expectedSidebarDom(groups, menuRoutePaths) {
  const paths = Array.isArray(menuRoutePaths) ? menuRoutePaths : []
  if (paths.length === 0) return { groupLabels: [], itemLabels: [] }
  const pathSet = new Set(paths)
  const groupLabels = []
  const itemLabels = []
  for (const g of groups) {
    const groupHasAnyMatch =
      paths.some(p => p.split('/').filter(Boolean)[0] === g.key) ||
      (g.items || []).some(it => pathSet.has(it.path))
    if (!groupHasAnyMatch) continue

    const kept = []
    for (const item of (g.items || [])) {
      if (item.path && item.path.startsWith('/system/settings')) continue
      if (Array.isArray(item.children) && item.children.length) {
        const itemHasPerm = pathSet.has(item.path)
        const kids = item.children.filter(
          c => pathSet.has(c.path) && !(c.path && c.path.startsWith('/system/settings'))
        )
        if (itemHasPerm || kids.length > 0) {
          kept.push(item.label)
          const useKids = itemHasPerm
            ? item.children.filter(c => !(c.path && c.path.startsWith('/system/settings')))
            : kids
          for (const c of useKids) kept.push(c.label)
        }
        continue
      }
      if (pathSet.has(item.path)) kept.push(item.label)
    }
    if (kept.length > 0) {
      groupLabels.push(g.label)
      itemLabels.push(...kept)
    }
  }
  return { groupLabels, itemLabels }
}



/** 按 E2E_MODULE_FILTER 过滤模块 */
export function filterModules(groups, filter = cfg.moduleFilter) {
  if (!filter.length) return groups
  return groups.filter(g => filter.includes(g.key))
}

export function ensureOutDir() {
  fs.mkdirSync(OUT_DIR, { recursive: true })
  return OUT_DIR
}

export function writeJson(name, data) {
  ensureOutDir()
  const p = path.join(OUT_DIR, name)
  fs.writeFileSync(p, JSON.stringify(data, null, 2), 'utf8')
  return p
}

export function readJson(name) {
  const p = path.join(OUT_DIR, name)
  if (!fs.existsSync(p)) return null
  return JSON.parse(fs.readFileSync(p, 'utf8'))
}

/** 启动自检打印（对应立项登记表 初始化方案 第3条） */
export async function bootSummary() {
  const host = assertTargetAllowed()
  assertTenantCredsPresent()
  const groups = await loadMenuGroups()
  const chk = selfCheck(groups)
  const filtered = filterModules(groups)
  const lines = [
    '===== 冒烟测试启动自检 =====',
    `目标环境      : ${cfg.baseUrl}  (host=${host}，白名单校验通过)`,
    `入口路径      : ${cfg.entryPath}${cfg.entryPath === '/' ? '  ⚠ 8cloud 根路径是官网宣传页，建议改为 /index.html' : ''}`,
    `模块数        : ${chk.moduleCount}（本次执行 ${filtered.length} 个${cfg.moduleFilter.length ? '，已按 E2E_MODULE_FILTER 过滤' : ''}）`,
    `菜单路径总数  : ${chk.pageCount}`,
    `租户管理员    : 用户=${cfg.adminUser}  口令来源=${cfg.adminPasswordFromEnv ? '环境变量 ✓' : '缺失 ✗'}`,
    `测试租户标识  : tenantCode=${cfg.expectTenantCode}  期望库=${cfg.expectTenantDb || `erp_tenant_${cfg.expectTenantCode}`}`,
    `测试账号密码  : ${cfg.testUserPassword}（后端 SysUserServiceImpl 硬限制，不可自定义）`,
    `数据前缀      : ${cfg.dataPrefix}`,
    `headless      : ${cfg.headless}`,
    `累加验证      : ${cfg.gradual ? '开' : '关'}`,
    `端到端闭环    : ${cfg.writeLoop ? '开' : '关'}`,
    '============================'
  ]
  console.log(lines.join('\n'))
  return { host, groups, filtered, chk }
}

/**
 * 凭据必须显式提供，禁止任何形式的默认口令兜底。
 *
 * 为什么做成硬门控而不是"给个默认值先跑起来"：
 *   本项目历史事故里，测试脚本用 admin/admin123 兜底登录 → token 无 tenantId →
 *   请求全部落到 default 池 erp_db（主库）→ 测试数据污染主库且无人察觉。
 *   宁可开跑前就报错，也不允许"静默写到主库"。
 */
export function assertTenantCredsPresent() {
  const missing = []
  if (!cfg.adminUser) missing.push('E2E_TENANT_ADMIN_USER')
  if (!cfg.adminPassword) missing.push('E2E_TENANT_ADMIN_PASSWORD')
  if (missing.length) {
    throw new Error(
      `缺少测试租户管理员凭据：${missing.join(' / ')}。\n` +
      `本套测试禁止使用平台超管 admin（会把数据写进主库 erp_db）。\n` +
      `推荐做法：在 frontend/tests/smoke/.env.local 里填一次（该文件不进仓库），或设环境变量：\n` +
      `  E2E_TENANT_ADMIN_USER=Lyc123\n` +
      `  E2E_TENANT_ADMIN_PASSWORD=<建租户时设的密码>\n` +
      `  E2E_TENANT_CODE=sc_wudong\n` +
      `也可直接跑：powershell -File run-all-tests.ps1（会自动读取 .env.local）`
    )
  }
  // 超管豁免：仅用于「环境准备 / 诊断」（建租户、重装权限、对比鉴权基线）。
  // 业务用例一律禁止用超管 —— 它不属于任何单一租户，token 可能落主库 erp_db。
  if (/^admin$/i.test(cfg.adminUser) && !envBool('E2E_ALLOW_SUPERADMIN', false)) {
    throw new Error(
      `拒绝执行：E2E_TENANT_ADMIN_USER 被设为平台超管 "admin"。\n` +
      `超管账号不属于任何单一租户，登录后 token 可能落在主库 erp_db。\n` +
      `请改用建租户时自动生成的租户管理员账号（形如 <租户编码>_admin）。\n` +
      `（确为环境准备/诊断用途时，可显式设置 E2E_ALLOW_SUPERADMIN=true 临时放行）`
    )
  }
}

// 支持 `node tests/smoke/smoke-env.mjs` 直接查看清单（S1 交付判据）
const isDirectRun = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))
if (isDirectRun) {
  const { groups, filtered, chk } = await bootSummary()
  console.log('\n模块清单：')
  for (const g of filtered) {
    const leaves = groupLeafItems(g)
    console.log(`  [${g.key}] ${g.label}  roles=${g.roles ? g.roles.join('/') : '所有角色'}  菜单 ${leaves.length} 项`)
  }
  console.log(`\n合计：${filtered.length} 模块 / ${chk.pageCount} 菜单路径`)
}
