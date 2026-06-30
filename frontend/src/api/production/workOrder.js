// 生产工单 API (baseURL 已含 /api 前缀)
import request from '@/utils/request'

const BASE = '/production/workorders'

export function pageWorkOrder(params) {
  return request.get(`${BASE}`, { params })
}
export function getWorkOrder(id) {
  return request.get(`${BASE}/${id}`)
}
export function createWorkOrder(data) {
  return request.post(BASE, data)
}
export function updateWorkOrder(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function releaseWorkOrder(id, operator) {
  return request.post(`${BASE}/${id}/release`, null, { headers: { 'X-Operator': operator || '' } })
}
export function completeWorkOrder(id, operator) {
  return request.post(`${BASE}/${id}/complete`, null, { headers: { 'X-Operator': operator || '' } })
}
export function closeWorkOrder(id, reason, operator) {
  return request.post(`${BASE}/${id}/close`, { reason }, { headers: { 'X-Operator': operator || '' } })
}
export function generateOrderNo() {
  return request.get(`${BASE}/generate-no`)
}
