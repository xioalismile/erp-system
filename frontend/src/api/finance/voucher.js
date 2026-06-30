// 总账 API - 凭证CRUD + 审核 + 记账 / 模板CRUD / 余额查询
import request from '@/utils/request'

const BASE = '/finance/gl'

// ========== 会计凭证 ==========

export function pageVoucher(params) {
  return request.get(`${BASE}/vouchers`, { params })
}

export function getVoucher(id) {
  return request.get(`${BASE}/vouchers/${id}`)
}

export function createVoucher(data) {
  return request.post(`${BASE}/vouchers`, data)
}

export function updateVoucher(id, data) {
  return request.put(`${BASE}/vouchers/${id}`, data)
}

export function deleteVoucher(id) {
  return request.delete(`${BASE}/vouchers/${id}`)
}

export function auditVoucher(id) {
  return request.post(`${BASE}/vouchers/${id}/audit`)
}

export function postVoucher(id) {
  return request.post(`${BASE}/vouchers/${id}/post`)
}

// ========== 凭证模板 ==========

export function listTemplates() {
  return request.get(`${BASE}/templates`)
}

export function saveTemplate(data) {
  return request.post(`${BASE}/templates`, data)
}

export function updateTemplate(id, data) {
  return request.put(`${BASE}/templates/${id}`, data)
}

export function deleteTemplate(id) {
  return request.delete(`${BASE}/templates/${id}`)
}

// ========== 科目余额 ==========

export function pageBalance(params) {
  return request.get(`${BASE}/balance`, { params })
}
