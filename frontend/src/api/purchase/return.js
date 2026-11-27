// 采购退货单 API
import request from '@/utils/request'

const BASE = '/purchase/returns'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getPurchaseReturnDashboard() { return request.get(`${BASE}/dashboard`) }

export function listPurchaseReturn(params) { return request.get(BASE, { params }) }
export function getPurchaseReturn(id) { return request.get(`${BASE}/${id}`) }
export function savePurchaseReturn(data) { return request.post(BASE, data) }
export function updatePurchaseReturn(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitPurchaseReturn(id) { return request.post(`${BASE}/${id}/submit`) }
export function approvePurchaseReturn(id) { return request.post(`${BASE}/${id}/approve`) }
export function reverseApprovePurchaseReturn(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function cancelPurchaseReturn(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPurchaseReturn(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function unvoidPurchaseReturn(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderPurchaseReturn(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenPurchaseReturn(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deletePurchaseReturn(id) { return request.delete(`${BASE}/${id}`) }
export function voidPurchaseReturn(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
