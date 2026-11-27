// 其他入库单 API
import request from '@/utils/request'
// 单据明细报表通用封装（列表页明细模式专属筛选条件元数据的来源，与明细报表页同一套接口）
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/inventory/other-receipts'

/** 其他入库单明细报表接口（/columns 元数据供列表页「筛选条件自定义」明细模式复用） */
export const otherReceiptDetailApi = createBillDetailReportApi('/report/inventory/other-receipt-detail')

export function listOtherReceipt(params) { return request.get(`${BASE}/page`, { params }) }
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getOtherReceiptDashboard() { return request.get(`${BASE}/dashboard`) }
export function listAllOtherReceipt() { return request.get(`${BASE}/list`) }
export function getOtherReceipt(id) { return request.get(`${BASE}/${id}`) }
export function saveOtherReceipt(data) { return request.post(BASE, data) }
export function updateOtherReceipt(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteOtherReceipt(id) { return request.delete(`${BASE}/${id}`) }
export function submitOtherReceipt(id) {
  return request.post(`${BASE}/${id}/submit`)
}
export function approveOtherReceipt(id, approver) {
  return request.post(`${BASE}/${id}/approve`, null, { params: { approver } })
}
export function closeOtherReceipt(id, reason) {
  return request.post(`${BASE}/${id}/close`, null, { params: { reason } })
}
export function cancelOtherReceipt(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, null, { params: { reason } })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectOtherReceipt(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveOtherReceipt(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function voidOtherReceipt(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
export function unvoidOtherReceipt(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function reopenOtherReceipt(id) { return request.post(`${BASE}/${id}/reopen`) }
