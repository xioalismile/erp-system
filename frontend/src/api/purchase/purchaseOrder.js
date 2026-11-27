// 采购订单 API
import request from '@/utils/request'

const BASE = '/purchase/orders'

// 列表页顶部统计卡片数据（与列表同数据权限口径）
// 注意：dashboard 挂在控制器的 /api/purchase 下（不是 /purchase/orders 下），故这里写绝对路径
export function getPurchaseOrderDashboard() { return request.get('/purchase/dashboard') }

export function listPurchaseOrder(params) { return request.get(BASE, { params }) }
export function getPurchaseOrder(id) { return request.get(`${BASE}/${id}`) }
export function savePurchaseOrder(data) { return request.post(BASE, data) }
export function updatePurchaseOrder(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitPurchaseOrder(id) { return request.post(`${BASE}/${id}/submit`) }
export function approvePurchaseOrder(id) { return request.post(`${BASE}/${id}/approve`) }
export function rejectPurchaseOrder(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApprovePurchaseOrder(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function closePurchaseOrder(id, reason) { return request.post(`${BASE}/${id}/close`, { reason }) }
export function cancelPurchaseOrder(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }
export function closeOrderPurchaseOrder(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenPurchaseOrder(id) { return request.post(`${BASE}/${id}/reopen`) }
export function voidPurchaseOrder(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidPurchaseOrder(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function deletePurchaseOrder(id) { return request.delete(`${BASE}/${id}`) }
