import request from '@/utils/request'
// 单据明细报表通用封装（列表页明细模式专属筛选条件元数据的来源，与明细报表页同一套接口）
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

/** 库存盘点单明细报表接口（/columns 元数据供列表页「筛选条件自定义」明细模式复用） */
export const stockCheckDetailApi = createBillDetailReportApi('/report/inventory/stock-check-detail')

export function listStockCheck(params) {
  return request({ url: '/inventory/stock-checks/page', method: 'get', params })
}
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getStockCheckDashboard() {
  return request({ url: '/inventory/stock-checks/dashboard', method: 'get' })
}
export function listAllStockCheck() {
  return request({ url: '/inventory/stock-checks/list', method: 'get' })
}
export function getStockCheck(id) {
  return request({ url: `/inventory/stock-checks/${id}`, method: 'get' })
}
export function saveStockCheck(data) {
  return request({ url: '/inventory/stock-checks', method: 'post', data })
}
export function updateStockCheck(id, data) {
  return request({ url: `/inventory/stock-checks/${id}`, method: 'put', data })
}
export function deleteStockCheck(id) {
  return request({ url: `/inventory/stock-checks/${id}`, method: 'delete' })
}
export function approveStockCheck(id, approver) {
  return request({ url: `/inventory/stock-checks/${id}/approve`, method: 'post', params: { approver } })
}
// 提交：草稿/已盘点 → 待审核（审批开关关闭时后端内部直接生效）
export function submitStockCheck(id) {
  return request({ url: `/inventory/stock-checks/${id}/submit`, method: 'post' })
}
export function closeStockCheck(id, reason) {
  return request({ url: `/inventory/stock-checks/${id}/close`, method: 'post', params: { reason } })
}
export function cancelStockCheck(id, reason) {
  return request({ url: `/inventory/stock-checks/${id}/cancel`, method: 'post', params: { reason } })
}
// ===== 状态流转统一接口（与其它入库/其它出库/调拨单同款，动作对齐后端 StockCheckController） =====
export function rejectStockCheck(id, reason) { return request.post(`/inventory/stock-checks/${id}/reject`, { reason }) }
export function reverseApproveStockCheck(id) { return request.post(`/inventory/stock-checks/${id}/reverse-approve`) }
export function reopenStockCheck(id) { return request.post(`/inventory/stock-checks/${id}/reopen`) }
export function voidStockCheck(id, reason) { return request.post(`/inventory/stock-checks/${id}/void`, { reason }) }
export function unvoidStockCheck(id) { return request.post(`/inventory/stock-checks/${id}/unvoid`) }
