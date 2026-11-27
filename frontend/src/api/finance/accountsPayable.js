// 应付账款 API
import request from '@/utils/request'

const BASE = '/finance'

export function pageAp(params) {
  return request.get(`${BASE}/ap`, { params })
}

export function getApDetail(id) {
  return request.get(`${BASE}/ap/${id}`)
}

export function makePayment(id, data) {
  return request.post(`${BASE}/ap/${id}/pay`, data)
}

export function saveAp(data) {
  return request.post(`${BASE}/ap`, data)
}

export function updateAp(id, data) {
  return request.put(`${BASE}/ap/${id}`, data)
}

export function deleteAp(id) {
  return request.delete(`${BASE}/ap/${id}`)
}
