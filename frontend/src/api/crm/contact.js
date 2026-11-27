// CRM联系人管理 API
import request from '@/utils/request'

const BASE = '/crm/contact'

export function listContact(params) {
  return request.get(`${BASE}/page`, { params })
}

export function getContact(id) {
  return request.get(`${BASE}/${id}`)
}

export function saveContact(data) {
  return request.post(BASE, data)
}

export function updateContact(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteContact(id) {
  return request.delete(`${BASE}/${id}`)
}

// 按客户查询联系人列表
export function listContactByCustomer(customerId) {
  return request.get(`${BASE}/by-customer/${customerId}`)
}

// 设为主联系人
export function setPrimaryContact(id) {
  return request.put(`${BASE}/${id}/set-primary`)
}
