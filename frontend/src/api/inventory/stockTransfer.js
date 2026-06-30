// 库存调拨单 API
import request from '@/utils/request'

const BASE = '/inventory/stock-transfers'

export function listStockTransfer(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllStockTransfer() { return request.get(`${BASE}/list`) }
export function getStockTransfer(id) { return request.get(`${BASE}/${id}`) }
export function saveStockTransfer(data) { return request.post(BASE, data) }
export function updateStockTransfer(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteStockTransfer(id) { return request.delete(`${BASE}/${id}`) }
export function approveStockTransfer(id, approver) {
  return request.post(`${BASE}/${id}/approve`, null, { params: { approver } })
}
export function closeStockTransfer(id, reason) {
  return request.post(`${BASE}/${id}/close`, null, { params: { reason } })
}
export function cancelStockTransfer(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, null, { params: { reason } })
}
