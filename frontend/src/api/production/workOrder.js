// 生产工单 API (baseURL 已含 /api 前缀)
import request from '@/utils/request'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/production/workorders'

// 生产工单明细报表 API（列表页「明细模式」取数/导出 + 筛选条件元数据；基路径与后端控制器一致）
export const productionOrderDetailApi = createBillDetailReportApi('/report/production/order-detail')

export function pageWorkOrder(params) {
  return request.get(`${BASE}`, { params })
}
// 顶部统计卡片数据（/api/production/dashboard：与列表同数据权限口径）
export function getWorkOrderDashboard() {
  return request.get('/production/dashboard')
}
export function getWorkOrder(id) {
  return request.get(`${BASE}/${id}`)
}
export function createWorkOrder(data) {
  return request.post(BASE, data)
}
export function updateWorkOrder(id, data) {
  return request.put(`${BASE}/${id}`, data)
}
// 提交：草稿 → 待审核（单据审批开关关闭时提交即生效）
export function submitWorkOrder(id, operator) {
  return request.post(`${BASE}/${id}/submit`, null, { headers: { 'X-Operator': operator || '' } })
}
// 审核：待审核 → 已生效
export function approveWorkOrder(id, operator) {
  return request.post(`${BASE}/${id}/approve`, null, { headers: { 'X-Operator': operator || '' } })
}
export function closeWorkOrder(id, reason, operator) {
  return request.post(`${BASE}/${id}/close`, { reason }, { headers: { 'X-Operator': operator || '' } })
}
export function generateOrderNo() {
  return request.get(`${BASE}/generate-no`)
}
export function getWorkOrderProgress(id) {
  return request.get(`${BASE}/${id}/progress`)
}

/**
 * 【只保存工单工序清单】（2026-09-27 第7期）：不改动单据其它字段、不校验整单状态。
 * 用于「工序明细」在草稿期落库，以及"已生效但生效时一道工序都没配"时补选。
 * @param {number} id 工单ID
 * @param {Array} operations 工序清单（数组顺序即工序号顺序；只传"选用"的行）
 */
export function saveWorkOrderOperations(id, operations) {
  return request.post(`${BASE}/${id}/operations`, operations)
}
export function createOperationReport(data) {
  return request.post('/production/operation-report', data)
}
export function pageOperationReport(params) {
  return request.get('/production/operation-report', { params })
}

// ==================== 下推接口 ====================
// 下推领料单（后端端点为 push-material-requisition）
export function pushRequisition(id) {
  return request({ url: `${BASE}/${id}/push-material-requisition`, method: 'post' })
}
// 下推生产入库单（后端端点为 push-production-receipt）
export function pushReceipt(id) {
  return request({ url: `${BASE}/${id}/push-production-receipt`, method: 'post' })
}


// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectWorkOrder(id, reason) {
  return request({ url: `${BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function reverseApproveWorkOrder(id) {
  return request({ url: `${BASE}/${id}/reverse-approve`, method: 'post' })
}
export function voidWorkOrder(id, reason) {
  return request({ url: `${BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidWorkOrder(id) {
  return request({ url: `${BASE}/${id}/unvoid`, method: 'post' })
}
export function reopenWorkOrder(id) {
  return request({ url: `${BASE}/${id}/reopen`, method: 'post' })
}
export function deleteWorkOrder(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}
