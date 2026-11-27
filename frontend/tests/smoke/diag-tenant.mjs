/**
 * 测试租户环境诊断（只读，不写任何数据）
 *
 * 用途：接入一个新的测试租户时，先跑这个确认"该租户库的数据是否齐备"。
 * 典型问题（2026-09-20 在 sc_wudong 上实测到）：模板库 erp_tenant_template 不新鲜/为空时，
 * 克隆出来的租户库里 sys_menu / sys_role_permission 可能是空的，
 * 表现为"租户管理员登录成功，但左侧菜单一条都没有"（userinfo.menuRoutePaths = 0）。
 * 这时任何"按菜单授权"的测试都会假失败，必须先修模板库或补该租户库的数据。
 *
 * 用法：
 *   node tests/smoke/diag-tenant.mjs
 */
import { cfg, bootSummary } from './smoke-env.mjs'
import { login, userinfo, api, currentTenant } from './smoke-http.mjs'

function line(k, v) {
  console.log(`  ${String(k).padEnd(24, ' ')}: ${v}`)
}

async function main() {
  await bootSummary()
  console.log('\n===== 登录并选定测试租户 =====')
  const { token, tenant } = await login()
  const t = currentTenant() || tenant
  line('tenantId', t.tenantId)
  line('tenantCode', t.tenantCode)
  line('推断库名', t.dbName)

  // 解码 token payload：确认 select 之后拿到的 token 真的带 tenantId。
  // 为什么必查：后端 SysUserServiceImpl.saveUser 用 TenantContextHolder.getTenantId() 决定
  // "新用户归属哪个租户"，而该值来自拦截器解析 JWT 的 tenantId；若 token 里没有它，
  // 新建用户就不会被关联到测试租户（实测表现为"新账号登录只看到假 default 租户"）。
  const payload = (() => {
    try { return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8')) } catch (_) { return null }
  })()
  console.log('\n===== 令牌内容（验证租户闸门）=====')
  line('token.tenantId', payload ? (payload.tenantId ?? '(缺省!)') : '(无法解码)')
  line('token.sub', payload ? payload.sub : '-')
  line('token.username', payload ? payload.username : '-')
  if (payload && (payload.tenantId === undefined || payload.tenantId === null)) {
    console.log('  ✗ token 未携带 tenantId → 业务请求会落到主库 erp_db，且新建用户不会被归属到测试租户')
  }

  console.log('\n===== 身份与权限（/api/auth/userinfo）=====')
  const info = await userinfo(token)
  line('userId', info.userId)
  line('username', info.username)
  line('roles', info.roles.join(',') || '(空)')
  line('menuRoutePaths 条数', info.menuRoutePaths.length)
  line('permissions 条数', info.permissions.length)
  line('menuIds 条数', info.menuIds.length)
  if (info.menuRoutePaths.length) {
    line('前 10 条路径', info.menuRoutePaths.slice(0, 10).join(', '))
  }

  console.log('\n===== 该租户库的数据量（只读探测）=====')
  // sys_menu：菜单是否已初始化
  const menus = await api('GET', '/api/system/menu/list', { token })
  const menuCount = Array.isArray(menus.data) ? menus.data.length : null
  line('sys_menu 条数', menuCount === null ? `无法解析（http=${menus.httpStatus} code=${menus.bizCode} msg=${menus.message}）` : menuCount)

  // 角色：管理员角色是否存在
  const roles = await api('GET', '/api/system/role/page?current=1&size=200', { token })
  const roleRecs = (roles.data && roles.data.records) || []
  line('sys_role 条数', roleRecs.length)
  if (roleRecs.length) line('角色编码', roleRecs.map(r => r.roleCode).slice(0, 12).join(', '))

  // 用户：当前租户库里有几个人
  const users = await api('GET', '/api/system/user/page?current=1&size=200', { token })
  const userRecs = (users.data && users.data.records) || []
  line('sys_user 条数', userRecs.length)

  // 基础数据（决定闭环用例能不能填下拉）
  const bases = [
    ['物料', '/api/basedata/material/page?current=1&size=5'],
    ['客户', '/api/basedata/customer/page?current=1&size=5'],
    ['供应商', '/api/basedata/supplier/page?current=1&size=5'],
    ['仓库', '/api/basedata/warehouse/page?current=1&size=5'],
    ['计量单位', '/api/basedata/unit/page?current=1&size=5'],
    ['银行账户', '/api/basedata/bank-account/page?current=1&size=5'],
    ['币种', '/api/basedata/currency/list']
  ]
  console.log('\n  --- 基础资料（闭环用例填下拉的前置数据）---')
  for (const [label, path] of bases) {
    const r = await api('GET', path, { token })
    let n = null
    if (Array.isArray(r.data)) n = r.data.length
    else if (r.data && Array.isArray(r.data.records)) n = r.data.records.length
    else if (r.data && typeof r.data.total === 'number') n = r.data.total
    line(label, n === null ? `http=${r.httpStatus} code=${r.bizCode} msg=${r.message || ''}` : n)
  }

  console.log('\n===== 结论 =====')
  const problems = []
  if (info.menuRoutePaths.length === 0) {
    problems.push('menuRoutePaths 为 0 → 租户库里没有可选菜单（模板库 erp_tenant_template 可能为空/陈旧），'
      + '按菜单授权的用例会全部假失败。需先修模板库或给该租户库初始化菜单与角色授权。')
  }
  if (menuCount === 0) problems.push('sys_menu 为空 → 同上')
  if (problems.length) {
    problems.forEach(p => console.log('  ✗ ' + p))
    process.exit(1)
  }
  console.log('  ✓ 该租户环境可用于测试')
}

main().catch(e => {
  console.error('\n诊断失败：' + e.message)
  process.exit(1)
})
