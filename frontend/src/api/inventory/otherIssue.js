import request from '@/utils/request'
// 单据明细报表通用封装（列表页明细模式专属筛选条件元数据的来源，与明细报表页同一套接口）
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/inventory/other-issues'

/** 其他出库单明细报表接口（/columns 元数据供列表页「筛选条件自定义」明细模式复用） */
export const otherIssueDetailApi = createBillDetailReportApi('/report/inventory/other-issue-detail')

export function listOtherIssue(params) {
  return request({ url: `${BASE}/page`, method: 'get', params })
}
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getOtherIssueDashboard() {
  return request({ url: `${BASE}/dashboard`, method: 'get' })
}
export function listAllOtherIssue() {
  return request({ url: `${BASE}/list`, method: 'get' })
}
export function getOtherIssue(id) {
  return request({ url: `${BASE}/${id}`, method: 'get' })
}
export function saveOtherIssue(data) {
  return request({ url: BASE, method: 'post', data })
}
export function updateOtherIssue(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'put', data })
}
export function deleteOtherIssue(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}
export function submitOtherIssue(id) {
  return request({ url: `${BASE}/${id}/submit`, method: 'post' })
}
export function approveOtherIssue(id, approver) {
  return request({ url: `${BASE}/${id}/approve`, method: 'post', params: { approver } })
}
export function reverseApproveOtherIssue(id, approver) {
  return request({ url: `${BASE}/${id}/reverse-approve`, method: 'post', params: { approver } })
}
export function closeOtherIssue(id, reason) {
  return request({ url: `${BASE}/${id}/close`, method: 'post', params: { reason } })
}
export function cancelOtherIssue(id, reason) {
  return request({ url: `${BASE}/${id}/cancel`, method: 'post', params: { reason } })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectOtherIssue(id, reason) {
  return request({ url: `${BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function unvoidOtherIssue(id) {
  return request({ url: `${BASE}/${id}/unvoid`, method: 'post' })
}
export function reopenOtherIssue(id) {
  return request({ url: `${BASE}/${id}/reopen`, method: 'post' })
}
export function voidOtherIssue(id, reason) {
  return request({ url: `${BASE}/${id}/void`, method: 'post', data: { reason } })
}
