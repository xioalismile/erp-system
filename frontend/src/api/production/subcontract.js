// 委外管理 API (baseURL 已含 /api 前缀)
import request from '@/utils/request'

// ==================== 委外加工单 ====================
const BASE = '/subcontract/orders'

export function listSubcontract(params) {
  return request({ url: BASE, method: 'get', params })
}
// 列表页顶部统计卡片数据（与列表同数据权限口径）
export function getSubcontractOrderDashboard() {
  return request({ url: `${BASE}/dashboard`, method: 'get' })
}

// ===== 2026-09-20 新增：委外 4 个子单据的列表统计卡 =====
// 背景：这 4 页的统计卡原来全部写死 '0'（占位），无 dashboard 接口；本次按委外加工单的现成写法补齐后端接口，
// 口径与列表逐字一致（统一数据权限按 create_by 过滤；「本月」= 当月1号~月末，按各自单据日期列取区间）。
export function getSubcontractPickDashboard() {
  return request({ url: '/subcontract/picks/dashboard', method: 'get' })
}
export function getSubcontractInboundDashboard() {
  return request({ url: '/subcontract/inbounds/dashboard', method: 'get' })
}
export function getSubcontractReturnDashboard() {
  return request({ url: '/subcontract/returns/dashboard', method: 'get' })
}
export function getSubcontractSupplementDashboard() {
  return request({ url: '/subcontract/supplements/dashboard', method: 'get' })
}
export function getSubcontract(id) {
  return request({ url: `${BASE}/${id}`, method: 'get' })
}
export function saveSubcontract(data) {
  return request({ url: BASE, method: 'post', data })
}
export function updateSubcontract(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'put', data })
}
export function deleteSubcontract(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}
export function releaseSubcontract(id) {
  return request({ url: `${BASE}/${id}/release`, method: 'post' })
}
export function completeSubcontract(id) {
  return request({ url: `${BASE}/${id}/complete`, method: 'post' })
}
export function closeSubcontract(id) {
  return request({ url: `${BASE}/${id}/close`, method: 'post' })
}
// 下推发料单
export function pushPick(id) {
  return request({ url: `${BASE}/${id}/push-pick`, method: 'post' })
}
// 下推入库单
export function pushInbound(id) {
  return request({ url: `${BASE}/${id}/push-inbound`, method: 'post' })
}

// ==================== 委外发料单 ====================
const PICK_BASE = '/subcontract/picks'

export function pageSubcontractPick(params) {
  return request({ url: `${PICK_BASE}/page`, method: 'get', params })
}
export function getSubcontractPick(id) {
  return request({ url: `${PICK_BASE}/${id}`, method: 'get' })
}
export function saveSubcontractPick(data) {
  return request({ url: PICK_BASE, method: 'post', data })
}
export function updateSubcontractPick(id, data) {
  return request({ url: `${PICK_BASE}/${id}`, method: 'put', data })
}
export function deleteSubcontractPick(id) {
  return request({ url: `${PICK_BASE}/${id}`, method: 'delete' })
}
export function submitSubcontractPick(id) {
  return request({ url: `${PICK_BASE}/${id}/submit`, method: 'post' })
}
export function approveSubcontractPick(id, approver) {
  return request({ url: `${PICK_BASE}/${id}/approve`, method: 'post', params: { approver } })
}
export function reverseApproveSubcontractPick(id) {
  return request({ url: `${PICK_BASE}/${id}/reverse-approve`, method: 'post' })
}
export function rejectSubcontractPick(id, reason) {
  return request({ url: `${PICK_BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function voidSubcontractPick(id, reason) {
  return request({ url: `${PICK_BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidSubcontractPick(id) {
  return request({ url: `${PICK_BASE}/${id}/unvoid`, method: 'post' })
}

// ==================== 委外入库单 ====================
const INBOUND_BASE = '/subcontract/inbounds'

export function pageSubcontractInbound(params) {
  return request({ url: `${INBOUND_BASE}/page`, method: 'get', params })
}
export function getSubcontractInbound(id) {
  return request({ url: `${INBOUND_BASE}/${id}`, method: 'get' })
}
export function saveSubcontractInbound(data) {
  return request({ url: INBOUND_BASE, method: 'post', data })
}
export function updateSubcontractInbound(id, data) {
  return request({ url: `${INBOUND_BASE}/${id}`, method: 'put', data })
}
export function deleteSubcontractInbound(id) {
  return request({ url: `${INBOUND_BASE}/${id}`, method: 'delete' })
}
export function submitSubcontractInbound(id) {
  return request({ url: `${INBOUND_BASE}/${id}/submit`, method: 'post' })
}
export function approveSubcontractInbound(id, approver) {
  return request({ url: `${INBOUND_BASE}/${id}/approve`, method: 'post', params: { approver } })
}
export function reverseApproveSubcontractInbound(id) {
  return request({ url: `${INBOUND_BASE}/${id}/reverse-approve`, method: 'post' })
}
export function rejectSubcontractInbound(id, reason) {
  return request({ url: `${INBOUND_BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function voidSubcontractInbound(id, reason) {
  return request({ url: `${INBOUND_BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidSubcontractInbound(id) {
  return request({ url: `${INBOUND_BASE}/${id}/unvoid`, method: 'post' })
}

// ==================== 委外补料单 ====================
const SUPPLEMENT_BASE = '/subcontract/supplements'

export function pageSubcontractSupplement(params) {
  return request({ url: `${SUPPLEMENT_BASE}/page`, method: 'get', params })
}
export function getSubcontractSupplement(id) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}`, method: 'get' })
}
export function saveSubcontractSupplement(data) {
  return request({ url: SUPPLEMENT_BASE, method: 'post', data })
}
export function updateSubcontractSupplement(id, data) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}`, method: 'put', data })
}
export function deleteSubcontractSupplement(id) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}`, method: 'delete' })
}
export function submitSubcontractSupplement(id) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}/submit`, method: 'post' })
}
export function approveSubcontractSupplement(id, approver) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}/approve`, method: 'post', params: { approver } })
}
export function reverseApproveSubcontractSupplement(id) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}/reverse-approve`, method: 'post' })
}
export function rejectSubcontractSupplement(id, reason) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function voidSubcontractSupplement(id, reason) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidSubcontractSupplement(id) {
  return request({ url: `${SUPPLEMENT_BASE}/${id}/unvoid`, method: 'post' })
}

// ==================== 委外退料单 ====================
const RETURN_BASE = '/subcontract/returns'

export function pageSubcontractReturn(params) {
  return request({ url: `${RETURN_BASE}/page`, method: 'get', params })
}
export function getSubcontractReturn(id) {
  return request({ url: `${RETURN_BASE}/${id}`, method: 'get' })
}
export function saveSubcontractReturn(data) {
  return request({ url: RETURN_BASE, method: 'post', data })
}
export function updateSubcontractReturn(id, data) {
  return request({ url: `${RETURN_BASE}/${id}`, method: 'put', data })
}
export function deleteSubcontractReturn(id) {
  return request({ url: `${RETURN_BASE}/${id}`, method: 'delete' })
}
export function submitSubcontractReturn(id) {
  return request({ url: `${RETURN_BASE}/${id}/submit`, method: 'post' })
}
export function approveSubcontractReturn(id, approver) {
  return request({ url: `${RETURN_BASE}/${id}/approve`, method: 'post', params: { approver } })
}
export function reverseApproveSubcontractReturn(id) {
  return request({ url: `${RETURN_BASE}/${id}/reverse-approve`, method: 'post' })
}
export function rejectSubcontractReturn(id, reason) {
  return request({ url: `${RETURN_BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function voidSubcontractReturn(id, reason) {
  return request({ url: `${RETURN_BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidSubcontractReturn(id) {
  return request({ url: `${RETURN_BASE}/${id}/unvoid`, method: 'post' })
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function approveSubcontract(id, approver) {
  return request({ url: `${BASE}/${id}/approve`, method: 'post', params: { approver } })
}
export function reverseApproveSubcontract(id) {
  return request({ url: `${BASE}/${id}/reverse-approve`, method: 'post' })
}
export function rejectSubcontract(id, reason) {
  return request({ url: `${BASE}/${id}/reject`, method: 'post', data: { reason } })
}
export function voidSubcontract(id, reason) {
  return request({ url: `${BASE}/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidSubcontract(id) {
  return request({ url: `${BASE}/${id}/unvoid`, method: 'post' })
}
export function reopenSubcontract(id) {
  return request({ url: `${BASE}/${id}/reopen`, method: 'post' })
}
