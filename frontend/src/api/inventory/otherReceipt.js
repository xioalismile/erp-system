// 其他入库单 API
import request from '@/utils/request'

const BASE = '/inventory/other-receipts'

export function listOtherReceipt(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllOtherReceipt() { return request.get(`${BASE}/list`) }
export function getOtherReceipt(id) { return request.get(`${BASE}/${id}`) }
export function saveOtherReceipt(data) { return request.post(BASE, data) }
export function updateOtherReceipt(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteOtherReceipt(id) { return request.delete(`${BASE}/${id}`) }
export function approveOtherReceipt(id, approver) {
  return request.post(`${BASE}/${id}/approve`, null, { params: { approver } })
}
export function closeOtherReceipt(id, reason) {
  return request.post(`${BASE}/${id}/close`, null, { params: { reason } })
}
export function cancelOtherReceipt(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, null, { params: { reason } })
}
