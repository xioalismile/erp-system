// 销售出库单 API
import request from '@/utils/request'

const BASE = '/sale/deliveries'

// 列表页顶部统计卡片数据（与列表同数据权限口径：按 saler_id 过滤）
export function getSaleDeliveryDashboard() { return request.get(`${BASE}/dashboard`) }

export function listSaleDelivery(params) { return request.get(BASE, { params }) }
export function getSaleDelivery(id) { return request.get(`${BASE}/${id}`) }
export function checkSaleDeliveryOverQty(id) { return request.get(`${BASE}/${id}/over-qty-check`) }
export function saveSaleDelivery(data) { return request.post(BASE, data) }
export function updateSaleDelivery(id, data) { return request.put(`${BASE}/${id}`, data) }
export function approveSaleDelivery(id, config = {}) { return request.post(`${BASE}/${id}/approve`, null, config) }
export function submitSaleDelivery(id, config = {}) { return request.post(`${BASE}/${id}/submit`, null, config) }
export function cancelSaleDelivery(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSaleDelivery(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSaleDelivery(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidSaleDelivery(id) { return request.post(`${BASE}/${id}/void`, {}) }
export function unvoidSaleDelivery(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSaleDelivery(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSaleDelivery(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deleteSaleDelivery(id) { return request.delete(`${BASE}/${id}`) }
