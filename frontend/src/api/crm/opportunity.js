// 商机管理 API - 文档 7.3
import request from '@/utils/request'

const BASE = '/crm/opportunity'

export function listOpportunity(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getOpportunity(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveOpportunity(data) {
  return request.post(BASE, data)
}

export function updateOpportunity(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteOpportunity(id) {
  return request.delete(`${BASE}/${id}`)
}

export function updateOppStage(id, data) {
  return request.put(`${BASE}/${id}/stage`, data)
}

export function closeOpportunity(id, data) {
  return request.put(`${BASE}/${id}/close`, data)
}

export function opportunityDashboard() {
  return request.get(`${BASE}/dashboard`)
}
