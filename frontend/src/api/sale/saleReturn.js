// 销售退货单 API
import request from '@/utils/request'

const BASE = '/sale/returns'

// 列表页顶部统计卡片数据（与列表同数据权限口径：按 saler_id 过滤）
export function getSaleReturnDashboard() { return request.get(`${BASE}/dashboard`) }

export function listSaleReturn(params) { return request.get(BASE, { params }) }
export function getSaleReturn(id) { return request.get(`${BASE}/${id}`) }
export function saveSaleReturn(data) { return request.post(BASE, data) }
export function updateSaleReturn(id, data) { return request.put(`${BASE}/${id}`, data) }
export function approveSaleReturn(id) { return request.post(`${BASE}/${id}/approve`) }
export function submitSaleReturn(id) { return request.post(`${BASE}/${id}/submit`) }
export function cancelSaleReturn(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSaleReturn(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSaleReturn(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidSaleReturn(id) { return request.post(`${BASE}/${id}/void`, {}) }
export function unvoidSaleReturn(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSaleReturn(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSaleReturn(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deleteSaleReturn(id) { return request.delete(`${BASE}/${id}`) }
