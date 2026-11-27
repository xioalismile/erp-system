// 销售报价单 API
import request from '@/utils/request'

const BASE = '/sale/quotes'

// 列表页顶部统计卡片数据（与列表同数据权限口径：按 saler_id 过滤）
export function getSaleQuoteDashboard() { return request.get(`${BASE}/dashboard`) }

export function listSaleQuote(params) { return request.get(BASE, { params }) }
export function getSaleQuote(id) { return request.get(`${BASE}/${id}`) }
export function saveSaleQuote(data) { return request.post(BASE, data) }
export function updateSaleQuote(id, data) { return request.put(`${BASE}/${id}`, data) }
export function approveSaleQuote(id) { return request.post(`${BASE}/${id}/approve`) }
export function submitSaleQuote(id) { return request.post(`${BASE}/${id}/submit`) }
export function cancelSaleQuote(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSaleQuote(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSaleQuote(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidSaleQuote(id) { return request.post(`${BASE}/${id}/void`) }
export function unvoidSaleQuote(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSaleQuote(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSaleQuote(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deleteSaleQuote(id) { return request.delete(`${BASE}/${id}`) }
