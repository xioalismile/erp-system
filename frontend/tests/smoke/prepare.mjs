/**
 * 阶段A：为每个模块创建「冒烟角色 + 账号」，并回读校验授权真的生效
 *
 * 用法：
 *   node tests/smoke/prepare.mjs --dry-run     # 只探测不写库（首次接入新环境先跑这个）
 *   node tests/smoke/prepare.mjs               # 正式创建（幂等：同名角色/账号复用并覆盖授权）
 *   node tests/smoke/prepare.mjs --cleanup     # 清理：先删 smoke_* 账号，再删 SMOKE_* 角色
 *
 * 授权口径（源码实证，不是猜的）：
 *   AuthController.userinfo 的 menuRoutePaths =「sys_menu 里按租户版本过滤的全部 route_path」
 *   ∩「该角色 sys_role_permission(permission_type='菜单') 的 menu_id」。
 *   即：授了一条环境里不存在的路径 = 白授，用户看不到菜单。
 *   因此本脚本的授权集合 = menuConfig 该模块路径 ∩ 测试租户管理员实际可用的 menuRoutePaths。
 *   注意：全程使用**测试租户管理员**（E2E_TENANT_ADMIN_USER），不使用平台超管 admin ——
 *         超管登录后 token 无 tenantId，会把角色/账号建到主库 erp_db。
 *
 * 风险 R2 的落实：授权后必须重新登录该测试账号并回读 userinfo，
 *   断言 menuRoutePaths 与授权集合完全一致（差异 0 条），否则中止。
 */
import {
  cfg, bootSummary, groupLeafItems, accountFor, expectedSidebar,
  writeJson, readJson, ensureOutDir, SMOKE_DIR
} from './smoke-env.mjs'
import { login, userinfo, api, apiOrThrow } from './smoke-http.mjs'

const argv = process.argv.slice(2)
const DRY = argv.includes('--dry-run')
const CLEANUP = argv.includes('--cleanup')

const OUT_ACCOUNTS = 'accounts.json'

/** 去掉 query（sys_menu.route_path 不含 ?item=xxx），用于与 DB 路径对齐 */
function normalizePath(p) {
  return String(p || '').split('?')[0]
}

async function findByRoleCode(token, roleCode) {
  const d = await apiOrThrow('GET', `/api/system/role/page?current=1&size=200&keyword=${encodeURIComponent(roleCode)}`, { token })
  const records = (d && d.records) || []
  return records.find(r => r.roleCode === roleCode) || null
}

async function findByUsername(token, username) {
  const d = await apiOrThrow('GET', `/api/system/user/page?current=1&size=200&keyword=${encodeURIComponent(username)}`, { token })
  const records = (d && d.records) || []
  return records.find(u => u.username === username) || null
}

async function ensureRole(token, { roleCode, roleName }) {
  const existing = await findByRoleCode(token, roleCode)
  if (existing) {
    console.log(`    角色已存在，复用: ${roleCode} (roleId=${existing.roleId})`)
    return existing
  }
  console.log(`    创建角色: ${roleCode} / ${roleName}`)
  await apiOrThrow('POST', '/api/system/role', {
    token,
    body: {
      roleCode,
      roleName,
      roleType: '业务角色',
      // 给"全部数据"保证页面能查到数据（否则列表空无法验证渲染），已与用户确认
      dataScope: '全部数据',
      isSystem: 0,
      sortOrder: 900,
      status: '启用',
      remark: '前端冒烟测试专用角色，由 tests/smoke/prepare.mjs 自动创建'
    }
  })
  const created = await findByRoleCode(token, roleCode)
  if (!created) throw new Error(`角色 ${roleCode} 创建后查不到，创建失败或查询口径不一致`)
  return created
}

async function ensureUser(token, { username, roleId, moduleLabel }) {
  const existing = await findByUsername(token, username)
  let user = existing
  if (!user) {
    console.log(`    创建账号: ${username}`)
    // 注意：password 会被后端无条件覆写为 admin123（SysUserServiceImpl L145），故不传
    await apiOrThrow('POST', '/api/system/user', {
      token,
      body: {
        username,
        realName: `冒烟-${moduleLabel}`,
        userType: '普通用户',
        remark: '前端冒烟测试专用账号，由 tests/smoke/prepare.mjs 自动创建'
      }
    })
    user = await findByUsername(token, username)
    if (!user) throw new Error(`账号 ${username} 创建后查不到`)
  } else {
    console.log(`    账号已存在，复用: ${username} (userId=${user.userId})`)
  }
  // 绑定角色（接口是"先删后插"覆盖式，幂等）
  await apiOrThrow('POST', `/api/system/user/${user.userId}/roles`, { token, body: [roleId] })
  return user
}

async function cleanup(token, moduleGroups) {
  console.log('\n===== 清理模式：删除 smoke_* 账号与 SMOKE_* 角色 =====')
  for (const g of moduleGroups) {
    const { roleCode, username } = accountFor(g.key)
    const u = await findByUsername(token, username)
    if (u) {
      await apiOrThrow('DELETE', `/api/system/user/${u.userId}`, { token })
      console.log(`  已删账号 ${username}`)
    } else {
      console.log(`  跳过（无此账号）${username}`)
    }
  }
  for (const g of moduleGroups) {
    const { roleCode } = accountFor(g.key)
    const r = await findByRoleCode(token, roleCode)
    if (r) {
      await apiOrThrow('DELETE', `/api/system/role/${r.roleId}`, { token })
      console.log(`  已删角色 ${roleCode}`)
    } else {
      console.log(`  跳过（无此角色）${roleCode}`)
    }
  }
  console.log('===== 清理完成 =====')
}

async function main() {
  const { groups, filtered } = await bootSummary()

  console.log(`\n登录测试租户管理员（${cfg.adminUser}）...`)
  const { token, tenant } = await login()
  console.log(`登录成功，已落测试租户：tenantCode=${tenant.tenantCode} tenantId=${tenant.tenantId} 库=${tenant.dbName}`)

  const baseInfo = await userinfo(token)
  const adminPaths = new Set(baseInfo.menuRoutePaths.map(normalizePath))
  console.log(`租户管理员可用菜单路径: ${adminPaths.size} 条，roles=${baseInfo.roles.join(',')}`)

  // DB 侧路径（交叉校验用，证明"环境真实存在"与 frontend 配置是否一致）
  let dbPaths = new Set()
  try {
    const menus = await apiOrThrow('GET', '/api/system/menu/list', { token })
    if (Array.isArray(menus)) {
      dbPaths = new Set(menus.map(m => m.routePath).filter(Boolean).map(normalizePath))
    }
  } catch (e) {
    console.log(`  提示: 读取 /api/system/menu/list 失败（将只用管理员路径作基准）: ${e.message}`)
  }
  console.log(`sys_menu 表 route_path: ${dbPaths.size} 条`)

  /**
   * 授权基准池 = 管理员实际可用的路径 ∪ sys_menu 里登记的全部 route_path。
   *
   * 为什么必须取并集（2026-09-20 在 8cloud 新租户 sc_wudong 上实测到）：
   *   新建租户的管理员角色（ADMIN_ULTIMATE）在 sys_role_permission 里可能**还没有任何授权行**，
   *   此时 userinfo.menuRoutePaths = 0（实测：sys_menu 514 条、角色 34 个，但管理员可授路径 0 条）。
   *   若只以"管理员路径"为基准，会把"环境明明有 514 个菜单"误判成"无可授路径"，
   *   结果所有测试账号授权为空、用例全红 —— 这是环境数据问题，不是测试该报的错误。
   *   sys_menu 是"该租户版本允许的全部菜单"，不会因为某角色未授权而为空，故用它兜底。
   */
  const grantPool = new Set([...adminPaths, ...dbPaths])
  if (grantPool.size === 0) {
    throw new Error(
      '该租户既无管理员可用路径、sys_menu 也没有任何 route_path —— 无法确定可授菜单，授权将全部落空。\n' +
      '请先确认该租户库的 sys_menu 是否已初始化（模板库 erp_tenant_template 是否新鲜）。'
    )
  }

  if (CLEANUP) {
    await cleanup(token, filtered)
    return
  }

  const accounts = []
  const plan = []

  // ---------- 计算每个模块的授权集合 ----------
  console.log('\n===== 授权集合计算（menuConfig 路径 ∩ 本环境真实可用路径）=====')
  for (const g of filtered) {
    const { roleCode, roleName, username } = accountFor(g.key)
    const rawPaths = groupLeafItems(g).map(i => i.path)
    const normalized = [...new Set(rawPaths.map(normalizePath))]
    const grantable = normalized.filter(p => grantPool.has(p))
    const missing = normalized.filter(p => !grantPool.has(p))

    console.log(`  [${g.key}] ${g.label}: 配置 ${normalized.length} 条 → 可授 ${grantable.length} 条` +
      (missing.length ? `  ⚠ 环境缺失 ${missing.length} 条: ${missing.slice(0, 5).join(', ')}${missing.length > 5 ? ' …' : ''}` : ''))
    if (grantable.length === 0) {
      console.log(`      ⚠ 该模块在本环境没有任何可授路径 → 其隔离断言将按"期望可见 0 项"处理`)
    }
    plan.push({ group: g, roleCode, roleName, username, grantable, missing })
  }

  // ---------- 环境覆盖度：侧边菜单之外的页面（必须如实登记，不伪装成已覆盖）----------
  const coveredByModules = new Set()
  for (const p of plan) for (const g2 of p.grantable) coveredByModules.add(g2)
  const uncovered = [...grantPool].filter(p => !coveredByModules.has(p)).sort()
  console.log(`\n===== 页面覆盖度 =====`)
  console.log(`  基准路径池 ${grantPool.size} 条（管理员 ${adminPaths.size} + sys_menu ${dbPaths.size}，取并集）；menuConfig 侧边菜单覆盖 ${coveredByModules.size} 条；不在侧边菜单 ${uncovered.length} 条`)
  if (uncovered.length) {
    console.log('  非侧边菜单页面（可达但不在左侧导航，按"同模块归属"纳入全量遍历，否则如实标记未覆盖）：')
    uncovered.forEach(p => console.log(`    - ${p}`))
  }

  // ---------- 逐模块创建 ----------
  console.log(`\n===== ${DRY ? '[DRY-RUN 不写库] ' : ''}创建角色 / 授权 / 建账号 =====`)
  for (const item of plan) {
    const { group: g, roleCode, roleName, username, grantable } = item
    console.log(`\n  [${g.key}] ${g.label}`)

    if (DRY) {
      const r = await findByRoleCode(token, roleCode)
      const u = await findByUsername(token, username)
      console.log(`    DRY: 角色 ${roleCode} ${r ? '已存在' : '待创建'}；账号 ${username} ${u ? '已存在' : '待创建'}；授权 ${grantable.length} 条`)
      accounts.push({ moduleKey: g.key, moduleLabel: g.label, roleCode, username, roleId: r ? r.roleId : null, userId: u ? u.userId : null, granted: grantable, missing: item.missing, verified: false })
      continue
    }

    const role = await ensureRole(token, { roleCode, roleName })
    if (grantable.length > 0) {
      await apiOrThrow('POST', `/api/system/role/${role.roleId}/permissions`, { token, body: grantable })
      console.log(`    已授权菜单 ${grantable.length} 条`)
    } else {
      // 清空授权，保证"期望 0 项"成立（覆盖上一轮可能残留的授权）
      await apiOrThrow('POST', `/api/system/role/${role.roleId}/permissions`, { token, body: [] })
      console.log('    已清空菜单授权（本环境无可授路径）')
    }
    const user = await ensureUser(token, { username, roleId: role.roleId, moduleLabel: g.label })
    accounts.push({ moduleKey: g.key, moduleLabel: g.label, roleCode, username, roleId: role.roleId, userId: user.userId, granted: grantable, missing: item.missing, verified: false })
  }

  if (DRY) {
    writeJson('accounts.dryrun.json', accounts)
    console.log('\nDRY-RUN 结束，未写库。结果已写入 tests/smoke/.out/accounts.dryrun.json')
    return
  }

  // ---------- 回读校验（R2 强制项）----------
  console.log('\n===== 授权回读校验（用测试账号真实登录 → userinfo.menuRoutePaths）=====')
  const failures = []
  for (const acc of accounts) {
    let info
    try {
      const r = await login(acc.username, cfg.testUserPassword)
      info = await userinfo(r.token)
    } catch (e) {
      acc.verified = false
      acc.error = e.message
      failures.push(`${acc.moduleKey}: 测试账号登录/取权限失败 → ${e.message}`)
      console.log(`  [${acc.moduleKey}] ✗ ${e.message}`)
      continue
    }
    const actual = [...new Set(info.menuRoutePaths.map(normalizePath))].sort()
    const expect = [...new Set(acc.granted)].sort()
    const onlyActual = actual.filter(p => !expect.includes(p))
    const onlyExpect = expect.filter(p => !actual.includes(p))
    acc.actualPaths = actual
    acc.roles = info.roles
    acc.diff = { onlyActual, onlyExpect }
    acc.verified = onlyActual.length === 0 && onlyExpect.length === 0

    const sb = expectedSidebar(groups, info.menuRoutePaths, info.roles)
    acc.expectedSidebarGroups = sb.groups
    acc.expectedSidebarItems = sb.items

    if (acc.verified) {
      console.log(`  [${acc.moduleKey}] ✓ 授权一致（${expect.length} 条），期望可见模块: ${sb.groups.join(',') || '(无)'}`)
    } else {
      failures.push(`${acc.moduleKey}: 授权不一致 多出=${onlyActual.length} 缺少=${onlyExpect.length}`)
      console.log(`  [${acc.moduleKey}] ✗ 授权不一致 多出=${onlyActual.length} 缺少=${onlyExpect.length}`)
      if (onlyExpect.length) console.log(`      缺少(授权了但没生效): ${onlyExpect.slice(0, 5).join(', ')}`)
      if (onlyActual.length) console.log(`      多出(未授权却生效): ${onlyActual.slice(0, 5).join(', ')}`)
    }
  }

  writeJson(OUT_ACCOUNTS, {
    generatedAt: new Date().toISOString(),
    baseUrl: cfg.baseUrl,
    // 租户上下文必须落盘：后续 02/03/04/05/06 用例据此断言"跑在同一个测试租户里"
    tenant: {
      tenantId: tenant.tenantId,
      tenantCode: tenant.tenantCode,
      tenantName: tenant.tenantName,
      dbName: tenant.dbName
    },
    testUserPassword: cfg.testUserPassword,
    dataPrefix: cfg.dataPrefix,
    accounts
  })
  console.log(`\n账号清单已写入 tests/smoke/.out/${OUT_ACCOUNTS}`)

  console.log(`\n===== 阶段A 汇总 =====`)
  console.log(`角色/账号: ${accounts.filter(a => a.roleId).length}/${accounts.length} 创建或复用成功`)
  console.log(`授权回读校验: ${accounts.filter(a => a.verified).length}/${accounts.length} 一致`)
  if (failures.length) {
    console.log(`失败明细 ${failures.length} 条：`)
    failures.forEach(f => console.log('  - ' + f))
    console.log('\n阶段A 未全部通过 → 中止（不进入阶段B）')
    process.exit(1)
  }
  console.log('阶段A 全部通过')
}

main().catch(e => {
  console.error('\n阶段A 执行失败：' + e.message)
  process.exit(1)
})
