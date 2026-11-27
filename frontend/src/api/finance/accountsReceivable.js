// 应收账款 API
import request from '@/utils/request'

const BASE = '/finance'

export function pageAr(params) {
  return request.get(`${BASE}/ar`, { params })
}

export function getArDetail(id) {
  return request.get(`${BASE}/ar/${id}`)
}

export function receivePayment(id, data) {
  return request.post(`${BASE}/ar/${id}/receive`, data)
}

export function saveAr(data) {
  return request.post(`${BASE}/ar`, data)
}

export function updateAr(id, data) {
  return request.put(`${BASE}/ar/${id}`, data)
}

export function deleteAr(id) {
  return request.delete(`${BASE}/ar/${id}`)
}
