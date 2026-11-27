// 库存调拨单 API
import request from '@/utils/request'
// 单据明细报表通用封装（列表页明细模式专属筛选条件元数据的来源，与明细报表页同一套接口）
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/inventory/stock-transfers'

/** 库存调拨单明细报表接口（/columns 元数据供列表页「筛选条件自定义」明细模式复用） */
export const stockTransferDetailApi = createBillDetailReportApi('/report/inventory/stock-transfer-detail')

export function listStockTransfer(params) { return request.get(`${BASE}/page`, { params }) }
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getStockTransferDashboard() { return request.get(`${BASE}/dashboard`) }
export function listAllStockTransfer() { return request.get(`${BASE}/list`) }
export function getStockTransfer(id) { return request.get(`${BASE}/${id}`) }
export function saveStockTransfer(data) { return request.post(BASE, data) }
export function updateStockTransfer(id, data) { return request.put(`${BASE}/${id}`, data) }
export function deleteStockTransfer(id) { return request.delete(`${BASE}/${id}`) }
export function approveStockTransfer(id, approver) {
  return request.post(`${BASE}/${id}/approve`, null, { params: { approver } })
}
export function submitStockTransfer(id) {
  return request.post(`${BASE}/${id}/submit`)
}
export function reverseApproveStockTransfer(id, approver) {
  return request.post(`${BASE}/${id}/reverse-approve`, null, { params: { approver } })
}
export function closeStockTransfer(id, reason) {
  return request.post(`${BASE}/${id}/close`, null, { params: { reason } })
}
export function cancelStockTransfer(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, null, { params: { reason } })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectStockTransfer(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function unvoidStockTransfer(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function reopenStockTransfer(id) { return request.post(`${BASE}/${id}/reopen`) }
export function voidStockTransfer(id, reason) { return request.post(`${BASE}/${id}/void`, { reason }) }
