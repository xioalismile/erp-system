// ====================================================================
// 平台管理-客户（租户）管理 API（仅自营环境；客户包通过编译期排除）
// ====================================================================
import request from '@/utils/request'

/** 分页查询租户列表 */
export function listTenants(params) {
  return request.get('/admin/tenant/list', { params })
}

/** 创建租户（自动建库+初始化+创建管理员） */
export function createTenant(data) {
  return request.post('/admin/tenant/create', data)
}

/** 更新租户信息 */
export function updateTenant(id, data) {
  return request.put(`/admin/tenant/${id}`, data)
}

/** 续费（延长到期日期） */
export function renewTenant(id, months) {
  return request.post(`/admin/tenant/${id}/renew`, { months })
}

/** 禁用租户（登录即失效，不删数据） */
export function disableTenant(id) {
  return request.delete(`/admin/tenant/${id}`)
}

/** 启用租户 */
export function enableTenant(id) {
  return request.post(`/admin/tenant/${id}/enable`)
}

/** 删除租户（物理删除：独立库整库删除+平台库登记清理，不可恢复） */
export function deleteTenant(id) {
  return request.post(`/admin/tenant/${id}/delete`)
}

/** 重置租户管理员密码（兜底：无手机号验证时使用） */
export function resetTenantPassword(id, newPassword) {
  return request.post(`/admin/tenant/${id}/reset-password`, { newPassword })
}

/** 读取平台分配给客户的 AI 月总额度（quota=0 不限）+ 该客户本月已消耗 */
export function getTenantAiQuota(id) {
  return request.get(`/admin/tenant/${id}/ai-quota`)
}

/** 平台分配/调整客户的 AI 月总额度（quota 传 0 表示不限） */
export function saveTenantAiQuota(id, quota) {
  return request.post(`/admin/tenant/${id}/ai-quota`, { quota })
}

// ============================================================
// 小楷助手积分制（2026-10-24）
// ============================================================

/** 平台「AI 默认规则」：积分规则 + 平台统一模型密钥 */
export function getAiDefaults() {
  return request.get('/admin/tenant/ai-defaults')
}

/** 保存平台「AI 默认规则」 */
export function saveAiDefaults(data) {
  return request.post('/admin/tenant/ai-defaults', data)
}

/** 读取某客户的公司总积分/本月消耗/累计消耗/已分配员工 */
export function getTenantCredit(id) {
  return request.get(`/admin/tenant/${id}/ai-credit`)
}

/** 平台给客户增加/扣减公司总积分（delta 正数增加、负数扣减） */
export function adjustTenantCredit(id, delta, remark) {
  return request.post(`/admin/tenant/${id}/ai-credit`, { delta, remark })
}
