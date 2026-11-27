// 销售预警（2026-10-07）
// 后端：com.erp.crm.controller.SalesWarningController @RequestMapping("/api/crm/sales-warning")
import request from '@/utils/request'

const BASE = '/crm/sales-warning'

export function listSalesWarning(params) {
  return request.get(`${BASE}/page`, { params })
}

export function salesWarningSummary() {
  return request.get(`${BASE}/summary`)
}

/** 手动触发结算（按日生成预警；不传日期 = 今天） */
export function generateSalesWarning(date) {
  return request.post(`${BASE}/generate`, null, { params: date ? { date } : {} })
}

export function escalateSalesWarning() {
  return request.post(`${BASE}/escalate`)
}

export function handleSalesWarning(id, data) {
  return request.post(`${BASE}/${id}/handle`, data)
}

/** 认领：待处理 → 处理中（闭环状态机第二态） */
export function claimSalesWarning(id) {
  return request.post(`${BASE}/${id}/claim`)
}

export function closeSalesWarning(id, data) {
  return request.post(`${BASE}/${id}/close`, data)
}
