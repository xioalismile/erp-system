// 生产入库单 API
import request from '@/utils/request'

const BASE = '/production/receipts'

export function listProductionReceipt(params) {
  return request.get(`${BASE}/page`, { params })
}
export function listAllProductionReceipt() {
  return request.get(`${BASE}/list`)
}
export function getProductionReceipt(id) {
  return request.get(`${BASE}/${id}`)
}
export function saveProductionReceipt(data) {
  return request.post(BASE, data)
}
export function updateProductionReceipt(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function deleteProductionReceipt(id) {
  return request.delete(`${BASE}/${id}`)
}
export function approveProductionReceipt(id, approver) {
  return request.post(`${BASE}/${id}/approve`, null, { params: { approver } })
}
export function closeProductionReceipt(id, reason) {
  return request.post(`${BASE}/${id}/close`, null, { params: { reason } })
}
export function cancelProductionReceipt(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, null, { params: { reason } })
}
