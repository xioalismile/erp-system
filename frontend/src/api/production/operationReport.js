// 工序汇报单 API (baseURL 已含 /api 前缀)
import request from '@/utils/request'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/production/operation-report'

// 工序汇报单明细报表 API（列表页「明细模式」取数/导出 + 筛选条件元数据；基路径与后端控制器一致）
export const operationReportDetailApi = createBillDetailReportApi('/report/production/operation-report-detail')

export function listOperationReport(params) {
  return request({ url: BASE, method: 'get', params })
}
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getOperationReportDashboard() {
  return request({ url: `${BASE}/dashboard`, method: 'get' })
}
export function getOperationReport(id) {
  return request({ url: `${BASE}/${id}`, method: 'get' })
}
export function saveOperationReport(data) {
  return request({ url: BASE, method: 'post', data })
}
export function updateOperationReport(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'put', data })
}
export function deleteOperationReport(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}
export function submitOperationReport(id) {
  return request({ url: `${BASE}/${id}/submit`, method: 'post' })
}
export function approveOperationReport(id) {
  return request({ url: `${BASE}/${id}/approve`, method: 'post' })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectOperationReport(id, reason) {
  return request({ url: `${BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function reverseApproveOperationReport(id) {
  return request({ url: `${BASE}/${id}/reverse-approve`, method: 'post' })
}
export function voidOperationReport(id, reason) {
  return request({ url: `${BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidOperationReport(id) {
  return request({ url: `${BASE}/${id}/unvoid`, method: 'post' })
}
export function closeOrderOperationReport(id) {
  return request({ url: `${BASE}/${id}/close-order`, method: 'post' })
}
export function reopenOperationReport(id) {
  return request({ url: `${BASE}/${id}/reopen`, method: 'post' })
}
