// 销售合同 API
import request from '@/utils/request'

const BASE = '/sale/contracts'

// 列表页顶部统计卡片数据（与列表同数据权限口径：按 saler_id 过滤）
export function getSaleContractDashboard() { return request.get(`${BASE}/dashboard`) }

export function listSaleContract(params) { return request.get(BASE, { params }) }
export function getSaleContract(id) { return request.get(`${BASE}/${id}`) }
export function saveSaleContract(data) { return request.post(BASE, data) }
export function updateSaleContract(id, data) { return request.put(`${BASE}/${id}`, data) }
export function submitSaleContract(id) { return request.post(`${BASE}/${id}/submit`) }
export function approveSaleContract(id) { return request.post(`${BASE}/${id}/approve`) }
// 注意：后端无 close 接口，业务上"关闭"等同于"取消"，统一走 cancel 接口
export function closeSaleContract(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }
export function cancelSaleContract(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSaleContract(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSaleContract(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidSaleContract(id) { return request.post(`${BASE}/${id}/void`, {}) }
export function unvoidSaleContract(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSaleContract(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSaleContract(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deleteSaleContract(id) { return request.delete(`${BASE}/${id}`) }
