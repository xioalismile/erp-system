// 生产入库单 API
import request from '@/utils/request'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/production/receipts'

// 生产入库单明细报表 API（列表页「明细模式」取数/导出 + 筛选条件元数据；基路径与后端控制器一致）
export const productionReceiptDetailApi = createBillDetailReportApi('/report/production/receipt-detail')

export function listProductionReceipt(params) {
  return request.get(`${BASE}/page`, { params })
}
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getProductionReceiptDashboard() {
  return request.get(`${BASE}/dashboard`)
}
export function listAllProductionReceipt() {
  return request.get(`${BASE}/list`)
}
export function getProductionReceipt(id) {
  return request.get(`${BASE}/${id}`)
}
export function saveProductionReceipt(data) {
  return request.post(BASE, data)
}
export function updateProductionReceipt(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
export function deleteProductionReceipt(id) {
  return request.delete(`${BASE}/${id}`)
}
export function approveProductionReceipt(id, approver) {
  return request.post(`${BASE}/${id}/approve`, null, { params: { approver } })
}
export function closeProductionReceipt(id, reason) {
  return request.post(`${BASE}/${id}/close`, null, { params: { reason } })
}
export function cancelProductionReceipt(id, reason) {
  return request.post(`${BASE}/${id}/cancel`, null, { params: { reason } })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectProductionReceipt(id, reason) { return request.post(`${BASE}/${id}/reject`, { reason }) }
export function reverseApproveProductionReceipt(id) { return request.post(`${BASE}/${id}/reverse-approve`) }
export function unvoidProductionReceipt(id) { return request.post(`${BASE}/${id}/unvoid`) }
export function reopenProductionReceipt(id) { return request.post(`${BASE}/${id}/reopen`) }
