import request from '@/utils/request'
const BASE = '/finance/operation-expense'

export function pageExpense(params) { return request.get(`${BASE}/page`, { params }) }
/** 列表页顶部统计卡片（与列表逐字同数据权限口径） */
export function getOperationExpenseDashboard() { return request.get(`${BASE}/dashboard`) }
export function getExpense(id) { return request.get(`${BASE}/${id}`) }
export function saveExpense(data) { return request.post(BASE, data) }
export function updateExpense(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteExpense(id) { return request.delete(`${BASE}/${id}`) }
export function submitExpense(id) { return request.post(`${BASE}/${id}/submit`) }
export function approveExpense(id) { return request.post(`${BASE}/${id}/approve`) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectExpense(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveExpense(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidExpense(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidExpense(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderExpense(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenExpense(id) { return request.post(`${BASE}/${id}/reopen`) }
