// 销售目标 · 五级分解（年/季/月/周/日）（2026-10-07）
// 后端：com.erp.crm.controller.SalesTargetBreakdownController
import request from '@/utils/request'

const BASE = '/crm/sales-target-breakdown'

export function getBreakdown(params) {
  return request.get(`${BASE}/breakdown`, { params })
}

/** 手工覆盖某月目标（周/日随之重算） */
export function saveMonthTarget(data) {
  return request.post(`${BASE}/month`, data)
}

/** 取消某月手工覆盖（回到 季 ÷ 3） */
export function clearMonthTarget(params) {
  return request.delete(`${BASE}/month`, { params })
}
