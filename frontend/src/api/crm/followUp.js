// 客户跟进记录 API - 文档 7.4
import request from '@/utils/request'

const BASE = '/crm/follow-up'

export function listFollowUp(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getFollowUp(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveFollowUp(data) {
  return request.post(BASE, data)
}

export function updateFollowUp(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteFollowUp(id) {
  return request.delete(`${BASE}/${id}`)
}

export function listBySource(sourceType, sourceId) {
  return request.get(`${BASE}/by-source`, { params: { sourceType, sourceId } })
}

export function todayFollowUps() {
  return request.get(`${BASE}/today`)
}

export function followUpDashboard() {
  return request.get(`${BASE}/dashboard`)
}
