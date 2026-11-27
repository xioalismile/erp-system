/**
 * 鉴权探测（只读/安全，不删改真实数据）
 *
 * 用途：当"租户管理员被判定为无权限"时，用它一次性看清
 *   哪些接口被拒（403 业务码 "无权限管理用户"）、哪些能过，
 *   从而定位是 Controller 里的 isAdmin() 判定失败，还是别的原因。
 *
 * 安全设计：只探测"删除一个不存在的用户 id"（鉴权通过后必然报"用户不存在"，不会误删数据），
 *          其余全部是只读查询接口。
 *
 * 用法：node tests/smoke/probe-admin.mjs
 */
import { bootSummary } from './smoke-env.mjs'
import { login, api, currentTenant, userinfo } from './smoke-http.mjs'

await bootSummary()
const { token } = await login()
const t = currentTenant()
const info = await userinfo(token)

console.log('\n===== 当前身份 =====')
console.log(`  租户        : ${t.tenantCode} (tenantId=${t.tenantId})`)
console.log(`  userId      : ${info.userId}  username: ${info.username}`)
console.log(`  roles(接口) : ${info.roles.join(',') || '(空)'}`)
console.log(`  权限码条数  : ${info.permissions.length}`)
console.log(`  菜单路径条数: ${info.menuRoutePaths.length}`)

const probes = [
  ['只读: 用户分页', 'GET', '/api/system/user/page?current=1&size=1'],
  ['只读: 角色分页', 'GET', '/api/system/role/page?current=1&size=1'],
  ['只读: 菜单列表', 'GET', '/api/system/menu/list'],
  ['鉴权: 删不存在用户', 'DELETE', '/api/system/user/999999'],
  ['鉴权: 改不存在用户', 'PUT', '/api/system/user/999999', { realName: 'probe' }]
]

console.log('\n===== 接口探测 =====')
for (const [label, method, path, body] of probes) {
  const r = await api(method, path, { token, body })
  const verdict =
    r.httpStatus === 403 || r.bizCode === 403 ? '← 被拒(鉴权失败)'
      : r.ok ? '← 通过'
        : `← 鉴权已过，业务报错(${r.message || r.bizCode})`
  console.log(`  ${label.padEnd(22, ' ')} http=${r.httpStatus} code=${r.bizCode} ${verdict}`)
  if (r.message) console.log(`       message: ${r.message}`)
}

console.log('\n===== 判定口径 =====')
console.log('  · 若「删/改不存在用户」返回 403 → Controller 的 isAdmin() 判定失败（角色未被识别为管理员）')
console.log('  · 若返回"用户不存在"类业务错误 → 鉴权已通过，之前 403 属缓存/时序问题')
