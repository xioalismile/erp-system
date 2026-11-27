// 采购入库单 API
import request from '@/utils/request'

const BASE = '/purchase/inbounds'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getPurchaseInboundDashboard() { return request.get(`${BASE}/dashboard`) }

export function listPurchaseInbound(params) { return request.get(BASE, { params }) }
export function getPurchaseInbound(id) { return request.get(`${BASE}/${id}`) }
export function savePurchaseInbound(data) { return request.post(BASE, data) }
export function updatePurchaseInbound(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitPurchaseInbound(id) { return request.post(`${BASE}/${id}/submit`) }
export function approvePurchaseInbound(id) { return request.post(`${BASE}/${id}/approve`) }
export function reverseApprovePurchaseInbound(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function cancelPurchaseInbound(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPurchaseInbound(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function unvoidPurchaseInbound(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderPurchaseInbound(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenPurchaseInbound(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deletePurchaseInbound(id) { return request.delete(`${BASE}/${id}`) }
export function voidPurchaseInbound(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
