// 客户投诉管理 API - 文档 7.5
import request from '@/utils/request'

const BASE = '/crm/complaint'

export function listComplaint(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getComplaint(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveComplaint(data) {
  return request.post(BASE, data)
}

export function updateComplaint(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteComplaint(id) {
  return request.delete(`${BASE}/${id}`)
}

export function assignHandler(id, handlerId) {
  return request.put(`${BASE}/${id}/assign`, { handlerId })
}

export function resolveComplaint(id, handleResult) {
  return request.put(`${BASE}/${id}/resolve`, { handleResult })
}

export function closeComplaint(id, satisfactionScore) {
  return request.put(`${BASE}/${id}/close`, { satisfactionScore })
}

export function complaintDashboard() {
  return request.get(`${BASE}/dashboard`)
}
