/**
 * 重建测试租户（环境准备工具，不参与业务测试）
 *
 * 用途：当某个已有租户是"模板库不新鲜"时创建的，它的管理员角色会是空壳
 *      （sys_role_permission 里 0 条权限），且可能缺少标准 ADMIN 角色 → 管理员登不进功能。
 *      本工具按当前模板库重建该租户，使其一次拿到完整的菜单 + 角色 + 权限种子。
 *
 * 安全护栏（三重）：
 *   ① 只允许删/建 tenantCode 在 SAFE_CODES 白名单内的租户；
 *   ② 必须显式传 --yes 才真正执行删除；
 *   ③ 需要 E2E_ALLOW_SUPERADMIN=true（用平台超管执行环境准备，业务用例禁用超管）。
 *
 * 用法（PowerShell）：
 *   $env:E2E_ALLOW_SUPERADMIN='true'
 *   $env:E2E_TENANT_ADMIN_USER='admin'; $env:E2E_TENANT_ADMIN_PASSWORD='admin123'
 *   $env:E2E_TENANT_CODE='DEFAULT'          # 超管只能进 DEFAULT 租户，用于取 token
 *   node tests/smoke/recreate-tenant.mjs sc_wudong --yes
 */
import { cfg } from './smoke-env.mjs'
import { login, api } from './smoke-http.mjs'

/** 允许重建的租户编码白名单（测试租户；生产租户一律拒绝） */
const SAFE_CODES = ['sc_wudong', 'autotest', 'qa', 'qatest', 'test']

const targetCode = (process.argv[2] || '').trim()
const confirmed = process.argv.includes('--yes')

if (!targetCode) {
  console.error('用法: node recreate-tenant.mjs <tenantCode> --yes')
  process.exit(2)
}
if (!SAFE_CODES.includes(targetCode)) {
  console.error(`拒绝执行：租户编码 "${targetCode}" 不在测试租户白名单内（${SAFE_CODES.join(', ')}）。`)
  process.exit(2)
}
if (!confirmed) {
  console.error(`将删除并重建租户 "${targetCode}"（会 DROP 该租户的数据库，不可逆）。确认请加 --yes`)
  process.exit(2)
}

/** 重建参数（与首次创建保持一致） */
const CREATE_BODY = {
  tenantCode: targetCode,
  tenantName: '自动化测试租户',
  version: 'ULTIMATE',
  trialDays: 0,
  expireDate: '2099-12-31',
  maxUsers: 50,
  adminUsername: 'Lyc123',
  adminPassword: '123456',
  contactPerson: 'E2E',
  remark: '由 recreate-tenant.mjs 重建（用于验证"创建即可用"的权限种子）'
}

const { token } = await login()
console.log(`\n使用平台超管执行环境准备（目标租户 ${targetCode}）`)

// 1) 找到租户（注意：该接口返回的是 SQL 直查的 Map，字段名是 snake_case）
const list = await api('GET', '/api/admin/tenant/list?page=1&size=200', { token })
const rows = Array.isArray(list.data) ? list.data : ((list.data && list.data.records) || [])
const codeOf = r => String(r.tenant_code ?? r.tenantCode ?? '')
const found = rows.find(r => codeOf(r) === targetCode)
if (!found) {
  console.error(`未找到租户 ${targetCode}；共解析到 ${rows.length} 行`)
  console.error(`  列表原始响应: ${String(list.raw).slice(0, 600)}`)
  process.exit(1)
}
const tid = found.tenant_id ?? found.tenantId ?? found.id
const dbName = found.db_name ?? found.dbName ?? ''
console.log(`  找到租户: tenantId=${tid} code=${codeOf(found)} db=${dbName} version=${found.version ?? ''}`)

// 2) 删除（物理删库）
console.log('  正在删除（DROP DATABASE，不可逆）...')
const del = await api('POST', `/api/admin/tenant/${tid}/delete`, { token })
console.log(`  删除结果: http=${del.httpStatus} code=${del.bizCode} msg=${del.message || ''}`)
if (!del.ok) {
  console.error('  删除失败，中止（不继续创建，避免同码冲突）')
  process.exit(1)
}

// 3) 重建
console.log('  正在按当前模板库重建...')
const created = await api('POST', '/api/admin/tenant/create', { token, body: CREATE_BODY })
console.log(`  创建结果: http=${created.httpStatus} code=${created.bizCode} msg=${created.message || ''}`)
if (!created.ok) {
  console.error(`  创建失败: ${created.raw}`)
  process.exit(1)
}
const d = created.data || {}
console.log('\n重建完成：')
console.log(`  租户编码 : ${d.tenantCode ?? targetCode}`)
console.log(`  租户ID   : ${d.tenantId ?? '(见响应)'}`)
console.log(`  库名     : ${d.dbName ?? ''}`)
console.log(`  管理员   : ${d.adminUsername ?? CREATE_BODY.adminUsername}`)
console.log('\n下一步：用该管理员账号跑 diag-tenant.mjs 验收（菜单/权限应非空，且删改用户不再 403）')
