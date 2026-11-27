// 销售目标管理 API
import request from '@/utils/request'

const BASE = '/crm/sales-target'

// ==================== 公司年度目标 ====================

export function pageCompany(params) {
  return request.get(`${BASE}/company/page`, { params })
}

export function getCompany(id) {
  return request.get(`${BASE}/company/${id}`)
}

export function createCompany(data) {
  return request.post(`${BASE}/company`, data)
}

export function updateCompany(id, data) {
  return request.put(`${BASE}/company/${id}`, data)
}

export function deleteCompany(id) {
  return request.delete(`${BASE}/company/${id}`)
}

/** 自动生成下年度目标（按增长率递增） */
export function generateNextYear(id) {
  return request.post(`${BASE}/company/${id}/generate-next-year`)
}

// ==================== 销售人员目标分配 ====================

export function pageEmployee(params) {
  return request.get(`${BASE}/employee/page`, { params })
}

export function allocateEmployee(data) {
  return request.post(`${BASE}/employee`, data)
}

export function batchAllocate(data) {
  return request.post(`${BASE}/employee/batch`, data)
}

export function updateEmployee(id, data) {
  return request.put(`${BASE}/employee/${id}`, data)
}

export function deleteEmployee(id) {
  return request.delete(`${BASE}/employee/${id}`)
}

/** 刷新实际完成指标；quarter 为空=年度口径（写回快照），1-4=季度口径（仅返回） */
export function refreshActual(year, employeeId, quarter) {
  const params = { year, employeeId }
  if (quarter != null && quarter !== '') params.quarter = quarter
  return request.post(`${BASE}/employee/refresh`, null, { params })
}

// ==================== 看板 ====================

export function getDashboard(year) {
  return request.get(`${BASE}/dashboard`, { params: { year } })
}

export function getRanking(year) {
  return request.get(`${BASE}/ranking`, { params: { year } })
}
