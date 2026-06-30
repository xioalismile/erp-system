import request from '@/utils/request'

export function listStockCheck(params) {
  return request({ url: '/inventory/stock-checks/page', method: 'get', params })
}
export function listAllStockCheck() {
  return request({ url: '/inventory/stock-checks/list', method: 'get' })
}
export function getStockCheck(id) {
  return request({ url: `/inventory/stock-checks/${id}`, method: 'get' })
}
export function saveStockCheck(data) {
  return request({ url: '/inventory/stock-checks', method: 'post', data })
}
export function updateStockCheck(id, data) {
  return request({ url: `/inventory/stock-checks/${id}`, method: 'put', data })
}
export function deleteStockCheck(id) {
  return request({ url: `/inventory/stock-checks/${id}`, method: 'delete' })
}
export function approveStockCheck(id, approver) {
  return request({ url: `/inventory/stock-checks/${id}/approve`, method: 'post', params: { approver } })
}
export function closeStockCheck(id, reason) {
  return request({ url: `/inventory/stock-checks/${id}/close`, method: 'post', params: { reason } })
}
export function cancelStockCheck(id, reason) {
  return request({ url: `/inventory/stock-checks/${id}/cancel`, method: 'post', params: { reason } })
}
