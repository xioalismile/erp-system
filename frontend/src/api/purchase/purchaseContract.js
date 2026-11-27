// 采购合同 API
import request from '@/utils/request'

const BASE = '/purchase/contract'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getPurchaseContractDashboard() { return request.get(`${BASE}/dashboard`) }

export function listPurchaseContract(params) { return request.get(`${BASE}/page`, { params }) }
export function getPurchaseContract(id) { return request.get(`${BASE}/${id}`) }
export function savePurchaseContract(data) { return request.post(BASE, data) }
export function updatePurchaseContract(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitPurchaseContract(id) { return request.post(`${BASE}/${id}/submit`) }
export function approvePurchaseContract(id) { return request.post(`${BASE}/${id}/approve`) }
export function reverseApprovePurchaseContract(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function cancelPurchaseContract(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPurchaseContract(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function unvoidPurchaseContract(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderPurchaseContract(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenPurchaseContract(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deletePurchaseContract(id) { return request.delete(`${BASE}/${id}`) }
export function voidPurchaseContract(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
