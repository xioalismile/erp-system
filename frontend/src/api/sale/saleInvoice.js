// 销售发票 API
import request from '@/utils/request'

const BASE = '/sale/invoices'

// 列表页顶部统计卡片数据（与列表同数据权限口径：按 saler_id 过滤）
export function getSaleInvoiceDashboard() { return request.get(`${BASE}/dashboard`) }

export function listSaleInvoice(params) { return request.get(BASE, { params }) }
export function getSaleInvoice(id) { return request.get(`${BASE}/${id}`) }
export function saveSaleInvoice(data) { return request.post(BASE, data) }
export function updateSaleInvoice(id, data) { return request.put(`${BASE}/${id}`, data) }
export function approveSaleInvoice(id) { return request.post(`${BASE}/${id}/approve`) }
export function submitSaleInvoice(id) { return request.post(`${BASE}/${id}/submit`) }
export function cancelSaleInvoice(id, reason) { return request.post(`${BASE}/${id}/cancel`, { reason }) }

/**
 * 批量查询源单明细是否被互斥锁定（销售发票选单时调用）。
 * @param {string} sourceType "销售订单" | "销售出库单"
 * @param {number[]} sourceItemIds 源单明细ID列表
 * @returns {Promise<number[]>} 被锁定的明细ID列表
 */
export function checkInvoiceMutex(sourceType, sourceItemIds) {
  return request.post(`${BASE}/mutex-check`, { sourceType, sourceItemIds })
    .then(res => (res && Array.isArray(res.lockedItemIds)) ? res.lockedItemIds : [])
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectSaleInvoice(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveSaleInvoice(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidSaleInvoice(id) { return request.post(`${BASE}/${id}/void`, {}) }
export function unvoidSaleInvoice(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function closeOrderSaleInvoice(id) { return request.post(`${BASE}/${id}/close-order`) }
export function reopenSaleInvoice(id) { return request.post(`${BASE}/${id}/reopen`) }
export function deleteSaleInvoice(id) { return request.delete(`${BASE}/${id}`) }
