// 采购发票 API
import request from '@/utils/request'

const BASE = '/purchase/invoices'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getPurchaseInvoiceDashboard() { return request.get(`${BASE}/dashboard`) }

export function listPurchaseInvoice(params) { return request.get(BASE, { params }) }
export function getPurchaseInvoice(id) { return request.get(`${BASE}/${id}`) }
export function savePurchaseInvoice(data) { return request.post(BASE, data) }
export function updatePurchaseInvoice(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitPurchaseInvoice(id) { return request.post(`${BASE}/${id}/submit`) }
export function approvePurchaseInvoice(id) { return request.post(`${BASE}/${id}/approve`) }
export function reverseApprovePurchaseInvoice(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function cancelPurchaseInvoice(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPurchaseInvoice(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function unvoidPurchaseInvoice(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderPurchaseInvoice(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenPurchaseInvoice(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deletePurchaseInvoice(id) { return request.delete(`${BASE}/${id}`) }
export function voidPurchaseInvoice(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
