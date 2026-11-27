// MRP运算方案 API (baseURL 已含 /api 前缀)
import request from '@/utils/request'
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

const BASE = '/production/mrp-plan'

// MRP 运算计划明细报表 API（列表页「明细模式」取数/导出 + 筛选条件元数据；基路径与后端控制器一致）
export const mrpPlanDetailApi = createBillDetailReportApi('/report/production/mrp-plan-detail')

export function listMrpPlan(params) {
  return request({ url: BASE, method: 'get', params })
}
export function getMrpPlan(id) {
  return request({ url: `${BASE}/${id}`, method: 'get' })
}
export function saveMrpPlan(data) {
  return request({ url: BASE, method: 'post', data })
}
export function updateMrpPlan(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'put', data })
}
export function deleteMrpPlan(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}
export function approveMrpPlan(id) {
  return request({ url: `${BASE}/${id}/approve`, method: 'post' })
}
export function executeMrpPlan(id) {
  return request({ url: `${BASE}/${id}/execute`, method: 'post' })
}

// ===== 按计划明细生成下游单据（itemIds 为空即按建议类型全量生成） =====
export function generatePurchase(id, itemIds) {
  return request({ url: `${BASE}/${id}/generate-purchase`, method: 'post', data: { itemIds: itemIds || [] } })
}
export function generateProduce(id, itemIds) {
  return request({ url: `${BASE}/${id}/generate-produce`, method: 'post', data: { itemIds: itemIds || [] } })
}
export function generateSubcontract(id, itemIds) {
  return request({ url: `${BASE}/${id}/generate-subcontract`, method: 'post', data: { itemIds: itemIds || [] } })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectMrpPlan(id, reason) {
  return request({ url: `${BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function reverseApproveMrpPlan(id) {
  return request({ url: `${BASE}/${id}/reverse-approve`, method: 'post' })
}
export function voidMrpPlan(id, reason) {
  return request({ url: `${BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidMrpPlan(id) {
  return request({ url: `${BASE}/${id}/unvoid`, method: 'post' })
}
export function closeOrderMrpPlan(id) {
  return request({ url: `${BASE}/${id}/close-order`, method: 'post' })
}
export function reopenMrpPlan(id) {
  return request({ url: `${BASE}/${id}/reopen`, method: 'post' })
}
