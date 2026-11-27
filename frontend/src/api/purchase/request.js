// 采购申请单 API
import request from '@/utils/request'

const BASE = '/purchase/requests'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getPurchaseRequestDashboard() { return request.get(`${BASE}/dashboard`) }

export function listPurchaseRequest(params) { return request.get(BASE, { params }) }
export function getPurchaseRequest(id) { return request.get(`${BASE}/${id}`) }
export function savePurchaseRequest(data) { return request.post(BASE, data) }
export function updatePurchaseRequest(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitPurchaseRequest(id) { return request.post(`${BASE}/${id}/submit`) }
export function approvePurchaseRequest(id) { return request.post(`${BASE}/${id}/approve`) }
export function reverseApprovePurchaseRequest(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function closePurchaseRequest(id) { return request.post(`${BASE}/${id}/close`) }
export function cancelPurchaseRequest(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPurchaseRequest(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function unvoidPurchaseRequest(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function reopenPurchaseRequest(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deletePurchaseRequest(id) { return request.delete(`${BASE}/${id}`) }
export function voidPurchaseRequest(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function closeOrderPurchaseRequest(id) { return request.post(`${BASE}/${id}/close-order`) }
