// 付款单 API
import request from '@/utils/request'

const BASE = '/finance'

// 分页查询付款单（原方法，保留向后兼容）
export function pageVoucher(params) {
  return request.get(`${BASE}/vouchers`, { params })
}

// 查询单张付款单详情
export function getVoucher(id) {
  return request.get(`${BASE}/vouchers/${id}`)
}

// 新增/保存付款单（原方法，保留向后兼容）
export function createVoucher(data) {
  return request.post(`${BASE}/vouchers`, data)
}

// 确认付款
export function confirmVoucher(id) {
  return request.post(`${BASE}/vouchers/${id}/confirm`)
}

// 提交付款单（草稿→开审批则待审核/免审批则直接生效）
export function submitVoucher(id) {
  return request.post(`${BASE}/vouchers/${id}/submit`)
}

// ===== 任务规范要求的方法别名 =====
// 分页查询付款单
export function listPayment(params) {
  return request.get(`${BASE}/vouchers`, { params })
}

// 付款单列表统计卡（数值口径与列表一致，含数据权限）
export function getPaymentVoucherDashboard() {
  return request.get(`${BASE}/vouchers/dashboard`)
}

// 保存付款单（新增/更新）
export function savePayment(data) {
  return request.post(`${BASE}/vouchers`, data)
}

// ===== 以下为状态流转统一接口（自动补齐） =====
export function rejectPayment(id, reason) { return request.post(`${BASE}/vouchers/${id}/reject`, { reason }) }
export function reverseApprovePayment(id) { return request.post(`${BASE}/vouchers/${id}/reverse-approve`) }
export function voidPayment(id, reason) { return request.post(`${BASE}/vouchers/${id}/void`, { reason }) }
export function unvoidPayment(id) { return request.post(`${BASE}/vouchers/${id}/unvoid`) }
export function closeOrderPayment(id) { return request.post(`${BASE}/vouchers/${id}/close-order`) }
export function reopenPayment(id) { return request.post(`${BASE}/vouchers/${id}/reopen`) }
export function deletePayment(id) { return request.delete(`${BASE}/vouchers/${id}`) }
