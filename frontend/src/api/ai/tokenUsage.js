import request from '@/utils/request'

const BASE = '/ai/token-usage'

/**
 * AI Token 用量管理 API 封装（守则2.8.2-4：禁止在 .vue 内硬写 URL）
 * 后端对应：com.erp.ai.controller.AiTokenUsageController
 *   GET  /api/ai/token-usage/stats        → 月度用量统计
 *   GET  /api/ai/token-usage/quota        → 全局默认配额
 *   POST /api/ai/token-usage/quota        → 设置全局默认配额 { quota }
 *   POST /api/ai/token-usage/user-quota   → 设置/清除用户专属配额 { userId, quota? }
 */

// 月度用量统计（month 缺省=当月，格式 YYYY-MM）
export function getTokenUsageStats(month) {
  return request.get(`${BASE}/stats`, { params: { month } })
}

// 获取全局默认配额
export function getDefaultQuota() {
  return request.get(`${BASE}/quota`)
}

// 设置全局默认配额，quota=0 表示不限制
export function setDefaultQuota(quota) {
  return request.post(`${BASE}/quota`, { quota: quota || 0 })
}

// 设置用户专属配额；不传 quota = 清除专属配置，恢复继承租户额度
export function setUserQuota(userId, quota) {
  const body = { userId }
  if (quota !== undefined && quota !== null) {
    body.quota = quota
  }
  return request.post(`${BASE}/user-quota`, body)
}

// 获取租户额度汇总（总额度、已分配、剩余、是否有自有Key）
export function getTenantQuota() {
  return request.get(`${BASE}/tenant-quota`)
}

// 设置租户月度总额度（0=不限）
export function setTenantQuota(quota) {
  return request.post(`${BASE}/tenant-quota`, { quota: quota || 0 })
}

/**
 * 获取租户消耗系数（默认 0.5 = 消耗按 1.5 倍计量）
 * 返回 { ratio, defaultRatio, maxRatio }
 */
export function getUsageRatio() {
  return request.get(`${BASE}/usage-ratio`)
}

/**
 * 设置租户消耗系数（0 ~ maxRatio，0=不加价）
 * 口径：记账token = 实际token × (1+系数)，仅影响消耗与配额扣减，不影响额度分配
 */
export function setUsageRatio(ratio) {
  return request.post(`${BASE}/usage-ratio`, { ratio })
}

// ============================================================
// 积分制（2026-10-24）
// ============================================================

/** 设置中心积分页：全局积分规则 + 公司总积分汇总 */
export function getCreditConfig() {
  return request.get(`${BASE}/credit-config`)
}

/** 管理员：从公司总积分划拨给员工 */
export function allocateUserCredit(userId, username, amount, remark) {
  return request.post(`${BASE}/user-credit`, { userId, username, amount, remark })
}

/** 管理员：收回员工个人积分，恢复共用公司总积分 */
export function reclaimUserCredit(userId, username, remark) {
  return request.post(`${BASE}/user-credit/reclaim`, { userId, username, remark })
}
