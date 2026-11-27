// 销售线索管理 API - 文档 7.2
import request from '@/utils/request'

const BASE = '/crm/lead'

export function listLead(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getLead(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveLead(data) {
  return request.post(BASE, data)
}

export function updateLead(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteLead(id) {
  return request.delete(`${BASE}/${id}`)
}

export function updateLeadStatus(id, status) {
  return request.put(`${BASE}/${id}/status`, { status })
}

export function assignLead(id, assignedSalesId) {
  return request.put(`${BASE}/${id}/assign`, { assignedSalesId })
}

export function abandonLead(id, reason) {
  return request.put(`${BASE}/${id}/abandon`, { reason })
}

export function convertLead(id) {
  return request.post(`${BASE}/${id}/convert`)
}

export function leadDashboard() {
  return request.get(`${BASE}/dashboard`)
}
